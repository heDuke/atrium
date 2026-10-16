package com.alliehe.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.batchRanges
import com.alliehe.core.model.componentKey
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

/** [loading] stays true until the last batch has been emitted. Icons are loaded lazily via [AppIconLoader]. */
data class AppCatalogSnapshot(
    val apps: List<AppEntry> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

private const val FIRST_BATCH = 12
private const val BATCH = 24

/** Application-lifetime catalog. Package queries and label loading never run on the UI thread. */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val iconLoader: AppIconLoader,
) {
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

    /** Emits the first screen quickly, then the rest in batches; previous apps stay visible meanwhile. */
    private fun loadIncrementally(): Flow<AppCatalogSnapshot> = flow {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            .filter { val a = it.activityInfo; a != null && a.packageName != context.packageName && a.exported }
        val previous = _catalog.value.apps.associateBy { it.componentKey }
        val loaded = ArrayList<AppEntry>(resolved.size)
        val ranges = batchRanges(resolved.size, FIRST_BATCH, BATCH)
        if (ranges.isEmpty()) emit(AppCatalogSnapshot(emptyList(), loading = false))
        ranges.forEachIndexed { i, range ->
            for (idx in range) loaded += toEntry(pm, resolved[idx])
            val last = i == ranges.lastIndex
            val fresh = loaded.distinctBy { it.componentKey }
            // Until done, keep not-yet-reloaded apps from the previous snapshot to avoid flicker.
            val keys = fresh.mapTo(HashSet()) { it.componentKey }
            val apps = if (last) fresh else fresh + previous.values.filter { it.componentKey !in keys }
            emit(AppCatalogSnapshot(apps = apps, loading = !last && previous.isEmpty()))
            if (!last) yield()
        }
    }.flowOn(Dispatchers.IO)

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
        )
    }
}
