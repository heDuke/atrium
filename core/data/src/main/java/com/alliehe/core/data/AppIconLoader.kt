package com.alliehe.core.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.componentKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lazy, per-item icon loading. Icons are decoded at the requested pixel size on a background
 * dispatcher and kept in a watch-sized LRU (byte-bounded); nothing is loaded up front.
 */
@Singleton
class AppIconLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val lock = Mutex()
    private val inFlight = HashMap<String, CompletableDeferred<Bitmap?>>()

    /** Synchronous cache hit only; safe on the main thread. */
    fun peek(entry: AppEntry, sizePx: Int): Bitmap? = cache.get(key(entry, sizePx))

    /** Loads (or joins an in-flight load of) [entry]'s icon at [sizePx]; null if unavailable. */
    suspend fun load(entry: AppEntry, sizePx: Int): Bitmap? {
        val key = key(entry, sizePx)
        cache.get(key)?.let { return it }
        var owner = false
        val deferred = lock.withLock {
            inFlight[key] ?: CompletableDeferred<Bitmap?>().also { inFlight[key] = it; owner = true }
        }
        if (!owner) return deferred.await()
        val bitmap = try {
            withContext(Dispatchers.IO) { decode(entry, sizePx) }
        } finally {
            lock.withLock { inFlight.remove(key) }
        }
        if (bitmap != null) cache.put(key, bitmap)
        deferred.complete(bitmap)
        return bitmap
    }

    /** Package changes can replace icons. */
    fun evictAll() = cache.evictAll()

    private fun decode(entry: AppEntry, sizePx: Int): Bitmap? = try {
        val px = sizePx.coerceAtLeast(1)
        context.packageManager.getActivityIcon(ComponentName(entry.packageName, entry.activityName))
            .toBitmap(px, px)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: android.content.res.Resources.NotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun key(entry: AppEntry, sizePx: Int) = "${entry.componentKey}@$sizePx"

    private companion object {
        /** 1/32 of the heap, capped at 4 MB (≈ 160 icons at 40dp/xhdpi). */
        fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 32).coerceAtMost(4L * 1024 * 1024).toInt()
    }
}
