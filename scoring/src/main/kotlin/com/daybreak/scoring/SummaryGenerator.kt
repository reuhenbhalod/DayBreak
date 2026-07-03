package com.daybreak.scoring

/**
 * Rule-based plain-language summary (PRD §8.3 / §10.4). Template selection is keyed
 * off the three scores and their dominant driver. Kept small, warm, and non-clinical.
 * A future P2 LLM summary can replace this while keeping it as the fallback.
 */
object SummaryGenerator {

    private const val LOW = 60
    private const val GOOD = 75

    fun generate(sleep: ScoreResult, recovery: ScoreResult, activity: ScoreResult): String {
        val recoveryDriver = recovery.dominantDriver.name
        val sleepDriver = sleep.dominantDriver.name

        return when {
            recovery.value < LOW && recoveryDriver == "Resting HR vs baseline" ->
                "Your heart rate stayed up last night. A good day to take it easy."

            recovery.value < LOW ->
                "You're a little under-recovered today. Go gently and listen to your body."

            sleep.value < LOW && sleepDriver == "Total sleep vs need" ->
                "You slept less than usual. Try to wind down a bit earlier tonight."

            sleep.value < LOW ->
                "Your sleep was a little restless. Nothing to worry about — just ease in."

            recovery.value >= GOOD && sleep.value >= GOOD ->
                "You slept well and recovered nicely. A great day to be active."

            else ->
                "A steady day — you're right around your usual. Carry on."
        }
    }
}
