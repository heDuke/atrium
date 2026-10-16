package com.alliehe.feature.drawer.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.model.LayoutDensity
import com.alliehe.core.model.componentKey
import com.alliehe.core.theme.TransformingContent
import com.alliehe.feature.drawer.AppIcon
import com.alliehe.feature.drawer.DrawerAppItem
import com.alliehe.feature.drawer.DrawerUiState
import com.alliehe.feature.drawer.R

private const val COLUMNS = 2

/**
 * (2) Grid: TLC rows of a fixed 2 columns, icon-only, ListHeader per section.
 * The very first and last rows get extra horizontal inset for the round screen
 * instead of changing the column count.
 */
object GridAppLayout : AppListLayout {
    @Composable
    override fun Content(state: DrawerUiState, actions: DrawerActions, modifier: Modifier) {
        val listState = rememberTransformingLazyColumnState()
        val spec = rememberTransformationSpec()
        val compact = state.density == LayoutDensity.Compact
        val cell = if (compact) 44.dp else 52.dp
        val icon = if (compact) 30.dp else 36.dp
        val chunked = state.sections.map { it to it.apps.chunked(COLUMNS) }
        val firstRow = chunked.firstOrNull { it.second.isNotEmpty() }?.second?.first()
        val lastRow = chunked.lastOrNull { it.second.isNotEmpty() }?.second?.last()
        ScreenScaffold(scrollState = listState, edgeButton = { SettingsEdgeButton(actions.onOpenSettings) }) { padding ->
            TransformingLazyColumn(state = listState, contentPadding = padding, modifier = modifier.fillMaxSize()) {
                drawerTopItems(state, actions, spec)
                chunked.forEach { (section, rows) ->
                    item(key = "header-${section.kind}") {
                        ListHeader(
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                            transformation = SurfaceTransformation(spec),
                        ) { Text(sectionTitle(section.kind)) }
                    }
                    items(rows, key = { row -> row.joinToString("|") { it.entry.componentKey } }) { row ->
                        val inset = if (row === firstRow || row === lastRow) 20.dp else 0.dp
                        TransformingContent(
                            SurfaceTransformation(spec),
                            Modifier.fillMaxWidth().transformedHeight(this, spec).padding(horizontal = inset),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            ) {
                                row.forEach { app -> GridCell(app, state, actions, cell, icon) }
                                repeat(COLUMNS - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.size(cell)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCell(app: DrawerAppItem, state: DrawerUiState, actions: DrawerActions, cell: Dp, icon: Dp) {
    val status = appStatus(app)
    val actionsLabel = stringResource(R.string.drawer_app_actions_for, app.entry.label)
    val bounds = remember { BoundsHolder() }
    val press = rememberLongPress { actions.onActions(app) }
    FilledTonalIconButton(
        onClick = { actions.onLaunch(app, bounds.rect) },
        onLongClick = press.trigger,
        onLongClickLabel = actionsLabel,
        modifier = Modifier.size(cell).then(press.modifier).trackBounds(bounds).semantics {
            contentDescription = app.entry.label
            stateDescription = status
            customActions = listOf(CustomAccessibilityAction(actionsLabel) { actions.onActions(app); true })
        },
    ) {
        AppIcon(state.icons[app.entry.componentKey], app.entry.label, size = icon)
    }
}
