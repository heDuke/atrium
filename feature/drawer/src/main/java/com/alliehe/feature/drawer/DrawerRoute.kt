package com.alliehe.feature.drawer

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.model.componentKey
import com.alliehe.core.theme.LocalAtriumMotion
import com.alliehe.core.theme.TransformingContent
import com.alliehe.feature.drawer.layout.DrawerActions
import com.alliehe.feature.drawer.layout.appListLayoutFor

@Composable
fun DrawerRoute(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenActions: (String) -> Unit,
    viewModel: DrawerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val motion = LocalAtriumMotion.current
    ObserveWriteErrors(viewModel)
    val actions = remember(context, view, motion, onOpenSettings, onOpenSearch, onOpenActions) {
        DrawerActions(
            onLaunch = { app, bounds ->
                val source = if (motion.reduceMotion) null else view
                if (AppLauncher.launch(context, app.entry, source, bounds) != LaunchResult.Started) {
                    Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
                    viewModel.refresh()
                }
            },
            onActions = { app -> onOpenActions(app.entry.componentKey) },
            onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings,
            onRetry = viewModel::refresh,
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
    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.writeErrors.collect { Toast.makeText(context, R.string.prefs_save_failed, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
fun AppActionsRoute(componentKey: String?, onDismiss: () -> Unit, viewModel: DrawerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val targetApp = uiState.apps.firstOrNull { it.entry.componentKey == componentKey }
    var hiding by remember(componentKey) { mutableStateOf(false) }
    LaunchedEffect(hiding, targetApp, uiState.loading) {
        if (hiding && targetApp == null && !uiState.loading) onDismiss()
    }
    ObserveWriteErrors(viewModel)
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, edgeButton = {
        EdgeButton(onClick = onDismiss) { Text(stringResource(R.string.drawer_actions_dismiss)) }
    }) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)) {
                    Text(targetApp?.entry?.label ?: stringResource(R.string.drawer_actions_title),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (targetApp != null) {
                item {
                    Button(onClick = { viewModel.setPinned(targetApp.entry.packageName, !targetApp.pinned) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec)) {
                        Text(stringResource(if (targetApp.pinned) R.string.drawer_action_unpin else R.string.drawer_action_pin))
                    }
                }
                item {
                    Button(onClick = { viewModel.setFavorite(targetApp.entry.packageName, !targetApp.favorite) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec)) {
                        Text(stringResource(if (targetApp.favorite) R.string.drawer_action_unfavorite else R.string.drawer_action_favorite))
                    }
                }
                item {
                    Button(onClick = { hiding = true; viewModel.setHidden(targetApp.entry.packageName, true) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        label = { Text(stringResource(R.string.drawer_action_hide)) },
                        secondaryLabel = { Text(stringResource(R.string.drawer_hide_hint)) })
                }
            } else {
                item {
                    TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                        Text(stringResource(if (uiState.loading) R.string.catalog_loading else R.string.app_not_visible),
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
