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
 * **Interim acceptance (Issue #3):** never-null chain is
 * `dynamicColorScheme(context) ?: ColorScheme()` (library default).
 * [seedColor] is accepted from `:app` / WFF for future official HCT mapping;
 * it is not applied via a custom ColorScheme builder.
 *
 * Motion follows G5/G3 via [performanceMode].
 */
@Composable
fun AtriumTheme(
    performanceMode: PerformanceMode = PerformanceMode.Balanced,
    seedColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Prefer dynamic; else library default. Seed reserved for official HCT API.
    if (seedColor != null) {
        // Intentionally unused until Wear Material exposes seed→ColorScheme officially.
    }
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
