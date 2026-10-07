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

    @Query("UPDATE class_questions SET answerText = :answerText, isAnswered = 1, answeredAt = :answeredAt WHERE questionId = :questionId")
    suspend fun answerQuestion(questionId: String, answerText: String, answeredAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM class_questions WHERE classId = :classId")
    suspend fun deleteQuestionsForClass(classId: String)
}
