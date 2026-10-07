package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val senderId: String? = null,
    val title: String,
    val body: String,
    val type: String, // "FRIEND_REQUEST", "REQUEST_ACCEPTED", "SYSTEM", "MESSAGE"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
