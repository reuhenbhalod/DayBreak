package com.daybreak.data

import android.util.Log
import com.daybreak.ble.DecodedNight
import com.daybreak.ble.RingClient
import com.daybreak.export.ExportRow
import com.daybreak.protocol.DailyActivitySummary
import com.daybreak.protocol.HeartRateLog
import com.daybreak.protocol.SleepDay
import com.daybreak.scoring.ActivityInput
import com.daybreak.scoring.BaselineCalculator
import com.daybreak.scoring.HrSample
import com.daybreak.scoring.NightData
import com.daybreak.scoring.ScoringEngine
import com.daybreak.scoring.ScoringState
import com.daybreak.scoring.SleepInput
import com.daybreak.scoring.SleepStages
import com.daybreak.summary.DailySummarizer
import com.daybreak.summary.PromptBuilder
import com.daybreak.summary.SummaryRequest
import com.daybreak.summary.SummarySelector
import com.daybreak.sync.CatchUp
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Maps Room entities to the pure-Kotlin scoring inputs and back. All per-day data is
 * scoped to the current wearer profile (PRD §15 P2 multi-wearer); the scoring logic
 * itself lives in `:scoring`.
 */
class DaybreakRepository(
    private val db: DaybreakDatabase,
    private val settings: SettingsStore,
    private val summarizer: DailySummarizer? = null,
) {

    // region profiles -----------------------------------------------------------------

    private val profileMutex = kotlinx.coroutines.sync.Mutex()
    private val syncMutex = kotlinx.coroutines.sync.Mutex()

    /** Returns the current wearer profile, creating a default one on first run.
     * Guarded by a mutex so concurrent callers don't create duplicate default profiles. */
    private suspend fun currentProfile(): ProfileEntity = profileMutex.withLock {
        val profiles = db.profileDao().all()
        if (profiles.isEmpty()) {
            val id = db.profileDao().insert(ProfileEntity(name = "Wearer 1", ageYears = settings.wearerAge))
            settings.currentWearerId = id
            return@withLock db.profileDao().byId(id)!!
        }
        profiles.firstOrNull { it.id == settings.currentWearerId }
            ?: profiles.first().also { settings.currentWearerId = it.id }
    }

    suspend fun profiles(): List<ProfileEntity> {
        currentProfile() // ensure at least one exists
        return db.profileDao().all()
    }

    suspend fun currentProfileId(): Long = currentProfile().id

    /** Add a new wearer and switch to them. */
    suspend fun addProfile(name: String, age: Int): Long {
        val id = db.profileDao().insert(ProfileEntity(name = name.trim().ifEmpty { "Wearer" }, ageYears = age))
        settings.currentWearerId = id
        return id
    }

    suspend fun switchProfile(id: Long) {
        if (db.profileDao().byId(id) != null) settings.currentWearerId = id
    }

    suspend fun setCurrentWearerAge(age: Int) {
        val profile = currentProfile()
        db.profileDao().update(profile.copy(ageYears = age))
    }

    // endregion

    /** Builds today's [ScoringState] from stored history (or Calibrating if too few nights). */
    suspend fun computeTodayState(): ScoringState {
        val profile = currentProfile()
        val nights = db.sleepDao().allOrdered(profile.id)
        if (nights.isEmpty()) {
            return ScoringState.Calibrating(0, ScoringEngine.CALIBRATION_NIGHTS)
        }

        val data = nights.map { toNightData(it, profile.ageYears) }
        val today = data.last()
        val history = data.dropLast(1)
        val state = ScoringEngine.compute(today, history)
        return if (state is ScoringState.Ready) state.copy(summary = chooseSummary(state)) else state
    }

    /**
     * Builds the home dashboard: scores when calibrated, plus the raw signals (battery,
     * steps, last-night sleep, resting HR, SpO2) that are available right away.
     */
    suspend fun homeData(): HomeData {
        val profile = currentProfile()
        val wid = profile.id
        val nights = db.sleepDao().allOrdered(wid)
        val scoring = computeTodayState()

        val today = LocalDate.now().toString()
        val stepsToday = db.activityDao().forDate(wid, today)?.steps

        val lastNight = nights.lastOrNull()
        val sleepMin = lastNight?.let { it.deepMin + it.remMin + it.lightMin }?.takeIf { it > 0 }
        val restingHr = lastNight?.let { n -> db.hrSampleDao().forDate(wid, n.date).minOfOrNull { it.bpm } }
        val spo2 = lastNight?.let { db.spo2Dao().forDate(wid, it.date)?.averagePct }?.takeIf { it > 0 }

        return HomeData(
            scoring = scoring,
            batteryPct = settings.lastBatteryPct.takeIf { it in 0..100 },
            charging = settings.lastBatteryCharging,
            stepsToday = stepsToday,
            sleepLastNightMin = sleepMin,
            restingHr = restingHr,
            spo2 = spo2,
            lastUpdated = lastUpdatedLabel(),
            calibrationNightsObserved = nights.size.coerceAtMost(ScoringEngine.CALIBRATION_NIGHTS),
            calibrationNightsRequired = ScoringEngine.CALIBRATION_NIGHTS,
        )
    }

    /** Optionally replace the rule-based sentence with an on-device LLM summary (PRD §8.3 P2). */
    private suspend fun chooseSummary(state: ScoringState.Ready): String {
        if (!settings.aiSummaryEnabled || summarizer == null) return state.summary
        val request = SummaryRequest(
            recovery = state.recovery.value,
            sleep = state.sleep.value,
            activity = state.activity.value,
            recoveryDriver = state.recovery.dominantDriver.name,
            sleepDriver = state.sleep.dominantDriver.name,
        )
        val ai = summarizer.summarize(PromptBuilder.build(request))
        return SummarySelector.choose(aiEnabled = true, aiSummary = ai, ruleBased = state.summary)
    }

    fun aiSummaryEnabled(): Boolean = settings.aiSummaryEnabled
    fun setAiSummaryEnabled(enabled: Boolean) { settings.aiSummaryEnabled = enabled }

    /**
     * Pull battery, heart-rate log, steps, sleep and SpO2 from the ring and persist them
     * for the current wearer. Returns a short status string for the sync log.
     */
    suspend fun syncFromRing(ring: RingClient): String = syncMutex.withLock {
        if (!ring.connect()) {
            Log.i(TAG, "sync: ring unavailable")
            return@withLock "ring-unavailable"
        }
        val wid = currentProfile().id
        try {
            ring.setTime() // align the ring's clock so day-keyed records match "today"
            val today = LocalDate.now()
            val battery = ring.fetchBattery()
            battery?.let {
                settings.lastBatteryPct = it.level
                settings.lastBatteryCharging = it.charging
            }

            // Catch-up: backfill HR + steps for every day missed since the last sync.
            val daysSince = settings.lastSuccessfulSync
                ?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), today).toInt() }.getOrDefault(CatchUp.DEFAULT_GAP) }
                ?: CatchUp.DEFAULT_GAP
            for (offset in CatchUp.offsets(daysSince)) {
                val day = today.minusDays(offset.toLong())
                val date = day.toString()
                val daySeconds = day.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                ring.fetchHeartRateLog(daySeconds)?.let { persistHeartRateLog(wid, date, it) }
                ring.fetchDailyActivity(offset)?.let { persistActivity(wid, date, it) }
            }
            ring.fetchSleep()?.forEach { persistSleep(wid, it) }
            ring.fetchSpo2()?.forEach { spo2 ->
                val date = today.minusDays(spo2.daysAgo.toLong()).toString()
                db.spo2Dao().upsert(Spo2Entity(wid, date, spo2.average, spo2.minimum))
            }

            settings.lastSuccessfulSync = today.toString()
            settings.lastUpdatedEpochMs = System.currentTimeMillis()
            val status = "ok battery=${battery?.level ?: "?"}%"
            Log.i(TAG, "sync: $status (date=$today, backfilled ${CatchUp.offsets(daysSince).size} days)")
            status
        } catch (t: Throwable) {
            Log.w(TAG, "sync failed", t)
            "error: ${t.message}"
        } finally {
            ring.disconnect()
        }
    }

    private suspend fun persistHeartRateLog(wid: Long, date: String, log: HeartRateLog) {
        val startMin = log.startTimestampSeconds / 60
        val samples = log.rates.mapIndexedNotNull { i, bpm ->
            if (bpm <= 0) null else HrSampleEntity(wearerId = wid, date = date, epochMin = startMin + i * 5L, bpm = bpm)
        }
        if (samples.isNotEmpty()) db.hrSampleDao().insertAll(samples)
    }

    private suspend fun persistSleep(wid: Long, day: SleepDay) {
        val date = LocalDate.now().minusDays(day.daysAgo.toLong()).toString()
        val leadingAwake = day.stages.firstOrNull()?.takeIf { it.type == 0x05 }?.durationMin ?: 0
        db.sleepDao().upsert(
            NightlySleepEntity(
                wearerId = wid, date = date,
                deepMin = day.deepMin, remMin = day.remMin, lightMin = day.lightMin, awakeMin = day.awakeMin,
                timeInBedMin = day.spanMin, sleepLatencyMin = leadingAwake, wakeEvents = day.wakeEvents,
                bedTimeMinutesOfDay = day.sleepStartMin, wakeTimeMinutesOfDay = day.sleepEndMin % 1440,
                hrvMs = null, score = null,
            ),
        )
    }

    private suspend fun persistActivity(wid: Long, date: String, activity: DailyActivitySummary) {
        if (activity.noData) return
        val existing = db.activityDao().forDate(wid, date)
        db.activityDao().upsert(
            DailyActivityEntity(
                wearerId = wid, date = date, steps = activity.totalSteps,
                activeMinutes = existing?.activeMinutes ?: 0,
                longestSedentaryStretchMin = existing?.longestSedentaryStretchMin ?: 0,
                priorDayActivityLoad = existing?.priorDayActivityLoad ?: 0.0,
                score = null,
            ),
        )
    }

    /** Persist a night decoded from the ring (also used for seeding). */
    suspend fun saveDecodedNight(wid: Long, night: DecodedNight) {
        val d = night.data
        db.sleepDao().upsert(
            NightlySleepEntity(
                wearerId = wid, date = night.date,
                deepMin = d.sleep.stages.deepMin, remMin = d.sleep.stages.remMin,
                lightMin = d.sleep.stages.lightMin, awakeMin = d.sleep.stages.awakeMin,
                timeInBedMin = d.sleep.timeInBedMin, sleepLatencyMin = d.sleep.sleepLatencyMin,
                wakeEvents = d.sleep.wakeEvents, bedTimeMinutesOfDay = d.sleep.bedTimeMinutesOfDay,
                wakeTimeMinutesOfDay = d.sleep.wakeTimeMinutesOfDay, hrvMs = d.hrvMs, score = null,
            ),
        )
        db.activityDao().upsert(
            DailyActivityEntity(
                wearerId = wid, date = night.date, steps = d.activity.steps,
                activeMinutes = d.activity.activeMinutes,
                longestSedentaryStretchMin = d.activity.longestSedentaryStretchMin,
                priorDayActivityLoad = d.activity.priorDayActivityLoad, score = null,
            ),
        )
        db.hrSampleDao().insertAll(
            d.overnightHr.map { HrSampleEntity(wearerId = wid, date = night.date, epochMin = it.epochMin, bpm = it.bpm) },
        )
    }

    private suspend fun toNightData(sleep: NightlySleepEntity, ageYears: Int): NightData {
        val activity = db.activityDao().forDate(sleep.wearerId, sleep.date)
        val hr = db.hrSampleDao().forDate(sleep.wearerId, sleep.date).map { HrSample(it.epochMin, it.bpm) }
        return NightData(
            sleep = SleepInput(
                stages = SleepStages(sleep.deepMin, sleep.remMin, sleep.lightMin, sleep.awakeMin),
                timeInBedMin = sleep.timeInBedMin,
                sleepLatencyMin = sleep.sleepLatencyMin,
                wakeEvents = sleep.wakeEvents,
                bedTimeMinutesOfDay = sleep.bedTimeMinutesOfDay,
                wakeTimeMinutesOfDay = sleep.wakeTimeMinutesOfDay,
            ),
            overnightHr = hr,
            activity = activity?.let {
                ActivityInput(it.steps, it.activeMinutes, it.longestSedentaryStretchMin, it.priorDayActivityLoad)
            } ?: ActivityInput(0, 0, 0, 0.0),
            hrvMs = sleep.hrvMs,
            ageYears = ageYears,
        )
    }

    /** Seeds ~6 weeks of varying data for the current wearer so the charts aren't flat. */
    suspend fun seedDemoData() {
        val profile = currentProfile()
        val wid = profile.id
        settings.lastUpdatedEpochMs = System.currentTimeMillis()
        val days = 45
        for (i in 0 until days) {
            val date = LocalDate.now().minusDays((days - 1 - i).toLong()).toString()
            val total = 430 + (i % 6) * 22
            val deep = (total * 0.18).toInt() + (i % 3) * 4
            val rem = (total * 0.21).toInt()
            val light = (total - deep - rem).coerceAtLeast(0)
            val minBpm = 49 + (i % 7)
            val steps = 5500 + (i % 8) * 700
            val hrv = 38.0 + (i % 6) * 3
            val hr = List(9) { k -> HrSample((k * 60).toLong(), minBpm + k) }
            val spo2Avg = 94 + (i % 5)
            db.spo2Dao().upsert(Spo2Entity(wid, date, spo2Avg, spo2Avg - 3))
            saveDecodedNight(
                wid,
                DecodedNight(
                    date = date,
                    data = NightData(
                        sleep = SleepInput(SleepStages(deep, rem, light, 20), total + 20, 12, 1 + (i % 3), 1380, 420),
                        overnightHr = hr,
                        activity = ActivityInput(steps, 30 + (i % 5) * 8, 60, 0.5),
                        hrvMs = hrv,
                        ageYears = profile.ageYears,
                    ),
                ),
            )
        }
    }

    /** Builds the chart-ready data for the Insights screen over the last [rangeDays]. */
    suspend fun getInsights(rangeDays: Int): InsightsData {
        val profile = currentProfile()
        val wid = profile.id
        val nights = db.sleepDao().allOrdered(wid)
        if (nights.isEmpty()) {
            return InsightsData(
                rangeDays = rangeDays,
                trend = emptyList(),
                restingHr = emptyList(),
                steps = emptyList(),
                lastNightStages = null,
                overnightHr = emptyList(),
                today = null,
            )
        }

        val data = nights.map { toNightData(it, profile.ageYears) }
        val trend = ArrayList<DailyPoint>()
        val trendDates = ArrayList<String>()
        val restingHr = ArrayList<Float>()
        val steps = ArrayList<Int>()
        val dayDates = nights.map { shortDate(it.date) }

        for (i in data.indices) {
            val today = data[i]
            restingHr.add(minBpm(today.overnightHr).toFloat())
            steps.add(today.activity.steps)
            if (i >= ScoringEngine.CALIBRATION_NIGHTS) {
                val history = data.subList(maxOf(0, i - BaselineCalculator.WINDOW_NIGHTS), i)
                (ScoringEngine.compute(today, history) as? ScoringState.Ready)?.let {
                    trend.add(DailyPoint(nights[i].date.takeLast(5), it.sleep.value, it.recovery.value, it.activity.value))
                    trendDates.add(nights[i].date)
                }
            }
        }

        val lastNight = nights.last()
        val stages = StageComposition(lastNight.deepMin, lastNight.remMin, lastNight.lightMin, lastNight.awakeMin)
        val overnightSamples = db.hrSampleDao().forDate(wid, lastNight.date)
        val overnight = overnightSamples.map { it.bpm }
        val overnightTimes = overnightSamples.map { timeOfDay(it.epochMin) }

        val tags = db.tagDao().all(wid)
        val visibleTrendDates = trendDates.takeLast(rangeDays)
        val taggedIndices = TagMarks.indices(visibleTrendDates, tags.map { it.date }.toSet())
        val notes = tags.take(NOTES_SHOWN).map { DayNote(it.date, it.label) }

        val spo2All = db.spo2Dao().allOrdered(wid)
        val lastNightSpo2 = db.spo2Dao().forDate(wid, lastNight.date)

        return InsightsData(
            rangeDays = rangeDays,
            trend = trend.takeLast(rangeDays),
            restingHr = restingHr.takeLast(rangeDays),
            restingHrDates = dayDates.takeLast(rangeDays),
            steps = steps.takeLast(rangeDays),
            stepsDates = dayDates.takeLast(rangeDays),
            lastNightStages = stages,
            overnightHr = overnight,
            overnightHrTimes = overnightTimes,
            today = computeTodayState() as? ScoringState.Ready,
            notes = notes,
            taggedTrendIndices = taggedIndices,
            spo2 = spo2All.map { it.averagePct }.takeLast(rangeDays),
            spo2Dates = spo2All.map { shortDate(it.date) }.takeLast(rangeDays),
            lastNightSpo2Avg = lastNightSpo2?.averagePct,
            lastNightSpo2Min = lastNightSpo2?.minimumPct,
        )
    }

    /** "2026-06-24" -> "Jun 24" for chart x-axis / tap read-outs. */
    private fun shortDate(iso: String): String =
        runCatching { LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("MMM d")) }
            .getOrDefault(iso.takeLast(5))

    /** Minutes-since-epoch -> local "h:mm a" (e.g. "2:35 AM") for the overnight HR chart. */
    private fun timeOfDay(epochMin: Long): String =
        java.time.Instant.ofEpochSecond(epochMin * 60)
            .atZone(ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

    /** Add a wearer note/tag for [date] (defaults to today) for the current wearer. */
    suspend fun addTag(label: String, date: String = LocalDate.now().toString()) {
        val clean = label.trim()
        if (clean.isNotEmpty()) db.tagDao().insert(TagEntity(wearerId = currentProfile().id, date = date, label = clean))
    }

    private fun minBpm(samples: List<HrSample>): Int = samples.minOfOrNull { it.bpm } ?: 0

    /** Friendly "last updated" label, e.g. "7:14 AM" (today) or "Jun 23, 7:14 AM". */
    fun lastUpdatedLabel(): String {
        val ms = settings.lastUpdatedEpochMs
        if (ms <= 0L) return "not yet"
        val updated = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
        val time = updated.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        return if (updated.toLocalDate() == LocalDate.now()) {
            time
        } else {
            updated.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
        }
    }

    /** True if the current wearer has any recorded night (gates the morning notification). */
    suspend fun hasAnyNight(): Boolean = db.sleepDao().allOrdered(currentProfile().id).isNotEmpty()

    /** Gathers every stored day for the current wearer (raw metrics + scores) for CSV export. */
    suspend fun exportRows(): List<ExportRow> {
        val profile = currentProfile()
        val nights = db.sleepDao().allOrdered(profile.id)
        val data = nights.map { toNightData(it, profile.ageYears) }
        return nights.indices.map { i ->
            val today = data[i]
            var recovery: Int? = null
            var sleep: Int? = null
            var activity: Int? = null
            if (i >= ScoringEngine.CALIBRATION_NIGHTS) {
                val history = data.subList(maxOf(0, i - BaselineCalculator.WINDOW_NIGHTS), i)
                (ScoringEngine.compute(today, history) as? ScoringState.Ready)?.let {
                    recovery = it.recovery.value; sleep = it.sleep.value; activity = it.activity.value
                }
            }
            val n = nights[i]
            ExportRow(
                date = n.date, recovery = recovery, sleep = sleep, activity = activity,
                totalSleepMin = n.deepMin + n.remMin + n.lightMin,
                deepMin = n.deepMin, remMin = n.remMin, lightMin = n.lightMin, awakeMin = n.awakeMin,
                steps = today.activity.steps, restingHr = minBpm(today.overnightHr),
            )
        }
    }

    companion object {
        private const val TAG = "DaybreakRepo"
        private const val NOTES_SHOWN = 10
    }
}
