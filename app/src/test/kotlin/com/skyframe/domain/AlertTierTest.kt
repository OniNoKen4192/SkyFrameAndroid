package com.skyframe.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlertTierTest {

    @Test
    fun `tier ranks are unique and contiguous 1 through 13`() {
        val ranks = AlertTier.entries.map { it.rank }.sorted()
        assertEquals((1..13).toList(), ranks)
    }

    @Test
    fun `tornado-emergency outranks all others`() {
        val emergency = AlertTier.TORNADO_EMERGENCY
        AlertTier.entries.filter { it != emergency }.forEach {
            assertTrue(emergency.rank < it.rank, "$it should rank higher (numerically lower) than $emergency")
        }
    }

    @Test
    fun `advisory is the catch-all tier with rank 13`() {
        assertEquals(13, AlertTier.ADVISORY.rank)
    }

    @Test
    fun `tier IDs match the web string discriminators exactly`() {
        // These string IDs are the wire-format match with the web's AlertTier type union.
        // They must not be renamed without coordinated changes in serialization.
        assertEquals("tornado-emergency", AlertTier.TORNADO_EMERGENCY.id)
        assertEquals("tornado-pds", AlertTier.TORNADO_PDS.id)
        assertEquals("tornado-warning", AlertTier.TORNADO_WARNING.id)
        assertEquals("tstorm-destructive", AlertTier.TSTORM_DESTRUCTIVE.id)
        assertEquals("severe-warning", AlertTier.SEVERE_WARNING.id)
        assertEquals("blizzard", AlertTier.BLIZZARD.id)
        assertEquals("winter-storm", AlertTier.WINTER_STORM.id)
        assertEquals("flood", AlertTier.FLOOD.id)
        assertEquals("heat", AlertTier.HEAT.id)
        assertEquals("special-weather-statement", AlertTier.SPECIAL_WEATHER_STATEMENT.id)
        assertEquals("watch", AlertTier.WATCH.id)
        assertEquals("advisory-high", AlertTier.ADVISORY_HIGH.id)
        assertEquals("advisory", AlertTier.ADVISORY.id)
    }
}
