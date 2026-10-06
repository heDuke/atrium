package com.alliehe.feature.drawer

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.alliehe.core.model.AppEntry

enum class LaunchResult { Started, Unavailable, NotAllowed }

object AppLauncher {
    fun launch(context: Context, entry: AppEntry): LaunchResult {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(entry.packageName, entry.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        return try {
            context.startActivity(intent)
            LaunchResult.Started
        } catch (_: ActivityNotFoundException) {
            LaunchResult.Unavailable
        } catch (_: SecurityException) {
            LaunchResult.NotAllowed
        }
    }
}
