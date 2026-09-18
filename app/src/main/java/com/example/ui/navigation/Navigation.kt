package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.BuildTrackerScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.BuildTrackerViewModel
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.OnboardingViewModel
import com.example.ui.viewmodel.SettingsViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val TRACKER = "tracker/{buildId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun tracker(buildId: String) = "tracker/$buildId"
}

@Composable
fun AppNavigation(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel()
            OnboardingScreen(
                viewModel = vm,
                onFinishOnboarding = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.DASHBOARD)
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            val vm: DashboardViewModel = viewModel()
            DashboardScreen(
                viewModel = vm,
                onNavigateToTracker = { buildId ->
                    navController.navigate(Routes.tracker(buildId))
                },
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.SETTINGS) {
            val activity = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
            val vm: SettingsViewModel = viewModel(activity)
            SettingsScreen(
                viewModel = vm,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.TRACKER,
            arguments = listOf(navArgument("buildId") { type = NavType.StringType })
        ) { backStackEntry ->
            val buildId = backStackEntry.arguments?.getString("buildId") ?: ""
            val vm: BuildTrackerViewModel = viewModel()
            BuildTrackerScreen(
                buildId = buildId,
                viewModel = vm,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.HISTORY) {
            val vm: HistoryViewModel = viewModel()
            HistoryScreen(
                viewModel = vm,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToTracker = { buildId ->
                    navController.navigate(Routes.tracker(buildId))
                }
            )
        }
    }
}
