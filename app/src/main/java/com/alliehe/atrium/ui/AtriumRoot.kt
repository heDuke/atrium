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
import com.alliehe.feature.settings.ThemeRoute
import com.alliehe.core.ui.LocalMessageHost
import com.alliehe.core.ui.MessageHost
import com.alliehe.core.ui.MessageHostDialog
import androidx.compose.runtime.CompositionLocalProvider

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

    val messages = remember { MessageHost() }
    CompositionLocalProvider(LocalMessageHost provides messages) {
    AppScaffold {
        SwipeDismissableNavHost(navController = navController, startDestination = startDestination) {
            composable(Routes.ONBOARDING) {
                OnboardingRoute(blockExit = homeSession, onFinished = {
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
                    onOpenHidden = { navController.navigate(Routes.HIDDEN) { launchSingleTop = true } },
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
            composable(Routes.SEARCH) {
                SearchRoute(onOpenActions = { componentKey ->
                    // ActionsScreen reads its target from the drawer entry (shared DrawerViewModel).
                    runCatching { navController.getBackStackEntry(Routes.DRAWER) }.getOrNull()?.let { drawer ->
                        drawer.savedStateHandle["actions_target"] = componentKey
                        navController.navigate(Routes.ACTIONS) { launchSingleTop = true }
                    }
                })
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onOpenHidden = { navController.navigate(Routes.HIDDEN) { launchSingleTop = true } },
                    onOpenTheme = { navController.navigate(Routes.THEME) { launchSingleTop = true } },
                )
            }
            composable(Routes.HIDDEN) { HiddenAppsRoute() }
            composable(Routes.THEME) { ThemeRoute() }
        }
        // Replaces Toasts: one confirmation dialog host for every screen.
        MessageHostDialog(messages)
    }
    }
    // Register after the host so this handler wins only at Home's root.
    BackHandler(enabled = homeSession && backStackEntry?.destination?.route == Routes.DRAWER) { }
}
