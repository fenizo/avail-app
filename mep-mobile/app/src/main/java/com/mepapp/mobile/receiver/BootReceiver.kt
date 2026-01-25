package com.mepapp.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mepapp.mobile.data.AuthRepository
import com.mepapp.mobile.service.CallLogSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    
    private val TAG = "BootReceiver"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Boot completed, checking if user is logged in...")
            
            // Check if user is authenticated before starting service
            scope.launch {
                try {
                    val authRepository = AuthRepository(context)
                    val token = authRepository.authToken.first()
                    
                    if (!token.isNullOrBlank()) {
                        Log.d(TAG, "User is logged in, starting CallLogSyncService")
                        CallLogSyncService.start(context)
                    } else {
                        Log.d(TAG, "User not logged in, service will start on next login")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking auth status", e)
                }
            }
        }
    }
}
