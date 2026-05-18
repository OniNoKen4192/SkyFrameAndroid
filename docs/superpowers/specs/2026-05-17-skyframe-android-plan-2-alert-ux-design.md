# SkyFrame Android — Plan 2: Full Alert UX + Trends Design Spec

**Date:** 2026-05-17
**Builds on:** [v0.1.1-mvp](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.1.1-mvp) (Plan 1 complete)
**Target tag:** `v0.2.0`
**Status:** Approved (brainstorming phase complete); awaiting implementation plan

## Goal

Add the three overlay sheets that complete the alert / forecast / station UX (AlertDetailSheet, ForecastNarrativeSheet, StationOverrideSheet), plus revive the trend arrows on NowScreen by fetching observation history. These were intentionally deferred from Plan 1; Plan 2 lands them on top of the solid v0.1.1 data layer.

## What ships

1. **AlertDetailSheet** — tap any alert event name in `AlertBanner` opens a ModalBottomSheet showing the NWS `description` parsed into HAZARD/SOURCE/IMPACT-tagged paragraphs (prefixes tier-colored), plus a meta line (`ISSUED 14:23 CDT · EXPIRES 14:53 CDT · MILWAUKEE COUNTY`).
2. **ForecastNarrativeSheet** — opens from three triggers (▶ next to NowScreen TEMP/FEEL, ▶ next to HourlyScreen NEXT 12H header, tap any day-row date label in OutlookScreen) and shows that day's `dayDetailedForecast` + `nightDetailedForecast` stacked with NWS-preserved period names (`TODAY` / `TONIGHT` / `FRIDAY` / `FRIDAY NIGHT`).
3. **StationOverrideSheet** — tap Footer's `LINK.<station>` text opens a smaller sheet with AUTO / FORCE_SECONDARY radio buttons + a live side-by-side preview of both stations (ID, observed time, temp, freshness status). APPLY button writes the change to settings and triggers an immediate refresh.
4. **Observation history fetch** — new `NwsClient.recentObservations(stationId, limit=6)` + DTO + wiring through `WeatherNormalizer` → `ConditionTrends` populated with real data → `HudMetricBar` trend arrows reappear silently (they currently hide because confidence is always MISSING).
5. **Minor cleanup** in files Plan 2 already touches (4 reviewer-flagged items, ~15 lines total).

## Non-goals (out of Plan 2 scope)

- Settings screen — the StationOverride sheet is a focused single-toggle popover, not a settings replacement. Full Settings is Plan 3.
- `isUpdateAlert` special-case in `AlertDescriptionFormat` — the synthetic update alerts don't exist until Plan 3 ships GitHub release polling.
- Sounds, mute glyph, Web Audio (Plan 4).
- Tap-handlers for notifications (no notifications until Plan 4).
- Station-preview cross-open caching — refetch every time the sheet opens (acceptable for an infrequent action; needless complexity otherwise).
- Custom sheet animations beyond Material defaults.

## Top-level decisions

| Decision | Value | Rationale |
|---|---|---|
| Sheet implementation | Material3 `ModalBottomSheet` with HUD restyling | Free swipe-to-dismiss, scrim, accessibility, system back — restyle container + hide drag handle for HUD aesthetic. Several days of behavior work vs ~1 day of theming. |
| Sheet state | Hoisted to `DashboardScaffold` as a sealed class | Mutual exclusion by construction; one source of truth; no risk of two sheets opening simultaneously. |
| `parseDescription` | Direct port of `_reference/client/alert-detail-format.ts` | 30-line paragraph splitter + HAZARD/SOURCE/IMPACT prefix detection. No design freedom needed. |
| Station preview source | Parallel `nws.latestObservation()` calls on sheet open | Simple, matches web's `/api/stations/preview` semantics. No caching across opens. |
| AlertDetail prefix color | The alert's own `tier` color, NOT the dashboard's active accent | So an IMPACT label of a tornado-warning is RED even if the dashboard already shifted to red — also so that nested-in-other-tier alerts visually distinguish. |
| StationOverride apply | Explicit `[APPLY]` button, not auto-on-radio-click | Matches user expectation of a confirmation step; lets the preview load before committing. |
| History fetch placement | After fallback resolution (6th sequential fetch), wrapped in `runCatching` | Active station ID is unknown until fallback decides — parallel fetch could hit the wrong station. Failure degrades gracefully to MISSING confidence. |
| Tag | `v0.2.0` (minor bump) | Three new user-visible screens qualifies as a feature release, not a patch. |

