# SkyFrame Android — Project Status

**Last updated:** 2026-05-18 (v0.2.0)
**Current tag:** [v0.2.0](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.2.0)
**Roadmap:** [docs/ROADMAP.md](ROADMAP.md)

## What this is

Native Android port of [SkyFrame](https://github.com/OniNoKen4192/SkyFrame). Local, ad-free weather dashboard for a configured location, with planned background severe-weather notifications. Single user. Forked at SkyFrame web v1.2.6.

## Tech stack

- **Language:** Kotlin 2.0.21
- **UI:** Jetpack Compose (BOM 2024.11), Material 3 (overridden with HUD theme), IBM Plex Mono bundled
- **DI:** Hilt 2.52
- **HTTP:** Ktor 3.0.1 with OkHttp engine; mandatory User-Agent header
- **JSON:** kotlinx.serialization 1.7.3
- **Time:** kotlinx.datetime 0.6.1
- **Persistence:** Jetpack DataStore Preferences 1.1.1
- **Background:** WorkManager 2.10.0 (dependency in place; first use in Plan 4)
- **Tests:** JUnit 5 + MockK + Turbine
- **Min SDK:** 26 (Android 8.0) — ~98% device coverage
- **Target SDK:** 35 (Android 15)
- **Build:** Gradle 8.10.2 wrapper, AGP 8.7.2

## Architecture

```
app/                                   Single-module Android project
  src/main/kotlin/com/skyframe/
    MainActivity.kt                    Hilt entry, debug-seed for Plan-1 testing
    SkyFrameApp.kt                     Application class (@HiltAndroidApp)
    domain/                            WeatherResponse, CurrentConditions, HourlyPeriod,
                                       DailyPeriod, Alert, AlertTier (13 tiers), AlertSeverity,
                                       WeatherMeta, StationOverride, WeatherError, Wind, IconCode,
                                       Trend, TrendDirection, TrendConfidence, TempUnit, Units
    data/
      nws/                             NwsHttpClient (per-request UA), NwsClient (5 endpoints),
                                       NwsDtos, WeatherNormalizer (orchestrator, partial-failure
                                       tolerant, timezone-aware), ObservationNormalizer,
                                       ForecastNormalizer (order-walk daily pairing),
                                       AlertNormalizer, NormalizerHelpers (WMO units),
                                       IconMapper (probability thresholds), TrendCalculator (OLS),
                                       SetupResolver (ZIP/lat-lon → grid)
      geocoding/                       Nominatim Geocoder (keyless)
      alerts/                          AlertClassifier (parameter-driven tier escalation)
      cache/                           WeatherCache (TTL, ConcurrentHashMap)
      settings/                        SettingsRepository (DataStore, atomic update),
                                       SettingsKeys, SettingsModule (Hilt)
      acknowledgments/                 AlertAcknowledgmentRepository (dismissed-set,
                                       auto-prune on alert lifecycle)
    repository/                        WeatherRepository (StateFlow + polling)
    viewmodel/                         DashboardViewModel (combine flow)
    di/                                NetworkModule, CacheModule, CoroutineModule
    theme/                             HudColors, HudAccent (CompositionLocal), HudType
                                       (IBM Plex Mono), HudTextGlow, HudTheme
    ui/
      shell/                           DashboardScaffold, TopBar (clock + location + ≡),
                                       Footer (LINK.XXXX + T-Xs), HudBottomNavBar,
                                       AlertBanner (hazard-stripe + expand + dismiss)
      screens/                         NowScreen, HourlyScreen, OutlookScreen
      widgets/                         HudHero (tap-toggle F/C), HudMetricBar, HudChart
                                       (Canvas line), HudRangeBar, HudGlowText, WxIcon,
                                       WxIcons (9 ImageVector ports from icons.svg)
      nav/                             Destinations enum
  src/main/res/
    font/                              ibm_plex_mono_{regular,medium}.ttf (OFL-licensed)
    drawable/, mipmap-anydpi-v26/      Adaptive launcher icon (placeholder; real logo Plan 5)
```

## Data flow

1. `DashboardScaffold` collects `DashboardViewModel.uiState` (StateFlow combining `WeatherRepository.state` + `AlertAcknowledgmentRepository.flow` + `SettingsRepository.flow`)
2. `MainActivity.onResume()` calls `viewModel.onResume()` → `WeatherRepository.startPolling()`
3. `WeatherRepository` loops: `WeatherNormalizer.load()` → `nws.points/forecast/hourlyForecast/activeAlerts` in parallel → fetch observation with primary→fallback escalation → assemble + cache `WeatherResponse` (90s TTL)
4. ViewModel exposes `visibleAlerts` (active minus dismissed) and auto-prunes stale dismissals
5. UI shifts accent via `HudAccent.fromTier(top.tier)` driven by the highest-severity visible alert

## Implemented features

Running list of what's in v0.1.1-mvp. **Update this in the same commit when a new feature ships.**

### Plan 1 — Foundation + MVP dashboard (v0.1.0-mvp / 2026-05-16)

#### Phase A: Repo migration
- Web project archived to `_reference/` (gitignored)
- Fresh git repo initialized, pointed at `github.com/OniNoKen4192/SkyFrameAndroid`
- New CLAUDE.md, README.md, ALERT_TIERS.md
- `.claude/settings.json` with broad permission allowlist + destructive-command denylist

#### Phase B: Project scaffold
- Gradle wrapper 8.10.2 + version catalog (`gradle/libs.versions.toml`)
- AGP 8.7.2 + Kotlin 2.0.21 + Compose BOM 2024.11 + Hilt + Ktor + kotlinx.serialization + DataStore + WorkManager dependency
- Adaptive launcher icon (placeholder)
- Hello-world MainActivity rendering on dark HUD background
- gradle.properties: useAndroidX, parallel builds, configuration cache, 4GB JVM args

#### Phase C: Domain types + pure logic
- `AlertTier` enum: 13 tiers ranked 1–13 with base + dark colors ported 1:1 from web's TIER_COLORS
- `AlertClassifier`: parameter-driven escalation for Tornado Warning (CATASTROPHIC→emergency, CONSIDERABLE→PDS) and Severe Thunderstorm Warning (DESTRUCTIVE→tstorm-destructive); unknown events fall through to ADVISORY
- `Units` + `TempUnit` + `Trend` + `TrendDirection` + `TrendConfidence`: F↔C / m·s⁻¹→mph / Pa→inHg conversions + trend rescaling
- `WeatherResponse` + 7 sub-types: type-safe domain model with `@Serializable` annotations, `kotlinx.datetime.Instant` for all timestamps

#### Phase D: HUD theming foundation
- `HudColors`: BackgroundDeep / BackgroundBase / BackgroundPanel / Foreground / ForegroundDim / DefaultAccent
- `HudAccent` + `LocalHudAccent` CompositionLocal: tier-driven accent flow, derived glow alphas (0.15 / 0.30 / 0.50)
- `HudType` per-role TextStyle definitions (titleBar, heroTemp, metricLabel, sectionHeader, navLabel, footerMono, etc.) backed by bundled IBM Plex Mono (OFL)
- `hudTextGlow` modifier: `BlurEffect` on API 31+, graceful no-op on API 26–30
- `HudTheme`: Material 3 ColorScheme override so incidentally-used Material widgets inherit HUD palette

#### Phase E: NWS HTTP layer
- `NwsHttpClient`: Ktor + OkHttp, mandatory User-Agent (per-request callback as of v0.1.1), 15s request timeout, 2x exponential-backoff retry, geo+json Accept header
- `NwsClient`: 5 endpoint methods (`points`, `stationsList`, `forecast`, `hourlyForecast`, `latestObservation`, `activeAlerts`) with `Locale.ROOT`-formatted lat/lon (v0.1.1 fix)
- `NwsDtos`: PointsDto (with astronomicalData), ForecastDto, ObservationDto (WMO measurement values), AlertsDto, StationsListDto
- `WeatherCache<V>`: generic ConcurrentHashMap-backed TTL cache with injectable clock
- `IconMapper`: NWS icon URL → IconCode with `forHourly` (<30% precip downgrade) and `forDaily` (≥50% precip upgrade, shortForecast keyword tiebreak: thunder > snow > rain)
- `TrendCalculator`: OLS linear-regression slope over up-to-6 observations, configurable steady threshold

#### Phase F: Normalizer pipeline
- `Geocoder`: Nominatim (keyless OpenStreetMap) ZIP→lat/lon
- `SetupResolver`: ZIP or "lat, lon" → NWS /points lookup → grid + timezone + forecast zone + nearby station list (with primary/secondary picking)
- `NormalizerHelpers`: WMO unit conversion (degC, degF, km/h, m/s, Pa, m, percent, degrees), 16-point cardinal lookup, 90-min staleness check
- `ObservationNormalizer`: NWS observation → CurrentConditions with feelsLike fallback chain (heatIndex → windChill → tempF)
- `ForecastNormalizer`: order-walk day+night pairing (v0.1.1 rewrite — handles Overnight-skip + orphan-end + lone-Tonight cases), max(day,night) pair precip, 3-letter day-of-week (`SAT`), `MMM DD` date label
- `AlertNormalizer`: NWS alerts → domain Alerts sorted by tier rank then issuedAt desc, with nullable `sent` fallback to `effective` (v0.1.1)
- `WeatherNormalizer`: orchestrator running 5 parallel fetches; alerts wrapped in `runCatching` for partial-failure semantics (v0.1.1); station primary→fallback escalation with `meta.error` reporting; passes `cfg.timezone` through to ForecastNormalizer (v0.1.1)

#### Phase G: Persistence + reactive layer
- `SettingsRepository` (DataStore-backed): atomic read-modify-write in single `edit{}` (v0.1.1 fix), Snapshot exposes 13 fields including `isConfigured` derived flag
- `AlertAcknowledgmentRepository`: Set<String> of dismissed alert IDs, `pruneTo(activeIds)` to discard stale entries
- `WeatherRepository`: StateFlow<WeatherState> (Idle/Loading/Success/Error), polling driven by `meta.nextRefreshAt`, manual `refresh()` for pull-to-refresh, idempotent `startPolling`
- `DashboardViewModel`: `combine(WeatherRepository.state, acknowledgments.flow, settings.flow)` into single UiState with derived `visibleAlerts` and auto-pruning
- Hilt modules: `NetworkModule` (HttpClient with per-request UA callback v0.1.1), `CacheModule`, `SettingsModule`, `CoroutineModule` (application-scoped SupervisorJob)

#### Phase H: UI shell
- `TopBar`: status dot (online/offline) + ticking clock (configured timezone) + clickable location (cyan) + ≡ hamburger
- `Footer`: `LINK.<stationid>` (amber `[PIN]` suffix when force-secondary) + fetched-time + `T-Xs` countdown
- `HudBottomNavBar`: hand-rolled (not Material), 3 destinations (NOW / HOURLY / OUTLOOK), accent top-border, accent underline indicator (v0.1.1 width fix)
- `DashboardScaffold`: tier-driven accent provider, lifecycle-aware polling, screen swap on bottom-nav selection
- `HudGlowText`: stacked crisp + blurred Text producing CSS text-shadow equivalent

#### Phase I: NowScreen + widgets
- `WxIcons`: 9 weather icons (sun, moon, cloud, partly-day, partly-night, rain, snow, thunder, fog) hand-ported from web's icons.svg to Compose ImageVector; static (SMIL animations deferred to v2)
- `WxIcon`: dispatcher composable
- `HudHero`: hero temperature (96sp) + TEMP/FEEL line + tap-to-toggle °F/°C; larger 96dp icon for clear sky, 72dp otherwise
- `HudMetricBar`: label + filled bar + value + trend arrow (hidden when confidence=MISSING — v0.1.1)
- `NowScreen`: 5 metric bars (humidity, wind+cardinal, pressure, dewpoint, visibility) + Loading/Error states

#### Phase J: HourlyScreen
- `HudChart`: Compose Canvas line chart with vertical gradient fill, accent stroke
- `HourlyScreen`: temperature line chart over 12h + icon row + precipitation probability bars

#### Phase K: OutlookScreen
- `HudRangeBar`: rounded high/low range bar positioned within week's min..max axis
- `OutlookScreen`: 7-day rows with day name + icon + numeric high/low + range bar + precip %

#### Phase L: Alert UX foundation + ship
- `AlertBanner`: hazard-stripe top border in alternating tier accent + dark, event name in tier color, multi-alert expand/collapse, per-alert dismiss → AlertAcknowledgmentRepository
- Debug-seed: BuildConfig fields `DEBUG_SEED_ZIP` + `DEBUG_SEED_EMAIL` populate location config on first launch when SettingsRepository is unconfigured (debug builds only; release leaves blank)
- SMOKE_TEST.md manual verification checklist
- 96 unit tests, 0 failures

### v0.1.1-mvp (2026-05-17) — Final-review fixes

10 issues from the final code reviewer addressed before Plan 2:
- Locale-safe URL formatting (NwsClient) + regression test
- Timezone piping from settings to ForecastNormalizer
- ForecastNormalizer.normalizeDaily rewritten to order-walk algorithm (handles Overnight-skip, orphan cases) + 4 new tests
- Dynamic per-request User-Agent (no longer baked at singleton construction)
- AlertProperties.sent nullable + fallback to effective + test
- Alerts fetch wrapped in safe try → `meta.error = PARTIAL` instead of cascade failure
- SettingsRepository.update() now atomic (read-modify-write inside `edit{}`)
- HudBottomNavBar selected-tab underline width fixed (was zero)
- Removed duplicate `viewModel.onResume()` (Activity + LaunchedEffect both fired)
- HudMetricBar hides trend arrow when confidence=MISSING (no more misleading `·`)

Test count: 89 → 96 (7 new tests).

### Plan 2 — Full alert UX + trends (v0.2.0 / 2026-05-18)

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
- `HourlyScreen` precip bar uses `fillMaxHeight(fraction)` + `fillMaxWidth` instead of integer-arithmetic `.dp` (fixes zero-height for sub-3% probabilities)
- `NowScreen` removes `/ 1.0` no-op in pressure fillFraction
- `IconMapper` adds 8 missing NWS codes: `scttsra`, `hi_shwrs`, `fzra_sct`, `ra_fzra`, `ra_sn`, `sn`, `blizzard`, `cold` + 3 regression tests

Test count: 96 → 119 (23 new tests).

## What's pending

See [docs/ROADMAP.md](ROADMAP.md) for the full Plans 3–5 outline. Headline pending items:

- **Plan 3:** SettingsScreen (replaces Toast stub) + first-run onboarding + GPS autodetect + GitHub update polling
- **Plan 4:** Background WorkManager alert polling + system notifications (life-safety + severe channels) + 1050 Hz NWR-style notification audio + battery-optimization whitelist + POST_NOTIFICATIONS permission flow
- **Plan 5:** Release signing keystore + GitHub Actions APK build on tag + Play Store internal track + README install instructions

## How to run

```powershell
# Debug build
./gradlew :app:assembleDebug

# Install on connected device/emulator
./gradlew :app:installDebug
adb shell am start -n com.skyframe/.MainActivity

# Tests
./gradlew :app:testDebugUnitTest

# Pre-flight for manual testing
# Update DEBUG_SEED_ZIP + DEBUG_SEED_EMAIL in app/build.gradle.kts first
```

See [docs/SMOKE_TEST.md](SMOKE_TEST.md) for the manual verification checklist.

## Key conventions

- **HUD aesthetic via tier-driven accent.** `LocalHudAccent.current` is the single source of accent color; everything tints from it. Highest-severity visible alert wins.
- **No analytics, no telemetry, no third-party trackers.** Per [CLAUDE.md](../CLAUDE.md) hard rules.
- **Commits follow imperative + multi-paragraph body + `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` trailer.**
- **PROJECT_STATUS.md is updated in the same commit a feature ships.** Don't batch doc updates.
- **`_reference/` is the original web project, gitignored.** Read-only during port; deleted when no longer useful (probably after Plan 4 or 5).
