package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.ClassEntity
import com.example.data.entity.EnrollmentEntity
import com.example.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EnrollmentDao {
    @Query("SELECT * FROM enrollments WHERE studentUserId = :studentUserId ORDER BY enrolledAt DESC")
    fun getEnrollmentsForStudent(studentUserId: String): Flow<List<EnrollmentEntity>>

    @Query("SELECT * FROM enrollments WHERE classId = :classId")
    fun getEnrollmentsForClass(classId: String): Flow<List<EnrollmentEntity>>

    @Query("""
        SELECT u.* FROM users u 
        INNER JOIN enrollments e ON u.userId = e.studentUserId 
        WHERE e.classId = :classId 
        ORDER BY e.enrolledAt ASC
    """)
    fun getEnrolledStudentsForClass(classId: String): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM enrollments WHERE classId = :classId")
    suspend fun getEnrollmentCountForClass(classId: String): Int

    @Query("SELECT COUNT(*) FROM enrollments WHERE studentUserId = :studentUserId")
    suspend fun getEnrollmentCountForStudent(studentUserId: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM enrollments WHERE studentUserId = :studentUserId AND classId = :classId)")
    fun isStudentEnrolled(studentUserId: String, classId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM enrollments WHERE studentUserId = :studentUserId AND classId = :classId)")
    suspend fun isStudentEnrolledSync(studentUserId: String, classId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrollment(enrollment: EnrollmentEntity)

    @Query("DELETE FROM enrollments WHERE studentUserId = :studentUserId AND classId = :classId")
    suspend fun deleteEnrollment(studentUserId: String, classId: String)

    @Query("SELECT c.* FROM classes c INNER JOIN enrollments e ON c.classId = e.classId WHERE e.studentUserId = :studentUserId ORDER BY e.enrolledAt DESC")
    fun getEnrolledClasses(studentUserId: String): Flow<List<ClassEntity>>
}
