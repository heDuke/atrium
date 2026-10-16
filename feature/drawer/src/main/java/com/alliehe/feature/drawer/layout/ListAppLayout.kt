package com.alliehe.feature.drawer.layout

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.model.LayoutDensity
import com.alliehe.core.model.componentKey
import com.alliehe.feature.drawer.AppIcon
import com.alliehe.feature.drawer.DrawerAppItem
import com.alliehe.feature.drawer.DrawerUiState
import com.alliehe.feature.drawer.R

/** (1) List: TLC, one Button per row (icon + name + overflow), sections pinned → favorites → all. */
object ListAppLayout : AppListLayout {
    @Composable
    override fun Content(state: DrawerUiState, actions: DrawerActions, modifier: Modifier) {
        val listState = rememberTransformingLazyColumnState()
        val spec = rememberTransformationSpec()
        ScreenScaffold(scrollState = listState, edgeButton = { SettingsEdgeButton(actions.onOpenSettings) }) { padding ->
            TransformingLazyColumn(state = listState, contentPadding = padding, modifier = modifier.fillMaxSize()) {
                drawerTopItems(state, actions, spec)
                state.sections.forEach { section ->
                    item(key = "header-${section.kind}") {
                        ListHeader(
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                            transformation = SurfaceTransformation(spec),
                        ) { Text(sectionTitle(section.kind)) }
                    }
                    items(section.apps, key = { it.entry.componentKey }) { app ->
                        AppRow(app, state, actions, spec)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.AppRow(
    app: DrawerAppItem,
    state: DrawerUiState,
    actions: DrawerActions,
    spec: TransformationSpec,
) {
    val compact = state.density == LayoutDensity.Compact
    val status = appStatus(app)
    val actionsLabel = stringResource(R.string.drawer_app_actions_for, app.entry.label)
    val bounds = remember { BoundsHolder() }
    val press = rememberLongPress { actions.onActions(app) }
    Button(
        onClick = { actions.onLaunch(app, bounds.rect) },
        onLongClick = press.trigger,
        onLongClickLabel = actionsLabel,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 40.dp else 52.dp)
            .transformedHeight(this, spec)
            .then(press.modifier)
            .semantics {
                stateDescription = status
                customActions = listOf(CustomAccessibilityAction(actionsLabel) { actions.onActions(app); true })
            },
        transformation = SurfaceTransformation(spec),
        icon = {
            AppIcon(
                app.entry,
                modifier = Modifier.trackBounds(bounds),
                size = if (compact) 24.dp else 32.dp,
            )
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    app.entry.label,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { actions.onActions(app) },
                    modifier = Modifier.size(if (compact) 28.dp else 32.dp)
                        .semantics { contentDescription = actionsLabel },
                ) { Text("⋮") }
            }
        },
        secondaryLabel = if (compact || status.isEmpty()) null else ({ Text(status) }),
    )
}
