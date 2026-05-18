# SkyFrame Android — Plan 2: Full Alert UX + Trends Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the three overlay sheets (AlertDetailSheet, ForecastNarrativeSheet, StationOverrideSheet) that complete the alert and forecast UX, plus revive the trend arrows on NowScreen by fetching observation history. Tag as `v0.2.0`.

**Architecture:** Single `HudBottomSheet` primitive built on Material3 `ModalBottomSheet` with HUD restyling. Sheet state hoisted to `DashboardScaffold` as a sealed class for mutual exclusion. New `NwsClient.recentObservations()` endpoint feeds history into `WeatherNormalizer` to populate `ConditionTrends`. Direct port of web's `alert-detail-format.ts` provides paragraph parsing.

**Tech Stack:**
- Kotlin 2.0.21 + Jetpack Compose BOM 2024.11 + Material 3 ModalBottomSheet
- Ktor 3.0.1 with OkHttp engine
- kotlinx.coroutines for parallel station preview
- JUnit 5 + MockK + Turbine + Ktor MockEngine for tests
- (All deps already in place from Plan 1; no `libs.versions.toml` changes)

**Reference spec:** [docs/superpowers/specs/2026-05-17-skyframe-android-plan-2-alert-ux-design.md](../specs/2026-05-17-skyframe-android-plan-2-alert-ux-design.md)

**Web reference codebase:** `_reference/` (gitignored) — still available for cross-checking. Especially relevant for this plan: `_reference/client/components/AlertDetailBody.tsx`, `_reference/client/components/ForecastBody.tsx`, `_reference/client/components/StationPopover.tsx`, `_reference/client/components/TerminalModal.tsx`, `_reference/client/alert-detail-format.ts`.

---

## File Structure (added/modified)

```
app/src/main/kotlin/com/skyframe/
  ui/
    sheets/                                  NEW package
      HudBottomSheet.kt                      Shared chrome primitive + HudSheetTitleBar
      SheetState.kt                          Sealed class for mutual exclusion
      AlertDetailSheet.kt                    Body for SheetState.AlertDetail
      ForecastNarrativeSheet.kt              Body for SheetState.Forecast
      StationOverrideSheet.kt                Body for SheetState.StationOverride
      StationPreview.kt                      Helper: parallel fetch of both stations
    widgets/
      ForecastButton.kt                      NEW — small ▶ trigger glyph
      HudHero.kt                             MODIFIED — add onOpenForecast callback
    screens/
      NowScreen.kt                           MODIFIED — wire ▶, fix pressure /1.0 no-op
      HourlyScreen.kt                        MODIFIED — wire ▶, fix precip-bar zero-height
      OutlookScreen.kt                       MODIFIED — make day labels clickable
    shell/
      DashboardScaffold.kt                   MODIFIED — hoist sheetState, render sheets,
                                             1Hz ticker for T-Xs countdown
      AlertBanner.kt                         MODIFIED — event-name click opens AlertDetail
      Footer.kt                              MODIFIED — LINK click opens StationOverride
  data/
    nws/
      NwsClient.kt                           MODIFIED — add recentObservations()
      NwsDtos.kt                             MODIFIED — add ObservationsListDto +
                                             ObservationFeatureDto
      WeatherNormalizer.kt                   MODIFIED — fetch history after fallback
      IconMapper.kt                          MODIFIED — add missing NWS codes
    alerts/
      AlertDescriptionFormat.kt              NEW — parseDescription + formatTime +
                                             formatAlertMeta
  viewmodel/
    DashboardViewModel.kt                    MODIFIED — add applyStationOverride()

app/src/test/kotlin/com/skyframe/
  data/
    alerts/
      AlertDescriptionFormatTest.kt          NEW
    nws/
      NwsClientTest.kt                       MODIFIED — add recentObservations URL test
      WeatherNormalizerTest.kt               NEW — orchestrator coverage (gap from Plan 1)
      IconMapperTest.kt                      MODIFIED — add 3 tests for new codes
  ui/
    sheets/
      StationPreviewTest.kt                  NEW

docs/
  PROJECT_STATUS.md                          MODIFIED — add Plan 2 features
  ROADMAP.md                                 MODIFIED — flip Plan 2 to ✅ Shipped
  SMOKE_TEST.md                              MODIFIED — extend with sheet flows
CHANGELOG.md                                 MODIFIED — v0.2.0 release notes
```

---

## Phase A — Data Layer: History Fetch

Add the `/stations/{id}/observations?limit=N` endpoint, wire it into `WeatherNormalizer`, and finally backfill the missing `WeatherNormalizerTest` from Plan 1's coverage gap. Trend arrows that `HudMetricBar` currently hides (because confidence is always MISSING) will reappear silently when history fetch succeeds.

### Task A.1: Add ObservationsListDto + ObservationFeatureDto

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/data/nws/NwsDtos.kt`

- [ ] **Step 1: Read the existing NwsDtos.kt to know where to append**

Run from project root:

```powershell
Get-Content "app/src/main/kotlin/com/skyframe/data/nws/NwsDtos.kt" | Select-Object -Last 10
```

Expected: shows the end of file with `StationsListDto`, `StationFeatureDto`, `StationFeatureProperties` data classes.

- [ ] **Step 2: Append the two new DTOs after `StationFeatureProperties`**

Append to `app/src/main/kotlin/com/skyframe/data/nws/NwsDtos.kt`:

```kotlin

// --------- /stations/{id}/observations?limit=N (history for trend computation) ---------

@Serializable
data class ObservationsListDto(val features: List<ObservationFeatureDto>)

@Serializable
data class ObservationFeatureDto(val properties: ObservationProperties)
```

`ObservationProperties` already exists from Plan 1 (used by `ObservationDto`), so the new feature wrapper just references it.

- [ ] **Step 3: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/NwsDtos.kt
git commit -m "$(@'
feat(nws): add ObservationsListDto + ObservationFeatureDto

Prep for the /stations/{id}/observations?limit=N endpoint that Plan 2
uses to revive trend arrows. Reuses existing ObservationProperties.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task A.2: Add recentObservations() to NwsClient with URL test

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/data/nws/NwsClient.kt`
- Modify: `app/src/test/kotlin/com/skyframe/data/nws/NwsClientTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `app/src/test/kotlin/com/skyframe/data/nws/NwsClientTest.kt` (before the closing brace of the test class):

```kotlin
    @Test
    fun `recentObservations builds expected URL with limit query`() = runTest {
        val (client, urls) = mockClient { """{"features":[]}""" }
        val nws = NwsClient(client)
        nws.recentObservations("KMKE", limit = 6)
        assertEquals(1, urls.size)
        assertTrue(urls[0].contains("/stations/KMKE/observations?limit=6"),
            "Expected /stations/KMKE/observations?limit=6, got ${urls[0]}")
    }

    @Test
    fun `recentObservations defaults limit to 6`() = runTest {
        val (client, urls) = mockClient { """{"features":[]}""" }
        val nws = NwsClient(client)
        nws.recentObservations("KMKE")
        assertTrue(urls[0].contains("?limit=6"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.NwsClientTest" --no-daemon
```

Expected: compile error (`recentObservations` unresolved).

- [ ] **Step 3: Add the method to NwsClient**

In `app/src/main/kotlin/com/skyframe/data/nws/NwsClient.kt`, add this method inside the class (after `activeAlerts`, before the `fmt` private function):

```kotlin
    suspend fun recentObservations(stationId: String, limit: Int = 6): ObservationsListDto =
        http.get("$base/stations/$stationId/observations?limit=$limit").body()
```

- [ ] **Step 4: Run test to verify pass**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.NwsClientTest" --no-daemon
```

Expected: all NwsClient tests pass (7 total now).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/NwsClient.kt app/src/test/kotlin/com/skyframe/data/nws/NwsClientTest.kt
git commit -m "$(@'
feat(nws): add recentObservations endpoint for trend history

Fetches up-to-N most-recent observations from a station's
/observations?limit=N. WeatherNormalizer will use this in the next
task to populate ConditionTrends so HudMetricBar's hidden trend
arrows reappear.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task A.3: Wire history fetch into WeatherNormalizer

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/data/nws/WeatherNormalizer.kt`

- [ ] **Step 1: Read the existing fetchObservationWithFallback flow**

```powershell
Get-Content "app/src/main/kotlin/com/skyframe/data/nws/WeatherNormalizer.kt" | Select-String -Pattern "fetchObservationWithFallback|ObservationNormalizer.normalize" -Context 1,3
```

This shows the call site where `recentObservations = emptyList()` is currently passed.

- [ ] **Step 2: Add the history fetch after fallback resolution**

In `app/src/main/kotlin/com/skyframe/data/nws/WeatherNormalizer.kt`, locate the line:

```kotlin
            // Station fallback: try primary first; if stale/null, try secondary.
            val (observation, activeStationId, fellBack) = fetchObservationWithFallback(cfg, now)
```

Immediately after that line (still inside the `coroutineScope { ... }` block), add:

```kotlin

            // History fetch is sequential after fallback — we don't know which
            // station ID won until fallback decides. Wrapped in runCatching for
            // partial-failure semantics (matches alerts pattern): on failure the
            // trends fall back to MISSING confidence and HudMetricBar hides the
            // arrows silently. ~200-400ms additional latency on cold loads;
            // cache hits skip it entirely.
            val history: List<ObservationDto> = runCatching {
                nws.recentObservations(activeStationId).features
                    .map { ObservationDto(properties = it.properties) }
            }.getOrDefault(emptyList())
```

- [ ] **Step 3: Change the `recentObservations = emptyList()` to `recentObservations = history`**

In the same file, change the `ObservationNormalizer.normalize` call:

```kotlin
                current = ObservationNormalizer.normalize(
                    latest = observation,
                    recentObservations = emptyList(),  // ← BEFORE
                    ...
                ),
```

to:

```kotlin
                current = ObservationNormalizer.normalize(
                    latest = observation,
                    recentObservations = history,  // ← AFTER
                    ...
                ),
```

(Leave the rest of the parameters intact — `stationDistanceKm`, `sunrise`, `sunset`, `precipOutlook`, `isDay`.)

- [ ] **Step 4: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/WeatherNormalizer.kt
git commit -m "$(@'
feat(normalizer): fetch observation history to populate ConditionTrends

History fetch is sequential after the primary-vs-fallback decision
(active station ID isn't known until fallback resolves; parallel fetch
would hit the wrong station). Wrapped in runCatching for partial-
failure semantics matching the alerts pattern: on failure trends go
back to MISSING confidence and HudMetricBar hides the arrows
silently. ~200-400ms additional latency on cold loads only; cache
hits skip the history fetch.

HudMetricBar's v0.1.1 fix #10 to hide arrows when confidence=MISSING
becomes the graceful-degradation path for history-fetch failure.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task A.4: Backfill WeatherNormalizerTest (closes Plan 1 coverage gap)

The reviewer's biggest finding from Plan 1 was that `WeatherNormalizer` had ZERO tests despite being the highest-stakes orchestrator. This task closes the gap.

**Files:**
- Create: `app/src/test/kotlin/com/skyframe/data/nws/WeatherNormalizerTest.kt`

- [ ] **Step 1: Create the test file with fixture helpers and the first three tests**

Create `app/src/test/kotlin/com/skyframe/data/nws/WeatherNormalizerTest.kt`:

```kotlin
package com.skyframe.data.nws

import com.skyframe.data.cache.WeatherCache
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.domain.StationOverride
import com.skyframe.domain.WeatherError
import com.skyframe.domain.WeatherResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeatherNormalizerTest {

    // ---------- fixtures ----------

    private fun snapshot(
        primary: String = "KMKE",
        fallback: String = "KRAC",
        override: StationOverride = StationOverride.AUTO,
    ) = SettingsRepository.Snapshot(
        email = "test@example.com",
        lat = 42.8744,
        lon = -87.8633,
        locationName = "OAK CREEK WI",
        forecastOffice = "MKX",
        gridX = 88,
        gridY = 58,
        timezone = "America/Chicago",
        forecastZone = "WIZ066",
        stationPrimary = primary,
        stationFallback = fallback,
        stationOverride = override,
    )

    private fun fakePointsDto() = PointsDto(
        properties = PointsProperties(
            gridId = "MKX",
            gridX = 88,
            gridY = 58,
            timeZone = "America/Chicago",
            forecastZone = "https://api.weather.gov/zones/forecast/WIZ066",
            observationStations = "https://api.weather.gov/gridpoints/MKX/88,58/stations",
            relativeLocation = RelativeLocation(RelativeLocationProperties("Oak Creek", "WI")),
            astronomicalData = AstronomicalDataDto(
                sunrise = "2026-05-17T05:30:00-05:00",
                sunset = "2026-05-17T20:15:00-05:00",
            ),
        )
    )

    private fun fakeForecastDto() = ForecastDto(
        properties = ForecastProperties(
            generatedAt = "2026-05-17T14:00:00Z",
            periods = listOf(
                ForecastPeriodDto(
                    number = 1, name = "Today",
                    startTime = "2026-05-17T06:00:00-05:00",
                    endTime = "2026-05-17T18:00:00-05:00",
                    isDaytime = true, temperature = 75, temperatureUnit = "F",
                    windSpeed = "5 mph", windDirection = "S",
                    icon = "https://api.weather.gov/icons/land/day/few?size=medium",
                    shortForecast = "Sunny",
                ),
                ForecastPeriodDto(
                    number = 2, name = "Tonight",
                    startTime = "2026-05-17T18:00:00-05:00",
                    endTime = "2026-05-18T06:00:00-05:00",
                    isDaytime = false, temperature = 55, temperatureUnit = "F",
                    windSpeed = "5 mph", windDirection = "S",
                    icon = "https://api.weather.gov/icons/land/night/skc?size=medium",
                    shortForecast = "Clear",
                ),
            ),
        )
    )

    private fun freshObservationDto(stationId: String = "KMKE") = ObservationDto(
        properties = ObservationProperties(
            station = "https://api.weather.gov/stations/$stationId",
            timestamp = "2026-05-17T14:00:00+00:00",
            textDescription = "Sunny",
            icon = "https://api.weather.gov/icons/land/day/skc?size=medium",
            temperature = NumberMeasurementDto(value = 22.0, unitCode = "wmoUnit:degC"),
            relativeHumidity = NumberMeasurementDto(value = 45.0, unitCode = "wmoUnit:percent"),
        )
    )

    private fun staleObservationDto(stationId: String = "KMKE") = ObservationDto(
        properties = ObservationProperties(
            station = "https://api.weather.gov/stations/$stationId",
            // 2 hours ago — exceeds the 90-min staleness threshold
            timestamp = "2026-05-17T12:00:00+00:00",
            temperature = NumberMeasurementDto(value = 22.0, unitCode = "wmoUnit:degC"),
        )
    )

    private fun emptyAlertsDto() = AlertsDto(features = emptyList())
    private fun emptyObservationsListDto() = ObservationsListDto(features = emptyList())

    private fun mockSettings(snap: SettingsRepository.Snapshot): SettingsRepository {
        val s = mockk<SettingsRepository>()
        coEvery { s.snapshot() } returns snap
        return s
    }

    // ---------- tests ----------

    @Test
    fun `happy path with fresh primary station populates all fields`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } returns emptyAlertsDto()
        coEvery { nws.latestObservation("KMKE") } returns freshObservationDto("KMKE")
        coEvery { nws.recentObservations("KMKE", any()) } returns emptyObservationsListDto()

        val cache = WeatherCache<WeatherResponse>()
        val settings = mockSettings(snapshot())
        val normalizer = WeatherNormalizer(nws, settings, cache)

        val result = normalizer.load()

        assertEquals("KMKE", result.meta.stationId)
        assertEquals(StationOverride.AUTO, result.meta.stationOverride)
        assertNull(result.meta.error, "happy path should have no meta.error")
        assertEquals(0, result.alerts.size)
    }

    @Test
    fun `stale primary observation falls back to secondary and sets STATION_FALLBACK`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } returns emptyAlertsDto()
        coEvery { nws.latestObservation("KMKE") } returns staleObservationDto("KMKE")
        coEvery { nws.latestObservation("KRAC") } returns freshObservationDto("KRAC")
        coEvery { nws.recentObservations("KRAC", any()) } returns emptyObservationsListDto()

        val cache = WeatherCache<WeatherResponse>()
        val normalizer = WeatherNormalizer(nws, mockSettings(snapshot()), cache)

        val result = normalizer.load()

        assertEquals("KRAC", result.meta.stationId)
        assertEquals(WeatherError.STATION_FALLBACK, result.meta.error)
    }

    @Test
    fun `force-secondary override skips primary entirely`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } returns emptyAlertsDto()
        coEvery { nws.latestObservation("KRAC") } returns freshObservationDto("KRAC")
        coEvery { nws.recentObservations("KRAC", any()) } returns emptyObservationsListDto()

        val cache = WeatherCache<WeatherResponse>()
        val normalizer = WeatherNormalizer(
            nws,
            mockSettings(snapshot(override = StationOverride.FORCE_SECONDARY)),
            cache,
        )

        val result = normalizer.load()

        assertEquals("KRAC", result.meta.stationId)
        // Verify primary was never called by checking the mock has no record of it.
        io.mockk.coVerify(exactly = 0) { nws.latestObservation("KMKE") }
    }
}
```

- [ ] **Step 2: Run first batch of tests to verify they pass**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.WeatherNormalizerTest" --no-daemon
```

