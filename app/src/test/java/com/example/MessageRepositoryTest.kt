package com.example

import com.example.data.model.Message
import com.example.data.repository.MessageRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryTest {

    @Test
    fun message_modelSerializationAndDeserialization() {
        val original = Message(
            id = "msg_123",
            chatId = "chat_abc",
            senderId = "user_1",
            senderName = "Alice",
            receiverId = "user_2",
            text = "Hello Firestore!",
            timestamp = 1700000000000L,
            read = true,
            status = "READ"
        )

        val map = original.toMap()
        assertEquals("msg_123", map["id"])
        assertEquals("chat_abc", map["chatId"])
        assertEquals("user_1", map["senderId"])
        assertEquals("Alice", map["senderName"])
        assertEquals("user_2", map["receiverId"])
        assertEquals("Hello Firestore!", map["text"])
        assertEquals(1700000000000L, map["timestamp"])
        assertEquals(true, map["read"])
        assertEquals("READ", map["status"])

        val reconstructed = Message.fromMap("msg_123", map)
        assertEquals(original.id, reconstructed.id)
        assertEquals(original.chatId, reconstructed.chatId)
        assertEquals(original.senderId, reconstructed.senderId)
        assertEquals(original.senderName, reconstructed.senderName)
        assertEquals(original.receiverId, reconstructed.receiverId)
        assertEquals(original.text, reconstructed.text)
        assertEquals(original.timestamp, reconstructed.timestamp)
        assertTrue(reconstructed.read)
        assertEquals("READ", reconstructed.status)
    }

    @Test
    fun messageRepository_sendAndRetrieveMessages() = runBlocking {
        val repo = MessageRepository(firestoreInstance = null) // Uses fallback

        val result = repo.sendMessage(
            text = "Real-time message test",
            senderId = "sender_101",
            senderName = "Bob",
            chatId = "room_1",
            receiverId = "user_202"
        )

        assertTrue(result.isSuccess)
        val sentMessage = result.getOrNull()
        assertNotNull(sentMessage)
        assertEquals("Real-time message test", sentMessage?.text)
        assertEquals("sender_101", sentMessage?.senderId)
        assertFalse(sentMessage?.read ?: true)

        val messages = repo.readMessagesOnce().getOrNull()
        assertNotNull(messages)
        assertEquals(1, messages?.size)
        assertEquals(sentMessage?.id, messages?.first()?.id)

        // Test stream flow
        val flowMessages = repo.getMessagesFlow().first()
        assertEquals(1, flowMessages.size)
        assertEquals("Real-time message test", flowMessages[0].text)

        // Mark as read
        repo.markMessageAsRead(sentMessage!!.id)
        val updatedMessages = repo.readMessagesOnce().getOrNull()
        assertTrue(updatedMessages?.first()?.read == true)
        assertEquals("READ", updatedMessages?.first()?.status)

        // Delete message
        repo.deleteMessage(sentMessage.id)
        val afterDelete = repo.readMessagesOnce().getOrNull()
        assertTrue(afterDelete?.isEmpty() == true)
    }

    @Test
    fun messageRepository_sendMessageWithImageUrl() = runBlocking {
        val repo = MessageRepository(firestoreInstance = null) // Fallback storage

        val result = repo.sendMessage(
            text = "Check out this photo!",
            senderId = "sender_101",
            senderName = "Bob",
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/app.appspot.com/o/chat_images%2Fsample.jpg?alt=media"
        )

        assertTrue(result.isSuccess)
        val sentMessage = result.getOrNull()
        assertNotNull(sentMessage)
        assertEquals("Check out this photo!", sentMessage?.text)
        assertNotNull(sentMessage?.imageUrl)
        assertTrue(sentMessage?.imageUrl?.contains("firebasestorage.googleapis.com") == true)
    }
}
