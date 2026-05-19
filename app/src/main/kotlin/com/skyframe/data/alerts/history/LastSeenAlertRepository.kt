package com.skyframe.data.alerts.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Persists the set of NWS alert IDs from the most recent successful background
 * poll. Read by AlertDiff to compute "new since last poll" alerts; overwritten
 * after each successful poll so the set is always exactly one poll's worth.
 *
 * Naturally bounded: NWS rarely returns more than ~5 active alerts for a single
 * point. No pruning logic needed - each write fully replaces the prior set.
 */
@Singleton
class LastSeenAlertRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringSetPreferencesKey("last_seen_alert_ids")

    suspend fun read(): Set<String> = dataStore.data.first()[key].orEmpty()

    suspend fun write(ids: Set<String>) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) prefs.remove(key) else prefs[key] = ids
        }
    }
}
