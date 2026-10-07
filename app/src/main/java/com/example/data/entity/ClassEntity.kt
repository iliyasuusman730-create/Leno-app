package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey val classId: String,
    val title: String,
    val subject: String,
    val level: String = "All Levels",
    val shortDescription: String,
    val lessonContent: String = "",
    val imageUrl: String = "",
    val optionalImages: String = "",
    val videoUrl: String = "",
    val hasVideo: Boolean = false,
    val isPaid: Boolean = true,
    val price: String = "₦1,500",
    val schedule: String = "Self-paced",
    val lessonType: String = "Text lesson",
    val instructorUserId: String,
    val instructorName: String,
    val instructorUsername: String,
    val instructorLinoId: String,
    val instructorIsTeacher: Boolean = true,
    val instructorIsTrusted: Boolean = false,
    val enrolledStudentsCount: Int = 0,
    val status: String = "PUBLISHED", // "DRAFT", "PUBLISHED", "ARCHIVED"
    val lessonCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isPublished: Boolean
        get() = status.equals("PUBLISHED", ignoreCase = true)

    val isDraft: Boolean
        get() = status.equals("DRAFT", ignoreCase = true)
}
