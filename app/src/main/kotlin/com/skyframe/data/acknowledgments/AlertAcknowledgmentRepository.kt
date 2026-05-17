package com.skyframe.data.acknowledgments

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistent set of dismissed alert IDs. Pruned automatically when an
 * alert drops off the active NWS feed.
 *
 * Port of the web's localStorage skyframe.alerts.dismissed set
 * (_reference/client/App.tsx).
 */
@Singleton
class AlertAcknowledgmentRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringSetPreferencesKey("dismissed_alert_ids")

    val flow: Flow<Set<String>> = dataStore.data.map { it[key].orEmpty() }

    suspend fun snapshot(): Set<String> = flow.first()

    suspend fun dismiss(id: String) {
        dataStore.edit { it[key] = (it[key].orEmpty()) + id }
    }

    suspend fun pruneTo(activeIds: Set<String>) {
        dataStore.edit { it[key] = (it[key].orEmpty()) intersect activeIds }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }
}
