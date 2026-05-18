# Changelog

All notable changes to SkyFrame for Android. Format roughly follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) but pre-1.0 we don't strictly distinguish breaking changes from features — everything before v1.0 is "MVP scaffolding."

## [Unreleased]

Plan 3 (SettingsScreen + onboarding + GPS + GitHub update polling) is the next target — see [docs/ROADMAP.md](docs/ROADMAP.md).

---

## [v0.2.0] — 2026-05-18

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
- **`HourlyScreen` precip bar** no longer renders zero-height for sub-3% probabilities (integer-arithmetic `.dp` truncation replaced with `fillMaxHeight(fraction)` + `fillMaxWidth`).
- **`NowScreen` pressure `fillFraction`** dropped the `/ 1.0` no-op.
- **`IconMapper`** maps 8 previously-missing NWS codes: `scttsra`, `hi_shwrs`, `fzra_sct`, `ra_fzra`, `ra_sn`, `sn`, `blizzard`, `cold`.

### Changed

- **Test count: 96 → 119** (+23 new tests including the first-ever `WeatherNormalizerTest` orchestrator coverage that closes Plan 1's biggest gap).
- **`NormalizerHelpers` visibility** bumped from `internal` to `public` so `StationPreview` (different package) can reuse `isObservationStale`.
- **`DashboardUiState`** extended with `primaryStationId` + `secondaryStationId` fields.
- **`AlertBanner`** signature adds `onAlertClick: (Alert) -> Unit` parameter.
- **`NowScreen` / `HourlyScreen` / `OutlookScreen`** signatures add `onOpenForecast: (DailyPeriod) -> Unit` parameter.
- **`HudHero`** signature adds `onOpenForecast: () -> Unit` parameter.
- **`DashboardScaffold`** signature adds `nwsClient: NwsClient` parameter (passed from `MainActivity` via Hilt injection).

---

## [v0.1.1-mvp] — 2026-05-17

Post-review fixes from the final code review of v0.1.0-mvp. No new features; targeted correctness fixes before Plan 2 lands more UI on the data layer.

### Fixed

- **Locale-safe URL formatting** in `NwsClient.fmt()`. Lat/lon now uses `Locale.ROOT` so the JVM default locale (e.g. `de_DE`) can't produce `42,8744,-87,8633` which NWS rejects with 400. Regression test added with `Locale.GERMANY`.
- **Timezone piping** from `SettingsRepository.Snapshot.timezone` through to `ForecastNormalizer.normalizeHourly/Daily`. Hour labels and daily date grouping now reflect the location's TZ instead of the device's — fixes incorrect grouping when the phone is in a different timezone than the configured weather location.
- **Daily-period collapse algorithm** rewritten from date-bucketing to the reference's order-walk algorithm. Properly handles three special cases: standalone "Tonight" at window start, orphan day at window end, and "Overnight" period that shares a local date with the next day's "Day" (now correctly skipped). 4 new tests cover the orphan paths.
- **Daily date labels** now match the web's format: 3-letter day-of-week (`SAT`) and `MMM DD` (`MAY 16`) instead of the bucketed `SATURDAY` / `5/16` from v0.1.0.
- **Pair precipitation probability** now uses `max(day, night)` instead of `day ?? night`. Night-heavy storms (afternoon clear, evening rain) no longer have their precip understated.
- **Dynamic User-Agent header.** `NwsHttpClient.create` now takes a `userAgentProvider: () -> String` callback invoked per request, so the configured email reflects the current `SettingsRepository` state immediately after onboarding instead of staying at the unconfigured placeholder until app restart. Critical for NWS compliance.
- **Nullable `AlertProperties.sent`** with `effective` fallback in `AlertNormalizer`. NWS sometimes omits `sent`; previously this crashed deserialization and took down the entire dashboard. Test added.
- **Alerts failure is now partial.** `WeatherNormalizer` wraps `nws.activeAlerts()` in `runCatching`; a 503 on `/alerts/active` now produces `meta.error = PARTIAL` with an empty alert list instead of cascading to a full `WeatherNormalizer.load()` failure.
- **`SettingsRepository.update()` atomicity.** Read-modify-write now happens inside a single `dataStore.edit{}` block, preventing TOCTOU races that Plan 4's background WorkManager + foreground updates would have hit.
- **`HudBottomNavBar` selected-tab indicator** now has explicit width (28dp) so it actually renders. Previously the underline was a zero-width box.
- **Removed duplicate `viewModel.onResume()`.** Was being called from both `MainActivity.onResume` and `DashboardScaffold.LaunchedEffect`. Activity lifecycle is now the sole trigger.
- **`HudMetricBar` hides trend arrow when confidence == MISSING.** Plan 1 feeds `emptyList()` for recent observations so every trend was MISSING, which previously surfaced as an always-steady `·` that misled users. Plan 1B or Plan 2 will add `/observations?limit=6` history fetching to re-enable real arrows.

### Changed

- Test count: **89 → 96** (added 7 tests covering the reviewer-flagged gaps).

### Notes

- Source-of-truth final review report is preserved in the conversation log of the session that shipped v0.1.0-mvp → v0.1.1-mvp.

---

## [v0.1.0-mvp] — 2026-05-16

Initial Plan 1 milestone: working MVP dashboard with the full NWS data layer ported from the web project. See [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for the full implemented-feature list and [docs/superpowers/specs/2026-05-16-skyframe-android-design.md](docs/superpowers/specs/2026-05-16-skyframe-android-design.md) for the design.

### Added

#### Foundation
- Fresh git repo at `github.com/OniNoKen4192/SkyFrameAndroid`; original web project archived to `_reference/` (gitignored)
- Gradle 8.10.2 wrapper, AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.11, Hilt 2.52, Ktor 3.0.1, kotlinx.serialization, DataStore, WorkManager (dep only — first use in Plan 4)
- Min SDK 26 (Android 8.0), Target SDK 35 (Android 15)
- Adaptive launcher icon placeholder (real logo deferred to Plan 5)
- `.claude/settings.json` with broad permission allowlist + destructive-command denylist

#### Domain
- 13-tier `AlertTier` enum with base + dark colors ported 1:1 from the web's `TIER_COLORS`
- `AlertClassifier` with parameter-driven escalation (Tornado Warning + tornadoDamageThreat=CATASTROPHIC/CONSIDERABLE, Severe Thunderstorm Warning + thunderstormDamageThreat=DESTRUCTIVE)
- Full `WeatherResponse` domain model (8 types) with `@Serializable` annotations and `kotlinx.datetime.Instant` timestamps
- Unit conversion (F↔C, m·s⁻¹→mph, Pa→inHg) and trend rescaling

#### Data layer
- `NwsClient` with 5 endpoint methods and mandatory User-Agent header
- `NwsDtos` for `/points` (with astronomicalData), `/forecast`, `/forecast/hourly`, `/stations/.../observations/latest`, `/alerts/active`, and `/stations` list
- `WeatherCache<V>` (TTL, ConcurrentHashMap, injectable clock)
- `IconMapper` with hourly precip downgrade (<30%) and daily upgrade (≥50%) + shortForecast keyword tiebreak
- `TrendCalculator` with OLS linear-regression slope over up-to-6 observations
- `Geocoder` (Nominatim, keyless)
- `SetupResolver` for ZIP or "lat,lon" → NWS grid metadata
- `NormalizerHelpers` for WMO unit conversion (`wmoUnit:degC`, `wmoUnit:m_s-1`, etc.) and 16-point cardinal lookup
- `ObservationNormalizer`, `ForecastNormalizer`, `AlertNormalizer`
- `WeatherNormalizer` orchestrator with 5 parallel fetches and station primary→fallback escalation

#### Persistence + reactive
- `SettingsRepository` (DataStore-backed, 13-field Snapshot)
- `AlertAcknowledgmentRepository` (dismissed-set with auto-pruning)
- `WeatherRepository` with `StateFlow<WeatherState>` and polling driven by `meta.nextRefreshAt`
- `DashboardViewModel` combining 3 flows into single UiState with derived `visibleAlerts`
- Hilt modules: `NetworkModule`, `CacheModule`, `SettingsModule`, `CoroutineModule`

#### Theme
- `HudColors`, `HudAccent` + `LocalHudAccent` CompositionLocal
- `HudType` per-role styles backed by bundled IBM Plex Mono (OFL-licensed)
- `hudTextGlow` modifier (RenderEffect on API 31+, graceful no-op below)
- `HudTheme` with Material 3 ColorScheme override

#### UI
- `DashboardScaffold` with tier-driven accent flow
- `TopBar` (status dot, ticking clock in configured TZ, clickable location, ≡ hamburger)
- `Footer` (LINK.station + amber `[PIN]` suffix + fetched-time + T-Xs countdown)
- `HudBottomNavBar` with 3 destinations (NOW/HOURLY/OUTLOOK)
- `NowScreen` with hero temp (tap-toggle F/C) + 5 metric bars + trend arrows
- `HourlyScreen` with Canvas line chart + icon row + precip bars
- `OutlookScreen` with 7-day high/low range bars
- `AlertBanner` with hazard-stripe top border, multi-alert expand/collapse, per-alert dismiss
- 9 weather icons (sun, moon, cloud, partly-day, partly-night, rain, snow, thunder, fog) hand-ported from web's `icons.svg` to Compose `ImageVector` (static — SMIL animations deferred to v2)
- `HudGlowText`, `HudHero`, `HudMetricBar`, `HudChart`, `HudRangeBar`, `WxIcon`

#### Tooling
- Debug-seed mechanism (`DEBUG_SEED_ZIP` + `DEBUG_SEED_EMAIL` BuildConfig fields in debug builds) for Plan 1 testing without Settings screen
- [SMOKE_TEST.md](docs/SMOKE_TEST.md) manual verification checklist
- 89 unit tests, 0 failures (~2.1s runtime)
