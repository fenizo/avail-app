package com.mepapp.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.work.*
import com.mepapp.mobile.ui.theme.MEPAppTheme
import com.mepapp.mobile.ui.MainNavigation
import com.mepapp.mobile.worker.CallLogWorker
import java.util.concurrent.TimeUnit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.mepapp.mobile.network.NetworkModule
import com.mepapp.mobile.network.MepApiService
import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupCallLogSync()

        setContent {
            MEPAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    private fun setupCallLogSync() {
        val prefs = getSharedPreferences("SyncSettings", MODE_PRIVATE)
        val currentInterval = prefs.getLong("sync_interval", 15L)

        // Fetch remote interval
        lifecycleScope.launch {
            try {
                val apiService = NetworkModule.createService<MepApiService>()
                val remoteInterval = apiService.getSyncInterval().value.toLongOrNull() ?: 15L
                
                if (remoteInterval != currentInterval) {
                    Log.d("SyncDebug", "Sync interval changed from $currentInterval to $remoteInterval. Updating schedule.")
                    schedulePeriodicWork(remoteInterval, replace = true)
                    prefs.edit().putLong("sync_interval", remoteInterval).apply()
                } else {
                    schedulePeriodicWork(currentInterval, replace = false)
                }
            } catch (e: Exception) {
                Log.e("SyncDebug", "Failed to fetch remote interval, using local default", e)
                schedulePeriodicWork(currentInterval, replace = false)
            }
        }

        // Always trigger an immediate sync on app open
        val oneTimeRequest = OneTimeWorkRequest.Builder(CallLogWorker::class.java)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueue(oneTimeRequest)
    }

    private fun schedulePeriodicWork(intervalMinutes: Long, replace: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<CallLogWorker>(
            if (intervalMinutes < 15) 15 else intervalMinutes, 
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CallLogSync",
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
