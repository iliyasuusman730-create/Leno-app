package com.example.data.model

/**
 * Model representing a support chat session between a user and the app owner/support team.
 * Collection: support_chats
 * Fields: userId, ownerId, userName, lastMessage, status (open/closed), createdAt
 */
data class SupportChatItem(
    val chatId: String = "",
    val userId: String = "",
    val ownerId: String = "",
    val userName: String = "",
    val lastMessage: String = "",
    val status: String = "open", // "open" or "closed"
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
) {
    val isOpen: Boolean
        get() = status.equals("open", ignoreCase = true)
}

/**
 * Model representing an individual message in support_chats/{chatId}/messages
 * Fields: text, senderId, senderRole, timestamp, read
 */
data class SupportMessageItem(
    val messageId: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderRole: String = "user", // "user", "owner", or "admin"
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false
) {
    val isFromOwner: Boolean
        get() = senderRole.equals("owner", ignoreCase = true) || senderRole.equals("admin", ignoreCase = true)
}
