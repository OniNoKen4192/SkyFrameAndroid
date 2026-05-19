package com.skyframe.background

import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import com.skyframe.data.alerts.history.LastSeenAlertRepository
import com.skyframe.data.nws.AlertFeatureDto
import com.skyframe.data.nws.AlertProperties
import com.skyframe.data.nws.AlertsDto
import com.skyframe.data.nws.NwsClient
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.notifications.NotificationDispatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class AlertPollerTest {

    private lateinit var nws: NwsClient
    private lateinit var settings: SettingsRepository
    private lateinit var lastSeen: LastSeenAlertRepository
    private lateinit var ack: AlertAcknowledgmentRepository
    private lateinit var dispatcher: NotificationDispatcher

    @BeforeEach
    fun setUp() {
        nws = mockk()
        settings = mockk()
        lastSeen = mockk(relaxed = true)
        ack = mockk()
        dispatcher = mockk(relaxed = true)
        coEvery { ack.snapshot() } returns emptySet()
        coEvery { lastSeen.read() } returns emptySet()
    }

    private fun poller() = AlertPoller(nws, settings, lastSeen, ack, dispatcher)

    private fun configured() = SettingsRepository.Snapshot(
        email = "test@example.com",
        lat = 42.8744, lon = -87.8633,
        locationName = "OAK CREEK WI",
        forecastOffice = "MKX",
        gridX = 88, gridY = 58,
        stationPrimary = "KMKE",
    )

    private fun unconfigured() = SettingsRepository.Snapshot()

    private fun alertsDto(vararg pairs: Pair<String, String>) = AlertsDto(
        features = pairs.map { (id, event) ->
            AlertFeatureDto(
                id = id,
                properties = AlertProperties(
                    id = id,
                    event = event,
                    severity = "Severe",
                    headline = "TEST",
                    description = "test alert",
                    sent = "2026-05-19T19:00:00Z",
                    effective = "2026-05-19T19:00:00Z",
                    expires = "2026-05-19T20:00:00Z",
                    areaDesc = "Milwaukee County",
                ),
            )
        }
    )

    @Test
    fun `skips cleanly when not configured`() = runTest {
        coEvery { settings.snapshot() } returns unconfigured()

        val outcome = poller().poll()

        assertEquals(AlertPoller.Outcome.Skipped, outcome)
        coVerify(exactly = 0) { nws.activeAlerts(any(), any()) }
    }

    @Test
    fun `returns Retry on IOException from activeAlerts`() = runTest {
        coEvery { settings.snapshot() } returns configured()
        coEvery { nws.activeAlerts(any(), any()) } throws IOException("network down")

        assertEquals(AlertPoller.Outcome.Retry, poller().poll())
    }

    @Test
    fun `non-network exception completes without retry or top-tier`() = runTest {
        coEvery { settings.snapshot() } returns configured()
        coEvery { nws.activeAlerts(any(), any()) } throws IllegalStateException("parse error")

        assertEquals(AlertPoller.Outcome.Polled(hasTopTier = false), poller().poll())
    }

    @Test
    fun `persists current alert ids to LastSeenAlertRepository`() = runTest {
        coEvery { settings.snapshot() } returns configured()
        coEvery { nws.activeAlerts(any(), any()) } returns alertsDto(
            "alert-1" to "Severe Thunderstorm Warning",
            "alert-2" to "Severe Thunderstorm Warning",
        )

        poller().poll()

        coVerify { lastSeen.write(setOf("alert-1", "alert-2")) }
    }

    @Test
    fun `empty alerts list writes empty set and reports no top-tier`() = runTest {
        coEvery { settings.snapshot() } returns configured()
        coEvery { nws.activeAlerts(any(), any()) } returns alertsDto()

        val outcome = poller().poll()

        coVerify { lastSeen.write(emptySet()) }
        assertEquals(AlertPoller.Outcome.Polled(hasTopTier = false), outcome)
    }

    @Test
    fun `fires notification only for new alerts not in lastSeen`() = runTest {
        coEvery { settings.snapshot() } returns configured()
        coEvery { lastSeen.read() } returns setOf("alert-1")
        coEvery { nws.activeAlerts(any(), any()) } returns alertsDto(
            "alert-1" to "Severe Thunderstorm Warning",
            "alert-2" to "Severe Thunderstorm Warning",
        )

        poller().poll()

        coVerify(exactly = 1) { dispatcher.notify(match { it.id == "alert-2" }) }
        coVerify(exactly = 0) { dispatcher.notify(match { it.id == "alert-1" }) }
    }

    @Test
    fun `does not fire notifications for acknowledged alerts`() = runTest {
        coEvery { settings.snapshot() } returns configured()
        coEvery { ack.snapshot() } returns setOf("alert-1", "alert-2")
        coEvery { nws.activeAlerts(any(), any()) } returns alertsDto(
            "alert-1" to "Severe Thunderstorm Warning",
            "alert-2" to "Severe Thunderstorm Warning",
        )

        poller().poll()

        coVerify(exactly = 0) { dispatcher.notify(any()) }
    }

    @Test
    fun `reissued alert (in lastSeen) does not re-notify`() = runTest {
        coEvery { settings.snapshot() } returns configured()
        coEvery { lastSeen.read() } returns setOf("alert-1")
        coEvery { nws.activeAlerts(any(), any()) } returns alertsDto("alert-1" to "Severe Thunderstorm Warning")

        poller().poll()

        coVerify(exactly = 0) { dispatcher.notify(any()) }
    }

    @Test
    fun `reports top-tier for tornado warning`() = runTest {
        coEvery { settings.snapshot() } returns configured()
        coEvery { nws.activeAlerts(any(), any()) } returns alertsDto("t-1" to "Tornado Warning")

        val outcome = poller().poll()

        assertTrue(outcome is AlertPoller.Outcome.Polled && outcome.hasTopTier)
    }

    @Test
    fun `reports no top-tier for advisory`() = runTest {
        coEvery { settings.snapshot() } returns configured()
        coEvery { nws.activeAlerts(any(), any()) } returns alertsDto("a-1" to "Wind Advisory")

        val outcome = poller().poll()

        assertEquals(AlertPoller.Outcome.Polled(hasTopTier = false), outcome)
    }
}
