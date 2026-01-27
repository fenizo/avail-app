package com.mepapp.mobile.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CallLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCallLog(callLog: CallLogEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllCallLogs(callLogs: List<CallLogEntity>)
    
    @Query("SELECT * FROM call_logs WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedCallLogs(): List<CallLogEntity>
    
    @Query("UPDATE call_logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
    
    @Query("SELECT COUNT(*) FROM call_logs WHERE phoneCallId = :phoneCallId")
    suspend fun callLogExists(phoneCallId: String): Int
    
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentCallLogs(): List<CallLogEntity>

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    suspend fun getAllCallLogs(): List<CallLogEntity>

    @Query("DELETE FROM call_logs WHERE isSynced = 1 AND timestamp < :timestamp")
    suspend fun deleteOldSyncedLogs(timestamp: String)
}