## Architecture

### Sheet state hoisting

```kotlin
// app/src/main/kotlin/com/skyframe/ui/sheets/SheetState.kt
sealed class SheetState {
    data object None : SheetState()
    data class AlertDetail(val alert: Alert) : SheetState()
    data class Forecast(val day: DailyPeriod) : SheetState()
    data object StationOverride : SheetState()
}
```

In `DashboardScaffold`:

```kotlin
var sheetState by remember { mutableStateOf<SheetState>(SheetState.None) }

// Render exactly one sheet when state != None:
when (val s = sheetState) {
    SheetState.None -> Unit
    is SheetState.AlertDetail -> AlertDetailSheet(s.alert, ui.timezone) { sheetState = SheetState.None }
    is SheetState.Forecast -> ForecastNarrativeSheet(s.day) { sheetState = SheetState.None }
    SheetState.StationOverride -> StationOverrideSheet(
        currentMode = (ui.weather as? WeatherState.Success)?.response?.meta?.stationOverride ?: StationOverride.AUTO,
        primaryStationId = ...,
        secondaryStationId = ...,
        onApply = { mode -> viewModel.applyStationOverride(mode); sheetState = SheetState.None },
        onDismiss = { sheetState = SheetState.None },
    )
}
```

Triggers set `sheetState` from various places:
- `AlertBanner` — event-name `Modifier.clickable` → `onAlertClick(alert)` → `sheetState = SheetState.AlertDetail(alert)`
- `NowScreen` / `HourlyScreen` / `OutlookScreen` — ▶ click or day-label click → `onOpenForecast(day)` → `sheetState = SheetState.Forecast(day)`
- `Footer` — LINK click → `onStationClick()` → `sheetState = SheetState.StationOverride`

### Shared sheet chrome

```kotlin
// app/src/main/kotlin/com/skyframe/ui/sheets/HudBottomSheet.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = HudColors.BackgroundPanel,
        dragHandle = null,  // we provide a custom title bar instead
        shape = RectangleShape,  // no rounded corners — HUD is angular
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Accent-colored top border for HUD chrome
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
```

Title bar renders `TERMINAL // ${TITLE}` in the active dashboard accent + a `[x]` close glyph. All three sheets get the same chrome; bodies differ.

### Files added/modified

```
app/src/main/kotlin/com/skyframe/
  ui/
    sheets/                                  NEW package
      HudBottomSheet.kt                      Shared chrome primitive + HudSheetTitleBar
      SheetState.kt                          Sealed class
      AlertDetailSheet.kt                    Body for SheetState.AlertDetail
      ForecastNarrativeSheet.kt              Body for SheetState.Forecast
      StationOverrideSheet.kt                Body for SheetState.StationOverride
      StationPreview.kt                      Helper: parallel fetch of both stations
    widgets/
      ForecastButton.kt                      NEW — small ▶ trigger glyph
      HudHero.kt                             MODIFIED — add onOpenForecast callback
    screens/
      NowScreen.kt                           MODIFIED — wire ▶ next to TEMP/FEEL,
                                             accept onOpenForecast(day) callback
      HourlyScreen.kt                        MODIFIED — wire ▶ next to NEXT 12H header,
                                             fix precip-bar integer-arithmetic zero-height bug
      OutlookScreen.kt                       MODIFIED — make day labels clickable
    shell/
      DashboardScaffold.kt                   MODIFIED — hoist sheetState, render sheets,
                                             fix frozen T-Xs countdown with 1Hz ticker
      AlertBanner.kt                         MODIFIED — event-name click opens AlertDetail
      Footer.kt                              MODIFIED — LINK click opens StationOverride
  data/
    nws/
      NwsClient.kt                           MODIFIED — add recentObservations()
      NwsDtos.kt                             MODIFIED — add ObservationsListDto +
                                             ObservationFeatureDto
      WeatherNormalizer.kt                   MODIFIED — fetch history after fallback
                                             resolution, pass to ObservationNormalizer
      IconMapper.kt                          MODIFIED — add missing NWS codes
                                             (scttsra, hi_shwrs, fzra_sct, ra_fzra,
                                             ra_sn, sn, blizzard, cold)
    alerts/
      AlertDescriptionFormat.kt              NEW — parseDescription + formatAlertMeta +
                                             formatTime (port of web alert-detail-format.ts)
  viewmodel/
    DashboardViewModel.kt                    MODIFIED — add applyStationOverride(mode)
                                             which updates settings + refreshes
```

