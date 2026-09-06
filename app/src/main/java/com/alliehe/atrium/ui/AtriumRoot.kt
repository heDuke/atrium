package com.alliehe.atrium.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.alliehe.core.navigation.Routes
import com.alliehe.feature.drawer.DrawerRoute
import com.alliehe.feature.drawer.SearchRoute
import com.alliehe.feature.settings.OnboardingRoute
import com.alliehe.feature.settings.SettingsRoute

/**
 * F9: AppScaffold + SwipeDismissableNavHost + system swipe-to-dismiss.
 * G2: first-run onboarding when prefs say incomplete.
 * Theme is applied in [com.alliehe.atrium.MainActivity].
 */
@Composable
fun AtriumRoot(
    viewModel: AtriumRootViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val loaded = prefs ?: return

    val navController = rememberSwipeDismissableNavController()
    val startDestination =
        if (loaded.onboardingCompleted) Routes.DRAWER else Routes.ONBOARDING

    AppScaffold {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingRoute(
                    onFinished = {
                        navController.navigate(Routes.DRAWER) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.DRAWER) {
                DrawerRoute(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.SEARCH) {
                SearchRoute()
            }
            composable(Routes.SETTINGS) {
                SettingsRoute()
            }
        }
    }
}
