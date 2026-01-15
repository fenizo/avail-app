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
fun JobListScreen(userId: String?, onJobClick: (String) -> Unit) {
    var jobs by remember { mutableStateOf<List<JobResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    // Explicitly typed to ensure compiler finds it
    val apiService = remember { NetworkModule.createService<MepApiService>() }

    LaunchedEffect(userId) {
        if (userId != null) {
            try {
                jobs = apiService.getJobs(userId)
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Failed to load jobs: ${e.message}"
                isLoading = false
            }
        } else {
            // Wait for userId or show error if null persists
            // isLoading remains true until userId available
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Assigned Jobs", fontWeight = FontWeight.Bold) })
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
