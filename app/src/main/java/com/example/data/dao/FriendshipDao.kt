package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.FriendshipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendshipDao {
    @Query("SELECT * FROM friendships WHERE requesterId = :userId OR targetId = :userId")
    fun getFriendshipsForUser(userId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE targetId = :userId AND status = 'PENDING'")
    fun getPendingRequestsForUser(userId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE (requesterId = :u1 AND targetId = :u2) OR (requesterId = :u2 AND targetId = :u1) LIMIT 1")
    fun getFriendshipBetween(u1: String, u2: String): Flow<FriendshipEntity?>

    @Query("SELECT * FROM friendships WHERE (requesterId = :u1 AND targetId = :u2) OR (requesterId = :u2 AND targetId = :u1) LIMIT 1")
    suspend fun getFriendshipBetweenSync(u1: String, u2: String): FriendshipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendship(friendship: FriendshipEntity)

    @Query("UPDATE friendships SET status = :status WHERE (requesterId = :u1 AND targetId = :u2) OR (requesterId = :u2 AND targetId = :u1)")
    suspend fun updateFriendshipStatus(u1: String, u2: String, status: String)

    @Query("DELETE FROM friendships WHERE (requesterId = :u1 AND targetId = :u2) OR (requesterId = :u2 AND targetId = :u1)")
    suspend fun removeFriendship(u1: String, u2: String)

    @Query("DELETE FROM friendships WHERE requesterId = :userId OR targetId = :userId")
    suspend fun clearFriendshipsForUser(userId: String)

    @Query("DELETE FROM friendships WHERE status = 'ACCEPTED' AND requesterId = :userId AND targetId NOT IN (:activeFriendIds)")
    suspend fun deleteAcceptedOutgoingNotIn(userId: String, activeFriendIds: List<String>)

    @Query("DELETE FROM friendships WHERE status = 'ACCEPTED' AND targetId = :userId AND requesterId NOT IN (:activeFriendIds)")
    suspend fun deleteAcceptedIncomingNotIn(userId: String, activeFriendIds: List<String>)

    @Query("DELETE FROM friendships WHERE status = 'ACCEPTED' AND (requesterId = :userId OR targetId = :userId)")
    suspend fun deleteAllAcceptedForUser(userId: String)

    @Query("DELETE FROM friendships WHERE status = 'PENDING' AND targetId = :userId AND requesterId NOT IN (:requesterIds)")
    suspend fun deletePendingIncomingNotIn(userId: String, requesterIds: List<String>)

    @Query("DELETE FROM friendships WHERE status = 'PENDING' AND targetId = :userId")
    suspend fun deleteAllPendingIncoming(userId: String)

    @Query("DELETE FROM friendships WHERE status = 'PENDING' AND requesterId = :userId AND targetId NOT IN (:targetIds)")
    suspend fun deletePendingOutgoingNotIn(userId: String, targetIds: List<String>)

    @Query("DELETE FROM friendships WHERE status = 'PENDING' AND requesterId = :userId")
    suspend fun deleteAllPendingOutgoing(userId: String)

    @Query("DELETE FROM friendships")
    suspend fun deleteAllFriendships()
}

