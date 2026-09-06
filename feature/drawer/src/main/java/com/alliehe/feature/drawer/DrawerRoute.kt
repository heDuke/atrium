package com.alliehe.feature.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.model.DrawerLayoutMode

/**
 * F1 drawer: Wear M3 ScreenScaffold + TLC list/grid.
 * F2: honors pin/favorite/hide from UserPrefs; overflow opens actions.
 * EdgeButton only in ScreenScaffold slot — never as TLC last item.
 */
@Composable
fun DrawerRoute(
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: DrawerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var actionsTarget by remember { mutableStateOf<DrawerAppItem?>(null) }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val configuration = LocalConfiguration.current
    // Grid: max 2 columns on round watches via TLC rows of Buttons (no phone LazyGrid).
    val gridColumns = if (configuration.isScreenRound) 2 else 2

    val target = actionsTarget
    if (target != null) {
        AppActionsScreen(
            item = target,
            onDismiss = { actionsTarget = null },
            onPin = {
                viewModel.setPinned(target.entry.packageName, !target.pinned)
                actionsTarget = null
            },
            onFavorite = {
                viewModel.setFavorite(target.entry.packageName, !target.favorite)
                actionsTarget = null
            },
            onHide = {
                viewModel.setHidden(target.entry.packageName, true)
                actionsTarget = null
            },
        )
        return
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.drawer_open_settings))
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader {
                    Text(
                        text = stringResource(R.string.drawer_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Button(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                ) {
                    Text(stringResource(R.string.drawer_open_search))
                }
            }

            if (uiState.apps.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.drawer_empty_title),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.drawer_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
            } else when (uiState.layout) {
                DrawerLayoutMode.List -> {
                    items(
                        items = uiState.apps,
                        key = { it.entry.packageName + "/" + it.entry.activityName },
                    ) { app ->
                        AppListRow(
                            app = app,
                            onLaunch = { AppLauncher.launch(context, app.entry) },
                            onOpenActions = { actionsTarget = app },
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                        )
                    }
                }
                DrawerLayoutMode.Grid -> {
                    val rows = uiState.apps.chunked(gridColumns)
                    items(
                        items = rows,
                        key = { row ->
                            row.joinToString("|") {
                                it.entry.packageName + "/" + it.entry.activityName
                            }
                        },
                    ) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEach { app ->
                                Button(
                                    onClick = { AppLauncher.launch(context, app.entry) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = app.entry.label,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            repeat(gridColumns - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListRow(
    app: DrawerAppItem,
    onLaunch: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(
            onClick = onLaunch,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = app.entry.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onOpenActions) {
            Text(stringResource(R.string.drawer_app_actions))
        }
    }
}

/**
 * F2 pin / favorite / hide using Wear M3 ScreenScaffold + Button rows.
 */
@Composable
private fun AppActionsScreen(
    item: DrawerAppItem,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onDismiss) {
                Text(stringResource(R.string.drawer_actions_dismiss))
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListHeader {
                    Text(
                        text = stringResource(R.string.drawer_actions_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Text(
                    text = item.entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                )
            }
            item {
                Button(
                    onClick = onPin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                ) {
                    Text(
                        stringResource(
                            if (item.pinned) R.string.drawer_action_unpin
                            else R.string.drawer_action_pin,
                        ),
                    )
                }
            }
            item {
                Button(
                    onClick = onFavorite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                ) {
                    Text(
                        stringResource(
                            if (item.favorite) R.string.drawer_action_unfavorite
                            else R.string.drawer_action_favorite,
                        ),
                    )
                }
            }
            item {
                Button(
                    onClick = onHide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                ) {
                    Text(stringResource(R.string.drawer_action_hide))
                }
            }
        }
    }
}
