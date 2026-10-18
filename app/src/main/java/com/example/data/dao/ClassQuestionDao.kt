package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.ClassQuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassQuestionDao {
    @Query("SELECT * FROM class_questions WHERE classId = :classId ORDER BY timestamp ASC")
    fun getQuestionsForClass(classId: String): Flow<List<ClassQuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: ClassQuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<ClassQuestionEntity>)

    @Query("UPDATE class_questions SET answerText = :answerText, isAnswered = 1, answeredAt = :answeredAt WHERE questionId = :questionId")
    suspend fun answerQuestion(questionId: String, answerText: String, answeredAt: Long = System.currentTimeMillis())

    @Query("UPDATE class_questions SET answerText = :answerText, answerAudioUrl = :answerAudioUrl, answerAudioDurationSeconds = :answerAudioDurationSeconds, isAnswered = 1, answeredAt = :answeredAt WHERE questionId = :questionId")
    suspend fun answerQuestionWithAudio(
        questionId: String,
        answerText: String,
        answerAudioUrl: String?,
        answerAudioDurationSeconds: Int,
        answeredAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM class_questions WHERE classId = :classId")
    suspend fun deleteQuestionsForClass(classId: String)
}
