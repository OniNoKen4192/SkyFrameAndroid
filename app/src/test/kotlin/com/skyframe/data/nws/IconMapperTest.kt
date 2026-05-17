package com.skyframe.data.nws

import com.skyframe.domain.IconCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IconMapperTest {

    @Test
    fun `clear day icon maps to sun`() {
        assertEquals(IconCode.SUN, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/skc?size=medium"))
    }

    @Test
    fun `clear night icon maps to moon`() {
        assertEquals(IconCode.MOON, IconMapper.fromUrl("https://api.weather.gov/icons/land/night/skc?size=medium"))
    }

    @Test
    fun `few clouds day maps to partly-day`() {
        assertEquals(IconCode.PARTLY_DAY, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/few?size=medium"))
    }

    @Test
    fun `few clouds night maps to partly-night`() {
        assertEquals(IconCode.PARTLY_NIGHT, IconMapper.fromUrl("https://api.weather.gov/icons/land/night/few?size=medium"))
    }

    @Test
    fun `overcast maps to cloud`() {
        assertEquals(IconCode.CLOUD, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/ovc?size=medium"))
    }

    @Test
    fun `rain icon maps to rain`() {
        assertEquals(IconCode.RAIN, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/rain,80?size=medium"))
    }

    @Test
    fun `snow icon maps to snow`() {
        assertEquals(IconCode.SNOW, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/snow,40?size=medium"))
    }

    @Test
    fun `thunderstorm icon maps to thunder`() {
        assertEquals(IconCode.THUNDER, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/tsra?size=medium"))
    }

    @Test
    fun `fog icon maps to fog`() {
        assertEquals(IconCode.FOG, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/fog?size=medium"))
    }

    @Test
    fun `null URL falls back to cloud`() {
        assertEquals(IconCode.CLOUD, IconMapper.fromUrl(null))
    }

    @Test
    fun `unknown short code falls back to cloud`() {
        assertEquals(IconCode.CLOUD, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/nonsense?size=medium"))
    }

    @Test
    fun `hourly rain with under-30 probability downgrades to partly-day`() {
        assertEquals(
            IconCode.PARTLY_DAY,
            IconMapper.forHourly(
                rawUrl = "https://api.weather.gov/icons/land/day/rain,20?size=medium",
                isDay = true,
                precipProbPct = 20,
            )
        )
    }

    @Test
    fun `hourly rain with over-30 probability stays as rain`() {
        assertEquals(
            IconCode.RAIN,
            IconMapper.forHourly(
                rawUrl = "https://api.weather.gov/icons/land/day/rain,50?size=medium",
                isDay = true,
                precipProbPct = 50,
            )
        )
    }

    @Test
    fun `daily icon upgrades to rain when NWS picked sun but precip is over 50`() {
        assertEquals(
            IconCode.RAIN,
            IconMapper.forDaily(
                rawUrl = "https://api.weather.gov/icons/land/day/few?size=medium",
                shortForecast = "Slight Chance Rain Showers then Mostly Sunny",
                precipProbPct = 60,
            )
        )
    }

    @Test
    fun `daily icon prefers thunder over snow over rain on tie`() {
        assertEquals(
            IconCode.THUNDER,
            IconMapper.forDaily(
                rawUrl = "https://api.weather.gov/icons/land/day/few?size=medium",
                shortForecast = "Chance of Rain and Thunderstorms, Snow Possible",
                precipProbPct = 70,
            )
        )
    }
}
