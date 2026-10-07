package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val userId: String,
    val lowDataMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val darkThemeEnabled: Boolean = false,
    val muteOfficialLeno: Boolean = false
)
