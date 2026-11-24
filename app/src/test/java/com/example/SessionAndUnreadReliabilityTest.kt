package com.example

import android.content.SharedPreferences
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.data.AppDatabase
import com.example.data.LenoRepository
import com.example.data.dao.BlockReportDao
import com.example.data.dao.CallDao
import com.example.data.dao.ClassDao
import com.example.data.dao.ClassQuestionDao
import com.example.data.dao.EnrollmentDao
import com.example.data.dao.FriendshipDao
import com.example.data.dao.LessonDao
import com.example.data.dao.MessageDao
import com.example.data.dao.NotificationDao
import com.example.data.dao.TeacherRoleDao
import com.example.data.dao.UserDao
import com.example.data.dao.UserSettingsDao
import com.example.data.entity.MessageEntity
import com.example.data.entity.UserEntity
import com.example.data.service.RemoteAccountService
import com.example.util.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SessionAndUnreadReliabilityTest {

    @Test
    fun sessionManager_preservesDeviceOwnedAccountsAcrossLogoutAndExcludesStrangers() {
        val prefs = InMemorySharedPreferences()
        val session = SessionManager(prefs)

        session.saveActiveUserId("uid_device_owner_1")
        session.saveActiveUserId("uid_device_owner_2")
        session.saveAuthCredentials("owner2@leno.chat", "secret123")

        val ownedBeforeLogout = session.getDeviceAccountIds()
        assertTrue(ownedBeforeLogout.contains("uid_device_owner_1"))
        assertTrue(ownedBeforeLogout.contains("uid_device_owner_2"))
        assertFalse(ownedBeforeLogout.contains("uid_remote_stranger"))

        // Logout clears active session & auth credentials, NOT device ownership list
        session.clearSession()
        assertNull(session.getActiveUserId())
        assertNull(session.getAuthEmail())

        val ownedAfterLogout = session.getDeviceAccountIds()
        assertEquals(setOf("uid_device_owner_1", "uid_device_owner_2"), ownedAfterLogout)

        session.removeDeviceAccountId("uid_device_owner_1")
        assertEquals(setOf("uid_device_owner_2"), session.getDeviceAccountIds())
    }

    @Test
    fun sessionManager_neverPersistsPlaintextPassword_andPurgesLegacyEntry() {
        val prefs = InMemorySharedPreferences()
        // Seed a legacy plaintext password entry
        prefs.edit().putString("active_auth_pass", "legacy_plaintext_pw").commit()

        val session = SessionManager(prefs)
        session.saveAuthCredentials("user@leno.chat", "new_plaintext_pw")

        assertEquals("user@leno.chat", session.getAuthEmail())
        assertNull("getAuthPass must always return null", session.getAuthPass())
        assertFalse("active_auth_pass must never remain in SharedPreferences", prefs.contains("active_auth_pass"))
    }

    @Test
    fun userDao_switchActiveUserAtomic_keepsSingleActiveSession() = runBlocking {
        val dao = InMemoryUserDao()
        val user1 = UserEntity(
            userId = "uid_1",
            username = "alice",
            displayName = "Alice",
            linoId = "LEN-11111111",
            isCurrentUser = true
        )
        val user2 = UserEntity(
            userId = "uid_2",
            username = "bob",
            displayName = "Bob",
            linoId = "LEN-22222222",
            isCurrentUser = false
        )
        dao.insertUsers(listOf(user1, user2))

        dao.switchActiveUserAtomic(user2)

        val current = dao.getCurrentUserSync()
        assertNotNull(current)
        assertEquals("uid_2", current?.userId)
        val all = dao.getAllUsersSync()
        assertEquals(1, all.count { it.isCurrentUser })
        assertFalse(all.first { it.userId == "uid_1" }.isCurrentUser)
        assertTrue(all.first { it.userId == "uid_2" }.isCurrentUser)
    }

    @Test
    fun userDao_upsertRemoteUsersSafely_preservesCurrentUserAndLocalSecrets() = runBlocking {
        val dao = InMemoryUserDao()
        val localActive = UserEntity(
            userId = "uid_me",
            username = "me_user",
            displayName = "Me",
            linoId = "LEN-10101010",
            isCurrentUser = true,
            passwordHash = "local_hash_abc",
            passwordSalt = "local_salt_xyz",
            recoveryCode = "REC-1234-5678"
        )
        dao.insertUser(localActive)

        // Simulated remote sync containing our profile (without isCurrentUser or local secrets)
        // plus a user created on another APK device (mistakenly marked isCurrentUser=true if raw)
        val remoteMe = UserEntity(
            userId = "uid_me",
            username = "me_user",
            displayName = "Me Updated",
            linoId = "LEN-10101010",
            isCurrentUser = false,
            password = "should_be_stripped"
        )
        val remoteOtherApkUser = UserEntity(
            userId = "uid_other_device",
            username = "friend_remote",
            displayName = "Friend On Other Phone",
            linoId = "LEN-99998888",
            isCurrentUser = true,
            password = "should_be_stripped"
        )

        dao.upsertRemoteUsersSafely(listOf(remoteMe, remoteOtherApkUser), activeUserId = "uid_me")

        val meAfter = dao.getUserByIdSync("uid_me")
        val otherAfter = dao.getUserByIdSync("uid_other_device")
        assertNotNull(meAfter)
        assertNotNull(otherAfter)
        assertTrue("Active local user must remain current after remote sync", meAfter!!.isCurrentUser)
        assertEquals("Me Updated", meAfter.displayName)
        assertEquals("local_hash_abc", meAfter.passwordHash)
        assertEquals("local_salt_xyz", meAfter.passwordSalt)
        assertEquals("REC-1234-5678", meAfter.recoveryCode)
        assertEquals("", meAfter.password)

        assertFalse("Remote user must never become active local user", otherAfter!!.isCurrentUser)
        assertEquals("", otherAfter.password)
    }

    @Test
    fun repository_insertMessagesMonotonic_neverDowngradesReadOrDeliveredState() = runBlocking {
        val userDao = InMemoryUserDao()
        val messageDao = InMemoryMessageDao()
        val repo = LenoRepository(database = FakeAppDatabase(userDao, messageDao), context = null)

        val initiallyRead = MessageEntity(
            messageId = "msg_1",
            senderId = "uid_partner",
            receiverId = "uid_me",
            text = "Hello",
            timestamp = 1000L,
            status = "READ",
            read = true
        )
        val initiallyDelivered = MessageEntity(
            messageId = "msg_2",
            senderId = "uid_me",
            receiverId = "uid_partner",
            text = "How are you?",
            timestamp = 2000L,
            status = "DELIVERED",
            read = false
        )
        repo.insertMessagesMonotonic(listOf(initiallyRead, initiallyDelivered))

        // Stale Firestore snapshot arrives claiming msg_1 is SENT/unread and msg_2 is SENT,
        // plus msg_3 arrives with lowercase "read"
        val staleRemote1 = initiallyRead.copy(status = "SENT", read = false)
        val staleRemote2 = initiallyDelivered.copy(status = "SENT", read = false)
        val lowercaseRead3 = MessageEntity(
            messageId = "msg_3",
            senderId = "uid_partner",
            receiverId = "uid_me",
            text = "Ping",
            timestamp = 3000L,
            status = "read",
            read = false
        )
        repo.insertMessagesMonotonic(listOf(staleRemote1, staleRemote2, lowercaseRead3))

        val stored1 = messageDao.getMessageByIdSync("msg_1")!!
        val stored2 = messageDao.getMessageByIdSync("msg_2")!!
        val stored3 = messageDao.getMessageByIdSync("msg_3")!!

        assertEquals("READ", stored1.status)
        assertTrue(stored1.read)

        assertEquals("DELIVERED", stored2.status)
        assertFalse(stored2.read)

        assertEquals("READ", stored3.status)
        assertTrue(stored3.read)
    }

    @Test
    fun repository_unreadCountsAndSenderIds_suppressFlickerWhenChatIsOpen() = runBlocking {
        val userDao = InMemoryUserDao()
        val messageDao = InMemoryMessageDao()
        val me = UserEntity(userId = "uid_me", username = "me", displayName = "Me", linoId = "LEN-10000001", isCurrentUser = true)
        val p1 = UserEntity(userId = "uid_p1", username = "p1", displayName = "Partner One", linoId = "LEN-10000002")
        val p2 = UserEntity(userId = "uid_p2", username = "p2", displayName = "Partner Two", linoId = "LEN-10000003")
        userDao.insertUsers(listOf(me, p1, p2))

        val repo = LenoRepository(database = FakeAppDatabase(userDao, messageDao), context = null)

        repo.insertMessagesMonotonic(
            listOf(
                MessageEntity("m1", senderId = "uid_p1", receiverId = "uid_me", text = "Hi 1", timestamp = 100L, status = "SENT", read = false),
                MessageEntity("m2", senderId = "uid_p1", receiverId = "uid_me", text = "Hi 2", timestamp = 200L, status = "DELIVERED", read = false),
                MessageEntity("m3", senderId = "uid_p2", receiverId = "uid_me", text = "Yo", timestamp = 300L, status = "SENT", read = false)
            )
        )

        assertEquals(2, repo.getUnreadCount("uid_me", "uid_p1").first())
        assertEquals(1, repo.getUnreadCount("uid_me", "uid_p2").first())
        assertEquals(setOf("uid_p1", "uid_p2"), repo.getUnreadSenderIds("uid_me").first())

        // Opening chat with p1 must immediately suppress p1's unread badge & filter ID while keeping p2's
        repo.setActiveChatPartnerId("uid_p1")
        assertEquals(0, repo.getUnreadCount("uid_me", "uid_p1").first())
        assertEquals(0, repo.getCachedUnreadCount("uid_p1"))
        assertEquals(setOf("uid_p2"), repo.getUnreadSenderIds("uid_me").first())

        // Marking as read and closing chat keeps count at 0 without re-appearing
        messageDao.markMessagesAsReadWithAliases(listOf("uid_me", "me", "LEN-10000001"), listOf("uid_p1", "p1", "LEN-10000002"))
        repo.setActiveChatPartnerId(null)
        assertEquals(0, repo.getUnreadCount("uid_me", "uid_p1").first())
        assertEquals(setOf("uid_p2"), repo.getUnreadSenderIds("uid_me").first())
    }

    @Test
    fun remoteAccountService_crossDeviceDocParsingAndDeduplicationWorksForOtherApkUsers() {
        val service = RemoteAccountService()
        val remoteDoc = service.docToUser(
            data = mapOf(
                "uid" to "firebase_uid_other_device_99",
                "username" to "khadija_other_apk",
                "displayName" to "Khadija Other Device",
                "lenId" to "LEN-77665544",
                "searchable" to true,
                "updatedAt" to 1700000500000L
            ),
            docId = "firebase_uid_other_device_99"
        )
        assertNotNull(remoteDoc)
        val deduped = service.deduplicateCanonicalUsers(listOf("firebase_uid_other_device_99" to remoteDoc!!))
        assertEquals(1, deduped.size)
        assertEquals("khadija_other_apk", deduped.first().username)
        assertEquals("LEN-77665544", deduped.first().linoId)
    }

    private class FakeAppDatabase(
        private val fakeUserDao: UserDao,
        private val fakeMessageDao: MessageDao
    ) : AppDatabase() {
        override fun userDao(): UserDao = fakeUserDao
        override fun messageDao(): MessageDao = fakeMessageDao
        override fun friendshipDao(): FriendshipDao = stubDao()
        override fun blockReportDao(): BlockReportDao = stubDao()
        override fun userSettingsDao(): UserSettingsDao = stubDao()
        override fun notificationDao(): NotificationDao = stubDao()
        override fun callDao(): CallDao = stubDao()
        override fun classDao(): ClassDao = stubDao()
        override fun enrollmentDao(): EnrollmentDao = stubDao()
        override fun teacherRoleDao(): TeacherRoleDao = stubDao()
        override fun lessonDao(): LessonDao = stubDao()
        override fun classQuestionDao(): ClassQuestionDao = stubDao()
        @Deprecated("Deprecated in RoomDatabase")
        override fun clearAllTables() {}
        override fun createInvalidationTracker(): InvalidationTracker {
            throw UnsupportedOperationException("Not needed in unit test")
        }
        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): SupportSQLiteOpenHelper {
            throw UnsupportedOperationException("Not needed in unit test")
        }

        private inline fun <reified T : Any> stubDao(): T {
            @Suppress("UNCHECKED_CAST")
            return Proxy.newProxyInstance(
                T::class.java.classLoader,
                arrayOf(T::class.java)
            ) { _, method, _ ->
                if (method.returnType == Flow::class.java) MutableStateFlow(emptyList<Any>()) else null
            } as T
        }
    }

    private class InMemoryUserDao : UserDao {
        private val usersFlow = MutableStateFlow<List<UserEntity>>(emptyList())

        override fun getAllUsers(): Flow<List<UserEntity>> = usersFlow
        override suspend fun getAllUsersSync(): List<UserEntity> = usersFlow.value
        override fun getCurrentUser(): Flow<UserEntity?> = usersFlow.map { it.firstOrNull { u -> u.isCurrentUser } }
        override suspend fun getCurrentUserSync(): UserEntity? = usersFlow.value.firstOrNull { it.isCurrentUser }
        override fun getUserById(userId: String): Flow<UserEntity?> = usersFlow.map { it.firstOrNull { u -> u.userId == userId } }
        override suspend fun getUserByIdSync(userId: String): UserEntity? = usersFlow.value.firstOrNull { it.userId == userId }
        override suspend fun getUserByUsernameSync(username: String): UserEntity? = usersFlow.value.firstOrNull { it.username.equals(username, ignoreCase = true) }
        override suspend fun getUserByLinoIdSync(linoId: String): UserEntity? = usersFlow.value.firstOrNull { it.linoId.equals(linoId, ignoreCase = true) }
        override suspend fun getAllUsersByLinoIdSync(linoId: String): List<UserEntity> = usersFlow.value.filter { it.linoId.equals(linoId, ignoreCase = true) }
        override fun getAllUsersByLinoId(linoId: String): Flow<List<UserEntity>> = usersFlow.map { list -> list.filter { it.linoId.equals(linoId, ignoreCase = true) } }
        override suspend fun getUserByDisplayNameSync(displayName: String): UserEntity? = usersFlow.value.firstOrNull { it.displayName.equals(displayName, ignoreCase = true) }
        override suspend fun getUserByEmailSync(email: String): UserEntity? = usersFlow.value.firstOrNull { it.email.equals(email, ignoreCase = true) }
        override suspend fun getUserByGoogleEmailSync(googleEmail: String): UserEntity? = usersFlow.value.firstOrNull { it.googleEmail.equals(googleEmail, ignoreCase = true) }
        override suspend fun getUserByRecoveryEmailSync(recoveryEmail: String): UserEntity? = usersFlow.value.firstOrNull { it.recoveryEmail.equals(recoveryEmail, ignoreCase = true) }
        override suspend fun getUserByIdentifierSync(identifier: String): UserEntity? = usersFlow.value.firstOrNull {
            it.userId == identifier || it.username.equals(identifier, true) || it.linoId.equals(identifier, true)
        }
        override suspend fun getUserByPhoneSync(rawPhone: String, normalizedPhone: String): UserEntity? = null
        override fun searchUsers(query: String, currentUserId: String): Flow<List<UserEntity>> = usersFlow.map { list ->
            list.filter { it.userId != currentUserId && (it.username.contains(query, true) || it.displayName.contains(query, true) || it.linoId.contains(query, true)) }
        }
        override suspend fun searchUsersSync(query: String, currentUserId: String): List<UserEntity> =
            usersFlow.value.filter { !it.isCurrentUser && it.userId != currentUserId && (it.username.contains(query, true) || it.displayName.contains(query, true) || it.linoId.contains(query, true)) }
        override fun getContactsForUser(userId: String): Flow<List<UserEntity>> = MutableStateFlow(emptyList())
        override fun getPendingRequestUsers(userId: String): Flow<List<UserEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertUser(user: UserEntity) {
            val map = usersFlow.value.associateBy { it.userId }.toMutableMap()
            map[user.userId] = user
            usersFlow.value = map.values.toList()
        }
        override suspend fun insertUsers(users: List<UserEntity>) {
            val map = usersFlow.value.associateBy { it.userId }.toMutableMap()
            users.forEach { map[it.userId] = it }
            usersFlow.value = map.values.toList()
        }
        override suspend fun updateUser(user: UserEntity) = insertUser(user)
        override suspend fun clearCurrentUser() {
            usersFlow.value = usersFlow.value.map { it.copy(isCurrentUser = false) }
        }
        override suspend fun setCurrentUser(userId: String) {
            usersFlow.value = usersFlow.value.map { it.copy(isCurrentUser = it.userId == userId) }
        }
        override suspend fun updateOnlineStatus(userId: String, isOnline: Boolean, lastSeen: Long) {
            usersFlow.value = usersFlow.value.map { if (it.userId == userId) it.copy(isOnline = isOnline, lastSeen = lastSeen) else it }
        }
        override suspend fun deleteUser(userId: String) { usersFlow.value = usersFlow.value.filterNot { it.userId == userId } }
        override suspend fun deleteNonOfficialUsers() {}
        override suspend fun purgeDemoAccounts() {}
        override suspend fun deleteDemoTeachers() {}
        override suspend fun updateUserRole(userId: String, role: String, assignedSubject: String) {}
        override suspend fun updateUserTeacherStatus(userId: String, teacherStatus: String, teacherSubject: String, updatedAt: Long) {}
        override suspend fun approveUserAsTeacher(userId: String, teacherSubject: String, updatedAt: Long) {}
        override suspend fun clearDemoAvatars() {}
        override suspend fun setUserRestricted(userId: String, restricted: Boolean, updatedAt: Long) {}
        override suspend fun markUserVerified(userId: String, updatedAt: Long) {}
        override fun getTeachers(): Flow<List<UserEntity>> = MutableStateFlow(emptyList())
        override fun getStudents(): Flow<List<UserEntity>> = MutableStateFlow(emptyList())
    }

    private class InMemoryMessageDao : MessageDao {
        private val messagesFlow = MutableStateFlow<List<MessageEntity>>(emptyList())

        override fun getMessagesBetweenUsers(user1Id: String, user2Id: String): Flow<List<MessageEntity>> =
            messagesFlow.map { list -> list.filter { (it.senderId == user1Id && it.receiverId == user2Id) || (it.senderId == user2Id && it.receiverId == user1Id) } }
        override fun getMessagesBetweenUserAliases(user1Ids: List<String>, user2Ids: List<String>): Flow<List<MessageEntity>> =
            messagesFlow.map { list -> list.filter { (it.senderId in user1Ids && it.receiverId in user2Ids) || (it.senderId in user2Ids && it.receiverId in user1Ids) } }
        override suspend fun getMessagesBetweenUserAliasesSync(user1Ids: List<String>, user2Ids: List<String>): List<MessageEntity> =
            messagesFlow.value.filter { (it.senderId in user1Ids && it.receiverId in user2Ids) || (it.senderId in user2Ids && it.receiverId in user1Ids) }
        override suspend fun getMessagesBetweenUsersSync(user1Id: String, user2Id: String): List<MessageEntity> =
            messagesFlow.value.filter { (it.senderId == user1Id && it.receiverId == user2Id) || (it.senderId == user2Id && it.receiverId == user1Id) }
        override fun getAllMessagesForUser(currentUserId: String): Flow<List<MessageEntity>> =
            messagesFlow.map { list -> list.filter { it.senderId == currentUserId || it.receiverId == currentUserId } }
        override suspend fun getAllMessagesForUserSync(currentUserId: String): List<MessageEntity> =
            messagesFlow.value.filter { it.senderId == currentUserId || it.receiverId == currentUserId }
        override suspend fun getAllMessagesForUserAliasesSync(userAliases: List<String>): List<MessageEntity> =
            messagesFlow.value.filter { it.senderId in userAliases || it.receiverId in userAliases }
        override fun getChatPartnersForUserAliases(userAliases: List<String>): Flow<List<UserEntity>> = MutableStateFlow(emptyList())
        override fun getChatPartnerIdsForUserAliases(userAliases: List<String>): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun getChatPartnerIdsForUserAliasesSync(userAliases: List<String>): List<String> = emptyList()
        override fun getLastMessageBetweenAliases(user1Ids: List<String>, user2Ids: List<String>): Flow<MessageEntity?> = MutableStateFlow(null)
        override suspend fun getLastMessageBetweenAliasesSync(user1Ids: List<String>, user2Ids: List<String>): MessageEntity? = null
        override fun getUnreadCountFromUser(currentUserId: String, otherUserId: String): Flow<Int> =
            getUnreadCountFromUserAliases(listOf(currentUserId), listOf(otherUserId))
        override fun getUnreadCountFromUserAliases(currentUserIds: List<String>, otherUserIds: List<String>): Flow<Int> =
            messagesFlow.map { list ->
                list.count { it.receiverId in currentUserIds && it.senderId in otherUserIds && !it.status.equals("READ", true) && !it.read }
            }
        override fun getTotalUnreadCountForUserAliases(currentUserIds: List<String>): Flow<Int> =
            messagesFlow.map { list ->
                list.count { it.receiverId in currentUserIds && it.senderId !in currentUserIds && !it.status.equals("READ", true) && !it.read }
            }
        override fun getUnreadSenderIdsForUserAliases(currentUserIds: List<String>): Flow<List<String>> =
            messagesFlow.map { list ->
                list.filter { it.receiverId in currentUserIds && it.senderId !in currentUserIds && !it.status.equals("READ", true) && !it.read }
                    .map { it.senderId }
                    .distinct()
            }
        override suspend fun insertMessage(message: MessageEntity) {
            val map = messagesFlow.value.associateBy { it.messageId }.toMutableMap()
            map[message.messageId] = message
            messagesFlow.value = map.values.toList()
        }
        override suspend fun insertMessages(messages: List<MessageEntity>) {
            val map = messagesFlow.value.associateBy { it.messageId }.toMutableMap()
            messages.forEach { map[it.messageId] = it }
            messagesFlow.value = map.values.toList()
        }
        override suspend fun markMessagesAsRead(currentUserId: String, otherUserId: String) =
            markMessagesAsReadWithAliases(listOf(currentUserId), listOf(otherUserId))
        override suspend fun markSentMessagesAsRead(senderId: String, receiverId: String) {}
        override suspend fun markMessagesAsReadWithAliases(currentUserIds: List<String>, otherUserIds: List<String>) {
            messagesFlow.value = messagesFlow.value.map {
                if (it.receiverId in currentUserIds && it.senderId in otherUserIds) it.copy(status = "READ", read = true) else it
            }
        }
        override suspend fun getMessageByIdSync(messageId: String): MessageEntity? = messagesFlow.value.firstOrNull { it.messageId == messageId }
        override suspend fun getMessagesByIdsSync(ids: List<String>): List<MessageEntity> = messagesFlow.value.filter { it.messageId in ids }
        override suspend fun getPendingMessagesForUserSync(currentUserId: String): List<MessageEntity> = emptyList()
        override suspend fun updateMessageStatus(messageId: String, status: String) {
            messagesFlow.value = messagesFlow.value.map {
                if (it.messageId == messageId) it.copy(status = if (status.equals("READ", true)) "READ" else status, read = it.read || status.equals("READ", true)) else it
            }
        }
        override suspend fun updateMessageReadStatus(messageId: String, read: Boolean) {
            messagesFlow.value = messagesFlow.value.map {
                if (it.messageId == messageId) it.copy(read = read, status = if (read) "READ" else it.status) else it
            }
        }
        override suspend fun migrateSenderId(oldUid: String, newUid: String) {}
        override suspend fun migrateReceiverId(oldUid: String, newUid: String) {}
        override suspend fun deleteConversation(user1Id: String, user2Id: String) {}
        override suspend fun deleteMessageById(messageId: String) {}
        override suspend fun clearMessagesForUser(userId: String) {}
        override suspend fun deleteCorruptedSelfMessages() {}
        override suspend fun deleteAllMessages() { messagesFlow.value = emptyList() }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val map = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                for ((k, v) in pending) {
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }
    }
}
