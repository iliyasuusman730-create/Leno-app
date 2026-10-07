package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.TeacherRoleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeacherRoleDao {
    @Query("SELECT * FROM teacher_roles WHERE userId = :userId")
    fun getTeacherRole(userId: String): Flow<TeacherRoleEntity?>

    @Query("SELECT * FROM teacher_roles WHERE userId = :userId")
    suspend fun getTeacherRoleSync(userId: String): TeacherRoleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(role: TeacherRoleEntity)

    @Query("UPDATE teacher_roles SET status = :status, reviewedAt = :reviewedAt WHERE userId = :userId")
    suspend fun updateStatus(userId: String, status: String, reviewedAt: Long = System.currentTimeMillis())

    @Query("UPDATE teacher_roles SET status = :status, approvedSubject = :approvedSubject, isTrustedTeacher = :isTrusted, reviewedAt = :reviewedAt WHERE userId = :userId")
    suspend fun updateTeacherApproval(userId: String, status: String, approvedSubject: String, isTrusted: Boolean, reviewedAt: Long = System.currentTimeMillis())

    @Query("UPDATE teacher_roles SET isContentCreator = :isCreator WHERE userId = :userId")
    suspend fun updateContentCreator(userId: String, isCreator: Boolean)

    @Query("SELECT * FROM teacher_roles WHERE status = 'PENDING' ORDER BY submittedAt DESC")
    fun getPendingTeacherApplications(): Flow<List<TeacherRoleEntity>>

    @Query("SELECT * FROM teacher_roles WHERE status = 'PENDING' ORDER BY submittedAt DESC")
    suspend fun getPendingTeacherApplicationsSync(): List<TeacherRoleEntity>

    @Query("UPDATE teacher_roles SET bankName = :bankName, accountNumber = :accountNumber, accountName = :accountName WHERE userId = :userId")
    suspend fun updateBankSettlementAccount(userId: String, bankName: String, accountNumber: String, accountName: String)

    @Query("UPDATE teacher_roles SET availableBalance = availableBalance + :amount, totalEarnings = totalEarnings + :amount WHERE userId = :userId")
    suspend fun creditTeacherEarnings(userId: String, amount: Double)

    @Query("UPDATE teacher_roles SET availableBalance = 0.0, totalPaidOut = totalPaidOut + :amount WHERE userId = :userId")
    suspend fun recordTeacherPayout(userId: String, amount: Double)

    @Query("DELETE FROM teacher_roles WHERE userId = :userId")
    suspend fun deleteTeacherRole(userId: String)

    @Query("DELETE FROM teacher_roles WHERE userId IN ('user_len_maths_teacher', 'user_len_quran_teacher', 'user_len_english_teacher', 'user_len_skills_teacher')")
    suspend fun deleteDemoTeacherRoles()
}
