package com.alliehe.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.theme.LocalAtriumMotion
import kotlin.math.sqrt

/**
 * Schematic preview of a drawer layout. Static unless [animated] (only the selected option animates),
 * and never animates when motion is reduced or the PowerSaver tier is active.
 * Drawn locally: :feature:settings must not depend on :feature:drawer.
 */
@Composable
fun LayoutThumbnail(mode: DrawerLayoutMode, animated: Boolean, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = MaterialTheme.colorScheme
    val motion = LocalAtriumMotion.current
    val phase = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    // Pause when not RESUMED (ambient / screen off / covered) so the loop never burns frames unseen.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val run = animated && motion.decorative && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(run, spec) {
        if (!run) { phase.snapTo(0f); return@LaunchedEffect }
        while (true) {
            phase.animateTo(1f, spec)
            phase.animateTo(0f, spec)
        }
    }
    Canvas(modifier.size(size)) {
        val w = size.toPx()
        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, w)) }
        drawCircle(colors.surfaceContainerHigh, w / 2f)
        clipPath(circle) {
            val p = phase.value
            when (mode) {
                DrawerLayoutMode.List -> {
                    val rowH = w * 0.16f
                    for (i in -1..5) {
                        val y = w * 0.12f + i * (rowH + w * 0.05f) - p * (rowH + w * 0.05f)
                        drawRoundRect(colors.primaryContainer, Offset(w * 0.15f, y), Size(w * 0.7f, rowH),
                            CornerRadius(rowH / 2f))
                        drawCircle(colors.primary, rowH * 0.32f, Offset(w * 0.15f + rowH / 2f, y + rowH / 2f))
                    }
                }
                DrawerLayoutMode.Grid -> {
                    val cell = w * 0.24f
                    for (row in -1..4) {
                        val y = w * 0.14f + row * (cell + w * 0.06f) - p * (cell + w * 0.06f)
                        for (col in 0..1) {
                            val x = w / 2f + (col - 0.5f) * (cell + w * 0.08f)
                            drawCircle(colors.primary, cell / 2f, Offset(x, y + cell / 2f))
                        }
                    }
                }
                DrawerLayoutMode.Honeycomb -> {
                    val s = w * 0.22f * (1f + 0.15f * p)
                    val c = Offset(w / 2f, w / 2f)
                    for (r in -3..3) for (q in -3..3) {
                        val pt = c + Offset(s * (q + r / 2f), s * r * sqrt(3f) / 2f)
                        val d = (pt - c).getDistance() / (w / 2f)
                        val scale = (1f - 0.6f * d * d).coerceAtLeast(0.2f)
                        drawCircle(colors.primary, s * 0.42f * scale, pt, alpha = (1.2f - d).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun layoutLabel(mode: DrawerLayoutMode): String = stringResource(
    when (mode) {
        DrawerLayoutMode.List -> R.string.layout_list
        DrawerLayoutMode.Grid -> R.string.layout_grid
        DrawerLayoutMode.Honeycomb -> R.string.layout_honeycomb
    },
)

/** Selectable layout option row used by Settings and the OOBE picker. */
@Composable
internal fun LayoutOption(
    mode: DrawerLayoutMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Button(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().semantics {
            this.selected = selected
            role = Role.RadioButton
        },
        colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        icon = { LayoutThumbnail(mode, animated = selected) },
        label = { Text(layoutLabel(mode)) },
        secondaryLabel = if (selected) ({ Text(stringResource(R.string.layout_selected)) }) else null,
    )
}
