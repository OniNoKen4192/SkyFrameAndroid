# SkyFrame Android — Design Spec

**Date:** 2026-05-16
**Target repo:** https://github.com/OniNoKen4192/SkyFrameAndroid
**Status:** Approved (brainstorming phase complete); awaiting implementation plan

## Goal

Fork the existing SkyFrame web app (React 18 + Vite + Fastify, currently at v1.2.6) to a native Android application with **full v1.2.6 user-visible feature parity plus background severe-weather notifications**. Same product, same HUD aesthetic, same "single user, local, no third parties, no telemetry, no API keys" ethos — adapted to native Android idioms.

The web project continues independent life at its own GitHub repo. This is a hard fork, not a port that preserves history.

## Non-goals (v1)

Explicitly out of scope to prevent creep:

- iOS app — deferred pending user demand survey + willingness to accept a server (required by APNs for reliable background notifications)
- Multiple saved locations (web is single-location; parity = single-location)
- Home screen widget
- Wear OS, Android Auto, tablet-optimized layouts
- Color picker / theme switcher (already a web v2 backlog item)
- Offline forecast cache for "last-known display" when no network
- Multi-user / cross-device sync

## Top-level architectural decisions

| Decision | Value | Rationale |
|---|---|---|
| Platform | Pure Android, Kotlin + Jetpack Compose | Native UX, life-safety background work, idiomatic Android. iOS deferred without speculative KMP scaffolding. |
| Backend model | In-app, no server | Phone calls NWS directly. Matches "no third parties" rule cleanly. Self-contained APK works on cellular data. |
| UI fidelity | HUD aesthetic preserved, layout adapted for phone ergonomics | Cyan-on-black, monospace, custom-drawn HUD widgets — same as web. Bottom navigation, full-screen sheets, system back behavior — native Android. |
| Distribution | Both Play Store + sideload APK from GitHub releases | Play for ease of install + auto-updates; APK for users who avoid Google account dependencies. |
| Background alerts | WorkManager 15-min baseline + expedited 2-min escalation when top-tier alert active | The headline native feature; the reason for going native over PWA. |
| Module structure | Single `app/` Android module, Hilt DI | Standard Android, no KMP/multiplatform overhead. |
| Min SDK | API 26 (Android 8.0) | ~98% device coverage as of 2026. Floor for Compose, DataStore, and notification channels — all of which we use. |
| Target SDK | API 35 (Android 15) | Current latest, required by Play Store policy. |

## Repo migration (one-time)

1. **Stash existing web project** into `_reference/` at the current `e:\SkyFrame - Android` root. Move all current files and directories — including the existing `.git/` — into the new `_reference/` subfolder.
2. **Add `_reference/` to `.gitignore`** for the new Android repo so it never enters Android git history but stays on disk.
3. **Initialize fresh git repo** at the project root: `git init`, `git remote add origin https://github.com/OniNoKen4192/SkyFrameAndroid.git`.
4. **First commit** includes: Android Studio Kotlin/Compose project skeleton + carried-over docs (PROJECT_SPEC.md, WEATHER_PROVIDER_RESEARCH.md, this design doc, ALERT_TIERS.md distilled from `_reference/shared/alert-tiers.ts`) + rewritten CLAUDE.md and README.md for the Android stack.
5. **`_reference/` available to Read but never to Edit** — used as a porting source during implementation, then deleted when porting is complete.

The web project's git history is **not** preserved in the Android repo. Every file is being replaced; carrying ~50 commits of React/Fastify history into a Kotlin repo adds noise without value. The web project itself stays alive at its own remote, untouched.

## Project structure