## Per-sheet detailed designs

### AlertDetailSheet

**Trigger:** tap event name text in `AlertBanner` (single-alert headline + each row in the expanded multi-alert list).

**Body structure (top → bottom):**

```
TORNADO WARNING                         ← tier-color, HudType.titleBar
ISSUED 14:23 CDT · EXPIRES 14:53 CDT · MILWAUKEE COUNTY
                                         ← HudColors.ForegroundDim, HudType.metaLabel
─────────────────────────────────────   ← divider in alert's tier color at 0.3 alpha
[HAZARD]  Tornado.                       ← prefix in tier color, body in Foreground
[SOURCE]  National Weather Service forecast office in Sullivan, WI...
[IMPACT]  Flying debris will be dangerous to those caught without...
[plain paragraph if no prefix]           ← rendered in Foreground without tag
```

- Each `HAZARD/SOURCE/IMPACT` prefix renders as a small uppercase label (`HudType.metaLabel`) in the alert's tier color (`Color(alert.tier.baseColor)`), NOT `LocalHudAccent.current`.
- Body text in `HudColors.Foreground` with `HudType.bodyMono`.
- Unlabeled paragraphs render plain in body color, no tag.
- Scrollable via `verticalScroll(rememberScrollState())` — NWS descriptions can be long (multiple paragraphs).
- Title bar: `TERMINAL // ALERT DETAIL`.

**Meta line formatting** matches web `formatAlertMeta` exactly:
- `ISSUED <time> CDT · EXPIRES <time> CDT · <AREA>` — both times formatted via `formatTime(instant, ui.timezone)` in the configured location TZ.
- `<AREA>` is `alert.areaDesc.uppercase()`.
- Plan 2 ships *without* `isUpdateAlert` special-case (no synthetic update alerts exist until Plan 3).

### ForecastNarrativeSheet

**Triggers:**
- `NowScreen` — ▶ glyph next to "TEMP / FEEL" label → opens `weather.daily.firstOrNull()`
- `HourlyScreen` — ▶ glyph next to "NEXT 12H" section header → also `weather.daily.firstOrNull()`
- `OutlookScreen` — tap any day-row's day-of-week label → opens that specific `DailyPeriod` from `weather.daily[index]`

`ForecastButton.kt` is the shared ▶ trigger composable:

```kotlin
@Composable
fun ForecastButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = "▶",
        color = LocalHudAccent.current.accent,
        style = HudType.metricLabel,
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}
```

**Body structure:**

```
┌ TODAY ┐                                ← dayPeriodName uppercased,
                                            tier-frame in accent color
Sunny, with a high near 75. Northwest wind 5 to 10 mph.

┌ TONIGHT ┐                              ← nightPeriodName uppercased
Mostly clear, with a low around 55. Northwest wind around 5 mph.
```

- Section headers use the NWS-preserved `dayPeriodName` / `nightPeriodName` (`"This Afternoon"`, `"Tonight"`, `"Friday"`, `"Friday Night"` — uppercased) in `HudType.sectionHeader` with the active accent color. The `┌ XYZ ┐` bracket-frame styling: render the header text with leading `┌ ` and trailing ` ┐` literal characters.
- Section body in `HudColors.Foreground` with `HudType.bodyMono`, 12dp top padding.
- Orphan days (day-only at window end, night-only at window start) render only the populated half — no placeholder for the missing side.
- Vertically scrollable.
- Title bar: `TERMINAL // FORECAST`.

### StationOverrideSheet

**Trigger:** tap Footer's `LINK.<station>` text.

**Body structure:**

