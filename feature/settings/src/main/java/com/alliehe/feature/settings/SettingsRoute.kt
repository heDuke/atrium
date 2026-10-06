package com.alliehe.feature.settings

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.theme.TransformingContent

@Composable
internal fun ObserveSettingsErrors(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.writeErrors.collect { Toast.makeText(context, R.string.settings_save_failed, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
fun SettingsRoute(onOpenHidden: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val prefsState by viewModel.prefs.collectAsStateWithLifecycle()
    val prefs = prefsState ?: return
    ObserveSettingsErrors(viewModel)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var homeState by remember { mutableStateOf(DefaultLauncherProbe.state(context)) }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) homeState = DefaultLauncherProbe.state(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        homeState = DefaultLauncherProbe.state(context)
        Toast.makeText(context, if (homeState == HomeRoleState.Held) R.string.settings_home_granted
            else R.string.settings_home_not_granted, Toast.LENGTH_SHORT).show()
    }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding,
            modifier = Modifier.fillMaxSize().selectableGroup()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.settings_title)) }
            }
            item {
                SwitchButton(checked = prefs.drawerLayout == DrawerLayoutMode.Grid,
                    onCheckedChange = { viewModel.setDrawerLayout(if (it) DrawerLayoutMode.Grid else DrawerLayoutMode.List) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.settings_drawer_grid)) },
                    secondaryLabel = { Text(stringResource(R.string.settings_drawer_grid_secondary)) })
            }
            item {
                Button(onClick = onOpenHidden,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.settings_hidden_apps)) }
            }
            item {
                TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                    Text(stringResource(R.string.settings_section_performance), style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            val modes = listOf(
                PerformanceMode.Performance to R.string.settings_mode_performance,
                PerformanceMode.Balanced to R.string.settings_mode_balanced,
                PerformanceMode.PowerSaver to R.string.settings_mode_power_saver,
            )
            modes.forEach { (mode, label) ->
                item {
                    RadioButton(selected = prefs.effectivePerformanceMode == mode,
                        onSelect = { viewModel.setPerformanceMode(mode) }, enabled = !prefs.reduceMotion,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(stringResource(label)) })
                }
            }
            item {
                SwitchButton(checked = prefs.effectivePerformanceMode == PerformanceMode.PowerSaver,
                    onCheckedChange = viewModel::setPowerSaverEnabled, enabled = !prefs.reduceMotion,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.settings_power_saver_toggle)) },
                    secondaryLabel = { Text(stringResource(R.string.settings_power_saver_secondary)) })
            }
            item {
                SwitchButton(checked = prefs.reduceMotion, onCheckedChange = viewModel::setReduceMotion,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.settings_reduce_motion)) },
                    secondaryLabel = { Text(stringResource(R.string.settings_reduce_motion_secondary)) })
            }
            item {
                if (homeState == HomeRoleState.Available) {
                    Button(onClick = {
                        val intent = DefaultLauncherProbe.createRequestIntent(context)
                        if (intent == null) {
                            homeState = DefaultLauncherProbe.state(context)
                            Toast.makeText(context, R.string.settings_home_unavailable, Toast.LENGTH_SHORT).show()
                        } else try {
                            roleLauncher.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.settings_home_unavailable, Toast.LENGTH_SHORT).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(context, R.string.settings_home_unavailable, Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(stringResource(R.string.settings_set_default_launcher)) },
                        secondaryLabel = { Text(stringResource(R.string.settings_set_default_launcher_secondary)) })
                } else {
                    TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                        Text(stringResource(if (homeState == HomeRoleState.Held) R.string.settings_home_granted
                            else R.string.settings_home_unavailable), textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun HiddenAppsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val apps by viewModel.hiddenApps.collectAsStateWithLifecycle()
    ObserveSettingsErrors(viewModel)
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.settings_hidden_apps)) }
            }
            item {
                TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                    Text(stringResource(R.string.settings_hidden_hint), style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            if (apps.isEmpty()) item {
                TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                    Text(stringResource(R.string.settings_hidden_empty), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
            items(apps, key = { it.packageName }) { app ->
                Button(onClick = { viewModel.restoreHidden(app.packageName) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(app.label) },
                    secondaryLabel = { Text(stringResource(R.string.settings_restore_hidden)) })
            }
        }
    }
}