```
SkyFrameAndroid/
├── app/                                  Standard Android module
│   └── src/
│       ├── main/
│       │   ├── kotlin/com/skyframe/
│       │   │   ├── MainActivity.kt
│       │   │   ├── SkyFrameApp.kt        Application class — Hilt entry, WorkManager init
│       │   │   ├── ui/
│       │   │   │   ├── shell/            DashboardScaffold (top bar, bottom nav, alert banner)
│       │   │   │   ├── screens/          NowScreen, HourlyScreen, OutlookScreen, SettingsScreen
│       │   │   │   ├── sheets/           AlertDetailSheet, ForecastNarrativeSheet, StationOverrideSheet
│       │   │   │   ├── widgets/          HudHero, HudMetricBar, HudRangeBar, HudChart, etc.
│       │   │   │   ├── onboarding/       WelcomeScreen, PermissionFlow
│       │   │   │   └── nav/              NavGraph, BottomNavDestinations
│       │   │   ├── theme/                HudColors, HudAccent, HudType, hudTextGlow modifier
│       │   │   ├── data/
│       │   │   │   ├── nws/              Ktor client, normalizer, icon mapping, trends, station fallback
│       │   │   │   ├── alerts/           Tier classification (port of shared/alert-tiers.ts)
│       │   │   │   ├── cache/            In-memory TTL cache (port of server/nws/cache.ts)
│       │   │   │   ├── settings/         DataStore-backed SettingsRepository
│       │   │   │   ├── geocoding/        ZIP → lat/lon (provider matched to web's setup.ts)
│       │   │   │   ├── acknowledgments/  Dismissed + sound-acknowledged sets
│       │   │   │   ├── stations/         Station override (auto / force-secondary)
│       │   │   │   └── updates/          GitHub release polling (parity with web update-check)
│       │   │   ├── notifications/        Channels, builders, IDs, full-screen intents
│       │   │   ├── background/           WorkManager workers + scheduler (alert poll, update poll)
│       │   │   ├── domain/               WeatherResponse, CurrentConditions, HourlyPeriod,
│       │   │   │                         DailyPeriod, Alert, AlertTier, WeatherMeta, IconCode
│       │   │   ├── repository/           WeatherRepository, AlertRepository, etc.
│       │   │   └── viewmodel/            StateFlow-based VMs per screen
│       │   ├── res/
│       │   │   ├── raw/
│       │   │   │   ├── notification_life_safety.ogg   1050 Hz NWR-style sustained tone
│       │   │   │   └── notification_severe.ogg        Shorter tone for severe-warning tier
│       │   │   └── values/
│       │   └── AndroidManifest.xml
│       └── test/                         JVM unit tests (pure logic + repository tests + WorkManager)
├── docs/
│   ├── PROJECT_SPEC.md                   Carried from web _reference/
│   ├── WEATHER_PROVIDER_RESEARCH.md      Carried from web _reference/
│   ├── ALERT_TIERS.md                    New, distilled from _reference/shared/alert-tiers.ts
│   └── superpowers/specs/
│       └── 2026-05-16-skyframe-android-design.md   This file
├── gradle/libs.versions.toml             Centralized version catalog
├── build.gradle.kts (root)
├── settings.gradle.kts
├── CLAUDE.md                             Rewritten for Android stack
├── README.md                             Rewritten for Android install flow
├── keystore.properties (gitignored)      Release signing config
├── .gitignore                            Includes _reference/, .gradle/, build/, *.keystore, etc.
└── _reference/ (gitignored)              Original web project on disk for porting
```

## UI / screens / navigation

### Layout shell (always-visible chrome)

```
┌─────────────────────────────────┐
│  [Alert banner — if any]        │  Conditional, hazard-stripe top
├─────────────────────────────────┤
│  ● 21:43  OAK CREEK, WI    ≡    │  TopBar: status dot + time +
│                                 │  clickable location (opens Settings) + hamburger
├─────────────────────────────────┤
│                                 │
│         SCREEN CONTENT          │  Swappable: NowScreen / HourlyScreen / OutlookScreen
│         (one of three)          │
│                                 │
├─────────────────────────────────┤
│  LINK.KMKE   T-90s   12:42:17   │  Footer: small, ~32dp tall
├─────────────────────────────────┤
│  ◉NOW    ░HOURLY    █OUTLOOK    │  HudBottomNavBar (HUD-themed,
└─────────────────────────────────┘  not Material default)
```

