package com.mepapp.mobile.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val callType: String, // INCOMING, OUTGOING, MISSED
    val duration: Long,
    val contactName: String?,
    val timestamp: String, // ISO format
    val staffId: String,
    val isSynced: Boolean = false, // Track if uploaded to server
    val phoneCallId: String? = null // Phone's call log ID for deduplication
)
