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
