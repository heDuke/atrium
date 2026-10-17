package com.alliehe.feature.drawer.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.alliehe.core.model.componentKey
import com.alliehe.core.model.moved
import com.alliehe.core.theme.LocalAtriumMotion
import com.alliehe.core.theme.LocalSchemeGenerator
import com.alliehe.core.ui.LocalAppIconLoader
import com.alliehe.core.ui.LocalIconStyle
import com.alliehe.feature.drawer.DrawerAppItem
import com.alliehe.feature.drawer.DrawerUiState
import com.alliehe.feature.drawer.R
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val HONEYCOMB_SETTINGS_TAG = "honeycomb-settings"
const val HONEYCOMB_CANVAS_TAG = "honeycomb-canvas"

/** Cells fainter than this are neither tappable nor exposed to accessibility (round-screen rim). */
private const val MIN_INTERACTIVE_ALPHA = 0.25f

/** Auto-pan speed at the very rim while dragging an icon, in hex units per frame. */
private const val AUTOPAN_UNITS_PER_FRAME = 0.06f

/** Icon-derived cell tint: low chroma, dark tone so icons and the black background stay dominant. */
private const val CELL_TINT_CHROMA = 24f
private const val CELL_TINT_TONE = 22f

/** The honeycomb is not a scrolling list: report "always at the end" so the EdgeButton stays fully shown. */
private object StaticScrollInfo : ScrollInfoProvider {
    override val isScrollAwayValid: Boolean get() = false
    override val isScrollable: Boolean get() = false
    override val isScrollInProgress: Boolean get() = false
    override val anchorItemOffset: Float get() = 0f
    override val lastItemOffset: Float get() = 0f
}

private enum class Phase { Tap, Pan, LongPress, Cancel }

/**
 * (3) Honeycomb — approved custom-drawn exception.
 * Fisheye scale/alpha by distance from centre, one gesture handler for tap / pan (fling + snap to the
 * nearest icon, rubber-band past the outermost ring) / long-press. Long-press without moving opens the
 * actions sheet; long-press and drag picks the icon up for reordering, with edge auto-pan; the order is
 * persisted. Rotary crown zooms. Settings is a permanent EdgeButton inside ScreenScaffold.
 *
 * Pan is stored in hex units (not pixels) so zooming keeps the centred icon centred.
 */
object HoneycombAppLayout : AppListLayout {
    @Composable
    override fun Content(state: DrawerUiState, actions: DrawerActions, modifier: Modifier) {
        val apps = state.honeycombApps
        val motion = LocalAtriumMotion.current
        val colors = MaterialTheme.colorScheme
        val snapSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Offset>()
        val haptics = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val spacingPx = with(density) { 58.dp.toPx() }
        val labelStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface)
        val units = remember(apps.size) { HexGeometry.spiral(apps.size) }
        val extent = remember(units) { HexGeometry.extent(units) }
        val loader = LocalAppIconLoader.current
        val iconStyle = LocalIconStyle.current
        val generator = LocalSchemeGenerator.current

        // #11 state restore: zoom + pan survive navigation and process recreation.
        var savedZoom by rememberSaveable { mutableFloatStateOf(1f) }
        var savedPanX by rememberSaveable { mutableFloatStateOf(0f) }
        var savedPanY by rememberSaveable { mutableFloatStateOf(0f) }
        val crown = remember { CrownZoom(initial = savedZoom) }
        var zoom by remember { mutableFloatStateOf(crown.zoom) }
        val pan = remember { Animatable(Offset(savedPanX, savedPanY), Offset.VectorConverter) } // hex units
        LaunchedEffect(Unit) {
            snapshotFlow { pan.value }.collect { savedPanX = it.x; savedPanY = it.y }
        }
        var origin by remember { mutableStateOf(Offset.Zero) }
        var canvasSize by remember { mutableStateOf(Size.Zero) }
        var pressed by remember { mutableIntStateOf(-1) }
        var crownCue by remember { mutableStateOf(false) }

