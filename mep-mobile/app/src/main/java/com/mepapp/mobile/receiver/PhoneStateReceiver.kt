package com.mepapp.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.mepapp.mobile.data.AuthRepository
import com.mepapp.mobile.database.AppDatabase
import com.mepapp.mobile.database.CallLogEntity
import com.mepapp.mobile.network.CallLogRequest
import com.mepapp.mobile.network.MepApiService
import com.mepapp.mobile.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PhoneStateReceiver : BroadcastReceiver() {
    
    private val TAG = "PhoneStateReceiver"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private var callStartTime: Long = 0
        private var incomingNumber: String? = null
        private var isCallActive = false
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        // Acquire wake lock to ensure processing completes even when screen is off
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "MEPApp::PhoneStateWakeLock"
        )
        wakeLock.acquire(10000) // 10 seconds max
        
        try {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            Log.d(TAG, "=== PHONE STATE CHANGED: $state ===")
            
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // Incoming call detected
                    incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    callStartTime = System.currentTimeMillis()
                    isCallActive = false
                    Log.d(TAG, "📞 RINGING - Incoming call from: $incomingNumber")
                    
                    // Show overlay
                    showCallOverlay(context)
                }
                
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    // Call answered (or outgoing call started)
                    if (!isCallActive) {
                        callStartTime = System.currentTimeMillis()
                        isCallActive = true
                        Log.d(TAG, "📱 OFFHOOK - Call active")
                        
                        // Show overlay for outgoing calls
                        showCallOverlay(context)
                    }
                }
                
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Call ended - save to database
                    Log.d(TAG, "📵 IDLE - Call ended")
                    
                    // Hide overlay
                    hideCallOverlay(context)
                    
                    if (isCallActive || incomingNumber != null) {
                        val callEndTime = System.currentTimeMillis()
                        val duration = (callEndTime - callStartTime) / 1000 // seconds
                        
                        Log.d(TAG, "💾 Saving call. Duration: $duration seconds")
                        
                        // Save to database and sync
                        saveCallToDatabase(context, incomingNumber, callStartTime, duration)
                        
                        // Reset state
                        incomingNumber = null
                        isCallActive = false
                        callStartTime = 0
                    } else {
                        Log.w(TAG, "⚠️ Call state idle but no active call tracked")
                    }
                }
                
                else -> {
                    Log.w(TAG, "⚠️ Unknown phone state: $state")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling phone state", e)
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
    
    private fun saveCallToDatabase(context: Context, phoneNumber: String?, timestamp: Long, duration: Long) {
        scope.launch {
            try {
                // Get auth token and staff ID
                val authRepository = AuthRepository(context)
                val token = authRepository.authToken.first()
                
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "User not logged in, skipping call log")
                    return@launch
                }
                
                NetworkModule.setAuthToken(token)
                val apiService = NetworkModule.createService<MepApiService>()
                val staffId = try {
                    apiService.getMe().id
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get staff ID", e)
                    return@launch
                }
                
                // Get call details from call log
                val callDetails = getCallDetailsFromLog(context, phoneNumber, timestamp)
                
                if (callDetails != null) {
                    val database = AppDatabase.getDatabase(context)
                    val callLogDao = database.callLogDao()
                    
                    // Create entity
                    val callEntity = CallLogEntity(
                        phoneNumber = callDetails.number,
                        callType = callDetails.type,
                        duration = callDetails.duration,
                        contactName = callDetails.contactName,
                        timestamp = callDetails.timestamp,
                        staffId = staffId,
                        isSynced = false,
                        phoneCallId = callDetails.callId
                    )
                    
                    // Check if already exists
                    val exists = callLogDao.callLogExists(callDetails.callId) > 0
                    if (!exists) {
                        callLogDao.insertCallLog(callEntity)
                        Log.d(TAG, "Call saved to database: ${callDetails.number}")
                        
                        // Try to sync immediately if online
                        syncToServer(context, callEntity)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving call to database", e)
            }
        }
    }
    
    private fun getCallDetailsFromLog(context: Context, phoneNumber: String?, timestamp: Long): CallDetails? {
        try {
            val projection = arrayOf(
                android.provider.CallLog.Calls._ID,
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.TYPE,
                android.provider.CallLog.Calls.DATE,
                android.provider.CallLog.Calls.DURATION,
                android.provider.CallLog.Calls.CACHED_NAME
            )
            
            // Query recent calls (last minute)
            val oneMinuteAgo = System.currentTimeMillis() - 60000
            val selection = "${android.provider.CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf(oneMinuteAgo.toString())
            
            val cursor = context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${android.provider.CallLog.Calls.DATE} DESC LIMIT 1"
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(android.provider.CallLog.Calls._ID)
                    val numberIndex = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                    val typeIndex = it.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                    val dateIndex = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
                    val durationIndex = it.getColumnIndex(android.provider.CallLog.Calls.DURATION)
                    val nameIndex = it.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                    
                    val callId = it.getString(idIndex)
                    val number = it.getString(numberIndex) ?: "Unknown"
                    val type = when (it.getInt(typeIndex)) {
                        android.provider.CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        android.provider.CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        android.provider.CallLog.Calls.MISSED_TYPE -> "MISSED"
                        else -> "UNKNOWN"
                    }
                    val date = it.getLong(dateIndex)
                    val duration = it.getLong(durationIndex)
                    val contactName = it.getString(nameIndex)
                    val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(date))
                    
                    return CallDetails(callId, number, type, duration, contactName, isoDate)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call log", e)
        }
        return null
    }
    
    private suspend fun syncToServer(context: Context, callEntity: CallLogEntity) {
        try {
            val authRepository = AuthRepository(context)
            val token = authRepository.authToken.first()
            
            if (!token.isNullOrBlank()) {
                NetworkModule.setAuthToken(token)
                val apiService = NetworkModule.createService<MepApiService>()
                
                val callRequest = CallLogRequest(
                    phoneNumber = callEntity.phoneNumber,
                    callType = callEntity.callType,
                    duration = callEntity.duration,
                    contactName = callEntity.contactName,
                    timestamp = callEntity.timestamp,
                    staffId = callEntity.staffId
                )
                
                apiService.logCalls(listOf(callRequest))
                
                // Mark as synced
                val database = AppDatabase.getDatabase(context)
                database.callLogDao().markAsSynced(listOf(callEntity.id))
                
                Log.d(TAG, "Call synced to server: ${callEntity.phoneNumber}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to server (will retry later)", e)
        }
    }
    
    data class CallDetails(
        val callId: String,
        val number: String,
        val type: String,
        val duration: Long,
        val contactName: String?,
        val timestamp: String
    )
    
    private fun showCallOverlay(context: Context) {
        try {
            val appContext = context.applicationContext
            Log.d(TAG, "Attempting to show call overlay...")
            // Check if overlay permission is granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val canDraw = android.provider.Settings.canDrawOverlays(appContext)
                Log.d(TAG, "Overlay permission granted: $canDraw")
                if (canDraw) {
                    com.mepapp.mobile.service.CallOverlayService.show(appContext)
                } else {
                    Log.w(TAG, "Overlay permission not granted - Popup will NOT show")
                }
            } else {
                com.mepapp.mobile.service.CallOverlayService.show(appContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
        }
    }
    
    private fun hideCallOverlay(context: Context) {
        try {
            com.mepapp.mobile.service.CallOverlayService.hide(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay", e)
        }
    }
}
