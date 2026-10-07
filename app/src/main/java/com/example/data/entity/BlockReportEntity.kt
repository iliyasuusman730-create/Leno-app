package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocks_reports")
data class BlockReportEntity(
    @PrimaryKey val id: String,
    val blockerId: String,
    val targetId: String,
    val isBlocked: Boolean = true,
    val isReported: Boolean = false,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
