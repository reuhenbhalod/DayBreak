package com.daybreak.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningPolicyTest {

    @Test
    fun `notifies in the morning when not yet notified today`() {
        assertTrue(MorningPolicy.shouldNotify(hourOfDay = 7, today = "2026-06-23", lastNotified = "2026-06-22"))
        assertTrue(MorningPolicy.shouldNotify(hourOfDay = 7, today = "2026-06-23", lastNotified = null))
    }

    @Test
    fun `does not notify twice on the same day`() {
        assertFalse(MorningPolicy.shouldNotify(hourOfDay = 8, today = "2026-06-23", lastNotified = "2026-06-23"))
    }

    @Test
    fun `does not notify outside the morning window`() {
        assertFalse(MorningPolicy.shouldNotify(hourOfDay = 0, today = "2026-06-23", lastNotified = null))
        assertFalse(MorningPolicy.shouldNotify(hourOfDay = 14, today = "2026-06-23", lastNotified = null))
        assertFalse(MorningPolicy.shouldNotify(hourOfDay = 23, today = "2026-06-23", lastNotified = null))
    }

    @Test
    fun `notifies at the morning window edges`() {
        assertTrue(MorningPolicy.shouldNotify(hourOfDay = 4, today = "d", lastNotified = null))
        assertTrue(MorningPolicy.shouldNotify(hourOfDay = 11, today = "d", lastNotified = null))
    }
}
