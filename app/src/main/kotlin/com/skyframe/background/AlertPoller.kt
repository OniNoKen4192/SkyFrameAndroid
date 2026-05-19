package com.skyframe.background

import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import com.skyframe.data.alerts.history.AlertDiff
import com.skyframe.data.alerts.history.LastSeenAlertRepository
import com.skyframe.data.nws.AlertNormalizer
import com.skyframe.data.nws.NwsClient
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.notifications.NotificationDispatcher
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pollable core of the background alert check, extracted from the
 * WorkManager workers so it can be unit-tested with plain mocks — no Context,
 * no Robolectric, no WorkManagerTestInitHelper. AlertCheckWorker and
 * EscalationWorker are thin shells that call poll() and map its Outcome to a
 * WorkManager Result + (for escalation) the chain-again decision.
 *
 * Steps:
 *   1. Read config; skip cleanly if !isConfigured (user still in onboarding).
 *   2. Fetch /alerts/active. IOException -> Outcome.Retry (worker maps to
 *      Result.retry(), WorkManager applies exponential backoff). Other
 *      exceptions are parse/programming errors that won't fix on retry, so
 *      treat as a completed-with-no-top-tier poll to avoid an infinite loop.
 *   3. Classify via AlertNormalizer.
 *   4. Diff against LastSeenAlertRepository + AlertAcknowledgmentRepository.
 *   5. Fire notifications for new alerts.
 *   6. Overwrite LastSeenAlertRepository with current IDs.
 *   7. Report whether a top-tier alert (rank 1-4) is active so the caller can
 *      decide whether to chain an EscalationWorker.
 */
@Singleton
class AlertPoller @Inject constructor(
    private val nws: NwsClient,
    private val settings: SettingsRepository,
    private val lastSeen: LastSeenAlertRepository,
    private val acknowledgments: AlertAcknowledgmentRepository,
    private val notificationDispatcher: NotificationDispatcher,
) {
    sealed interface Outcome {
        /** Not configured yet; nothing fetched. */
        data object Skipped : Outcome

        /** Transient network failure; caller should Result.retry(). */
        data object Retry : Outcome

        /** Poll completed. [hasTopTier] drives EscalationWorker chaining. */
        data class Polled(val hasTopTier: Boolean) : Outcome
    }

    suspend fun poll(): Outcome {
        val cfg = settings.snapshot()
        if (!cfg.isConfigured) return Outcome.Skipped

        val alertsDto = try {
            nws.activeAlerts(cfg.lat, cfg.lon)
        } catch (e: IOException) {
            return Outcome.Retry
        } catch (e: Exception) {
            // Parse/programming error - retrying won't help; report a clean
            // no-top-tier poll so the worker succeeds without looping.
            return Outcome.Polled(hasTopTier = false)
        }

        val classified = AlertNormalizer.normalize(alertsDto)
        val newAlerts = AlertDiff.diff(
            current = classified,
            lastSeen = lastSeen.read(),
            acknowledged = acknowledgments.snapshot(),
        )
        newAlerts.forEach { notificationDispatcher.notify(it) }
        lastSeen.write(classified.map { it.id }.toSet())

        return Outcome.Polled(hasTopTier = classified.any { it.tier.rank in 1..4 })
    }
}