```
◉ AUTO                                    ← selected radio (filled accent ring)
  Primary station with automatic
  fallback to secondary when stale

○ FORCE SECONDARY                         ← unselected radio (outline only)
  Always use the secondary station

─────────────── PREVIEW ───────────────  ← HudType.metricLabel section divider

PRIMARY     KMKE
            Observed 14:23 · 72°
            ● LIVE                        ← cyan dot, fresh

SECONDARY   KRAC
            Observed 14:18 · 71°
            ● LIVE

          [ APPLY ]                       ← disabled when mode == currentMode
```

- Two custom HUD-styled radio buttons (NOT Material `RadioButton` — small accent-ringed circle with filled inner when selected). Click changes local sheet state, not committed until APPLY.
- "─── PREVIEW ───" divider in `HudType.metricLabel`.
- Per-station row: ID + observed time (formatted via `formatTime` in location TZ) + temp + status indicator:
  - `● LIVE` cyan (`HudColors.DefaultAccent`): fresh (<90 min old) AND non-null temp
  - `● STALE` amber (`Color(0xFFFFAA22)`): >90 min OR null temp
  - `● ERROR` red (`Color(0xFFFF4444)`): fetch failed
- Both stations fetched in parallel on sheet open via `StationPreview.fetch(client, primaryId, fallbackId)` → returns `Pair<Result<StationSnapshot>, Result<StationSnapshot>>`. While in flight, both rows show `Fetching…` in dim text.
- `[APPLY]` button: disabled (dimmed, no border) when sheet's selected radio matches the current `cfg.stationOverride`; enabled (accent border + accent text) when it differs.
- On APPLY click: call `viewModel.applyStationOverride(mode)` which updates settings + triggers refresh + closes sheet.
- Title bar: `TERMINAL // STATION OVERRIDE`.

## Data layer additions

### `recentObservations` endpoint

```kotlin
// NwsClient.kt — append:
suspend fun recentObservations(stationId: String, limit: Int = 6): ObservationsListDto =
    http.get("$base/stations/$stationId/observations?limit=$limit").body()

// NwsDtos.kt — append:
@Serializable
data class ObservationsListDto(val features: List<ObservationFeatureDto>)

@Serializable
data class ObservationFeatureDto(val properties: ObservationProperties)
// ObservationProperties already exists from Plan 1.
```

### WeatherNormalizer wiring

```kotlin
// After fetchObservationWithFallback returns (observation, activeStationId, fellBack):
val history: List<ObservationDto> = runCatching {
    nws.recentObservations(activeStationId).features
        .map { ObservationDto(properties = it.properties) }
}.getOrDefault(emptyList())

// Pass to ObservationNormalizer.normalize:
ObservationNormalizer.normalize(
    latest = observation,
    recentObservations = history,   // ← was emptyList() in Plan 1
    ...
)
```

