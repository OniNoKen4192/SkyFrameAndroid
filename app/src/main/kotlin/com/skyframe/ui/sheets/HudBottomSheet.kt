package com.skyframe.ui.sheets

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
import com.skyframe.theme.hudDashedBorder

/**
 * Shared bottom-sheet chrome for all three Plan 2 sheets. Wraps Material3's
 * ModalBottomSheet (which gives us swipe-to-dismiss, scrim, system back,
 * accessibility focus management) while overriding the visual chrome:
 *  - container color: HudColors.BackgroundBase (same as the dashboard body so
 *    the sheet reads as the same HUD surface, not a different color — the scrim
 *    behind it provides the modal "lift")
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
        containerColor = HudColors.BackgroundBase,
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
            .hudDashedBorder(accent.accent, bottom = true)
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
