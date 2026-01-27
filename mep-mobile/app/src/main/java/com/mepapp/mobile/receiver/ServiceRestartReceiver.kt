package com.mepapp.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mepapp.mobile.data.AuthRepository
import com.mepapp.mobile.service.CallLogSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ServiceRestartReceiver : BroadcastReceiver() {

    private val TAG = "ServiceRestartReceiver"

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "ServiceRestartReceiver triggered")

        // Check if user is logged in before restarting service
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authRepository = AuthRepository(context)
                val token = authRepository.authToken.first()

                if (!token.isNullOrBlank()) {
                    Log.d(TAG, "User is logged in, restarting CallLogSyncService...")
                    startService(context)
                } else {
                    Log.d(TAG, "User not logged in, skipping service restart")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking auth state", e)
                // Try to restart anyway as a fallback
                startService(context)
            }
        }
    }

    private fun startService(context: Context) {
        try {
            val serviceIntent = Intent(context, CallLogSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Service restart initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart service", e)
        }
    }
}
