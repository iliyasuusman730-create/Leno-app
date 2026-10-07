package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.MainScreenContainer
import com.example.ui.screens.ProtectAccountScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.UserProfileScreen
import com.example.viewmodel.LenoViewModel

@Composable
fun LenoNavGraph(
    navController: NavHostController,
    viewModel: LenoViewModel
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val isFetchingUserData by viewModel.isFetchingUserData.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loggedOutEvent.collect {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != Screen.Auth.route) {
                navController.navigate(Screen.Auth.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(currentUser, isFetchingUserData) {
        val currentRoute = navController.currentDestination?.route
        if (currentRoute == Screen.Main.route && currentUser == null && !isFetchingUserData && !viewModel.repository.isSessionActive()) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                isLoggedInProvider = { viewModel.currentUser.value != null || viewModel.repository.isSessionActive() },
                onNavigateNext = { isLoggedIn ->
                    val destination = if (isLoggedIn && (viewModel.currentUser.value != null || viewModel.repository.isSessionActive())) {
                        Screen.Main.route
                    } else {
                        Screen.Auth.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Auth.route) {
            AuthScreen(
                viewModel = viewModel,
                onAuthSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreenContainer(
                viewModel = viewModel,
                onOpenChat = { partnerId ->
                    navController.navigate(Screen.ChatDetail.createRoute(partnerId))
                },
                onOpenProfile = { targetUserId ->
                    navController.navigate(Screen.UserProfile.createRoute(targetUserId))
                },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToProtectAccount = {
                    navController.navigate(Screen.ProtectAccount.route)
                }
            )
        }

        composable(Screen.ProtectAccount.route) {
            ProtectAccountScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(
                navArgument("partnerId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val partnerId = backStackEntry.arguments?.getString("partnerId") ?: ""
            ChatDetailScreen(
                viewModel = viewModel,
                partnerId = partnerId,
                onBack = { navController.popBackStack() },
                onOpenProfile = { targetUserId ->
                    navController.navigate(Screen.UserProfile.createRoute(targetUserId))
                }
            )
        }

        composable(
            route = Screen.UserProfile.route,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            UserProfileScreen(
                viewModel = viewModel,
                userId = userId,
                onBack = { navController.popBackStack() },
                onOpenChat = { partnerId ->
                    navController.navigate(Screen.ChatDetail.createRoute(partnerId))
                }
            )
        }

        composable(route = Screen.LiveChat.route) {
            ChatScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
