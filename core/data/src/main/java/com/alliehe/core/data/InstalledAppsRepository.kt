package com.alliehe.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.componentKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppCatalogSnapshot(
    val apps: List<AppEntry> = emptyList(),
    val icons: Map<String, Bitmap> = emptyMap(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/** Application-lifetime catalog. Package queries and bitmap work never run on the UI thread. */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val invalidateIcons = AtomicBoolean(false)
    private val iconCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val _catalog = MutableStateFlow(AppCatalogSnapshot())
    val catalog: StateFlow<AppCatalogSnapshot> = _catalog.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            invalidateIcons.set(true)
            refresh()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // Protected system package broadcasts; one receiver for the application lifetime.
        ContextCompat.registerReceiver(context, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            for (ignored in requests) {
                try {
                    if (invalidateIcons.getAndSet(false)) iconCache.evictAll()
                    _catalog.value = queryCatalog()
                } catch (_: SecurityException) {
                    _catalog.value = _catalog.value.copy(loading = false, failed = true)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        requests.trySend(Unit)
    }

    private fun queryCatalog(): AppCatalogSnapshot {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        val icons = mutableMapOf<String, Bitmap>()
        val iconSize = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val entries = resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (activity.packageName == context.packageName || !activity.exported) return@mapNotNull null
            val entry = AppEntry(
                packageName = activity.packageName,
                activityName = activity.name,
                label = try {
                    info.loadLabel(pm)?.toString().orEmpty().ifBlank { activity.packageName }
                } catch (_: android.content.res.Resources.NotFoundException) {
                    activity.packageName
                },
                isSystemApp = (activity.applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
            )
            val key = entry.componentKey
            try {
                val bitmap = iconCache.get(key) ?: info.loadIcon(pm).toBitmap(iconSize, iconSize)
                    .also { iconCache.put(key, it) }
                icons[key] = bitmap
            } catch (_: android.content.res.Resources.NotFoundException) {
                // A package can change while resolving it; the UI uses a text fallback.
            } catch (_: SecurityException) {
                // A missing icon must not make the launchable entry disappear.
            }
            entry
        }.distinctBy { it.componentKey }
        return AppCatalogSnapshot(apps = entries, icons = icons.toMap(), loading = false)
    }
}