### Three primary screens

- **NowScreen** — port of `_reference/client/components/CurrentPanel.tsx`. Hero temperature (tap toggles °F/°C, persisted to DataStore), 5 metric bars (humidity, wind, pressure, dewpoint, visibility) with trend arrows, ▶ glyph next to TEMP/FEEL opens ForecastNarrativeSheet.
- **HourlyScreen** — port of `HourlyPanel.tsx`. Compose `Canvas` draws the line chart matching the web's SVG rendering (gradient stroke, glow effect, icons row, precip-probability bars). ▶ glyph opens ForecastNarrativeSheet.
- **OutlookScreen** — port of `OutlookPanel.tsx`. 7-day list of high/low range bars + icons + precip %. Tapping a day-row label opens that day's ForecastNarrativeSheet.

Web "ALL" tab is dropped — a single screen can't comfortably show everything at phone width, and bottom-nav swipe between three screens covers the same ground better.

### Overlay surfaces

Web modals become **bottom sheets** styled with HUD chrome (cyan border, monospace title, accent-glow header):

- **AlertDetailSheet** — full-screen modal sheet. Triggered by tapping an alert event name in the AlertBanner. Renders NWS `description` text with HAZARD/SOURCE/IMPACT prefixes tier-colored. Meta line: issued/expires/area. Closes via system back, close glyph, or swipe down.
- **ForecastNarrativeSheet** — full-screen modal sheet. Day + night sections stacked, NWS-preserved period names ("THIS AFTERNOON" / "TONIGHT" / "FRIDAY" / etc.).
- **StationOverrideSheet** — small bottom sheet anchored to footer station tap. AUTO / FORCE SECONDARY radios + live preview rows for both stations (ID, observed time, temp, status). Preview data fetched on open via parallel calls to both stations.

### Full-screen routes

- **SettingsScreen** — full-screen Compose Navigation destination. Same form fields as web Settings modal: location (with GPS button), email (for NWS User-Agent), update-check checkbox, cosmetic-skin placeholder. Opens via `≡` hamburger or by tapping location name in TopBar.
- **OnboardingFlow** — first-launch only: Welcome → permission sequence → Settings setup. System back intercepted until setup completes.

### Navigation graph

```
NavHost (root)
├── OnboardingRoute     (first launch only; auto-skipped on subsequent launches)
├── DashboardRoute      (the always-visible shell + bottom nav)
│   ├── NowScreen       [start destination]
│   ├── HourlyScreen
│   └── OutlookScreen
└── SettingsRoute       (full-screen, no bottom nav)

Sheets are shell-level state, not nav destinations — they overlay
whatever DashboardRoute screen is active.
```

### Phone-specific UX

- **Pull-to-refresh** on each screen — triggers immediate `WeatherRepository.refresh()`. Polling is automatic on the same ~90s cycle as web when foregrounded.
- **System back** — closes any open sheet first; if no sheet open, bottom nav handles back as standard.
- **Edge-to-edge** — content draws under the status bar with appropriate insets. Pairs naturally with the dark HUD background.
- **Orientation** — portrait optimized; landscape supported but uses the same layout rotated. True tablet-optimized two-pane layouts deferred to v2.

## Data layer

### HTTP + serialization

- **Ktor Client** with OkHttp engine. Default request interceptor sets `User-Agent: SkyFrame/{version} ({email})` per NWS requirement.
- **kotlinx.serialization** for JSON DTOs. `@Serializable` data classes, code-generated marshaling.
- **kotlinx-coroutines + StateFlow** for reactive state. ViewModels expose `StateFlow<UiState>`, Composables consume via `collectAsStateWithLifecycle()`.

### Domain model

Port of `_reference/shared/types.ts` to Kotlin data classes:

