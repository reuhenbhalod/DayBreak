package com.daybreak.scoring

import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryGeneratorTest {

    private fun result(value: Int, vararg contributors: Pair<String, Double>): ScoreResult =
        ScoreResult(value, contributors.map { Contributor(it.first, 10, it.second) })

    @Test
    fun `elevated resting HR drives the take-it-easy message`() {
        val recovery = result(
            50,
            "Previous-night sleep" to 70.0,
            "Resting HR vs baseline" to 20.0, // dominant drag
            "Recovery index" to 60.0,
        )
        val sleep = result(80, "Total sleep vs need" to 85.0)
        val activity = result(70, "Steps vs goal" to 70.0)

        val msg = SummaryGenerator.generate(sleep, recovery, activity)
        assertTrue(msg.contains("heart rate", ignoreCase = true))
    }

    @Test
    fun `short sleep drives the slept-less message`() {
        val recovery = result(80, "Previous-night sleep" to 80.0, "Resting HR vs baseline" to 80.0)
        val sleep = result(50, "Total sleep vs need" to 20.0, "Sleep efficiency" to 70.0)
        val activity = result(70, "Steps vs goal" to 70.0)

        val msg = SummaryGenerator.generate(sleep, recovery, activity)
        assertTrue(msg.contains("slept less", ignoreCase = true))
    }

    @Test
    fun `strong sleep and recovery give the positive message`() {
        val recovery = result(85, "Previous-night sleep" to 85.0, "Resting HR vs baseline" to 85.0)
        val sleep = result(85, "Total sleep vs need" to 85.0)
        val activity = result(80, "Steps vs goal" to 80.0)

        val msg = SummaryGenerator.generate(sleep, recovery, activity)
        assertTrue(msg.contains("great day", ignoreCase = true))
    }

    @Test
    fun `mid-range scores give the steady default`() {
        val recovery = result(65, "Previous-night sleep" to 65.0, "Resting HR vs baseline" to 65.0)
        val sleep = result(65, "Total sleep vs need" to 65.0)
        val activity = result(65, "Steps vs goal" to 65.0)

        val msg = SummaryGenerator.generate(sleep, recovery, activity)
        assertTrue(msg.contains("steady", ignoreCase = true))
    }
}
