package com.alliehe.atrium.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.alliehe.core.navigation.Routes
import com.alliehe.feature.drawer.AppActionsRoute
import com.alliehe.feature.drawer.DrawerRoute
import com.alliehe.feature.drawer.DrawerViewModel
import com.alliehe.feature.drawer.SearchRoute
import com.alliehe.feature.settings.HiddenAppsRoute
import com.alliehe.feature.settings.OnboardingRoute
import com.alliehe.feature.settings.SettingsRoute

@Composable
fun AtriumRoot(
    homeRequest: Int = 0,
    homeSession: Boolean = false,
    viewModel: AtriumRootViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val loaded = prefs ?: return
    val navController = rememberSwipeDismissableNavController()
    val startDestination = remember {
        if (loaded.onboardingCompleted) Routes.DRAWER else Routes.ONBOARDING
    }
    val backStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(homeRequest) {
        if (homeRequest > 0 && loaded.onboardingCompleted) {
            if (!navController.popBackStack(Routes.DRAWER, false)) {
                navController.navigate(Routes.DRAWER) { launchSingleTop = true }
            }
        }
    }

    AppScaffold {
        SwipeDismissableNavHost(navController = navController, startDestination = startDestination) {
            composable(Routes.ONBOARDING) {
                OnboardingRoute(onFinished = {
                    navController.navigate(Routes.DRAWER) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                })
            }
            composable(Routes.DRAWER) {
                DrawerRoute(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } },
                    onOpenActions = { componentKey ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("actions_target", componentKey)
                        navController.navigate(Routes.ACTIONS) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.ACTIONS) { entry ->
                val drawerEntry = remember(entry) { navController.getBackStackEntry(Routes.DRAWER) }
                val drawerViewModel: DrawerViewModel = hiltViewModel(drawerEntry)
                AppActionsRoute(
                    componentKey = drawerEntry.savedStateHandle.get<String>("actions_target"),
                    onDismiss = { navController.popBackStack() },
                    viewModel = drawerViewModel,
                )
            }
            composable(Routes.SEARCH) { SearchRoute() }
            composable(Routes.SETTINGS) {
                SettingsRoute(onOpenHidden = { navController.navigate(Routes.HIDDEN) { launchSingleTop = true } })
            }
            composable(Routes.HIDDEN) { HiddenAppsRoute() }
        }
    }
    // Register after the host so this handler wins only at Home's root.
    BackHandler(enabled = homeSession && backStackEntry?.destination?.route == Routes.DRAWER) { }
}
