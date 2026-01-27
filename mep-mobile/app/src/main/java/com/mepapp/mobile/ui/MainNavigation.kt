package com.mepapp.mobile.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.unit.dp

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
        if (tokenState.value != null) {
            com.mepapp.mobile.network.NetworkModule.setAuthToken(tokenState.value!!)
            if (currentScreen == "login") {
                currentScreen = "list"
            }
        }
    }

    val workManager = androidx.work.WorkManager.getInstance(context)
    val workInfos = workManager.getWorkInfosForUniqueWorkLiveData("CallLogSync")
        .observeAsState(initial = emptyList())
        
    val isSyncing = workInfos.value.any { it.state == androidx.work.WorkInfo.State.RUNNING }

    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        when (currentScreen) {
            "login" -> LoginScreen(authRepository, onLoginSuccess = {
                currentScreen = "list"
            })
            "list" -> JobListScreen(
                userId = userIdState.value,
                token = tokenState.value,
                onJobClick = { id ->
                    selectedJobId = id
                    currentScreen = "details"
                },
                onLogsClick = {
                    currentScreen = "logs"
                }
            )
            "details" -> JobDetailScreen(jobId = selectedJobId, onBack = {
                currentScreen = "list"
            })
            "logs" -> CallLogsScreen(onBack = {
                currentScreen = "list"
            })
        }

        if (isSyncing) {
            Surface(
                color = androidx.compose.ui.graphics.Color(0xFF38BDF8), // Light Blue
                modifier = androidx.compose.ui.Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                androidx.compose.material3.Text(
                    text = "Syncing Call Logs...",
                    color = androidx.compose.ui.graphics.Color.Black,
                    modifier = androidx.compose.ui.Modifier.padding(8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
