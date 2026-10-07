package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.MessageEntity
import com.example.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("""
        SELECT * FROM messages 
        WHERE (senderId = :user1Id AND receiverId = :user2Id) 
           OR (senderId = :user2Id AND receiverId = :user1Id)
        ORDER BY timestamp ASC
    """)
    fun getMessagesBetweenUsers(user1Id: String, user2Id: String): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM messages 
        WHERE (senderId IN (:user1Ids) AND receiverId IN (:user2Ids)) 
           OR (senderId IN (:user2Ids) AND receiverId IN (:user1Ids))
        ORDER BY timestamp ASC
    """)
    fun getMessagesBetweenUserAliases(user1Ids: List<String>, user2Ids: List<String>): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM messages 
        WHERE (senderId IN (:user1Ids) AND receiverId IN (:user2Ids)) 
           OR (senderId IN (:user2Ids) AND receiverId IN (:user1Ids))
        ORDER BY timestamp ASC
    """)
    suspend fun getMessagesBetweenUserAliasesSync(user1Ids: List<String>, user2Ids: List<String>): List<MessageEntity>

    @Query("""
        SELECT * FROM messages 
        WHERE (senderId = :user1Id AND receiverId = :user2Id) 
           OR (senderId = :user2Id AND receiverId = :user1Id)
        ORDER BY timestamp ASC
    """)
    suspend fun getMessagesBetweenUsersSync(user1Id: String, user2Id: String): List<MessageEntity>

    @Query("""
        SELECT * FROM messages 
        WHERE senderId = :currentUserId OR receiverId = :currentUserId
        ORDER BY timestamp DESC
    """)
    fun getAllMessagesForUser(currentUserId: String): Flow<List<MessageEntity>>

    @Query("""
        SELECT * FROM messages 
        WHERE senderId = :currentUserId OR receiverId = :currentUserId
        ORDER BY timestamp ASC
    """)
    suspend fun getAllMessagesForUserSync(currentUserId: String): List<MessageEntity>

    @Query("""
        SELECT * FROM messages 
        WHERE senderId IN (:userAliases) OR receiverId IN (:userAliases)
        ORDER BY timestamp DESC
    """)
    suspend fun getAllMessagesForUserAliasesSync(userAliases: List<String>): List<MessageEntity>

    @Query("""
        SELECT u.* FROM users u
        WHERE (
            u.userId IN (
                SELECT DISTINCT CASE 
                    WHEN senderId IN (:userAliases) THEN receiverId 
                    ELSE senderId 
                END
                FROM messages 
                WHERE senderId IN (:userAliases) OR receiverId IN (:userAliases)
            )
            OR u.username IN (
                SELECT DISTINCT CASE 
                    WHEN senderId IN (:userAliases) THEN receiverId 
                    ELSE senderId 
                END
                FROM messages 
                WHERE senderId IN (:userAliases) OR receiverId IN (:userAliases)
            )
            OR u.linoId IN (
                SELECT DISTINCT CASE 
                    WHEN senderId IN (:userAliases) THEN receiverId 
                    ELSE senderId 
                END
                FROM messages 
                WHERE senderId IN (:userAliases) OR receiverId IN (:userAliases)
            )
        )
        AND u.userId NOT IN (:userAliases)
        AND u.username NOT IN (:userAliases)
        ORDER BY u.displayName ASC
    """)
    fun getChatPartnersForUserAliases(userAliases: List<String>): Flow<List<UserEntity>>

    fun getChatPartnersForUser(currentUserId: String): Flow<List<UserEntity>> =
        getChatPartnersForUserAliases(listOf(currentUserId))

    fun getLastMessageBetween(user1Id: String, user2Id: String): Flow<MessageEntity?> =
        getLastMessageBetweenAliases(listOf(user1Id), listOf(user2Id))

    suspend fun getLastMessageBetweenSync(user1Id: String, user2Id: String): MessageEntity? =
        getLastMessageBetweenAliasesSync(listOf(user1Id), listOf(user2Id))

    @Query("""
        SELECT DISTINCT CASE 
            WHEN senderId IN (:userAliases) THEN receiverId 
            ELSE senderId 
        END
        FROM messages 
        WHERE senderId IN (:userAliases) OR receiverId IN (:userAliases)
    """)
    fun getChatPartnerIdsForUserAliases(userAliases: List<String>): Flow<List<String>>

    @Query("""
        SELECT DISTINCT CASE 
            WHEN senderId IN (:userAliases) THEN receiverId 
            ELSE senderId 
        END
        FROM messages 
        WHERE senderId IN (:userAliases) OR receiverId IN (:userAliases)
    """)
    suspend fun getChatPartnerIdsForUserAliasesSync(userAliases: List<String>): List<String>

    @Query("""
        SELECT * FROM messages 
        WHERE (senderId IN (:user1Ids) AND receiverId IN (:user2Ids)) 
           OR (senderId IN (:user2Ids) AND receiverId IN (:user1Ids))
        ORDER BY timestamp DESC LIMIT 1
    """)
    fun getLastMessageBetweenAliases(user1Ids: List<String>, user2Ids: List<String>): Flow<MessageEntity?>

    @Query("""
        SELECT * FROM messages 
        WHERE (senderId IN (:user1Ids) AND receiverId IN (:user2Ids)) 
           OR (senderId IN (:user2Ids) AND receiverId IN (:user1Ids))
        ORDER BY timestamp DESC LIMIT 1
    """)
    suspend fun getLastMessageBetweenAliasesSync(user1Ids: List<String>, user2Ids: List<String>): MessageEntity?

    @Query("""
        SELECT COUNT(*) FROM messages 
        WHERE receiverId = :currentUserId AND senderId = :otherUserId AND status != 'READ'
    """)
    fun getUnreadCountFromUser(currentUserId: String, otherUserId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET status = 'READ' WHERE receiverId = :currentUserId AND senderId = :otherUserId")
    suspend fun markMessagesAsRead(currentUserId: String, otherUserId: String)

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageByIdSync(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE senderId = :currentUserId AND (status = 'SENDING' OR status = 'FAILED')")
    suspend fun getPendingMessagesForUserSync(currentUserId: String): List<MessageEntity>

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE messages SET senderId = :newUid WHERE senderId = :oldUid")
    suspend fun migrateSenderId(oldUid: String, newUid: String)

    @Query("UPDATE messages SET receiverId = :newUid WHERE receiverId = :oldUid")
    suspend fun migrateReceiverId(oldUid: String, newUid: String)

    @Query("DELETE FROM messages WHERE (senderId = :user1Id AND receiverId = :user2Id) OR (senderId = :user2Id AND receiverId = :user1Id)")
    suspend fun deleteConversation(user1Id: String, user2Id: String)

    @Query("DELETE FROM messages WHERE senderId = :userId OR receiverId = :userId")
    suspend fun clearMessagesForUser(userId: String)

    @Query("DELETE FROM messages WHERE senderId = receiverId")
    suspend fun deleteCorruptedSelfMessages()

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}
