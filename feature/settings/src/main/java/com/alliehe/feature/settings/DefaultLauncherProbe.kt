package com.alliehe.feature.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

enum class HomeRoleState { Unsupported, Available, Held }

/** Role availability is a device capability, not a promise about physical button routing. */
object DefaultLauncherProbe {
    fun state(context: Context): HomeRoleState = try {
        val manager = context.getSystemService(RoleManager::class.java)
        when {
            manager == null || !manager.isRoleAvailable(RoleManager.ROLE_HOME) -> HomeRoleState.Unsupported
            manager.isRoleHeld(RoleManager.ROLE_HOME) -> HomeRoleState.Held
            else -> HomeRoleState.Available
        }
    } catch (_: RuntimeException) {
        HomeRoleState.Unsupported
    }

    fun createRequestIntent(context: Context): Intent? = try {
        if (state(context) != HomeRoleState.Available) null
        else context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_HOME)
    } catch (_: RuntimeException) {
        null
    }
}
