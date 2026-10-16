package com.alliehe.core.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion policy derived from UserPrefs + system state.
 *
 * - [reduceMotion]: system animator duration scale is 0 or the user enabled Reduce motion.
 *   Decorative motion (pop-in, fisheye, press scale) is skipped; state changes snap or fade.
 * - [lite]: PowerSaver tier is active. Fisheye and pop-in are disabled, fades are kept.
 *
 * Springs/durations always come from `MaterialTheme.motionScheme` (Wear M3 Expressive).
 */
@Immutable
data class AtriumMotion(val reduceMotion: Boolean = false, val lite: Boolean = false) {
    /** Fisheye / pop-in / press-scale allowed. */
    val decorative: Boolean get() = !reduceMotion && !lite
    /** Opacity cross-fades allowed (kept in lite mode, dropped when motion is reduced). */
    val fades: Boolean get() = !reduceMotion
}

val LocalAtriumMotion = staticCompositionLocalOf { AtriumMotion() }

/** Observes `Settings.Global.ANIMATOR_DURATION_SCALE`; true when the user turned animations off. */
@Composable
fun rememberSystemAnimationsDisabled(context: Context): Boolean {
    var disabled by remember { mutableStateOf(readAnimatorScale(context) == 0f) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                disabled = readAnimatorScale(context) == 0f
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return disabled
}

private fun readAnimatorScale(context: Context): Float =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
