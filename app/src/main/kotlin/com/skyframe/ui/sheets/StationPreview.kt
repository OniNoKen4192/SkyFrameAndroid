package com.skyframe.ui.sheets

import com.skyframe.data.nws.NormalizerHelpers
import com.skyframe.data.nws.NwsClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class StationSnapshot(
    val stationId: String,
    val observedAt: Instant?,
    val tempF: Double?,
    val isStale: Boolean,
)

/**
 * Fetches both primary + secondary station observations in parallel for
 * the StationOverrideSheet's live preview. Each station's result wrapped
 * in Result so a single-side failure doesn't abort the other side.
 *
 * Port of the web's GET /api/stations/preview semantics
 * (_reference/server/routes.ts).
 */
object StationPreview {
    suspend fun fetch(
        client: NwsClient,
        primaryId: String,
        secondaryId: String,
        now: Instant = Clock.System.now(),
    ): Pair<Result<StationSnapshot>, Result<StationSnapshot>> = coroutineScope {
        val p = async { runCatching { fetchOne(client, primaryId, now) } }
        val s = async { runCatching { fetchOne(client, secondaryId, now) } }
        Pair(p.await(), s.await())
    }

    private suspend fun fetchOne(client: NwsClient, id: String, now: Instant): StationSnapshot {
        val obs = client.latestObservation(id)
        val props = obs.properties
        val observedAt = runCatching { Instant.parse(props.timestamp) }.getOrNull()
        val tempF = NormalizerHelpers.toFahrenheit(props.temperature)
        val isStale = observedAt == null || NormalizerHelpers.isObservationStale(
            timestampEpochMs = observedAt.toEpochMilliseconds(),
            nowEpochMs = now.toEpochMilliseconds(),
            temperatureF = tempF,
        )
        return StationSnapshot(stationId = id, observedAt = observedAt, tempF = tempF, isStale = isStale)
    }
}
