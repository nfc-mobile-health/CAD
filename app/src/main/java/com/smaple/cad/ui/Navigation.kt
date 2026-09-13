/**
 * Defines the Compose navigation graph, routing between screens based on authentication and session states.
 */
package com.smaple.cad.ui

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun Navigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val mainViewModel: MainViewModel = hiltViewModel()

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) "home" else "login"
    ) {
        composable("login") { LoginScreen(authViewModel, onLoginSuccess = { navController.navigate("home") { popUpTo(0) } }) }
        composable("home") { HomeScreen(authViewModel, mainViewModel, navController) }
        composable("scan") { ScanScreen(mainViewModel, navController) }
        composable("vitals") { VitalsScreen(mainViewModel, navController) }
        composable("review") { ReviewScreen(mainViewModel, navController) }
        composable("session") { SessionScreen(mainViewModel, navController) }
        composable("history") { HistoryScreen(mainViewModel, navController) }
    }
}
