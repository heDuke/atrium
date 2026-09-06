package com.alliehe.feature.settings

import android.content.Context
import android.content.Intent

/**
 * F11 optional: runtime probe for set-as-default (ROLE_HOME).
 *
 * Uses reflection so missing RoleManager stubs / Wear builds still compile;
 * unsupported devices simply hide the row. Request failures are swallowed —
 * never block core flows.
 */
object DefaultLauncherProbe {

    private const val ROLE_HOME = "android.app.role.HOME"
    private const val ROLE_MANAGER_CLASS = "android.app.RoleManager"

    fun isAvailable(context: Context): Boolean {
        return createRequestIntent(context) != null
    }

    /**
     * Builds the role-request intent, or null if unavailable / any failure.
     */
    fun createRequestIntent(context: Context): Intent? {
        return try {
            val roleManagerClass = Class.forName(ROLE_MANAGER_CLASS)
            val roleManager = context.getSystemService(roleManagerClass) ?: return null

            val available = roleManagerClass
                .getMethod("isRoleAvailable", String::class.java)
                .invoke(roleManager, ROLE_HOME) as? Boolean
                ?: return null
            if (!available) return null

            val held = roleManagerClass
                .getMethod("isRoleHeld", String::class.java)
                .invoke(roleManager, ROLE_HOME) as? Boolean
                ?: return null
            if (held) return null

            roleManagerClass
                .getMethod("createRequestRoleIntent", String::class.java)
                .invoke(roleManager, ROLE_HOME) as? Intent
        } catch (_: Throwable) {
            null
        }
    }
}
