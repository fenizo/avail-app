package com.mepapp.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mepapp.mobile.database.AppDatabase
import com.mepapp.mobile.database.CallLogEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }

    var callLogs by remember { mutableStateOf<List<CallLogEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalCount by remember { mutableStateOf(0) }
    var syncedCount by remember { mutableStateOf(0) }
    var unsyncedCount by remember { mutableStateOf(0) }

    // Load call logs from local database
    fun loadCallLogs() {
        scope.launch {
            isLoading = true
            try {
                callLogs = database.callLogDao().getAllCallLogs()
                totalCount = callLogs.size
                syncedCount = callLogs.count { it.isSynced }
                unsyncedCount = callLogs.count { !it.isSynced }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isLoading = false
        }
    }

    // Load on first composition
    LaunchedEffect(Unit) {
        loadCallLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Call Logs",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadCallLogs() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
        ) {
            // Stats Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = "Total",
                        value = totalCount.toString(),
                        color = Color(0xFF1E293B)
                    )
                    StatItem(
                        label = "Synced",
                        value = syncedCount.toString(),
                        color = Color(0xFF10B981)
                    )
                    StatItem(
                        label = "Pending",
                        value = unsyncedCount.toString(),
                        color = Color(0xFFEF4444)
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                }
            } else if (callLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No call logs yet",
                            fontSize = 18.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Call logs will appear here once recorded",
                            fontSize = 14.sp,
                            color = Color.LightGray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(callLogs) { callLog ->
                        CallLogItem(callLog)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun CallLogItem(callLog: CallLogEntity) {
    val callTypeColor = when (callLog.callType) {
        "INCOMING" -> Color(0xFF10B981) // Green
        "OUTGOING" -> Color(0xFF3B82F6) // Blue
        "MISSED" -> Color(0xFFEF4444) // Red
        else -> Color.Gray
    }

    val callTypeIcon = when (callLog.callType) {
        "INCOMING" -> "↓"
        "OUTGOING" -> "↑"
        "MISSED" -> "✕"
        else -> "?"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Call type indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(callTypeColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = callTypeIcon,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = callTypeColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Call details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = callLog.contactName ?: callLog.phoneNumber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (callLog.contactName != null) {
                    Text(
                        text = callLog.phoneNumber,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTimestamp(callLog.timestamp),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = " • ",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = formatDuration(callLog.duration),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            // Sync status
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (callLog.isSynced) Color(0xFF10B981) else Color(0xFFEF4444),
                        CircleShape
                    )
            )
        }
    }
}

private fun formatTimestamp(isoTimestamp: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = inputFormat.parse(isoTimestamp) ?: return isoTimestamp

        val now = Calendar.getInstance()
        val callDate = Calendar.getInstance().apply { time = date }

        val outputFormat = if (now.get(Calendar.DAY_OF_YEAR) == callDate.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == callDate.get(Calendar.YEAR)) {
            SimpleDateFormat("hh:mm a", Locale.US) // Today: just time
        } else if (now.get(Calendar.YEAR) == callDate.get(Calendar.YEAR)) {
            SimpleDateFormat("MMM dd, hh:mm a", Locale.US) // This year: date + time
        } else {
            SimpleDateFormat("MMM dd yyyy, hh:mm a", Locale.US) // Other year: full date
        }

        outputFormat.format(date)
    } catch (e: Exception) {
        isoTimestamp
    }
}

private fun formatDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
