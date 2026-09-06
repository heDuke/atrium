package com.alliehe.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.model.UserPrefs

/**
 * Settings: G3 a11y, G5 power modes, drawer layout, optional F11.
 * Wear M3 only — ScreenScaffold + TLC + SwitchButton / Button (no Chip).
 */
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    SettingsScreen(
        prefs = prefs,
        onDrawerGridChange = { enabled ->
            viewModel.setDrawerLayout(
                if (enabled) DrawerLayoutMode.Grid else DrawerLayoutMode.List,
            )
        },
        onPerformanceMode = viewModel::setPerformanceMode,
        onPowerSaverChange = viewModel::setPowerSaverEnabled,
        onReduceMotionChange = viewModel::setReduceMotion,
    )
}

@Composable
internal fun SettingsScreen(
    prefs: UserPrefs,
    onDrawerGridChange: (Boolean) -> Unit,
    onPerformanceMode: (PerformanceMode) -> Unit,
    onPowerSaverChange: (Boolean) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val context = LocalContext.current
    val showDefaultLauncher = remember(context) { DefaultLauncherProbe.isAvailable(context) }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* F11: never block; ignore result */ }

    val modeLocked = prefs.reduceMotion
    val powerSaverOn = prefs.performanceMode == PerformanceMode.PowerSaver
    val selectedMode = prefs.effectivePerformanceMode

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                SectionLabel(
                    text = stringResource(R.string.settings_section_layout),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item {
                SwitchButton(
                    checked = prefs.drawerLayout == DrawerLayoutMode.Grid,
                    onCheckedChange = onDrawerGridChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    label = { Text(stringResource(R.string.settings_drawer_grid)) },
                    secondaryLabel = {
                        Text(stringResource(R.string.settings_drawer_grid_secondary))
                    },
                )
            }

            item {
                SectionLabel(
                    text = stringResource(R.string.settings_section_performance),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item {
                ModeButton(
                    label = stringResource(R.string.settings_mode_performance),
                    selected = selectedMode == PerformanceMode.Performance,
                    enabled = !modeLocked,
                    onClick = { onPerformanceMode(PerformanceMode.Performance) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item {
                ModeButton(
                    label = stringResource(R.string.settings_mode_balanced),
                    selected = selectedMode == PerformanceMode.Balanced,
                    enabled = !modeLocked,
                    onClick = { onPerformanceMode(PerformanceMode.Balanced) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item {
                ModeButton(
                    label = stringResource(R.string.settings_mode_power_saver),
                    selected = selectedMode == PerformanceMode.PowerSaver,
                    enabled = !modeLocked,
                    onClick = { onPerformanceMode(PerformanceMode.PowerSaver) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            if (modeLocked) {
                item {
                    Text(
                        text = stringResource(R.string.settings_mode_locked_by_a11y),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
            }
            item {
                SwitchButton(
                    checked = powerSaverOn || modeLocked,
                    onCheckedChange = onPowerSaverChange,
                    enabled = !modeLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    label = { Text(stringResource(R.string.settings_power_saver_toggle)) },
                    secondaryLabel = {
                        Text(stringResource(R.string.settings_power_saver_secondary))
                    },
                )
            }

            item {
                SectionLabel(
                    text = stringResource(R.string.settings_section_a11y),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item {
                SwitchButton(
                    checked = prefs.reduceMotion,
                    onCheckedChange = onReduceMotionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    label = { Text(stringResource(R.string.settings_reduce_motion)) },
                    secondaryLabel = {
                        Text(stringResource(R.string.settings_reduce_motion_secondary))
                    },
                )
            }

            if (showDefaultLauncher) {
                item {
                    SectionLabel(
                        text = stringResource(R.string.settings_section_optional),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                    )
                }
                item {
                    Button(
                        onClick = {
                            val intent = DefaultLauncherProbe.createRequestIntent(context)
                                ?: return@Button
                            runCatching { roleLauncher.launch(intent) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        label = {
                            Text(stringResource(R.string.settings_set_default_launcher))
                        },
                        secondaryLabel = {
                            Text(stringResource(R.string.settings_set_default_launcher_secondary))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = if (selected) {
            ButtonDefaults.filledVariantButtonColors()
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
    ) {
        Text(label)
    }
}
