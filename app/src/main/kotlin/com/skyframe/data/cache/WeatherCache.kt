package com.skyframe.data.cache

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * Generic TTL cache. Used in two configurations:
 *   - 90-second TTL on full WeatherResponse (matches web server cache.ts)
 *   - 1-hour TTL on /points lookups (grid coordinates don't change)
 *
 * Thread-safe via ConcurrentHashMap. The clock parameter exists for tests.
 */
class WeatherCache<V>(
    private val now: () -> Instant = { Clock.System.now() },
) {
    private data class Entry<V>(val value: V, val expiresAt: Instant)

    private val map = ConcurrentHashMap<String, Entry<V>>()

    fun get(key: String): V? {
        val entry = map[key] ?: return null
        return if (now() < entry.expiresAt) entry.value else {
            map.remove(key)
            null
        }
    }

    fun put(key: String, value: V, ttl: Duration) {
        map[key] = Entry(value, now() + ttl)
    }

    fun invalidate(key: String) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }
}
