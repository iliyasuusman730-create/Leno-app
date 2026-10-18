package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_questions")
data class ClassQuestionEntity(
    @PrimaryKey val questionId: String,
    val classId: String,
    val senderUserId: String,
    val senderName: String,
    val senderLinoId: String,
    val senderRole: String = "STUDENT", // "STUDENT" or "TEACHER"
    val questionText: String,
    val audioUrl: String? = null,
    val audioDurationSeconds: Int = 0,
    val answerText: String = "",
    val answerAudioUrl: String? = null,
    val answerAudioDurationSeconds: Int = 0,
    val isAnswered: Boolean = false,
    val answeredAt: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
