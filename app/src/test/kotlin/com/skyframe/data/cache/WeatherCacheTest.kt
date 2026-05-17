package com.skyframe.data.cache

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class WeatherCacheTest {

    @Test
    fun `cache returns stored value within TTL`() {
        var now = Instant.fromEpochSeconds(1_000)
        val cache = WeatherCache<String> { now }
        cache.put("key", "value", ttl = 60.seconds)
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `cache returns null after TTL expires`() {
        var now = Instant.fromEpochSeconds(1_000)
        val cache = WeatherCache<String> { now }
        cache.put("key", "value", ttl = 60.seconds)
        now = Instant.fromEpochSeconds(1_061)
        assertNull(cache.get("key"))
    }

    @Test
    fun `cache returns most recent put`() {
        val now = Instant.fromEpochSeconds(1_000)
        val cache = WeatherCache<String> { now }
        cache.put("key", "first", ttl = 60.seconds)
        cache.put("key", "second", ttl = 60.seconds)
        assertEquals("second", cache.get("key"))
    }

    @Test
    fun `invalidate removes entry`() {
        val now = Instant.fromEpochSeconds(1_000)
        val cache = WeatherCache<String> { now }
        cache.put("key", "value", ttl = 60.seconds)
        cache.invalidate("key")
        assertNull(cache.get("key"))
    }

    @Test
    fun `clear removes all entries`() {
        val now = Instant.fromEpochSeconds(1_000)
        val cache = WeatherCache<String> { now }
        cache.put("a", "1", ttl = 60.seconds)
        cache.put("b", "2", ttl = 60.seconds)
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
