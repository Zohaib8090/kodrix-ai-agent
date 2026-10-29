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
    const val CHAT = "chat"
    const val AGENT_SETTINGS = "agent_settings"
    const val MEMORY = "memory"

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
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.CHAT)
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

        composable(Routes.CHAT) {
            val vm: com.example.ui.viewmodel.ChatViewModel = viewModel()
            com.example.ui.screens.ChatScreen(
                viewModel = vm,
                onNavigateToBuild = { navController.navigate(Routes.DASHBOARD) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToAgentSettings = { navController.navigate(Routes.AGENT_SETTINGS) },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY) },
            )
        }

        composable(Routes.MEMORY) {
            com.example.ui.screens.MemoryScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.AGENT_SETTINGS) {
            com.example.ui.screens.AgentSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel()
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