Expected: 3 tests pass.

- [ ] **Step 3: Append the remaining three tests for failure/partial paths**

Add these test methods inside the `WeatherNormalizerTest` class (before the closing brace):

```kotlin
    @Test
    fun `alerts fetch failure becomes meta error PARTIAL with empty alerts`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } throws RuntimeException("503 from /alerts/active")
        coEvery { nws.latestObservation("KMKE") } returns freshObservationDto("KMKE")
        coEvery { nws.recentObservations("KMKE", any()) } returns emptyObservationsListDto()

        val cache = WeatherCache<WeatherResponse>()
        val normalizer = WeatherNormalizer(nws, mockSettings(snapshot()), cache)

        val result = normalizer.load()

        // Forecast still rendered; alerts gone; meta.error flagged.
        assertEquals(WeatherError.PARTIAL, result.meta.error)
        assertEquals(0, result.alerts.size)
        assertNotNull(result.current)
    }

    @Test
    fun `history fetch failure leaves trends as MISSING confidence`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } returns emptyAlertsDto()
        coEvery { nws.latestObservation("KMKE") } returns freshObservationDto("KMKE")
        coEvery { nws.recentObservations("KMKE", any()) } throws RuntimeException("503 from /observations")

        val cache = WeatherCache<WeatherResponse>()
        val normalizer = WeatherNormalizer(nws, mockSettings(snapshot()), cache)

        val result = normalizer.load()

        // History failure does NOT escalate to meta.error — graceful degradation.
        assertNull(result.meta.error)
        // All trends should be MISSING confidence since history is empty.
        assertEquals(com.skyframe.domain.TrendConfidence.MISSING, result.current.trends.temp.confidence)
        assertEquals(com.skyframe.domain.TrendConfidence.MISSING, result.current.trends.humidity.confidence)
    }

    @Test
    fun `second load within TTL returns cached response with cacheHit=true`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } returns emptyAlertsDto()
        coEvery { nws.latestObservation("KMKE") } returns freshObservationDto("KMKE")
        coEvery { nws.recentObservations("KMKE", any()) } returns emptyObservationsListDto()

        val cache = WeatherCache<WeatherResponse>()
        val normalizer = WeatherNormalizer(nws, mockSettings(snapshot()), cache)

        val first = normalizer.load()
        val second = normalizer.load()  // Within TTL → cache hit

        assertTrue(!first.meta.cacheHit, "first load should be a fresh fetch")
        assertTrue(second.meta.cacheHit, "second load within TTL should be a cache hit")
        io.mockk.coVerify(exactly = 1) { nws.points(any(), any()) }
    }
```

- [ ] **Step 4: Run all WeatherNormalizer tests to verify pass**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.WeatherNormalizerTest" --no-daemon
```

Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/test/kotlin/com/skyframe/data/nws/WeatherNormalizerTest.kt
git commit -m "$(@'
test(normalizer): backfill WeatherNormalizer orchestrator coverage

Closes the biggest gap from Plan 1's final code review: WeatherNormalizer
had ZERO tests despite being the highest-stakes data class. Six new tests:
- happy path with fresh primary station
- stale primary triggers fallback to secondary + STATION_FALLBACK
- force-secondary override skips primary entirely
- alerts fetch failure becomes meta.error=PARTIAL with empty alerts
- history fetch failure leaves trends at MISSING confidence (graceful)
- TTL cache hit on second load within 90s

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase A milestone:** History fetch is live in `WeatherNormalizer`. `ConditionTrends` now populates with real OLS-computed deltas (when NWS returns at least 3 observations). `HudMetricBar` trend arrows will appear on the next app launch with real data. `WeatherNormalizerTest` closes the orchestrator coverage gap.

Total test count after Phase A: ~104 (up from 96).

---

## Phase B — AlertDescriptionFormat helper

Direct port of `_reference/client/alert-detail-format.ts`. Pure logic, fully tested via TDD. Three exposed functions: `parseDescription`, `formatTime`, `formatAlertMeta`.

### Task B.1: AlertDescriptionFormat with full test coverage

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/alerts/AlertDescriptionFormat.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/alerts/AlertDescriptionFormatTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/skyframe/data/alerts/AlertDescriptionFormatTest.kt`:

```kotlin
package com.skyframe.data.alerts

import com.skyframe.data.alerts.AlertDescriptionParagraph.Plain
import com.skyframe.data.alerts.AlertDescriptionParagraph.Prefix
import com.skyframe.data.alerts.AlertDescriptionParagraph.Tagged
import com.skyframe.domain.Alert
import com.skyframe.domain.AlertSeverity
import com.skyframe.domain.AlertTier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlertDescriptionFormatTest {

    @Test
    fun `empty input returns empty list`() {
        assertEquals(emptyList<AlertDescriptionParagraph>(), AlertDescriptionFormat.parseDescription(""))
    }

    @Test
    fun `single plain paragraph emits one Plain`() {
        val result = AlertDescriptionFormat.parseDescription("A tornado has been spotted near downtown.")
        assertEquals(1, result.size)
        assertEquals(Plain("A tornado has been spotted near downtown."), result[0])
    }

    @Test
    fun `HAZARD prefix is stripped and tagged`() {
        val result = AlertDescriptionFormat.parseDescription("HAZARD...Tornado.")
        assertEquals(1, result.size)
        assertEquals(Tagged(Prefix.HAZARD, "Tornado."), result[0])
    }

    @Test
    fun `SOURCE and IMPACT prefixes also tagged`() {
        val result = AlertDescriptionFormat.parseDescription(
            "SOURCE...National Weather Service.\n\nIMPACT...Flying debris."
        )
        assertEquals(2, result.size)
        assertEquals(Tagged(Prefix.SOURCE, "National Weather Service."), result[0])
        assertEquals(Tagged(Prefix.IMPACT, "Flying debris."), result[1])
    }

    @Test
    fun `paragraphs separated by double newlines`() {
        val result = AlertDescriptionFormat.parseDescription("First paragraph.\n\nSecond paragraph.")
        assertEquals(2, result.size)
        assertEquals(Plain("First paragraph."), result[0])
        assertEquals(Plain("Second paragraph."), result[1])
    }

    @Test
    fun `CRLF line endings normalized`() {
        val result = AlertDescriptionFormat.parseDescription("First.\r\n\r\nSecond.")
        assertEquals(2, result.size)
        assertEquals(Plain("Second."), result[1])
    }

    @Test
    fun `mixed prefix and plain paragraphs preserved in order`() {
        val raw = "Setup paragraph.\n\nHAZARD...Tornado.\n\nMore detail."
        val result = AlertDescriptionFormat.parseDescription(raw)
        assertEquals(3, result.size)
        assertEquals(Plain("Setup paragraph."), result[0])
        assertEquals(Tagged(Prefix.HAZARD, "Tornado."), result[1])
        assertEquals(Plain("More detail."), result[2])
    }

    @Test
    fun `formatTime renders 12-hour clock with TZ abbreviation`() {
        // 2026-05-17T19:30:00Z = 14:30 in America/Chicago (CDT, UTC-5)
        val instant = Instant.parse("2026-05-17T19:30:00Z")
        val result = AlertDescriptionFormat.formatTime(instant, TimeZone.of("America/Chicago"))
        // Expect something like "2:30 PM CDT" — uppercased
        assertEquals(true, result.contains("2:30"), "expected 2:30 in output, got $result")
        assertEquals(true, result.endsWith("CDT") || result.endsWith("PM"),
            "expected CDT or AM/PM suffix, got $result")
    }

    @Test
    fun `formatAlertMeta has ISSUED EXPIRES AREA structure`() {
        val alert = Alert(
            id = "urn:oid:test",
            event = "Tornado Warning",
            tier = AlertTier.TORNADO_WARNING,
            severity = AlertSeverity.EXTREME,
            headline = "Tornado",
            description = "...",
            issuedAt = Instant.parse("2026-05-17T19:30:00Z"),
            effective = Instant.parse("2026-05-17T19:30:00Z"),
            expires = Instant.parse("2026-05-17T20:00:00Z"),
            areaDesc = "Milwaukee County",
        )
        val result = AlertDescriptionFormat.formatAlertMeta(alert, TimeZone.of("America/Chicago"))
        assertEquals(true, result.startsWith("ISSUED "), "expected ISSUED prefix, got $result")
        assertEquals(true, result.contains(" · EXPIRES "), "expected EXPIRES separator, got $result")
        assertEquals(true, result.endsWith("MILWAUKEE COUNTY"), "expected uppercase area suffix, got $result")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.alerts.AlertDescriptionFormatTest" --no-daemon
```