        // Reorder state: [dragFrom] → [dragTo] in [apps]; the dragged icon follows [dragPos] (canvas px).
        var dragFrom by remember { mutableIntStateOf(-1) }
        var dragTo by remember { mutableIntStateOf(-1) }
        var dragPos by remember { mutableStateOf(Offset.Zero) }
        // Keeps the dropped order on screen until the persisted order arrives (no snap-back flicker).
        var committed by remember { mutableStateOf<List<DrawerAppItem>?>(null) }
        LaunchedEffect(apps) { committed = null }
        val shown: List<DrawerAppItem> = when {
            dragFrom >= 0 && dragTo >= 0 -> (committed ?: apps).moved(dragFrom, dragTo)
            else -> committed ?: apps
        }
        val latestShown by rememberUpdatedState(shown)
        val latestApps by rememberUpdatedState(committed ?: apps)

        val settingsLabel = stringResource(R.string.drawer_open_settings)
        val searchLabel = stringResource(R.string.drawer_open_search)
        val honeycombLabel = stringResource(R.string.drawer_honeycomb_a11y)
        val earlierLabel = stringResource(R.string.honeycomb_move_earlier)
        val laterLabel = stringResource(R.string.honeycomb_move_later)

        fun spacing() = spacingPx * zoom
        fun center() = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        fun radius() = canvasSize.minDimension / 2f
        fun cellCenter(i: Int): Offset = center() + (pan.value + Offset(units[i].x, units[i].y)) * spacing()
        fun distance(i: Int) = (cellCenter(i) - center()).getDistance()
        fun cellAlpha(i: Int) = if (motion.fades) HexGeometry.edgeAlpha(distance(i), radius()) else 1f
        fun cellRadius(i: Int): Float {
            val f = if (motion.decorative) HexGeometry.fisheyeScale(distance(i), radius()) else 1f
            return spacing() * 0.44f * f
        }
        fun nearestSlot(p: Offset): Int =
            if (units.isEmpty()) -1
            else HexGeometry.nearest(units.indices.map { HexPoint(cellCenter(it).x, cellCenter(it).y) }, HexPoint(p.x, p.y))
        fun hit(p: Offset): Int {
            val i = nearestSlot(p)
            return if (i >= 0 && cellAlpha(i) >= MIN_INTERACTIVE_ALPHA && (cellCenter(i) - p).getDistance() <= cellRadius(i)) i else -1
        }
        fun boundsOf(i: Int): Rect {
            val r = cellRadius(i)
            return Rect(origin + cellCenter(i) - Offset(r, r), Size(2 * r, 2 * r))
        }
        fun snapFor(targetUnits: Offset): Offset {
            if (units.isEmpty()) return Offset.Zero
            val i = HexGeometry.nearest(units, HexPoint(-targetUnits.x, -targetUnits.y))
            return Offset(-units[i].x, -units[i].y)
        }
        fun HexPoint.toOffset() = Offset(x, y)
        fun Offset.toHex() = HexPoint(x, y)

        // Interactive (visible, not faded-out) cells; recomposes only when the set changes.
        val interactive by remember(units) {
            derivedStateOf {
                if (canvasSize == Size.Zero) emptyList()
                else units.indices.filter { cellAlpha(it) >= MIN_INTERACTIVE_ALPHA && distance(it) <= radius() }
            }
        }

        // #3 icons keyed by component id (stable across catalog batches and reorders); decodes are
        // launched independently, so a fling that changes the visible set never cancels in-flight work.
        // Full-colour icons also get a cell tint from their dominant colour (native extractor).
        val images = remember(iconStyle) { mutableStateMapOf<String, ImageBitmap>() }
        val tints = remember(iconStyle) { mutableStateMapOf<String, Color>() }
        val inFlight = remember(iconStyle) { HashSet<String>() }
        LaunchedEffect(units, loader, iconStyle) {
            if (loader == null) return@LaunchedEffect
            snapshotFlow {
                val r = radius() + spacing() * 1.5f
                units.indices.filter { distance(it) <= r }.mapNotNull { latestShown.getOrNull(it)?.entry }
            }.collect { near ->
                near.forEach { entry ->
                    val key = entry.componentKey
                    if (key !in images && inFlight.add(key)) {
                        scope.launch {
                            try {
                                loader.load(entry, iconStyle)?.let { images[key] = it.asImageBitmap() }
                                if (!iconStyle.themed) loader.accent(entry)?.let { accent ->
                                    tints[key] = Color(generator.retone(accent, CELL_TINT_CHROMA, CELL_TINT_TONE))
                                }
                            } finally {
                                inFlight.remove(key)
                            }
                        }
                    }
                }
            }
        }

