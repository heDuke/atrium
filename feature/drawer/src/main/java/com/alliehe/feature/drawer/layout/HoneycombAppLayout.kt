package com.alliehe.feature.drawer.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import com.alliehe.feature.drawer.LocalAppIconLoader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.alliehe.core.model.componentKey
import com.alliehe.core.theme.LocalAtriumMotion
import com.alliehe.feature.drawer.DrawerUiState
import com.alliehe.feature.drawer.R
import kotlinx.coroutines.launch


/**
 * (3) Honeycomb — approved custom-drawn exception.
 * Fisheye scale/alpha by distance from centre, drag with fling + snap to the nearest icon,
 * rotary crown zooms; continuing to turn past minimum zoom reveals the Settings EdgeButton.
 */
const val HONEYCOMB_SETTINGS_TAG = "honeycomb-settings"

object HoneycombAppLayout : AppListLayout {
    @Composable
    override fun Content(state: DrawerUiState, actions: DrawerActions, modifier: Modifier) {
        val apps = state.apps
        val motion = LocalAtriumMotion.current
        val colors = MaterialTheme.colorScheme
        val snapSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Offset>()
        val haptics = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val textMeasurer = rememberTextMeasurer()
        val spacingPx = with(LocalDensity.current) { 58.dp.toPx() }
        val labelStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp)
        val units = remember(apps.size) { HexGeometry.spiral(apps.size) }
        val loader = LocalAppIconLoader.current
        val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
        // Only icons within/near the viewport are decoded; others draw a letter placeholder.
        val images = remember(apps) { mutableStateMapOf<String, ImageBitmap>() }
        val crown = remember { CrownZoom() }
        var zoom by remember { mutableFloatStateOf(crown.zoom) }
        val pan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        var origin by remember { mutableStateOf(Offset.Zero) }
        var canvasSize by remember { mutableStateOf(Size.Zero) }
        var pressed by remember { mutableIntStateOf(-1) }
        var showSettings by remember { mutableStateOf(false) }
        val latestApps by rememberUpdatedState(apps)
        val settingsLabel = stringResource(R.string.drawer_open_settings)
        val searchLabel = stringResource(R.string.drawer_open_search)
        val honeycombLabel = stringResource(R.string.drawer_honeycomb_a11y)

        fun spacing() = spacingPx * zoom
        LaunchedEffect(apps, loader, iconPx) {
            if (loader == null) return@LaunchedEffect
            snapshotFlow {
                val r = canvasSize.minDimension / 2f + spacing() * 1.5f
                val p = pan.value
                val s = spacing()
                units.indices.filter { i ->
                    Offset(p.x + units[i].x * s, p.y + units[i].y * s).getDistance() <= r
                }
            }.distinctUntilChanged().collectLatest { visible ->
                visible.forEach { i ->
                    val entry = apps[i].entry
                    if (entry.componentKey !in images) {
                        loader.load(entry, iconPx)?.let { images[entry.componentKey] = it.asImageBitmap() }
                    }
                }
            }
        }
        fun cellCenter(i: Int): Offset {
            val c = Offset(canvasSize.width / 2f, canvasSize.height / 2f) + pan.value
            return c + Offset(units[i].x * spacing(), units[i].y * spacing())
        }
        fun cellRadius(i: Int): Float {
            val d = (cellCenter(i) - Offset(canvasSize.width / 2f, canvasSize.height / 2f)).getDistance()
            val f = if (motion.decorative) HexGeometry.fisheyeScale(d, canvasSize.minDimension / 2f) else 1f
            return spacing() * 0.44f * f
        }
        fun hit(p: Offset): Int {
            if (units.isEmpty()) return -1
            val i = HexGeometry.nearest(
                units.indices.map { HexPoint(cellCenter(it).x, cellCenter(it).y) }, HexPoint(p.x, p.y),
            )
            return if (i >= 0 && (cellCenter(i) - p).getDistance() <= cellRadius(i)) i else -1
        }
        fun boundsOf(i: Int): Rect {
            val r = cellRadius(i)
            return Rect(origin + cellCenter(i) - Offset(r, r), Size(2 * r, 2 * r))
        }
        /** Pan that puts the icon nearest to the projected [target] pan at the centre. */
        fun snapFor(target: Offset): Offset {
            if (units.isEmpty()) return Offset.Zero
            val s = spacing()
            val i = HexGeometry.nearest(units, HexPoint(-target.x / s, -target.y / s))
            return Offset(-units[i].x * s, -units[i].y * s)
        }

