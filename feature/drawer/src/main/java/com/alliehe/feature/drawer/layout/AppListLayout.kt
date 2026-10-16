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
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
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
import com.alliehe.core.theme.TransformingContent
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

/** Search button (top), retry and loading/empty rows shared by the TLC-based layouts. */
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
        item(key = "status") {
            TransformingContent(SurfaceTransformation(spec), Modifier.fillMaxWidth().transformedHeight(this, spec)) {
                Text(
                    stringResource(if (state.loading) R.string.catalog_loading else R.string.drawer_empty_hint),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
