package com.alliehe.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.dynamicColorScheme
import com.alliehe.core.model.PerformanceMode

/**
 * Wear Material3 theme only — no hand-authored palette.
 *
 * Color resolution:
 * 1. `dynamicColorScheme(context)` when the platform provides one
 * 2. otherwise the library default [ColorScheme] (MaterialTheme tokens)
 *
 * Seed/HCT expansion (WFF / user accent) is Phase 1+ via official Material APIs,
 * not a custom ColorScheme builder. Motion follows G5/G3.
 */
@Composable
fun AtriumTheme(
    performanceMode: PerformanceMode = PerformanceMode.Balanced,
    @Suppress("UNUSED_PARAMETER") seedColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = dynamicColorScheme(context) ?: ColorScheme()

    val motionScheme = when (performanceMode) {
        PerformanceMode.PowerSaver -> MotionScheme.standard()
        PerformanceMode.Performance,
        PerformanceMode.Balanced,
        -> MotionScheme.expressive()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = motionScheme,
        content = content,
    )
}
