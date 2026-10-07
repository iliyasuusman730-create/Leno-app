package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey val callId: String,
    val callerId: String,
    val callerName: String,
    val callerAvatar: String = "",
    val callerUsername: String,
    val receiverId: String,
    val receiverName: String,
    val receiverAvatar: String = "",
    val receiverUsername: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val callType: String = "VOICE", // "VOICE" or "VIDEO"
    val status: String = "COMPLETED" // "COMPLETED", "MISSED", "DECLINED", "OUTGOING", "INCOMING"
)
