package com.example.data.service

import android.util.Log
import com.example.data.LenoRepository
import com.example.data.model.SupportChatItem
import com.example.data.model.SupportMessageItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Service to manage real support chats:
 * - Collection: support_chats
 *   Fields: userId, ownerId, userName, lastMessage, status (open/closed), createdAt, lastUpdatedAt
 * - Subcollection: messages
 *   Fields: text, senderId, senderRole, timestamp, read
 */
class SupportChatService(
    private val firestoreInstance: FirebaseFirestore? = null
) {
    companion object {
        private const val TAG = "SupportChatService"
        const val COLLECTION_SUPPORT_CHATS = "support_chats"
        const val SUBCOLLECTION_MESSAGES = "messages"
    }

    private val db: FirebaseFirestore? by lazy {
        firestoreInstance ?: try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase Firestore not initialized: ${e.message}")
            null
        }
    }

    // In-memory fallbacks for resilient offline and instant local feedback
    private val inMemoryChats = ConcurrentHashMap<String, SupportChatItem>()
    private val inMemoryMessages = ConcurrentHashMap<String, MutableList<SupportMessageItem>>()

    /**
     * Finds an existing support chat for the given user, or creates a new one with status = "open".
     */
    suspend fun getOrCreateSupportChat(
        userId: String,
        userName: String,
        ownerId: String = LenoRepository.OFFICIAL_LENO_ID
    ): Result<SupportChatItem> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("userId cannot be blank"))
        }

        val database = db
        if (database == null) {
            // Local fallback
            val existing = inMemoryChats.values.firstOrNull { it.userId == userId }
            if (existing != null) {
                return@withContext Result.success(existing)
            }
            val newId = "local_support_$userId"
            val newChat = SupportChatItem(
                chatId = newId,
                userId = userId,
                ownerId = ownerId,
                userName = userName.ifBlank { "User ${userId.takeLast(4)}" },
                lastMessage = "Support chat opened",
                status = "open",
                createdAt = System.currentTimeMillis(),
                lastUpdatedAt = System.currentTimeMillis()
            )
            inMemoryChats[newId] = newChat
            val welcomeMsg = SupportMessageItem(
                messageId = "msg_welcome_$newId",
                text = "Hello $userName! Welcome to Leno Beta Support. How can we help you today?",
                senderId = ownerId,
                senderRole = "owner",
                timestamp = System.currentTimeMillis(),
                read = false
            )
            inMemoryMessages.getOrPut(newId) { mutableListOf() }.add(welcomeMsg)
            return@withContext Result.success(newChat)
        }

        try {
            // Check if a support_chat with this userId exists
            val querySnapshot = database.collection(COLLECTION_SUPPORT_CHATS)
                .whereEqualTo("userId", userId)
                .limit(1)
                .get()
                .await()

            if (!querySnapshot.isEmpty) {
                val doc = querySnapshot.documents.first()
                val chat = parseChatFromDoc(doc.id, doc.data ?: emptyMap())
                inMemoryChats[chat.chatId] = chat
                return@withContext Result.success(chat)
            }

            // Chat does not exist, create a new document
            val cleanUserName = userName.ifBlank { "User ${userId.takeLast(4)}" }
            val now = System.currentTimeMillis()
            val newDocRef = database.collection(COLLECTION_SUPPORT_CHATS).document()
            val newChatId = newDocRef.id

            val chatData = hashMapOf(
                "userId" to userId,
                "ownerId" to ownerId,
                "userName" to cleanUserName,
                "lastMessage" to "Support chat opened",
                "status" to "open",
                "createdAt" to now,
                "lastUpdatedAt" to now
            )
            newDocRef.set(chatData).await()

            // Seed initial welcome message from support owner
            val welcomeMessageData = hashMapOf(
                "text" to "Hello $cleanUserName! Welcome to Leno Beta Support. How can we help you today?",
                "senderId" to ownerId,
                "senderRole" to "owner",
                "timestamp" to now,
                "read" to false
            )
            newDocRef.collection(SUBCOLLECTION_MESSAGES).add(welcomeMessageData).await()

            val createdChat = SupportChatItem(
                chatId = newChatId,
                userId = userId,
                ownerId = ownerId,
                userName = cleanUserName,
                lastMessage = "Support chat opened",
                status = "open",
                createdAt = now,
                lastUpdatedAt = now
            )
            inMemoryChats[newChatId] = createdChat
            Result.success(createdChat)
        } catch (e: Exception) {
            Log.e(TAG, "Error in getOrCreateSupportChat: ${e.message}", e)
            // Fallback to local
            val fallbackChat = inMemoryChats.values.firstOrNull { it.userId == userId }
                ?: SupportChatItem(
                    chatId = "support_$userId",
                    userId = userId,
                    ownerId = ownerId,
                    userName = userName.ifBlank { "User ${userId.takeLast(4)}" },
                    lastMessage = "Support chat opened",
                    status = "open"
                ).also { inMemoryChats[it.chatId] = it }
            Result.success(fallbackChat)
        }
    }

    /**
     * Listens in real-time to messages in support_chats/{chatId}/messages
     */
    fun listenToSupportChatMessages(chatId: String): Flow<List<SupportMessageItem>> = callbackFlow {
        val database = db
        if (database == null || chatId.startsWith("local_")) {
            val list = inMemoryMessages[chatId] ?: emptyList()
            trySend(list.sortedBy { it.timestamp })
            awaitClose { }
            return@callbackFlow
        }

        val registration = database.collection(COLLECTION_SUPPORT_CHATS)
            .document(chatId)
            .collection(SUBCOLLECTION_MESSAGES)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Snapshot error for support messages in $chatId: ${error.message}")
                    val list = inMemoryMessages[chatId] ?: emptyList()
                    trySend(list.sortedBy { it.timestamp })
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        parseMessageFromDoc(doc.id, doc.data ?: emptyMap())
                    }
                    inMemoryMessages[chatId] = messages.toMutableList()
                    trySend(messages)
                }
            }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * Sends a message to support_chats/{chatId}/messages and updates lastMessage on the parent chat doc.
     */
    suspend fun sendSupportMessage(
        chatId: String,
        text: String,
        senderId: String,
        senderRole: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext Result.failure(IllegalArgumentException("Message cannot be blank"))
        val now = System.currentTimeMillis()

        val localMsg = SupportMessageItem(
            messageId = "msg_${now}_${senderId.takeLast(4)}",
            text = cleanText,
            senderId = senderId,
            senderRole = senderRole,
            timestamp = now,
            read = false
        )
        inMemoryMessages.getOrPut(chatId) { mutableListOf() }.add(localMsg)
        inMemoryChats[chatId]?.let {
            inMemoryChats[chatId] = it.copy(lastMessage = cleanText, lastUpdatedAt = now, status = "open")
        }

        val database = db ?: return@withContext Result.success(Unit)
        try {
            val chatDocRef = database.collection(COLLECTION_SUPPORT_CHATS).document(chatId)
            val msgData = hashMapOf(
                "text" to cleanText,
                "senderId" to senderId,
                "senderRole" to senderRole,
                "timestamp" to now,
                "read" to false
            )
            chatDocRef.collection(SUBCOLLECTION_MESSAGES).add(msgData).await()

            // Update parent document's lastMessage and status
            val updateData = hashMapOf<String, Any>(
                "lastMessage" to cleanText,
                "lastUpdatedAt" to now,
                "status" to "open"
            )
            chatDocRef.set(updateData, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending support message: ${e.message}", e)
            Result.success(Unit) // Local succeeded, do not break UX
        }
    }

    /**
     * Listens to all open support chats (used in Owner Console > Support Inbox).
     */
    fun listenToOpenSupportChats(): Flow<List<SupportChatItem>> = callbackFlow {
        val database = db
        if (database == null) {
            val list = inMemoryChats.values.filter { it.isOpen }.sortedByDescending { it.lastUpdatedAt }
            trySend(list)
            awaitClose { }
            return@callbackFlow
        }

        val registration = database.collection(COLLECTION_SUPPORT_CHATS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Snapshot error for open support chats: ${error.message}")
                    val list = inMemoryChats.values.filter { it.isOpen }.sortedByDescending { it.lastUpdatedAt }
                    trySend(list)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val chats = snapshot.documents.mapNotNull { doc ->
                        parseChatFromDoc(doc.id, doc.data ?: emptyMap())
                    }.filter { it.isOpen }.sortedByDescending { it.lastUpdatedAt }

                    chats.forEach { inMemoryChats[it.chatId] = it }
                    trySend(chats)
                }
            }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * Closes or reopens a support chat.
     */
    suspend fun updateSupportChatStatus(chatId: String, newStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
        inMemoryChats[chatId]?.let {
            inMemoryChats[chatId] = it.copy(status = newStatus, lastUpdatedAt = System.currentTimeMillis())
        }
        val database = db ?: return@withContext Result.success(Unit)
        try {
            database.collection(COLLECTION_SUPPORT_CHATS).document(chatId)
                .update(
                    mapOf(
                        "status" to newStatus,
                        "lastUpdatedAt" to System.currentTimeMillis()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating support chat status: ${e.message}")
            Result.success(Unit)
        }
    }

    private fun parseChatFromDoc(id: String, data: Map<String, Any>): SupportChatItem {
        return SupportChatItem(
            chatId = id,
            userId = data["userId"] as? String ?: "",
            ownerId = data["ownerId"] as? String ?: "",
            userName = data["userName"] as? String ?: "User",
            lastMessage = data["lastMessage"] as? String ?: "",
            status = data["status"] as? String ?: "open",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            lastUpdatedAt = (data["lastUpdatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun parseMessageFromDoc(id: String, data: Map<String, Any>): SupportMessageItem {
        return SupportMessageItem(
            messageId = id,
            text = data["text"] as? String ?: "",
            senderId = data["senderId"] as? String ?: "",
            senderRole = data["senderRole"] as? String ?: "user",
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            read = data["read"] as? Boolean ?: false
        )
    }
}
