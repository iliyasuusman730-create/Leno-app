package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date

/**
 * Represents a chat message document stored in Cloud Firestore for real-time chat.
 */
@IgnoreExtraProperties
data class Message(
    @DocumentId
    var id: String = "",
    var chatId: String = "",
    var senderId: String = "",
    var senderName: String = "",
    var receiverId: String = "",
    var text: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var read: Boolean = false,
    var status: String = "SENT", // "SENT", "DELIVERED", "READ", "FAILED"
    var imageUrl: String? = null,
    var audioUrl: String? = null
) {
    /**
     * Converts this Message instance to a map representation for Cloud Firestore storage.
     */
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "id" to id,
            "chatId" to chatId,
            "senderId" to senderId,
            "senderName" to senderName,
            "receiverId" to receiverId,
            "text" to text,
            "timestamp" to timestamp,
            "read" to read,
            "status" to status
        )
        if (!imageUrl.isNullOrBlank()) map["imageUrl"] = imageUrl
        if (!audioUrl.isNullOrBlank()) map["audioUrl"] = audioUrl
        return map
    }

    companion object {
        /**
         * Safely converts Firestore document snapshot map data into a Message entity.
         */
        fun fromMap(id: String, map: Map<String, Any?>): Message {
            val ts = when (val rawTs = map["timestamp"]) {
                is Timestamp -> rawTs.toDate().time
                is Date -> rawTs.time
                is Number -> rawTs.toLong()
                else -> System.currentTimeMillis()
            }
            val isRead = (map["read"] as? Boolean) == true || (map["status"] as? String) == "READ"

            return Message(
                id = id,
                chatId = map["chatId"] as? String ?: "",
                senderId = map["senderId"] as? String ?: "",
                senderName = map["senderName"] as? String ?: "",
                receiverId = map["receiverId"] as? String ?: "",
                text = map["text"] as? String ?: "",
                timestamp = ts,
                read = isRead,
                status = map["status"] as? String ?: (if (isRead) "READ" else "SENT"),
                imageUrl = map["imageUrl"] as? String,
                audioUrl = map["audioUrl"] as? String
            )
        }
    }
}
