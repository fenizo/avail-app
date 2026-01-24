package com.mepapp.mobile

import android.os.Bundle
import android.os.Build
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mepapp.mobile.ui.theme.MEPAppTheme
import com.mepapp.mobile.ui.MainNavigation
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fix keyboard covering input fields in WebView
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        // Request phone permission for call detection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_PHONE_STATE,
                        android.Manifest.permission.READ_CALL_LOG,
                        android.Manifest.permission.READ_CONTACTS
                    ),
                    100
                )
            }
            
            // Request battery optimization exemption for background call detection
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val packageName = packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                    Log.d("MainActivity", "Requesting battery optimization exemption")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to request battery optimization exemption", e)
                }
            }
            
            // Request overlay permission for call popup
            if (!android.provider.Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                try {
                    startActivity(intent)
                    Log.d("MainActivity", "Requesting overlay permission")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to request overlay permission", e)
                }
            }
        }
        
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
        // Start the foreground service for continuous syncing
        lifecycleScope.launch {
            try {
                val authRepository = com.mepapp.mobile.data.AuthRepository(this@MainActivity)
                // Check if user is authenticated
                authRepository.authToken.collect { token ->
                    if (!token.isNullOrBlank()) {
                        // Start foreground service
                        com.mepapp.mobile.service.CallLogSyncService.start(this@MainActivity)
                        Log.d("MainActivity", "Foreground sync service started")
                    } else {
                        // Stop service if user logs out
                        com.mepapp.mobile.service.CallLogSyncService.stop(this@MainActivity)
                        Log.d("MainActivity", "Foreground sync service stopped")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error managing sync service", e)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure service is running every time app comes to foreground
        lifecycleScope.launch {
            try {
                val authRepository = com.mepapp.mobile.data.AuthRepository(this@MainActivity)
                val token = authRepository.authToken.first()
                if (!token.isNullOrBlank()) {
                    com.mepapp.mobile.service.CallLogSyncService.start(this@MainActivity)
                    Log.d("MainActivity", "Service check on resume - started")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking service on resume", e)
            }
        }
    }
}
