package com.alliehe.feature.drawer

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.data.AppShortcut
import com.alliehe.core.data.SystemNavigation
import com.alliehe.core.model.componentKey
import com.alliehe.core.theme.LocalAtriumMotion
import com.alliehe.core.ui.AppIcon
import com.alliehe.core.ui.EmptyState
import com.alliehe.core.ui.LocalMessageHost
import com.alliehe.core.ui.MessageKind
import com.alliehe.core.ui.SectionHeader
import com.alliehe.feature.drawer.layout.DrawerActions
import com.alliehe.feature.drawer.layout.appListLayoutFor
import com.alliehe.core.ui.R as UiR

@Composable
fun DrawerRoute(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenActions: (String) -> Unit,
    onOpenHidden: () -> Unit = onOpenSettings,
    viewModel: DrawerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val motion = LocalAtriumMotion.current
    val messages = LocalMessageHost.current
    ObserveWriteErrors(viewModel)
    val actions = remember(context, view, motion, messages, onOpenSettings, onOpenSearch, onOpenActions, onOpenHidden) {
        DrawerActions(
            onLaunch = { app, bounds ->
                val source = if (motion.reduceMotion) null else view
                if (AppLauncher.launch(context, app.entry, source, bounds) == LaunchResult.Started) {
                    viewModel.recordLaunch(app.entry)
                } else {
                    messages.show(MessageKind.Failure, R.string.launch_failed)
                    viewModel.refresh()
                }
            },
            onActions = { app -> onOpenActions(app.entry.componentKey) },
            onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings,
            onOpenHidden = onOpenHidden,
            onRetry = viewModel::refresh,
            onReorder = viewModel::moveHoneycomb,
            onBackToWatchFace = { (context as? Activity)?.let(SystemNavigation::backToWatchFace) },
            onTogglePinned = { app -> viewModel.setPinned(app.entry.packageName, !app.pinned) },
        )
    }
    // Layout switch: cross-fade with M3 effects spring (snap when motion is reduced).
    Crossfade(
        targetState = uiState.layout,
        animationSpec = if (motion.fades) MaterialTheme.motionScheme.defaultEffectsSpec() else snap(),
        label = "drawer-layout",
    ) { mode ->
        appListLayoutFor(mode).Content(uiState, actions, Modifier)
    }
}

@Composable
private fun ObserveWriteErrors(viewModel: DrawerViewModel) {
    val messages = LocalMessageHost.current
    LaunchedEffect(viewModel, messages) {
        viewModel.writeErrors.collect { messages.show(MessageKind.Failure, R.string.prefs_save_failed) }
    }
}

@Composable
fun AppActionsRoute(componentKey: String?, onDismiss: () -> Unit, viewModel: DrawerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val targetApp = uiState.apps.firstOrNull { it.entry.componentKey == componentKey }
    var hiding by remember(componentKey) { mutableStateOf(false) }
    val messages = LocalMessageHost.current
    LaunchedEffect(hiding, targetApp, uiState.loading) {
        if (hiding && targetApp == null && !uiState.loading) onDismiss()
    }
    ObserveWriteErrors(viewModel)
    // App shortcuts: only when Atrium is the default HOME (platform restriction); empty otherwise.
    val packageName = targetApp?.entry?.packageName
    val shortcuts by produceState(emptyList<AppShortcut>(), packageName) {
        value = if (packageName != null && viewModel.shortcutsAvailable()) viewModel.shortcutsFor(packageName) else emptyList()
    }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, edgeButton = {
        EdgeButton(onClick = onDismiss) { Text(stringResource(R.string.drawer_actions_dismiss)) }
    }) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        targetApp?.let { AppIcon(it.entry, size = 24.dp) }
                        Text(targetApp?.entry?.label ?: stringResource(R.string.drawer_actions_title),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (targetApp != null) {
                if (shortcuts.isNotEmpty()) {
                    item(key = "shortcuts-header") { SectionHeader(stringResource(R.string.drawer_shortcuts), spec) }
                    items(shortcuts, key = { "shortcut-${it.id}" }) { shortcut ->
                        Button(onClick = {
                            if (!viewModel.startShortcut(shortcut)) messages.show(MessageKind.Failure, R.string.shortcut_failed)
                        },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                            transformation = SurfaceTransformation(spec),
                            label = { Text(shortcut.label, maxLines = 2, overflow = TextOverflow.Ellipsis) })
                    }
                    item(key = "actions-header") { SectionHeader(stringResource(R.string.drawer_actions_title), spec) }
                }
                item {
                    Button(onClick = { viewModel.setPinned(targetApp.entry.packageName, !targetApp.pinned) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(painterResource(UiR.drawable.ic_atrium_pin), contentDescription = null) },
                        label = { Text(stringResource(if (targetApp.pinned) R.string.drawer_action_unpin else R.string.drawer_action_pin)) })
                }
                item {
                    Button(onClick = { viewModel.setFavorite(targetApp.entry.packageName, !targetApp.favorite) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(painterResource(UiR.drawable.ic_atrium_star), contentDescription = null) },
                        label = { Text(stringResource(if (targetApp.favorite) R.string.drawer_action_unfavorite else R.string.drawer_action_favorite)) })
                }
                item {
                    Button(onClick = { hiding = true; viewModel.setHidden(targetApp.entry.packageName, true) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(painterResource(UiR.drawable.ic_atrium_hide), contentDescription = null) },
                        label = { Text(stringResource(R.string.drawer_action_hide)) },
                        secondaryLabel = { Text(stringResource(R.string.drawer_hide_hint)) })
                }
            } else {
                item {
                    EmptyState(stringResource(if (uiState.loading) R.string.catalog_loading else R.string.app_not_visible), spec)
                }
            }
        }
    }
}