```kotlin
@Serializable data class WeatherResponse(
    val current: CurrentConditions,
    val hourly: List<HourlyPeriod>,
    val daily: List<DailyPeriod>,
    val alerts: List<Alert>,
    val meta: WeatherMeta,
)

@Serializable data class CurrentConditions(...)   // hero + 5 metrics + trends
@Serializable data class HourlyPeriod(...)        // 12+h chart points with icon, temp, precipProb
@Serializable data class DailyPeriod(
    val date: LocalDate,
    val high: Int,
    val low: Int,
    val icon: IconCode,
    val precipProb: Int,
    val dayDetailedForecast: String?,
    val nightDetailedForecast: String?,
    val dayPeriodName: String?,
    val nightPeriodName: String?,
)
@Serializable data class Alert(
    val id: String,
    val event: String,
    val tier: AlertTier,
    val issuedAt: Instant,
    val expiresAt: Instant?,
    val description: String,
    val area: String,
)

enum class AlertTier(val rank: Int, val accent: Long) {
    TORNADO_EMERGENCY(1, 0xFFff0044),
    TORNADO_PDS(2, 0xFFff22aa),
    TORNADO_WARNING(3, 0xFFff3344),
    TSTORM_DESTRUCTIVE(4, 0xFFdc1437),
    SEVERE_WARNING(5, 0xFFff8800),
    BLIZZARD(6, 0xFFb0e6ff),
    WINTER_STORM(7, 0xFF80c8ff),
    FLOOD(8, 0xFF44ddaa),
    HEAT(9, 0xFFffaa44),
    SPECIAL_WEATHER_STATEMENT(10, 0xFFffcc00),
    WATCH(11, 0xFFcccccc),
    ADVISORY_HIGH(12, 0xFFffaa22),
    ADVISORY(13, 0xFF22d3ee),
}

@Serializable data class WeatherMeta(
    val nextRefreshAt: Instant,
    val forecastGeneratedAt: Instant?,
    val timezone: String,
    val forecastOffice: String,
    val gridX: Int,
    val gridY: Int,
    val forecastZone: String,
    val primaryStation: String,
    val activeStation: String,
    val stationOverride: StationOverride,    // AUTO | FORCE_SECONDARY
    val error: WeatherError?,                // null on success
)

enum class IconCode { SUN, MOON, CLOUD, PARTLY_DAY, PARTLY_NIGHT, RAIN, SNOW, THUNDER, FOG }
```

Color values are placeholders pending exact port from `_reference/shared/alert-tiers.ts` `TIER_COLORS`.

### NWS client surface

```kotlin
class NwsClient(private val http: HttpClient, private val userAgent: String) {
    suspend fun points(lat: Double, lon: Double): PointsDto
    suspend fun forecast(office: String, x: Int, y: Int): ForecastDto
    suspend fun hourlyForecast(office: String, x: Int, y: Int): ForecastDto
    suspend fun latestObservation(stationId: String): ObservationDto
    suspend fun activeAlerts(lat: Double, lon: Double): AlertsDto
}

class WeatherNormalizer(
    private val nws: NwsClient,
    private val cache: WeatherCache,
    private val config: SettingsRepository,
    private val stationOverride: StationOverrideRepository,
    private val updateCheck: UpdateCheckRepository,
) {
    suspend fun load(): WeatherResponse  // orchestrates parallel fetch + normalize
}
```

Direct port of `_reference/server/nws/client.ts` + `_reference/server/nws/normalizer.ts`. Parallel fetch via `coroutineScope { async { ... } }` instead of `Promise.all`. Unit conversion (°C→°F, m/s→mph, Pa→inHg), icon mapping (with the ≥50% daily upgrade and <30% hourly downgrade thresholds), trends computation, and alert filtering/sorting are pure functions ported one-to-one.

### Setup resolver (ZIP → grid)