        Box(modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { origin = it.positionInRoot(); canvasSize = Size(it.size.width.toFloat(), it.size.height.toFloat()) }
                    .semantics {
                        contentDescription = honeycombLabel
                        customActions = buildList {
                            add(CustomAccessibilityAction(settingsLabel) { actions.onOpenSettings(); true })
                            latestApps.forEach { app ->
                                add(CustomAccessibilityAction(app.entry.label) { actions.onLaunch(app, null); true })
                            }
                        }
                    }
                    .onRotaryScrollEvent { e ->
                        if (crown.onRotary(e.verticalScrollPixels)) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                        zoom = crown.zoom
                        showSettings = crown.settingsRevealed
                        true
                    }
                    // Re-acquires focus whenever this NavHost destination becomes active
                    // (a one-shot requestFocus is lost once SwipeDismissableNavHost moves focus).
                    .requestFocusOnHierarchyActive()
                    .focusable()
                    .pointerInput(units) {
                        detectTapGestures(
                            onPress = { p ->
                                pressed = hit(p)
                                tryAwaitRelease()
                                pressed = -1
                            },
                            onTap = { p ->
                                val i = hit(p)
                                if (i >= 0) actions.onLaunch(latestApps[i], boundsOf(i))
                            },
                            onLongPress = { p ->
                                val i = hit(p)
                                if (i >= 0) {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    actions.onActions(latestApps[i])
                                }
                            },
                        )
                    }
                    .pointerInput(units) {
                        val decay = splineBasedDecay<Offset>(this)
                        val tracker = VelocityTracker()
                        detectDragGestures(
                            onDragStart = {
                                tracker.resetTracking()
                                scope.launch { pan.stop() }
                            },
                            onDrag = { change, amount ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                                scope.launch { pan.snapTo(pan.value + amount) }
                            },
                            onDragEnd = {
                                val v = tracker.calculateVelocity()
                                val velocity = Offset(v.x, v.y)
                                scope.launch {
                                    val projected = decay.calculateTargetValue(Offset.VectorConverter, pan.value, velocity)
                                    val target = snapFor(projected)
                                    if (motion.fades) pan.animateTo(target, snapSpec, velocity) else pan.snapTo(target)
                                }
                            },
                        )
                    },
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                val s = spacing()
                units.forEachIndexed { i, u ->
                    val p = center + pan.value + Offset(u.x * s, u.y * s)
                    val d = (p - center).getDistance()
                    if (d > radius + s) return@forEachIndexed
                    val f = if (motion.decorative) HexGeometry.fisheyeScale(d, radius) else 1f
                    val alpha = if (motion.fades) HexGeometry.edgeAlpha(d, radius) else 1f
                    if (alpha <= 0f) return@forEachIndexed
                    val r = s * 0.44f * f * if (i == pressed && motion.decorative) 1.08f else 1f
                    drawCircle(colors.surfaceContainer, r, p, alpha)
                    val image = images[apps[i].entry.componentKey]
                    if (image != null) {
                        val side = (r * 1.6f).toInt().coerceAtLeast(1)
                        drawImage(
                            image,
                            dstOffset = IntOffset((p.x - side / 2f).toInt(), (p.y - side / 2f).toInt()),
                            dstSize = IntSize(side, side),
                            alpha = alpha,
                        )
                    } else {
                        val layout = textMeasurer.measure(apps[i].entry.label.take(1), labelStyle)
                        drawText(
                            layout,
                            topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f),
                            alpha = alpha,
                        )
                    }
                    if (i == pressed) drawCircle(colors.primary, r, p, alpha, style = Stroke(2.dp.toPx()))
                }
            }
            if (state.loading || apps.isEmpty()) {
                Text(
                    stringResource(if (state.loading) R.string.catalog_loading else R.string.drawer_empty_hint),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = colors.onSurfaceVariant,
                )
            }
            CompactButton(
                onClick = actions.onOpenSearch,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                label = { Text(searchLabel) },
            )
            // Always-visible, tappable Settings entry (the crown reveal below is only a shortcut).
            CompactButton(
                onClick = actions.onOpenSettings,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    .testTag(HONEYCOMB_SETTINGS_TAG),
                label = { Text(settingsLabel) },
            )
            AnimatedVisibility(
                visible = showSettings,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                EdgeButton(onClick = actions.onOpenSettings, buttonSize = EdgeButtonSize.Small) { Text(settingsLabel) }
            }
        }
    }
}
