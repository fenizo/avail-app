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
        
        // Check and request all necessary permissions with explanatory dialogs
        checkAndRequestPermissions()
        
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
    
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 1. Check basic runtime permissions
            val missingPermissions = mutableListOf<String>()
            
            if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(android.Manifest.permission.READ_PHONE_STATE)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(android.Manifest.permission.READ_CALL_LOG)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(android.Manifest.permission.READ_CONTACTS)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            
            if (missingPermissions.isNotEmpty()) {
                showPermissionDialog(
                    "Permissions Required",
                    "MEP App needs access to:\n\n" +
                    "• Call Logs - to track work calls\n" +
                    "• Contacts - to show customer names\n" +
                    "• Phone State - to detect calls\n" +
                    "• Notifications - to keep you updated\n\n" +
                    "These are essential for the app to work properly."
                ) {
                    requestPermissions(missingPermissions.toTypedArray(), 100)
                }
            } else {
                // Basic permissions granted, check special permissions
                checkSpecialPermissions()
            }
        }
    }
    
    private fun checkSpecialPermissions() {
        // 2. Check battery optimization exemption
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val packageName = packageName
        
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            showPermissionDialog(
                "Battery Optimization",
                "To ensure call logs sync reliably even when the app is in background, " +
                "MEP App needs to be exempt from battery optimization.\n\n" +
                "This will NOT drain your battery significantly."
            ) {
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
            return
        }
        
        // 3. Check overlay permission (for floating call window)
        if (!android.provider.Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                "Display Over Other Apps",
                "MEP App needs permission to display call information over other apps.\n\n" +
                "This allows you to see customer details during incoming calls."
            ) {
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
    }
    
    private fun showPermissionDialog(title: String, message: String, onOkClick: () -> Unit) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Grant Permission") { dialog, _ ->
            onOkClick()
            dialog.dismiss()
        }
        builder.setNegativeButton("Not Now") { dialog, _ ->
            dialog.dismiss()
        }
        builder.setCancelable(false)
        builder.show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val allGranted = grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                // Basic permissions granted, now check special permissions
                checkSpecialPermissions()
            } else {
                // Show why permissions are critical
                showPermissionDialog(
                    "Permissions Denied",
                    "Without these permissions, MEP App cannot track call logs. " +
                    "Please grant all permissions for the app to function."
                ) {
                    checkAndRequestPermissions()
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
