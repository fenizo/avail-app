package com.mepapp.mobile.ui

import androidx.compose.runtime.*

@Composable
fun MainNavigation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authRepository = remember { com.mepapp.mobile.data.AuthRepository(context) }
    val tokenState = authRepository.authToken.collectAsState(initial = null)
    val userIdState = authRepository.userId.collectAsState(initial = null)
    
    var currentScreen by remember { mutableStateOf("login") }
    var selectedJobId by remember { mutableStateOf("") }

    // Navigation logic based on authentication
    val startScreen = if (tokenState.value != null) "list" else "login"
    
    LaunchedEffect(tokenState.value) {
        if (tokenState.value != null && currentScreen == "login") {
            currentScreen = "list"
        }
    }

    when (currentScreen) {
        "login" -> LoginScreen(authRepository, onLoginSuccess = {
            currentScreen = "list"
        })
        "list" -> JobListScreen(
            userId = userIdState.value,
            onJobClick = { id ->
                selectedJobId = id
                currentScreen = "details"
            }
        )
        "details" -> JobDetailScreen(jobId = selectedJobId, onBack = {
            currentScreen = "list"
        })
    }
}
