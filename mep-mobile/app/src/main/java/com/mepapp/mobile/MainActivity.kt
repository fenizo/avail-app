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
        
        // Check for app updates
        checkForUpdates()
        
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
    
    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                val updateInfo = com.mepapp.mobile.update.AppUpdater.checkForUpdate(this@MainActivity)
                if (updateInfo != null) {
                    showUpdateDialog(updateInfo)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking for updates", e)
            }
        }
    }
    
    private fun showUpdateDialog(updateInfo: com.mepapp.mobile.update.UpdateInfo) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Update Available")
        builder.setMessage(
            "Version ${updateInfo.version} is now available!\n\n" +
            "${updateInfo.releaseNotes}\n\n" +
            "Would you like to update now?"
        )
        builder.setPositiveButton("Update Now") { dialog, _ ->
            com.mepapp.mobile.update.AppUpdater.downloadAndInstall(this, updateInfo)
            dialog.dismiss()
        }
        builder.setNegativeButton("Later") { dialog, _ ->
            dialog.dismiss()
        }
        builder.setCancelable(false)
        builder.show()
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
        // Use WorkManager for guaranteed periodic sync
        lifecycleScope.launch {
            try {
                val authRepository = com.mepapp.mobile.data.AuthRepository(this@MainActivity)
                authRepository.authToken.collect { token ->
                    if (!token.isNullOrBlank()) {
                        // Schedule WorkManager periodic sync (Android minimum is 15 minutes)
                        schedulePeriodicSync()
                        Log.d("MainActivity", "WorkManager periodic sync scheduled")
                    } else {
                        // Cancel sync if user logs out
                        cancelPeriodicSync()
                        Log.d("MainActivity", "WorkManager periodic sync cancelled")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error managing WorkManager sync", e)
            }
        }
    }
    
    private fun schedulePeriodicSync() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.mepapp.mobile.worker.CallLogWorker>(
            15, // Repeat every 15 minutes (Android minimum for periodic work)
            java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                15,
                java.util.concurrent.TimeUnit.MINUTES
            )
            .build()
        
        androidx.work.WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                "CallLogSync",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }
    
    private fun cancelPeriodicSync() {
        androidx.work.WorkManager.getInstance(applicationContext)
            .cancelUniqueWork("CallLogSync")
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure WorkManager sync is scheduled when app comes to foreground
        lifecycleScope.launch {
            try {
                val authRepository = com.mepapp.mobile.data.AuthRepository(this@MainActivity)
                val token = authRepository.authToken.first()
                if (!token.isNullOrBlank()) {
                    schedulePeriodicSync()
                    Log.d("MainActivity", "WorkManager check on resume - scheduled")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking WorkManager on resume", e)
            }
        }
    }
}
