package com.alliehe.feature.drawer.layout

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
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
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.RevealValue
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.rememberRevealState
import com.alliehe.core.model.LayoutDensity
import com.alliehe.core.ui.AppIcon
import com.alliehe.core.ui.SectionHeader
import com.alliehe.feature.drawer.DrawerAppItem
import com.alliehe.feature.drawer.DrawerUiState
import com.alliehe.feature.drawer.R
import kotlinx.coroutines.launch
import com.alliehe.core.ui.R as UiR

/**
 * (1) List: TLC, one Button per row (icon + name), sections recents → pinned → favorites → all.
 * Secondary actions live behind SwipeToReveal (swipe left: "more" / pin); long-press and the
 * TalkBack custom actions still open the full actions screen.
 */
object ListAppLayout : AppListLayout {
    @Composable
    override fun Content(state: DrawerUiState, actions: DrawerActions, modifier: Modifier) {
        val listState = rememberTransformingLazyColumnState()
        val spec = rememberTransformationSpec()
        ScreenScaffold(scrollState = listState, edgeButton = { SettingsEdgeButton(actions.onOpenSettings) }) { padding ->
            TransformingLazyColumn(state = listState, contentPadding = padding, modifier = modifier.fillMaxSize()) {
                drawerTopItems(state, actions, spec)
                state.sections.forEach { section ->
                    item(key = "header-${section.kind}") { SectionHeader(sectionTitle(section.kind), spec) }
                    items(section.apps, key = { section.itemKey(it) }) { app ->
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
    val pinLabel = stringResource(if (app.pinned) R.string.drawer_action_unpin else R.string.drawer_action_pin)
    val moreLabel = stringResource(R.string.drawer_action_more)
    val bounds = remember { BoundsHolder() }
    val press = rememberLongPress { actions.onActions(app) }
    val revealState = rememberRevealState()
    val scope = rememberCoroutineScope()
    fun close() = scope.launch { revealState.animateTo(RevealValue.Covered) }
    SwipeToReveal(
        primaryAction = {
            PrimaryActionButton(
                onClick = { close(); actions.onActions(app) },
                icon = { Icon(painterResource(UiR.drawable.ic_atrium_more), contentDescription = null) },
                text = { Text(moreLabel) },
            )
        },
        onSwipePrimaryAction = { close(); actions.onActions(app) },
        secondaryAction = {
            SecondaryActionButton(
                onClick = { close(); actions.onTogglePinned(app) },
                icon = { Icon(painterResource(UiR.drawable.ic_atrium_pin), contentDescription = pinLabel) },
            )
        },
        modifier = Modifier.transformedHeight(this, spec),
    ) {
        Button(
            onClick = { actions.onLaunch(app, bounds.rect) },
            onLongClick = press.trigger,
            onLongClickLabel = actionsLabel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 48.dp else 52.dp)
                .then(press.modifier)
                .semantics {
                    stateDescription = status
                    // Swipe actions are not reachable with TalkBack; expose them (and the full sheet) here.
                    customActions = listOf(
                        CustomAccessibilityAction(actionsLabel) { actions.onActions(app); true },
                        CustomAccessibilityAction(pinLabel) { actions.onTogglePinned(app); true },
                    )
                },
            transformation = SurfaceTransformation(spec),
            icon = {
                AppIcon(app.entry, modifier = Modifier.trackBounds(bounds), size = if (compact) 24.dp else 32.dp)
            },
            label = {
                Text(app.entry.label, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            },
            secondaryLabel = if (compact || status.isEmpty()) null else ({ Text(status) }),
        )
    }
}
