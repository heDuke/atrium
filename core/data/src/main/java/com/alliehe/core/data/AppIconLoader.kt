package com.alliehe.core.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import com.alliehe.core.color.nativeimpl.NativeSchemeGenerator
import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.componentKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * How icons are rendered. [themed] tints adaptive icons that ship a monochrome layer: [foreground] on a
 * [container] circle (theme onPrimaryContainer / primaryContainer). Icons without one stay in colour.
 */
data class IconStyle(val themed: Boolean = false, val foreground: Int = 0, val container: Int = 0) {
    internal val cacheTag: String get() = if (themed) "t%08x%08x".format(foreground, container) else "c"

    companion object {
        val Default = IconStyle()
    }
}

/**
 * Lazy, per-item icon loading. Icons are decoded at the requested pixel size on a background
 * dispatcher and kept in a watch-sized LRU (byte-bounded); nothing is loaded up front.
 * Adaptive icons are cut with a round mask (the watch shape) instead of the OEM mask.
 */
@Singleton
class AppIconLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val generator: NativeSchemeGenerator,
) {
    private val accents = LruCache<String, Int>(256)

    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val lock = Mutex()
    private val inFlight = HashMap<String, CompletableDeferred<Bitmap?>>()

    /** Single canonical decode size so every layout/density shares one cache entry per app. */
    val canonicalSizePx: Int = (CANONICAL_ICON_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    fun peek(entry: AppEntry, style: IconStyle = IconStyle.Default): Bitmap? = peek(entry, canonicalSizePx, style)
    suspend fun load(entry: AppEntry, style: IconStyle = IconStyle.Default): Bitmap? = load(entry, canonicalSizePx, style)

    /** Synchronous cache hit only; safe on the main thread. A style change re-keys the cache. */
    fun peek(entry: AppEntry, sizePx: Int, style: IconStyle): Bitmap? = cache.get(key(entry, sizePx, style))

    /** Cached dominant colour of [entry]'s icon, if already computed (main-thread safe). */
    fun peekAccent(entry: AppEntry): Int? = accents.get(entry.componentKey)?.takeIf { it != 0 }

    /**
     * Dominant (seed) colour of [entry]'s full-colour icon via the native extractor, or null for
     * monochrome/gray icons. Used to tint honeycomb cells.
     */
    suspend fun accent(entry: AppEntry): Int? {
        accents.get(entry.componentKey)?.let { return it.takeIf { c -> c != 0 } }
        val color = withContext(Dispatchers.Default) {
            val bitmap = withContext(Dispatchers.IO) { decode(entry, ACCENT_SIZE_PX, IconStyle.Default) }
            bitmap?.let { generator.dominantColor(it) }.also { bitmap?.recycle() }
        }
        accents.put(entry.componentKey, color ?: 0)
        return color
    }

    /** Loads (or joins an in-flight load of) [entry]'s icon at [sizePx]; null if unavailable. */
    suspend fun load(entry: AppEntry, sizePx: Int, style: IconStyle): Bitmap? {
        val key = key(entry, sizePx, style)
        cache.get(key)?.let { return it }
        var owner = false
        val deferred = lock.withLock {
            inFlight[key] ?: CompletableDeferred<Bitmap?>().also { inFlight[key] = it; owner = true }
        }
        if (!owner) return deferred.await()
        var bitmap: Bitmap? = null
        try {
            bitmap = withContext(Dispatchers.IO) { decode(entry, sizePx, style) }
            if (bitmap != null) cache.put(key, bitmap)
        } finally {
            // Always release joiners, even if the owning coroutine was cancelled mid-decode.
            withContext(NonCancellable) { lock.withLock { inFlight.remove(key) } }
            deferred.complete(bitmap)
        }
        return bitmap
    }

    /** Package changes can replace icons. */
    fun evictAll() {
        cache.evictAll()
        accents.evictAll()
    }

    private fun decode(entry: AppEntry, sizePx: Int, style: IconStyle): Bitmap? = try {
        val px = sizePx.coerceAtLeast(1)
        val drawable = context.packageManager.getActivityIcon(ComponentName(entry.packageName, entry.activityName))
        if (drawable is AdaptiveIconDrawable) renderAdaptive(drawable, px, style) else drawable.toBitmap(px, px)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: android.content.res.Resources.NotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /**
     * Adaptive icon → round bitmap. Layers are 108dp with a 72dp safe zone, so each layer is drawn
     * 25 % larger than the target on every side and clipped to a circle.
     */
    private fun renderAdaptive(icon: AdaptiveIconDrawable, px: Int, style: IconStyle): Bitmap {
        val bitmap = createBitmap(px, px)
        val canvas = Canvas(bitmap)
        val circle = Path().apply { addCircle(px / 2f, px / 2f, px / 2f, Path.Direction.CW) }
        canvas.clipPath(circle)
        val inset = px / 4
        fun Drawable.drawLayer() {
            setBounds(-inset, -inset, px + inset, px + inset)
            draw(canvas)
        }
        val mono = icon.monochrome
        if (style.themed && mono != null) {
            canvas.drawPaint(Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.container })
            mono.mutate().colorFilter = PorterDuffColorFilter(style.foreground, PorterDuff.Mode.SRC_IN)
            mono.drawLayer()
        } else {
            icon.background?.drawLayer()
            icon.foreground?.drawLayer()
        }
        return bitmap
    }

    private fun key(entry: AppEntry, sizePx: Int, style: IconStyle) =
        "${entry.componentKey}@$sizePx/${style.cacheTag}"

    private companion object {
        /** Extraction samples ≤ 64×64 anyway; decode small. */
        const val ACCENT_SIZE_PX = 48

        const val CANONICAL_ICON_DP = 40

        /** 1/32 of the heap, capped at 4 MB (≈ 160 icons at 40dp/xhdpi). */
        fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 32).coerceAtMost(4L * 1024 * 1024).toInt()
    }
}
