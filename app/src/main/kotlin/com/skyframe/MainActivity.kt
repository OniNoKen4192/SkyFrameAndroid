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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HelloSkyFrame()
        }
    }
}

@Composable
private fun HelloSkyFrame() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1018)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "SKYFRAME",
            color = Color(0xFF22D3EE),
            fontSize = 32.sp
        )
    }
}
