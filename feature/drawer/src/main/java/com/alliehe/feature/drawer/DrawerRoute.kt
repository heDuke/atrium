package com.alliehe.feature.drawer

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
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
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.componentKey
import com.alliehe.core.theme.TransformingContent

@Composable
fun DrawerRoute(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenActions: (String) -> Unit,
    viewModel: DrawerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ObserveWriteErrors(viewModel)
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val launch: (DrawerAppItem) -> Unit = { app ->
        if (AppLauncher.launch(context, app.entry) != LaunchResult.Started) {
            Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
            viewModel.refresh()
        }
    }
    ScreenScaffold(scrollState = listState, edgeButton = {
        EdgeButton(onClick = onOpenSettings) { Text(stringResource(R.string.drawer_open_settings)) }
    }) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.drawer_title)) }
            }
            item {
                Button(onClick = onOpenSearch,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.drawer_open_search)) }
            }
            item {
                TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                    Text(stringResource(R.string.drawer_gesture_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
            if (uiState.refreshFailed) {
                item {
                    Button(onClick = viewModel::refresh,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) { Text(stringResource(R.string.catalog_retry)) }
                }
            }
            if (uiState.loading || uiState.apps.isEmpty()) {
                item {
                    TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                        Text(stringResource(if (uiState.loading) R.string.catalog_loading else R.string.drawer_empty_hint),
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else when (uiState.layout) {
                DrawerLayoutMode.List -> items(uiState.apps, key = { it.entry.componentKey }) { app ->
                    val status = appStatus(app)
                    val actionsLabel = stringResource(R.string.drawer_app_actions_for, app.entry.label)
                    Button(
                        onClick = { launch(app) },
                        onLongClick = { onOpenActions(app.entry.componentKey) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                            .semantics {
                                stateDescription = status
                                customActions = listOf(CustomAccessibilityAction(actionsLabel) {
                                    onOpenActions(app.entry.componentKey); true
                                })
                            },
                        transformation = SurfaceTransformation(spec),
                        icon = { AppIcon(uiState.icons[app.entry.componentKey], app.entry.label) },
                        label = { Text(app.entry.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = if (status.isEmpty()) null else ({ Text(status) }),
                    )
                }
                DrawerLayoutMode.Grid -> items(uiState.apps.chunked(2), key = { row ->
                    row.joinToString("|") { it.entry.componentKey }
                }) { row ->
                    TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { app ->
                                val status = appStatus(app)
                                val actionsLabel = stringResource(R.string.drawer_app_actions_for, app.entry.label)
                                Button(
                                    onClick = { launch(app) },
                                    onLongClick = { onOpenActions(app.entry.componentKey) },
                                    modifier = Modifier.weight(1f).heightIn(min = 72.dp).semantics {
                                        stateDescription = status
                                        customActions = listOf(CustomAccessibilityAction(actionsLabel) {
                                            onOpenActions(app.entry.componentKey); true
                                        })
                                    },
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp),
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 4.dp)) {
                                        AppIcon(uiState.icons[app.entry.componentKey], app.entry.label)
                                        Text(app.entry.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center)
                                        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun appStatus(app: DrawerAppItem): String = when {
    app.pinned && app.favorite -> stringResource(R.string.app_pinned_favorite)
    app.pinned -> stringResource(R.string.app_pinned)
    app.favorite -> stringResource(R.string.app_favorite)
    else -> ""
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
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
