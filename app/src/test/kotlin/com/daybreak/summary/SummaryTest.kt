package com.daybreak.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryTest {

    private val req = SummaryRequest(
        recovery = 78, sleep = 86, activity = 94,
        recoveryDriver = "Resting HR vs baseline", sleepDriver = "Total sleep vs need",
    )

    @Test
    fun `prompt includes the scores and dominant drivers`() {
        val prompt = PromptBuilder.build(req)
        assertTrue(prompt.contains("78"))
        assertTrue(prompt.contains("86"))
        assertTrue(prompt.contains("94"))
        assertTrue(prompt.contains("Resting HR vs baseline"))
        assertTrue(prompt.contains("Total sleep vs need"))
        assertTrue(prompt.contains("ONE short", ignoreCase = true))
    }

    @Test
    fun `selector uses AI summary when enabled and present`() {
        assertEquals("AI says hi", SummarySelector.choose(true, "AI says hi", "rule-based"))
    }

    @Test
    fun `selector falls back when AI disabled, null, or blank`() {
        assertEquals("rule-based", SummarySelector.choose(false, "AI says hi", "rule-based"))
        assertEquals("rule-based", SummarySelector.choose(true, null, "rule-based"))
        assertEquals("rule-based", SummarySelector.choose(true, "   ", "rule-based"))
    }
}
