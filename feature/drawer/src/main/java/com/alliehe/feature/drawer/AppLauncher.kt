package com.alliehe.feature.drawer

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.compose.ui.geometry.Rect
import com.alliehe.core.model.AppEntry

enum class LaunchResult { Started, Unavailable, NotAllowed }

object AppLauncher {
    /**
     * Starts [entry]. When [source] and non-empty [iconBounds] (in [source] coordinates) are given,
     * the window scales up from the tapped icon via [ActivityOptions.makeScaleUpAnimation].
     */
    fun launch(context: Context, entry: AppEntry, source: View? = null, iconBounds: Rect? = null): LaunchResult {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(entry.packageName, entry.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        val options = if (source != null && iconBounds != null && !iconBounds.isEmpty) {
            ActivityOptions.makeScaleUpAnimation(
                source,
                iconBounds.left.toInt(),
                iconBounds.top.toInt(),
                iconBounds.width.toInt().coerceAtLeast(1),
                iconBounds.height.toInt().coerceAtLeast(1),
            ).toBundle()
        } else {
            null
        }
        return try {
            context.startActivity(intent, options)
            LaunchResult.Started
        } catch (_: ActivityNotFoundException) {
            // Fallback: the activity may have been renamed by an update; use the package's own entry.
            fallback(context, entry, options)
        } catch (_: SecurityException) {
            fallback(context, entry, options) ?: LaunchResult.NotAllowed
        } ?: LaunchResult.Unavailable
    }

    private fun fallback(context: Context, entry: AppEntry, options: android.os.Bundle?): LaunchResult? {
        val intent = context.packageManager.getLaunchIntentForPackage(entry.packageName) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent, options)
            LaunchResult.Started
        } catch (_: ActivityNotFoundException) {
            null
        } catch (_: SecurityException) {
            LaunchResult.NotAllowed
        }
    }
}
