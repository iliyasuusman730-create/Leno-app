package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY displayName ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY displayName ASC")
    suspend fun getAllUsersSync(): List<UserEntity>

    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    fun getCurrentUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUserSync(): UserEntity?

    @Query("SELECT * FROM users WHERE userId = :userId")
    fun getUserById(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserByIdSync(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsernameSync(username: String): UserEntity?

    @Query("""
        SELECT * FROM users 
        WHERE UPPER(linoId) = UPPER(:linoId) 
           OR UPPER(linoId) = 'LEN-' || UPPER(:linoId)
           OR UPPER(linoId) = 'LEN' || UPPER(:linoId)
           OR UPPER(REPLACE(linoId, '-', '')) = UPPER(REPLACE(:linoId, '-', ''))
           OR UPPER(REPLACE(REPLACE(linoId, 'LEN-', ''), '-', '')) = UPPER(REPLACE(REPLACE(:linoId, 'LEN-', ''), '-', ''))
           OR linoId LIKE '%' || :linoId || '%'
        LIMIT 1
    """)
    suspend fun getUserByLinoIdSync(linoId: String): UserEntity?

    @Query("""
        SELECT * FROM users 
        WHERE UPPER(linoId) = UPPER(:linoId) 
           OR UPPER(linoId) = 'LEN-' || UPPER(:linoId)
           OR UPPER(linoId) = 'LEN' || UPPER(:linoId)
           OR UPPER(REPLACE(linoId, '-', '')) = UPPER(REPLACE(:linoId, '-', ''))
           OR UPPER(REPLACE(REPLACE(linoId, 'LEN-', ''), '-', '')) = UPPER(REPLACE(REPLACE(:linoId, 'LEN-', ''), '-', ''))
           OR linoId LIKE '%' || :linoId || '%'
    """)
    suspend fun getAllUsersByLinoIdSync(linoId: String): List<UserEntity>

    @Query("""
        SELECT * FROM users 
        WHERE UPPER(linoId) = UPPER(:linoId) 
           OR UPPER(linoId) = 'LEN-' || UPPER(:linoId)
           OR UPPER(linoId) = 'LEN' || UPPER(:linoId)
           OR UPPER(REPLACE(linoId, '-', '')) = UPPER(REPLACE(:linoId, '-', ''))
           OR UPPER(REPLACE(REPLACE(linoId, 'LEN-', ''), '-', '')) = UPPER(REPLACE(REPLACE(:linoId, 'LEN-', ''), '-', ''))
           OR linoId LIKE '%' || :linoId || '%'
    """)
    fun getAllUsersByLinoId(linoId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE LOWER(displayName) = LOWER(:displayName) OR LOWER(displayName) LIKE '%' || LOWER(:displayName) || '%' LIMIT 1")
    suspend fun getUserByDisplayNameSync(displayName: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmailSync(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(googleEmail) = LOWER(:googleEmail) LIMIT 1")
    suspend fun getUserByGoogleEmailSync(googleEmail: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(recoveryEmail) = LOWER(:recoveryEmail) OR LOWER(email) = LOWER(:recoveryEmail) LIMIT 1")
    suspend fun getUserByRecoveryEmailSync(recoveryEmail: String): UserEntity?

    @Query("""
        SELECT * FROM users 
        WHERE LOWER(username) = LOWER(:identifier) 
           OR UPPER(linoId) = UPPER(:identifier) 
           OR UPPER(linoId) = 'LEN-' || UPPER(:identifier)
           OR UPPER(linoId) = 'LEN' || UPPER(:identifier)
           OR UPPER(REPLACE(linoId, '-', '')) = UPPER(REPLACE(:identifier, '-', '')) 
           OR UPPER(REPLACE(REPLACE(linoId, 'LEN-', ''), '-', '')) = UPPER(REPLACE(REPLACE(:identifier, 'LEN-', ''), '-', ''))
           OR LOWER(email) = LOWER(:identifier) 
           OR LOWER(googleEmail) = LOWER(:identifier) 
           OR LOWER(recoveryEmail) = LOWER(:identifier) 
           OR LOWER(displayName) = LOWER(:identifier) 
           OR phoneNumber = :identifier 
           OR userId = :identifier
        ORDER BY 
           CASE 
               WHEN LOWER(username) = LOWER(:identifier) THEN 1
               WHEN UPPER(linoId) = UPPER(:identifier) OR UPPER(linoId) = 'LEN-' || UPPER(:identifier) THEN 2
               WHEN LOWER(email) = LOWER(:identifier) OR LOWER(googleEmail) = LOWER(:identifier) THEN 3
               WHEN userId = :identifier THEN 4
               ELSE 5 
           END
        LIMIT 1
    """)
    suspend fun getUserByIdentifierSync(identifier: String): UserEntity?

    @Query("SELECT * FROM users WHERE (normalizedPhoneNumber = :normalizedPhone AND normalizedPhoneNumber != '') OR (phoneNumber = :rawPhone AND phoneNumber != '') OR (normalizedPhoneNumber = :rawPhone AND normalizedPhoneNumber != '') OR (phoneNumber = :normalizedPhone AND phoneNumber != '') OR REPLACE(phoneNumber, ' ', '') = REPLACE(:rawPhone, ' ', '') OR REPLACE(phoneNumber, '-', '') = REPLACE(:rawPhone, '-', '') LIMIT 1")
    suspend fun getUserByPhoneSync(rawPhone: String, normalizedPhone: String = rawPhone): UserEntity?

    @Query("""
        SELECT * FROM users 
        WHERE userId != :currentUserId 
          AND (
            LOWER(username) LIKE '%' || LOWER(:query) || '%' 
            OR LOWER(displayName) LIKE '%' || LOWER(:query) || '%' 
            OR UPPER(linoId) LIKE '%' || UPPER(:query) || '%'
            OR UPPER(REPLACE(linoId, '-', '')) LIKE '%' || UPPER(REPLACE(:query, '-', '')) || '%'
            OR (phoneNumber != '' AND phoneNumber LIKE '%' || :query || '%')
            OR LOWER(userId) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(email) LIKE '%' || LOWER(:query) || '%'
          )
        ORDER BY displayName ASC
    """)
    fun searchUsers(query: String, currentUserId: String): Flow<List<UserEntity>>

    @Query("""
        SELECT u.* FROM users u
        INNER JOIN friendships f ON (
            (f.requesterId = :userId AND f.targetId = u.userId)
            OR (f.targetId = :userId AND f.requesterId = u.userId)
        )
        WHERE f.status = 'ACCEPTED' AND u.userId != :userId
        ORDER BY u.displayName ASC
    """)
    fun getContactsForUser(userId: String): Flow<List<UserEntity>>

    @Query("""
        SELECT u.* FROM users u
        INNER JOIN friendships f ON f.requesterId = u.userId
        WHERE f.targetId = :userId AND f.status = 'PENDING'
        ORDER BY f.createdAt DESC
    """)
    fun getPendingRequestUsers(userId: String): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET isCurrentUser = 0")
    suspend fun clearCurrentUser()

    @Query("UPDATE users SET isCurrentUser = (CASE WHEN userId = :userId THEN 1 ELSE 0 END)")
    suspend fun setCurrentUser(userId: String)

    @Query("UPDATE users SET isOnline = :isOnline, lastSeen = :lastSeen WHERE userId = :userId")
    suspend fun updateOnlineStatus(userId: String, isOnline: Boolean, lastSeen: Long = System.currentTimeMillis())

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUser(userId: String)

    @Query("DELETE FROM users WHERE isOfficial = 0 AND userId != 'official_leno_account'")
    suspend fun deleteNonOfficialUsers()

    @Query("DELETE FROM users WHERE userId = 'test_dummy_user_to_purge'")
    suspend fun purgeDemoAccounts()

    @Query("DELETE FROM users WHERE userId = 'never_delete_registered_teachers'")
    suspend fun deleteDemoTeachers()

    @Query("UPDATE users SET role = :role, assignedSubject = :assignedSubject WHERE userId = :userId")
    suspend fun updateUserRole(userId: String, role: String, assignedSubject: String)

    @Query("UPDATE users SET teacherStatus = :teacherStatus, teacherSubject = :teacherSubject, updatedAt = :updatedAt WHERE userId = :userId")
    suspend fun updateUserTeacherStatus(userId: String, teacherStatus: String, teacherSubject: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE users SET role = 'TEACHER', teacherStatus = 'APPROVED', teacherSubject = :teacherSubject, assignedSubject = :teacherSubject, updatedAt = :updatedAt WHERE userId = :userId")
    suspend fun approveUserAsTeacher(userId: String, teacherSubject: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE users SET avatarUrl = '' WHERE avatarUrl LIKE '%picsum.photos%' OR avatarUrl LIKE '%demo%'")
    suspend fun clearDemoAvatars()

    @Query("UPDATE users SET isRestricted = :restricted, updatedAt = :updatedAt WHERE userId = :userId")
    suspend fun setUserRestricted(userId: String, restricted: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE users SET isVerified = 1, updatedAt = :updatedAt WHERE userId = :userId")
    suspend fun markUserVerified(userId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM users WHERE UPPER(role) = 'TEACHER' ORDER BY displayName ASC")
    fun getTeachers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE UPPER(role) = 'STUDENT' ORDER BY displayName ASC")
    fun getStudents(): Flow<List<UserEntity>>
}