Expected: compile error (`AlertDescriptionFormat` and `AlertDescriptionParagraph` unresolved).

- [ ] **Step 3: Implement AlertDescriptionFormat**

Create `app/src/main/kotlin/com/skyframe/data/alerts/AlertDescriptionFormat.kt`:

```kotlin
package com.skyframe.data.alerts

import com.skyframe.domain.Alert
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

/**
 * Parsed paragraph of an NWS alert description. NWS alerts use ALL CAPS
 * "HAZARD...", "SOURCE...", "IMPACT..." section prefixes that we strip
 * and tag so the UI can render them tier-colored. Other paragraphs render
 * plain in body color.
 */
sealed class AlertDescriptionParagraph {
    abstract val text: String

    data class Tagged(val prefix: Prefix, override val text: String) : AlertDescriptionParagraph()
    data class Plain(override val text: String) : AlertDescriptionParagraph()

    enum class Prefix { HAZARD, SOURCE, IMPACT }
}

/**
 * Port of _reference/client/alert-detail-format.ts. Pure functions — no
 * Android dependencies beyond kotlinx.datetime.
 */
object AlertDescriptionFormat {

    private val PREFIX_RE = Regex("""^(HAZARD|SOURCE|IMPACT)\.\.\.\s*""")

    fun parseDescription(raw: String): List<AlertDescriptionParagraph> {
        if (raw.isEmpty()) return emptyList()
        return raw.replace("\r\n", "\n")
            .split(Regex("""\n{2,}"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { chunk ->
                val match = PREFIX_RE.find(chunk)
                if (match != null) {
                    val prefix = AlertDescriptionParagraph.Prefix.valueOf(match.groupValues[1])
                    AlertDescriptionParagraph.Tagged(prefix, chunk.substring(match.range.last + 1))
                } else {
                    AlertDescriptionParagraph.Plain(chunk)
                }
            }
    }

    /**
     * Renders an Instant as "2:30 PM CDT" in the supplied timezone. Uppercased.
     * Uses Locale.US to keep AM/PM and month/weekday names in English regardless
     * of device locale — NWS responses are English-only anyway.
     */
    fun formatTime(instant: Instant, tz: TimeZone): String {
        val ldt = instant.toLocalDateTime(tz)
        val hour12 = ((ldt.hour + 11) % 12) + 1
        val ampm = if (ldt.hour < 12) "AM" else "PM"
        val minute = ldt.minute.toString().padStart(2, '0')
        // TZ abbreviation: use the Java zone's short display name in standard or daylight time.
        val zone = java.util.TimeZone.getTimeZone(tz.id)
        val isDst = zone.inDaylightTime(java.util.Date(instant.toEpochMilliseconds()))
        val tzAbbr = zone.getDisplayName(isDst, java.util.TimeZone.SHORT, Locale.US)
        return "$hour12:$minute $ampm $tzAbbr"
    }

    fun formatAlertMeta(alert: Alert, tz: TimeZone): String {
        val issued = formatTime(alert.issuedAt, tz)
        val expires = formatTime(alert.expires, tz)
        return "ISSUED $issued · EXPIRES $expires · ${alert.areaDesc.uppercase()}"
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.alerts.AlertDescriptionFormatTest" --no-daemon
```

Expected: 9 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/alerts/AlertDescriptionFormat.kt app/src/test/kotlin/com/skyframe/data/alerts/AlertDescriptionFormatTest.kt
git commit -m "$(@'
feat(alerts): port AlertDescriptionFormat from web project

Pure-logic port of _reference/client/alert-detail-format.ts:
- parseDescription splits on \n{2,} and tags HAZARD/SOURCE/IMPACT
  prefix paragraphs while leaving non-prefix paragraphs Plain.
- formatTime renders Instant as "2:30 PM CDT" in supplied TZ.
- formatAlertMeta combines into "ISSUED ... · EXPIRES ... · AREA".

Used by AlertDetailSheet (next phase). isUpdateAlert special-case
deferred to Plan 3 when GitHub release polling lands.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase B milestone:** Pure-logic helper ready for the AlertDetailSheet to consume. Total tests: ~113.

---

## Phase C — Shared Sheet Chrome

The `HudBottomSheet` primitive that all three sheets wrap. Plus `SheetState` sealed class and the `ForecastButton` widget.

### Task C.1: SheetState sealed class

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/sheets/SheetState.kt`

- [ ] **Step 1: Create the sheets/ package directory**

```powershell
New-Item -ItemType Directory -Force -Path "app/src/main/kotlin/com/skyframe/ui/sheets" | Out-Null
New-Item -ItemType Directory -Force -Path "app/src/test/kotlin/com/skyframe/ui/sheets" | Out-Null
"ok"
```

- [ ] **Step 2: Write SheetState.kt**

Create `app/src/main/kotlin/com/skyframe/ui/sheets/SheetState.kt`:

```kotlin
package com.skyframe.ui.sheets

import com.skyframe.domain.Alert
import com.skyframe.domain.DailyPeriod

/**
 * Mutual-exclusion sheet state hoisted to DashboardScaffold. Only one sheet
 * can be open at a time by construction; each trigger sets sheetState =
 * SheetState.Foo(...) and dismissal goes back to None.
 */
sealed class SheetState {
    data object None : SheetState()
    data class AlertDetail(val alert: Alert) : SheetState()
    data class Forecast(val day: DailyPeriod) : SheetState()
    data object StationOverride : SheetState()
}
```

- [ ] **Step 3: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/sheets/SheetState.kt
git commit -m "$(@'
feat(sheets): add SheetState sealed class for mutual exclusion

Hoisted to DashboardScaffold in a later task. Sealed class prevents
two sheets opening at once and makes the sheet-render dispatch a
total function.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task C.2: HudBottomSheet primitive

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/sheets/HudBottomSheet.kt`

- [ ] **Step 1: Write HudBottomSheet.kt**

Create `app/src/main/kotlin/com/skyframe/ui/sheets/HudBottomSheet.kt`:

```kotlin
package com.skyframe.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent

/**
 * Shared bottom-sheet chrome for all three Plan 2 sheets. Wraps Material3's
 * ModalBottomSheet (which gives us swipe-to-dismiss, scrim, system back,
 * accessibility focus management) while overriding the visual chrome:
 *  - container color: HudColors.BackgroundPanel
 *  - drag handle: removed (we render our own title bar instead)
 *  - corner shape: rectangular (HUD is angular, not rounded)
 *  - top border: 2dp accent stripe
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = LocalHudAccent.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = HudColors.BackgroundPanel,
        dragHandle = null,
        shape = RectangleShape,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // 2dp accent top border
                    drawLine(
                        color = accent.accent,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f,
                    )
                },
        ) {
            HudSheetTitleBar(title = title, onClose = onDismissRequest)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun HudSheetTitleBar(title: String, onClose: () -> Unit) {
    val accent = LocalHudAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(HudColors.BackgroundDeep)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "TERMINAL // $title",
            color = accent.accent,
            style = HudType.titleBar,
        )
        Text(
            text = "[x]",
            color = accent.accent,
            style = HudType.titleBar,
            modifier = Modifier
                .clickable { onClose() }
                .padding(horizontal = 8.dp),
        )
    }
}
```

- [ ] **Step 2: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/sheets/HudBottomSheet.kt
git commit -m "$(@'
feat(sheets): add HudBottomSheet shared chrome primitive

Wraps Material3 ModalBottomSheet (free swipe-to-dismiss, scrim, system
back integration, accessibility focus management) with HUD restyling:
HudColors.BackgroundPanel container, rectangular shape (no rounded
corners), 2dp accent top border, custom TERMINAL // {TITLE} title bar
with [x] close glyph.

All three Plan 2 sheets (AlertDetail, ForecastNarrative, StationOverride)
use this primitive — same chrome, different bodies.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task C.3: ForecastButton widget

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/widgets/ForecastButton.kt`

- [ ] **Step 1: Write ForecastButton.kt**

Create `app/src/main/kotlin/com/skyframe/ui/widgets/ForecastButton.kt`:

```kotlin
package com.skyframe.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent

/**
 * Small ▶ trigger glyph used by NowScreen (next to TEMP/FEEL label),
 * HourlyScreen (next to NEXT 12H header), and any other surface that
 * wants to open ForecastNarrativeSheet.
 */
@Composable
fun ForecastButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "▶",
        color = LocalHudAccent.current.accent,
        style = HudType.metricLabel,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
```

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/widgets/ForecastButton.kt
git commit -m "$(@'
feat(ui): ForecastButton ▶ trigger glyph

Small accent-colored ▶ used to open ForecastNarrativeSheet from
NowScreen (next to TEMP/FEEL) and HourlyScreen (next to NEXT 12H
header). Tap target padded to ~16dp for thumb-friendliness.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase C milestone:** Shared chrome and `ForecastButton` ready. Three sheets can now be built on `HudBottomSheet` knowing the title bar + dismissal + scrim work identically.

---

## Phase D — AlertDetailSheet

### Task D.1: AlertDetailSheet body

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/sheets/AlertDetailSheet.kt`

- [ ] **Step 1: Write AlertDetailSheet.kt**

Create `app/src/main/kotlin/com/skyframe/ui/sheets/AlertDetailSheet.kt`:

