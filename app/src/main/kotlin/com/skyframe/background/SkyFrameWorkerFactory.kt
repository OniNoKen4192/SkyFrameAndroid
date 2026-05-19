package com.skyframe.background

import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin re-export for clarity. HiltWorkerFactory is already @Singleton-eligible
 * via androidx.hilt:hilt-work; injecting it into SkyFrameApp lets WorkManager
 * resolve @HiltWorker-annotated workers (AlertCheckWorker, EscalationWorker).
 */
@Singleton
class SkyFrameWorkerFactory @Inject constructor(
    val hiltFactory: HiltWorkerFactory,
)
