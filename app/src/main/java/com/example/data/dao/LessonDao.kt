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

    @Query("""
        DELETE FROM lessons 
        WHERE lessonId LIKE 'demo_%' 
           OR lessonId LIKE 'mock_%' 
           OR lessonId LIKE 'test_%' 
           OR lessonId IN ('les_quran_01', 'les_quran_02', 'les_skills_01', 'les_skills_02')
           OR classId IN ('class_quran_mastery', 'class_online_skills_dev', 'class_maths_core_algebra', 'class_jamb_intensive', 'class_secondary_maths', 'class_jamb_prep', 'class_science_biology', 'class_math_core_01', 'class_quran_tajweed_02', 'class_english_grammar_03', 'class_vocational_skills_04', 'class_general_science_05', 'class_digital_literacy_06')
    """)
    suspend fun deleteDemoLessons()
}
