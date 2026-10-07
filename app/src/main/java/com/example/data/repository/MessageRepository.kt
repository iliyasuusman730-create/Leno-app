package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.model.Message
import com.example.data.service.FirebaseStorageService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Repository layer to interact with Cloud Firestore for real-time chat data storage,
 * live streams, sending messages, read receipts, and message lifecycle management.
 */
class MessageRepository(
    firestoreInstance: FirebaseFirestore? = null,
    private val storageService: FirebaseStorageService = FirebaseStorageService()
) {
    private val firestore: FirebaseFirestore? = firestoreInstance ?: try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    }

    private val inMemoryFallback = mutableListOf<Message>()

    /**
     * Emits real-time stream of messages from the specified Firestore collection
     * using a snapshot listener, ordered chronologically by timestamp.
     */
    fun getMessagesFlow(collectionName: String = "messages"): Flow<List<Message>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(synchronized(inMemoryFallback) { inMemoryFallback.sortedBy { it.timestamp } })
            awaitClose { }
            return@callbackFlow
        }

        val query = db.collection(collectionName)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(200)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(synchronized(inMemoryFallback) { inMemoryFallback.sortedBy { it.timestamp } })
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Message.fromMap(doc.id, data)
                }
                trySend(list)
            }
        }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * Emits real-time stream of messages filtered by chatId.
     */
    fun getChatMessagesFlow(chatId: String, collectionName: String = "messages"): Flow<List<Message>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(synchronized(inMemoryFallback) {
                inMemoryFallback.filter { it.chatId == chatId }.sortedBy { it.timestamp }
            })
            awaitClose { }
            return@callbackFlow
        }

        val query = db.collection(collectionName)
            .whereEqualTo("chatId", chatId)
            .limit(200)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(synchronized(inMemoryFallback) {
                    inMemoryFallback.filter { it.chatId == chatId }.sortedBy { it.timestamp }
                })
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Message.fromMap(doc.id, data)
                }.sortedBy { it.timestamp }
                trySend(list)
            }
        }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * Sends a message and persists it to Cloud Firestore in real time.
     */
    suspend fun sendMessage(
        message: Message,
        collectionName: String = "messages"
    ): Result<Message> = withContext(Dispatchers.IO) {
        try {
            val messageId = if (message.id.isBlank()) UUID.randomUUID().toString() else message.id
            val messageToSend = message.copy(id = messageId)

            val db = firestore
            if (db != null) {
                db.collection(collectionName)
                    .document(messageId)
                    .set(messageToSend.toMap(), SetOptions.merge())
                    .await()
            }

            synchronized(inMemoryFallback) {
                val index = inMemoryFallback.indexOfFirst { it.id == messageId }
                if (index != -1) {
                    inMemoryFallback[index] = messageToSend
                } else {
                    inMemoryFallback.add(messageToSend)
                }
            }

            Result.success(messageToSend)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convenience method to send a text or image message with the specified parameters.
     */
    suspend fun sendMessage(
        text: String,
        senderId: String,
        senderName: String = "",
        chatId: String = "",
        receiverId: String = "",
        imageUrl: String? = null,
        collectionName: String = "messages"
    ): Result<Message> {
        val msg = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = senderId,
            senderName = senderName,
            receiverId = receiverId,
            text = text,
            imageUrl = imageUrl,
            timestamp = System.currentTimeMillis(),
            read = false,
            status = "SENT"
        )
        return sendMessage(msg, collectionName)
    }

    /**
     * Uploads an image from a device Uri to Firebase Cloud Storage.
     * Returns the Firebase Storage download URL.
     */
    suspend fun uploadImage(uri: Uri, context: Context, chatId: String = ""): Result<String> {
        return storageService.uploadChatImage(uri, context, chatId)
    }

    /**
     * Uploads an image File to Firebase Cloud Storage.
     * Returns the Firebase Storage download URL.
     */
    suspend fun uploadImageFile(file: File, chatId: String = ""): Result<String> {
        return storageService.uploadChatImageFile(file, chatId)
    }

    /**
     * Sends a chat message with an image attached using Firebase Storage.
     */
    suspend fun sendMessageWithImage(
        imageUri: Uri,
        context: Context,
        senderId: String,
        senderName: String = "",
        chatId: String = "",
        receiverId: String = "",
        caption: String = "",
        collectionName: String = "messages"
    ): Result<Message> {
        val uploadResult = uploadImage(imageUri, context, chatId)
        val imageUrl = uploadResult.getOrElse {
            return Result.failure(it)
        }

        val msg = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = senderId,
            senderName = senderName,
            receiverId = receiverId,
            text = caption.ifBlank { "📷 Photo" },
            imageUrl = imageUrl,
            timestamp = System.currentTimeMillis(),
            read = false,
            status = "SENT"
        )
        return sendMessage(msg, collectionName)
    }

    /**
     * Updates message read status to true with READ status.
     */
    suspend fun markMessageAsRead(
        messageId: String,
        collectionName: String = "messages"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = firestore
            if (db != null) {
                db.collection(collectionName)
                    .document(messageId)
                    .update(
                        mapOf(
                            "read" to true,
                            "status" to "READ"
                        )
                    )
                    .await()
            }

            synchronized(inMemoryFallback) {
                val index = inMemoryFallback.indexOfFirst { it.id == messageId }
                if (index != -1) {
                    inMemoryFallback[index] = inMemoryFallback[index].copy(read = true, status = "READ")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marks all unread messages received by currentUserId as read in batch.
     */
    suspend fun markAllMessagesAsRead(
        currentUserId: String,
        chatId: String? = null,
        collectionName: String = "messages"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: return@withContext Result.success(Unit)

            var query: Query = db.collection(collectionName)
                .whereEqualTo("read", false)

            if (!chatId.isNullOrBlank()) {
                query = query.whereEqualTo("chatId", chatId)
            }

            val snapshot = query.get().await()
            val unreadForUser = snapshot.documents.filter { doc ->
                val senderId = doc.getString("senderId") ?: ""
                val receiverId = doc.getString("receiverId") ?: ""
                (receiverId == currentUserId) || (senderId.isNotBlank() && senderId != currentUserId)
            }

            if (unreadForUser.isNotEmpty()) {
                val batch = db.batch()
                unreadForUser.forEach { doc ->
                    batch.update(doc.reference, mapOf("read" to true, "status" to "READ"))
                }
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a message from Cloud Firestore.
     */
    suspend fun deleteMessage(
        messageId: String,
        collectionName: String = "messages"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = firestore
            if (db != null) {
                db.collection(collectionName)
                    .document(messageId)
                    .delete()
                    .await()
            }

            synchronized(inMemoryFallback) {
                inMemoryFallback.removeAll { it.id == messageId }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads all messages from the specified collection once (one-shot fetch).
     */
    suspend fun readMessagesOnce(collectionName: String = "messages"): Result<List<Message>> = withContext(Dispatchers.IO) {
        try {
            val db = firestore
            if (db == null) {
                return@withContext Result.success(synchronized(inMemoryFallback) { inMemoryFallback.sortedBy { it.timestamp } })
            }

            val snapshot = db.collection(collectionName)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(200)
                .get()
                .await()

            val messages = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                Message.fromMap(doc.id, data)
            }

            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