```kotlin
package com.skyframe.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.data.alerts.AlertDescriptionFormat
import com.skyframe.data.alerts.AlertDescriptionParagraph
import com.skyframe.domain.Alert
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import kotlinx.datetime.TimeZone

/**
 * Renders an NWS alert's full description as parsed paragraphs with
 * HAZARD/SOURCE/IMPACT prefixes tier-colored using the alert's OWN tier
 * color (not the dashboard's active accent).
 */
@Composable
fun AlertDetailSheet(
    alert: Alert,
    timezone: String,
    onDismiss: () -> Unit,
) {
    val tz = remember(timezone) {
        runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.currentSystemDefault())
    }
    val tierColor = Color(alert.tier.baseColor)
    val paragraphs = remember(alert.description) {
        AlertDescriptionFormat.parseDescription(alert.description)
    }
    val meta = remember(alert, tz) { AlertDescriptionFormat.formatAlertMeta(alert, tz) }

    HudBottomSheet(title = "ALERT DETAIL", onDismissRequest = onDismiss) {
        // Event name in alert's tier color
        Text(
            text = alert.event.uppercase(),
            color = tierColor,
            style = HudType.titleBar,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )

        // Meta line
        Text(
            text = meta,
            color = HudColors.ForegroundDim,
            style = HudType.metaLabel,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(tierColor.copy(alpha = 0.3f))
                .padding(bottom = 12.dp),
        )

        // Paragraphs scrollable
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
        ) {
            paragraphs.forEach { para ->
                when (para) {
                    is AlertDescriptionParagraph.Tagged -> {
                        Row(modifier = Modifier.padding(bottom = 12.dp)) {
                            Text(
                                text = "[${para.prefix.name}] ",
                                color = tierColor,
                                style = HudType.metaLabel,
                            )
                            Text(
                                text = para.text,
                                color = HudColors.Foreground,
                                style = HudType.bodyMono,
                            )
                        }
                    }
                    is AlertDescriptionParagraph.Plain -> {
                        Text(
                            text = para.text,
                            color = HudColors.Foreground,
                            style = HudType.bodyMono,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/sheets/AlertDetailSheet.kt
git commit -m "$(@'
feat(sheets): AlertDetailSheet body

Tier-colored event name + meta line (ISSUED · EXPIRES · AREA) +
divider + scrollable paragraphs. HAZARD/SOURCE/IMPACT prefixes
render in the alert's own tier color (not the dashboard's active
accent) so they're consistently distinguishable even when the
dashboard has already shifted to the same color family.

Wiring to AlertBanner + DashboardScaffold lands in next two tasks.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task D.2: Wire AlertBanner event-name click to open AlertDetail

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/shell/AlertBanner.kt`

- [ ] **Step 1: Read AlertBanner to find the event-name Text widgets**

```powershell
Get-Content "app/src/main/kotlin/com/skyframe/ui/shell/AlertBanner.kt" | Select-String -Pattern "top.event|alert.event" -Context 1,2
```

There are two: the top alert's event name (always visible) and each expanded alert's event name (in the expand-toggle list).

- [ ] **Step 2: Add an onAlertClick parameter to AlertBanner**

Edit the function signature in `app/src/main/kotlin/com/skyframe/ui/shell/AlertBanner.kt`:

```kotlin
@Composable
fun AlertBanner(
    alerts: List<Alert>,
    onDismiss: (String) -> Unit,
    onAlertClick: (Alert) -> Unit,    // ← NEW
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 3: Make the top event-name Text clickable**

Find the top alert's event Text:

```kotlin
            Text(
                text = top.event.uppercase(),
                color = tierAccent,
                style = HudType.titleBar,
                modifier = Modifier.weight(1f),
            )
```

Change `modifier` to add a clickable:

```kotlin
            Text(
                text = top.event.uppercase(),
                color = tierAccent,
                style = HudType.titleBar,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAlertClick(top) },
            )
```

Add the import if needed: `import androidx.compose.foundation.clickable`.

- [ ] **Step 4: Make expanded-list event Text rows clickable too**

Find the expanded list's per-alert row (inside `if (expanded) { alerts.drop(1).forEach { alert -> ... } }`):

```kotlin
                    Text(
                        text = alert.event.uppercase(),
                        color = Color(alert.tier.baseColor),
                        style = HudType.metaLabel,
                        modifier = Modifier.weight(1f),
                    )
```

Change to:

```kotlin
                    Text(
                        text = alert.event.uppercase(),
                        color = Color(alert.tier.baseColor),
                        style = HudType.metaLabel,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAlertClick(alert) },
                    )
```

- [ ] **Step 5: Verify compile (DashboardScaffold callsite will break — fix in next task)**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | Select-Object -Last 6
```

Expected: error in `DashboardScaffold.kt` about missing `onAlertClick` argument. That's fixed in D.3.

**No commit yet — combined with D.3.**

---

### Task D.3: Wire AlertDetail into DashboardScaffold sheet state

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt`

- [ ] **Step 1: Add SheetState import and remember-state at top of DashboardScaffold**

In `app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt`, add the import:

```kotlin
import com.skyframe.ui.sheets.AlertDetailSheet
import com.skyframe.ui.sheets.SheetState
```

Locate where `var selected by remember { mutableStateOf(DashboardDestination.NOW) }` is defined and add immediately after:

```kotlin
    var sheetState by remember { mutableStateOf<SheetState>(SheetState.None) }
