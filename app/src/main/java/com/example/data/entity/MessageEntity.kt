package com.example.data.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val senderId: String,
    val receiverId: String,
    val text: String,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val audioDurationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT", // "SENT", "DELIVERED", "READ"
    val read: Boolean = false,
    val isSystemMessage: Boolean = false
) {
    @get:Ignore
    val isRead: Boolean
        get() = read || status.equals("READ", ignoreCase = true)
}