```kotlin
class SetupResolver(private val nws: NwsClient, private val geocoder: Geocoder) {
    suspend fun resolve(input: LocationInput): ResolvedSetup
}

sealed interface LocationInput {
    data class Zip(val zip: String) : LocationInput
    data class LatLon(val lat: Double, val lon: Double) : LocationInput
}

data class ResolvedSetup(
    val lat: Double,
    val lon: Double,
    val office: String,
    val gridX: Int,
    val gridY: Int,
    val timezone: String,
    val forecastZone: String,
    val primaryStation: String,
    val secondaryStation: String,
)
```

Geocoder uses the same provider as the web's `_reference/server/nws/setup.ts` — to be confirmed during port (likely Census Geocoder or zippopotam.us, both keyless). For `LocationInput.LatLon`, geocoder is skipped.

### Repository layer

- `WeatherRepository` — wraps `WeatherNormalizer` + manages polling lifecycle (foreground 90s poll, background WorkManager handled separately). Exposes `StateFlow<WeatherState>` where `WeatherState = Loading | Success(WeatherResponse) | Error(String)`.
- `SettingsRepository` — DataStore Preferences-backed. Exposes `StateFlow<Settings>`; `suspend fun update(transform: (Settings) -> Settings)` for writes.
- `AlertAcknowledgmentRepository` — dismissed-set + sound-acknowledged-set, persisted to DataStore. Pruning logic: when an alert ID drops off the NWS feed, drop from sets too. Port of web's localStorage pattern.
- `StationOverrideRepository` — AUTO | FORCE_SECONDARY mode, persisted to DataStore. Live updates trigger immediate `WeatherRepository.refresh()`.
- `UpdateCheckRepository` — wraps GitHub releases polling. Cached available-update state. Disabled by default; opt-in via Settings checkbox.

### Caching

- **In-memory TTL cache** (`WeatherCache`) — port of `_reference/server/nws/cache.ts`. 90s TTL on full WeatherResponse, longer (1h) on `/points` results since grid coordinates don't change.
- **No disk-backed forecast cache for v1.** Parity with web. Cold start always fetches.

## Background alerts & notifications

The headline native feature — the reason for going native over a PWA.

### Scheduling

- **`PeriodicWorkRequest`** registered with WorkManager, 15-min interval (Android minimum). Triggers `AlertCheckWorker`, which:
  1. Calls `NwsClient.activeAlerts(lat, lon)`.
  2. Classifies via `AlertTierClassifier`.
  3. Diffs against last-known set (persisted in DataStore).
  4. For each new alert not in `AlertAcknowledgmentRepository`, fires a notification on the appropriate channel.
- **`ExpeditedWorkRequest` escalation** — when last poll returned a top-tier active alert (tornado-warning / tornado-pds / tornado-emergency / tstorm-destructive), chain a one-shot expedited worker on a tighter ~2-min cadence for as long as a top-tier alert remains active. Expedited work runs with foreground-service-like priority and bypasses the 15-min floor.
- **Constraints:** `setRequiresBatteryNotLow(false)` and `setRequiredNetworkType(CONNECTED)`. Severe weather doesn't pause for low battery.
- **Battery optimization whitelist** — first-launch politely prompts via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with rationale explaining doze impact. User can decline; app still works but background reliability degrades on aggressive OEMs (Samsung, Xiaomi).

### Notification channels

Grouped by user-relevant urgency, not 1:1 with the 13 tiers:

| Channel ID | Importance | Sound | DND bypass | Tiers |
|---|---|---|---|---|
| `life_safety` | HIGH + full-screen intent | `notification_life_safety.ogg` (looping) | yes (`canBypassDnd = true`) | tornado-emergency, tornado-pds, tornado-warning, tstorm-destructive |
| `severe_weather` | HIGH | `notification_severe.ogg` (single play) | no | severe-warning, blizzard, winter-storm |
| `watches` | DEFAULT | system default | no | flood, heat, watch |
| `advisories` | LOW | none | no | special-weather-statement, advisory-high, advisory |
| `app_updates` | MIN | none | no | (GitHub update alerts) |

