package com.alliehe.core.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** Leaving Atrium for the watch face, and opening system settings pages ("quick settings" shortcuts). */
object SystemNavigation {
    /**
     * Shows the watch face. As a normal app, sending our task to the back reveals it. When Atrium holds
     * the HOME role the face host is another HOME activity (the system's), so start that explicitly.
     */
    fun backToWatchFace(activity: Activity): Boolean {
        if (DefaultLauncherProbe.state(activity) == HomeRoleState.Held) {
            systemHome(activity)?.let { component ->
                try {
                    activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                } catch (_: ActivityNotFoundException) {
                } catch (_: SecurityException) {
                }
            }
        }
        return activity.moveTaskToBack(true)
    }

    private fun systemHome(context: Context): ComponentName? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            .mapNotNull { it.activityInfo }
            .firstOrNull { it.packageName != context.packageName && it.exported }
            ?.let { ComponentName(it.packageName, it.name) }
    }

    enum class SettingsPage(val action: String) {
        WiFi(Settings.ACTION_WIFI_SETTINGS),
        Bluetooth(Settings.ACTION_BLUETOOTH_SETTINGS),
        Display(Settings.ACTION_DISPLAY_SETTINGS),
        Sound(Settings.ACTION_SOUND_SETTINGS),
        Battery(Intent.ACTION_POWER_USAGE_SUMMARY),
        All(Settings.ACTION_SETTINGS),
    }

    /** Pages this device can actually open (Wear builds omit some). */
    fun availablePages(context: Context): List<SettingsPage> = SettingsPage.entries.filter {
        context.packageManager.resolveActivity(Intent(it.action), PackageManager.ResolveInfoFlags.of(0L)) != null
    }

    fun open(context: Context, page: SettingsPage): Boolean = try {
        context.startActivity(Intent(page.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
