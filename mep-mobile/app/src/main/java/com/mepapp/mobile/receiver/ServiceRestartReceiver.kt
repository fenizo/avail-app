package com.mepapp.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mepapp.mobile.service.CallLogSyncService

class ServiceRestartReceiver : BroadcastReceiver() {
    
    private val TAG = "ServiceRestartReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Restarting CallLogSyncService...")
        
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
