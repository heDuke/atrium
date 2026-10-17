package com.alliehe.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.batchRanges
import com.alliehe.core.model.componentKey
import com.alliehe.core.model.visibleApps
import com.alliehe.core.model.withPinyin
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** [loading] = more batches pending (apps may already be non-empty). Icons are loaded lazily via [AppIconLoader]. */
data class AppCatalogSnapshot(
    val apps: List<AppEntry> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

private const val FIRST_BATCH = 12
private const val BATCH = 24
private const val STALE_MS = 5 * 60_000L

/** Application-lifetime catalog. Package queries and label loading never run on the UI thread. */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val iconLoader: AppIconLoader,
    private val userPrefsRepository: UserPrefsRepository,
) {
    @Volatile private var lastLoadedAt = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val invalidateIcons = AtomicBoolean(false)
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
                    if (invalidateIcons.getAndSet(false)) iconLoader.evictAll()
                    loadIncrementally().collect { _catalog.value = it }
                    lastLoadedAt = SystemClock.elapsedRealtime()
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

    /** For onResume: package broadcasts already trigger refresh, so only reload when stale. */
    fun refreshIfStale(maxAgeMs: Long = STALE_MS) {
        val last = lastLoadedAt
        if (last == 0L || SystemClock.elapsedRealtime() - last > maxAgeMs) refresh()
    }

    /**
     * Labels (cheap strings, no Drawables) are resolved on IO, then sorted in final display order
     * (pinned → favorites → all, alphabetical; hidden last) *before* batching, so later batches
     * only append below what is already on screen. The first 12 render first.
     */
    private fun loadIncrementally(): Flow<AppCatalogSnapshot> = flow {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            .filter { val a = it.activityInfo; a != null && a.packageName != context.packageName && a.exported }
        val prefs = userPrefsRepository.prefs.first()
        val entries = resolved.map { toEntry(pm, it) }.distinctBy { it.componentKey }
        val visible = entries.visibleApps(prefs)
        val visibleKeys = visible.mapTo(HashSet()) { it.componentKey }
        val ordered = visible + entries.filter { it.componentKey !in visibleKeys }
        // Re-loads with apps already on screen emit once, so the list never shrinks to a first batch.
        val ranges = if (_catalog.value.apps.isEmpty()) batchRanges(ordered.size, FIRST_BATCH, BATCH)
        else listOf(ordered.indices).filter { !it.isEmpty() }
        if (ranges.isEmpty()) emit(AppCatalogSnapshot(emptyList(), loading = false))
        ranges.forEachIndexed { i, range ->
            val last = i == ranges.lastIndex
            emit(AppCatalogSnapshot(apps = ordered.subList(0, range.last + 1).toList(), loading = !last))
            if (!last) yield()
        }
    }.flowOn(Dispatchers.IO)

    /** ICU Han→pinyin (android.icu, part of the platform). Transliterator instances are not thread-safe. */
    private val transliterator by lazy {
        runCatching { android.icu.text.Transliterator.getInstance(com.alliehe.core.model.PinyinKeys.TRANSLITERATOR_ID) }.getOrNull()
    }

    private fun romanize(text: String): String {
        val t = transliterator ?: return text
        return synchronized(t) { t.transliterate(text) }
    }

    private fun toEntry(pm: PackageManager, info: android.content.pm.ResolveInfo): AppEntry {
        val activity = info.activityInfo
        return AppEntry(
            packageName = activity.packageName,
            activityName = activity.name,
            label = try {
                info.loadLabel(pm)?.toString().orEmpty().ifBlank { activity.packageName }
            } catch (_: android.content.res.Resources.NotFoundException) {
                activity.packageName
            },
            isSystemApp = (activity.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
        ).withPinyin(::romanize)
    }
}
