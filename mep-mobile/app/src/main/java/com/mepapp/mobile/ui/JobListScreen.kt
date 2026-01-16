package com.mepapp.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mepapp.mobile.network.JobResponse
import com.mepapp.mobile.network.MepApiService
import com.mepapp.mobile.network.NetworkModule
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(userId: String?, token: String?, onJobClick: (String) -> Unit) {
    var jobs by remember { mutableStateOf<List<JobResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    // Explicitly typed to ensure compiler finds it
    val apiService = remember { NetworkModule.createService<MepApiService>() }

    LaunchedEffect(userId, token) {
        if (userId != null && token != null) {
            try {
                // Ensure token is set before call (fixes restart race condition)
                NetworkModule.setAuthToken(token)
                jobs = apiService.getJobs(userId)
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Failed to load jobs: ${e.message}"
                isLoading = false
            }
        } else {
            // Wait for userId and token
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Sync Logic
    fun syncLogs() {
        scope.launch {
            try {
                snackbarHostState.showSnackbar("Syncing logs...")
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                val cursor = context.contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    null,
                    "${android.provider.CallLog.Calls.DATE} > ?",
                    arrayOf(thirtyDaysAgo.toString()),
                    "${android.provider.CallLog.Calls.DATE} DESC"
                )
                
                val logs = mutableListOf<com.mepapp.mobile.network.CallLogRequest>()
                cursor?.use {
                    val numberIdx = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                    val typeIdx = it.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                    val dateIdx = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
                    val durIdx = it.getColumnIndex(android.provider.CallLog.Calls.DURATION)
                    
                    while (it.moveToNext()) {
                        val number = it.getString(numberIdx)
                        val type = when (it.getInt(typeIdx)) {
                            android.provider.CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                            android.provider.CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                            android.provider.CallLog.Calls.MISSED_TYPE -> "MISSED"
                            else -> "UNKNOWN"
                        }
                        val date = it.getLong(dateIdx)
                        val duration = it.getLong(durIdx)
                        val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date(date))
                        
                        logs.add(com.mepapp.mobile.network.CallLogRequest(
                            staffId = userId!!,
                            phoneNumber = number,
                            duration = duration,
                            callType = type,
                            timestamp = isoDate
                        ))
                    }
                }
                
                if (logs.isNotEmpty()) {
                    apiService.logCalls(logs)
                    snackbarHostState.showSnackbar("Success: Uploaded ${logs.size} logs")
                } else {
                    snackbarHostState.showSnackbar("No recent calls found locally.")
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error: ${e.message}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("My Assigned Jobs", fontWeight = FontWeight.Bold) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { syncLogs() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("Sync Logs")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Text(errorMessage!!, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(jobs) { job ->
                        Card(
                            onClick = { onJobClick(job.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(job.customerName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    StatusBadge(job.status)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(job.serviceType, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = if (status == "In Progress") Color(0xFF38BDF8) else Color(0xFFF59E0B)
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