Channel groups (API 26+) make these individually editable in system settings.

**Full-screen intent** for `life_safety` channel uses Android 14+'s `USE_FULL_SCREEN_INTENT` permission (requested explicitly with rationale dialog).

### Notification audio (1050 Hz NWR-style)

Bundled `.ogg` files in `res/raw/`, generated once from a Python/ffmpeg script committed to the repo (`tools/generate-notification-audio.py` or equivalent). The script generates:

- `notification_life_safety.ogg` — 1050 Hz sustained tone, ~500ms on / 1000ms off, ~3 cycles for looping channel. Inspired by the NOAA Weather Radio Warning Alarm Tone (WAT) character; deliberately NOT EAS Attention Signal (853+960 Hz) or SAME header bursts, per 47 CFR § 11.45 legal constraints.
- `notification_severe.ogg` — single ~800ms 1050 Hz tone for severe-warning tier.

A comment in the generator script explicitly documents the legal constraint so future changes don't drift toward EAS reproduction.

### Notification content

```
Title:   ⚠ TORNADO WARNING                  [tier color in accent line]
Body:    Until 22:15 · Milwaukee County
         The National Weather Service has issued a Tornado…
Actions: [VIEW DETAILS]  [DISMISS]
```

- **Tap notification** → opens app to AlertDetailSheet for that alert (deep-link via `PendingIntent` with alert ID extra).
- **DISMISS action** → marks alert ID as dismissed in `AlertAcknowledgmentRepository`. Prevents re-notification.
- **Notification ID** = stable hash of `alert.id` so re-firing updates instead of stacks.
- **Bidirectional sync** — in-app dismissal calls `NotificationManagerCompat.cancel(notificationId)` so the shade clears too.

### Update-check scheduler

Port of `_reference/server/updates/update-check.ts`. Runs at app start and via WorkManager `PeriodicWorkRequest` at local midnight when user has enabled GitHub release checking. Polls `https://api.github.com/repos/OniNoKen4192/SkyFrameAndroid/releases/latest`. Newer tag than installed `versionName` injects a synthetic `advisory`-tier alert with release notes — identical to web behavior. No outbound requests when checkbox is off.

## Theming / HUD aesthetic

### Color system

```kotlin
// theme/HudColors.kt
object HudColors {
    val BackgroundDeep   = Color(0xFF050a10)   // recessed bands (title bar interior)
    val BackgroundBase   = Color(0xFF0a1018)   // main background
    val BackgroundPanel  = Color(0xFF0e1620)   // panel surfaces
    val Foreground       = Color(0xFFc6ecff)   // body text
    val ForegroundDim    = Color(0xFF7a96a8)   // labels, footer text
}

@Immutable data class HudAccent(
    val accent: Color,
    val accentRgb: IntArray,
    val glowSoft: Color,      // accent at 0.15 alpha
    val glowMedium: Color,    // accent at 0.30 alpha
    val glowStrong: Color,    // accent at 0.50 alpha
)

val LocalHudAccent = compositionLocalOf<HudAccent> { error("No HudAccent provided") }
```

Active accent is held in shell-level state (`HudAccentProvider`), driven by the highest-severity *visible* alert (identical to web rule). Shell wraps content in `CompositionLocalProvider(LocalHudAccent provides currentAccent)`. Composables read `LocalHudAccent.current` — Compose recomposition propagates changes when a new tornado warning arrives (entire UI shifts to red).

Exact color values are ported 1:1 from `_reference/shared/alert-tiers.ts` `TIER_COLORS` during implementation.

### Typography

IBM Plex Mono bundled as a resource (OFL-licensed, free + redistributable):

```
app/src/main/res/font/
    ibm_plex_mono_regular.ttf
    ibm_plex_mono_medium.ttf
```

`HudType` object exposes per-role `TextStyle`s (`titleBar`, `metaLabel`, `bodyMono`, `heroTemp`, etc.) mapped 1:1 from `_reference/client/styles/hud.css` and `terminal-modal.css`.

