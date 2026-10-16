package com.alliehe.feature.drawer.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.alliehe.core.model.componentKey
import com.alliehe.core.theme.LocalAtriumMotion
import com.alliehe.feature.drawer.DrawerUiState
import com.alliehe.feature.drawer.LocalAppIconLoader
import com.alliehe.feature.drawer.R
import kotlinx.coroutines.launch

const val HONEYCOMB_SETTINGS_TAG = "honeycomb-settings"
const val HONEYCOMB_CANVAS_TAG = "honeycomb-canvas"

/** Cells fainter than this are neither tappable nor exposed to accessibility (round-screen rim). */
private const val MIN_INTERACTIVE_ALPHA = 0.25f

/** The honeycomb is not a scrolling list: report "always at the end" so the EdgeButton stays fully shown. */
private object StaticScrollInfo : ScrollInfoProvider {
    override val isScrollAwayValid: Boolean get() = false
    override val isScrollable: Boolean get() = false
    override val isScrollInProgress: Boolean get() = false
    override val anchorItemOffset: Float get() = 0f
    override val lastItemOffset: Float get() = 0f
}

/**
 * (3) Honeycomb — approved custom-drawn exception.
 * Fisheye scale/alpha by distance from centre, drag with fling + snap to the nearest icon,
 * rotary crown zooms. Settings is a permanent EdgeButton inside ScreenScaffold (round-screen safe
 * area); turning the crown past minimum zoom enlarges it as a shortcut cue.
 *
 * Pan is stored in hex units (not pixels) so zooming keeps the centred icon centred.
 */
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
        val density = LocalDensity.current
        val spacingPx = with(density) { 58.dp.toPx() }
        val labelStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp)
        val units = remember(apps.size) { HexGeometry.spiral(apps.size) }
        val loader = LocalAppIconLoader.current

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
        val latestApps by rememberUpdatedState(apps)
        val settingsLabel = stringResource(R.string.drawer_open_settings)
        val searchLabel = stringResource(R.string.drawer_open_search)
        val honeycombLabel = stringResource(R.string.drawer_honeycomb_a11y)

        fun spacing() = spacingPx * zoom
        fun center() = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        fun cellCenter(i: Int): Offset = center() + (pan.value + Offset(units[i].x, units[i].y)) * spacing()
        fun distance(i: Int) = (cellCenter(i) - center()).getDistance()
        fun cellAlpha(i: Int) = if (motion.fades) HexGeometry.edgeAlpha(distance(i), canvasSize.minDimension / 2f) else 1f
        fun cellRadius(i: Int): Float {
            val f = if (motion.decorative) HexGeometry.fisheyeScale(distance(i), canvasSize.minDimension / 2f) else 1f
            return spacing() * 0.44f * f
        }
        fun hit(p: Offset): Int {
            if (units.isEmpty()) return -1
            val i = HexGeometry.nearest(units.indices.map { HexPoint(cellCenter(it).x, cellCenter(it).y) }, HexPoint(p.x, p.y))
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

        // Interactive (visible, not faded-out) cells; recomposes only when the set changes.
        val interactive by remember(units) {
            derivedStateOf {
                if (canvasSize == Size.Zero) emptyList()
                else units.indices.filter { cellAlpha(it) >= MIN_INTERACTIVE_ALPHA && distance(it) <= canvasSize.minDimension / 2f }
            }
        }

        // #3 icons keyed by component id (stable across catalog batches); decodes are launched
        // independently, so a fling that changes the visible set never cancels in-flight work.
        val images = remember { mutableStateMapOf<String, ImageBitmap>() }
        val inFlight = remember { HashSet<String>() }
        LaunchedEffect(units, loader) {
            if (loader == null) return@LaunchedEffect
            snapshotFlow {
                val r = canvasSize.minDimension / 2f + spacing() * 1.5f
                units.indices.filter { distance(it) <= r }
            }.collect { near ->
                near.forEach { i ->
                    val entry = latestApps.getOrNull(i)?.entry ?: return@forEach
                    val key = entry.componentKey
                    if (key !in images && inFlight.add(key)) {
                        scope.launch {
                            try {
                                loader.load(entry)?.let { images[key] = it.asImageBitmap() }
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
                            detectTapGestures(
                                onPress = { p ->
                                    pressed = hit(p)
                                    tryAwaitRelease()
                                    pressed = -1
                                },
                                onTap = { p ->
                                    val i = hit(p)
                                    if (i >= 0) latestApps.getOrNull(i)?.let { actions.onLaunch(it, boundsOf(i)) }
                                },
                                onLongPress = { p ->
                                    val i = hit(p)
                                    if (i >= 0) latestApps.getOrNull(i)?.let {
                                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        actions.onActions(it)
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
                                    val s = spacing()
                                    scope.launch { pan.snapTo(pan.value + amount / s) }
                                },
                                onDragEnd = {
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
                                },
                            )
                        },
                ) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f
                    val s = spacing()
                    units.forEachIndexed { i, u ->
                        val app = apps.getOrNull(i) ?: return@forEachIndexed
                        val p = c + (pan.value + Offset(u.x, u.y)) * s
                        val d = (p - c).getDistance()
                        if (d > radius + s) return@forEachIndexed
                        val f = if (motion.decorative) HexGeometry.fisheyeScale(d, radius) else 1f
                        val alpha = if (motion.fades) HexGeometry.edgeAlpha(d, radius) else 1f
                        if (alpha <= 0f) return@forEachIndexed
                        val r = s * 0.44f * f * if (i == pressed && motion.decorative) 1.08f else 1f
                        drawCircle(colors.surfaceContainer, r, p, alpha)
                        val image = images[app.entry.componentKey]
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
                        if (i == pressed) drawCircle(colors.primary, r, p, alpha, style = Stroke(2.dp.toPx()))
                    }
                }
                // #5 one accessibility node per interactive cell (no pointer input: touches reach the canvas).
                val cellDp = with(density) { (spacing() * 0.88f).toDp() }
                interactive.forEach { i ->
                    val app = apps.getOrNull(i) ?: return@forEach
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
                                customActions = listOf(CustomAccessibilityAction(actionsLabel) { actions.onActions(app); true })
                            },
                    )
                }
                if (apps.isEmpty()) {
                    Text(
                        stringResource(if (state.loading) R.string.catalog_loading else R.string.drawer_empty_hint),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = colors.onSurfaceVariant,
                    )
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
