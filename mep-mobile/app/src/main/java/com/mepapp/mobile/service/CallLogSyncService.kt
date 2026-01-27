package com.mepapp.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.CallLog
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mepapp.mobile.MainActivity
import com.mepapp.mobile.R
import com.mepapp.mobile.data.AuthRepository
import com.mepapp.mobile.database.AppDatabase
import com.mepapp.mobile.database.CallLogEntity
import com.mepapp.mobile.network.CallLogRequest
import com.mepapp.mobile.network.MepApiService
import com.mepapp.mobile.network.NetworkModule
import com.mepapp.mobile.receiver.ServiceRestartReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class CallLogSyncService : Service() {

    private val TAG = "CallLogSyncService"
    private val CHANNEL_ID = "CallLogSyncChannel"
    private val NOTIFICATION_ID = 1001

    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var apiService: MepApiService
    private lateinit var authRepository: AuthRepository
    private lateinit var database: AppDatabase
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        authRepository = AuthRepository(applicationContext)
        apiService = NetworkModule.createService<MepApiService>()
        database = AppDatabase.getDatabase(applicationContext)

        // Acquire WakeLock to prevent CPU from sleeping
        acquireWakeLock()

        createNotificationChannel()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "MEPApp::CallLogSyncWakeLock"
            )
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        // Start foreground service with notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // CRITICAL: Do an immediate sync to capture any calls that happened while app was closed
        serviceScope.launch {
            try {
                Log.d(TAG, "Running initial sync to capture missed calls...")
                saveCallLogsToDatabase()
                syncDatabaseToServer()
                Log.d(TAG, "Initial sync complete")
            } catch (e: Exception) {
                Log.e(TAG, "Initial sync failed", e)
            }
        }
        
        // Start the sync loop
        startSyncLoop()
        
        return START_STICKY // Restart service if killed by system
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed - App closed, restarting service...")
        
        // Restart service immediately
        sendBroadcast(Intent(this, ServiceRestartReceiver::class.java))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed, attempting restart...")
        serviceJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()

        // Restart service immediately
        try {
            sendBroadcast(Intent(this, ServiceRestartReceiver::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send restart broadcast", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Log Sync Service",
                NotificationManager.IMPORTANCE_HIGH // Changed from DEFAULT to HIGH for persistence
            ).apply {
                description = "Syncs call logs to server in background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MEP App")
            .setContentText("MEPSTEP Service is Live")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Changed from LOW to MAX
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // Mark as service notification
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE) // Android 12+
            .build()
    }
    
    private fun startSyncLoop() {
        serviceJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Step 1: Save new call logs from phone to local database
                    saveCallLogsToDatabase()
                    
                    // Step 2: Sync unsynced logs from database to server
                    syncDatabaseToServer()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during sync", e)
                }
                
                // Wait 5 seconds before next sync
                delay(5000)
            }
        }
    }
    
    private suspend fun saveCallLogsToDatabase() {
        try {
            // Get staff ID from local storage (works offline!)
            val staffId = authRepository.userId.first()
            if (staffId.isNullOrBlank()) {
                Log.d(TAG, "No staff ID stored locally, skipping call log capture")
                return
            }

            // Read call logs from phone - THIS WORKS OFFLINE
            val callLogDao = database.callLogDao()
            val phoneCallLogs = getCallLogsFromPhone(staffId)

            var savedCount = 0
            for (log in phoneCallLogs) {
                // Check if already exists in database
                val exists = callLogDao.callLogExists(log.phoneCallId ?: "") > 0
                if (!exists) {
                    callLogDao.insertCallLog(log)
                    savedCount++
                }
            }

            if (savedCount > 0) {
                Log.d(TAG, "Saved $savedCount new call logs to local database (OFFLINE OK)")
                updateNotification("Stored $savedCount new calls locally")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving to database", e)
        }
    }
    
    private suspend fun syncDatabaseToServer() {
        try {
            // Check network connectivity first
            if (!isNetworkAvailable()) {
                Log.d(TAG, "No network available - skipping server sync (local storage continues)")
                return
            }

            // Get auth token
            val token = authRepository.authToken.first()
            if (token.isNullOrBlank()) {
                return
            }
            NetworkModule.setAuthToken(token)

            // Get unsynced call logs from database
            val callLogDao = database.callLogDao()
            val unsyncedLogs = callLogDao.getUnsyncedCallLogs()

            if (unsyncedLogs.isEmpty()) {
                return
            }

            // Convert to API format
            val callLogRequests = unsyncedLogs.map { entity ->
                CallLogRequest(
                    phoneNumber = entity.phoneNumber,
                    callType = entity.callType,
                    duration = entity.duration,
                    contactName = entity.contactName,
                    timestamp = entity.timestamp,
                    staffId = entity.staffId
                )
            }

            // Upload to backend
            apiService.logCalls(callLogRequests)
            Log.d(TAG, "Uploaded ${callLogRequests.size} call logs to server")

            // Mark as synced
            callLogDao.markAsSynced(unsyncedLogs.map { it.id })

            // Update notification
            updateNotification("Synced: ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())} | ${unsyncedLogs.size} uploaded")

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to server (will retry later)", e)
            // Don't crash - data is safe in local database
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    private fun getCallLogsFromPhone(staffId: String): List<CallLogEntity> {
        val list = mutableListOf<CallLogEntity>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_NAME
        )
        
        // Smart sync: First time = 240 days (8 months, includes August 2025), subsequent = 30 days (ongoing)
        val prefs = applicationContext.getSharedPreferences("mep_app_prefs", android.content.Context.MODE_PRIVATE)
        val hasHistoricalSync = prefs.getBoolean("historical_sync_complete", false)
        
        val daysToSync = if (hasHistoricalSync) {
            30L // Ongoing: last 30 days
        } else {
            240L // First time: last 8 months (ensures August 2025 is included)
        }
        
        val daysAgo = System.currentTimeMillis() - (daysToSync * 24 * 60 * 60 * 1000)
        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(daysAgo.toString())
        
        Log.d(TAG, "Syncing last $daysToSync days of call logs (historical_sync_complete=$hasHistoricalSync)")
        
        val cursor = applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndex(CallLog.Calls._ID)
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
            val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            
            while (it.moveToNext()) {
                val callId = it.getString(idIndex)
                val number = it.getString(numberIndex) ?: "Unknown"
                val type = when (it.getInt(typeIndex)) {
                    CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                    CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                    CallLog.Calls.MISSED_TYPE -> "MISSED"
                    else -> "UNKNOWN"
                }
                val date = it.getLong(dateIndex)
                val duration = it.getLong(durationIndex)
                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(date))
                val contactName = it.getString(nameIndex)
                
                list.add(
                    CallLogEntity(
                        phoneNumber = number,
                        callType = type,
                        duration = duration,
                        contactName = contactName,
                        timestamp = isoDate,
                        staffId = staffId,
                        isSynced = false,
                        phoneCallId = callId
                    )
                )
            }
        }
        
        // Mark historical sync as complete after first successful sync
        if (!hasHistoricalSync && list.isNotEmpty()) {
            prefs.edit().putBoolean("historical_sync_complete", true).apply()
            Log.d(TAG, "Historical sync complete! Saved ${list.size} call logs. Future syncs will use 30-day window.")
        }
        
        return list
    }
    
    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MEP App")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CallLogSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, CallLogSyncService::class.java)
            context.stopService(intent)
        }
    }
}
