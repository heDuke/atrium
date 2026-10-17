package com.alliehe.feature.drawer.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.placeholder
import androidx.wear.compose.material3.placeholderShimmer
import androidx.wear.compose.material3.rememberPlaceholderState
import com.alliehe.core.ui.EmptyState
import com.alliehe.core.ui.R as UiR
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.theme.LocalAtriumMotion
import com.alliehe.feature.drawer.DrawerAppItem
import com.alliehe.feature.drawer.DrawerUiState
import com.alliehe.feature.drawer.R
import com.alliehe.feature.drawer.SectionKind
import kotlinx.coroutines.launch

/** Callbacks shared by every layout. Bounds are in root (= host view) coordinates. */
@Stable
class DrawerActions(
    val onLaunch: (DrawerAppItem, Rect?) -> Unit,
    val onActions: (DrawerAppItem) -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenHidden: () -> Unit = onOpenSettings,
    /** Honeycomb reorder: indices into [DrawerUiState.honeycombApps]. */
    val onReorder: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onBackToWatchFace: () -> Unit = {},
    val onTogglePinned: (DrawerAppItem) -> Unit = {},
)

/** Rendering strategy for the drawer. Data (catalog + UserPrefs) arrives pre-sorted in [DrawerUiState]. */
interface AppListLayout {
    @Composable
    fun Content(state: DrawerUiState, actions: DrawerActions, modifier: Modifier)
}

fun appListLayoutFor(mode: DrawerLayoutMode): AppListLayout = when (mode) {
    DrawerLayoutMode.List -> ListAppLayout
    DrawerLayoutMode.Grid -> GridAppLayout
    DrawerLayoutMode.Honeycomb -> HoneycombAppLayout
}

/** Mutable, non-state holder: icon bounds are read only at launch time, so no recomposition. */
internal class BoundsHolder { var rect: Rect? = null }

internal fun Modifier.trackBounds(holder: BoundsHolder): Modifier =
    onGloballyPositioned { holder.rect = it.boundsInRoot() }

/** F2: light haptic + scale pop, then navigate to ActionsScreen. */
@Stable
internal class LongPressHandle(private val scale: Animatable<Float, *>, val trigger: () -> Unit) {
    val modifier: Modifier = Modifier.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
internal fun rememberLongPress(onTriggered: () -> Unit): LongPressHandle {
    val haptics = LocalHapticFeedback.current
    val motion by rememberUpdatedState(LocalAtriumMotion.current)
    val spec by rememberUpdatedState(MaterialTheme.motionScheme.fastSpatialSpec<Float>())
    val latest by rememberUpdatedState(onTriggered)
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    return remember(scale, haptics) {
        LongPressHandle(scale) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            if (!motion.decorative) {
                latest()
            } else {
                scope.launch {
                    scale.animateTo(1.12f, spec)
                    latest()
                    scale.snapTo(1f)
                }
            }
        }
    }
}

@Composable
internal fun sectionTitle(kind: SectionKind): String = stringResource(
    when (kind) {
        SectionKind.Recents -> R.string.drawer_section_recents
        SectionKind.Pinned -> R.string.drawer_section_pinned
        SectionKind.Favorites -> R.string.drawer_section_favorites
        SectionKind.All -> R.string.drawer_section_all
    },
)

@Composable
internal fun appStatus(app: DrawerAppItem): String = when {
    app.pinned && app.favorite -> stringResource(R.string.app_pinned_favorite)
    app.pinned -> stringResource(R.string.app_pinned)
    app.favorite -> stringResource(R.string.app_favorite)
    else -> ""
}

@Composable
internal fun SettingsEdgeButton(onClick: () -> Unit) {
    EdgeButton(onClick = onClick, buttonSize = EdgeButtonSize.Small) {
        Text(stringResource(R.string.drawer_open_settings))
    }
}

/**
 * Top of the TLC layouts: search, glance card (date/battery), back-to-watch-face, retry, and — only
 * while nothing is on screen — shimmer placeholders (loading) or an empty state with an action.
 */
internal fun TransformingLazyColumnScope.drawerTopItems(
    state: DrawerUiState,
    actions: DrawerActions,
    spec: TransformationSpec,
) {
    item(key = "search") {
        Button(
            onClick = actions.onOpenSearch,
            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
            transformation = SurfaceTransformation(spec),
        ) { Text(stringResource(R.string.drawer_open_search)) }
    }
    item(key = "status") { StatusCard(spec) }
    item(key = "watchface") {
        Button(
            onClick = actions.onBackToWatchFace,
            colors = ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
            transformation = SurfaceTransformation(spec),
            icon = { Icon(painterResource(UiR.drawable.ic_atrium_watch), contentDescription = null) },
            label = { Text(stringResource(R.string.drawer_back_to_watchface)) },
        )
    }
    if (state.refreshFailed) {
        item(key = "retry") {
            Button(
                onClick = actions.onRetry,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
            ) { Text(stringResource(R.string.catalog_retry)) }
        }
    }
    // Only when nothing is on screen yet: later batches append silently below the first screen.
    if (state.sections.isEmpty()) {
        if (state.loading) {
            items(PLACEHOLDER_ROWS, key = { "placeholder-$it" }) { PlaceholderRow(spec) }
        } else {
            item(key = "empty") {
                EmptyState(
                    stringResource(R.string.drawer_empty_hint), spec,
                    actionLabel = stringResource(R.string.drawer_manage_hidden),
                    onAction = actions.onOpenHidden,
                    actionIcon = UiR.drawable.ic_atrium_restore,
                )
            }
        }
    }
}

private const val PLACEHOLDER_ROWS = 3

/** Shimmering stand-in for an app row while the first catalog batch loads. */
@Composable
private fun TransformingLazyColumnItemScope.PlaceholderRow(spec: TransformationSpec) {
    val placeholder = rememberPlaceholderState(isVisible = true)
    Button(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).placeholderShimmer(placeholder)
            .clearAndSetSemantics { },
        transformation = SurfaceTransformation(spec),
        icon = { Box(Modifier.size(32.dp).placeholder(placeholder, CircleShape)) },
        label = { Text("", modifier = Modifier.fillMaxWidth().placeholder(placeholder)) },
    )
}
