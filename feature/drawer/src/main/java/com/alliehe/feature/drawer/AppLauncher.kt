package com.alliehe.feature.drawer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.alliehe.core.model.AppEntry

/**
 * F7: start the resolved MAIN+LAUNCHER activity directly (no trampoline Activity).
 * Recents attributes the launched app's task, not Atrium.
 */
object AppLauncher {
    fun launch(context: Context, entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(entry.packageName, entry.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        context.startActivity(intent)
    }
}
