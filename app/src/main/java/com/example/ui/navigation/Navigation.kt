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
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProjectWorkspaceScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.OnboardingViewModel
import com.example.ui.viewmodel.ProjectWorkspaceViewModel
import com.example.ui.viewmodel.SettingsViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val WORKSPACE = "workspace/{projectId}"
    const val TRACKER = "tracker/{buildId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun workspace(projectId: String) = "workspace/$projectId"
    fun tracker(buildId: String) = "workspace/$buildId"
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
                    navController.navigate(Routes.workspace(buildId))
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
            route = Routes.WORKSPACE,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val vm: ProjectWorkspaceViewModel = viewModel()
            ProjectWorkspaceScreen(
                projectId = projectId,
                viewModel = vm,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.TRACKER,
            arguments = listOf(navArgument("buildId") { type = NavType.StringType })
        ) { backStackEntry ->
            val buildId = backStackEntry.arguments?.getString("buildId") ?: ""
            val vm: ProjectWorkspaceViewModel = viewModel()
            ProjectWorkspaceScreen(
                projectId = buildId,
                viewModel = vm,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
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
                    navController.navigate(Routes.workspace(buildId))
                }
            )
        }
    }
}
