package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.ClassEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes WHERE status = 'PUBLISHED' ORDER BY createdAt DESC")
    fun getPublishedClasses(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes ORDER BY createdAt DESC")
    fun getAllClasses(): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE subject = :subject AND status = 'PUBLISHED' ORDER BY createdAt DESC")
    fun getPublishedClassesBySubject(subject: String): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE subject = :subject ORDER BY createdAt DESC")
    fun getClassesBySubject(subject: String): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE instructorUserId = :instructorUserId ORDER BY createdAt DESC")
    fun getClassesByInstructor(instructorUserId: String): Flow<List<ClassEntity>>

    @Query("SELECT * FROM classes WHERE classId = :classId")
    fun getClassById(classId: String): Flow<ClassEntity?>

    @Query("SELECT * FROM classes WHERE classId = :classId")
    suspend fun getClassByIdSync(classId: String): ClassEntity?

    @Query("SELECT COUNT(*) FROM classes")
    suspend fun getClassCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(classEntity: ClassEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasses(classes: List<ClassEntity>)

    @Update
    suspend fun updateClass(classEntity: ClassEntity)

    @Query("UPDATE classes SET status = :status WHERE classId = :classId")
    suspend fun updateClassStatus(classId: String, status: String)

    @Query("UPDATE classes SET lessonCount = :count WHERE classId = :classId")
    suspend fun updateLessonCount(classId: String, count: Int)

    @Query("DELETE FROM classes WHERE classId = :classId")
    suspend fun deleteClassById(classId: String)

    @Query("""
        DELETE FROM classes 
        WHERE classId LIKE 'demo_%' 
           OR classId LIKE 'mock_%' 
           OR classId LIKE 'test_%'
           OR classId IN ('class_quran_mastery', 'class_online_skills_dev', 'class_maths_core_algebra', 'class_jamb_intensive', 'class_secondary_maths', 'class_jamb_prep', 'class_science_biology', 'class_math_core_01', 'class_quran_tajweed_02', 'class_english_grammar_03', 'class_vocational_skills_04', 'class_general_science_05', 'class_digital_literacy_06', 'class_maths_1', 'class_maths_2', 'class_maths_3', 'class_quran_1', 'class_english_1', 'class_skills_1')
           OR instructorUserId LIKE 'user_len_%_teacher'
    """)
    suspend fun deleteDemoClasses()

    @Query("DELETE FROM classes WHERE classId IN (:classIds)")
    suspend fun deleteClassesByIds(classIds: List<String>)

    @Query("UPDATE classes SET enrolledStudentsCount = enrolledStudentsCount + 1 WHERE classId = :classId")
    suspend fun incrementEnrolledStudents(classId: String)
}
