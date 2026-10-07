package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.LessonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE classId = :classId ORDER BY orderIndex ASC")
    fun getLessonsForClass(classId: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE classId = :classId ORDER BY orderIndex ASC")
    suspend fun getLessonsForClassSync(classId: String): List<LessonEntity>

    @Query("SELECT COUNT(*) FROM lessons WHERE classId = :classId")
    suspend fun getLessonCountForClass(classId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLesson(lesson: LessonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Query("DELETE FROM lessons WHERE lessonId = :lessonId")
    suspend fun deleteLesson(lessonId: String)

    @Query("DELETE FROM lessons WHERE classId = :classId")
    suspend fun deleteLessonsForClass(classId: String)

    @Query("DELETE FROM lessons WHERE classId LIKE 'class_maths_%' OR classId LIKE 'class_quran_%' OR classId LIKE 'class_english_%' OR classId LIKE 'class_skills_%' OR classId = 'class_maths_1' OR classId = 'class_maths_2' OR classId = 'class_maths_3' OR classId = 'class_quran_1' OR classId = 'class_english_1' OR classId = 'class_skills_1'")
    suspend fun deleteDemoLessons()
}
