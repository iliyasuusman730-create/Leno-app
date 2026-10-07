package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "enrollments")
data class EnrollmentEntity(
    @PrimaryKey val enrollmentId: String,
    val classId: String,
    val studentUserId: String,
    val studentLinoId: String = "",
    val teacherUserId: String = "",
    val status: String = "ACTIVE",
    val enrolledAt: Long = System.currentTimeMillis(),
    val progress: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis()
)
