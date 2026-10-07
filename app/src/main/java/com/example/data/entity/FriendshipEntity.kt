package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friendships")
data class FriendshipEntity(
    @PrimaryKey val id: String,
    val requesterId: String,
    val targetId: String,
    val status: String = "ACCEPTED", // "ACCEPTED", "PENDING", "BLOCKED"
    val createdAt: Long = System.currentTimeMillis()
)
