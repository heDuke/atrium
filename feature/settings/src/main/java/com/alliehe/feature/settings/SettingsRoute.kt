package com.alliehe.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.color.ThemeSource
import com.alliehe.core.data.DefaultLauncherProbe
import com.alliehe.core.data.HomeRoleState
import com.alliehe.core.data.SystemNavigation
import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.LayoutDensity
import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.theme.TransformingContent
import com.alliehe.core.ui.AppIcon
import com.alliehe.core.ui.EmptyState
import com.alliehe.core.ui.LocalMessageHost
import com.alliehe.core.ui.MessageKind
import com.alliehe.core.ui.SectionHeader
import com.alliehe.core.ui.R as UiR

@Composable
internal fun ObserveSettingsErrors(viewModel: SettingsViewModel) {
    val messages = LocalMessageHost.current
    LaunchedEffect(viewModel, messages) {
        viewModel.writeErrors.collect { messages.show(MessageKind.Failure, R.string.settings_save_failed) }
    }
}

@Composable
fun SettingsRoute(
    onOpenHidden: () -> Unit,
    onOpenTheme: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefsState by viewModel.prefs.collectAsStateWithLifecycle()
    val prefs = prefsState ?: return
    ObserveSettingsErrors(viewModel)
    val context = LocalContext.current
    val messages = LocalMessageHost.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var homeState by remember { mutableStateOf(DefaultLauncherProbe.state(context)) }
    val systemPages = remember(context) { SystemNavigation.availablePages(context) }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) homeState = DefaultLauncherProbe.state(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        homeState = DefaultLauncherProbe.state(context)
        if (homeState == HomeRoleState.Held) messages.show(MessageKind.Success, R.string.settings_home_granted)
        else messages.show(MessageKind.Info, R.string.settings_home_not_granted)
    }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.settings_title)) }
            }
            item(key = "theme") {
                Button(onClick = onOpenTheme,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.settings_theme)) },
                    secondaryLabel = { Text(stringResource(when (prefs.themeSource) {
                        ThemeSource.System -> R.string.settings_theme_secondary_system
                        ThemeSource.Seed -> R.string.settings_theme_secondary_seed
                        ThemeSource.Brand -> R.string.settings_theme_secondary_brand
                    })) })
            }
            item(key = "h-layout") { SectionHeader(stringResource(R.string.settings_section_layout), spec) }
            item(key = "layouts") {
                RadioGroupItem(spec) {
                    DrawerLayoutMode.entries.forEach { mode ->
                        LayoutOption(mode, prefs.drawerLayout == mode) { viewModel.setDrawerLayout(mode) }
                    }
                }
            }
            if (prefs.drawerLayout != DrawerLayoutMode.Honeycomb) {
                item(key = "h-density") { SectionHeader(stringResource(R.string.settings_density), spec) }
                item(key = "density") {
                    RadioGroupItem(spec) {
                        listOf(LayoutDensity.Standard to R.string.settings_density_standard,
                            LayoutDensity.Compact to R.string.settings_density_compact).forEach { (density, label) ->
                            RadioButton(selected = prefs.layoutDensity == density,
                                onSelect = { viewModel.setLayoutDensity(density) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(label)) })
                        }
                    }
                }
            } else {
                item(key = "reset-honeycomb") {
                    Button(onClick = { viewModel.resetHoneycombOrder { messages.show(MessageKind.Success, R.string.settings_reset_honeycomb_done) } },
                        enabled = prefs.honeycombOrder.isNotEmpty(),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(stringResource(R.string.settings_reset_honeycomb)) },
                        secondaryLabel = { Text(stringResource(R.string.settings_reset_honeycomb_secondary)) })
                }
            }
            item(key = "hidden") {
                Button(onClick = onOpenHidden,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    icon = { Icon(painterResource(UiR.drawable.ic_atrium_hide), contentDescription = null) },
                    label = { Text(stringResource(R.string.settings_hidden_apps)) })
            }
            item(key = "clear-recents") {
                Button(onClick = { viewModel.clearRecents { messages.show(MessageKind.Success, R.string.settings_clear_recents_done) } },
                    enabled = prefs.recentComponents.isNotEmpty(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.settings_clear_recents)) })
            }
            item(key = "h-performance") { SectionHeader(stringResource(R.string.settings_section_performance), spec) }
            item(key = "modes") {
                RadioGroupItem(spec) {
                    listOf(
                        PerformanceMode.Performance to R.string.settings_mode_performance,
                        PerformanceMode.Balanced to R.string.settings_mode_balanced,
                        PerformanceMode.PowerSaver to R.string.settings_mode_power_saver,
                    ).forEach { (mode, label) ->
                        RadioButton(selected = prefs.effectivePerformanceMode == mode,
                            onSelect = { viewModel.setPerformanceMode(mode) }, enabled = !prefs.reduceMotion,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(label)) })
                    }
                }
            }
            item(key = "power-saver") {
                SwitchButton(checked = prefs.effectivePerformanceMode == PerformanceMode.PowerSaver,
                    onCheckedChange = viewModel::setPowerSaverEnabled, enabled = !prefs.reduceMotion,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.settings_power_saver_toggle)) },
                    secondaryLabel = { Text(stringResource(R.string.settings_power_saver_secondary)) })
            }
            item(key = "h-a11y") { SectionHeader(stringResource(R.string.settings_section_a11y), spec) }
            item(key = "reduce-motion") {
                SwitchButton(checked = prefs.reduceMotion, onCheckedChange = viewModel::setReduceMotion,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(stringResource(R.string.settings_reduce_motion)) },
                    secondaryLabel = { Text(stringResource(R.string.settings_reduce_motion_secondary)) })
            }
            if (systemPages.isNotEmpty()) {
                item(key = "h-system") { SectionHeader(stringResource(R.string.settings_section_system), spec) }
                items(systemPages, key = { "page-${it.name}" }) { page ->
                    Button(onClick = {
                        if (!SystemNavigation.open(context, page)) messages.show(MessageKind.Failure, R.string.settings_page_failed)
                    },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(stringResource(page.label())) })
                }
            }
            item(key = "h-optional") { SectionHeader(stringResource(R.string.settings_section_optional), spec) }
            item(key = "home") {
                if (homeState == HomeRoleState.Available) {
                    Button(onClick = {
                        val intent = DefaultLauncherProbe.createRequestIntent(context)
                        if (intent == null) {
                            homeState = DefaultLauncherProbe.state(context)
                            messages.show(MessageKind.Info, R.string.settings_home_unavailable)
                        } else try {
                            roleLauncher.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            messages.show(MessageKind.Info, R.string.settings_home_unavailable)
                        } catch (_: SecurityException) {
                            messages.show(MessageKind.Info, R.string.settings_home_unavailable)
                        }
                    }, modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(stringResource(R.string.settings_set_default_launcher)) },
                        secondaryLabel = { Text(stringResource(R.string.settings_set_default_launcher_secondary)) })
                } else {
                    EmptyState(stringResource(if (homeState == HomeRoleState.Held) R.string.settings_home_granted
                        else R.string.settings_home_unavailable), spec)
                }
            }
        }
    }
}