### Glow effect

Compose has no native `text-shadow`. Custom `Modifier.hudTextGlow(color, radius)` extension uses `Modifier.graphicsLayer { renderEffect = BlurEffect(...) }` for Android 12+ (API 31+, native Skia-backed blur via `RenderEffect.createBlurEffect`), with a layered-draw fallback (multiple offset draws of the text with low alpha) for API 26–30. Wraps Text calls as `Text("21:43", modifier = Modifier.hudTextGlow(...))`. The fallback is visually close but slightly less smooth — acceptable for the ~5% of devices on API 26–30 by 2026.

### Material 3 escape hatches

Compose pulls Material 3 by default. We override `MaterialTheme.colorScheme` at root with a `ColorScheme` whose `surface`, `onSurface`, `primary`, etc. resolve to HUD colors — incidentally-used Material widgets (ModalBottomSheet, Snackbar) inherit HUD palette instead of looking like default Material sore thumbs.

Bottom navigation is hand-rolled as `HudBottomNavBar` Composable, NOT Material's `NavigationBar` (which has purple-pill defaults that don't fit). Hazard-stripe top border, monospace labels, tap states matching `_reference/client/styles/hud.css` `.tab-button`.

### Icons

Web uses inline SVG sprite with SMIL animations. Android equivalent:

- **`ImageVector`** — Compose-native vector format. Hand-converted from each SVG via `valkyrie` tool or manual port. ~9 icons (sun, moon, cloud, partly-day, partly-night, rain, snow, thunder, fog).
- **SMIL animations** don't port directly. Rewritten as Compose `Transition`-based property tweens — e.g., sun-ray rotation becomes a `rememberInfiniteTransition` rotating a child `ImageVector`.

Per-platform glow rendering needs a brief tuning pass during implementation since Skia handles blur slightly differently across Android versions; acceptable cost.

## First-run / onboarding

1. **Splash** — single Compose screen with SkyFrame logo + accent cyan. ~200ms while DataStore loads asynchronously.
2. **Welcome screen** (first run only) — single screen explaining the product ("local, ad-free, NOAA-only, no telemetry"). Single "GET STARTED" button.
3. **Permission sequence:**
   - Location (FINE) — rationale: "Used to look up your local forecast and severe weather alerts. You can also enter a ZIP manually."
   - POST_NOTIFICATIONS — rationale: "Required to alert you to severe weather when the app isn't open. Tornado warnings, flash floods, and other life-safety alerts."
   - USE_FULL_SCREEN_INTENT (only if POST_NOTIFICATIONS granted) — rationale: "Lets life-threatening alerts show on your lock screen even when your phone is silent."
   - Battery optimization whitelist — optional, last step.
4. **Location setup** — full SettingsScreen, same form as web. GPS button enabled if location permission granted (autofills lat/lon); manual ZIP entry always available. Email field (for NWS User-Agent). Update-check checkbox (default off). Save button is the "complete onboarding" trigger.
5. **First fetch** — return to NowScreen, spinner during initial weather load.

System back is intercepted on screens 2–4 until setup completes. After completion, normal back behavior resumes; SettingsScreen reachable via hamburger and behaves identically to web Settings modal.

**Declined-permission persistent banners:**
- POST_NOTIFICATIONS declined → SettingsScreen shows persistent yellow banner: "SEVERE WEATHER ALERTS DISABLED — system notification permission required" + GRANT button deep-linking to system settings.
- Location declined → same pattern: "GPS UNAVAILABLE — system location permission required" with deep-link.

## Testing strategy

Mirrors web's "pure-helper tests cover messy logic; UI is hand-verified" posture.

