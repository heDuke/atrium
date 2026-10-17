package com.alliehe.core.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.dynamicColorScheme
import com.alliehe.core.color.BrandColors
import com.alliehe.core.color.KotlinSchemeGenerator
import com.alliehe.core.color.PaletteOrigin
import com.alliehe.core.color.SchemeGenerator
import com.alliehe.core.color.ThemeResolution
import com.alliehe.core.color.ThemeResolver
import com.alliehe.core.color.ThemeSource
import com.alliehe.core.color.WearColorRoles
import com.alliehe.core.model.PerformanceMode

/** Which palette is on screen (Settings → Theme shows it, e.g. "system colours unavailable"). */
val LocalThemeResolution = staticCompositionLocalOf { ThemeResolution(PaletteOrigin.Brand, BrandColors.BRAND_SEED) }

/** Generator used by the theme; screens use it for previews and icon-derived tints. */
val LocalSchemeGenerator = staticCompositionLocalOf<SchemeGenerator> { KotlinSchemeGenerator() }

/**
 * Wear Material3 theme.
 *
 * Palette = [ThemeResolver] over [themeSource]:
 * - System → `dynamicColorScheme(context)` (Wear OS 5+, `dynamic_color_theme_enabled`), watched with a
 *   ContentObserver so toggling it in system settings re-themes live; if unavailable → seed chain.
 * - Seed → user preset → watch-face seed → brand.
 * - Brand → [BrandColors.BRAND_SEED].
 * Seeds go through [generator] (native C++ HCT, Kotlin fallback). The background is always pure black
 * (OLED), including on the dynamic scheme.
 *
 * Motion follows G5/G3 via [performanceMode].
 */
@Composable
fun AtriumTheme(
    performanceMode: PerformanceMode = PerformanceMode.Balanced,
    reduceMotion: Boolean = false,
    themeSource: ThemeSource = ThemeSource.System,
    userSeedArgb: Int? = null,
    watchFaceSeedArgb: Int? = null,
    generator: SchemeGenerator? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val gen = generator ?: remember { KotlinSchemeGenerator() }
    val dynamicSetting = rememberDynamicColorSetting(context)
    val configuration = LocalConfiguration.current
    // Re-read system colours only when the setting flips or the configuration (palette) changes.
    val dynamic = remember(dynamicSetting, configuration) { dynamicColorScheme(context) }
    val resolution = ThemeResolver.resolve(themeSource, dynamic != null, userSeedArgb, watchFaceSeedArgb)
    val colorScheme = remember(resolution, dynamic, gen) {
        if (resolution.origin == PaletteOrigin.Dynamic && dynamic != null) {
            dynamic.copy(background = Color.Black)
        } else {
            gen.generate(resolution.seedArgb ?: BrandColors.BRAND_SEED).toColorScheme()
        }
    }

    val motionScheme = when (performanceMode) {
        PerformanceMode.PowerSaver -> MotionScheme.standard()
        PerformanceMode.Performance,
        PerformanceMode.Balanced,
        -> MotionScheme.expressive()
    }

    val systemOff = rememberSystemAnimationsDisabled(context)
    val motion = AtriumMotion(
        reduceMotion = reduceMotion || systemOff,
        lite = performanceMode == PerformanceMode.PowerSaver,
    )

    CompositionLocalProvider(
        LocalAtriumMotion provides motion,
        LocalThemeResolution provides resolution,
        LocalSchemeGenerator provides gen,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            content = content,
        )
    }
}

/** Generated roles → Wear M3 [ColorScheme] (every role, including *Dim and surfaceContainer levels). */
fun WearColorRoles.toColorScheme(): ColorScheme = ColorScheme(
    primary = Color(primary),
    primaryDim = Color(primaryDim),
    primaryContainer = Color(primaryContainer),
    onPrimary = Color(onPrimary),
    onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(secondary),
    secondaryDim = Color(secondaryDim),
    secondaryContainer = Color(secondaryContainer),
    onSecondary = Color(onSecondary),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    tertiaryDim = Color(tertiaryDim),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiary = Color(onTertiary),
    onTertiaryContainer = Color(onTertiaryContainer),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    onSurface = Color(onSurface),
    onSurfaceVariant = Color(onSurfaceVariant),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    background = Color(background),
    onBackground = Color(onBackground),
    error = Color(error),
    errorDim = Color(errorDim),
    errorContainer = Color(errorContainer),
    onError = Color(onError),
    onErrorContainer = Color(onErrorContainer),
)

private const val DYNAMIC_COLOR_SETTING = "dynamic_color_theme_enabled"

/** Same switch Wear Compose's `dynamicColorScheme` reads; observed so the theme reacts without restart. */
fun isDynamicColorSettingOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, DYNAMIC_COLOR_SETTING, 0) == 1

@Composable
private fun rememberDynamicColorSetting(context: Context): Int {
    var version by remember { mutableIntStateOf(if (isDynamicColorSettingOn(context)) 1 else 0) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Bump on every change (also palette updates that keep the flag at 1).
                version += 2
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(DYNAMIC_COLOR_SETTING), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return version
}
