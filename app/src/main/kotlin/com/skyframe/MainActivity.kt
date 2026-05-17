package com.skyframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudFontFamily
import com.skyframe.theme.HudTheme
import com.skyframe.theme.LocalHudAccent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HudTheme {
                HelloSkyFrame()
            }
        }
    }
}

@Composable
private fun HelloSkyFrame() {
    val accent = LocalHudAccent.current
    Box(
        modifier = Modifier.fillMaxSize().background(HudColors.BackgroundBase),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "SKYFRAME",
            color = accent.accent,
            fontFamily = HudFontFamily,
            fontSize = 32.sp,
        )
    }
}