- **Pure logic unit tests** (JVM, JUnit5 + MockK):
  - `AlertTierClassifier` — equivalent of web's `classifyAlert` tests
  - `WeatherNormalizer` — canned NWS JSON fixtures (lifted from `_reference/server/nws/__tests__/`), assert normalized output
  - `IconMapper` — NWS icon URL → IconCode incl. probability thresholds
  - `TrendCalculator` — 6-observation rolling trend
  - `UnitConverter` — °C↔°F, m/s↔mph, Pa↔inHg
  - `UpdateChecker` — version comparison, GitHub release JSON parsing
  - `AlertDiff` — "what's new since last poll" predicate (drives notification firing)
- **Repository tests** (JVM, fake `NwsClient`):
  - `WeatherRepository` — polling cadence, station fallback orchestration, error states
  - `AlertAcknowledgmentRepository` — dismissed-set persistence + pruning
  - `StationOverrideRepository` — mode persistence + cache invalidation
- **WorkManager tests** (`WorkManagerTestInitHelper`):
  - `AlertCheckWorker` fires correct notifications for new alerts
  - Top-tier active alert escalates polling to expedited cadence
  - Acknowledged alerts don't re-notify
- **No Compose UI tests in v1.** Same posture as web. Compose UI testing infrastructure deferrable to v2 if a specific UI bug becomes hard to hand-verify.

Target: ~200 unit tests at parity with the web's 260 (some web tests are server-architecture-specific and don't port).

## Distribution, signing, versioning

- **Versioning:** Android `versionName` is the product version (v1.0.0 for initial Android release, detached from web's v1.2.6 numbering — Android is its own product line). `versionCode` is monotonically incremented integer (1 for v1.0.0, 2 for v1.0.1, etc.).
- **Signing:** Release keystore generated locally, backed up offline (NOT in repo). `keystore.properties` referenced by `app/build.gradle.kts`, gitignored.
- **APK distribution (sideload path):** GitHub Actions workflow on `git tag v*` builds release APK + uploads as a GitHub release asset. Update-check feature polls `/repos/OniNoKen4192/SkyFrameAndroid/releases/latest` to detect newer versions.
- **Play Store distribution:** Internal track first → closed beta → production. Data Safety form declares "no data collected." Permissions justification text drafted as part of v1.0 release prep.
- **R8/ProGuard** — enabled for release builds with explicit rules for Ktor, kotlinx.serialization, Hilt, and any other reflection-using libs. Standard Android ship config.

## Key NWS-related constraints (carried from web)

- **User-Agent header required** on every NWS request. Format: `SkyFrame/{version} ({email})`. Missing or generic User-Agent can be rate-limited or rejected.
- **No API keys, no account-gated providers.** NWS only.
- **No third-party services** beyond NWS itself (GitHub for release polling is single-user opt-in, not "data sharing").
- **No telemetry, analytics, crash reporting.** Hard rule.
- **Minimize dependencies.** Keep the dependency graph small and vetted.
- **Station fallback** when primary observation is older than ~90 min or has null core fields; secondary station configured in setup. Manual override (AUTO / FORCE_SECONDARY) per StationOverrideRepository.
- **EAS Attention Signal and SAME header tones must NOT be reproduced** (47 CFR § 11.45). Only NWR-style 1050 Hz sustained tone is used for notification audio.

## Open questions for implementation phase

- **Geocoding provider** — confirm during port whether `_reference/server/nws/setup.ts` uses Census Geocoder, zippopotam.us, or another keyless provider. Match exactly so resolution behavior is identical to the web's first-run flow.

## What's NOT in this design (explicit YAGNI)

- iOS app (see "Non-goals")
- Multi-location switching
- Home screen widget
- Wear OS, Android Auto, tablet two-pane layouts
- Color picker / theme switcher
- Offline cache
- Multi-user / sync
- Voice assistant integration (Google Assistant App Actions)
- Sharing forecasts / screenshots
- In-app weather radar imagery

Each is a defensible v2+ feature; none belong in the first parity-and-background-alerts release.

---

**Approved by user on 2026-05-16. Next step: write implementation plan via the writing-plans skill.**
