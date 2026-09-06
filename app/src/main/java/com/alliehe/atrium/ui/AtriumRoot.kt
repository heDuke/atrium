package com.alliehe.atrium.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.alliehe.core.navigation.Routes
import com.alliehe.feature.drawer.DrawerRoute
import com.alliehe.feature.drawer.R as DrawerR
import com.alliehe.feature.settings.SettingsRoute

/**
 * F9: AppScaffold + SwipeDismissableNavHost + system swipe-to-dismiss.
 * Start destination is F1 drawer empty shell (Phase 0).
 */
@Composable
fun AtriumRoot() {
    val navController = rememberSwipeDismissableNavController()

    AppScaffold {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = Routes.DRAWER,
        ) {
            composable(Routes.DRAWER) {
                DrawerRoute(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.SEARCH) {
                // G1 lands in Phase 2; Phase 0 reuses drawer empty shell as placeholder.
                DrawerRoute(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenSearch = {},
                    titleRes = DrawerR.string.drawer_search_title,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsRoute()
            }
        }
    }
}