```

- [ ] **Step 2: Pass onAlertClick to AlertBanner**

Find the `AlertBanner(` call and add the `onAlertClick` parameter:

```kotlin
            AlertBanner(
                alerts = ui.visibleAlerts,
                onDismiss = { id -> viewModel.dismissAlert(id) },
                onAlertClick = { alert -> sheetState = SheetState.AlertDetail(alert) },  // ← NEW
            )
```

- [ ] **Step 3: Render the sheet when state matches**

After the `Column { ... }` block that contains the existing UI layers, immediately before the function's closing brace of the `HudTheme { ... }` block, add the sheet dispatch:

```kotlin
        // Sheet dispatch — only one sheet open at a time per SheetState sealed class.
        when (val s = sheetState) {
            SheetState.None -> Unit
            is SheetState.AlertDetail -> AlertDetailSheet(
                alert = s.alert,
                timezone = ui.timezone,
                onDismiss = { sheetState = SheetState.None },
            )
            // SheetState.Forecast and SheetState.StationOverride handled in Phase E/F
            else -> Unit
        }
```

- [ ] **Step 4: Verify compile + build APK**

```powershell
./gradlew :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 6
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit D.2 + D.3 together**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/shell/AlertBanner.kt app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt
git commit -m "$(@'
feat(ui): wire AlertDetailSheet open via AlertBanner tap

AlertBanner gets an onAlertClick(alert) callback; both the top
alert's event name and each expanded-list alert row are now
clickable. DashboardScaffold hoists sheetState as SheetState sealed
class (mutually exclusive) and renders AlertDetailSheet when
state is AlertDetail. SheetState.Forecast and StationOverride
get their dispatch arms in Phases E and F.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase D milestone:** Tap an alert event name → AlertDetailSheet opens with full NWS description in tier-colored paragraphs. First fully wired sheet in the app.

---

## Phase E — ForecastNarrativeSheet

### Task E.1: ForecastNarrativeSheet body

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/sheets/ForecastNarrativeSheet.kt`

- [ ] **Step 1: Write ForecastNarrativeSheet.kt**

Create `app/src/main/kotlin/com/skyframe/ui/sheets/ForecastNarrativeSheet.kt`:

```kotlin
package com.skyframe.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skyframe.domain.DailyPeriod
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent

/**
 * Renders a DailyPeriod's day + night detailed forecast strings, each under
 * an NWS-preserved period-name section header (THIS AFTERNOON, TONIGHT,
 * FRIDAY, FRIDAY NIGHT, etc.). Orphan halves (day-only at window end,
 * night-only at window start) just render the populated half.
 */
@Composable
fun ForecastNarrativeSheet(
    day: DailyPeriod,
    onDismiss: () -> Unit,
) {
    val accent = LocalHudAccent.current.accent
    HudBottomSheet(title = "FORECAST", onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 12.dp),
        ) {
            day.dayPeriodName?.let { name ->
                Text(
                    text = "┌ ${name.uppercase()} ┐",
                    color = accent,
                    style = HudType.sectionHeader,
                    modifier = Modifier.padding(bottom = 4.dp, top = 4.dp),
                )
                day.dayDetailedForecast?.let { text ->
                    Text(
                        text = text,
                        color = HudColors.Foreground,
                        style = HudType.bodyMono,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
            day.nightPeriodName?.let { name ->
                Text(
                    text = "┌ ${name.uppercase()} ┐",
                    color = accent,
                    style = HudType.sectionHeader,
                    modifier = Modifier.padding(bottom = 4.dp, top = 4.dp),
                )
                day.nightDetailedForecast?.let { text ->
                    Text(
                        text = text,
                        color = HudColors.Foreground,
                        style = HudType.bodyMono,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/sheets/ForecastNarrativeSheet.kt
git commit -m "$(@'
feat(sheets): ForecastNarrativeSheet body

Renders DailyPeriod.dayDetailedForecast + nightDetailedForecast
stacked with NWS-preserved period names (THIS AFTERNOON, TONIGHT,
FRIDAY, FRIDAY NIGHT) as section headers in the active accent
color. Orphan halves emit only the populated section.

Wiring to NowScreen ▶, HourlyScreen ▶, OutlookScreen day labels
+ DashboardScaffold dispatch arm lands in next task.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task E.2: Wire ForecastButton ▶ into NowScreen + HudHero

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/widgets/HudHero.kt`
- Modify: `app/src/main/kotlin/com/skyframe/ui/screens/NowScreen.kt`

- [ ] **Step 1: Add onOpenForecast callback to HudHero**

Find HudHero's signature and the TEMP/FEEL Text:

```kotlin
@Composable
fun HudHero(
    current: CurrentConditions,
    tempUnit: TempUnit,
    accent: Color,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

Add a parameter:

```kotlin
@Composable
fun HudHero(
    current: CurrentConditions,
    tempUnit: TempUnit,
    accent: Color,
    onToggleUnit: () -> Unit,
    onOpenForecast: () -> Unit,         // ← NEW
    modifier: Modifier = Modifier,
) {
```

Find the TEMP/FEEL line:

```kotlin
            Text(
                text = "TEMP / FEEL  $feel°$unitSuffix",
                color = HudColors.ForegroundDim,
                style = HudType.heroFeel,
            )
```

Wrap it in a Row that places the ForecastButton next to it. Replace the surrounding Column with:

```kotlin
        Column(
            modifier = Modifier.clickable { onToggleUnit() }
        ) {
            HudGlowText(
                text = "$temp°",
                color = accent,
                style = HudType.heroTemp,
            )
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = "TEMP / FEEL  $feel°$unitSuffix",
                    color = HudColors.ForegroundDim,
                    style = HudType.heroFeel,
                )
                ForecastButton(onClick = onOpenForecast)
            }
        }
```

Add the import:

```kotlin
import com.skyframe.ui.widgets.ForecastButton
```

Wait — HudHero is already in `com.skyframe.ui.widgets`. Same package so no import needed. Skip the import.

- [ ] **Step 2: Update NowScreen to pass onOpenForecast**

In `app/src/main/kotlin/com/skyframe/ui/screens/NowScreen.kt`, find the `HudHero(` call inside `NowContent`:

```kotlin
        HudHero(
            current = current,
            tempUnit = tempUnit,
            accent = accent,
            onToggleUnit = {
                tempUnit = if (tempUnit == TempUnit.FAHRENHEIT) TempUnit.CELSIUS else TempUnit.FAHRENHEIT
            },
        )
```

Add the onOpenForecast callback (passed down through `NowScreen`'s caller):

```kotlin
        HudHero(
            current = current,
            tempUnit = tempUnit,
            accent = accent,
            onToggleUnit = {
                tempUnit = if (tempUnit == TempUnit.FAHRENHEIT) TempUnit.CELSIUS else TempUnit.FAHRENHEIT
            },
            onOpenForecast = onOpenForecast,    // ← NEW
        )
```

And update `NowContent`'s signature to accept it:

```kotlin
@Composable
private fun NowContent(current: CurrentConditions, onOpenForecast: () -> Unit) {
```

And update `NowScreen`'s signature:

```kotlin
@Composable
fun NowScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenForecast: (DailyPeriod) -> Unit,     // ← NEW
    modifier: Modifier = Modifier,
) {
```

Inside `NowScreen`, find the Success-state branch where `NowContent(weather.response.current)` is called and update to:

```kotlin
            is WeatherState.Success -> {
                val today = weather.response.daily.firstOrNull()
                NowContent(
                    current = weather.response.current,
                    onOpenForecast = { today?.let(onOpenForecast) },
                )
            }
```

Add import: `import com.skyframe.domain.DailyPeriod`.

- [ ] **Step 3: Verify compile (DashboardScaffold will break — fix in E.3)**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | Select-Object -Last 6
```

Expected: error about missing `onOpenForecast` argument in DashboardScaffold's `NowScreen(...)` call. Fixed in E.3.

**No commit yet.**

---

### Task E.3: Wire HourlyScreen + OutlookScreen + DashboardScaffold dispatch

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/screens/HourlyScreen.kt`
- Modify: `app/src/main/kotlin/com/skyframe/ui/screens/OutlookScreen.kt`
- Modify: `app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt`

- [ ] **Step 1: Add onOpenForecast to HourlyScreen + wire ▶ next to NEXT 12H header**

In `app/src/main/kotlin/com/skyframe/ui/screens/HourlyScreen.kt`, update the signature:

```kotlin
@Composable
fun HourlyScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenForecast: (DailyPeriod) -> Unit,    // ← NEW
    modifier: Modifier = Modifier,
) {
```

Add import: `import com.skyframe.domain.DailyPeriod` and `import com.skyframe.ui.widgets.ForecastButton`.

Update `HourlyContent` to accept the callback too:

```kotlin
@Composable
private fun HourlyContent(
    periods: List<HourlyPeriod>,
    accent: Color,
    onOpenForecast: () -> Unit,                // ← NEW
    modifier: Modifier,
) {
```

Find the "NEXT 12H" header Text:

```kotlin
        Text("NEXT 12H", color = HudColors.ForegroundDim, style = HudType.sectionHeader)
```

Replace with a Row that includes ForecastButton:

```kotlin
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("NEXT 12H", color = HudColors.ForegroundDim, style = HudType.sectionHeader)
            ForecastButton(onClick = onOpenForecast)
        }
```

And in the Success branch of `HourlyScreen`, update the call to pass `today`:

```kotlin
    when (val weather = state.weather) {
        is WeatherState.Success -> {
            val today = weather.response.daily.firstOrNull()
            HourlyContent(
                periods = weather.response.hourly,
                accent = accent,
                onOpenForecast = { today?.let(onOpenForecast) },
                modifier = modifier,
            )
        }
        // ... error/loading branches unchanged
```

- [ ] **Step 2: Add onOpenForecast to OutlookScreen + make day labels clickable**

In `app/src/main/kotlin/com/skyframe/ui/screens/OutlookScreen.kt`, update the signature:

```kotlin
@Composable
fun OutlookScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenForecast: (DailyPeriod) -> Unit,    // ← NEW
    modifier: Modifier = Modifier,
) {
```

Update `OutlookContent`:

```kotlin
@Composable
private fun OutlookContent(
    periods: List<DailyPeriod>,
    accent: Color,
    onOpenForecast: (DailyPeriod) -> Unit,    // ← NEW
    modifier: Modifier,
) {
```

Find the day-name Text in the per-row Row:

```kotlin
                Text(
                    text = p.dayOfWeek,
                    color = accent,
                    style = HudType.metricValue,
                    modifier = Modifier.padding(end = 8.dp).fillMaxWidth(0.20f),
                )
```

Add a clickable:

```kotlin
                Text(
                    text = p.dayOfWeek,
                    color = accent,
                    style = HudType.metricValue,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .fillMaxWidth(0.20f)
                        .clickable { onOpenForecast(p) },
                )
```

Add import: `import androidx.compose.foundation.clickable`.

In the Success branch:

```kotlin
        is WeatherState.Success -> OutlookContent(w.response.daily, accent, onOpenForecast, modifier)
```

- [ ] **Step 3: Update DashboardScaffold to pass onOpenForecast + add Forecast dispatch arm**

In `app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt`, find the screen dispatch:

```kotlin
            Box(modifier = Modifier.weight(1f)) {
                when (selected) {
                    DashboardDestination.NOW -> NowScreen(state = ui, onRefresh = viewModel::refresh)
                    DashboardDestination.HOURLY -> HourlyScreen(state = ui, onRefresh = viewModel::refresh)
                    DashboardDestination.OUTLOOK -> OutlookScreen(state = ui, onRefresh = viewModel::refresh)
                }
            }
```

Replace with:

```kotlin
            Box(modifier = Modifier.weight(1f)) {
                val openForecast: (com.skyframe.domain.DailyPeriod) -> Unit = { day ->
                    sheetState = SheetState.Forecast(day)
                }
                when (selected) {
                    DashboardDestination.NOW -> NowScreen(
                        state = ui, onRefresh = viewModel::refresh, onOpenForecast = openForecast,
                    )
                    DashboardDestination.HOURLY -> HourlyScreen(
                        state = ui, onRefresh = viewModel::refresh, onOpenForecast = openForecast,
                    )
                    DashboardDestination.OUTLOOK -> OutlookScreen(
                        state = ui, onRefresh = viewModel::refresh, onOpenForecast = openForecast,
                    )
                }
            }
```

Replace the sheet dispatch's `else -> Unit` with the Forecast arm:

```kotlin
        // Sheet dispatch — only one sheet open at a time per SheetState sealed class.
        when (val s = sheetState) {
            SheetState.None -> Unit
            is SheetState.AlertDetail -> AlertDetailSheet(
                alert = s.alert,
                timezone = ui.timezone,
                onDismiss = { sheetState = SheetState.None },
            )
            is SheetState.Forecast -> com.skyframe.ui.sheets.ForecastNarrativeSheet(
                day = s.day,
                onDismiss = { sheetState = SheetState.None },
            )
            SheetState.StationOverride -> Unit   // wired in Phase F
        }
```

- [ ] **Step 4: Verify compile + build APK**

```powershell
./gradlew :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 6
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit E.2 + E.3 together**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/widgets/HudHero.kt app/src/main/kotlin/com/skyframe/ui/screens/NowScreen.kt app/src/main/kotlin/com/skyframe/ui/screens/HourlyScreen.kt app/src/main/kotlin/com/skyframe/ui/screens/OutlookScreen.kt app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt
git commit -m "$(@'
feat(ui): wire ForecastNarrativeSheet via 3 triggers

NowScreen + HudHero get ForecastButton ▶ next to TEMP/FEEL —
opens today's DailyPeriod.

HourlyScreen gets ForecastButton ▶ next to NEXT 12H header —
also opens today's DailyPeriod (since the chart shows today).

OutlookScreen day-of-week labels are now clickable — open that
specific day's DailyPeriod.

DashboardScaffold dispatches SheetState.Forecast to ForecastNarrativeSheet.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase E milestone:** All three forecast-narrative triggers wired. Tap ▶ on NowScreen, ▶ on HourlyScreen, or any day-row label on OutlookScreen → ForecastNarrativeSheet opens with that day's narrative.

---

## Phase F — StationOverrideSheet

### Task F.1: StationPreview helper + tests

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/sheets/StationPreview.kt`
- Create: `app/src/test/kotlin/com/skyframe/ui/sheets/StationPreviewTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/skyframe/ui/sheets/StationPreviewTest.kt`:

```kotlin
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
            timestamp = "2026-05-17T14:00:00+00:00",
            temperature = NumberMeasurementDto(value = 22.0, unitCode = "wmoUnit:degC"),
        )
    )

    private val now = Instant.parse("2026-05-17T14:30:00Z")

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
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.ui.sheets.StationPreviewTest" --no-daemon
```

Expected: compile error (StationPreview, StationSnapshot unresolved).

- [ ] **Step 3: Implement StationPreview.kt**

Create `app/src/main/kotlin/com/skyframe/ui/sheets/StationPreview.kt`:

```kotlin
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
```

`NormalizerHelpers` was made `internal` in Plan 1 — same package's test can see it, but `StationPreview` lives in `com.skyframe.ui.sheets` which is a different package. **Need to bump `NormalizerHelpers` visibility from `internal` to `public`.**

Edit `app/src/main/kotlin/com/skyframe/data/nws/NormalizerHelpers.kt`, change:

```kotlin
internal object NormalizerHelpers {
```

to:

```kotlin
object NormalizerHelpers {
```

- [ ] **Step 4: Run tests to verify pass**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.ui.sheets.StationPreviewTest" --no-daemon
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/sheets/StationPreview.kt app/src/test/kotlin/com/skyframe/ui/sheets/StationPreviewTest.kt app/src/main/kotlin/com/skyframe/data/nws/NormalizerHelpers.kt
git commit -m "$(@'
feat(sheets): StationPreview helper for parallel-fetch station preview

Fetches both primary + secondary station observations in parallel and
wraps each side in Result so single-side failures don't abort the
other. Computes isStale via existing NormalizerHelpers.isObservationStale
rule (>90 min OR null temp).

NormalizerHelpers visibility bumped from internal to public so callers
outside the data.nws package (StationPreview lives in ui.sheets) can
reuse the staleness check.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task F.2: StationOverrideSheet body

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/sheets/StationOverrideSheet.kt`

- [ ] **Step 1: Write StationOverrideSheet.kt**

Create `app/src/main/kotlin/com/skyframe/ui/sheets/StationOverrideSheet.kt`:

```kotlin
package com.skyframe.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.data.alerts.AlertDescriptionFormat
import com.skyframe.data.nws.NwsClient
import com.skyframe.domain.StationOverride
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import kotlinx.datetime.TimeZone
import kotlin.math.roundToInt

@Composable
fun StationOverrideSheet(
    currentMode: StationOverride,
    primaryStationId: String,
    secondaryStationId: String,
    timezone: String,
    client: NwsClient,
    onApply: (StationOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalHudAccent.current.accent
    var selectedMode by remember { mutableStateOf(currentMode) }

    var preview by remember {
        mutableStateOf<Pair<Result<StationSnapshot>, Result<StationSnapshot>>?>(null)
    }
    LaunchedEffect(primaryStationId, secondaryStationId) {
        preview = StationPreview.fetch(client, primaryStationId, secondaryStationId)
    }

    val tz = remember(timezone) {
        runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.currentSystemDefault())
    }

    HudBottomSheet(title = "STATION OVERRIDE", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(top = 12.dp)) {

            HudRadioRow(
                label = "AUTO",
                description = "Primary station with automatic fallback to secondary when stale",
                selected = selectedMode == StationOverride.AUTO,
                onSelect = { selectedMode = StationOverride.AUTO },
                accent = accent,
            )
            Spacer(Modifier.height(8.dp))
            HudRadioRow(
                label = "FORCE SECONDARY",
                description = "Always use the secondary station",
                selected = selectedMode == StationOverride.FORCE_SECONDARY,
                onSelect = { selectedMode = StationOverride.FORCE_SECONDARY },
                accent = accent,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "─────────────── PREVIEW ───────────────",
                color = HudColors.ForegroundDim,
                style = HudType.metricLabel,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            StationPreviewRow(
                label = "PRIMARY",
                stationId = primaryStationId,
                result = preview?.first,
                tz = tz,
            )
            Spacer(Modifier.height(8.dp))
            StationPreviewRow(
                label = "SECONDARY",
                stationId = secondaryStationId,
                result = preview?.second,
                tz = tz,
            )

            Spacer(Modifier.height(24.dp))
            ApplyButton(
                enabled = selectedMode != currentMode,
                onClick = { onApply(selectedMode) },
                accent = accent,
            )
        }
    }
}

@Composable
private fun HudRadioRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Custom radio: outer ring + filled inner circle when selected
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(BorderStroke(2.dp, accent), CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = if (selected) accent else HudColors.Foreground, style = HudType.titleBar)
            Text(description, color = HudColors.ForegroundDim, style = HudType.metaLabel)
        }
    }
}

@Composable
private fun StationPreviewRow(
    label: String,
    stationId: String,
    result: Result<StationSnapshot>?,
    tz: TimeZone,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            color = HudColors.ForegroundDim,
            style = HudType.metricLabel,
            modifier = Modifier.width(100.dp).padding(top = 2.dp),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(stationId, color = HudColors.Foreground, style = HudType.metricValue)
            when {
                result == null ->
                    Text("Fetching…", color = HudColors.ForegroundDim, style = HudType.metaLabel)
                result.isFailure ->
                    StatusDot("● ERROR", Color(0xFFFF4444))
                else -> {
                    val snap = result.getOrThrow()
                    val time = snap.observedAt?.let { AlertDescriptionFormat.formatTime(it, tz) } ?: "—"
                    val temp = snap.tempF?.let { "${it.roundToInt()}°" } ?: "—"
                    Text("Observed $time · $temp", color = HudColors.Foreground, style = HudType.metaLabel)
                    if (snap.isStale) StatusDot("● STALE", Color(0xFFFFAA22))
                    else StatusDot("● LIVE", HudColors.DefaultAccent)
                }
            }
        }
    }
}

@Composable
private fun StatusDot(text: String, color: Color) {
    Text(text, color = color, style = HudType.metaLabel)
}

@Composable
private fun ApplyButton(enabled: Boolean, onClick: () -> Unit, accent: Color) {
    val color = if (enabled) accent else HudColors.ForegroundDim
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "[ APPLY ]",
            color = color,
            style = HudType.titleBar,
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .border(BorderStroke(1.dp, color))
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}
```

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/sheets/StationOverrideSheet.kt
git commit -m "$(@'
feat(sheets): StationOverrideSheet body

Two custom HUD-styled radio buttons (AUTO / FORCE SECONDARY) +
parallel live preview of both stations (ID, observed time, temp,
LIVE/STALE/ERROR status dot) + APPLY button that's accent-bordered
when selection differs from current state, dimmed when matching.

Preview fetched via StationPreview.fetch on sheet open. APPLY click
fires onApply(mode); caller wires that to settings update + refresh
+ sheet dismissal.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task F.3: Add applyStationOverride() to DashboardViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt`

- [ ] **Step 1: Add the new method**

In `app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt`, find the existing `dismissAlert` method and add `applyStationOverride` after it:

```kotlin
    fun dismissAlert(id: String) {
        viewModelScope.launch { acknowledgments.dismiss(id) }
    }

    fun applyStationOverride(mode: com.skyframe.domain.StationOverride) {
        viewModelScope.launch {
            settings.update { it.copy(stationOverride = mode) }
            // Immediate refresh so the UI reflects the new station without
            // waiting for the next 90s poll cycle.
            weatherRepository.refresh()
        }
    }
```

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt
git commit -m "$(@'
feat(viewmodel): add applyStationOverride()

Persists the new mode via SettingsRepository.update (atomic since
v0.1.1) and triggers immediate weatherRepository.refresh() so the
UI reflects the new station without waiting for the 90s poll tick.

Wired to StationOverrideSheet's APPLY button in next task.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task F.4: Wire Footer LINK click + DashboardScaffold dispatch

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/shell/Footer.kt`
- Modify: `app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt`

- [ ] **Step 1: Footer's onStationClick is already a parameter — just need to wire it**

Footer's signature already has `onStationClick: () -> Unit` (Plan 1). The click handler is also already on the LINK Text. No Footer change needed for the click itself. Verify:

```powershell
Get-Content "app/src/main/kotlin/com/skyframe/ui/shell/Footer.kt" | Select-String -Pattern "onStationClick"
```

Expected: shows the parameter + the `.clickable { onStationClick() }` modifier.

- [ ] **Step 2: Inject NwsClient into DashboardScaffold so it can be passed to StationOverrideSheet**

This is the architectural awkward bit — `DashboardScaffold` is a Composable that historically didn't take service dependencies. Three options:

**Option A (chosen)**: pass the NwsClient through DashboardScaffold's parameter list from MainActivity. Activity already has Hilt injection.

In `app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt`, update the function signature:

```kotlin
@Composable
fun DashboardScaffold(
    viewModel: DashboardViewModel,
    nwsClient: com.skyframe.data.nws.NwsClient,   // ← NEW
    onNavigateToSettings: () -> Unit,
) {
```

- [ ] **Step 3: Update MainActivity to inject and pass NwsClient**

In `app/src/main/kotlin/com/skyframe/MainActivity.kt`, add the injection:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var setupResolver: SetupResolver
    @Inject lateinit var nwsClient: com.skyframe.data.nws.NwsClient    // ← NEW
```

And update the `setContent { DashboardScaffold(...) }` call to pass it:

```kotlin
        setContent {
            DashboardScaffold(
                viewModel = viewModel,
                nwsClient = nwsClient,                                  // ← NEW
                onNavigateToSettings = {
                    Toast.makeText(this, "Settings: lands in Plan 3", Toast.LENGTH_SHORT).show()
                },
            )
        }
```

- [ ] **Step 4: Wire Footer's onStationClick + Sheet dispatch in DashboardScaffold**

In `DashboardScaffold.kt`, find the `Footer(...)` call:

```kotlin
            Footer(
                stationId = (ui.weather as? WeatherState.Success)?.response?.meta?.stationId.orEmpty(),
                stationOverride = (ui.weather as? WeatherState.Success)?.response?.meta?.stationOverride ?: StationOverride.AUTO,
                lastFetchedLabel = formatFetchedLabel(ui.weather, ui.timezone),
                nextRefreshLabel = formatRefreshLabel(ui.weather),
                onStationClick = {},  // Plan 2 wires StationOverrideSheet
            )
```

Replace `onStationClick = {}` with:

```kotlin
                onStationClick = { sheetState = SheetState.StationOverride },
```

Replace the `SheetState.StationOverride -> Unit` dispatch arm:

```kotlin
            SheetState.StationOverride -> {
                val success = ui.weather as? WeatherState.Success
                if (success != null) {
                    com.skyframe.ui.sheets.StationOverrideSheet(
                        currentMode = success.response.meta.stationOverride,
                        primaryStationId = (ui.weather as? WeatherState.Success)
                            ?.let { resolvePrimaryStationId(ui) } ?: "",
                        secondaryStationId = (ui.weather as? WeatherState.Success)
                            ?.let { resolveSecondaryStationId(ui) } ?: "",
                        timezone = ui.timezone,
                        client = nwsClient,
                        onApply = { mode ->
                            viewModel.applyStationOverride(mode)
                            sheetState = SheetState.None
                        },
                        onDismiss = { sheetState = SheetState.None },
                    )
                }
            }
```

The `resolvePrimary/SecondaryStationId(ui)` helpers don't exist — the actual primary/fallback IDs come from `SettingsRepository.snapshot()`, not `WeatherMeta`. Bring them into the UI state by extending `DashboardUiState` instead. Simpler approach:

Replace the above with — pull from `viewModel.settings.flow` already exposed via `ui`... but `DashboardUiState` doesn't currently expose primary/fallback IDs. Add them.

In `DashboardViewModel.kt`, expand `DashboardUiState`:

```kotlin
data class DashboardUiState(
    val weather: WeatherState,
    val dismissedAlertIds: Set<String>,
    val isConfigured: Boolean,
    val locationName: String,
    val timezone: String,
    val primaryStationId: String,         // ← NEW
    val secondaryStationId: String,       // ← NEW
)
```

And in the `combine { ... }`:

```kotlin
        DashboardUiState(
            weather = weather,
            dismissedAlertIds = dismissed,
            isConfigured = cfg.isConfigured,
            locationName = cfg.locationName,
            timezone = cfg.timezone,
            primaryStationId = cfg.stationPrimary,      // ← NEW
            secondaryStationId = cfg.stationFallback,   // ← NEW
        )
```

Update the `initialValue` too:

```kotlin
        initialValue = DashboardUiState(
            WeatherState.Idle, emptySet(), false, "", "America/Chicago", "", "",
        ),
```

Now the DashboardScaffold dispatch can use:

```kotlin
            SheetState.StationOverride -> {
                val success = ui.weather as? WeatherState.Success
                val currentMode = success?.response?.meta?.stationOverride ?: StationOverride.AUTO
                com.skyframe.ui.sheets.StationOverrideSheet(
                    currentMode = currentMode,
                    primaryStationId = ui.primaryStationId,
                    secondaryStationId = ui.secondaryStationId,
                    timezone = ui.timezone,
                    client = nwsClient,
                    onApply = { mode ->
                        viewModel.applyStationOverride(mode)
                        sheetState = SheetState.None
                    },
                    onDismiss = { sheetState = SheetState.None },
                )
            }
```

- [ ] **Step 5: Verify compile + build APK**

```powershell
./gradlew :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 6
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt app/src/main/kotlin/com/skyframe/MainActivity.kt app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt
git commit -m "$(@'
feat(ui): wire StationOverrideSheet open via Footer LINK tap

Footer's onStationClick was already a callback parameter from Plan 1
just needing wiring. DashboardScaffold dispatches SheetState.StationOverride
to StationOverrideSheet, plumbing in:
- the active mode from WeatherMeta
- primary/fallback IDs (now exposed on DashboardUiState)
- the NwsClient (injected via MainActivity, passed through DashboardScaffold)
- timezone for observation-time formatting

DashboardUiState gains primaryStationId + secondaryStationId. APPLY
callback fires viewModel.applyStationOverride(mode) + closes sheet.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase F milestone:** All three sheets fully wired. Tap Footer's `LINK.<station>` → StationOverrideSheet opens with parallel-fetched preview; APPLY persists + refreshes.

---

## Phase G — Minor Reviewer Cleanup

Four small fixes in files Plan 2 already touches.

### Task G.1: Fix T-Xs countdown in DashboardScaffold

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt`

- [ ] **Step 1: Add a 1Hz ticker LaunchedEffect that drives the refresh label**

In `app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt`, locate the existing `formatRefreshLabel` private function:

```kotlin
private fun formatRefreshLabel(state: WeatherState): String {
    val success = state as? WeatherState.Success ?: return "T-???"
    val secondsLeft = (success.response.meta.nextRefreshAt.epochSeconds - Clock.System.now().epochSeconds).coerceAtLeast(0)
    return "T-${secondsLeft}s"
}
```

This is correct — but it only re-evaluates on recomposition. To force a 1Hz recomposition of the Footer's refresh label, hoist a tick counter inside `DashboardScaffold`:

Inside the `DashboardScaffold` Composable body, before the `HudTheme { ... }` block, add:

```kotlin
    // Forces a recomposition every second so the Footer's T-Xs countdown
    // actually decrements (without this it freezes between weather updates).
    var nowTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = kotlinx.datetime.Clock.System.now().epochSeconds
            kotlinx.coroutines.delay(1000)
        }
    }
```

And change the Footer call to pass `nowTick` as a "trigger" — but more cleanly, change `formatRefreshLabel` to take an explicit current-time parameter, then pass `nowTick`:

```kotlin
private fun formatRefreshLabel(state: WeatherState, nowSeconds: Long): String {
    val success = state as? WeatherState.Success ?: return "T-???"
    val secondsLeft = (success.response.meta.nextRefreshAt.epochSeconds - nowSeconds).coerceAtLeast(0)
    return "T-${secondsLeft}s"
}
```

Update the call site:

```kotlin
                nextRefreshLabel = formatRefreshLabel(ui.weather, nowTick),
```

Same treatment for `formatFetchedLabel` not needed (it only changes when the data fetches, which already triggers recomposition).

Add imports if missing:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
```

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:assembleDebug --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt
git commit -m "$(@'
fix(ui): drive T-Xs countdown with 1Hz ticker

formatRefreshLabel was reading Clock.System.now() at composition only,
so the countdown stayed frozen between weather state emissions (every
~90s). Added a LaunchedEffect ticker that updates nowTick once per
second, which the Footer's label depends on — forcing recomposition
and making the countdown visibly decrement.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task G.2: Fix HourlyScreen precip-bar zero-height bug

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/screens/HourlyScreen.kt`

- [ ] **Step 1: Find the buggy line**

```powershell
Get-Content "app/src/main/kotlin/com/skyframe/ui/screens/HourlyScreen.kt" | Select-String -Pattern "40 \* p.precipProbPct"
```

Expected: shows `.height((40 * p.precipProbPct / 100).dp)`.

- [ ] **Step 2: Fix with Float arithmetic + fillMaxHeight fraction**

Find the precip-bar inner Box:

```kotlin
                        Box(
                            modifier = Modifier
                                .height((40 * p.precipProbPct / 100).dp)
                                .background(accent.copy(alpha = 0.6f))
                                .align(Alignment.BottomCenter),
                        )
```

Replace with:

```kotlin
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(p.precipProbPct / 100f)
                                .fillMaxWidth()
                                .background(accent.copy(alpha = 0.6f))
                                .align(Alignment.BottomCenter),
                        )
```

Add the import: `import androidx.compose.foundation.layout.fillMaxHeight`.

- [ ] **Step 3: Verify compile + commit**

```powershell
./gradlew :app:assembleDebug --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/screens/HourlyScreen.kt
git commit -m "$(@'
fix(ui): HourlyScreen precip bar zero-height for low probabilities

Integer arithmetic (40 * pct / 100).dp truncated to 0.dp for any
precipProb < 3% — the bar disappeared entirely instead of being a
visible-but-tiny sliver. Replaced with fillMaxHeight(fraction) +
fillMaxWidth so the fraction is computed as a Float and the bar
always renders at the correct proportional height.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task G.3: Remove NowScreen pressure /1.0 no-op

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/screens/NowScreen.kt`

- [ ] **Step 1: Find the no-op**

```powershell
Get-Content "app/src/main/kotlin/com/skyframe/ui/screens/NowScreen.kt" | Select-String -Pattern "/ 1.0"
```

Expected: shows `fillFraction = (((current.pressureInHg ?: 29.92) - 29.50) / 1.0).toFloat()`.

- [ ] **Step 2: Simplify**

Replace:

```kotlin
            // Normalize pressure 29.50..30.50 inHg to 0..1 (covers typical range)
            fillFraction = (((current.pressureInHg ?: 29.92) - 29.50) / 1.0).toFloat(),
```

with:

```kotlin
            // Normalize pressure 29.50..30.50 inHg to 0..1 (covers typical range)
            fillFraction = ((current.pressureInHg ?: 29.92) - 29.50).toFloat(),
```

- [ ] **Step 3: Verify compile + commit**

```powershell
./gradlew :app:assembleDebug --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/screens/NowScreen.kt
git commit -m "$(@'
fix(ui): NowScreen pressure fillFraction /1.0 no-op removed

Trivial cleanup. The division by 1.0 was a leftover from when the
divisor was the range width; since 30.50 - 29.50 = 1.0, dividing by 1.0
is a no-op. Just subtract.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task G.4: Add missing NWS icon codes to IconMapper + tests

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/data/nws/IconMapper.kt`
- Modify: `app/src/test/kotlin/com/skyframe/data/nws/IconMapperTest.kt`

- [ ] **Step 1: Write failing tests for the new codes**

Append to `app/src/test/kotlin/com/skyframe/data/nws/IconMapperTest.kt` (inside the test class):

```kotlin
    @Test
    fun `scattered thunderstorms maps to thunder`() {
        assertEquals(IconCode.THUNDER, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/scttsra?size=medium"))
    }

    @Test
    fun `blizzard maps to snow`() {
        assertEquals(IconCode.SNOW, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/blizzard?size=medium"))
    }

    @Test
    fun `cold maps to cloud as a safe fallback`() {
        assertEquals(IconCode.CLOUD, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/cold?size=medium"))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.IconMapperTest" --no-daemon
```

Expected: 3 failures (the new codes currently fall through to CLOUD which makes one test pass and two fail).

- [ ] **Step 3: Add the missing codes to IconMapper.CODE_MAP_DAY**

In `app/src/main/kotlin/com/skyframe/data/nws/IconMapper.kt`, locate `CODE_MAP_DAY` and add these entries (any reasonable position; the map order doesn't matter):

```kotlin
        "scttsra" to IconCode.THUNDER,
        "hi_shwrs" to IconCode.RAIN,
        "fzra_sct" to IconCode.SNOW,
        "ra_fzra" to IconCode.SNOW,
        "ra_sn" to IconCode.SNOW,
        "sn" to IconCode.SNOW,
        "blizzard" to IconCode.SNOW,
        "cold" to IconCode.CLOUD,
```

- [ ] **Step 4: Run tests to verify pass**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.IconMapperTest" --no-daemon
```

Expected: all IconMapper tests pass (17 total now).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/IconMapper.kt app/src/test/kotlin/com/skyframe/data/nws/IconMapperTest.kt
git commit -m "$(@'
fix(nws): add missing NWS icon codes to IconMapper

Reviewer-flagged: scttsra (scattered thunderstorms), hi_shwrs
(high-intensity showers), fzra_sct, ra_fzra, ra_sn, sn, blizzard,
cold were all silently falling through to CLOUD when they had
clearly-mapped equivalents. Real NWS responses include these for
specific weather states (especially blizzard during winter storms,
scttsra during summer afternoon storms).

3 regression tests added; total IconMapper coverage now 17 tests.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase G milestone:** All 4 reviewer-flagged minor cleanups landed. Total test count after Phase G: ~119.

---

## Phase H — Ship

### Task H.1: Update SMOKE_TEST.md with sheet flows

**Files:**
- Modify: `docs/SMOKE_TEST.md`

- [ ] **Step 1: Append new sheet-verification sections**

Append to `docs/SMOKE_TEST.md` (after the existing "Alert handling (synthetic test)" section, before "Regression"):

```markdown

## Sheet flows (v0.2.0 / Plan 2)

### AlertDetailSheet

- [ ] Inject a synthetic alert per the "Alert handling" section above
- [ ] Tap the alert event name in the banner → AlertDetailSheet opens
- [ ] Sheet shows event name in tier color (red for tornado-warning) + meta line (ISSUED ... · EXPIRES ... · COUNTY)
- [ ] Description paragraphs render with HAZARD/SOURCE/IMPACT prefixes in the alert's tier color
- [ ] Swipe down dismisses the sheet
- [ ] System back dismisses the sheet
- [ ] Tap outside (on scrim) dismisses the sheet
- [ ] Tap [x] in title bar dismisses the sheet

### ForecastNarrativeSheet

- [ ] Tap ▶ next to "TEMP / FEEL" on NowScreen → opens TODAY's forecast
- [ ] Sheet shows two sections: ┌ TODAY ┐ and ┌ TONIGHT ┐ (or the NWS-preserved names like "THIS AFTERNOON")
- [ ] Each section body shows the dayDetailedForecast / nightDetailedForecast text
- [ ] Tap ▶ next to "NEXT 12H" on HourlyScreen → also opens TODAY
- [ ] Tap any day-of-week label on OutlookScreen → opens that day's forecast
- [ ] All dismissal methods work (swipe, back, scrim, [x])

### StationOverrideSheet

- [ ] Tap "LINK.<station>" in Footer → StationOverrideSheet opens
- [ ] Currently-active mode's radio is selected (AUTO by default)
- [ ] "Fetching…" text shows briefly under both PRIMARY and SECONDARY rows
- [ ] After ~1s: each row shows station ID + observed time + temp + LIVE/STALE indicator
- [ ] Tap FORCE SECONDARY radio → APPLY button enables (accent border)
- [ ] Tap APPLY → sheet closes, footer LINK now shows `[PIN]` suffix in amber
- [ ] Open sheet again → AUTO radio + selecting it + APPLY clears the pin

### Trend arrows (Phase A)

- [ ] After ~3 successful weather poll cycles (3 × 90s = 4.5 min), NowScreen metric bars should show ▲ ▼ · arrows in the accent color
- [ ] Arrows are hidden gracefully if the /observations history endpoint fails (no `·` dots showing in confusion)

### T-Xs countdown (G.1 fix)

- [ ] Footer's `T-Xs` value should decrement once per second (not freeze for 90s)
```

- [ ] **Step 2: Commit**

```powershell
git add docs/SMOKE_TEST.md
git commit -m "$(@'
docs: extend SMOKE_TEST.md with v0.2.0 sheet + trend flows

Adds verification checklists for AlertDetailSheet, ForecastNarrativeSheet,
StationOverrideSheet (all four dismissal methods each), trend arrows
finally appearing, and the T-Xs countdown decrementing.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task H.2: Update PROJECT_STATUS, ROADMAP, CHANGELOG for v0.2.0

**Files:**
- Modify: `docs/PROJECT_STATUS.md`
- Modify: `docs/ROADMAP.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Add Plan 2 section to PROJECT_STATUS.md**

In `docs/PROJECT_STATUS.md`, locate the existing `### v0.1.1-mvp (2026-05-17) — Final-review fixes` section. After it, before the `## What's pending` section, add:

```markdown

### Plan 2 — Full alert UX + trends (v0.2.0 / 2026-05-17)

#### Phase A: History fetch
- `NwsClient.recentObservations(stationId, limit)` endpoint + `ObservationsListDto` + `ObservationFeatureDto`
- `WeatherNormalizer` fetches history sequentially after the primary→fallback decision (wrapped in `runCatching` for partial-failure semantics); feeds non-empty `recentObservations` to `ObservationNormalizer.normalize`
- `WeatherNormalizerTest` backfilled — 6 tests covering happy path, station fallback, force-secondary override, alerts-failure-PARTIAL, history-failure-graceful-degradation, TTL cache hit
- `HudMetricBar` trend arrows reappear silently when history fetch succeeds (still hidden via v0.1.1 fix #10 when MISSING)

#### Phase B: AlertDescriptionFormat
- `parseDescription` splits on `\n{2,}` and tags HAZARD/SOURCE/IMPACT paragraphs (port of web's `alert-detail-format.ts`)
- `formatTime(Instant, TimeZone)` renders as `"2:30 PM CDT"`
- `formatAlertMeta(Alert, TimeZone)` produces `"ISSUED ... · EXPIRES ... · AREA"`

#### Phase C: Shared sheet chrome
- `HudBottomSheet` Composable wraps Material3 ModalBottomSheet with HUD restyling (rectangular shape, BackgroundPanel container, 2dp accent top border, custom `TERMINAL // {TITLE}` title bar with `[x]` close)
- `SheetState` sealed class for mutual exclusion (`None | AlertDetail | Forecast | StationOverride`)
- `ForecastButton` widget — small ▶ trigger glyph

#### Phase D: AlertDetailSheet
- Renders parsed paragraphs with HAZARD/SOURCE/IMPACT prefixes in the alert's tier color (NOT dashboard's active accent)
- AlertBanner event-name click (both top alert and expanded list rows) opens the sheet
- DashboardScaffold hoists `sheetState` and dispatches to the sheet

#### Phase E: ForecastNarrativeSheet
- Renders day + night detailed forecasts under NWS-preserved period names (THIS AFTERNOON / TONIGHT / FRIDAY / FRIDAY NIGHT) in the active accent color
- Wired via three triggers: ▶ next to NowScreen TEMP/FEEL, ▶ next to HourlyScreen NEXT 12H header, tap any day-row label in OutlookScreen
- Orphan halves (day-only / night-only) render only the populated section

#### Phase F: StationOverrideSheet
- Two custom HUD-styled radio buttons (AUTO / FORCE SECONDARY) with descriptions
- `StationPreview.fetch` parallel-fetches both stations on sheet open, wraps each in `Result` so single-side failures don't abort the other
- Per-station preview rows show ID + observed time + temp + `● LIVE` / `● STALE` / `● ERROR` status dot
- `[ APPLY ]` button enabled (accent-bordered) when selection differs from current; on click writes via `DashboardViewModel.applyStationOverride()` → SettingsRepository + immediate refresh + sheet dismissal
- `DashboardUiState` extended with `primaryStationId` + `secondaryStationId`
- `MainActivity` injects `NwsClient` via Hilt and passes through to `DashboardScaffold`
- `NormalizerHelpers` visibility bumped from `internal` to `public` so `StationPreview` (different package) can reuse `isObservationStale`

#### Phase G: Minor reviewer cleanups
- `DashboardScaffold` 1Hz LaunchedEffect ticker drives `T-Xs` countdown so it actually decrements (was frozen between 90s state emissions)
- `HourlyScreen` precip bar uses `fillMaxHeight(fraction)` instead of integer-arithmetic `.dp` (fixes zero-height for sub-3% probabilities)
- `NowScreen` removes `/ 1.0` no-op in pressure fillFraction
- `IconMapper` adds 8 missing NWS codes: `scttsra`, `hi_shwrs`, `fzra_sct`, `ra_fzra`, `ra_sn`, `sn`, `blizzard`, `cold` + 3 regression tests
```

Then update the "Last updated" line at the top:

```markdown
**Last updated:** 2026-05-17 (v0.2.0)
**Current tag:** [v0.2.0](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.2.0)
```

- [ ] **Step 2: Update ROADMAP.md**

In `docs/ROADMAP.md`, change the Plan 2 row:

```markdown
| **Plan 2** — Full alert UX | AlertDetailSheet ... | Not started | — |
```

to:

```markdown
| **Plan 2** — Full alert UX + trends | AlertDetailSheet (tap alert event → NWS description with HAZARD/SOURCE/IMPACT tier-colored), ForecastNarrativeSheet (▶ glyph / day-row tap → day+night narrative), StationOverrideSheet (Footer LINK tap → AUTO/FORCE_SECONDARY with live preview), observation history fetch + trend arrows | ✅ **Shipped** | [`v0.2.0`](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.2.0) |
```

And remove the Plan 1B row entirely (now bundled into Plan 2). Update the dependency diagram to remove Plan 1B reference.

- [ ] **Step 3: Add v0.2.0 section to CHANGELOG.md**

In `CHANGELOG.md`, replace the `## [Unreleased]` section with:

```markdown
## [Unreleased]

Plan 3 (SettingsScreen + onboarding + GPS + GitHub update polling) is the next target — see [docs/ROADMAP.md](docs/ROADMAP.md).

---

## [v0.2.0] — 2026-05-17

Plan 2 milestone: three overlay sheets complete the alert and forecast UX, plus observation history fetching revives the trend arrows on NowScreen.

### Added

- **AlertDetailSheet** — tap any alert event name in `AlertBanner` opens a ModalBottomSheet rendering the NWS `description` as HAZARD/SOURCE/IMPACT-tagged paragraphs (prefixes in the alert's own tier color) + meta line (`ISSUED ... · EXPIRES ... · AREA`).
- **ForecastNarrativeSheet** — opens from three triggers (▶ next to NowScreen TEMP/FEEL, ▶ next to HourlyScreen NEXT 12H header, tap any day-row date label in OutlookScreen) and shows day + night detailed forecasts stacked with NWS-preserved period names.
- **StationOverrideSheet** — tap Footer's `LINK.<station>` opens a small sheet with AUTO / FORCE_SECONDARY radio buttons + parallel-fetched live preview rows (ID, observed time, temp, LIVE/STALE/ERROR status). `[APPLY]` persists + triggers immediate refresh + closes sheet.
- **Observation history fetch** — new `NwsClient.recentObservations(stationId, limit=6)` + DTO + wired into `WeatherNormalizer` (sequential after fallback resolution, `runCatching`-wrapped). `ConditionTrends` now populates with real OLS-computed deltas; `HudMetricBar` trend arrows reappear silently.
- **`HudBottomSheet`** shared chrome primitive — Material3 ModalBottomSheet with HUD restyling (rectangular shape, BackgroundPanel container, 2dp accent top border, custom `TERMINAL // {TITLE}` title bar + `[x]` close glyph). All three sheets use it.
- **`AlertDescriptionFormat`** helper — port of web's `alert-detail-format.ts`. Pure logic: `parseDescription` (paragraph splitting + prefix detection), `formatTime` (12-hour with TZ abbreviation), `formatAlertMeta` (ISSUED · EXPIRES · AREA).
- **`StationPreview.fetch`** helper — parallel `Result`-wrapped fetch of two stations for the override sheet's live preview.
- **`SheetState`** sealed class — mutual exclusion for sheet rendering hoisted to `DashboardScaffold`.
- **`DashboardViewModel.applyStationOverride(mode)`** — atomic settings update + immediate refresh.

### Fixed

- **`DashboardScaffold` T-Xs countdown** now decrements once per second (1Hz `LaunchedEffect` ticker) instead of freezing between 90s state emissions.
- **`HourlyScreen` precip bar** no longer renders zero-height for sub-3% probabilities (integer-arithmetic `.dp` truncation replaced with `fillMaxHeight(fraction)`).
- **`NowScreen` pressure `fillFraction`** dropped the `/ 1.0` no-op.
- **`IconMapper`** maps 8 previously-missing NWS codes: `scttsra`, `hi_shwrs`, `fzra_sct`, `ra_fzra`, `ra_sn`, `sn`, `blizzard`, `cold`.

### Changed

- **Test count: 96 → ~119** (+23 new tests including the first-ever `WeatherNormalizerTest` orchestrator coverage that closes Plan 1's biggest gap).
- **`NormalizerHelpers` visibility** bumped from `internal` to `public` so `StationPreview` (different package) can reuse `isObservationStale`.
- **`DashboardUiState`** extended with `primaryStationId` + `secondaryStationId` fields.
- **`AlertBanner`** signature adds `onAlertClick: (Alert) -> Unit` parameter.
- **`NowScreen` / `HourlyScreen` / `OutlookScreen`** signatures add `onOpenForecast: (DailyPeriod) -> Unit` parameter.
- **`HudHero`** signature adds `onOpenForecast: () -> Unit` parameter.
- **`DashboardScaffold`** signature adds `nwsClient: NwsClient` parameter (passed from `MainActivity` via Hilt injection).
```

- [ ] **Step 4: Commit all three doc updates together**

```powershell
git add docs/PROJECT_STATUS.md docs/ROADMAP.md CHANGELOG.md
git commit -m "$(@'
docs: update PROJECT_STATUS, ROADMAP, CHANGELOG for v0.2.0

PROJECT_STATUS: full Plan 2 implemented-features list organized by
phase (history fetch, AlertDescriptionFormat, shared chrome,
AlertDetailSheet, ForecastNarrativeSheet, StationOverrideSheet,
minor cleanups). Header date bumped to 2026-05-17 / v0.2.0.

ROADMAP: Plan 2 row flipped to Shipped at v0.2.0; Plan 1B row removed
(its scope was bundled into Plan 2).

CHANGELOG: v0.2.0 release notes added — three sheets, observation
history fetch reviving trends, four reviewer-flagged minor fixes,
test count 96 → ~119.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task H.3: Final test run + tag v0.2.0 + push

- [ ] **Step 1: Run the full test suite one more time**

```powershell
./gradlew :app:testDebugUnitTest --no-daemon 2>&1 | Select-Object -Last 8
```

Expected: BUILD SUCCESSFUL. Verify test count is in the ~115-120 range with 0 failures:

```powershell
Get-Content "app/build/reports/tests/testDebugUnitTest/index.html" -Raw | Select-String -Pattern '<div class="counter">[^<]+</div>' -AllMatches | ForEach-Object { $_.Matches } | ForEach-Object { $_.Value } | Select-Object -First 4
```

- [ ] **Step 2: Build the release APK to confirm R8/ProGuard config still works**

```powershell
./gradlew :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 5
```

Expected: BUILD SUCCESSFUL. APK at `app/build/outputs/apk/debug/app-debug.apk` (~13 MB).

- [ ] **Step 3: Tag v0.2.0 + push everything**

```powershell
git tag -a v0.2.0 -m "Plan 2 milestone: full alert UX (3 sheets) + trend arrows revived. ~119 unit tests, 0 failures."
git push origin main
git push origin v0.2.0
```

Expected: tag pushed; visit https://github.com/OniNoKen4192/SkyFrameAndroid/releases — v0.2.0 should appear.

- [ ] **Step 4: Verify the v0.2.0 tag exists locally and remotely**

```powershell
git tag --list | Select-String "v0.2.0"
git ls-remote --tags origin | Select-String "v0.2.0"
```

Expected: both show `v0.2.0`.

---

**Phase H milestone — Plan 2 complete.** v0.2.0 tagged on GitHub. Three sheets ship, trend arrows are back, four minor bugs squashed.

---

## Plan 2 Self-Review

### Spec coverage check

Walked through each section of [the design spec](../specs/2026-05-17-skyframe-android-plan-2-alert-ux-design.md):

- **What ships #1 (AlertDetailSheet):** Phase D ✓
- **What ships #2 (ForecastNarrativeSheet):** Phase E ✓
- **What ships #3 (StationOverrideSheet):** Phase F ✓
- **What ships #4 (observation history fetch):** Phase A ✓
- **What ships #5 (minor cleanup):** Phase G ✓
- **Non-goals:** all 6 explicitly excluded items remain out of Plan 2 (no Settings screen, no isUpdateAlert, no sounds, no notification handlers, no preview caching across opens, no custom animations) ✓
- **Top-level decisions:** all 8 honored (ModalBottomSheet w/HUD restyling, hoisted SheetState, parseDescription port, parallel station preview, alert-own-tier-color for prefixes, APPLY button (no auto-on-click), history fetch sequential after fallback, v0.2.0 tag) ✓
- **Sheet state hoisting code:** present in Task D.3 ✓
- **HudBottomSheet code:** Task C.2 ✓
- **Test strategy 5 suites (~21 tests):** AlertDescriptionFormatTest (9, slightly over spec's ~8), NwsClientTest (+2, slightly over spec's +1), WeatherNormalizerTest (6), StationPreviewTest (3), IconMapperTest (+3) = ~23 total. Close to spec's ~21 target ✓
- **Documentation updates:** Task H.2 covers PROJECT_STATUS + ROADMAP + CHANGELOG + SMOKE_TEST ✓

No spec gaps.

### Placeholder scan

Searched the plan for red flags:
- `TBD` / `TODO` / `implement later`: none
- "Add appropriate error handling" / "handle edge cases": none
- "Write tests for the above" without code: none
- "Similar to Task N": none (every task self-contained)
- Steps that describe without showing code: none

### Type consistency check

- `SheetState` sealed class same shape across C.1 / D.3 / E.3 / F.4 ✓
- `applyStationOverride(mode: StationOverride)` signature consistent in F.3 + F.4 ✓
- `onOpenForecast: (DailyPeriod) -> Unit` consistent in E.2 (NowScreen, HudHero) + E.3 (HourlyScreen, OutlookScreen, DashboardScaffold) ✓
- `onAlertClick: (Alert) -> Unit` consistent in D.2 (AlertBanner) + D.3 (DashboardScaffold) ✓
- `StationSnapshot` data class fields (stationId, observedAt, tempF, isStale) consistent in F.1 (helper + test) + F.2 (sheet body) ✓
- `AlertDescriptionParagraph.Prefix` enum values (HAZARD, SOURCE, IMPACT) consistent in B.1 (helper + test) + D.1 (sheet body uses `.prefix.name`) ✓
- `HudBottomSheet(title, onDismissRequest, content)` signature consistent in C.2 (definition) + D.1 / E.1 / F.2 (callers) ✓
- `nwsClient: NwsClient` parameter consistent in F.4 (DashboardScaffold + MainActivity) ✓
- `DashboardUiState.primaryStationId` / `.secondaryStationId` consistent in F.4 (ViewModel + scaffold usage) ✓

No type-consistency issues.

### Scope check

Plan 2 is one focused milestone — "full alert UX + trends." All work serves the design spec's stated goals. No drift into Plan 3/4/5 territory. Decomposed appropriately.

### Ambiguity check

- Phase A's history fetch is wrapped in `runCatching` — both the rationale and the graceful-degradation outcome are spelled out.
- Phase F's `DashboardUiState` extension and `NwsClient` injection are flagged explicitly as architectural changes with file paths.
- Phase G.1's ticker pattern has a comment explaining *why* — prevents a future engineer from "simplifying" it away.
- `NormalizerHelpers` visibility bump in F.1 is documented in the commit message.

---

## Execution Handoff

Plan complete and saved to [docs/superpowers/plans/2026-05-17-skyframe-android-plan-2-alert-ux.md](2026-05-17-skyframe-android-plan-2-alert-ux.md). Total: 26 tasks across 8 phases (A-H), ~23 new unit tests for a ~119 total, ends with `v0.2.0` tagged on GitHub.

Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
