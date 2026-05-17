package com.skyframe.data.settings

import javax.inject.Inject
import javax.inject.Singleton

/**
 * STUB — full DataStore-backed implementation lands in Task G.1.
 * Keeps NetworkModule compilable in Phase E.
 */
@Singleton
class SettingsRepository @Inject constructor() {
    data class Snapshot(val email: String = "")
    suspend fun snapshot(): Snapshot = Snapshot()
}
