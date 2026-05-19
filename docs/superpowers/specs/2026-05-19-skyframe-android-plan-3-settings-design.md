# SkyFrame Android — Plan 3: Settings + Onboarding + GPS + Update Polling Design Spec

**Date:** 2026-05-19
**Builds on:** [v0.2.0](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.2.0) (Plan 2 complete)
**Target tag:** `v0.3.0`
**Status:** Approved (brainstorming phase complete); awaiting implementation plan

## Goal

Replace the Settings Toast stub with a real full-screen `SettingsScreen`, add a first-run onboarding flow that forces config completion before reaching the dashboard, ship GPS-based location autodetect via platform `LocationManager` (no Play Services dep), and add opt-in GitHub release polling for the sideload distribution path only (Play Store users get updates from Google Play; the checkbox is hidden for them).

## What ships

1. **Compose Navigation NavHost** with two destinations (`dashboard`, `settings`). `MainActivity` switches from rendering `DashboardScaffold` directly to hosting the NavHost. Start destination decided at first composition based on `SettingsRepository.snapshot().isConfigured` — first-run users land on settings; configured users land on dashboard.
2. **SettingsScreen** — full-screen Compose route, HUD-styled. Form fields: LOCATION (text input + GPS button), EMAIL, conditional checkbox `Check GitHub for SkyFrame updates`, disabled cosmetic-skin placeholder, CANCEL + SAVE. On first launch: CANCEL hidden, system back swallowed until SAVE completes.
3. **First-run onboarding** — `MainActivity` routes to `settings` when `!isConfigured`. No welcome screen — straight to the form. Debug-seed mechanism remains as a dev-only fallback for testing.
4. **GPS autodetect button** — `USE MY LOCATION` button. Just-in-time `ACCESS_FINE_LOCATION` permission request via `ActivityResultContracts.RequestPermission`. Uses platform `LocationManager.getLastKnownLocation()` (NETWORK then GPS provider fallback). On grant + last-known-location available: populates LOCATION as `"lat, lon"` with 4-decimal precision. On deny: button shows "GPS UNAVAILABLE — open system settings" with deep-link via `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
5. **Conditional GitHub release polling** — `UpdateCheckRepository` polls `https://api.github.com/repos/OniNoKen4192/SkyFrameAndroid/releases/latest` on app start + foreground transition, throttled to once per 24h via `lastCheckedAt` in DataStore. Polls **only when**: install source is NOT Play Store (via `PackageManager.getInstallSourceInfo` / `getInstallerPackageName`) AND user's checkbox is enabled. Newer tag than `BuildConfig.VERSION_NAME` → cache `UpdateAvailable(version, htmlUrl, body)`.
6. **Synthetic update alert injection** — `WeatherNormalizer` prepends an `advisory`-tier `Alert(id = "update-${version}", event = "Update Available", description = body, ...)` to `WeatherResponse.alerts` when `UpdateCheckRepository.currentAvailable()` is non-null. Reuses existing AlertBanner + AlertDetailSheet pipeline; tap → opens detail sheet with release notes inline.
7. **`isUpdateAlert(alert) -> Boolean`** helper in `AlertDescriptionFormat` (port of web's `isUpdateAlert`). AlertDetailSheet meta line skips the EXPIRES segment for update alerts since their far-future `expires` is meaningless.

## Non-goals (out of Plan 3 scope)

- **POST_NOTIFICATIONS permission** — deferred to Plan 4 when notifications actually fire. Requesting a permission you don't use is a known anti-pattern.
- **USE_FULL_SCREEN_INTENT permission** — Plan 4.
- **WorkManager infrastructure** — Plan 4 introduces WorkManager for background alert polling. Plan 3's update check is foreground-only (24h-throttled on app start / foreground transition).
- **FusedLocationProviderClient** — Play Services dependency. Platform `LocationManager` is sufficient for the "set my home location once" use case and matches the "minimize dependencies" hard rule.
- **Real cosmetic skin switching** — the disabled "Default (HUD cyan)" select is a placeholder per the original design spec. Actual theming is a v2+ feature.
- **Tap-to-open-browser on update alert** — `AlertDetailSheet` renders the release notes text inline; opening the GitHub release URL in a browser requires an `Intent.ACTION_VIEW` + URL handling which is deferred to keep Plan 3 contained.
- **GitHub API signature verification** — single-user opt-in path, no auth, trust GitHub HTTPS.
- **Removing debug-seed mechanism** — kept as dev-only fallback. Already gated on `!isConfigured`; harmless once onboarding completes.

## Top-level decisions

| Decision | Value | Rationale |
|---|---|---|
| SettingsScreen form factor | Full-screen Compose Navigation route | Settings is conventionally a top-level destination in Android. Matches original design spec. Avoids "settings inside a transient sheet" UX awkwardness. |
| Update polling architecture | Conditional on install source + 24h-throttled foreground check | Play Store handles updates for Play users — GitHub polling is redundant and confusing in that case. Sideload users need the feature. 24h throttle prevents GitHub rate-limit issues; no WorkManager needed for Plan 3. |
| Location permission timing | Just-in-time on `USE MY LOCATION` button tap | Higher grant rate. Standard Android best practice. Less verbose onboarding. |
| First-run flow | Straight to Settings, no welcome screen | Minimum friction. Settings labels are self-explanatory. User installed the app knowing what it is. |
| GPS provider | Platform `LocationManager` (NETWORK then GPS fallback) | No Play Services dependency. Sufficient for "set home location once" — we don't need continuous tracking. |
| ViewModel split | Separate `SettingsViewModel` from `DashboardViewModel` | Settings has its own state machine (form input, GPS state, save in-flight). Mixing into `DashboardViewModel` would inflate that VM's surface area. |
| Update alert injection point | In `WeatherNormalizer`, prepended to `alerts` list | Reuses existing AlertBanner + AlertDetailSheet pipeline. No new UI code for the synthetic alert. Matches web behavior. |
| Tag | `v0.3.0` | Three new user-visible capabilities (Settings, onboarding, update alerts) — minor bump. |

## Architecture

### Compose Navigation

`MainActivity` switches from rendering `DashboardScaffold` directly to hosting a `NavHost`:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var nwsClient: NwsClient
    @Inject lateinit var updateCheckRepository: UpdateCheckRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeDebugSeed()
        setContent {
            val startDestination = remember {
                if (runBlocking { settingsRepository.snapshot().isConfigured }) "dashboard"
                else "settings"
            }
            SkyFrameNavHost(
                startDestination = startDestination,
                dashboardViewModel = hiltViewModel(),
                settingsViewModel = hiltViewModel(),
                nwsClient = nwsClient,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { updateCheckRepository.maybeCheck() }
    }
}
```

`SkyFrameNavHost` composes a `NavController` and dispatches to either `DashboardScaffold` or `SettingsScreen`. System back from settings goes to dashboard. From the force-completion variant (first-run), system back is swallowed via `BackHandler { /* no-op */ }` until save completes.

### SettingsViewModel state machine

```kotlin
data class SettingsUiState(
    val locationInput: String = "",
    val emailInput: String = "",
    val updateCheckEnabled: Boolean = false,
    val showUpdateCheckCheckbox: Boolean = false,    // gated on install source
    val gpsState: GpsState = GpsState.Idle,
    val saveState: SaveState = SaveState.Idle,
    val isConfigured: Boolean = false,                // controls force-completion mode
)

sealed class GpsState {
    data object Idle : GpsState()
    data object Requesting : GpsState()
    data object Available : GpsState()                // permission granted, last-known available
    data object NoLastKnown : GpsState()              // permission granted but no recent fix
    data object PermissionDenied : GpsState()
    data object PermissionDeniedPermanent : GpsState()
}

sealed class SaveState {
    data object Idle : SaveState()
    data object Resolving : SaveState()
    data object Saved : SaveState()
    data class Error(val message: String) : SaveState()
}
```

### Files added/modified

```
app/src/main/kotlin/com/skyframe/
  MainActivity.kt                          MODIFIED — host NavHost, decide start destination,
                                           call updateCheckRepository.maybeCheck() on resume
  ui/
    screens/
      SettingsScreen.kt                    NEW — form + save flow + force-completion mode
    nav/
      SkyFrameNavHost.kt                   NEW — NavHost composition
      NavRoutes.kt                         NEW — "dashboard" / "settings" string constants
  viewmodel/
    SettingsViewModel.kt                   NEW
  data/
    gps/
      GpsAutodetect.kt                     NEW — wraps LocationManager + permission flow
    install/
      InstallSource.kt                     NEW — isFromPlayStore() helper
    updates/
      GithubReleaseClient.kt               NEW — Ktor wrapper for GitHub /releases/latest
      GithubReleaseDto.kt                  NEW — release JSON shape
      UpdateCheckRepository.kt             NEW — 24h-throttled polling + cached state
      UpdateAvailable.kt                   NEW — typed cached-state model
      VersionCompare.kt                    NEW — pure isNewer(latest, current) helper
    nws/
      WeatherNormalizer.kt                 MODIFIED — inject synthetic update alert
    alerts/
      AlertDescriptionFormat.kt            MODIFIED — add isUpdateAlert() helper + skip
                                           EXPIRES segment in formatAlertMeta for updates
  di/
    UpdateCheckModule.kt                   NEW — Hilt provides GithubReleaseClient +
                                           UpdateCheckRepository
  ui/shell/
    DashboardScaffold.kt                   MODIFIED — TopBar location/hamburger taps now
                                           call onNavigateToSettings (was Toast stub)

app/src/test/kotlin/com/skyframe/
  data/
    install/
      InstallSourceTest.kt                 NEW — install-source detection logic
    updates/
      UpdateCheckRepositoryTest.kt         NEW — 24h throttle, cached-state lifecycle
      GithubReleaseClientTest.kt           NEW — URL + DTO parsing
      VersionCompareTest.kt                NEW — semver-like comparison
    nws/
      WeatherNormalizerTest.kt             MODIFIED — +2 tests for synthetic update alert
    alerts/
      AlertDescriptionFormatTest.kt        MODIFIED — +1 test for isUpdateAlert + meta variant
  viewmodel/
    SettingsViewModelTest.kt               NEW

docs/
  PROJECT_STATUS.md                        MODIFIED — Plan 3 implemented features
  ROADMAP.md                               MODIFIED — flip Plan 3 to ✅ Shipped
  SMOKE_TEST.md                            MODIFIED — Settings + onboarding + GPS flows
CHANGELOG.md                               MODIFIED — v0.3.0 release notes
README.md                                  MODIFIED — flip Plan 3 row to ✅
```

## Component details

### SettingsScreen body

```
┌──────────────────────────────────────────────────────┐
│ TERMINAL // SETTINGS                ← back  (or [x]) │  ← title bar
├──────────────────────────────────────────────────────┤
│                                                      │
│  LOCATION                                            │
│  [ 53154 or 42.8744, -87.8633          ]            │  ← TextField
│  [ ⌖ USE MY LOCATION ]                              │  ← GPS button (state-driven label)
│                                                      │
│  EMAIL                                               │
│  [ you@example.com                      ]            │  ← TextField
│  Used for NWS User-Agent header. Not transmitted    │  ← helper text
│  to any third party.                                 │
│                                                      │
│  ─────────────────────────────────────────           │
│                                                      │
│  [x] Check GitHub for SkyFrame updates              │  ← Checkbox (conditional)
│  Polls once per day. Off by default. (Sideload-only) │
│                                                      │
│  COSMETIC SKIN  [ Default (HUD cyan) ▾ ]            │  ← disabled select (placeholder)
│                                                      │
│  ─────────────────────────────────────────           │
│                                                      │
│         [ CANCEL ]    [ SAVE ]                       │  ← CANCEL hidden in force-completion
│                                                      │
└──────────────────────────────────────────────────────┘
```

**Behavior:**
- On entry: hydrate form from `SettingsRepository.snapshot()`. First-run users see empty fields.
- LOCATION accepts `53154` (5-digit ZIP) or `42.8744, -87.8633` (lat,lon comma-separated). Validation deferred to SAVE — same `zipRegex` / `latLonRegex` rules as `SetupResolver`.
- `USE MY LOCATION` button label by state: `⌖ USE MY LOCATION` (idle/available) → `⌖ REQUESTING…` (Requesting) → `⌖ GPS PENDING — try moving outside` (NoLastKnown, informational only — button stays tappable to retry) → `⌖ GPS UNAVAILABLE — open system settings` (PermissionDeniedPermanent, tap opens app settings).
- EMAIL has no validation beyond non-blank — NWS just needs *some* identifying contact.
- Checkbox visibility gated on `InstallSource.isFromPlayStore(context) == false`. When Play-installed, checkbox + helper text are entirely omitted from the layout — no ghosted/disabled UI.
- SAVE button:
  - Validates LOCATION not blank, EMAIL not blank → otherwise inline error
  - Sets `saveState = Resolving` → calls `setupResolver.resolve(locationInput)` (suspend) → on success, `settingsRepository.update { ... }` with the resolved grid + email + checkbox → on success, `saveState = Saved` → `onSaved()` callback navigates back to dashboard + triggers `weatherRepository.refresh()`
  - On `SetupException` (bad ZIP, NWS lookup failed, etc.): `saveState = Error(message)`, leave form populated, no nav. Error renders inline above SAVE: `! Couldn't resolve location: <NWS error message>`
- CANCEL button hidden when `!isConfigured` (force-completion). When visible: discards form changes, `popBackStack` to dashboard.

**Force-completion mode** (first-run): `BackHandler(enabled = !isConfigured) { /* swallow */ }`. User can't escape Settings without successfully saving — same hard gate as the web's first-run modal.

### UpdateCheckRepository internals

```kotlin
@Singleton
class UpdateCheckRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val settings: SettingsRepository,
    private val releaseClient: GithubReleaseClient,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val lastCheckedKey = longPreferencesKey("update_check_last_at")
    private val cachedVersionKey = stringPreferencesKey("update_check_version")
    private val cachedUrlKey = stringPreferencesKey("update_check_url")
    private val cachedBodyKey = stringPreferencesKey("update_check_body")

    val available: Flow<UpdateAvailable?> = dataStore.data.map { prefs ->
        val v = prefs[cachedVersionKey]; val u = prefs[cachedUrlKey]; val b = prefs[cachedBodyKey]
        if (v != null && u != null && b != null) UpdateAvailable(v, u, b) else null
    }

    suspend fun currentAvailable(): UpdateAvailable? = available.first()

    /**
     * Fire-and-forget update check. Runs only when ALL preconditions met:
     *  - Install source is NOT Play Store
     *  - User's checkbox is enabled
     *  - Last check was >24h ago
     * Failures (no network, GitHub down, parse error) are swallowed.
     */
    suspend fun maybeCheck() {
        if (InstallSource.isFromPlayStore(context)) return
        if (!settings.snapshot().updateCheckEnabled) return

        val lastCheckedMs = dataStore.data.first()[lastCheckedKey] ?: 0L
        val nowMs = now().toEpochMilliseconds()
        if (nowMs - lastCheckedMs < 24 * 60 * 60 * 1000L) return

        runCatching {
            val release = releaseClient.latestRelease()
            val current = BuildConfig.VERSION_NAME
            val latest = release.tag_name.removePrefix("v")
            dataStore.edit { prefs ->
                prefs[lastCheckedKey] = nowMs
                if (VersionCompare.isNewer(latest, current)) {
                    prefs[cachedVersionKey] = latest
                    prefs[cachedUrlKey] = release.html_url
                    prefs[cachedBodyKey] = release.body.orEmpty()
                } else {
                    prefs.remove(cachedVersionKey)
                    prefs.remove(cachedUrlKey)
                    prefs.remove(cachedBodyKey)
                }
            }
        }
    }

    suspend fun clearCachedUpdate() {
        dataStore.edit {
            it.remove(cachedVersionKey); it.remove(cachedUrlKey); it.remove(cachedBodyKey)
        }
    }
}

data class UpdateAvailable(
    val version: String,
    val htmlUrl: String,
    val body: String,
)
```

**Where `maybeCheck()` is called:**
- `MainActivity.onResume` — every foreground transition (24h throttle makes it safe)
- `SettingsViewModel` when the checkbox flips from off→on — immediate check rather than waiting for next launch

`VersionCompare.isNewer` is a pure helper — semver-like, port of the web's `compareVersions`. Splits by `.`, compares each segment numerically, longer version wins ties (`0.3.0` > `0.3` > `0.2.99`). Handles `v` prefix stripping.

### WeatherNormalizer synthetic alert injection

In `WeatherNormalizer.load()` after the WeatherResponse is assembled:

```kotlin
val updateAvailable = updateCheckRepository.currentAvailable()
val syntheticAlerts = if (updateAvailable != null) {
    listOf(buildUpdateAlert(updateAvailable))
} else {
    emptyList()
}
// ...
alerts = syntheticAlerts + (alertsDto?.let { AlertNormalizer.normalize(it) } ?: emptyList()),
```

`buildUpdateAlert` produces:

```kotlin
Alert(
    id = "update-${available.version}",
    event = "Update Available",
    tier = AlertTier.ADVISORY,
    severity = AlertSeverity.MINOR,
    headline = "SkyFrame ${available.version} available",
    description = available.body,
    issuedAt = Clock.System.now(),
    effective = Clock.System.now(),
    expires = Clock.System.now().plus(365.days),  // far future; AlertDetailSheet
                                                   // suppresses EXPIRES for update alerts
    areaDesc = "",
)
```

### `isUpdateAlert` helper

In `AlertDescriptionFormat`:

```kotlin
fun isUpdateAlert(alert: Alert): Boolean = alert.id.startsWith("update-")
```

`formatAlertMeta` becomes:

```kotlin
fun formatAlertMeta(alert: Alert, tz: TimeZone): String {
    val issued = formatTime(alert.issuedAt, tz)
    return if (isUpdateAlert(alert)) {
        "ISSUED $issued"  // skip EXPIRES + AREA for synthetic update alerts
    } else {
        val expires = formatTime(alert.expires, tz)
        "ISSUED $issued · EXPIRES $expires · ${alert.areaDesc.uppercase()}"
    }
}
```

### Install-source detection

```kotlin
object InstallSource {
    private const val PLAY_PACKAGE = "com.android.vending"

    fun isFromPlayStore(context: Context): Boolean {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { pm.getInstallSourceInfo(context.packageName).installingPackageName }
                .getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching { pm.getInstallerPackageName(context.packageName) }.getOrNull()
        }
        return installer == PLAY_PACKAGE
    }
}
```

API 30+ uses the modern `getInstallSourceInfo`. API 26–29 falls back to deprecated `getInstallerPackageName` (still works). `runCatching` wraps both because some manufacturers throw `IllegalArgumentException` for odd package name resolutions.

### GPS autodetect helper

```kotlin
class GpsAutodetect @Inject constructor(@ApplicationContext private val context: Context) {
    sealed class Result {
        data class Coordinates(val lat: Double, val lon: Double) : Result()
        data object PermissionDenied : Result()
        data object NoLastKnownLocation : Result()
    }

    fun hasFineLocationPermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Returns the most recent fix from NETWORK provider (typically faster, less
     * battery), falling back to GPS provider. Caller MUST have already obtained
     * permission — this method does not request it.
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Result {
        if (!hasFineLocationPermission()) return Result.PermissionDenied
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fix = runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            ?: runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        return fix?.let { Result.Coordinates(it.latitude, it.longitude) } ?: Result.NoLastKnownLocation
    }
}
```

Not using `FusedLocationProviderClient` — that's Play Services dep, which we're avoiding. Platform `LocationManager` is sufficient for "set my home location once."

### GithubReleaseClient

```kotlin
class GithubReleaseClient @Inject constructor(private val http: HttpClient) {
    private val base = "https://api.github.com/repos/OniNoKen4192/SkyFrameAndroid/releases/latest"
    suspend fun latestRelease(): GithubReleaseDto = http.get(base).body()
}

@Serializable
data class GithubReleaseDto(
    val tag_name: String,
    val html_url: String,
    val body: String? = null,
)
```

Reuses the existing Hilt-provided `HttpClient` (which has the SkyFrame User-Agent header — GitHub doesn't require it but it's good citizenship). No auth header — unauthenticated GitHub API gives 60 requests/hour which far exceeds the once-per-24h throttle.

## Test strategy

Target: **~135 tests after Plan 3** (up from 119, +16 new tests).

| Suite | New tests | Coverage |
|---|---|---|
| `InstallSourceTest` | ~3 | Returns true for `"com.android.vending"`; false for null/other; doesn't crash when `PackageManager` throws |
| `GithubReleaseClientTest` | ~3 | URL is `/repos/{owner}/{repo}/releases/latest`; parses real release JSON sample; handles 403 (rate-limit) gracefully via `runCatching` upstream |
| `VersionCompareTest` | ~5 | Equal versions; single segment newer; segment-count differences (`0.3.0` > `0.3`); `v`-prefix stripping; non-numeric segment handling |
| `UpdateCheckRepositoryTest` | ~8 | First check ever (no `lastCheckedAt`) → runs; second check <24h → throttled; >24h → runs; Play Store install → no-op; checkbox off → no-op; newer version → cache populated; same/older version → cache cleared; network failure → swallowed, cache unchanged |
| `WeatherNormalizerTest` | +2 | Synthetic update alert prepended when `UpdateCheckRepository` has cached update; no synthetic alert when null |
| `AlertDescriptionFormatTest` | +2 | `isUpdateAlert("update-0.3.0") == true`; `formatAlertMeta` for update alert produces only `ISSUED <time>` |
| `SettingsViewModelTest` | ~4 | Initial state from `SettingsRepository.snapshot()`; SAVE success → triggers `onSaved`; SAVE failure → `SaveState.Error` populated; checkbox toggle off→on triggers `maybeCheck` |

**No tests for:** SettingsScreen Composable (project convention — no Compose UI test infra yet), GpsAutodetect (LocationManager interactions are platform-mocked and tedious; manual smoke-test verifies), Compose Navigation (NavHost behavior is library-tested).

## Documentation updates (in same commits per CLAUDE.md rule)

Per the [documentation-is-lifeblood feedback memory](file:///C:/Users/kencu/.claude/projects/e--SkyFrame---Android/memory/feedback_documentation_is_lifeblood.md):

- `docs/PROJECT_STATUS.md` — Plan 3 features as each ships
- `docs/ROADMAP.md` — flip Plan 3 from `Not started` → `✅ Shipped at v0.3.0`
- `CHANGELOG.md` — v0.3.0 release notes
- `docs/SMOKE_TEST.md` — extend with onboarding flow, Settings save round-trip, GPS button states across all 4 states, update alert appearance after a mock newer-version release
- `README.md` — flip Plan 3 row to ✅, update current-tag badge, update test count

## Release

End-state: **v0.3.0** tag. Minor bump because three new user-visible capabilities (real Settings, first-run onboarding, update alerts via the AlertBanner pipeline).

## Open questions for the implementation plan

None known. The design is concrete; all decisions resolved during brainstorming.

---

**Approved by user on 2026-05-19. Next step: write the Plan 3 implementation plan via the writing-plans skill.**
