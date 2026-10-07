package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.entity.MessageEntity
import com.example.data.entity.UserEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * MessageBackupStore provides resilient on-device persistence for chat messages and chat partners.
 * This guarantees that even if a user logs out, switches accounts, or the local Room database cache
 * is refreshed, all previously sent and received messages remain saved and can be restored seamlessly.
 */
class MessageBackupStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "MessageBackupStore"
        private const val PREF_NAME = "leno_message_backup_store"
        private const val KEY_MESSAGES = "backup_messages_v1"
        private const val KEY_PARTNERS = "backup_partners_v1"
        private const val MAX_MESSAGES = 1000
    }

    /**
     * Persists a message to the resilient backup store.
     */
    @Synchronized
    fun saveMessage(message: MessageEntity) {
        try {
            val list = getAllMessagesInternal().toMutableList()
            // Remove existing version of same message if present
            list.removeAll { it.messageId == message.messageId }
            list.add(message)

            // Keep within sensible storage bounds
            val trimmed = if (list.size > MAX_MESSAGES) {
                list.sortedByDescending { it.timestamp }.take(MAX_MESSAGES)
            } else {
                list
            }

            saveMessagesInternal(trimmed)
            Log.d(TAG, "Saved message ${message.messageId} to resilient store (total: ${trimmed.size})")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to backup message ${message.messageId}: ${e.message}")
        }
    }

    /**
     * Persists multiple messages in a batch.
     */
    @Synchronized
    fun saveMessages(messages: List<MessageEntity>) {
        if (messages.isEmpty()) return
        try {
            val map = getAllMessagesInternal().associateBy { it.messageId }.toMutableMap()
            messages.forEach { msg ->
                map[msg.messageId] = msg
            }
            val list = map.values.sortedByDescending { it.timestamp }.take(MAX_MESSAGES)
            saveMessagesInternal(list)
            Log.d(TAG, "Batch saved ${messages.size} messages to backup store")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to batch backup messages: ${e.message}")
        }
    }

    /**
     * Retrieves all messages relevant to a user (as sender or receiver).
     */
    fun getMessagesForUser(userId: String, aliasIds: List<String> = emptyList()): List<MessageEntity> {
        if (userId.isBlank()) return emptyList()
        val ids = (listOf(userId) + aliasIds.filter { it.isNotBlank() }).toSet()
        val all = getAllMessagesInternal()
        return all.filter { it.senderId in ids || it.receiverId in ids }
            .sortedBy { it.timestamp }
    }

    /**
     * Migrates message sender/receiver IDs when account UID links change.
     */
    @Synchronized
    fun migrateUserId(oldUid: String, newUid: String) {
        if (oldUid.isBlank() || newUid.isBlank() || oldUid == newUid) return
        try {
            val list = getAllMessagesInternal().toMutableList()
            var changed = false
            val updated = list.map { msg ->
                var m = msg
                if (m.senderId == oldUid) {
                    m = m.copy(senderId = newUid)
                    changed = true
                }
                if (m.receiverId == oldUid) {
                    m = m.copy(receiverId = newUid)
                    changed = true
                }
                m
            }
            if (changed) {
                saveMessagesInternal(updated)
                Log.d(TAG, "Migrated messages from $oldUid to $newUid in backup store")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error migrating messages in backup store: ${e.message}")
        }
    }

    /**
     * Retrieves all backed-up messages across the device.
     */
    fun getAllMessages(): List<MessageEntity> = getAllMessagesInternal()

    /**
     * Saves a chat partner's profile so it is never lost if Room users are purged.
     * Note: Current user must never be saved as a chat partner profile.
     */
    @Synchronized
    fun saveChatPartnerProfile(partner: UserEntity) {
        if (partner.userId.isBlank() || partner.isCurrentUser) return
        try {
            val partnersJson = prefs.getString(KEY_PARTNERS, "[]") ?: "[]"
            val array = JSONArray(partnersJson)
            val updated = JSONArray()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uid = obj.optString("userId", "").trim()
                val uname = obj.optString("username", "").trim()
                val lid = obj.optString("linoId", "").trim()
                val matchesUid = uid.isNotBlank() && partner.userId.isNotBlank() && uid == partner.userId
                val matchesUname = uname.isNotBlank() && partner.username.isNotBlank() && uname.equals(partner.username, ignoreCase = true)
                val matchesLid = lid.isNotBlank() && partner.linoId.isNotBlank() && lid.equals(partner.linoId, ignoreCase = true)
                if (!matchesUid && !matchesUname && !matchesLid) {
                    updated.put(obj)
                }
            }

            val pObj = JSONObject().apply {
                put("userId", partner.userId)
                put("username", partner.username)
                put("displayName", partner.displayName)
                put("bio", partner.bio)
                put("avatarUrl", partner.avatarUrl)
                put("isOnline", partner.isOnline)
                put("lastSeen", partner.lastSeen)
                put("statusMessage", partner.statusMessage)
                put("linoId", partner.linoId)
                put("isOfficial", partner.isOfficial)
                put("role", partner.role)
                put("assignedSubject", partner.assignedSubject)
            }
            updated.put(pObj)
            prefs.edit().putString(KEY_PARTNERS, updated.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save partner profile: ${e.message}")
        }
    }

    @Synchronized
    fun removeChatPartnerProfile(userId: String) {
        if (userId.isBlank()) return
        try {
            val partnersJson = prefs.getString(KEY_PARTNERS, "[]") ?: "[]"
            val array = JSONArray(partnersJson)
            val updated = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("userId") != userId) {
                    updated.put(obj)
                }
            }
            prefs.edit().putString(KEY_PARTNERS, updated.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove partner profile: ${e.message}")
        }
    }

    /**
     * Retrieves all saved chat partner profiles.
     */
    fun getAllChatPartnerProfiles(): List<UserEntity> {
        val result = mutableListOf<UserEntity>()
        try {
            val partnersJson = prefs.getString(KEY_PARTNERS, "[]") ?: "[]"
            val array = JSONArray(partnersJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    UserEntity(
                        userId = obj.optString("userId"),
                        username = obj.optString("username"),
                        displayName = obj.optString("displayName"),
                        bio = obj.optString("bio", "Connecting on Leno ✨"),
                        avatarUrl = obj.optString("avatarUrl"),
                        isOnline = obj.optBoolean("isOnline", false),
                        lastSeen = obj.optLong("lastSeen", 0L),
                        statusMessage = obj.optString("statusMessage", ""),
                        isCurrentUser = false,
                        linoId = obj.optString("linoId"),
                        isOfficial = obj.optBoolean("isOfficial", false),
                        role = obj.optString("role", "USER"),
                        assignedSubject = obj.optString("assignedSubject", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read saved partner profiles: ${e.message}")
        }
        return result
    }

    private fun getAllMessagesInternal(): List<MessageEntity> {
        val result = mutableListOf<MessageEntity>()
        try {
            val json = prefs.getString(KEY_MESSAGES, "[]") ?: "[]"
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    MessageEntity(
                        messageId = obj.optString("messageId"),
                        senderId = obj.optString("senderId"),
                        receiverId = obj.optString("receiverId"),
                        text = obj.optString("text"),
                        imageUrl = if (obj.has("imageUrl") && !obj.isNull("imageUrl")) obj.getString("imageUrl") else null,
                        audioUrl = if (obj.has("audioUrl") && !obj.isNull("audioUrl")) obj.getString("audioUrl") else null,
                        audioDurationSeconds = obj.optInt("audioDurationSeconds", 0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        status = obj.optString("status", "SENT"),
                        isSystemMessage = obj.optBoolean("isSystemMessage", false)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing backup messages: ${e.message}")
        }
        return result
    }

    private fun saveMessagesInternal(messages: List<MessageEntity>) {
        val array = JSONArray()
        for (m in messages) {
            val obj = JSONObject().apply {
                put("messageId", m.messageId)
                put("senderId", m.senderId)
                put("receiverId", m.receiverId)
                put("text", m.text)
                put("imageUrl", m.imageUrl)
                put("audioUrl", m.audioUrl)
                put("audioDurationSeconds", m.audioDurationSeconds)
                put("timestamp", m.timestamp)
                put("status", m.status)
                put("isSystemMessage", m.isSystemMessage)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }
}
