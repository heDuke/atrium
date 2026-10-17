package com.alliehe.feature.drawer.layout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.alliehe.feature.drawer.R
import java.util.Locale

private data class Status(val date: String, val level: Int, val charging: Boolean)

/**
 * Non-clickable glance card: date + battery. The time itself is already in the scaffold's TimeText.
 * Updated by the sticky battery broadcast and the minute tick (no polling).
 */
@Composable
internal fun TransformingLazyColumnItemScope.StatusCard(spec: TransformationSpec) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readStatus(context, null)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                status = readStatus(c, intent.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED })
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val battery = if (status.level < 0) "" else stringResource(
        if (status.charging) R.string.drawer_status_charging else R.string.drawer_status_battery, status.level,
    )
    val description = listOf(status.date, battery).filter { it.isNotEmpty() }.joinToString("，")
    Card(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).clearAndSetSemantics { contentDescription = description },
        transformation = SurfaceTransformation(spec),
    ) {
        Text(status.date, style = MaterialTheme.typography.titleSmall)
        if (battery.isNotEmpty()) {
            Text(battery, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun readStatus(context: Context, batteryIntent: Intent?): Status {
    val intent = batteryIntent ?: ContextCompat.registerReceiver(
        context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val percent = if (level < 0 || scale <= 0) -1 else level * 100 / scale
    val date = android.icu.text.DateFormat.getInstanceForSkeleton("MMMEd", Locale.getDefault()).format(java.util.Date())
    return Status(date, percent, plugged)
}
