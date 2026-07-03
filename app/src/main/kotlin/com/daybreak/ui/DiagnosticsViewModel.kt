package com.daybreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daybreak.ble.RingClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Bare diagnostics: connect to the ring and report what each command returns, so we can
 * confirm whether real data is actually coming in. No scoring, no storage — just reads.
 */
class DiagnosticsViewModel(private val ring: RingClient) : ViewModel() {

    private val _report = MutableStateFlow<List<String>>(listOf("Tap “Read ring” to start."))
    val report: StateFlow<List<String>> = _report.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _liveHeartRate = MutableStateFlow<Int?>(null)
    val liveHeartRate: StateFlow<Int?> = _liveHeartRate.asStateFlow()

    /** Live raw BLE log (tx/rx/connection) straight from the ring client. */
    val bleLog: StateFlow<List<String>> = ring.events

    /** On-demand real-time heart-rate measurement (PRD: measure now). */
    fun measureHeartRate() {
        if (_running.value) return
        _running.value = true
        _liveHeartRate.value = null
        val lines = mutableListOf<String>()
        fun log(s: String) { android.util.Log.i("Diag", s); lines.add(s); _report.value = lines.toList() }

        viewModelScope.launch {
            try {
                log("Measuring heart rate — keep the ring snug on the finger…")
                if (!ring.connect()) {
                    log("❌ Could not connect to the ring")
                    return@launch
                }
                val finalBpm = ring.measureHeartRate { bpm -> _liveHeartRate.value = bpm }
                log(
                    if (finalBpm != null) "❤️ Heart rate: $finalBpm bpm"
                    else "Couldn't get a reading — make sure the ring is snug on the finger and try again.",
                )
            } catch (t: Throwable) {
                log("❌ Error: ${t.message}")
            } finally {
                ring.disconnect()
                _running.value = false
            }
        }
    }

    fun readRing() {
        if (_running.value) return
        _running.value = true
        val lines = mutableListOf<String>()
        fun log(s: String) {
            android.util.Log.i("Diag", s)
            lines.add(s)
            _report.value = lines.toList()
        }

        viewModelScope.launch {
            try {
                log("Connecting…")
                if (!ring.connect()) {
                    log("❌ Could not connect to the ring (asleep, out of range, or used by another app?)")
                    return@launch
                }
                log("✅ Connected")

                ring.setTime()
                log("Clock set on ring")

                val battery = ring.fetchBattery()
                log("Battery: " + (battery?.let { "${it.level}%${if (it.charging) " (charging)" else ""}" } ?: "no data"))

                val nowSeconds = System.currentTimeMillis() / 1000
                val hr = ring.fetchHeartRateLog(nowSeconds)
                log("Heart rate log: " + (hr?.let {
                    val readings = it.rates.count { r -> r > 0 }
                    if (readings == 0) "no readings yet" else "$readings readings (e.g. ${it.rates.filter { r -> r > 0 }.take(5)})"
                } ?: "no data"))

                for (offset in 0..2) {
                    val steps = ring.fetchDailyActivity(offset)
                    val label = when (offset) { 0 -> "today"; 1 -> "yesterday"; else -> "$offset days ago" }
                    log("Steps $label: " + (steps?.let {
                        if (it.noData) "no data" else "${it.totalSteps} steps, ${it.totalCalories} cal, ${it.totalDistanceMeters} m"
                    } ?: "no data"))
                }

                val sleep = ring.fetchSleep()
                log("Sleep: " + (sleep?.let {
                    if (it.isEmpty()) "no nights recorded" else it.joinToString("; ") { d -> "${d.daysAgo}d ago: ${d.deepMin + d.remMin + d.lightMin} min, ${d.stages.size} stages" }
                } ?: "no data"))

                val spo2 = ring.fetchSpo2()
                log("SpO2: " + (spo2?.let {
                    if (it.isEmpty()) "no data" else it.joinToString("; ") { d -> "${d.daysAgo}d ago: avg ${d.average}%" }
                } ?: "no data"))

                log("Done.")
            } catch (t: Throwable) {
                log("❌ Error: ${t.message}")
            } finally {
                ring.disconnect()
                _running.value = false
            }
        }
    }

    companion object {
        fun factory(ring: RingClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DiagnosticsViewModel(ring) as T
            }
    }
}
