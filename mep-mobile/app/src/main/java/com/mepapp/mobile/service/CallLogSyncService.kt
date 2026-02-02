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
                NotificationManager.IMPORTANCE_LOW // LOW importance = no sound
            ).apply {
                description = "Syncs call logs to server in background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Disable sound
                enableVibration(false) // Disable vibration
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
            .setContentTitle("Avawke")
            .setContentText("Avawke Service is Live")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // LOW = no sound
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true) // Silent notification
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

                    // Step 3: Send heartbeat to server
                    sendHeartbeat()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during sync", e)
                }

                // Wait 30 seconds before next sync
                delay(30000)
            }
        }
    }

    private suspend fun sendHeartbeat() {
        try {
            if (!isNetworkAvailable()) return

            val token = authRepository.authToken.first()
            if (token.isNullOrBlank()) return

            NetworkModule.setAuthToken(token)
            apiService.sendHeartbeat()
            Log.d(TAG, "Heartbeat sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send heartbeat", e)
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
                // Still update last sync time to show service is active
                authRepository.updateLastSyncTime()
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

            // Upload to backend - sync one by one to identify failures
            Log.d(TAG, "=== STARTING SERVER SYNC ===")
            Log.d(TAG, "Attempting to sync ${unsyncedLogs.size} call logs")

            var successCount = 0
            val successIds = mutableListOf<Long>()

            for (entity in unsyncedLogs) {
                try {
                    val request = CallLogRequest(
                        phoneNumber = entity.phoneNumber,
                        callType = entity.callType,
                        duration = entity.duration,
                        contactName = entity.contactName,
                        timestamp = entity.timestamp,
                        staffId = entity.staffId,
                        phoneCallId = entity.phoneCallId // Unique ID for deduplication on server
                    )
                    Log.d(TAG, "Syncing: id=${entity.id}, phone=${entity.phoneNumber}, staffId=${entity.staffId}, phoneCallId=${entity.phoneCallId}")

                    apiService.logCall(request) // Use single call API
                    successIds.add(entity.id)
                    successCount++
                    Log.d(TAG, "SUCCESS: Synced call ${entity.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "FAILED to sync call ${entity.id}: ${e.message}")
                }
            }

            // Mark successful ones as synced
            if (successIds.isNotEmpty()) {
                callLogDao.markAsSynced(successIds)
                Log.d(TAG, "Marked ${successIds.size} logs as synced")
                updateNotification("Synced $successCount/${unsyncedLogs.size} calls")

                // Update last sync time for UI status indicator
                authRepository.updateLastSyncTime()
            } else {
                Log.w(TAG, "No calls were synced successfully!")
                updateNotification("Sync failed - ${unsyncedLogs.size} pending")
            }

            Log.d(TAG, "=== SYNC COMPLETE: $successCount/${unsyncedLogs.size} ===")

        } catch (e: Exception) {
            Log.e(TAG, "=== SYNC ERROR ===", e)
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

        // MINIMUM DATE: August 1, 2025 - don't sync calls before this date
        val minDateCalendar = Calendar.getInstance().apply {
            set(2025, Calendar.AUGUST, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val minDateMillis = minDateCalendar.timeInMillis

        // Only sync calls from August 1, 2025 onwards
        val selection = "${CallLog.Calls.DATE} >= ?"
        val selectionArgs = arrayOf(minDateMillis.toString())

        Log.d(TAG, "Syncing call logs from August 1, 2025 onwards")
        
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
        
        Log.d(TAG, "Found ${list.size} call logs from August 1, 2025 onwards")
        return list
    }
    
    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Avawke")
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
