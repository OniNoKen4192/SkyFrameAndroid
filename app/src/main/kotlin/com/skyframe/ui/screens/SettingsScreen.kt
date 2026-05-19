package com.skyframe.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.viewmodel.GpsState
import com.skyframe.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onSaved: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalHudAccent.current.accent
    val context = LocalContext.current

    // Force-completion mode: swallow system back until first save succeeds.
    BackHandler(enabled = !state.isConfigured) { /* no-op */ }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onGpsPermissionGranted()
        } else {
            // shouldShowRequestPermissionRationale returns false after permanent denial
            // ("Don't ask again" / two consecutive denials on modern Android).
            val activity = context as? Activity
            val permanent = activity != null && !activity.shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
            viewModel.onGpsPermissionDenied(permanently = permanent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.BackgroundBase),
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
                text = "TERMINAL // SETTINGS",
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
            HudFieldLabel("LOCATION")
            HudTextField(
                value = state.locationInput,
                onValueChange = viewModel::onLocationChange,
                placeholder = "53154 or 42.8744, -87.8633",
                accent = accent,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )
            Spacer(Modifier.height(8.dp))

            GpsButton(
                state = state.gpsState,
                accent = accent,
                onTap = {
                    when (state.gpsState) {
                        GpsState.PermissionDeniedPermanent -> {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                        else -> permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
            )

            Spacer(Modifier.height(16.dp))

            HudFieldLabel("EMAIL")
            HudTextField(
                value = state.emailInput,
                onValueChange = viewModel::onEmailChange,
                placeholder = "you@example.com",
                accent = accent,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
            Text(
                text = "Used for NWS User-Agent header. Not transmitted to any third party.",
                color = HudColors.ForegroundDim,
                style = HudType.metaLabel,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))

            if (state.showUpdateCheckCheckbox) {
                HudDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { viewModel.onUpdateCheckToggle(!state.updateCheckEnabled) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.updateCheckEnabled,
                        onCheckedChange = viewModel::onUpdateCheckToggle,
                        colors = CheckboxDefaults.colors(
                            checkedColor = accent,
                            uncheckedColor = HudColors.ForegroundDim,
                            checkmarkColor = HudColors.BackgroundBase,
                        ),
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = "Check GitHub for SkyFrame updates",
                            color = HudColors.Foreground,
                            style = HudType.titleBar,
                        )
                        Text(
                            text = "Polls once per day. Off by default. (Sideload-only)",
                            color = HudColors.ForegroundDim,
                            style = HudType.metaLabel,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HudDivider()
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "COSMETIC SKIN",
                    color = HudColors.ForegroundDim,
                    style = HudType.metricLabel,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    text = "Default (HUD cyan) ▾",
                    color = HudColors.ForegroundDim,
                    style = HudType.bodyMono,
                    modifier = Modifier
                        .border(
                            BorderStroke(1.dp, HudColors.ForegroundDim),
                            RoundedCornerShape(0.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Text(
                text = "Theme selection lands in a future plan.",
                color = HudColors.ForegroundDim,
                style = HudType.metaLabel,
            )

            Spacer(Modifier.height(24.dp))

            if (state.saveState is SettingsViewModel.SaveState.Error) {
                Text(
                    text = "! ${(state.saveState as SettingsViewModel.SaveState.Error).message}",
                    color = androidx.compose.ui.graphics.Color(0xFFFF4444),
                    style = HudType.bodyMono,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (state.isConfigured) {
                    HudActionButton(label = "CANCEL", enabled = true, accent = HudColors.ForegroundDim) {
                        onSaved()
                    }
                    Spacer(Modifier.padding(horizontal = 12.dp))
                }
                val saving = state.saveState is SettingsViewModel.SaveState.Resolving
                HudActionButton(
                    label = if (saving) "RESOLVING…" else "SAVE",
                    enabled = !saving,
                    accent = accent,
                ) {
                    viewModel.save(onSaved)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HudFieldLabel(text: String) {
    Text(
        text = text,
        color = HudColors.ForegroundDim,
        style = HudType.metricLabel,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun HudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    accent: androidx.compose.ui.graphics.Color,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = HudType.bodyMono.copy(color = HudColors.Foreground),
        cursorBrush = SolidColor(accent),
        keyboardOptions = keyboardOptions,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, accent))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = HudColors.ForegroundDim,
                        style = HudType.bodyMono,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun GpsButton(
    state: GpsState,
    accent: androidx.compose.ui.graphics.Color,
    onTap: () -> Unit,
) {
    val (label, color) = when (state) {
        GpsState.Idle, GpsState.Available -> "⌖ USE MY LOCATION" to accent
        GpsState.Requesting -> "⌖ REQUESTING…" to HudColors.ForegroundDim
        GpsState.NoLastKnown -> "⌖ GPS PENDING — try moving outside" to HudColors.ForegroundDim
        GpsState.PermissionDenied -> "⌖ USE MY LOCATION" to accent
        GpsState.PermissionDeniedPermanent -> "⌖ GPS UNAVAILABLE — open system settings" to HudColors.ForegroundDim
    }
    Text(
        text = label,
        color = color,
        style = HudType.titleBar,
        modifier = Modifier
            .border(BorderStroke(1.dp, color))
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun HudDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HudColors.ForegroundDim.copy(alpha = 0.3f)),
    )
}

@Composable
private fun HudActionButton(
    label: String,
    enabled: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val color = if (enabled) accent else HudColors.ForegroundDim
    Text(
        text = "[ $label ]",
        color = color,
        style = HudType.titleBar,
        modifier = Modifier
            .border(BorderStroke(1.dp, color))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