        ScreenScaffold(
            scrollInfoProvider = StaticScrollInfo,
            modifier = modifier,
            edgeButton = {
                EdgeButton(
                    onClick = actions.onOpenSettings,
                    buttonSize = if (crownCue) EdgeButtonSize.Medium else EdgeButtonSize.Small,
                    modifier = Modifier.testTag(HONEYCOMB_SETTINGS_TAG),
                ) { Text(settingsLabel) }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .testTag(HONEYCOMB_CANVAS_TAG)
                        .onGloballyPositioned {
                            origin = it.positionInRoot()
                            canvasSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
                        }
                        .semantics { contentDescription = honeycombLabel }
                        .onRotaryScrollEvent { e ->
                            if (crown.onRotary(e.verticalScrollPixels)) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            zoom = crown.zoom
                            savedZoom = crown.zoom
                            crownCue = crown.settingsRevealed
                            true
                        }
                        .requestFocusOnHierarchyActive()
                        .focusable()
                        .pointerInput(units) {
                            val decay = splineBasedDecay<Offset>(this)

                            /** Free pan with rubber band past the outermost ring, then fling + snap. */
                            suspend fun AwaitPointerEventScope.panUntilUp(id: PointerId, tracker: VelocityTracker) {
                                var raw = pan.value
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: break
                                    if (!change.pressed) break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    raw += change.positionChange() / spacing()
                                    change.consume()
                                    val banded = HexGeometry.rubberBand(raw.toHex(), extent).toOffset()
                                    scope.launch { pan.snapTo(banded) }
                                }
                                val v = tracker.calculateVelocity()
                                val s = spacing()
                                val velocityUnits = Offset(v.x, v.y) / s
                                scope.launch {
                                    val projected = decay.calculateTargetValue(
                                        Offset.VectorConverter, pan.value * s, velocityUnits * s,
                                    ) / s
                                    val target = snapFor(projected)
                                    if (motion.fades) pan.animateTo(target, snapSpec, velocityUnits) else pan.snapTo(target)
                                }
                            }

                            /** Picked-up icon follows the finger; near the rim the field auto-pans. */
                            suspend fun AwaitPointerEventScope.reorderUntilUp(id: PointerId, start: Int, down: Offset) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragFrom = start
                                dragTo = start
                                dragPos = down
                                var moved = false
                                val autopan = scope.launch {
                                    while (isActive) {
                                        withFrameNanos { }
                                        val v = HexGeometry.edgePan((dragPos - center()).toHex(), radius())
                                        if (v.x != 0f || v.y != 0f) {
                                            val next = pan.value - v.toOffset() * AUTOPAN_UNITS_PER_FRAME
                                            pan.snapTo(HexGeometry.clamp(next.toHex(), extent).toOffset())
                                            dragTo = nearestSlot(dragPos).coerceIn(0, latestApps.lastIndex)
                                        }
                                    }
                                }
                                try {
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: break
                                        if (!change.pressed) break
                                        if ((change.position - down).getDistance() > viewConfiguration.touchSlop) moved = true
                                        dragPos = change.position
                                        if (moved) {
                                            val slot = nearestSlot(change.position).coerceIn(0, latestApps.lastIndex)
                                            if (slot != dragTo) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            dragTo = slot
                                        }
                                        change.consume()
                                    }
                                } finally {
                                    autopan.cancel()
                                }
                                val from = dragFrom
                                val to = dragTo
                                if (!moved) {
                                    latestApps.getOrNull(from)?.let(actions.onActions)
                                } else if (from >= 0 && to >= 0 && from != to) {
                                    committed = latestApps.moved(from, to)
                                    actions.onReorder(from, to)
                                }
                                dragFrom = -1
                                dragTo = -1
                                if (motion.fades) scope.launch { pan.animateTo(snapFor(pan.value), snapSpec) }
                            }

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                scope.launch { pan.stop() }
                                val start = hit(down.position)
                                pressed = start
                                val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                                val phase = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    var result = Phase.Cancel
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            result = if (change.isConsumed) Phase.Cancel else Phase.Tap
                                            break
                                        }
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                            result = Phase.Pan
                                            break
                                        }
                                    }
                                    result
                                } ?: Phase.LongPress
                                pressed = -1
                                when (phase) {
                                    Phase.Tap -> if (start >= 0) {
                                        latestShown.getOrNull(start)?.let { actions.onLaunch(it, boundsOf(start)) }
                                    }
                                    Phase.Pan -> panUntilUp(down.id, tracker)
                                    Phase.LongPress -> if (start >= 0) reorderUntilUp(down.id, start, down.position)
                                        else panUntilUp(down.id, tracker)
                                    Phase.Cancel -> Unit
                                }
                            }
                        },
                ) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f
                    val s = spacing()
                    units.forEachIndexed { i, u ->
                        val app = shown.getOrNull(i) ?: return@forEachIndexed
                        val p = c + (pan.value + Offset(u.x, u.y)) * s
                        val d = (p - c).getDistance()
                        if (d > radius + s) return@forEachIndexed
                        val f = if (motion.decorative) HexGeometry.fisheyeScale(d, radius) else 1f
                        val alpha = if (motion.fades) HexGeometry.edgeAlpha(d, radius) else 1f
                        if (alpha <= 0f) return@forEachIndexed
                        val r = s * 0.44f * f * if (i == pressed && motion.decorative) 1.08f else 1f
                        if (i == dragTo && dragFrom >= 0) {
                            // Empty drop slot; the icon itself is drawn under the finger below.
                            drawCircle(colors.primary, r, p, alpha, style = Stroke(2.dp.toPx()))
                            return@forEachIndexed
                        }
                        drawCell(app, p, r, alpha, images[app.entry.componentKey],
                            tints[app.entry.componentKey] ?: colors.surfaceContainer, textMeasurer, labelStyle)
                        if (i == pressed) drawCircle(colors.primary, r, p, alpha, style = Stroke(2.dp.toPx()))
                    }
                    if (dragFrom >= 0) {
                        val app = shown.getOrNull(dragTo)
                        if (app != null) {
                            val r = s * 0.44f * 1.15f
                            drawCell(app, dragPos, r, 1f, images[app.entry.componentKey], colors.primaryContainer,
                                textMeasurer, labelStyle)
                            drawCircle(colors.primary, r, dragPos, style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                // #5 one accessibility node per interactive cell (no pointer input: touches reach the canvas).
                // Reorder is also available here as "move earlier / later" custom actions.
                val cellDp = with(density) { (spacing() * 0.88f).toDp() }
                interactive.forEach { i ->
                    val app = shown.getOrNull(i) ?: return@forEach
                    val actionsLabel = stringResource(R.string.drawer_app_actions_for, app.entry.label)
                    Box(
                        Modifier
                            .offset {
                                val cc = cellCenter(i)
                                val half = spacing() * 0.44f
                                IntOffset((cc.x - half).toInt(), (cc.y - half).toInt())
                            }
                            .size(cellDp)
                            .semantics {
                                contentDescription = app.entry.label
                                role = Role.Button
                                onClick(app.entry.label) { actions.onLaunch(app, boundsOf(i)); true }
                                customActions = buildList {
                                    add(CustomAccessibilityAction(actionsLabel) { actions.onActions(app); true })
                                    if (i > 0) add(CustomAccessibilityAction(earlierLabel) { actions.onReorder(i, i - 1); true })
                                    if (i < shown.lastIndex) add(CustomAccessibilityAction(laterLabel) { actions.onReorder(i, i + 1); true })
                                }
                            },
                    )
                }
                if (apps.isEmpty()) {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp))
                    } else {
                        CompactButton(
                            onClick = actions.onOpenHidden,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            label = { Text(stringResource(R.string.drawer_manage_hidden)) },
                        )
                    }
                }
                CompactButton(
                    onClick = actions.onOpenSearch,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding()),
                    label = { Text(searchLabel) },
                )
            }
        }
    }
}

private fun DrawScope.drawCell(
    app: DrawerAppItem,
    p: Offset,
    r: Float,
    alpha: Float,
    image: ImageBitmap?,
    fill: Color,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    drawCircle(fill, r, p, alpha)
    if (image != null) {
        val side = (r * 1.6f).toInt().coerceAtLeast(1)
        drawImage(
            image,
            dstOffset = IntOffset((p.x - side / 2f).toInt(), (p.y - side / 2f).toInt()),
            dstSize = IntSize(side, side),
            alpha = alpha,
        )
    } else {
        val layout = textMeasurer.measure(app.entry.label.take(1), labelStyle)
        drawText(layout, topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f), alpha = alpha)
    }
}