History fetch wrapped in `runCatching` for partial-failure semantics (matches alerts pattern). On failure, trends just go MISSING confidence (which `HudMetricBar` already hides — courtesy of v0.1.1 fix #10). Trend arrows reappear silently when history succeeds.

**Why not fetch history in parallel with the initial 5-fetch block?** Active station ID isn't known until *after* the primary-vs-fallback decision. Fetching for the wrong station is wasted work. So history is a 6th sequential fetch. Adds ~200-400ms to cold loads; cache hits skip it entirely.

### StationPreview helper

```kotlin
// app/src/main/kotlin/com/skyframe/ui/sheets/StationPreview.kt
data class StationSnapshot(
    val stationId: String,
    val observedAt: Instant?,
    val tempF: Double?,
    val isStale: Boolean,  // >90min OR null temp
)

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
    private suspend fun fetchOne(client: NwsClient, id: String, now: Instant): StationSnapshot { ... }
}
```

### AlertDescriptionFormat (port of web helper)

```kotlin
// app/src/main/kotlin/com/skyframe/data/alerts/AlertDescriptionFormat.kt
sealed class AlertDescriptionParagraph {
    abstract val text: String
    data class Tagged(val prefix: Prefix, override val text: String) : AlertDescriptionParagraph()
    data class Plain(override val text: String) : AlertDescriptionParagraph()

    enum class Prefix { HAZARD, SOURCE, IMPACT }
}

object AlertDescriptionFormat {
    private val PREFIX_RE = Regex("""^(HAZARD|SOURCE|IMPACT)\.\.\.\s*""")

    fun parseDescription(raw: String): List<AlertDescriptionParagraph> {
        if (raw.isEmpty()) return emptyList()
        return raw.replace("\r\n", "\n")
            .split(Regex("""\n{2,}"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { chunk ->
                PREFIX_RE.find(chunk)?.let {
                    val prefix = AlertDescriptionParagraph.Prefix.valueOf(it.groupValues[1])
                    AlertDescriptionParagraph.Tagged(prefix, chunk.substring(it.range.last + 1))
                } ?: AlertDescriptionParagraph.Plain(chunk)
            }
    }

    fun formatTime(instant: Instant, tz: TimeZone): String { /* "2:23 PM CDT" */ }

    fun formatAlertMeta(alert: Alert, tz: TimeZone): String =
        "ISSUED ${formatTime(alert.issuedAt, tz)} · " +
        "EXPIRES ${formatTime(alert.expires, tz)} · " +
        alert.areaDesc.uppercase()
}
```

## Minor reviewer-cleanup items (folded into Plan 2)

| File | Fix |
|---|---|
| `DashboardScaffold.kt` | Replace static `formatRefreshLabel` with a 1Hz `LaunchedEffect` ticker so `T-Xs` actually decrements |
| `HourlyScreen.kt` | Fix `(40 * p.precipProbPct / 100).dp` zero-height bug for low probabilities — use `Modifier.fillMaxHeight(p.precipProbPct / 100f)` instead |
| `NowScreen.kt` | Remove `/ 1.0` no-op in pressure `fillFraction` |
| `IconMapper.kt` | Add missing NWS codes from web reference: `scttsra`, `hi_shwrs`, `fzra_sct`, `ra_fzra`, `ra_sn`, `sn`, `blizzard`, `cold` |

Total: ~15 lines of code change across 4 files. Adds 3 tests to `IconMapperTest` covering the new codes.

## Test strategy

Pure-logic tests per project convention. Target: **~115 tests after Plan 2** (up from 96).

| Suite | New tests | Coverage |
|---|---|---|
| `AlertDescriptionFormatTest` | ~8 | Paragraph splitting on `\n{2,}`, prefix detection for HAZARD/SOURCE/IMPACT, no-prefix paragraphs, empty input, formatTime with TZ, formatAlertMeta full-string format |
| `NwsClientTest` | 1 | `recentObservations(stationId, limit)` URL includes `?limit=6` |
| `WeatherNormalizerTest` | ~6 | First orchestrator tests: happy-path parallel fetch, primary-fresh, primary-stale-fallback-used, force-secondary, alerts-fail-PARTIAL, history-fail-trends-MISSING |
| `StationPreviewTest` | ~3 | Both succeed, primary fails secondary succeeds, both fail |
| `IconMapperTest` | ~3 | New codes (`scttsra`, `blizzard`, `cold`) map correctly |

`WeatherNormalizerTest` specifically closes the biggest gap the v0.1.0 final reviewer flagged — the orchestrator was 100% untested in Plan 1.

## Documentation updates (in same commits per CLAUDE.md rule)

Per the [documentation-is-lifeblood feedback memory](file:///C:/Users/kencu/.claude/projects/e--SkyFrame---Android/memory/feedback_documentation_is_lifeblood.md):

- `docs/PROJECT_STATUS.md` — add Plan 2 features as each ships (don't batch)
- `docs/ROADMAP.md` — flip Plan 2 from `Not started` → `✅ Shipped at v0.2.0` when the tag goes out
- `CHANGELOG.md` — backfill release notes for v0.2.0
- `docs/SMOKE_TEST.md` — extend the manual checklist to cover sheet open/dismiss flows, AlertDetail multi-paragraph rendering, StationOverride APPLY round-trip

## Release

End-state: **v0.2.0** tag. Minor bump (not v0.1.2) because three new user-visible screens is a feature release.

Push to `main`, push the tag, update PROJECT_STATUS + ROADMAP + CHANGELOG + SMOKE_TEST in the same commits.

## Open questions for the implementation plan

None known. The design is concrete; everything that was undecided got resolved during the brainstorming phase.

---

**Approved by user on 2026-05-17. Next step: write the Plan 2 implementation plan via the writing-plans skill.**
