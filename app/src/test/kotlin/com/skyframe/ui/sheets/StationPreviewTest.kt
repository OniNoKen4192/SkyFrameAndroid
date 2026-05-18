package com.skyframe.ui.sheets

import com.skyframe.data.nws.NumberMeasurementDto
import com.skyframe.data.nws.NwsClient
import com.skyframe.data.nws.ObservationDto
import com.skyframe.data.nws.ObservationProperties
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StationPreviewTest {

    private fun freshObs(stationId: String) = ObservationDto(
        ObservationProperties(
            station = "https://api.weather.gov/stations/$stationId",
            // Use a "now"-anchored timestamp so the staleness check sees it as fresh.
            timestamp = kotlinx.datetime.Clock.System.now()
                .minus(kotlin.time.Duration.parse("PT5M")).toString(),
            temperature = NumberMeasurementDto(value = 22.0, unitCode = "wmoUnit:degC"),
        )
    )

    private val now = kotlinx.datetime.Clock.System.now()

    @Test
    fun `both stations succeed produces two Success snapshots`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.latestObservation("KMKE") } returns freshObs("KMKE")
        coEvery { nws.latestObservation("KRAC") } returns freshObs("KRAC")

        val (primary, secondary) = StationPreview.fetch(nws, "KMKE", "KRAC", now)

        assertTrue(primary.isSuccess)
        assertTrue(secondary.isSuccess)
        assertEquals("KMKE", primary.getOrThrow().stationId)
        assertEquals("KRAC", secondary.getOrThrow().stationId)
    }

    @Test
    fun `primary fails but secondary succeeds returns partial result`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.latestObservation("KMKE") } throws RuntimeException("503")
        coEvery { nws.latestObservation("KRAC") } returns freshObs("KRAC")

        val (primary, secondary) = StationPreview.fetch(nws, "KMKE", "KRAC", now)

        assertTrue(primary.isFailure)
        assertTrue(secondary.isSuccess)
    }

    @Test
    fun `both fail returns two Failure results`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.latestObservation(any()) } throws RuntimeException("503")

        val (primary, secondary) = StationPreview.fetch(nws, "KMKE", "KRAC", now)

        assertTrue(primary.isFailure)
        assertTrue(secondary.isFailure)
    }
}
