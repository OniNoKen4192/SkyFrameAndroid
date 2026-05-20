package com.skyframe.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent

/**
 * Post-onboarding permission cascade. Reached only on first run, after
 * SettingsScreen SAVE succeeds. Three rows, each independently tappable:
 *
 *   POST_NOTIFICATIONS (Android 13+) - permission dialog
 *   USE_FULL_SCREEN_INTENT (Android 14+) - system intent
 *   Battery optimization whitelist - system intent
 *
 * Each row shows current status + rationale. The CONTINUE button is
 * always enabled - permissions are optional from the app's perspective;
 * the user can revisit via SettingsScreen banner if they decline now.
 */
@Composable
fun PermissionScreen(onContinue: () -> Unit) {
    val accent = LocalHudAccent.current.accent
    val context = LocalContext.current
    // refreshTick reruns the permission-state reads after the system dialogs
    // return, so the row checkmarks update without leaving the screen.
    var refreshTick by remember { mutableIntStateOf(0) }

    // Re-read permission state every time the screen resumes. The battery and
    // full-screen rows launch system-settings intents (not the permission
    // launcher), so returning from them only surfaces here via ON_RESUME -
    // without this, their checkmarks stayed stale after granting.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler { /* swallow - force-completion */ }

    val notificationGranted = remember(refreshTick) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val fullScreenGranted = remember(refreshTick) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) true
        else (context.getSystemService(NotificationManager::class.java)
            ?.canUseFullScreenIntent() == true)
    }

    val batteryWhitelisted = remember(refreshTick) {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.BackgroundBase)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(HudColors.BackgroundDeep)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TERMINAL // PERMISSIONS",
                color = accent,
                style = HudType.titleBar,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Severe weather alerts require these permissions. " +
                    "You can change them later in Settings.",
                color = HudColors.ForegroundDim,
                style = HudType.bodyMono,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            PermissionRow(
                title = "NOTIFICATIONS",
                rationale = "Required for severe weather alerts when the app is closed.",
                granted = notificationGranted,
                accent = accent,
                onTap = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PermissionRow(
                    title = "FULL-SCREEN INTENT",
                    rationale = "Lets life-threatening alerts show on your lock screen.",
                    granted = fullScreenGranted,
                    accent = accent,
                    onTap = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        ).apply { data = Uri.fromParts("package", context.packageName, null) }
                        context.startActivity(intent)
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            PermissionRow(
                title = "BATTERY OPTIMIZATION",
                rationale = "Improves background reliability on aggressive OEMs (Samsung, Xiaomi).",
                granted = batteryWhitelisted,
                accent = accent,
                onTap = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
            )

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "[ CONTINUE ]",
                    color = accent,
                    style = HudType.titleBar,
                    modifier = Modifier
                        .border(BorderStroke(1.dp, accent))
                        .clickable(onClick = onContinue)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    rationale: String,
    granted: Boolean,
    accent: Color,
    onTap: () -> Unit,
) {
    val borderColor = if (granted) accent else HudColors.ForegroundDim
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, borderColor))
            .clickable(enabled = !granted, onClick = onTap)
            .padding(16.dp),
    ) {
        Text(
            text = if (granted) "[✓] $title" else "[ ] $title",
            color = if (granted) accent else HudColors.Foreground,
            style = HudType.titleBar,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = rationale,
            color = HudColors.ForegroundDim,
            style = HudType.metaLabel,
        )
    }
}
