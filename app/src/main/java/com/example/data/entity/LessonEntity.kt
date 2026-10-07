package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val lessonId: String,
    val classId: String,
    val title: String,
    val content: String,
    val orderIndex: Int = 1,
    val summary: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
