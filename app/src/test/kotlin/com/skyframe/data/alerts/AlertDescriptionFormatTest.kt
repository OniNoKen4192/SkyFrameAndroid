package com.skyframe.data.alerts

import com.skyframe.data.alerts.AlertDescriptionParagraph.Plain
import com.skyframe.data.alerts.AlertDescriptionParagraph.Prefix
import com.skyframe.data.alerts.AlertDescriptionParagraph.Tagged
import com.skyframe.domain.Alert
import com.skyframe.domain.AlertSeverity
import com.skyframe.domain.AlertTier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlertDescriptionFormatTest {

    @Test
    fun `empty input returns empty list`() {
        assertEquals(emptyList<AlertDescriptionParagraph>(), AlertDescriptionFormat.parseDescription(""))
    }

    @Test
    fun `single plain paragraph emits one Plain`() {
        val result = AlertDescriptionFormat.parseDescription("A tornado has been spotted near downtown.")
        assertEquals(1, result.size)
        assertEquals(Plain("A tornado has been spotted near downtown."), result[0])
    }

    @Test
    fun `HAZARD prefix is stripped and tagged`() {
        val result = AlertDescriptionFormat.parseDescription("HAZARD...Tornado.")
        assertEquals(1, result.size)
        assertEquals(Tagged(Prefix.HAZARD, "Tornado."), result[0])
    }

    @Test
    fun `SOURCE and IMPACT prefixes also tagged`() {
        val result = AlertDescriptionFormat.parseDescription(
            "SOURCE...National Weather Service.\n\nIMPACT...Flying debris."
        )
        assertEquals(2, result.size)
        assertEquals(Tagged(Prefix.SOURCE, "National Weather Service."), result[0])
        assertEquals(Tagged(Prefix.IMPACT, "Flying debris."), result[1])
    }

    @Test
    fun `paragraphs separated by double newlines`() {
        val result = AlertDescriptionFormat.parseDescription("First paragraph.\n\nSecond paragraph.")
        assertEquals(2, result.size)
        assertEquals(Plain("First paragraph."), result[0])
        assertEquals(Plain("Second paragraph."), result[1])
    }

    @Test
    fun `CRLF line endings normalized`() {
        val result = AlertDescriptionFormat.parseDescription("First.\r\n\r\nSecond.")
        assertEquals(2, result.size)
        assertEquals(Plain("Second."), result[1])
    }

    @Test
    fun `mixed prefix and plain paragraphs preserved in order`() {
        val raw = "Setup paragraph.\n\nHAZARD...Tornado.\n\nMore detail."
        val result = AlertDescriptionFormat.parseDescription(raw)
        assertEquals(3, result.size)
        assertEquals(Plain("Setup paragraph."), result[0])
        assertEquals(Tagged(Prefix.HAZARD, "Tornado."), result[1])
        assertEquals(Plain("More detail."), result[2])
    }

    @Test
    fun `formatTime renders 12-hour clock with TZ abbreviation`() {
        // 2026-05-17T19:30:00Z = 14:30 in America/Chicago (CDT, UTC-5)
        val instant = Instant.parse("2026-05-17T19:30:00Z")
        val result = AlertDescriptionFormat.formatTime(instant, TimeZone.of("America/Chicago"))
        assertTrue(result.contains("2:30"), "expected 2:30 in output, got $result")
        assertTrue(result.contains("PM"), "expected PM marker, got $result")
    }

    @Test
    fun `formatAlertMeta has ISSUED EXPIRES AREA structure`() {
        val alert = Alert(
            id = "urn:oid:test",
            event = "Tornado Warning",
            tier = AlertTier.TORNADO_WARNING,
            severity = AlertSeverity.EXTREME,
            headline = "Tornado",
            description = "...",
            issuedAt = Instant.parse("2026-05-17T19:30:00Z"),
            effective = Instant.parse("2026-05-17T19:30:00Z"),
            expires = Instant.parse("2026-05-17T20:00:00Z"),
            areaDesc = "Milwaukee County",
        )
        val result = AlertDescriptionFormat.formatAlertMeta(alert, TimeZone.of("America/Chicago"))
        assertTrue(result.startsWith("ISSUED "), "expected ISSUED prefix, got $result")
        assertTrue(result.contains(" · EXPIRES "), "expected EXPIRES separator, got $result")
        assertTrue(result.endsWith("MILWAUKEE COUNTY"), "expected uppercase area suffix, got $result")
    }
}