private fun SystemNavigation.SettingsPage.label(): Int = when (this) {
    SystemNavigation.SettingsPage.WiFi -> R.string.settings_page_wifi
    SystemNavigation.SettingsPage.Bluetooth -> R.string.settings_page_bluetooth
    SystemNavigation.SettingsPage.Display -> R.string.settings_page_display
    SystemNavigation.SettingsPage.Sound -> R.string.settings_page_sound
    SystemNavigation.SettingsPage.Battery -> R.string.settings_page_battery
    SystemNavigation.SettingsPage.All -> R.string.settings_page_all
}

@Composable
fun HiddenAppsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val apps by viewModel.hiddenApps.collectAsStateWithLifecycle()
    ObserveSettingsErrors(viewModel)
    val messages = LocalMessageHost.current
    var confirm by remember { mutableStateOf<AppEntry?>(null) }
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
                    Text(stringResource(R.string.settings_hidden_hint),
                        style = androidx.wear.compose.material3.MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            if (apps.isEmpty()) item { EmptyState(stringResource(R.string.settings_hidden_empty), spec) }
            items(apps, key = { it.packageName }) { app ->
                Button(onClick = { confirm = app },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    icon = { AppIcon(app) },
                    label = { Text(app.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(stringResource(R.string.settings_restore_hidden)) })
            }
        }
    }
    val target = confirm
    AlertDialog(
        visible = target != null,
        onDismissRequest = { confirm = null },
        title = { Text(stringResource(R.string.settings_restore_confirm_title, target?.label.orEmpty())) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                target?.let { viewModel.restoreHidden(it.packageName) { messages.show(MessageKind.Success, R.string.settings_restore_done) } }
                confirm = null
            })
        },
    )
}
