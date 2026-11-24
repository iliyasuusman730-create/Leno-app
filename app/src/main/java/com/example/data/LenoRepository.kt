package com.example.data

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.entity.BlockReportEntity
import com.example.data.entity.CallEntity
import com.example.data.entity.ClassEntity
import com.example.data.entity.ClassQuestionEntity
import com.example.data.entity.EnrollmentEntity
import com.example.data.entity.FriendshipEntity
import com.example.data.entity.LessonEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.NotificationEntity
import com.example.data.entity.TeacherRoleEntity
import com.example.data.entity.UserEntity
import com.example.data.entity.UserSettingsEntity
import com.example.data.model.TeacherApplicationItem
import com.example.data.model.SupportChatItem
import com.example.data.model.SupportMessageItem
import com.example.data.service.SupportChatService
import com.example.data.repository.MessageRepository
import com.example.data.service.FriendService
import com.example.data.service.RemoteAccountService
import com.example.util.AccountBackupStore
import com.example.util.AuthSecurity
import com.example.util.LessonProgressManager
import com.example.util.NotificationHelper
import com.example.util.PhoneUtils
import com.example.util.SecurityEvent
import com.example.util.SessionManager
import com.example.util.TimeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LenoRepository(
    private val database: AppDatabase,
    private val context: Context? = null,
    val remoteAccountService: RemoteAccountService = RemoteAccountService()
) {
    private val userDao = database.userDao()
    private val messageDao = database.messageDao()
    private val friendshipDao = database.friendshipDao()
    private val blockReportDao = database.blockReportDao()
    private val settingsDao = database.userSettingsDao()
    private val notificationDao = database.notificationDao()
    private val callDao = database.callDao()
    private val classDao = database.classDao()
    private val enrollmentDao = database.enrollmentDao()
    private val teacherRoleDao = database.teacherRoleDao()
    private val lessonDao = database.lessonDao()
    private val classQuestionDao = database.classQuestionDao()

    val sessionManager = context?.let { SessionManager(it) }
    val messageRepository = MessageRepository()
    val accountBackupStore = context?.let { AccountBackupStore(it) }
    val messageBackupStore = context?.let { com.example.util.MessageBackupStore(it) }
    private val friendService = FriendService()
    val supportChatService: SupportChatService = SupportChatService()
    private val syncChatsMutex = Mutex()
    private val deviceAccountsVersion = MutableStateFlow(0)

    @Volatile
    var isAuthTransitionInProgress: Boolean = false

    val activeChatPartnerIdFlow = MutableStateFlow<String?>(null)
    var activeChatPartnerIdSupplier: (() -> String?)? = null

    fun setActiveChatPartnerId(partnerId: String?) {
        activeChatPartnerIdFlow.value = partnerId
        if (!partnerId.isNullOrBlank()) {
            unreadCountCache[partnerId] = 0
        }
    }

    val unreadCountCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun getCachedUnreadCount(otherUserId: String): Int = unreadCountCache[otherUserId] ?: 0

    private fun recordDeviceOwnedAccount(user: UserEntity) {
        if (user.userId.isBlank() || user.isOfficial || user.userId == OFFICIAL_LENO_ID) return
        sessionManager?.addDeviceAccountId(user.userId)
        accountBackupStore?.saveAccount(user.copy(isCurrentUser = false))
        deviceAccountsVersion.value = deviceAccountsVersion.value + 1
    }

    private fun messageStatusRank(status: String, read: Boolean): Int {
        if (read || status.equals("READ", ignoreCase = true)) return 4
        return when (status.uppercase()) {
            "DELIVERED" -> 3
            "SENT" -> 2
            "SENDING" -> 1
            else -> 0
        }
    }

    internal suspend fun insertMessagesMonotonic(messages: List<MessageEntity>, forceRead: Boolean = false) {
        if (messages.isEmpty()) return
        val existingMap = mutableMapOf<String, MessageEntity>()
        val ids = messages.map { it.messageId }.filter { it.isNotBlank() }.distinct()
        val idSet = ids.toSet()
        for (chunk in ids.chunked(450)) {
            messageDao.getMessagesByIdsSync(chunk).forEach { existingMap[it.messageId] = it }
        }
        val backupMap = messageBackupStore?.getAllMessages()
            ?.filter { it.messageId in existingMap || it.messageId in idSet }
            ?.associateBy { it.messageId }
            .orEmpty()

        val merged = messages.map { incoming ->
            val normIncomingRead = forceRead || incoming.read || incoming.isRead || incoming.status.equals("READ", ignoreCase = true)
            val normIncomingStatus = if (normIncomingRead) "READ" else incoming.status.uppercase()

            val local = existingMap[incoming.messageId]
            val backup = backupMap[incoming.messageId]

            val localRead = local != null && (local.read || local.isRead || local.status.equals("READ", ignoreCase = true))
            val backupRead = backup != null && (backup.read || backup.isRead || backup.status.equals("READ", ignoreCase = true))
            val finalRead = normIncomingRead || localRead || backupRead

            val bestRank = maxOf(
                messageStatusRank(normIncomingStatus, normIncomingRead),
                local?.let { messageStatusRank(it.status, localRead) } ?: 0,
                backup?.let { messageStatusRank(it.status, backupRead) } ?: 0
            )
            val finalStatus = when {
                finalRead -> "READ"
                bestRank == 3 -> "DELIVERED"
                bestRank == 2 -> "SENT"
                bestRank == 1 -> "SENDING"
                else -> normIncomingStatus
            }
            incoming.copy(status = finalStatus, read = finalRead)
        }
        messageDao.insertMessages(merged)
        messageBackupStore?.saveMessages(merged)
    }

    private val chatPrefs by lazy {
        context?.getSharedPreferences("leno_chat_features_prefs", Context.MODE_PRIVATE)
    }

    private val _pinnedChatIds = MutableStateFlow<Set<String>>(
        chatPrefs?.getStringSet("pinned_chat_ids", emptySet())?.toSet() ?: emptySet()
    )
    val pinnedChatIds: StateFlow<Set<String>> = _pinnedChatIds.asStateFlow()

    fun togglePinChat(partnerId: String): Boolean {
        if (partnerId.isBlank()) return false
        val current = _pinnedChatIds.value.toMutableSet()
        val willPin = if (current.contains(partnerId)) {
            current.remove(partnerId)
            false
        } else {
            if (current.size >= 3) {
                val first = current.first()
                current.remove(first)
            }
            current.add(partnerId)
            true
        }
        _pinnedChatIds.value = current
        chatPrefs?.edit()?.putStringSet("pinned_chat_ids", current)?.apply()
        return willPin
    }

    fun isChatPinned(partnerId: String): Boolean = _pinnedChatIds.value.contains(partnerId)

    private val _starredMessageIds = MutableStateFlow<Set<String>>(
        chatPrefs?.getStringSet("starred_msg_ids", emptySet())?.toSet() ?: emptySet()
    )
    val starredMessageIds: StateFlow<Set<String>> = _starredMessageIds.asStateFlow()

    fun toggleStarMessage(messageId: String): Boolean {
        if (messageId.isBlank()) return false
        val current = _starredMessageIds.value.toMutableSet()
        val willStar = if (current.contains(messageId)) {
            current.remove(messageId)
            false
        } else {
            current.add(messageId)
            true
        }
        _starredMessageIds.value = current
        chatPrefs?.edit()?.putStringSet("starred_msg_ids", current)?.apply()
        return willStar
    }

    fun isMessageStarred(messageId: String): Boolean = _starredMessageIds.value.contains(messageId)

    val currentUser: Flow<UserEntity?> = userDao.getCurrentUser()
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    fun getAlternativeAccountsForUser(user: UserEntity?): Flow<List<UserEntity>> {
        if (user == null) return flowOf(emptyList())
        return combine(userDao.getAllUsers(), deviceAccountsVersion) { _, _ ->
            val backupAccounts = accountBackupStore?.getAllAccounts().orEmpty()
            val ownedIds = sessionManager?.getDeviceAccountIds()
            val candidates = if (ownedIds != null) {
                backupAccounts.filter { it.userId in ownedIds }
            } else {
                backupAccounts
            }
            candidates
                .distinctBy { it.userId }
                .filter {
                    it.userId != user.userId &&
                    (user.username.isBlank() || it.username.isBlank() || !it.username.equals(user.username, ignoreCase = true)) &&
                    (user.linoId.isBlank() || it.linoId.isBlank() || !it.linoId.equals(user.linoId, ignoreCase = true)) &&
                    it.userId != OFFICIAL_LENO_ID &&
                    !it.isOfficial &&
                    it.userId != "user_len_50638964"
                }
        }
    }

    suspend fun switchToAccount(targetUserId: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        isAuthTransitionInProgress = true
        try {
            ensureOwnersAccountsCreated()
            val target = userDao.getUserByIdSync(targetUserId)
                ?: accountBackupStore?.findAccount(targetUserId)
                ?: (if (targetUserId == "usr_officialjaiby_2026") {
                    userDao.getUserByUsernameSync("officialjaiby")
                } else null)
                ?: return@withContext Result.failure(Exception("Account not found for id $targetUserId"))

            val updated = target.copy(
                isCurrentUser = true,
                isOnline = true,
                lastSeen = System.currentTimeMillis()
            )
            userDao.switchActiveUserAtomic(updated)
            sessionManager?.saveActiveUserId(target.userId)
            recordDeviceOwnedAccount(updated)

            try {
                syncRemoteContactsIntoRoom(target.userId)
                syncAllUserChatsAndMessages(target.userId)
                ensureInitialOfficialMessageForUser(target.userId)
            } catch (e: Exception) {
                Log.w("LenoRepository", "Non-fatal account switch sync notice: ${e.message}")
            }

            return@withContext Result.success(updated)
        } catch (e: Exception) {
            Log.e("LenoRepository", "Error switching account: ${e.message}", e)
            return@withContext Result.failure(e)
        } finally {
            isAuthTransitionInProgress = false
        }
    }

    init {
        LessonProgressManager.remoteSyncHandler = { userId, classId, completedIds, quizScores, streakDays, lastStudyDate, lastOpenedClassId, lastOpenedLessonId ->
            CoroutineScope(Dispatchers.IO).launch {
                remoteAccountService.saveLessonProgressToRemote(
                    userId = userId,
                    classId = classId,
                    completedLessonIds = completedIds,
                    quizScores = quizScores,
                    lastOpenedLessonId = lastOpenedLessonId,
                    lastOpenedClassId = lastOpenedClassId,
                    streakDays = streakDays,
                    lastStudyDate = lastStudyDate
                )
            }
        }
        LessonProgressManager.remoteCertificateHandler = { cert ->
            CoroutineScope(Dispatchers.IO).launch {
                remoteAccountService.saveCertificateToRemote(cert)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Delete any corrupted self-messages from previous bug
                try {
                    messageDao.deleteCorruptedSelfMessages()
                } catch (_: Exception) {}

                // Restore any backed-up chat messages and partners into Room monotonically
                try {
                    messageBackupStore?.getAllMessages()?.let { msgs ->
                        val validMsgs = msgs.filter { it.senderId != it.receiverId && it.senderId.isNotBlank() && it.receiverId.isNotBlank() }
                        if (validMsgs.isNotEmpty()) {
                            insertMessagesMonotonic(validMsgs)
                        }
                    }
                    messageBackupStore?.getAllChatPartnerProfiles()?.let { partners ->
                        val activeUid = sessionManager?.getActiveUserId()
                        val filteredPartners = partners.filter { it.userId != activeUid }
                        userDao.upsertRemoteUsersSafely(filteredPartners, activeUid)
                    }
                } catch (e: Exception) {
                    Log.w("LenoRepository", "Error restoring message backup: ${e.message}")
                }

                userDao.purgeDemoAccounts()
                accountBackupStore?.purgeDemoAccounts()
                ensureOfficialLenoAccountCreated()

                val currentLocalUser = userDao.getCurrentUserSync()
                val savedUserId = sessionManager?.getActiveUserId()

                if (currentLocalUser != null) {
                    sessionManager?.saveActiveUserId(currentLocalUser.userId)
                    recordDeviceOwnedAccount(currentLocalUser)
                    syncRemoteContactsIntoRoom(currentLocalUser.userId)
                    syncAllUserChatsAndMessages(currentLocalUser.userId)
                } else if (!savedUserId.isNullOrBlank()) {
                    val existing = userDao.getUserByIdSync(savedUserId)
                        ?: accountBackupStore?.findAccount(savedUserId)
                        ?: remoteAccountService.findRemoteUserById(savedUserId)

                    if (existing != null && sessionManager?.getActiveUserId() == savedUserId) {
                        val restored = existing.copy(userId = savedUserId, isCurrentUser = true, isOnline = true)
                        userDao.switchActiveUserAtomic(restored)
                        recordDeviceOwnedAccount(restored)
                        syncRemoteContactsIntoRoom(savedUserId)
                        syncAllUserChatsAndMessages(savedUserId)
                    }
                } else {
                    val authEmail = sessionManager?.getAuthEmail()
                    val authPass = sessionManager?.getAuthPass()
                    if (!authEmail.isNullOrBlank() && !authPass.isNullOrBlank()) {
                        var fbUser: com.google.firebase.auth.FirebaseUser? = remoteAccountService.getCurrentAuthUser()
                        if (fbUser == null || !fbUser.email.equals(authEmail, ignoreCase = true)) {
                            try {
                                val authRes = remoteAccountService.signInWithFirebaseAuth(authEmail, authPass)
                                if (authRes.isSuccess) {
                                    fbUser = remoteAccountService.getCurrentAuthUser()
                                } else {
                                    sessionManager?.clearAuthCredentials()
                                }
                            } catch (e: Exception) {
                                Log.w("LenoRepository", "Auto re-auth notice: ${e.message}")
                                sessionManager?.clearAuthCredentials()
                            }
                        }

                        if (fbUser != null && sessionManager?.getAuthEmail() == authEmail) {
                            val existing = remoteAccountService.findRemoteUserById(fbUser.uid)
                                ?: (if (!fbUser.email.isNullOrBlank()) remoteAccountService.findRemoteUser(fbUser.email!!) else null)
                                ?: userDao.getUserByIdSync(fbUser.uid)
                                ?: accountBackupStore?.findAccount(fbUser.uid)

                            if (existing != null && sessionManager?.getAuthEmail() == authEmail) {
                                val restored = existing.copy(userId = fbUser.uid, isCurrentUser = true, isOnline = true)
                                userDao.switchActiveUserAtomic(restored)
                                sessionManager?.saveActiveUserId(fbUser.uid)
                                recordDeviceOwnedAccount(restored)
                                remoteAccountService.saveUserToRemote(restored.copy(lastSeen = System.currentTimeMillis()))
                                syncRemoteContactsIntoRoom(fbUser.uid)
                                syncAllUserChatsAndMessages(fbUser.uid)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("LenoRepository", "Repository init exception: ${e.message}")
            }
        }
    }

    fun isSessionActive(): Boolean {
        return !sessionManager?.getActiveUserId().isNullOrBlank()
    }

    /**
     * Registers an authentication listener with Firebase to track login/logout changes.
     */
    fun registerAuthStateListener(listener: (String?) -> Unit): FirebaseAuth.AuthStateListener? {
        return remoteAccountService.addAuthStateListener { user ->
            listener(user?.uid)
        }
    }

    /**
     * Unregisters an authentication state listener.
     */
    fun unregisterAuthStateListener(listener: FirebaseAuth.AuthStateListener?) {
        remoteAccountService.removeAuthStateListener(listener)
    }

    suspend fun syncCurrentUser() {
        val user = userDao.getCurrentUserSync() ?: return
        userDao.updateOnlineStatus(user.userId, true)
        remoteAccountService.saveUserToRemote(user.copy(isOnline = true, lastSeen = System.currentTimeMillis()))
    }

    suspend fun syncCurrentUserFromFirebase() {
        if (isAuthTransitionInProgress) return
        val currentLocal = userDao.getCurrentUserSync()

        // 1. Keep existing logged-in session safe! Never wipe active user!
        if (currentLocal != null) {
            sessionManager?.saveActiveUserId(currentLocal.userId)
            recordDeviceOwnedAccount(currentLocal)
            return
        }

        // 2. Check active session manager ID first across Room, persistent backup store, and remote Firestore
        val savedUserId = sessionManager?.getActiveUserId()
        if (!savedUserId.isNullOrBlank()) {
            val userToRestore = userDao.getUserByIdSync(savedUserId)
                ?: accountBackupStore?.findAccount(savedUserId)
                ?: remoteAccountService.findRemoteUserById(savedUserId)
            if (userToRestore != null && !isAuthTransitionInProgress) {
                val restored = userToRestore.copy(isCurrentUser = true, isOnline = true)
                userDao.switchActiveUserAtomic(restored)
                sessionManager?.saveActiveUserId(savedUserId)
                recordDeviceOwnedAccount(restored)
                syncRemoteContactsIntoRoom(savedUserId)
                syncAllUserChatsAndMessages(savedUserId)
                return
            }
        }

        // 3. Fallback to Firebase Auth user ONLY when there is an active auth session and no transition in flight
        val fbUser = remoteAccountService.getCurrentAuthUser()
        if (fbUser != null && !isAuthTransitionInProgress) {
            val remoteUser = remoteAccountService.findRemoteUserById(fbUser.uid)
                ?: (if (!fbUser.email.isNullOrBlank()) remoteAccountService.findRemoteUser(fbUser.email!!) else null)
            if (remoteUser != null && !isAuthTransitionInProgress) {
                val restored = remoteUser.copy(isCurrentUser = true, isOnline = true)
                userDao.switchActiveUserAtomic(restored)
                sessionManager?.saveActiveUserId(restored.userId)
                recordDeviceOwnedAccount(restored)
                syncRemoteContactsIntoRoom(restored.userId)
                syncAllUserChatsAndMessages(restored.userId)
                return
            } else {
                val cached = userDao.getUserByIdSync(fbUser.uid)
                if (cached != null && !isAuthTransitionInProgress) {
                    val restored = cached.copy(isCurrentUser = true, isOnline = true)
                    userDao.switchActiveUserAtomic(restored)
                    sessionManager?.saveActiveUserId(restored.userId)
                    recordDeviceOwnedAccount(restored)
                    syncRemoteContactsIntoRoom(restored.userId)
                    syncAllUserChatsAndMessages(restored.userId)
                    return
                }
            }

            val backupUser = if (!fbUser.email.isNullOrBlank()) accountBackupStore?.findAccount(fbUser.email!!) else null
            if (backupUser != null && !isAuthTransitionInProgress) {
                val restored = backupUser.copy(isCurrentUser = true, isOnline = true)
                userDao.switchActiveUserAtomic(restored)
                sessionManager?.saveActiveUserId(restored.userId)
                recordDeviceOwnedAccount(restored)
                syncRemoteContactsIntoRoom(restored.userId)
                syncAllUserChatsAndMessages(restored.userId)
                return
            }
        }
    }

    suspend fun syncRemoteContactsIntoRoom(userId: String) {
        if (userId.isBlank() || userId == OFFICIAL_LENO_ID) return
        try {
            val remoteContacts = remoteAccountService.getContactsFromRemote(userId)
            if (remoteContacts.isNotEmpty()) {
                userDao.insertUsers(remoteContacts)
                remoteContacts.forEach { contact ->
                    friendshipDao.insertFriendship(
                        FriendshipEntity(
                            id = "${userId}_${contact.userId}",
                            requesterId = userId,
                            targetId = contact.userId,
                            status = "ACCEPTED",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error syncing remote contacts into Room: ${e.message}")
        }
    }

    suspend fun syncRemoteChatMessages(otherUserId: String) {
        val current = userDao.getCurrentUserSync() ?: return
        if (otherUserId.isBlank() || otherUserId == OFFICIAL_LENO_ID) return
        try {
            val primaryChatId = remoteAccountService.getChatId(current.userId, otherUserId)
            val chatIdsToCheck = linkedSetOf(primaryChatId)

            for (chatId in chatIdsToCheck) {
                val remoteMessages = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    remoteAccountService.getChatMessagesFromRemote(chatId)
                }.orEmpty()

                if (remoteMessages.isNotEmpty()) {
                    val activePartner = activeChatPartnerIdFlow.value ?: activeChatPartnerIdSupplier?.invoke()
                    val partnerAliases = getUserAliasesSync(otherUserId).toSet()
                    val isChatActive = activePartner != null && (activePartner == otherUserId || activePartner in partnerAliases)

                    val existingLocalMessages = messageDao.getMessagesBetweenUserAliasesSync(
                        listOf(current.userId) + getUserAliasesSync(current.userId),
                        listOf(otherUserId) + partnerAliases
                    ).associateBy { it.messageId }

                    val adjustedMessages = remoteMessages.map { msg ->
                        val local = existingLocalMessages[msg.messageId]
                        val isAlreadyReadLocally = local != null && (local.status.equals("READ", ignoreCase = true) || local.read || local.isRead)
                        if (isChatActive || isAlreadyReadLocally) {
                            msg.copy(status = "READ", read = true)
                        } else {
                            msg
                        }
                    }

                    insertMessagesMonotonic(adjustedMessages, forceRead = isChatActive)
                    remoteMessages.forEach { msg ->
                        if (msg.receiverId == current.userId && (msg.status == "SENT" || msg.status == "DELIVERED")) {
                            val local = existingLocalMessages[msg.messageId]
                            val isAlreadyReadLocally = local != null && (local.status.equals("READ", ignoreCase = true) || local.read || local.isRead)
                            if (isChatActive || isAlreadyReadLocally) {
                                messageDao.updateMessageStatus(msg.messageId, "READ")
                                remoteAccountService.markMessageReadInRemote(chatId, msg.messageId, msg.senderId)
                            } else {
                                messageDao.updateMessageStatus(msg.messageId, "DELIVERED")
                                remoteAccountService.markMessageDeliveredInRemote(chatId, msg.messageId, msg.senderId)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error syncing remote chat messages: ${e.message}")
        }
    }

    suspend fun syncAllRemoteUsersIntoRoom() = withContext(Dispatchers.IO) {
        try {
            purgeAllMockUsers()
            val remoteUsers = remoteAccountService.fetchAllRemoteUsers(1500)
            if (remoteUsers.isNotEmpty()) {
                val currentUid = sessionManager?.getActiveUserId().orEmpty()
                val validRemote = remoteUsers.filter { u ->
                    u.userId != currentUid &&
                    !u.userId.endsWith("_teacher") &&
                    u.userId != "user_len_50638964" &&
                    u.userId != "test_dummy_user_to_purge" &&
                    u.username !in setOf("bello_maths", "ibrahim_quran", "fatima_english", "usman_skills", "jibril")
                }
                val nonCurrent = validRemote.map { it.copy(isCurrentUser = false) }
                userDao.upsertRemoteUsersSafely(nonCurrent, currentUid.ifBlank { null })
                val ownedIds = sessionManager?.getDeviceAccountIds().orEmpty()
                if (ownedIds.isNotEmpty()) {
                    accountBackupStore?.getAllAccounts()?.forEach { acc ->
                        if (acc.userId.isNotBlank() && acc.userId !in ownedIds) {
                            accountBackupStore.deleteAccount(acc.userId)
                        }
                    }
                }
                Log.i("LenoRepository", "Synced ${nonCurrent.size} real remote users into local database.")
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error syncing all remote users: ${e.message}")
        }
    }

    fun getAllUsersFlow(): Flow<List<UserEntity>> = userDao.getAllUsers()

    suspend fun syncAllUserChatsAndMessages(myUserId: String) {
        if (myUserId.isBlank()) return
        if (!syncChatsMutex.tryLock()) {
            return
        }
        try {
            // 1. Restore all saved chat partner profiles and account records into Room first
            val activeUid = myUserId.ifBlank { sessionManager?.getActiveUserId().orEmpty() }
            if (activeUid.isNotBlank()) {
                messageBackupStore?.removeChatPartnerProfile(activeUid)
            }
            val savedPartners = messageBackupStore?.getAllChatPartnerProfiles().orEmpty()
            val partnersToInsert = savedPartners
                .filter { it.userId != activeUid }
                .map { it.copy(isCurrentUser = false) }
            if (partnersToInsert.isNotEmpty()) {
                userDao.upsertRemoteUsersSafely(partnersToInsert, activeUid.ifBlank { null })
            }
            val ownedIds = sessionManager?.getDeviceAccountIds()
            val savedAccounts = accountBackupStore?.getAllAccounts().orEmpty()
                .let { list -> if (!ownedIds.isNullOrEmpty()) list.filter { it.userId in ownedIds } else list }
            if (savedAccounts.isNotEmpty()) {
                userDao.upsertRemoteUsersSafely(savedAccounts, myUserId)
            }

            // 2. Restore all locally backed up messages for user into Room monotonically
            val aliases = getUserAliasesSync(myUserId)
            val allDeviceMessages = messageBackupStore?.getAllMessages().orEmpty()
            if (allDeviceMessages.isNotEmpty()) {
                val userRelevant = allDeviceMessages.filter { it.senderId in aliases || it.receiverId in aliases }
                insertMessagesMonotonic(userRelevant)
            }

            // 3. Sync all registered users from remote Firestore so missing accounts are recovered
            syncAllRemoteUsersIntoRoom()

            // 4. Directly recover all sent and received messages stored across Firestore inboxes and sent folders
            val recoveredRemoteMsgs = remoteAccountService.getUserSentAndReceivedMessagesFromRemote(myUserId, aliases)
            if (recoveredRemoteMsgs.isNotEmpty()) {
                insertMessagesMonotonic(recoveredRemoteMsgs)
                for (recMsg in recoveredRemoteMsgs) {
                    val partnerId = if (recMsg.senderId in aliases) recMsg.receiverId else recMsg.senderId
                    if (partnerId.isNotBlank() && partnerId != OFFICIAL_LENO_ID && partnerId !in aliases) {
                        var partner = userDao.getUserByIdSync(partnerId)
                            ?: fetchAndSyncUserProfile(partnerId)
                        partner?.let { messageBackupStore?.saveChatPartnerProfile(it) }
                    }
                }
            }

            // 5. Ensure any partner IDs referenced in messages exist in Room users table
            val partnerIds = messageDao.getChatPartnerIdsForUserAliasesSync(aliases)
            if (partnerIds.isNotEmpty()) {
                val existingIds = userDao.getAllUsersSync().map { it.userId }.toSet()
                val missing = partnerIds.filter { it !in existingIds }
                for (mId in missing) {
                    val p = fetchAndSyncUserProfile(mId)
                    p?.let { messageBackupStore?.saveChatPartnerProfile(it) }
                }
            }

            // 6. Remote Firestore chat channels sync
            val remoteChats = remoteAccountService.getAllUserChatsFromRemote(myUserId, aliases)
            for ((chatId, otherUserId) in remoteChats) {
                if (otherUserId == OFFICIAL_LENO_ID || otherUserId in aliases) continue
                var partner = userDao.getUserByIdSync(otherUserId)
                if (partner == null) {
                    partner = fetchAndSyncUserProfile(otherUserId)
                }
                partner?.let { messageBackupStore?.saveChatPartnerProfile(it) }

                val remoteMessages = remoteAccountService.getChatMessagesFromRemote(chatId)
                if (remoteMessages.isNotEmpty()) {
                    val activePartner = activeChatPartnerIdFlow.value ?: activeChatPartnerIdSupplier?.invoke()
                    val partnerAliases = getUserAliasesSync(otherUserId).toSet()
                    val isChatActive = activePartner != null && (activePartner == otherUserId || activePartner in partnerAliases)

                    val existingLocalMessages = messageDao.getMessagesBetweenUserAliasesSync(
                        listOf(myUserId) + aliases,
                        listOf(otherUserId) + partnerAliases
                    ).associateBy { it.messageId }

                    val adjustedMessages = remoteMessages.map { msg ->
                        val local = existingLocalMessages[msg.messageId]
                        val isAlreadyReadLocally = local != null && (local.status.equals("READ", ignoreCase = true) || local.read || local.isRead)
                        if (isChatActive || isAlreadyReadLocally) {
                            msg.copy(status = "READ", read = true)
                        } else {
                            msg
                        }
                    }

                    insertMessagesMonotonic(adjustedMessages, forceRead = isChatActive)
                    remoteMessages.forEach { msg ->
                        if (msg.receiverId == myUserId && (msg.status == "SENT" || msg.status == "DELIVERED")) {
                            val local = existingLocalMessages[msg.messageId]
                            val isAlreadyReadLocally = local != null && (local.status.equals("READ", ignoreCase = true) || local.read || local.isRead)
                            if (isChatActive || isAlreadyReadLocally) {
                                messageDao.updateMessageStatus(msg.messageId, "READ")
                                remoteAccountService.markMessageReadInRemote(chatId, msg.messageId, msg.senderId)
                            } else {
                                messageDao.updateMessageStatus(msg.messageId, "DELIVERED")
                                remoteAccountService.markMessageDeliveredInRemote(chatId, msg.messageId, msg.senderId)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error in syncAllUserChatsAndMessages: ${e.message}")
        } finally {
            syncChatsMutex.unlock()
        }
    }

    suspend fun handleIncomingMessage(message: MessageEntity, isChatActiveWithSender: Boolean) {
        try {
            var sender = userDao.getUserByIdSync(message.senderId)
            if (sender == null) {
                sender = fetchAndSyncUserProfile(message.senderId)
            }
            sender?.let { messageBackupStore?.saveChatPartnerProfile(it) }

            val updatedMsg = message.copy(
                status = if (isChatActiveWithSender) "READ" else "DELIVERED",
                read = isChatActiveWithSender
            )
            insertMessagesMonotonic(listOf(updatedMsg), forceRead = isChatActiveWithSender)

            val chatId = remoteAccountService.getChatId(message.senderId, message.receiverId)
            if (isChatActiveWithSender) {
                remoteAccountService.markMessageReadInRemote(chatId, message.messageId, message.senderId)
            } else {
                remoteAccountService.markMessageDeliveredInRemote(chatId, message.messageId, message.senderId)
            }
            remoteAccountService.deleteInboxMessage(message.receiverId, message.messageId)
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error handling incoming message: ${e.message}")
        }
    }

    suspend fun handleSentReceipt(messageId: String, status: String, myUserId: String) {
        try {
            val isRead = status.equals("READ", ignoreCase = true)
            messageDao.updateMessageStatus(messageId, status)
            if (isRead) {
                messageDao.updateMessageReadStatus(messageId, true)
            }
            messageBackupStore?.updateMessageStatus(messageId, status)
            remoteAccountService.deleteSentReceipt(myUserId, messageId)
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error handling sent receipt: ${e.message}")
        }
    }

    suspend fun deleteMessage(messageId: String) {
        try {
            val existing = messageDao.getMessageByIdSync(messageId)
            messageDao.deleteMessageById(messageId)
            messageBackupStore?.deleteMessage(messageId)
            if (existing != null && existing.senderId.isNotBlank() && existing.receiverId.isNotBlank() &&
                existing.receiverId != OFFICIAL_LENO_ID && existing.senderId != OFFICIAL_LENO_ID) {
                val chatId = remoteAccountService.getChatId(existing.senderId, existing.receiverId)
                CoroutineScope(Dispatchers.IO).launch {
                    remoteAccountService.deleteMessageFromRemote(chatId, messageId)
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error deleting message $messageId: ${e.message}")
        }
    }

    suspend fun deleteMessagesByIdsLocally(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        try {
            messageIds.forEach { id ->
                if (id.isNotBlank()) {
                    messageDao.deleteMessageById(id)
                    messageBackupStore?.deleteMessage(id)
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error deleting local messages: ${e.message}")
        }
    }

    fun getUserById(userId: String): Flow<UserEntity?> = userDao.getUserById(userId)

    suspend fun getUserByIdSync(userId: String): UserEntity? = userDao.getUserByIdSync(userId)

    fun getCurrentUserIdSync(): String? = sessionManager?.getActiveUserId()

    suspend fun getCurrentUserSync(): UserEntity? = userDao.getCurrentUserSync()

    suspend fun fetchAndSyncUserProfile(userId: String): UserEntity? {
        if (userId.isBlank()) return null
        if (userId == OFFICIAL_LENO_ID) {
            ensureOfficialLenoAccountCreated()
            return userDao.getUserByIdSync(OFFICIAL_LENO_ID)
        }
        val remote = remoteAccountService.findRemoteUserById(userId)
            ?: remoteAccountService.findRemoteUser(userId)
        if (remote != null) {
            val local = userDao.getUserByIdSync(userId)
            val isCurrent = local?.isCurrentUser ?: (sessionManager?.getActiveUserId() == userId)
            val updated = remote.copy(
                isCurrentUser = isCurrent,
                password = "",
                passwordHash = local?.passwordHash ?: remote.passwordHash,
                passwordSalt = local?.passwordSalt ?: remote.passwordSalt
            )
            userDao.insertUser(updated)
            return updated
        }
        val fromBackup = messageBackupStore?.getAllChatPartnerProfiles()?.firstOrNull { it.userId == userId }
            ?: accountBackupStore?.findAccount(userId)
        if (fromBackup != null) {
            val restored = fromBackup.copy(isCurrentUser = false)
            userDao.insertUser(restored)
            return restored
        }
        val existing = userDao.getUserByIdSync(userId)
        if (existing != null) return existing
        val fallback = UserEntity(
            userId = userId,
            username = if (userId.startsWith("usr_")) userId.removePrefix("usr_").take(10) else userId.take(12),
            displayName = if (userId.startsWith("usr_")) "User ${userId.takeLast(4)}" else userId,
            bio = "Connecting on Leno ✨",
            isCurrentUser = false
        )
        userDao.insertUser(fallback)
        return fallback
    }

    fun searchUsers(query: String, currentUserId: String = ""): Flow<List<UserEntity>> = flow {
        val cleanQuery = query.trim().removePrefix("@")
        if (cleanQuery.isBlank()) {
            emit(emptyList())
            return@flow
        }

        // 1. Emit local Room & backup results immediately so UI is instant
        val localRoom = try { userDao.searchUsersSync(cleanQuery, currentUserId) } catch (_: Exception) { emptyList() }
        val cleanQueryDigits = cleanQuery.replace("-", "").uppercase()
        val ownedIds = sessionManager?.getDeviceAccountIds()
        val backupMatches = accountBackupStore?.getAllAccounts().orEmpty()
            .let { list -> if (!ownedIds.isNullOrEmpty()) list.filter { it.userId in ownedIds } else list }
            .filter {
                val rawLeno = it.linoId.replace("-", "").uppercase()
                it.userId != currentUserId && !it.isCurrentUser && (
                    it.username.contains(cleanQuery, ignoreCase = true) ||
                    it.displayName.contains(cleanQuery, ignoreCase = true) ||
                    it.linoId.contains(cleanQuery, ignoreCase = true) ||
                    it.userId.contains(cleanQuery, ignoreCase = true) ||
                    (cleanQueryDigits.isNotBlank() && rawLeno.contains(cleanQueryDigits)) ||
                    it.email.contains(cleanQuery, ignoreCase = true)
                )
            }
        val partnerMatches = messageBackupStore?.getAllChatPartnerProfiles().orEmpty().filter {
            val rawLeno = it.linoId.replace("-", "").uppercase()
            it.userId != currentUserId && (
                it.username.contains(cleanQuery, ignoreCase = true) ||
                it.displayName.contains(cleanQuery, ignoreCase = true) ||
                it.linoId.contains(cleanQuery, ignoreCase = true) ||
                it.userId.contains(cleanQuery, ignoreCase = true) ||
                (cleanQueryDigits.isNotBlank() && rawLeno.contains(cleanQueryDigits)) ||
                it.email.contains(cleanQuery, ignoreCase = true)
            )
        }
        val lowerQuery = cleanQuery.lowercase()
        val officialMatches = mutableListOf<UserEntity>()
        if (lowerQuery.contains("officialjaiby") || "officialjaiby".contains(lowerQuery) || lowerQuery.contains("50638964")) {
            val officialJaiby = UserEntity(
                userId = "usr_officialjaiby_2026",
                username = "officialjaiby",
                displayName = "Official Jaiby",
                bio = "Leno Platform Founder & Developer 🌟",
                linoId = "LEN-50638964",
                email = "officialjaiby@leno.chat",
                role = "OWNER",
                isOfficial = true,
                isOnline = true,
                createdAt = 1700000000000L
            )
            if (officialJaiby.userId != currentUserId) officialMatches.add(officialJaiby)
        }
        if (lowerQuery.contains("leno") || lowerQuery.contains("00000001")) {
            val officialLeno = UserEntity(
                userId = OFFICIAL_LENO_ID,
                username = "leno",
                displayName = "Official Leno",
                bio = "Official system announcements, platform updates, and verified assistance from Leno.",
                avatarUrl = "android.resource://com.example/drawable/ic_official_leno_avatar",
                isOnline = true,
                lastSeen = System.currentTimeMillis(),
                statusMessage = "Official System Account ✓",
                isCurrentUser = false,
                email = "official@leno.app",
                role = "OFFICIAL",
                isOfficial = true,
                linoId = "LEN-00000001"
            )
            if (officialLeno.userId != currentUserId) officialMatches.add(officialLeno)
        }

        val initialList = (localRoom + backupMatches + partnerMatches + officialMatches)
            .filter { it.userId != currentUserId }
            .distinctBy { it.userId }
        emit(initialList)

        // 2. Direct real-time fetch from Firebase Firestore so multiple devices find each other immediately
        try {
            val remoteMatches = remoteAccountService.searchRemoteUsers(cleanQuery, currentUserId)
            val exactMatch = remoteAccountService.findRemoteUser(cleanQuery)
                ?: remoteAccountService.findRemoteUserById(cleanQuery)

            val ignoredUsernames = setOf("bello_maths", "ibrahim_quran", "fatima_english", "usman_skills", "jibril")
            val fastRemote = (remoteMatches + listOfNotNull(exactMatch))
                .filter { u ->
                    u.userId != currentUserId &&
                    !u.userId.endsWith("_teacher") &&
                    u.userId != "user_len_50638964" &&
                    u.userId != "test_dummy_user_to_purge" &&
                    u.username !in ignoredUsernames
                }
                .distinctBy { it.userId }

            if (fastRemote.isNotEmpty()) {
                val toSave = fastRemote.map { it.copy(isCurrentUser = false) }
                userDao.upsertRemoteUsersSafely(toSave, currentUserId.ifBlank { null })
                toSave.forEach { messageBackupStore?.saveChatPartnerProfile(it) }
                val combined = (initialList + toSave)
                    .filter { it.userId != currentUserId }
                    .distinctBy { it.userId }
                emit(combined)
            } else {
                val allRemote = remoteAccountService.fetchAllRemoteUsers(200)
                val broadMatches = allRemote.filter {
                    val rawLeno = it.linoId.replace("-", "").uppercase()
                    it.userId != currentUserId && (
                        it.username.contains(cleanQuery, ignoreCase = true) ||
                        it.displayName.contains(cleanQuery, ignoreCase = true) ||
                        it.linoId.contains(cleanQuery, ignoreCase = true) ||
                        it.userId.contains(cleanQuery, ignoreCase = true) ||
                        (cleanQueryDigits.isNotBlank() && rawLeno.contains(cleanQueryDigits)) ||
                        it.email.contains(cleanQuery, ignoreCase = true) ||
                        (cleanQuery.length >= 3 && it.phoneNumber.contains(cleanQuery))
                    )
                }.filter { u ->
                    !u.userId.endsWith("_teacher") &&
                    u.userId != "user_len_50638964" &&
                    u.userId != "test_dummy_user_to_purge" &&
                    u.username !in ignoredUsernames
                }.distinctBy { it.userId }

                if (broadMatches.isNotEmpty()) {
                    val toSave = broadMatches.map { it.copy(isCurrentUser = false) }
                    userDao.upsertRemoteUsersSafely(toSave, currentUserId.ifBlank { null })
                    toSave.forEach { messageBackupStore?.saveChatPartnerProfile(it) }
                    val combined = (initialList + toSave)
                        .filter { it.userId != currentUserId }
                        .distinctBy { it.userId }
                    emit(combined)
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Search remote sync: ${e.message}")
        }
    }

    fun getContactsForUser(userId: String): Flow<List<UserEntity>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return userDao.getContactsForUser(userId)
    }

    suspend fun getUserAliasesSync(userId: String): List<String> {
        if (userId.isBlank()) return emptyList()
        val set = linkedSetOf(userId)
        var user = userDao.getUserByIdSync(userId)
            ?: userDao.getUserByUsernameSync(userId)
            ?: userDao.getUserByLinoIdSync(userId)
            ?: accountBackupStore?.findAccount(userId)
        if (user == null && userId != OFFICIAL_LENO_ID) {
            user = remoteAccountService.findRemoteUserById(userId) ?: remoteAccountService.findRemoteUser(userId)
            if (user != null) {
                userDao.insertUser(user)
            }
        }
        if (user != null) {
            set.add(user.userId)
            if (user.username.isNotBlank()) set.add(user.username)
            if (user.linoId.isNotBlank()) set.add(user.linoId)
            if (user.email.isNotBlank()) set.add(user.email)
            if (user.googleEmail.isNotBlank()) set.add(user.googleEmail)
            if (user.isOwner) {
                set.add("usr_officialjaiby_2026")
                set.add("officialjaiby")
                set.add("LEN-50638964")
                set.add("50638964")
            }
        }
        if (userId == OFFICIAL_LENO_ID) {
            set.add("official_leno")
            set.add("Official Leno")
            set.add("LEN-00000001")
        }
        return set.filter { it.isNotBlank() }.toList()
    }

    fun getChatPartnersForUser(userId: String): Flow<List<UserEntity>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val aliases = getUserAliasesSync(userId)
        val activeUid = userId.ifBlank { sessionManager?.getActiveUserId().orEmpty() }
        if (activeUid.isNotBlank()) {
            messageBackupStore?.removeChatPartnerProfile(activeUid)
        }
        try {
            // Restore backed up messages into Room immediately so messages table is never empty on restart
            val allBackedUpMessages = messageBackupStore?.getAllMessages().orEmpty()
            if (allBackedUpMessages.isNotEmpty()) {
                val userRelevant = allBackedUpMessages.filter { it.senderId in aliases || it.receiverId in aliases }
                if (userRelevant.isNotEmpty()) {
                    insertMessagesMonotonic(userRelevant)
                }
            }

            // Ensure saved partners from backup store and device-owned account backup are present in Room
            val savedPartners = messageBackupStore?.getAllChatPartnerProfiles().orEmpty()
            val partnersToInsert = savedPartners
                .filter { it.userId != activeUid && it.userId !in aliases }
                .map { it.copy(isCurrentUser = false) }
            if (partnersToInsert.isNotEmpty()) {
                userDao.upsertRemoteUsersSafely(partnersToInsert, activeUid.ifBlank { null })
            }
            val ownedIds = sessionManager?.getDeviceAccountIds()
            val savedAccounts = accountBackupStore?.getAllAccounts().orEmpty()
                .let { list -> if (!ownedIds.isNullOrEmpty()) list.filter { it.userId in ownedIds } else list }
            if (savedAccounts.isNotEmpty()) {
                val nonCurrent = savedAccounts.filter { it.userId != activeUid && it.userId !in aliases }
                    .map { it.copy(isCurrentUser = false) }
                if (nonCurrent.isNotEmpty()) {
                    userDao.upsertRemoteUsersSafely(nonCurrent, activeUid.ifBlank { null })
                }
            }

            // Heal any partner IDs in messages missing from Room immediately without blocking
            val partnerIds = messageDao.getChatPartnerIdsForUserAliasesSync(aliases)
            if (partnerIds.isNotEmpty()) {
                val existing = userDao.getAllUsersSync().map { it.userId }.toSet()
                val missing = partnerIds.filter { it !in existing && it !in aliases && it != OFFICIAL_LENO_ID }
                if (missing.isNotEmpty()) {
                    val placeholders = missing.map { mId ->
                        val fromBackup = messageBackupStore?.getAllChatPartnerProfiles()?.firstOrNull { it.userId == mId }
                            ?: accountBackupStore?.findAccount(mId)
                        fromBackup?.copy(isCurrentUser = false) ?: UserEntity(
                            userId = mId,
                            username = if (mId.startsWith("usr_")) mId.removePrefix("usr_").take(10) else mId.take(12),
                            displayName = if (mId.startsWith("usr_")) "User ${mId.takeLast(4)}" else "User ${mId.take(6)}",
                            bio = "Connecting on Leno ✨",
                            isCurrentUser = false
                        )
                    }
                    userDao.upsertRemoteUsersSafely(placeholders, activeUid.ifBlank { null })
                }
            }
        } catch (_: Exception) {}

        val combinedPartnersFlow = messageDao.getChatPartnersForUserAliases(aliases).map { roomPartners ->
            val backupProfiles = messageBackupStore?.getAllChatPartnerProfiles().orEmpty()
                .filter { it.userId != activeUid && it.userId !in aliases && it.userId != OFFICIAL_LENO_ID }
            val allMessages = (messageBackupStore?.getAllMessages().orEmpty() + messageDao.getAllMessagesForUserAliasesSync(aliases)).distinctBy { it.messageId }
            val relevantPartnerIds = allMessages
                .filter { it.senderId in aliases || it.receiverId in aliases }
                .map { if (it.senderId in aliases) it.receiverId else it.senderId }
                .filter { it.isNotBlank() && it != activeUid && it !in aliases && it != OFFICIAL_LENO_ID }
                .toSet()

            val extraFromBackup = backupProfiles.filter { 
                it.userId in relevantPartnerIds || 
                (it.username.isNotBlank() && it.username in relevantPartnerIds) || 
                (it.linoId.isNotBlank() && it.linoId in relevantPartnerIds)
            }
            val ownedIds = sessionManager?.getDeviceAccountIds()
            val extraFromAccounts = accountBackupStore?.getAllAccounts().orEmpty()
                .let { list -> if (!ownedIds.isNullOrEmpty()) list.filter { it.userId in ownedIds } else list }
                .filter { 
                    (it.userId in relevantPartnerIds || 
                     (it.username.isNotBlank() && it.username in relevantPartnerIds) || 
                     (it.linoId.isNotBlank() && it.linoId in relevantPartnerIds))
                    && it.userId != activeUid && it.userId !in aliases 
                }

            // Synthesize any partner ID that has sent or received messages so conversations NEVER disappear
            val knownPartnerIds = (roomPartners.map { it.userId } + extraFromBackup.map { it.userId } + extraFromAccounts.map { it.userId }).toSet()
            val missingPartnerIds = relevantPartnerIds.filter { pId ->
                pId !in knownPartnerIds && extraFromBackup.none { 
                    it.userId == pId || 
                    (it.username.isNotBlank() && it.username.equals(pId, true)) || 
                    (it.linoId.isNotBlank() && it.linoId.equals(pId, true)) 
                }
            }
            val synthesized = missingPartnerIds.map { pId ->
                val fromBackup = messageBackupStore?.getAllChatPartnerProfiles()?.firstOrNull {
                    it.userId == pId || 
                    (it.username.isNotBlank() && it.username.equals(pId, true)) || 
                    (it.linoId.isNotBlank() && it.linoId.equals(pId, true))
                } ?: accountBackupStore?.findAccount(pId)

                fromBackup?.copy(isCurrentUser = false) ?: UserEntity(
                    userId = pId,
                    username = if (pId.startsWith("usr_")) pId.removePrefix("usr_").take(10) else if (pId.startsWith("LEN-")) pId.removePrefix("LEN-").take(10) else pId.take(12),
                    displayName = if (pId.startsWith("usr_")) "User ${pId.takeLast(4)}" else if (pId.startsWith("LEN-")) "User $pId" else "User ${pId.take(6)}",
                    bio = "Connecting on Leno ✨",
                    isCurrentUser = false
                )
            }

            val partnerTimestamps = allMessages
                .filter { (it.senderId in aliases && it.receiverId !in aliases) || (it.receiverId in aliases && it.senderId !in aliases) }
                .groupBy { if (it.senderId in aliases) it.receiverId else it.senderId }
                .mapValues { entry -> entry.value.maxOfOrNull { m -> m.timestamp } ?: 0L }

            val allPartners = (roomPartners + extraFromBackup + extraFromAccounts + synthesized).distinctBy { it.userId }
            allPartners.sortedByDescending { partner ->
                partnerTimestamps[partner.userId]
                    ?: (if (partner.username.isNotBlank()) partnerTimestamps[partner.username] else null)
                    ?: (if (partner.linoId.isNotBlank()) partnerTimestamps[partner.linoId] else null)
                    ?: partner.lastSeen
            }
        }
        emitAll(combinedPartnersFlow)
    }

    fun getMessagesBetween(user1Id: String, user2Id: String): Flow<List<MessageEntity>> = flow {
        if (user1Id.isBlank() || user2Id.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val u1Known = getUserAliasesSync(user1Id)
        val u2Known = getUserAliasesSync(user2Id)
        val myIds = u1Known.toSet()
        val partnerIds = u2Known.toSet()

        try {
            messageBackupStore?.getMessagesForUser(user1Id, u1Known)?.let { backedUp ->
                val relevant = backedUp.filter {
                    (it.senderId in myIds && it.receiverId in partnerIds) ||
                    (it.senderId in partnerIds && it.receiverId in myIds)
                }
                if (relevant.isNotEmpty()) {
                    insertMessagesMonotonic(relevant)
                }
            }
        } catch (_: Exception) {}

        emitAll(messageDao.getMessagesBetweenUserAliases(u1Known, u2Known).map { roomMsgs ->
            val backedUp = messageBackupStore?.getMessagesForUser(user1Id, u1Known).orEmpty()
                .filter {
                    (it.senderId in myIds && it.receiverId in partnerIds) ||
                    (it.senderId in partnerIds && it.receiverId in myIds)
                }
            (roomMsgs + backedUp).distinctBy { it.messageId }.sortedBy { it.timestamp }
        })
    }

    fun getLastMessageBetween(user1Id: String, user2Id: String): Flow<MessageEntity?> = flow {
        if (user1Id.isBlank() || user2Id.isBlank()) {
            emit(null)
            return@flow
        }
        val u1Known = getUserAliasesSync(user1Id)
        val u2Known = getUserAliasesSync(user2Id)
        val myIds = u1Known.toSet()
        val partnerIds = u2Known.toSet()

        try {
            messageBackupStore?.getMessagesForUser(user1Id, u1Known)?.let { backedUp ->
                val relevant = backedUp.filter {
                    (it.senderId in myIds && it.receiverId in partnerIds) ||
                    (it.senderId in partnerIds && it.receiverId in myIds)
                }
                if (relevant.isNotEmpty()) {
                    insertMessagesMonotonic(relevant)
                }
            }
        } catch (_: Exception) {}

        emitAll(messageDao.getLastMessageBetweenAliases(u1Known, u2Known).map { roomMsg ->
            if (roomMsg != null) roomMsg
            else {
                messageBackupStore?.getMessagesForUser(user1Id, u1Known).orEmpty()
                    .filter {
                        (it.senderId in myIds && it.receiverId in partnerIds) ||
                        (it.senderId in partnerIds && it.receiverId in myIds)
                    }
                    .maxByOrNull { it.timestamp }
            }
        })
    }

    fun getUnreadCount(currentUserId: String, otherUserId: String): Flow<Int> = flow {
        if (currentUserId.isBlank() || otherUserId.isBlank()) {
            emit(0)
            return@flow
        }

        val myAliases = (getUserAliasesSync(currentUserId) + currentUserId).filter { it.isNotBlank() }.distinct()
        val partnerAliases = (getUserAliasesSync(otherUserId) + otherUserId).filter { it.isNotBlank() }.distinct()
        val partnerSet = partnerAliases.toSet()

        emitAll(
            combine(
                messageDao.getUnreadCountFromUserAliases(
                    currentUserIds = myAliases,
                    otherUserIds = partnerAliases
                ),
                activeChatPartnerIdFlow
            ) { count, activeId ->
                val currentActive = activeId ?: activeChatPartnerIdSupplier?.invoke()
                val finalCount = if (currentActive != null && (currentActive in partnerSet || currentActive == otherUserId)) {
                    0
                } else {
                    count
                }
                unreadCountCache[otherUserId] = finalCount
                finalCount
            }.distinctUntilChanged()
        )
    }

    fun getTotalUnreadChatCount(currentUserId: String): Flow<Int> = flow {
        if (currentUserId.isBlank()) {
            emit(0)
            return@flow
        }
        val myAliases = (getUserAliasesSync(currentUserId) + currentUserId).filter { it.isNotBlank() }.distinct()
        emitAll(messageDao.getTotalUnreadCountForUserAliases(myAliases).distinctUntilChanged())
    }

    fun getUnreadSenderIds(currentUserId: String): Flow<Set<String>> = flow {
        if (currentUserId.isBlank()) {
            emit(emptySet())
            return@flow
        }
        val myAliases = (getUserAliasesSync(currentUserId) + currentUserId).filter { it.isNotBlank() }.distinct()
        emitAll(
            combine(
                messageDao.getUnreadSenderIdsForUserAliases(myAliases),
                activeChatPartnerIdFlow
            ) { senderIds, activeId ->
                val currentActive = activeId ?: activeChatPartnerIdSupplier?.invoke()
                val activeAliases = if (!currentActive.isNullOrBlank()) {
                    (getUserAliasesSync(currentActive) + currentActive).toSet()
                } else {
                    emptySet()
                }
                senderIds.filter { it.isNotBlank() && it !in activeAliases }.toSet()
            }.distinctUntilChanged()
        )
    }

    fun getFriendships(userId: String): Flow<List<FriendshipEntity>> =
        friendshipDao.getFriendshipsForUser(userId)

    fun getPendingRequestsForUser(userId: String): Flow<List<FriendshipEntity>> =
        friendshipDao.getPendingRequestsForUser(userId)

    fun getPendingRequestUsers(userId: String): Flow<List<UserEntity>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return userDao.getPendingRequestUsers(userId)
    }

    fun getFriendshipBetween(u1: String, u2: String): Flow<FriendshipEntity?> =
        friendshipDao.getFriendshipBetween(u1, u2)

    fun getBlockedUsers(userId: String): Flow<List<BlockReportEntity>> =
        blockReportDao.getBlockedUsers(userId)

    fun getUserSettings(userId: String): Flow<UserSettingsEntity?> =
        settingsDao.getUserSettings(userId)

    fun getNotificationsForUser(userId: String): Flow<List<NotificationEntity>> =
        notificationDao.getNotificationsForUser(userId)

    fun getUnreadNotificationCount(userId: String): Flow<Int> =
        notificationDao.getUnreadNotificationCount(userId)

    suspend fun markNotificationAsRead(id: String) {
        notificationDao.markNotificationAsRead(id)
        val uid = getCurrentUserIdSync()
        if (!uid.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                remoteAccountService.markNotificationReadInRemote(uid, id)
            }
        }
    }

    suspend fun markAllNotificationsAsRead(userId: String) {
        notificationDao.markAllAsRead(userId)
        if (userId.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                remoteAccountService.markAllNotificationsReadInRemote(userId)
            }
        }
    }

    // LENO ONBOARDING & REGISTRATION SYSTEM
    suspend fun checkUsernameAvailability(username: String): Result<Boolean> {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.isBlank()) {
            return Result.failure(Exception("Please enter a username."))
        }
        if (clean.length < 3) {
            return Result.failure(Exception("Username must be at least 3 characters."))
        }
        if (!clean.matches(Regex("^[a-zA-Z0-9_.]+$"))) {
            return Result.failure(Exception("Username can only contain letters, numbers, underscores and periods."))
        }
        if (clean == "leno" || clean == "lino" || clean == "official_leno" || clean == "official_lino" || clean.contains("officialleno") || clean.contains("officiallino")) {
            return Result.failure(Exception("This username is reserved for official system services."))
        }

        // 1. Fast local Room & backup check
        val localUser = userDao.getUserByUsernameSync(clean)
            ?: accountBackupStore?.findAccountByUsername(clean)
        if (localUser != null) {
            return Result.failure(Exception("This username is already taken. Please choose another username."))
        }

        // 2. Single fast remote check with strict timeout
        val isRemoteTaken = kotlinx.coroutines.withTimeoutOrNull(2000L) {
            remoteAccountService.isUsernameTakenInUsersCollection(clean)
        } ?: false

        if (isRemoteTaken) {
            return Result.failure(Exception("Username '@$clean' is already taken. Please choose another username."))
        }

        return Result.success(true)
    }

    suspend fun checkLenoIdAvailability(lenoId: String): Result<Boolean> {
        val clean = lenoId.trim().uppercase()
        val formatted = if (!clean.startsWith("LEN-")) "LEN-$clean" else clean

        val localUser = userDao.getUserByLinoIdSync(formatted)
            ?: accountBackupStore?.findAccount(formatted)
        if (localUser != null) {
            return Result.failure(Exception("This Leno ID is already in use."))
        }

        val isRemoteTaken = kotlinx.coroutines.withTimeoutOrNull(1500L) {
            remoteAccountService.isLenoIdTakenInFirestore(formatted)
        } ?: false

        if (isRemoteTaken) {
            return Result.failure(Exception("This Leno ID is already in use."))
        }

        return Result.success(true)
    }

    suspend fun generateUniqueLinoId(): String {
        for (i in 0 until 3) {
            val randomNumber = (10000000..99999999).random()
            val candidateId = "LEN-$randomNumber"
            val localMatch = userDao.getUserByLinoIdSync(candidateId)
                ?: accountBackupStore?.findAccount(candidateId)
            if (localMatch == null) {
                return candidateId
            }
        }
        return "LEN-${(10000000..99999999).random()}"
    }

    suspend fun registerWithLino(
        fullName: String,
        username: String,
        password: String
    ): Result<UserEntity> {
        val cleanName = fullName.trim()
        val cleanUsername = username.trim().removePrefix("@").lowercase()
        val cleanPassword = password.trim()

        if (cleanName.isBlank()) {
            return Result.failure(Exception("Please enter your full name."))
        }
        if (!com.example.util.NetworkUtils.isNetworkAvailable(context)) {
            return Result.failure(Exception("No internet connection detected. An active internet or mobile data connection is required to register your Leno account."))
        }
        if (cleanUsername == "leno" || cleanUsername == "official_leno" || cleanName.equals("Official Leno", ignoreCase = true)) {
            return Result.failure(Exception("This username is reserved for official platform services."))
        }
        if (cleanPassword.length < 6) {
            return Result.failure(Exception("Password must be at least 6 characters."))
        }

        val userCheck = checkUsernameAvailability(cleanUsername)
        if (userCheck.isFailure) {
            return Result.failure(userCheck.exceptionOrNull() ?: Exception("This account already exists."))
        }

        isAuthTransitionInProgress = true
        try {
            val generatedLinoId = generateUniqueLinoId()
            val authEmail = "${cleanUsername}@leno.chat"

            // 1. Attempt to create permanent Firebase Authentication account and write userMap to Firestore
            val authResult = remoteAccountService.signUpWithFirebaseAuth(
                email = authEmail,
                password = cleanPassword,
                username = cleanUsername,
                displayName = cleanName,
                lenId = generatedLinoId
            )
            val uid = if (authResult.isSuccess) {
                authResult.getOrThrow()
            } else {
                val authErr = authResult.exceptionOrNull()
                return Result.failure(
                    authErr ?: Exception("Please sign in with Google to create your cloud account.")
                )
            }

            // 2. Check for existing remote profile or claim new identity atomically
            val existingRemote = remoteAccountService.findRemoteUserById(uid) ?: userDao.getUserByIdSync(uid)
            val effectiveLinoId = existingRemote?.linoId?.ifBlank { null } ?: generatedLinoId

            // Explicitly ensure Firestore write succeeds for newly created accounts
            val regWriteRes = remoteAccountService.writeRegisteredUserToFirestore(
                uid = uid,
                username = cleanUsername,
                lenId = effectiveLinoId,
                displayName = cleanName,
                email = authEmail
            )

            val claimRes = remoteAccountService.claimUsernameAtomic(uid, cleanUsername)
            if (claimRes.isFailure && existingRemote == null) {
                val claimErr = claimRes.exceptionOrNull()
                if (claimErr?.message?.contains("taken", ignoreCase = true) == true) {
                    return Result.failure(claimErr)
                }
            }

            if (existingRemote == null) {
                val claimLenoRes = remoteAccountService.claimLenoIdAtomic(uid, generatedLinoId)
                if (claimLenoRes.isFailure) {
                    val claimErr = claimLenoRes.exceptionOrNull()
                    if (claimErr?.message?.contains("claimed", ignoreCase = true) == true) {
                        return Result.failure(claimErr)
                    }
                }
            }
            
            val currentFbUser = remoteAccountService.getCurrentAuthUser()
            val isBootstrapOwner = currentFbUser != null &&
                currentFbUser.isEmailVerified &&
                currentFbUser.email.equals("iliyasuusman730@gmail.com", ignoreCase = true)
            val resolvedEmail = currentFbUser?.email?.trim()?.lowercase()?.ifBlank { authEmail } ?: authEmail
            val resolvedGoogleEmail = currentFbUser?.email?.trim()?.lowercase().orEmpty()
            val photo = existingRemote?.avatarUrl?.ifBlank { currentFbUser?.photoUrl?.toString().orEmpty() }
                ?: currentFbUser?.photoUrl?.toString().orEmpty()
            val now = System.currentTimeMillis()
            val salt = AuthSecurity.generateSalt()
            val hash = AuthSecurity.hashPassword(cleanPassword, salt)
            val initialRole = when {
                isBootstrapOwner -> "OWNER"
                else -> existingRemote?.role?.ifBlank { null } ?: "USER"
            }

            val newUser = existingRemote?.copy(
                isCurrentUser = true,
                isOnline = true,
                lastSeen = now,
                username = cleanUsername,
                displayName = cleanName,
                googleEmail = if (resolvedGoogleEmail.isNotBlank()) resolvedGoogleEmail else existingRemote.googleEmail,
                passwordHash = if (hash.isNotBlank()) hash else existingRemote.passwordHash,
                passwordSalt = if (salt.isNotBlank()) salt else existingRemote.passwordSalt
            ) ?: UserEntity(
                userId = uid,
                username = cleanUsername,
                displayName = cleanName,
                bio = "Connecting on Leno ✨",
                avatarUrl = photo,
                isOnline = true,
                lastSeen = now,
                statusMessage = "Hey there! I am using Leno.",
                isCurrentUser = true,
                email = resolvedEmail,
                googleEmail = resolvedGoogleEmail,
                phoneNumber = "",
                normalizedPhoneNumber = "",
                createdAt = now,
                role = initialRole,
                password = "",
                passwordHash = hash,
                passwordSalt = salt,
                recoveryCode = AuthSecurity.generateRecoveryCode(),
                isOfficial = isBootstrapOwner,
                isVerified = isBootstrapOwner,
                linoId = effectiveLinoId
            )

            // 3. Write persistent public profile to Cloud Firestore at /users/{uid} and verify cloud sync succeeded
            val remoteRes = remoteAccountService.saveUserToRemote(newUser)
            if (remoteRes.isFailure) {
                val err = remoteRes.exceptionOrNull() ?: regWriteRes.exceptionOrNull()
                Log.e("LenoRepository", "Failed saving new account to Cloud Firestore: ${err?.message}", err)
                return Result.failure(
                    Exception(
                        "Could not save account to Leno Cloud: ${err?.message ?: "network error"}. Please check your internet connection and try again.",
                        err
                    )
                )
            }

            // 4. Switch active user atomically and record as device-owned account
            userDao.purgeDemoAccounts()
            accountBackupStore?.purgeDemoAccounts()
            userDao.switchActiveUserAtomic(newUser)
            sessionManager?.saveActiveUserId(uid)
            recordDeviceOwnedAccount(newUser)
            sessionManager?.saveAuthCredentials(resolvedEmail, "")
            sessionManager?.logSecurityEvent(
                uid,
                "Account Created",
                "Registered @$cleanUsername with backup recovery code"
            )

            settingsDao.insertUserSettings(
                UserSettingsEntity(
                    userId = uid,
                    lowDataMode = false,
                    notificationsEnabled = true,
                    darkThemeEnabled = false
                )
            )

            notificationDao.insertNotification(
                NotificationEntity(
                    id = "welcome_" + UUID.randomUUID().toString().take(8),
                    userId = uid,
                    title = "Welcome to Leno! 🎉",
                    body = "Your permanent Leno ID is $generatedLinoId. Share it with friends to connect and chat in real-time!",
                    type = "SYSTEM"
                )
            )

            ensureInitialOfficialMessageForUser(uid)

            return Result.success(newUser)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Failed to create account."
            Log.e("LenoRepository", "Leno registration error: $msg", e)
            return Result.failure(Exception(msg, e))
        } finally {
            isAuthTransitionInProgress = false
        }
    }

    suspend fun findUserByAnyIdentifier(identifier: String, hint: String = ""): UserEntity? {
        val cleanId = identifier.trim().removePrefix("@")
        val cleanHint = hint.trim()
        if (cleanId.isBlank() && cleanHint.isBlank()) return null

        val formattedLenoId = if (!cleanId.uppercase().startsWith("LEN-")) "LEN-${cleanId.uppercase()}" else cleanId.uppercase()
        val rawDigits = cleanId.uppercase().removePrefix("LEN-").removePrefix("LEN").replace("-", "").trim()
        val hintFormattedLeno = if (cleanHint.isNotBlank() && !cleanHint.uppercase().startsWith("LEN-")) "LEN-${cleanHint.uppercase()}" else cleanHint.uppercase()
        val hintDigits = cleanHint.uppercase().removePrefix("LEN-").removePrefix("LEN").replace("-", "").trim()

        // 1. Try local Room DB: prioritize exact username match, then exact Leno ID, then email
        var user = userDao.getUserByUsernameSync(cleanId.lowercase())
            ?: userDao.getUserByLinoIdSync(formattedLenoId)
            ?: userDao.getUserByLinoIdSync(cleanId)
            ?: (if (rawDigits.isNotBlank()) userDao.getUserByLinoIdSync(rawDigits) else null)
            ?: userDao.getUserByEmailSync(cleanId.lowercase())
            ?: userDao.getUserByGoogleEmailSync(cleanId.lowercase())
            ?: userDao.getUserByRecoveryEmailSync(cleanId.lowercase())
            ?: userDao.getUserByIdentifierSync(cleanId)
            ?: userDao.getUserByIdentifierSync(formattedLenoId)
            ?: userDao.getUserByDisplayNameSync(cleanId)

        // 2. Try by Hint (e.g. Full Name "Jibril Abdurrahman" or recovery code/email)
        if (user == null && cleanHint.isNotBlank()) {
            user = userDao.getUserByDisplayNameSync(cleanHint)
                ?: userDao.getUserByUsernameSync(cleanHint.lowercase())
                ?: userDao.getUserByLinoIdSync(hintFormattedLeno)
                ?: (if (hintDigits.isNotBlank()) userDao.getUserByLinoIdSync(hintDigits) else null)
                ?: userDao.getUserByEmailSync(cleanHint.lowercase())
                ?: userDao.getUserByGoogleEmailSync(cleanHint.lowercase())
                ?: userDao.getUserByRecoveryEmailSync(cleanHint.lowercase())
                ?: userDao.getUserByIdentifierSync(cleanHint)
        }

        // 3. Try AccountBackupStore
        if (user == null) {
            user = accountBackupStore?.findAccountByUsername(cleanId.lowercase())
                ?: accountBackupStore?.findAccount(cleanId)
                ?: accountBackupStore?.findAccount(formattedLenoId)
                ?: (if (rawDigits.isNotBlank()) accountBackupStore?.findAccount(rawDigits) else null)
                ?: (if (cleanHint.isNotBlank()) accountBackupStore?.findAccount(cleanHint) else null)
        }

        // 4. If matching the known owner account identifier, look up real existing owner
        if (user == null && (cleanId == "50638964" || cleanId.equals("LEN-50638964", true) || cleanId.equals("officialjaiby", true) || cleanHint.equals("officialjaiby", true))) {
            user = userDao.getUserByUsernameSync("officialjaiby")
                ?: userDao.getUserByLinoIdSync("LEN-50638964")
        }

        // 5. Try Remote Cloud Store (Firestore)
        if (user == null) {
            user = remoteAccountService.findRemoteUser(cleanId)
                ?: remoteAccountService.findRemoteUser(formattedLenoId)
                ?: (if (cleanHint.isNotBlank()) remoteAccountService.findRemoteUser(cleanHint) else null)
        }

        return user
    }

    suspend fun loginWithLino(identifier: String, password: String, targetUserId: String? = null): Result<UserEntity> {
        val cleanIdentifier = identifier.trim().removePrefix("@")
        val cleanPassword = password.trim()

        if (targetUserId != null) {
            return switchToAccount(targetUserId)
        }

        if (cleanIdentifier.isBlank() || cleanPassword.isBlank()) {
            return Result.failure(Exception("Please enter your Username / Leno ID and password."))
        }
        if (cleanPassword.length < 4) {
            return Result.failure(Exception("Password must be at least 4 characters."))
        }

        val rateLimitKey = "login_${cleanIdentifier.lowercase()}"
        val lockoutSec = sessionManager?.getLockoutRemainingSeconds(rateLimitKey) ?: 0L
        if (lockoutSec > 0L) {
            return Result.failure(Exception("Too many failed login attempts. Please wait ${lockoutSec}s before trying again."))
        }

        isAuthTransitionInProgress = true
        try {
            val formattedLenoId = if (!cleanIdentifier.uppercase().startsWith("LEN-")) "LEN-${cleanIdentifier.uppercase()}" else cleanIdentifier.uppercase()
            val localUser = findUserByAnyIdentifier(cleanIdentifier)
            val remoteUser = if (localUser == null) remoteAccountService.findRemoteUser(cleanIdentifier) else null

            // Collect all candidate accounts, strictly filtering out legacy dummy seed IDs
            val candidateUsers = linkedSetOf<UserEntity>().apply {
                if (cleanIdentifier.equals("officialjaiby", ignoreCase = true)) {
                    userDao.getUserByUsernameSync("officialjaiby")?.let { add(it) }
                }
                userDao.getUserByUsernameSync(cleanIdentifier.lowercase())?.let { add(it) }
                userDao.getUserByEmailSync(cleanIdentifier.lowercase())?.let { add(it) }
                userDao.getUserByGoogleEmailSync(cleanIdentifier.lowercase())?.let { add(it) }
                userDao.getUserByRecoveryEmailSync(cleanIdentifier.lowercase())?.let { add(it) }
                userDao.getUserByIdSync(cleanIdentifier)?.let { add(it) }
                userDao.getAllUsersByLinoIdSync(cleanIdentifier).forEach { add(it) }
                userDao.getAllUsersByLinoIdSync(formattedLenoId).forEach { add(it) }
                accountBackupStore?.findAccountByUsername(cleanIdentifier.lowercase())?.let { add(it) }
                accountBackupStore?.findAccount(cleanIdentifier)?.let { add(it) }
                accountBackupStore?.findAccount(formattedLenoId)?.let { add(it) }
                localUser?.let { add(it) }
                remoteUser?.let { add(it) }
            }.filter { it.userId != "user_len_50638964" }

            var matchedUser: UserEntity? = null
            var matchedUid: String? = null

            // 1. Instant local password verification across all candidates
            for (candidate in candidateUsers) {
                var isPasswordCorrect = false
                if (candidate.passwordHash.isNotBlank()) {
                    isPasswordCorrect = AuthSecurity.verifyPassword(cleanPassword, candidate.passwordHash, candidate.passwordSalt)
                }
                if (!isPasswordCorrect && candidate.password.isNotBlank()) {
                    isPasswordCorrect = candidate.password == cleanPassword
                }
                if (isPasswordCorrect) {
                    matchedUser = candidate
                    matchedUid = candidate.userId
                    break
                }
            }

            // 2. If not matched locally, check remote cloud store (Firestore) and verify password
            if (matchedUid == null) {
                val remote = remoteUser ?: remoteAccountService.findRemoteUser(cleanIdentifier)
                    ?: remoteAccountService.findRemoteUser(formattedLenoId)
                if (remote != null) {
                    var isPasswordCorrect = false
                    if (remote.passwordHash.isNotBlank()) {
                        isPasswordCorrect = AuthSecurity.verifyPassword(cleanPassword, remote.passwordHash, remote.passwordSalt)
                    }
                    if (!isPasswordCorrect && remote.password.isNotBlank()) {
                        isPasswordCorrect = remote.password == cleanPassword
                    }
                    if (isPasswordCorrect) {
                        matchedUser = remote
                        matchedUid = remote.userId
                    }
                }
            }

            // 3. Fallback: Single direct Firebase Auth verification when recovery sync is not pending
            val hasRecoveryPending = candidateUsers.any { sessionManager?.isRecoveryPasswordPending(it.userId) == true } ||
                (remoteUser != null && sessionManager?.isRecoveryPasswordPending(remoteUser.userId) == true)
            if (matchedUid == null && cleanPassword.length >= 6 && !hasRecoveryPending) {
                val primaryCandidateEmail = remoteUser?.email?.takeIf { it.isNotBlank() }?.lowercase()
                    ?: candidateUsers.firstOrNull { it.email.isNotBlank() }?.email?.lowercase()
                val targetEmail = when {
                    cleanIdentifier.contains("@") -> cleanIdentifier.lowercase()
                    !primaryCandidateEmail.isNullOrBlank() -> primaryCandidateEmail
                    else -> "${cleanIdentifier.lowercase()}@leno.chat"
                }

                var isFirebaseAuthPasswordError = false
                try {
                    val res = kotlinx.coroutines.withTimeoutOrNull(8000L) {
                        remoteAccountService.signInWithFirebaseAuth(targetEmail, cleanPassword)
                    }
                    if (res != null) {
                        if (res.isSuccess) {
                            val authUid = res.getOrThrow()
                            matchedUid = authUid
                            matchedUser = remoteAccountService.findRemoteUserById(authUid)
                                ?: userDao.getUserByIdSync(authUid)
                                ?: accountBackupStore?.findAccount(authUid)
                        } else {
                            val err = res.exceptionOrNull()?.message ?: ""
                            val isCredErr = err.contains("password", ignoreCase = true) ||
                                    err.contains("credential", ignoreCase = true) ||
                                    err.contains("malformed", ignoreCase = true) ||
                                    err.contains("expired", ignoreCase = true) ||
                                    err.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true)
                            if (isCredErr) {
                                isFirebaseAuthPasswordError = true
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (matchedUid == null && isFirebaseAuthPasswordError) {
                    val failCount = sessionManager?.recordFailedAttempt(rateLimitKey) ?: 0
                    val msg = if (failCount >= SessionManager.MAX_FAILED_ATTEMPTS) {
                        "Too many failed login attempts. Account sign-in temporarily locked for 60 seconds."
                    } else {
                        "Incorrect password for '$cleanIdentifier'. Please check your password or tap 'Forgot Password?' to reset."
                    }
                    return Result.failure(Exception(msg))
                }
            }

            if (matchedUid == null) {
                val hasAccount = candidateUsers.isNotEmpty() || remoteUser != null
                val errorMsg = if (hasAccount) {
                    val failCount = sessionManager?.recordFailedAttempt(rateLimitKey) ?: 0
                    if (failCount >= SessionManager.MAX_FAILED_ATTEMPTS) {
                        "Too many failed login attempts. Account sign-in temporarily locked for 60 seconds."
                    } else {
                        "Incorrect password for '$cleanIdentifier'. Please check your password or tap 'Forgot Password?' to reset."
                    }
                } else {
                    "Account not found for '$cleanIdentifier'. Please check your username/email or create a new account."
                }
                return Result.failure(Exception(errorMsg))
            }

            val authenticatedUid = matchedUid
            val candidateUser = matchedUser

            // 2. Authoritative profile load from Cloud Firestore using authenticated Firebase UID
            var profile = remoteAccountService.findRemoteUserById(authenticatedUid)
            if (profile == null) {
                // Check local database strictly for this exact authenticated UID or candidate
                val localForSameUid = userDao.getUserByIdSync(authenticatedUid) ?: candidateUser?.takeIf { it.userId == authenticatedUid }
                if (localForSameUid != null) {
                    profile = localForSameUid
                    remoteAccountService.saveUserToRemote(localForSameUid)
                } else if (candidateUser != null) {
                    val oldUid = candidateUser.userId
                    val linked = candidateUser.copy(userId = authenticatedUid)
                    if (oldUid != authenticatedUid && oldUid.isNotBlank()) {
                        messageDao.migrateSenderId(oldUid, authenticatedUid)
                        messageDao.migrateReceiverId(oldUid, authenticatedUid)
                        messageBackupStore?.migrateUserId(oldUid, authenticatedUid)
                        userDao.deleteUser(oldUid)
                        accountBackupStore?.deleteAccount(oldUid)
                    }
                    userDao.insertUser(linked.copy(isCurrentUser = false))
                    remoteAccountService.saveUserToRemote(linked)
                    profile = linked
                } else {
                    // If account exists in Firebase Auth but Firestore doc was missing, reconstruct and save to /users/{authenticatedUid}
                    val fbUser = remoteAccountService.getCurrentAuthUser()
                    val usernameFromEmail = if (fbUser != null && !fbUser.email.isNullOrBlank() && fbUser.email!!.endsWith("@leno.chat")) {
                        fbUser.email!!.substringBefore("@leno.chat")
                    } else if (cleanIdentifier.contains("@")) {
                        cleanIdentifier.substringBefore("@").lowercase()
                    } else {
                        cleanIdentifier.lowercase()
                    }
                    val dName = fbUser?.displayName?.ifBlank { null } ?: usernameFromEmail.replaceFirstChar { it.uppercase() }
                    val pPhoto = fbUser?.photoUrl?.toString() ?: ""
                    val now = System.currentTimeMillis()
                    val isOwner = authenticatedUid == "usr_officialjaiby_2026" ||
                        (fbUser?.isEmailVerified == true && fbUser.email.equals("iliyasuusman730@gmail.com", ignoreCase = true))
                    val linoId = if (isOwner) "LEN-50638964" else generateUniqueLinoId()
                    val salt = AuthSecurity.generateSalt()
                    val hash = AuthSecurity.hashPassword(cleanPassword, salt)
                    val reconstructed = UserEntity(
                        userId = authenticatedUid,
                        username = usernameFromEmail,
                        displayName = if (isOwner && dName.equals("Officialjaiby", true)) "Official Jaiby" else dName,
                        bio = if (isOwner) "Leno Platform Founder & Developer 🌟" else "Connecting on Leno ✨",
                        avatarUrl = pPhoto,
                        isOnline = true,
                        lastSeen = now,
                        statusMessage = if (isOwner) "Founder & Owner of Leno" else "Hey there! I am using Leno.",
                        isCurrentUser = true,
                        email = fbUser?.email ?: "${usernameFromEmail}@leno.chat",
                        phoneNumber = fbUser?.phoneNumber ?: "",
                        normalizedPhoneNumber = fbUser?.phoneNumber ?: "",
                        createdAt = now,
                        role = if (isOwner) "OWNER" else "USER",
                        password = "",
                        passwordHash = hash,
                        passwordSalt = salt,
                        recoveryCode = AuthSecurity.generateRecoveryCode(),
                        isOfficial = isOwner,
                        linoId = linoId
                    )
                    remoteAccountService.saveUserToRemote(reconstructed)
                    remoteAccountService.claimUsernameAtomic(authenticatedUid, usernameFromEmail)
                    remoteAccountService.claimLenoIdAtomic(authenticatedUid, linoId)
                    profile = reconstructed
                }
            }

            val currentProfile = profile ?: return Result.failure(Exception("No Leno user profile found for this account. Please register first."))
            val effectiveProfile = if (currentProfile.userId.isBlank()) currentProfile.copy(userId = authenticatedUid) else currentProfile

            // Sync Firebase Auth session without redundant signUp / signIn retries
            try {
                val emailToAuth = effectiveProfile.email.ifBlank { "${effectiveProfile.username.lowercase()}@leno.chat" }
                val currentFbUser = remoteAccountService.getCurrentAuthUser()
                val recoveryPending = sessionManager?.isRecoveryPasswordPending(authenticatedUid) == true
                if (recoveryPending) {
                    if (currentFbUser != null && currentFbUser.uid == authenticatedUid) {
                        val upd = remoteAccountService.updateFirebasePassword(cleanPassword)
                        if (upd.isSuccess) {
                            sessionManager?.clearRecoveryPasswordPending(authenticatedUid)
                        }
                    }
                } else if (cleanPassword.length >= 6 &&
                    (currentFbUser == null || !currentFbUser.email.equals(emailToAuth, ignoreCase = true))
                ) {
                    val authRes = remoteAccountService.signInWithFirebaseAuth(emailToAuth, cleanPassword)
                    if (authRes.isSuccess) {
                        sessionManager?.clearRecoveryPasswordPending(authenticatedUid)
                    } else {
                        val err = authRes.exceptionOrNull()?.message ?: ""
                        val isCredErr = err.contains("password", ignoreCase = true) ||
                                err.contains("credential", ignoreCase = true) ||
                                err.contains("malformed", ignoreCase = true) ||
                                err.contains("expired", ignoreCase = true) ||
                                err.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true)
                        if (isCredErr) {
                            sessionManager?.markRecoveryPasswordPending(authenticatedUid)
                        }
                    }
                }
            } catch (_: Exception) {}

            profile = effectiveProfile

            // 3. Purge demo accounts
            userDao.purgeDemoAccounts()
            accountBackupStore?.purgeDemoAccounts()

            val salt = if (profile.passwordSalt.isNotBlank()) profile.passwordSalt else AuthSecurity.generateSalt()
            val hash = if (profile.passwordHash.isNotBlank()) profile.passwordHash else AuthSecurity.hashPassword(cleanPassword, salt)
            val ensuredRecoveryCode = profile.recoveryCode.ifBlank { AuthSecurity.generateRecoveryCode() }

            // 4. Switch active user atomically & record device-owned account
            val loggedIn = profile.copy(
                isCurrentUser = true,
                isOnline = true,
                passwordHash = hash,
                passwordSalt = salt,
                recoveryCode = ensuredRecoveryCode,
                lastSeen = System.currentTimeMillis()
            )
            userDao.switchActiveUserAtomic(loggedIn)
            sessionManager?.saveActiveUserId(authenticatedUid)
            recordDeviceOwnedAccount(loggedIn)
            sessionManager?.clearFailedAttempts(rateLimitKey)
            sessionManager?.logSecurityEvent(
                authenticatedUid,
                "Signed In",
                "Signed in as @${loggedIn.username}"
            )
            userDao.updateOnlineStatus(authenticatedUid, true)
            if (profile.recoveryCode.isBlank()) {
                try {
                    remoteAccountService.saveUserToRemote(loggedIn)
                } catch (_: Exception) {}
            }

            // 5. Consolidate any duplicate local records matching same non-blank username or Leno ID, and migrate their messages
            val duplicates = userDao.getAllUsersSync().filter {
                it.userId != authenticatedUid && (
                    (candidateUser != null && candidateUser.userId.isNotBlank() && it.userId == candidateUser.userId) ||
                    (loggedIn.username.isNotBlank() && it.username.isNotBlank() && it.username.equals(loggedIn.username, ignoreCase = true)) ||
                    (loggedIn.linoId.isNotBlank() && it.linoId.isNotBlank() && it.linoId.equals(loggedIn.linoId, ignoreCase = true)) ||
                    (loggedIn.isOwner && (it.userId == "usr_officialjaiby_2026" || it.username.equals("officialjaiby", ignoreCase = true)))
                )
            }
            for (dup in duplicates) {
                if (dup.userId.isBlank() || dup.userId == authenticatedUid) continue
                userDao.deleteUser(dup.userId)
                accountBackupStore?.deleteAccount(dup.userId)
                sessionManager?.removeDeviceAccountId(dup.userId)
                messageDao.migrateSenderId(dup.userId, authenticatedUid)
                messageDao.migrateReceiverId(dup.userId, authenticatedUid)
                messageBackupStore?.migrateUserId(dup.userId, authenticatedUid)
            }
            val primaryEmailToSave = if (loggedIn.email.isNotBlank()) loggedIn.email else "${loggedIn.username.lowercase()}@leno.chat"
            sessionManager?.saveAuthCredentials(primaryEmailToSave, "")
            try {
                syncRemoteContactsIntoRoom(authenticatedUid)
                syncAllUserChatsAndMessages(authenticatedUid)
                ensureInitialOfficialMessageForUser(authenticatedUid)
            } catch (syncEx: Exception) {
                Log.w("LenoRepository", "Non-fatal post-login sync notice: ${syncEx.message}")
            }
            return Result.success(loggedIn)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Login failed. Check your credentials."
            Log.e("LenoRepository", "loginWithLino error: $msg", e)
            return Result.failure(Exception(msg, e))
        } finally {
            isAuthTransitionInProgress = false
        }
    }

    suspend fun registerUser(
        displayName: String,
        username: String,
        email: String,
        password: String,
        phoneNumber: String = "",
        avatarUrl: String = "",
        bio: String = "Connecting on Leno ✨"
    ): Result<UserEntity> {
        val cleanUsername = username.trim().replace("@", "").lowercase()
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = phoneNumber.trim()
        val normPhone = PhoneUtils.normalizePhoneNumber(cleanPhone)

        if (!com.example.util.NetworkUtils.isNetworkAvailable(context)) {
            return Result.failure(Exception("No internet connection detected. An active internet or mobile data connection is required to register."))
        }

        if (cleanUsername == "leno" || cleanUsername == "official_leno" || cleanUsername.contains("officialleno") || displayName.trim().equals("Official Leno", ignoreCase = true)) {
            return Result.failure(Exception("Cannot register using the Official Leno identity. This username/identity is reserved for system service."))
        }

        if (cleanUsername.isBlank()) {
            return Result.failure(Exception("Username cannot be empty."))
        }
        if (password.length < 6) {
            return Result.failure(Exception("Password must be at least 6 characters."))
        }

        val availCheck = checkUsernameAvailability(cleanUsername)
        if (availCheck.isFailure) {
            return Result.failure(availCheck.exceptionOrNull() ?: Exception("This username is already taken."))
        }

        if (cleanEmail.isNotBlank()) {
            val isEmailTaken = remoteAccountService.isEmailTakenInFirebaseAuth(cleanEmail)
            if (isEmailTaken) {
                return Result.failure(Exception("This email is already in use by another account. Please log in."))
            }
        }

        val existingRoomUser = userDao.getUserByUsernameSync(cleanUsername)
            ?: userDao.getUserByIdentifierSync(cleanUsername)
            ?: accountBackupStore?.findAccount(cleanUsername)
            ?: (if (cleanPhone.isNotBlank()) userDao.getUserByPhoneSync(cleanPhone, normPhone) else null)
        if (existingRoomUser != null && existingRoomUser.passwordHash.isNotBlank()) {
            val valid = AuthSecurity.verifyPassword(password, existingRoomUser.passwordHash, existingRoomUser.passwordSalt)
            if (!valid) {
                return Result.failure(Exception("This account already exists. Please log in."))
            }
        }

        isAuthTransitionInProgress = true
        try {
            val authEmail = if (cleanEmail.isNotBlank()) cleanEmail else "${cleanUsername}@leno.chat"
            val effectiveLinoId = generateUniqueLinoId()
            val authResult = remoteAccountService.signUpWithFirebaseAuth(
                email = authEmail,
                password = password,
                username = cleanUsername,
                displayName = displayName.ifBlank { cleanUsername },
                lenId = effectiveLinoId
            )
            val uid = if (authResult.isSuccess) {
                authResult.getOrThrow()
            } else {
                val authErr = authResult.exceptionOrNull()
                return Result.failure(
                    authErr ?: Exception("Please sign in with Google to create your cloud account.")
                )
            }

            val regWriteRes = remoteAccountService.writeRegisteredUserToFirestore(
                uid = uid,
                username = cleanUsername,
                lenId = effectiveLinoId,
                displayName = displayName.ifBlank { cleanUsername },
                email = authEmail
            )

            val existingRemote = remoteAccountService.findRemoteUserById(uid) ?: userDao.getUserByIdSync(uid)

            val claimRes = remoteAccountService.claimUsernameAtomic(uid, cleanUsername)
            if (claimRes.isFailure && existingRemote == null) {
                return Result.failure(claimRes.exceptionOrNull() ?: Exception("This username is already taken."))
            }
            if (existingRemote == null) {
                val claimLenoRes = remoteAccountService.claimLenoIdAtomic(uid, effectiveLinoId)
                if (claimLenoRes.isFailure) {
                    return Result.failure(claimLenoRes.exceptionOrNull() ?: Exception("This Leno ID is already taken."))
                }
            }

            val photo = avatarUrl.ifBlank { "" }
            val now = System.currentTimeMillis()
            val salt = AuthSecurity.generateSalt()
            val hash = AuthSecurity.hashPassword(password, salt)

            val newUser = existingRemote?.copy(
                isCurrentUser = true,
                isOnline = true,
                lastSeen = now,
                passwordHash = if (hash.isNotBlank()) hash else existingRemote.passwordHash,
                passwordSalt = if (salt.isNotBlank()) salt else existingRemote.passwordSalt
            ) ?: UserEntity(
                userId = uid,
                username = cleanUsername,
                displayName = displayName.ifBlank { cleanUsername },
                bio = bio.ifBlank { "Connecting on Leno ✨" },
                avatarUrl = photo,
                isOnline = true,
                lastSeen = now,
                statusMessage = "Hey there! I am using Leno.",
                isCurrentUser = true,
                email = authEmail,
                phoneNumber = cleanPhone,
                normalizedPhoneNumber = normPhone,
                createdAt = now,
                password = "",
                passwordHash = hash,
                passwordSalt = salt,
                recoveryCode = AuthSecurity.generateRecoveryCode(),
                isOfficial = false,
                linoId = effectiveLinoId
            )

            val remoteRes = remoteAccountService.saveUserToRemote(newUser)
            if (remoteRes.isFailure) {
                val err = remoteRes.exceptionOrNull() ?: regWriteRes.exceptionOrNull()
                Log.e("LenoRepository", "Failed saving user to Cloud Firestore in registerUser: ${err?.message}", err)
                return Result.failure(
                    Exception(
                        "Could not save account to Leno Cloud: ${err?.message ?: "network error"}. Please check your internet connection and try again.",
                        err
                    )
                )
            }

            userDao.purgeDemoAccounts()
            accountBackupStore?.purgeDemoAccounts()
            userDao.switchActiveUserAtomic(newUser)
            sessionManager?.saveActiveUserId(uid)
            recordDeviceOwnedAccount(newUser)

            settingsDao.insertUserSettings(
                UserSettingsEntity(
                    userId = uid,
                    lowDataMode = false,
                    notificationsEnabled = true,
                    darkThemeEnabled = false
                )
            )

            notificationDao.insertNotification(
                NotificationEntity(
                    id = "welcome_" + UUID.randomUUID().toString().take(8),
                    userId = uid,
                    title = "Welcome to Leno!",
                    body = "Your permanent account @$cleanUsername (Leno ID: $effectiveLinoId) is ready. Connect and chat with friends!",
                    type = "SYSTEM"
                )
            )

            ensureInitialOfficialMessageForUser(uid)

            return Result.success(newUser)
        } finally {
            isAuthTransitionInProgress = false
        }
    }

    suspend fun loginUser(identifier: String, password: String): Result<UserEntity> {
        return loginWithLino(identifier, password)
    }

    suspend fun resetPassword(identifier: String, newPassword: String): Result<Unit> {
        val cleanIdentifier = identifier.trim().removePrefix("@")
        if (cleanIdentifier.isBlank()) {
            return Result.failure(Exception("Please enter your Username, Leno ID, or Email."))
        }
        val localUser = findUserByAnyIdentifier(cleanIdentifier)
            ?: return Result.failure(Exception("No account found for '$cleanIdentifier'."))

        val targetEmail = listOf(localUser.recoveryEmail, localUser.googleEmail, localUser.email)
            .firstOrNull { it.isNotBlank() && !it.trim().lowercase().endsWith("@leno.chat") }

        return if (!targetEmail.isNullOrBlank()) {
            try {
                remoteAccountService.sendPasswordResetEmail(targetEmail)
            } catch (_: Exception) {}
            Result.success(Unit)
        } else {
            Result.failure(Exception("Password reset requires verification with your Backup Recovery Code or connected Recovery / Google Email in 'Forgot Password?'."))
        }
    }

    suspend fun recoverAccount(
        identifier: String,
        verificationHint: String,
        newPassword: String
    ): Result<UserEntity> {
        val cleanIdentifier = identifier.trim().removePrefix("@")
        val cleanHint = verificationHint.trim()
        val cleanNewPass = newPassword.trim()

        if (cleanIdentifier.isBlank()) {
            return Result.failure(Exception("Please enter your Username, Leno ID, or Email."))
        }
        if (cleanHint.isBlank()) {
            return Result.failure(Exception("Verification required. Enter your Backup Recovery Code (REC-...), connected Recovery Email, or Google Email."))
        }
        if (cleanNewPass.length < 6) {
            return Result.failure(Exception("New password must be at least 6 characters."))
        }

        val rateLimitKey = "recover_${cleanIdentifier.lowercase()}"
        val lockoutSec = sessionManager?.getLockoutRemainingSeconds(rateLimitKey) ?: 0L
        if (lockoutSec > 0L) {
            return Result.failure(Exception("Too many failed recovery attempts. Please wait ${lockoutSec}s before trying again."))
        }

        // Lookup strictly by account identifier (never resolve via verification hint)
        val localUser = findUserByAnyIdentifier(cleanIdentifier)
            ?: return Result.failure(Exception("No account found for '$cleanIdentifier'."))

        val hasAnyRecoverySecret = localUser.recoveryCode.isNotBlank() ||
            localUser.recoveryEmail.isNotBlank() ||
            localUser.googleEmail.isNotBlank() ||
            (localUser.email.isNotBlank() && !localUser.email.trim().lowercase().endsWith("@leno.chat"))

        if (!hasAnyRecoverySecret) {
            return Result.failure(Exception("This account does not have a recovery method or backup code configured. Please sign in with Google or contact Leno Support."))
        }

        if (!AuthSecurity.verifyRecoveryProof(localUser, cleanHint)) {
            val fails = sessionManager?.recordFailedAttempt(rateLimitKey) ?: 0
            val msg = if (fails >= SessionManager.MAX_FAILED_ATTEMPTS) {
                "Too many failed recovery attempts. Account recovery temporarily locked for 60 seconds."
            } else {
                "Verification failed. The Backup Recovery Code or connected Recovery / Google Email does not match this account."
            }
            return Result.failure(Exception(msg))
        }

        sessionManager?.clearFailedAttempts(rateLimitKey)

        val salt = AuthSecurity.generateSalt()
        val hash = AuthSecurity.hashPassword(cleanNewPass, salt)
        val ensuredRecoveryCode = localUser.recoveryCode.ifBlank { AuthSecurity.generateRecoveryCode() }
        val updatedUser = localUser.copy(
            password = "",
            passwordHash = hash,
            passwordSalt = salt,
            recoveryCode = ensuredRecoveryCode
        )
        userDao.insertUser(updatedUser)
        recordDeviceOwnedAccount(updatedUser)
        try {
            remoteAccountService.saveUserToRemote(updatedUser)
            val fbUser = remoteAccountService.getCurrentAuthUser()
            if (fbUser != null && fbUser.uid == updatedUser.userId) {
                val updRes = remoteAccountService.updateFirebasePassword(cleanNewPass)
                if (updRes.isSuccess) {
                    sessionManager?.clearRecoveryPasswordPending(updatedUser.userId)
                } else {
                    sessionManager?.markRecoveryPasswordPending(updatedUser.userId)
                }
            } else {
                sessionManager?.markRecoveryPasswordPending(updatedUser.userId)
            }
        } catch (e: Exception) {
            sessionManager?.markRecoveryPasswordPending(updatedUser.userId)
            Log.w("LenoRepository", "Remote sync notice: ${e.message}")
        }

        sessionManager?.logSecurityEvent(
            updatedUser.userId,
            "Account Recovered",
            "Password reset after verified identity check"
        )
        notificationDao.insertNotification(
            NotificationEntity(
                id = "sec_recover_" + UUID.randomUUID().toString().take(8),
                userId = updatedUser.userId,
                title = "Password Reset via Recovery ✓",
                body = "Your account password was recently reset via verified account recovery. If this wasn't you, change your password and regenerate your backup code immediately.",
                type = "SYSTEM"
            )
        )
        return Result.success(updatedUser)
    }

    // ==========================================
    // ACCOUNT PROTECTION (GOOGLE & EMAIL RECOVERY)
    // ==========================================

    suspend fun connectGoogleAccount(userId: String, firebaseUser: FirebaseUser): Result<UserEntity> {
        val cleanEmail = firebaseUser.email?.trim()?.lowercase().orEmpty()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(Exception("Google account did not provide a valid email address."))
        }

        val existingWithGoogle = userDao.getUserByGoogleEmailSync(cleanEmail)
        if (existingWithGoogle != null && existingWithGoogle.userId != userId) {
            return Result.failure(Exception("This Google account is already linked to another Leno account (@${existingWithGoogle.username})."))
        }

        val currentUser = userDao.getUserByIdSync(userId)
            ?: return Result.failure(Exception("User not found."))

        val updated = currentUser.copy(googleEmail = cleanEmail)
        userDao.insertUser(updated)
        recordDeviceOwnedAccount(updated)
        remoteAccountService.saveUserToRemote(updated)
        sessionManager?.logSecurityEvent(userId, "Google Recovery Connected", "Linked verified $cleanEmail")

        notificationDao.insertNotification(
            NotificationEntity(
                id = "prot_google_" + UUID.randomUUID().toString().take(8),
                userId = userId,
                title = "Account Protected ✓",
                body = "Verified Google account ($cleanEmail) connected as a secure sign-in and recovery method.",
                type = "SYSTEM"
            )
        )

        return Result.success(updated)
    }

    suspend fun connectRecoveryEmail(userId: String, recoveryEmail: String): Result<UserEntity> {
        val cleanEmail = recoveryEmail.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(Exception("Please enter a valid recovery email address."))
        }

        val existingWithEmail = userDao.getUserByRecoveryEmailSync(cleanEmail)
        if (existingWithEmail != null && existingWithEmail.userId != userId) {
            return Result.failure(Exception("This email address is already linked to another Leno account (@${existingWithEmail.username})."))
        }

        val currentUser = userDao.getUserByIdSync(userId)
            ?: return Result.failure(Exception("User not found."))

        val updated = currentUser.copy(recoveryEmail = cleanEmail)
        userDao.insertUser(updated)
        recordDeviceOwnedAccount(updated)
        remoteAccountService.saveUserToRemote(updated)
        sessionManager?.logSecurityEvent(userId, "Recovery Email Added", "Linked $cleanEmail")

        notificationDao.insertNotification(
            NotificationEntity(
                id = "prot_email_" + UUID.randomUUID().toString().take(8),
                userId = userId,
                title = "Account Protected ✓",
                body = "Recovery email ($cleanEmail) added as a secure recovery method.",
                type = "SYSTEM"
            )
        )

        return Result.success(updated)
    }

    suspend fun removeRecoveryMethod(userId: String, methodType: String): Result<UserEntity> {
        val currentUser = userDao.getUserByIdSync(userId)
            ?: return Result.failure(Exception("User not found."))

        val isGoogle = methodType.equals("GOOGLE", ignoreCase = true)
        val isEmail = methodType.equals("EMAIL", ignoreCase = true)

        if (isGoogle) {
            if (currentUser.recoveryEmail.isBlank()) {
                return Result.failure(Exception("Cannot remove this recovery method because it is your only active recovery method. Add an email address first to keep your account protected."))
            }
            val updated = currentUser.copy(googleEmail = "")
            userDao.insertUser(updated)
            recordDeviceOwnedAccount(updated)
            remoteAccountService.saveUserToRemote(updated)
            sessionManager?.logSecurityEvent(userId, "Google Recovery Removed", "Disconnected Google recovery method")
            return Result.success(updated)
        } else if (isEmail) {
            if (currentUser.googleEmail.isBlank()) {
                return Result.failure(Exception("Cannot remove this recovery method because it is your only active recovery method. Connect Google first to keep your account protected."))
            }
            val updated = currentUser.copy(recoveryEmail = "")
            userDao.insertUser(updated)
            recordDeviceOwnedAccount(updated)
            remoteAccountService.saveUserToRemote(updated)
            sessionManager?.logSecurityEvent(userId, "Recovery Email Removed", "Disconnected recovery email method")
            return Result.success(updated)
        }

        return Result.failure(Exception("Invalid recovery method specified."))
    }

    suspend fun loginWithAuthenticatedGoogleUser(firebaseUser: FirebaseUser): Result<UserEntity> {
        val cleanEmail = firebaseUser.email?.trim()?.lowercase().orEmpty()
        if (cleanEmail.isBlank()) {
            return Result.failure(Exception("Your Google account did not provide an email address."))
        }
        val authUid = firebaseUser.uid
        val isBootstrapOwner = firebaseUser.isEmailVerified && cleanEmail == "iliyasuusman730@gmail.com"

        isAuthTransitionInProgress = true
        try {
            // Look up primarily by canonical Firebase Auth UID so request.auth.uid == userId
            var matchedUser = remoteAccountService.findRemoteUserById(authUid)
                ?: userDao.getUserByIdSync(authUid)
                ?: accountBackupStore?.findAccount(authUid)
                ?: userDao.getUserByGoogleEmailSync(cleanEmail)
                ?: userDao.getUserByEmailSync(cleanEmail)
                ?: accountBackupStore?.findAccount(cleanEmail)
                ?: remoteAccountService.findRemoteUser(cleanEmail)

            if (matchedUser != null && matchedUser.userId != authUid) {
                val oldUid = matchedUser.userId
                matchedUser = matchedUser.copy(userId = authUid)
                if (oldUid.isNotBlank() && oldUid != OFFICIAL_LENO_ID) {
                    messageDao.migrateSenderId(oldUid, authUid)
                    messageDao.migrateReceiverId(oldUid, authUid)
                    messageBackupStore?.migrateUserId(oldUid, authUid)
                }
            }

            if (matchedUser == null) {
                val emailPrefix = cleanEmail.substringBefore("@").replace(".", "_").filter { it.isLetterOrDigit() || it == '_' }
                val baseUname = if (emailPrefix.length >= 3) emailPrefix.lowercase().take(15) else "user_${(1000..9999).random()}"
                val dName = firebaseUser.displayName?.trim()?.ifBlank { null }
                    ?: emailPrefix.ifBlank { baseUname }.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                val genLinoId = if (isBootstrapOwner) "LEN-50638964" else generateUniqueLinoId()
                val now = System.currentTimeMillis()
                val initialRole = if (isBootstrapOwner) "OWNER" else "USER"

                var finalUname = baseUname
                val unameClaim = remoteAccountService.claimUsernameAtomic(authUid, finalUname)
                if (unameClaim.isFailure) {
                    finalUname = "${baseUname.take(11)}_${(100..999).random()}"
                    remoteAccountService.claimUsernameAtomic(authUid, finalUname)
                }
                remoteAccountService.claimLenoIdAtomic(authUid, genLinoId)

                val newGoogleUser = UserEntity(
                    userId = authUid,
                    username = finalUname,
                    displayName = dName,
                    bio = if (isBootstrapOwner) "Leno Platform Founder & Developer 🌟" else "Connecting on Leno ✨",
                    avatarUrl = firebaseUser.photoUrl?.toString().orEmpty(),
                    isOnline = true,
                    lastSeen = now,
                    statusMessage = "Hey there! I am using Leno.",
                    isCurrentUser = true,
                    email = cleanEmail,
                    googleEmail = cleanEmail,
                    phoneNumber = "",
                    normalizedPhoneNumber = "",
                    createdAt = now,
                    role = initialRole,
                    password = "",
                    passwordHash = "",
                    passwordSalt = "",
                    recoveryCode = AuthSecurity.generateRecoveryCode(),
                    isOfficial = isBootstrapOwner,
                    isVerified = isBootstrapOwner,
                    linoId = genLinoId
                )

                val remoteRes = remoteAccountService.saveUserToRemote(newGoogleUser)
                if (remoteRes.isFailure) {
                    val err = remoteRes.exceptionOrNull()
                    return Result.failure(
                        Exception(
                            "Could not save Google account to Leno Cloud: ${err?.message ?: "network error"}. Please check your internet connection and try again.",
                            err
                        )
                    )
                }

                userDao.switchActiveUserAtomic(newGoogleUser)
                sessionManager?.saveActiveUserId(newGoogleUser.userId)
                sessionManager?.saveAuthCredentials(cleanEmail, "")
                recordDeviceOwnedAccount(newGoogleUser)

                settingsDao.insertUserSettings(
                    UserSettingsEntity(
                        userId = authUid,
                        lowDataMode = false,
                        notificationsEnabled = true,
                        darkThemeEnabled = false
                    )
                )

                notificationDao.insertNotification(
                    NotificationEntity(
                        id = "welcome_g_" + UUID.randomUUID().toString().take(8),
                        userId = authUid,
                        title = "Welcome to Leno! 🎉",
                        body = "Your verified Google account ($cleanEmail) is ready. Your Leno ID is $genLinoId.",
                        type = "SYSTEM"
                    )
                )

                ensureInitialOfficialMessageForUser(authUid)
                syncAllUserChatsAndMessages(authUid)
                return Result.success(newGoogleUser)
            }

            // Restore EXISTING account session atomically
            val ensuredCode = matchedUser.recoveryCode.ifBlank { AuthSecurity.generateRecoveryCode() }
            val loggedInUser = matchedUser.copy(
                userId = authUid,
                isCurrentUser = true,
                isOnline = true,
                email = matchedUser.email.ifBlank { cleanEmail },
                googleEmail = cleanEmail,
                role = if (isBootstrapOwner) "OWNER" else matchedUser.role,
                isOfficial = if (isBootstrapOwner) true else matchedUser.isOfficial,
                isVerified = if (isBootstrapOwner) true else matchedUser.isVerified,
                recoveryCode = ensuredCode,
                lastSeen = System.currentTimeMillis()
            )
            userDao.switchActiveUserAtomic(loggedInUser)
            sessionManager?.saveActiveUserId(authUid)
            sessionManager?.saveAuthCredentials(cleanEmail, "")
            recordDeviceOwnedAccount(loggedInUser)
            sessionManager?.logSecurityEvent(authUid, "Signed In via Google", "Verified Google account $cleanEmail")
            remoteAccountService.saveUserToRemote(loggedInUser)
            syncRemoteContactsIntoRoom(authUid)
            syncAllUserChatsAndMessages(authUid)
            ensureInitialOfficialMessageForUser(authUid)

            return Result.success(loggedInUser)
        } finally {
            isAuthTransitionInProgress = false
        }
    }

    // MESSAGING
    suspend fun updateUserPresence(isOnline: Boolean) {
        val currentUid = userDao.getCurrentUserSync()?.userId ?: return
        val now = System.currentTimeMillis()
        userDao.updateOnlineStatus(currentUid, isOnline, now)
        CoroutineScope(Dispatchers.IO).launch {
            remoteAccountService.updateUserPresenceInRemote(currentUid, isOnline, now)
        }
    }

    suspend fun setChatTypingStatus(myUid: String, partnerId: String, isTyping: Boolean) {
        if (myUid.isBlank() || partnerId.isBlank() || partnerId == OFFICIAL_LENO_ID) return
        val chatId = remoteAccountService.getChatId(myUid, partnerId)
        remoteAccountService.setTypingStatusInRemote(chatId, myUid, isTyping)
    }

    fun listenToChatTyping(
        myUid: String,
        partnerId: String,
        onTyping: (Boolean) -> Unit
    ): ListenerRegistration? {
        if (myUid.isBlank() || partnerId.isBlank() || partnerId == OFFICIAL_LENO_ID) return null
        val chatId = remoteAccountService.getChatId(myUid, partnerId)
        return remoteAccountService.listenToChatTyping(chatId, partnerId, onTyping)
    }

    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        text: String,
        imageUrl: String? = null,
        audioUrl: String? = null,
        audioDurationSeconds: Int = 0
    ) {
        val canonicalReceiverId = if (receiverId != senderId && receiverId != OFFICIAL_LENO_ID) {
            var partner = userDao.getUserByIdSync(receiverId)
                ?: userDao.getUserByUsernameSync(receiverId)
                ?: userDao.getUserByLinoIdSync(receiverId)
                ?: accountBackupStore?.findAccount(receiverId)
            if (partner == null) {
                partner = remoteAccountService.findRemoteUserById(receiverId)
                    ?: remoteAccountService.findRemoteUser(receiverId)
            }
            if (partner == null) {
                partner = UserEntity(
                    userId = receiverId,
                    username = if (receiverId.startsWith("usr_")) receiverId.removePrefix("usr_").take(10) else receiverId.take(12),
                    displayName = if (receiverId.startsWith("usr_")) "User ${receiverId.takeLast(4)}" else "User ${receiverId.take(6)}",
                    bio = "Connecting on Leno ✨",
                    isCurrentUser = false
                )
            }
            try {
                userDao.upsertRemoteUsersSafely(listOf(partner.copy(isCurrentUser = false)), senderId)
                messageBackupStore?.saveChatPartnerProfile(partner.copy(isCurrentUser = false))
            } catch (_: Exception) {}
            partner.userId.ifBlank { receiverId }
        } else {
            receiverId
        }

        val msgId = "msg_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val message = MessageEntity(
            messageId = msgId,
            senderId = senderId,
            receiverId = canonicalReceiverId,
            text = text,
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            audioDurationSeconds = audioDurationSeconds,
            timestamp = now,
            status = "SENT"
        )
        messageDao.insertMessage(message)
        messageBackupStore?.saveMessage(message)

        if (canonicalReceiverId != OFFICIAL_LENO_ID) {
            // Asynchronously dispatch remote Firestore sync in background so local messaging is instant and never hangs
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    remoteAccountService.saveMessageToRemote(message)
                } catch (e: Exception) {
                    Log.w("LenoRepository", "Remote message sync notice: ${e.message}")
                }
            }
        }

        if (receiverId == OFFICIAL_LENO_ID && senderId != OFFICIAL_LENO_ID) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(700)
                val reply = when {
                    text.contains("hello", ignoreCase = true) || text.contains("hi", ignoreCase = true) || text.contains("hey", ignoreCase = true) ->
                        "Hello! 👋 Welcome to Leno. How can we help you today? You can search for friends using their Leno ID or username, send messages, and make voice calls."
                    text.contains("help", ignoreCase = true) || text.contains("support", ignoreCase = true) ->
                        "Leno Support & Help Desk ℹ️\n\n• To add contacts: tap the New Chat (+) button on the Chats screen.\n• Your Leno ID: check Settings or your Profile.\n• Calls & Media: tap the phone icon or photo/mic buttons in any chat."
                    text.contains("id", ignoreCase = true) || text.contains("leno id", ignoreCase = true) ->
                        "Your Leno ID is your permanent, unique identifier on Leno. Friends can use it to find and chat with you directly."
                    else ->
                        "Thank you for contacting Leno Official Support! 🤖\n\nYour message has been received: \"$text\"\nOur automated platform assistant is active. System notices, updates, and account alerts will also appear here."
                }
                sendMessage(
                    senderId = OFFICIAL_LENO_ID,
                    receiverId = senderId,
                    text = reply
                )
            }
        }
    }

    suspend fun uploadChatImageFile(file: File, chatId: String = ""): Result<String> {
        return messageRepository.uploadImageFile(file, chatId)
    }

    suspend fun uploadChatImageUri(uri: Uri, context: Context, chatId: String = ""): Result<String> {
        return messageRepository.uploadImage(uri, context, chatId)
    }

    suspend fun retrySendMessage(messageId: String) {
        messageDao.updateMessageStatus(messageId, "SENT")
    }

    suspend fun insertMessages(messages: List<MessageEntity>) {
        insertMessagesMonotonic(messages)
    }

    suspend fun updateMessageStatus(messageId: String, status: String) {
        messageDao.updateMessageStatus(messageId, status)
    }

    suspend fun markSentMessagesAsRead(senderId: String, receiverId: String) {
        messageDao.markSentMessagesAsRead(senderId, receiverId)
    }

    suspend fun markMessagesAsRead(currentUserId: String, otherUserId: String) {
        if (currentUserId.isBlank() || otherUserId.isBlank()) return
        unreadCountCache[otherUserId] = 0
        val myAliases = (getUserAliasesSync(currentUserId) + currentUserId).filter { it.isNotBlank() }.distinct()
        val partnerAliases = (getUserAliasesSync(otherUserId) + otherUserId).filter { it.isNotBlank() }.distinct()

        messageDao.markMessagesAsReadWithAliases(myAliases, partnerAliases)
        messageDao.markMessagesAsRead(currentUserId, otherUserId)
        messageBackupStore?.markMessagesAsRead(myAliases, partnerAliases)

        if (otherUserId == OFFICIAL_LENO_ID) return
        try {
            val chatId = remoteAccountService.getChatId(currentUserId, otherUserId)
            remoteAccountService.markAllChatMessagesAsReadInRemote(
                chatId = chatId,
                currentUserId = currentUserId,
                otherUserId = otherUserId,
                currentUserAliases = myAliases,
                otherUserAliases = partnerAliases
            )
            val remoteMsgs = remoteAccountService.getChatMessagesFromRemote(chatId)
            val myAliasesSet = myAliases.toSet()
            remoteMsgs.forEach { msg ->
                if ((msg.receiverId in myAliasesSet || msg.senderId !in myAliasesSet) && !msg.status.equals("read", ignoreCase = true)) {
                    remoteAccountService.markMessageReadInRemote(chatId, msg.messageId, msg.senderId)
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error syncing read status to remote: ${e.message}")
        }
    }

    // FRIEND REQUEST & CONNECTION SYSTEM
    suspend fun sendFriendRequest(currentUserId: String, targetId: String) {
        val sender = userDao.getUserByIdSync(currentUserId)
        val friendDocId = "${currentUserId}_${targetId}"
        val friendship = FriendshipEntity(
            id = friendDocId,
            requesterId = currentUserId,
            targetId = targetId,
            status = "PENDING"
        )
        friendshipDao.insertFriendship(friendship)

        val notifId = "notif_" + UUID.randomUUID().toString().take(8)
        val notifEntity = NotificationEntity(
            id = notifId,
            userId = targetId,
            senderId = currentUserId,
            title = "New Connection Request",
            body = "${sender?.displayName ?: "A user"} (@${sender?.username ?: ""}) sent you a connection request.",
            type = "FRIEND_REQUEST"
        )
        notificationDao.insertNotification(notifEntity)

        remoteAccountService.sendFriendRequestToRemote(
            senderId = currentUserId,
            receiverId = targetId,
            senderDisplayName = sender?.displayName ?: "",
            senderUsername = sender?.username ?: "",
            senderAvatarUrl = sender?.avatarUrl ?: "",
            senderLinoId = sender?.linoId ?: ""
        )
        remoteAccountService.saveNotificationToRemote(notifEntity)
        friendService.sendFriendRequest(
            senderId = currentUserId,
            receiverId = targetId,
            senderDisplayName = sender?.displayName ?: "",
            senderUsername = sender?.username ?: ""
        )
    }

    suspend fun acceptFriendRequest(currentUserId: String, requesterId: String) {
        val currentUserEntity = userDao.getUserByIdSync(currentUserId)
        friendshipDao.updateFriendshipStatus(requesterId, currentUserId, "ACCEPTED")

        val targetUser = userDao.getUserByIdSync(requesterId)
            ?: remoteAccountService.findRemoteUserById(requesterId)
        val myUser = userDao.getUserByIdSync(currentUserId)
            ?: remoteAccountService.findRemoteUserById(currentUserId)
        if (targetUser != null) {
            userDao.insertUser(targetUser.copy(isCurrentUser = false))
            remoteAccountService.saveContactToRemote(currentUserId, targetUser, "ACCEPTED")
        }
        if (myUser != null) {
            remoteAccountService.saveContactToRemote(requesterId, myUser, "ACCEPTED")
        }

        val acceptNotifId = "notif_" + UUID.randomUUID().toString().take(8)
        val acceptNotif = NotificationEntity(
            id = acceptNotifId,
            userId = requesterId,
            senderId = currentUserId,
            title = "Request Accepted 🎉",
            body = "${currentUserEntity?.displayName ?: "User"} accepted your connection request. You can now chat!",
            type = "REQUEST_ACCEPTED"
        )
        notificationDao.insertNotification(acceptNotif)

        remoteAccountService.acceptFriendRequestInRemote(currentUserId, requesterId)
        remoteAccountService.saveNotificationToRemote(acceptNotif)
        friendService.acceptFriendRequest(
            currentUserId = currentUserId,
            requesterId = requesterId,
            currentDisplayName = currentUserEntity?.displayName ?: ""
        )
    }

    suspend fun declineFriendRequest(currentUserId: String, requesterId: String) {
        friendshipDao.removeFriendship(requesterId, currentUserId)
        remoteAccountService.removeContactFromRemote(currentUserId, requesterId)
        remoteAccountService.removeContactFromRemote(requesterId, currentUserId)
        remoteAccountService.declineFriendRequestInRemote(currentUserId, requesterId)
        friendService.declineFriendRequest(
            currentUserId = currentUserId,
            requesterId = requesterId
        )
    }

    suspend fun unfollowUser(currentUserId: String, targetId: String) {
        friendshipDao.removeFriendship(currentUserId, targetId)
        remoteAccountService.removeContactFromRemote(currentUserId, targetId)
        remoteAccountService.declineFriendRequestInRemote(currentUserId, targetId)
    }

    suspend fun searchUserByLinoOrUsername(query: String): Result<UserEntity> {
        val clean = query.trim()
        if (clean.isBlank()) {
            return Result.failure(Exception("Please enter a Leno ID, username, or name."))
        }
        val cleanNoAt = clean.removePrefix("@").trim()
        val upper = cleanNoAt.uppercase()
        val lower = cleanNoAt.lowercase()

        // 0. Instant guaranteed match for Official Accounts across any device
        if (lower == "officialjaiby" || lower == "official jaiby" || lower == "50638964" || upper == "LEN-50638964" || lower.contains("officialjaiby") || clean.contains("officialjaiby", ignoreCase = true)) {
            val officialJaiby = UserEntity(
                userId = "usr_officialjaiby_2026",
                username = "officialjaiby",
                displayName = "Official Jaiby",
                bio = "Leno Platform Founder & Developer 🌟",
                linoId = "LEN-50638964",
                email = "officialjaiby@leno.chat",
                role = "OWNER",
                isOfficial = true,
                isOnline = true,
                createdAt = 1700000000000L
            )
            val safeUser = officialJaiby.copy(isCurrentUser = false, password = "")
            userDao.upsertRemoteUsersSafely(listOf(safeUser), sessionManager?.getActiveUserId())
            messageBackupStore?.saveChatPartnerProfile(safeUser)
            try { remoteAccountService.saveUserToRemote(safeUser) } catch (_: Exception) {}
            return Result.success(safeUser)
        }

        if (lower == "leno" || lower == "official leno" || upper == "LEN-00000001" || upper == "LEN-00000000" || lower.contains("official leno") || clean.contains("official leno", ignoreCase = true)) {
            val officialLeno = UserEntity(
                userId = OFFICIAL_LENO_ID,
                username = "leno",
                displayName = "Official Leno",
                bio = "Official system announcements, platform updates, and verified assistance from Leno.",
                avatarUrl = "android.resource://com.example/drawable/ic_official_leno_avatar",
                isOnline = true,
                lastSeen = System.currentTimeMillis(),
                statusMessage = "Official System Account ✓",
                isCurrentUser = false,
                email = "official@leno.app",
                role = "OFFICIAL",
                isOfficial = true,
                linoId = "LEN-00000001"
            )
            val safeUser = officialLeno.copy(isCurrentUser = false, password = "")
            userDao.upsertRemoteUsersSafely(listOf(safeUser), sessionManager?.getActiveUserId())
            messageBackupStore?.saveChatPartnerProfile(safeUser)
            try { remoteAccountService.saveUserToRemote(safeUser) } catch (_: Exception) {}
            return Result.success(safeUser)
        }

        // 1. Check local Room database, account backup store, and partner profiles first (instant)
        var user: UserEntity? = userDao.getUserByUsernameSync(lower)
            ?: userDao.getUserByLinoIdSync(upper)
            ?: userDao.getUserByDisplayNameSync(cleanNoAt)
            ?: userDao.getUserByIdSync(cleanNoAt)
            ?: userDao.getUserByIdentifierSync(cleanNoAt)
            ?: accountBackupStore?.findAccount(cleanNoAt)
            ?: accountBackupStore?.findAccount(lower)
            ?: messageBackupStore?.getAllChatPartnerProfiles()?.firstOrNull {
                it.username.equals(lower, ignoreCase = true) ||
                it.username.equals(cleanNoAt, ignoreCase = true) ||
                it.linoId.equals(upper, ignoreCase = true) ||
                it.displayName.equals(cleanNoAt, ignoreCase = true) ||
                it.userId.equals(cleanNoAt, ignoreCase = true)
            }

        // 2. Query remote Firestore via findRemoteUser (handles @ stripping, lowercase, uppercase, whereIn, indexes)
        if (user == null) {
            try {
                val remoteUser = remoteAccountService.findRemoteUser(cleanNoAt)
                if (remoteUser != null) {
                    val sanitized = remoteUser.copy(isCurrentUser = false, password = "")
                    userDao.upsertRemoteUsersSafely(listOf(sanitized), sessionManager?.getActiveUserId())
                    messageBackupStore?.saveChatPartnerProfile(sanitized)
                    user = sanitized
                }
            } catch (e: Exception) {
                Log.w("LenoRepository", "Remote search notice: ${e.message}")
            }
        }

        // 3. Fallback to searchRemoteUsers if exact match was not found
        if (user == null) {
            try {
                val remoteMatches = remoteAccountService.searchRemoteUsers(cleanNoAt, "")
                val matched = remoteMatches.firstOrNull {
                    it.username.equals(lower, ignoreCase = true) ||
                    it.username.equals(cleanNoAt, ignoreCase = true) ||
                    it.linoId.equals(upper, ignoreCase = true) ||
                    it.displayName.equals(cleanNoAt, ignoreCase = true)
                } ?: remoteMatches.firstOrNull()
                if (matched != null) {
                    val sanitized = matched.copy(isCurrentUser = false, password = "")
                    userDao.upsertRemoteUsersSafely(listOf(sanitized), sessionManager?.getActiveUserId())
                    messageBackupStore?.saveChatPartnerProfile(sanitized)
                    user = sanitized
                }
            } catch (_: Exception) {}
        }

        // 4. Return result
        if (user != null) {
            val safeUser = user.copy(isCurrentUser = false, password = "")
            userDao.upsertRemoteUsersSafely(listOf(safeUser), sessionManager?.getActiveUserId())
            messageBackupStore?.saveChatPartnerProfile(safeUser)
            Log.d("LenoAuthSearch", "SEARCH RESULT SUCCESS: Found '${safeUser.displayName}' (@${safeUser.username}, ${safeUser.linoId})")
            return Result.success(safeUser)
        }

        Log.d("LenoAuthSearch", "SEARCH RESULT FAILURE: No Leno account found for '$clean'")
        return Result.failure(Exception("No Leno account found for '$clean'."))
    }

    suspend fun searchUserByPhone(inputPhone: String): Result<UserEntity> {
        val cleanRaw = inputPhone.trim()
        if (cleanRaw.isBlank()) {
            return Result.failure(Exception("Please enter a Leno ID, username, or name."))
        }
        return searchUserByLinoOrUsername(cleanRaw)
    }

    suspend fun addNewContact(
        myUserId: String,
        displayName: String,
        phoneNumber: String = "",
        username: String = "",
        email: String = ""
    ): Result<UserEntity> {
        val query = if (username.isNotBlank()) username else if (phoneNumber.isNotBlank()) phoneNumber else displayName
        val searchResult = searchUserByLinoOrUsername(query)
        val registeredUser = searchResult.getOrNull()
            ?: return Result.failure(searchResult.exceptionOrNull() ?: Exception("No Leno account found for '$query'."))

        try {
            val friendDocId = "${myUserId}_${registeredUser.userId}"
            val friendship = FriendshipEntity(
                id = friendDocId,
                requesterId = myUserId,
                targetId = registeredUser.userId,
                status = "ACCEPTED",
                createdAt = System.currentTimeMillis()
            )
            friendshipDao.insertFriendship(friendship)
            remoteAccountService.saveContactToRemote(myUserId, registeredUser, "ACCEPTED")

            // Reciprocal contact save so both accounts see each other in Contacts & Connections!
            val myUser = userDao.getUserByIdSync(myUserId)
            if (myUser != null) {
                val reciprocalId = "${registeredUser.userId}_${myUserId}"
                val reciprocalFriendship = FriendshipEntity(
                    id = reciprocalId,
                    requesterId = registeredUser.userId,
                    targetId = myUserId,
                    status = "ACCEPTED",
                    createdAt = System.currentTimeMillis()
                )
                friendshipDao.insertFriendship(reciprocalFriendship)
                remoteAccountService.saveContactToRemote(registeredUser.userId, myUser, "ACCEPTED")
            }

            return Result.success(registeredUser)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Failed to save contact."
            Log.e("LenoRepository", "Add contact error: $msg")
            return Result.failure(Exception(msg))
        }
    }

    suspend fun blockUser(currentUserId: String, targetId: String, reason: String? = null) {
        val block = BlockReportEntity(
            id = "block_" + UUID.randomUUID().toString().take(8),
            blockerId = currentUserId,
            targetId = targetId,
            isBlocked = true,
            reason = reason
        )
        blockReportDao.insertBlockReport(block)
        friendshipDao.removeFriendship(currentUserId, targetId)
    }

    suspend fun unblockUser(currentUserId: String, targetId: String) {
        blockReportDao.unblockUser(currentUserId, targetId)
    }

    suspend fun reportUser(currentUserId: String, targetId: String, reason: String) {
        val existing = blockReportDao.getBlockReportSync(currentUserId, targetId)
        val report = BlockReportEntity(
            id = existing?.id ?: ("report_" + UUID.randomUUID().toString().take(8)),
            blockerId = currentUserId,
            targetId = targetId,
            isBlocked = existing?.isBlocked ?: false,
            isReported = true,
            reason = reason
        )
        blockReportDao.insertBlockReport(report)
    }

    suspend fun updateProfile(
        userId: String,
        displayName: String,
        username: String,
        bio: String,
        avatarUrl: String,
        statusMessage: String,
        phoneNumber: String = ""
    ): Result<UserEntity> {
        if (userId == OFFICIAL_LENO_ID) return Result.failure(Exception("Cannot modify official platform service account."))
        val cleanUsername = username.trim().replace("@", "").lowercase()
        if (cleanUsername == "leno" || cleanUsername == "official_leno" || displayName.trim().equals("Official Leno", ignoreCase = true)) {
            return Result.failure(Exception("This username is reserved for official system services."))
        }
        val existing = userDao.getUserByIdSync(userId) ?: return Result.failure(Exception("User not found."))

        // If username changed, claim it atomically in Firestore
        val isUsernameChanged = !existing.username.equals(cleanUsername, ignoreCase = true)
        if (isUsernameChanged) {
            val claimResult = remoteAccountService.claimUsernameAtomic(userId, cleanUsername, existing.username)
            if (claimResult.isFailure) {
                return Result.failure(claimResult.exceptionOrNull() ?: Exception("Username @$cleanUsername is already taken."))
            }
        }

        val isLenoEmail = existing.email.isBlank() || existing.email.endsWith("@leno.chat", ignoreCase = true)
        val newEmail = if (isUsernameChanged && isLenoEmail) "${cleanUsername}@leno.chat" else existing.email
        if (isUsernameChanged && isLenoEmail) {
            remoteAccountService.updateFirebaseEmail(newEmail, userId)
        }

        val finalDisplayName = displayName.trim().ifBlank { existing.displayName.ifBlank { cleanUsername } }
        val normPhone = PhoneUtils.normalizePhoneNumber(phoneNumber)
        val updated = existing.copy(
            displayName = finalDisplayName,
            username = cleanUsername,
            email = newEmail,
            bio = bio,
            avatarUrl = avatarUrl,
            statusMessage = statusMessage,
            phoneNumber = phoneNumber,
            normalizedPhoneNumber = normPhone,
            lastSeen = System.currentTimeMillis()
        )

        // Persist locally first so the user's edits are immediately and safely saved
        userDao.updateUser(updated)
        recordDeviceOwnedAccount(updated)

        // Sync with remote cloud
        val remoteRes = remoteAccountService.saveUserToRemote(updated)
        if (remoteRes.isFailure) {
            val err = remoteRes.exceptionOrNull()
            Log.w("LenoRepository", "Remote sync notice: ${err?.message}")
        }
        return Result.success(updated)
    }

    suspend fun updateOnlineStatus(userId: String, isOnline: Boolean) {
        val now = System.currentTimeMillis()
        userDao.updateOnlineStatus(userId, isOnline, now)
        CoroutineScope(Dispatchers.IO).launch {
            remoteAccountService.updateUserPresenceInRemote(userId, isOnline, now)
        }
    }

    suspend fun updateUserSettings(
        userId: String,
        lowDataMode: Boolean,
        notificationsEnabled: Boolean,
        darkThemeEnabled: Boolean,
        muteOfficialLeno: Boolean = false
    ) {
        settingsDao.insertUserSettings(
            UserSettingsEntity(
                userId = userId,
                lowDataMode = lowDataMode,
                notificationsEnabled = notificationsEnabled,
                darkThemeEnabled = darkThemeEnabled,
                muteOfficialLeno = muteOfficialLeno
            )
        )
    }

    suspend fun changePassword(userId: String, newPass: String): Result<Unit> {
        val cleanPass = newPass.trim()
        if (cleanPass.length < 6) return Result.failure(Exception("Password must be at least 6 characters."))
        val localUser = userDao.getUserByIdSync(userId)
        return if (localUser != null) {
            val salt = AuthSecurity.generateSalt()
            val hash = AuthSecurity.hashPassword(cleanPass, salt)
            val updatedUser = localUser.copy(password = "", passwordHash = hash, passwordSalt = salt)
            userDao.insertUser(updatedUser)
            recordDeviceOwnedAccount(updatedUser)
            try {
                val fbUser = remoteAccountService.getCurrentAuthUser()
                if (fbUser != null && fbUser.uid == userId) {
                    val updRes = remoteAccountService.updateFirebasePassword(cleanPass)
                    if (updRes.isSuccess) {
                        sessionManager?.clearRecoveryPasswordPending(userId)
                    } else {
                        sessionManager?.markRecoveryPasswordPending(userId)
                    }
                } else {
                    sessionManager?.markRecoveryPasswordPending(userId)
                }
                remoteAccountService.saveUserToRemote(updatedUser)
            } catch (e: Exception) {
                sessionManager?.markRecoveryPasswordPending(userId)
                Log.w("LenoRepository", "Notice updating remote password: ${e.message}")
            }
            val email = if (updatedUser.email.isNotBlank()) updatedUser.email else "${updatedUser.username.lowercase()}@leno.chat"
            sessionManager?.saveAuthCredentials(email, cleanPass)
            sessionManager?.logSecurityEvent(userId, "Password Changed", "Updated account password")
            Result.success(Unit)
        } else {
            Result.failure(Exception("No user currently logged in."))
        }
    }

    suspend fun changePasswordVerified(
        userId: String,
        currentPass: String,
        newPass: String
    ): Result<Unit> {
        val cleanCurrent = currentPass.trim()
        val cleanNew = newPass.trim()
        if (cleanNew.length < 6) {
            return Result.failure(Exception("New password must be at least 6 characters."))
        }

        val localUser = userDao.getUserByIdSync(userId)
            ?: return Result.failure(Exception("No user currently logged in."))

        val hasExistingPassword = localUser.passwordHash.isNotBlank() || localUser.password.isNotBlank()
        if (hasExistingPassword) {
            if (cleanCurrent.isBlank()) {
                return Result.failure(Exception("Please enter your current password."))
            }
            val currentMatches = (localUser.passwordHash.isNotBlank() &&
                AuthSecurity.verifyPassword(cleanCurrent, localUser.passwordHash, localUser.passwordSalt)) ||
                (localUser.password.isNotBlank() && localUser.password == cleanCurrent)
            if (!currentMatches) {
                return Result.failure(Exception("Current password is incorrect."))
            }
            if (cleanNew == cleanCurrent) {
                return Result.failure(Exception("New password must be different from your current password."))
            }
        }

        val salt = AuthSecurity.generateSalt()
        val hash = AuthSecurity.hashPassword(cleanNew, salt)
        val updatedUser = localUser.copy(password = "", passwordHash = hash, passwordSalt = salt)
        userDao.insertUser(updatedUser)
        recordDeviceOwnedAccount(updatedUser)
        try {
            val fbUser = remoteAccountService.getCurrentAuthUser()
            if (fbUser != null && fbUser.uid == userId) {
                val updRes = remoteAccountService.updateFirebasePassword(cleanNew)
                if (updRes.isSuccess) {
                    sessionManager?.clearRecoveryPasswordPending(userId)
                } else {
                    sessionManager?.markRecoveryPasswordPending(userId)
                }
            } else {
                sessionManager?.markRecoveryPasswordPending(userId)
            }
            remoteAccountService.saveUserToRemote(updatedUser)
        } catch (e: Exception) {
            sessionManager?.markRecoveryPasswordPending(userId)
            Log.w("LenoRepository", "Notice updating remote password: ${e.message}")
        }
        val email = if (updatedUser.email.isNotBlank()) updatedUser.email else "${updatedUser.username.lowercase()}@leno.chat"
        sessionManager?.saveAuthCredentials(email, cleanNew)
        sessionManager?.logSecurityEvent(userId, "Password Changed", "Verified and updated account password")
        notificationDao.insertNotification(
            NotificationEntity(
                id = "sec_pass_" + UUID.randomUUID().toString().take(8),
                userId = userId,
                title = "Password Updated ✓",
                body = "Your Leno account password was changed cleanly.",
                type = "SYSTEM"
            )
        )
        return Result.success(Unit)
    }

    suspend fun ensureRecoveryCodePresent(userId: String): String? {
        val localUser = userDao.getUserByIdSync(userId) ?: return null
        if (localUser.recoveryCode.isNotBlank()) return localUser.recoveryCode
        val generated = AuthSecurity.generateRecoveryCode()
        val updated = localUser.copy(recoveryCode = generated)
        userDao.insertUser(updated)
        recordDeviceOwnedAccount(updated)
        try {
            remoteAccountService.saveUserToRemote(updated)
        } catch (_: Exception) {}
        return generated
    }

    suspend fun regenerateRecoveryCode(userId: String): Result<String> {
        val localUser = userDao.getUserByIdSync(userId)
            ?: return Result.failure(Exception("User not found."))
        val newCode = AuthSecurity.generateRecoveryCode()
        val updated = localUser.copy(recoveryCode = newCode)
        userDao.insertUser(updated)
        recordDeviceOwnedAccount(updated)
        try {
            remoteAccountService.saveUserToRemote(updated)
        } catch (e: Exception) {
            Log.w("LenoRepository", "Remote recovery code sync notice: ${e.message}")
        }
        sessionManager?.logSecurityEvent(userId, "Recovery Code Regenerated", "Generated a new private backup recovery code")
        notificationDao.insertNotification(
            NotificationEntity(
                id = "sec_code_" + UUID.randomUUID().toString().take(8),
                userId = userId,
                title = "Backup Recovery Code Updated",
                body = "A new backup recovery code was generated. Your old recovery code is no longer valid.",
                type = "SYSTEM"
            )
        )
        return Result.success(newCode)
    }

    fun isSecurityPinEnabled(userId: String): Boolean =
        sessionManager?.isSecurityPinEnabled(userId) == true

    fun setSecurityPin(userId: String, pin: String): Result<Unit> {
        val clean = pin.trim()
        if (clean.length !in 4..6 || !clean.all { it.isDigit() }) {
            return Result.failure(Exception("Security PIN must be 4 to 6 digits."))
        }
        sessionManager?.setSecurityPin(userId, clean)
        sessionManager?.clearFailedAttempts("pin_$userId")
        sessionManager?.logSecurityEvent(userId, "App Security PIN Enabled", "4-6 digit app lock PIN configured")
        return Result.success(Unit)
    }

    fun verifySecurityPin(userId: String, pin: String): Result<Unit> {
        val rateKey = "pin_$userId"
        val lockout = sessionManager?.getLockoutRemainingSeconds(rateKey) ?: 0L
        if (lockout > 0L) {
            return Result.failure(Exception("Too many wrong PIN attempts. Try again in ${lockout}s."))
        }
        val ok = sessionManager?.verifySecurityPin(userId, pin.trim()) == true
        return if (ok) {
            sessionManager?.clearFailedAttempts(rateKey)
            Result.success(Unit)
        } else {
            val fails = sessionManager?.recordFailedAttempt(rateKey) ?: 0
            val msg = if (fails >= SessionManager.MAX_FAILED_ATTEMPTS) {
                "Too many wrong PIN attempts. Locked for 60 seconds."
            } else {
                "Incorrect PIN (${SessionManager.MAX_FAILED_ATTEMPTS - fails} attempts remaining)."
            }
            Result.failure(Exception(msg))
        }
    }

    fun disableSecurityPin(userId: String, currentPin: String): Result<Unit> {
        val verifyRes = verifySecurityPin(userId, currentPin)
        if (verifyRes.isFailure) return verifyRes
        sessionManager?.clearSecurityPin(userId)
        sessionManager?.logSecurityEvent(userId, "App Security PIN Disabled", "Removed app lock PIN")
        return Result.success(Unit)
    }

    fun getSecurityEvents(userId: String): List<SecurityEvent> =
        sessionManager?.getSecurityEvents(userId).orEmpty()

    fun logSecurityEvent(userId: String, title: String, detail: String) {
        sessionManager?.logSecurityEvent(userId, title, detail)
    }

    suspend fun logout(userId: String) {
        isAuthTransitionInProgress = true
        try {
            setActiveChatPartnerId(null)
            unreadCountCache.clear()
            try {
                userDao.updateOnlineStatus(userId, false)
                val u = userDao.getUserByIdSync(userId)
                if (u != null) {
                    remoteAccountService.saveUserToRemote(u.copy(isOnline = false, lastSeen = System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                Log.w("LenoRepository", "Logout remote status sync: ${e.message}")
            }
            sessionManager?.clearSession()
            remoteAccountService.signOutFromFirebase()
            userDao.clearCurrentUser()
            userDao.purgeDemoAccounts()
            accountBackupStore?.purgeDemoAccounts()
            ensureOfficialLenoAccountCreated()
        } finally {
            isAuthTransitionInProgress = false
        }
    }

    suspend fun clearCurrentSession() {
        val current = userDao.getCurrentUserSync()
        if (current != null) {
            logout(current.userId)
        } else {
            isAuthTransitionInProgress = true
            try {
                setActiveChatPartnerId(null)
                unreadCountCache.clear()
                remoteAccountService.signOutFromFirebase()
                userDao.clearCurrentUser()
                userDao.purgeDemoAccounts()
                accountBackupStore?.purgeDemoAccounts()
                sessionManager?.clearSession()
                ensureOfficialLenoAccountCreated()
            } finally {
                isAuthTransitionInProgress = false
            }
        }
    }

    // CALL HISTORY OPERATIONS
    fun getCallsForUser(userId: String): Flow<List<CallEntity>> = callDao.getCallsForUser(userId)

    suspend fun saveCallLog(call: CallEntity) {
        callDao.insertCall(call)
    }

    suspend fun deleteCall(callId: String) {
        callDao.deleteCall(callId)
    }

    suspend fun clearCallsForUser(userId: String) {
        callDao.clearCallsForUser(userId)
    }

    companion object {
        const val OFFICIAL_LENO_ID = "official_leno_account"
    }

    suspend fun getInstructorUserId(): String = ""

    suspend fun purgeAllMockUsers() = withContext(Dispatchers.IO) {
        try {
            userDao.purgeDemoAccounts()
            userDao.deleteDemoTeachers()
            accountBackupStore?.purgeDemoAccounts()
            messageBackupStore?.purgeMockPartners()
            Log.i("LenoRepository", "Successfully purged all mock users. Only Official Leno and registered users remain.")
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error purging mock users: ${e.message}")
        }
    }

    suspend fun ensureOfficialLenoAccountCreated() {
        try {
            purgeAllMockUsers()
            val newAvatarUrl = "android.resource://com.example/drawable/ic_official_leno_avatar"
            val existing = userDao.getUserByIdSync(OFFICIAL_LENO_ID)
            if (existing != null) {
                if (existing.avatarUrl != newAvatarUrl || existing.role == "TEACHER" || existing.role != "OFFICIAL") {
                    userDao.insertUser(existing.copy(
                        avatarUrl = newAvatarUrl,
                        role = "OFFICIAL",
                        teacherStatus = "NOT_APPLIED",
                        teacherSubject = "",
                        bio = "Official system announcements, platform updates, and verified assistance from Leno.",
                        isVerified = true
                    ))
                }
            } else {
                val now = System.currentTimeMillis()
                val officialAccount = UserEntity(
                    userId = OFFICIAL_LENO_ID,
                    username = "leno",
                    displayName = "Official Leno",
                    bio = "Official system announcements, platform updates, and verified assistance from Leno.",
                    avatarUrl = newAvatarUrl,
                    isOnline = true,
                    lastSeen = now,
                    statusMessage = "Official System Account ✓",
                    isCurrentUser = false,
                    email = "official@leno.app",
                    password = "",
                    phoneNumber = "",
                    createdAt = 0L,
                    isOfficial = true,
                    linoId = "LEN-00000001",
                    role = "OFFICIAL",
                    teacherStatus = "NOT_APPLIED",
                    teacherSubject = "",
                    isVerified = true
                )
                userDao.insertUser(officialAccount)
            }
            val jaiby = userDao.getUserByIdSync("usr_officialjaiby_2026")
                ?: userDao.getUserByUsernameSync("officialjaiby")
            if (jaiby == null) {
                userDao.insertUser(UserEntity(
                    userId = "usr_officialjaiby_2026",
                    username = "officialjaiby",
                    displayName = "Official Jaiby",
                    bio = "Leno Platform Founder & Developer 🌟",
                    linoId = "LEN-50638964",
                    email = "officialjaiby@leno.chat",
                    role = "OWNER",
                    isOfficial = true,
                    isOnline = true,
                    createdAt = 1700000000000L
                ))
            }
        } catch (e: Exception) {
            Log.e("LenoRepository", "Error setting up Official Leno account: ${e.message}")
        }
        purgeAllMockUsers()
        purgeAllDemoClasses()
        ensureOwnersAccountsCreated()
        remoteAccountService.ensureOfficialAccountsInFirestore()
    }

    suspend fun purgeAllDemoClasses() = withContext(Dispatchers.IO) {
        try {
            classDao.deleteDemoClasses()
            lessonDao.deleteDemoLessons()
            val demoClassIds = listOf(
                "class_quran_mastery",
                "class_online_skills_dev",
                "class_maths_core_algebra",
                "class_jamb_intensive",
                "class_secondary_maths",
                "class_jamb_prep",
                "class_science_biology",
                "class_math_core_01",
                "class_quran_tajweed_02",
                "class_english_grammar_03",
                "class_vocational_skills_04",
                "class_general_science_05",
                "class_digital_literacy_06",
                "class_maths_1", "class_maths_2", "class_maths_3",
                "class_quran_1", "class_english_1", "class_skills_1"
            )
            CoroutineScope(Dispatchers.IO).launch {
                for (id in demoClassIds) {
                    try {
                        remoteAccountService.deleteClassFromRemote(id)
                    } catch (_: Exception) {}
                }
            }
            Log.i("LenoRepository", "Successfully purged all demo classes and demo lessons.")
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error purging demo classes: ${e.message}")
        }
    }

    suspend fun ensureOwnersAccountsCreated() {
        try {
            // Consolidate duplicate accounts to prevent duplicate accounts and preserve all messages
            val current = userDao.getCurrentUserSync()
            if (current != null) {
                val duplicates = userDao.getAllUsersSync().filter {
                    it.userId != current.userId && (
                        (current.username.isNotBlank() && it.username.isNotBlank() && it.username.equals(current.username, ignoreCase = true)) ||
                        (current.linoId.isNotBlank() && it.linoId.isNotBlank() && it.linoId.equals(current.linoId, ignoreCase = true)) ||
                        (current.isOwner && (it.userId == "usr_officialjaiby_2026" || it.username.equals("officialjaiby", ignoreCase = true)))
                    )
                }
                for (legacy in duplicates) {
                    if (legacy.userId.isBlank() || legacy.userId == current.userId) continue
                    messageDao.migrateSenderId(legacy.userId, current.userId)
                    messageDao.migrateReceiverId(legacy.userId, current.userId)
                    messageBackupStore?.migrateUserId(legacy.userId, current.userId)
                    userDao.deleteUser(legacy.userId)
                    accountBackupStore?.deleteAccount(legacy.userId)
                }
            }
        } catch (e: Exception) {
            Log.e("LenoRepository", "Error ensuring clean owner account: ${e.message}")
        }
    }

    suspend fun ensureInitialOfficialMessageForUser(userId: String) {
        if (userId.isBlank() || userId == OFFICIAL_LENO_ID) return
        val existingMsg = messageDao.getLastMessageBetweenSync(OFFICIAL_LENO_ID, userId)
        if (existingMsg != null) return

        val now = System.currentTimeMillis()
        val welcomeMsg = MessageEntity(
            messageId = "msg_welcome_official_$userId",
            senderId = OFFICIAL_LENO_ID,
            receiverId = userId,
            text = "🎉 Welcome to Leno!\n\nThis is the Official Leno announcement channel. Official platform updates, new features, and verified announcements are broadcast here.",
            timestamp = now - 2000,
            status = "DELIVERED",
            isSystemMessage = true
        )
        messageDao.insertMessage(welcomeMsg)
        messageBackupStore?.saveMessage(welcomeMsg)

        val securityMsg = MessageEntity(
            messageId = "msg_security_official_$userId",
            senderId = OFFICIAL_LENO_ID,
            receiverId = userId,
            text = "🔒 Security & Account Protection Alert\n\n• Your Leno ID is your permanent, unique identifier.\n• Leno team members will NEVER ask for your password or credentials.\n• Always verify official messages by looking for the orange verified badge beside @Leno.",
            timestamp = now - 1000,
            status = "DELIVERED",
            isSystemMessage = true
        )
        messageDao.insertMessage(securityMsg)
        messageBackupStore?.saveMessage(securityMsg)
        userDao.getUserByIdSync(OFFICIAL_LENO_ID)?.let { messageBackupStore?.saveChatPartnerProfile(it) }
    }

    suspend fun sendOfficialAnnouncement(
        title: String,
        body: String,
        category: String,
        targetUserIds: List<String> = emptyList(),
        imageUrl: String? = null
    ): Result<Int> {
        val current = userDao.getCurrentUserSync()
        val isAuthorized = current?.isOwner == true ||
                current?.isAdmin == true ||
                current?.isOfficial == true ||
                current?.userId == OFFICIAL_LENO_ID ||
                current?.userId == "usr_officialjaiby_2026" ||
                current?.role.equals("OWNER", ignoreCase = true) ||
                current?.role.equals("ADMIN", ignoreCase = true)

        if (!isAuthorized) {
            return Result.failure(Exception("Unauthorized: Only platform owners and Official Leno identity can broadcast announcements."))
        }

        if (title.isBlank() || body.isBlank()) {
            return Result.failure(Exception("Announcement title and body cannot be empty."))
        }

        val allRecipients = if (targetUserIds.isNotEmpty()) {
            targetUserIds
        } else {
            userDao.getAllUsersSync().map { it.userId }.filter { it != OFFICIAL_LENO_ID && it.isNotBlank() }
        }

        var sentCount = 0
        val now = System.currentTimeMillis()

        for (targetId in allRecipients) {
            val notifId = "announcement_" + UUID.randomUUID().toString().take(8)
            val notifEntity = NotificationEntity(
                id = notifId,
                userId = targetId,
                senderId = OFFICIAL_LENO_ID,
                title = "[$category] $title",
                body = body,
                timestamp = now,
                type = "ANNOUNCEMENT"
            )
            notificationDao.insertNotification(notifEntity)
            CoroutineScope(Dispatchers.IO).launch {
                remoteAccountService.saveNotificationToRemote(notifEntity)
            }

            sendMessage(
                senderId = OFFICIAL_LENO_ID,
                receiverId = targetId,
                text = "📢 Official Announcement: $title\n\n$body",
                imageUrl = imageUrl
            )

            sentCount++
        }

        return Result.success(sentCount)
    }

    // ----------------------------------------------------
    // LENO EDUCATION & CLASS SYSTEM
    // ----------------------------------------------------

    val allClasses: Flow<List<ClassEntity>> = classDao.getAllClasses()
    val publishedClasses: Flow<List<ClassEntity>> = classDao.getPublishedClasses()

    fun getClassesBySubject(subject: String): Flow<List<ClassEntity>> =
        if (subject.equals("All", ignoreCase = true)) classDao.getPublishedClasses()
        else classDao.getPublishedClassesBySubject(subject)

    fun getClassesByInstructor(instructorUserId: String): Flow<List<ClassEntity>> =
        classDao.getClassesByInstructor(instructorUserId)

    fun getClassById(classId: String): Flow<ClassEntity?> =
        classDao.getClassById(classId)

    suspend fun getClassByIdSync(classId: String): ClassEntity? =
        classDao.getClassByIdSync(classId)

    suspend fun createOrUpdateClass(classEntity: ClassEntity): Result<Unit> {
        val user = userDao.getUserByIdSync(classEntity.instructorUserId)
        val role = teacherRoleDao.getTeacherRoleSync(classEntity.instructorUserId)
        val isUserApproved = user != null && user.isApprovedTeacher && !user.isPendingTeacher && !user.isRejectedTeacher
        val isRoleApproved = role != null && (role.isApprovedTeacher || role.status.equals("APPROVED", ignoreCase = true) || role.status.equals("Teacher", ignoreCase = true) || role.status.equals("Trusted Teacher", ignoreCase = true)) && !role.isPending && !role.isRejected
        val isApproved = isUserApproved || isRoleApproved || (user?.isAdmin == true)
        if (!isApproved) {
            return Result.failure(Exception("Unauthorized: Users must become an approved Teacher before they are allowed to create or publish classes."))
        }

        // ONE TEACHER = ONE SUBJECT: Class subject MUST match approved subject
        val approvedSub = if (user != null && user.isApprovedTeacher && user.teacherSubject.isNotBlank()) {
            user.teacherSubject
        } else {
            role?.effectiveApprovedSubject.orEmpty()
        }
        if (approvedSub.isNotBlank() && !classEntity.subject.equals(approvedSub, ignoreCase = true) && user?.isAdmin != true) {
            return Result.failure(Exception("Unauthorized: Teacher can only create classes under approved subject: $approvedSub"))
        }

        classDao.insertClass(classEntity)
        try {
            remoteAccountService.saveClassToRemote(classEntity)
        } catch (e: Exception) {
            Log.w("LenoRepository", "Firestore class sync notice: ${e.message}")
        }
        return Result.success(Unit)
    }

    suspend fun updateClassDetails(
        classId: String,
        callerUserId: String,
        title: String,
        shortDescription: String,
        level: String,
        isPaid: Boolean,
        price: String,
        schedule: String,
        lessonType: String,
        status: String,
        imageUrl: String = "",
        videoUrl: String = ""
    ): Result<ClassEntity> {
        val existingClass = classDao.getClassByIdSync(classId)
            ?: return Result.failure(Exception("Class not found."))

        val caller = userDao.getUserByIdSync(callerUserId)
        val isOwner = existingClass.instructorUserId == callerUserId
        val isAdmin = caller?.isAdmin == true

        if (!isOwner && !isAdmin) {
            return Result.failure(Exception("Unauthorized: Only the teacher who published this class can edit it."))
        }

        if (title.isBlank()) {
            return Result.failure(Exception("Class title cannot be blank."))
        }
        if (shortDescription.isBlank()) {
            return Result.failure(Exception("Class description cannot be blank."))
        }

        val effectiveIsPaid = isPaid && !price.equals("FREE", ignoreCase = true) && !price.equals("Free", ignoreCase = true)
        val effectiveFee = if (effectiveIsPaid) {
            val clean = price.trim()
            if (clean.isBlank() || clean.equals("FREE", ignoreCase = true) || clean.equals("Free", ignoreCase = true)) {
                "₦1,500"
            } else if (clean.startsWith("₦") || clean.startsWith("N", ignoreCase = true)) {
                clean
            } else {
                "₦$clean"
            }
        } else {
            "Free"
        }

        val updatedClass = existingClass.copy(
            title = title.trim(),
            shortDescription = shortDescription.trim(),
            level = level.trim().ifBlank { existingClass.level },
            isPaid = effectiveIsPaid,
            price = effectiveFee,
            schedule = schedule.trim().ifBlank { existingClass.schedule },
            lessonType = lessonType.trim().ifBlank { existingClass.lessonType },
            status = status.trim().ifBlank { existingClass.status },
            imageUrl = if (imageUrl.isNotBlank()) imageUrl.trim() else existingClass.imageUrl,
            videoUrl = if (videoUrl.isNotBlank()) videoUrl.trim() else existingClass.videoUrl,
            hasVideo = videoUrl.isNotBlank() || (if (videoUrl.isBlank() && imageUrl.isNotBlank()) false else existingClass.hasVideo)
        )

        classDao.insertClass(updatedClass)
        try {
            remoteAccountService.saveClassToRemote(updatedClass)
        } catch (e: Exception) {
            Log.w("LenoRepository", "Firestore updateClassDetails sync notice: ${e.message}")
        }
        return Result.success(updatedClass)
    }

    suspend fun publishClassDraft(classId: String, instructorUserId: String): Result<Unit> {
        val classEntity = classDao.getClassByIdSync(classId)
            ?: return Result.failure(Exception("Class not found."))

        if (classEntity.instructorUserId != instructorUserId) {
            val actor = userDao.getUserByIdSync(instructorUserId)
            if (actor?.isAdmin != true) {
                return Result.failure(Exception("Unauthorized: You do not own this class."))
            }
        }

        val user = userDao.getUserByIdSync(instructorUserId)
        val role = teacherRoleDao.getTeacherRoleSync(instructorUserId)
        val isApproved = (user != null && user.isApprovedTeacher) || (role != null && role.isApprovedTeacher) || (user?.isAdmin == true)
        if (!isApproved) {
            return Result.failure(Exception("Unauthorized: Only approved Teachers can publish classes."))
        }

        val approvedSub = if (user != null && user.isApprovedTeacher && user.teacherSubject.isNotBlank()) {
            user.teacherSubject
        } else {
            role?.effectiveApprovedSubject.orEmpty()
        }
        if (approvedSub.isNotBlank() && !classEntity.subject.equals(approvedSub, ignoreCase = true) && user?.isAdmin != true) {
            return Result.failure(Exception("Unauthorized: Class subject does not match approved subject."))
        }

        classDao.updateClassStatus(classId, "PUBLISHED")
        val updatedClass = classEntity.copy(status = "PUBLISHED")
        try {
            remoteAccountService.saveClassToRemote(updatedClass)
        } catch (e: Exception) {
            Log.w("LenoRepository", "Firestore class publish sync notice: ${e.message}")
        }
        return Result.success(Unit)
    }

    suspend fun purgeDemoClasses() = withContext(Dispatchers.IO) {
        try {
            ensureOfficialLenoAccountCreated()
            userDao.clearDemoAvatars()
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error purging demo data: ${e.message}")
        }
    }

    suspend fun ensureOfficialStarterClasses() = withContext(Dispatchers.IO) {
        // App is in production: do NOT seed demo classes or fake teachers.
        purgeAllDemoClasses()
    }

    suspend fun syncClassesFromRemote() = withContext(Dispatchers.IO) {
        try {
            purgeAllDemoClasses()
            val remoteClasses = remoteAccountService.fetchClassesFromRemote()
            if (remoteClasses.isNotEmpty()) {
                classDao.insertClasses(remoteClasses)
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "syncClassesFromRemote notice: ${e.message}")
        }
    }

    fun listenToRemoteClasses(onClasses: (List<ClassEntity>) -> Unit): ListenerRegistration? =
        remoteAccountService.listenToRemoteClassesWithRemovals { list, removedIds ->
            CoroutineScope(Dispatchers.IO).launch {
                purgeAllDemoClasses()
                removedIds.forEach { removedClassId ->
                    classDao.deleteClassById(removedClassId)
                    lessonDao.deleteLessonsForClass(removedClassId)
                }
                if (list.isNotEmpty()) {
                    classDao.insertClasses(list)
                }
            }
            onClasses(list)
        }

    fun listenToUserEnrollments(studentUserId: String, onEnrollments: (List<EnrollmentEntity>) -> Unit): ListenerRegistration? =
        remoteAccountService.listenToUserEnrollmentsWithRemovals(studentUserId) { list, removedIds ->
            CoroutineScope(Dispatchers.IO).launch {
                list.forEach { enrollmentDao.insertEnrollment(it) }
                val activeClassIds = list.map { it.classId }.filter { it.isNotBlank() }
                if (activeClassIds.isNotEmpty()) {
                    enrollmentDao.deleteEnrollmentsForStudentNotIn(studentUserId, activeClassIds)
                } else if (removedIds.isNotEmpty()) {
                    enrollmentDao.deleteAllEnrollmentsForStudent(studentUserId)
                }
            }
            onEnrollments(list)
        }

    fun listenToClassEnrollments(classId: String): ListenerRegistration? =
        remoteAccountService.listenToClassEnrollments(classId) { list, removedIds ->
            CoroutineScope(Dispatchers.IO).launch {
                list.forEach { enrollment ->
                    enrollmentDao.insertEnrollment(enrollment)
                    if (enrollment.studentUserId.isNotBlank() && userDao.getUserByIdSync(enrollment.studentUserId) == null) {
                        fetchAndSyncUserProfile(enrollment.studentUserId)
                    }
                }
                val activeStudentIds = list.map { it.studentUserId }.filter { it.isNotBlank() }
                if (activeStudentIds.isNotEmpty()) {
                    enrollmentDao.deleteEnrollmentsForClassNotIn(classId, activeStudentIds)
                } else if (removedIds.isNotEmpty()) {
                    enrollmentDao.deleteAllEnrollmentsForClass(classId)
                }
            }
        }

    fun listenToContacts(userId: String): ListenerRegistration? =
        remoteAccountService.listenToContactsWithRemovals(userId) { contacts, removedIds ->
            CoroutineScope(Dispatchers.IO).launch {
                if (contacts.isNotEmpty()) {
                    userDao.insertUsers(contacts.map { it.copy(isCurrentUser = false) })
                    contacts.forEach { contact ->
                        friendshipDao.insertFriendship(
                            FriendshipEntity(
                                id = "${userId}_${contact.userId}",
                                requesterId = userId,
                                targetId = contact.userId,
                                status = "ACCEPTED",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                    val activeFriendIds = contacts.map { it.userId }.filter { it.isNotBlank() }
                    friendshipDao.deleteAcceptedOutgoingNotIn(userId, activeFriendIds)
                    friendshipDao.deleteAcceptedIncomingNotIn(userId, activeFriendIds)
                } else if (removedIds.isNotEmpty()) {
                    friendshipDao.deleteAllAcceptedForUser(userId)
                }
            }
        }

    fun listenToIncomingFriendRequests(userId: String): ListenerRegistration? =
        remoteAccountService.listenToIncomingFriendRequests(userId) { list ->
            CoroutineScope(Dispatchers.IO).launch {
                val pendingRequesters = mutableListOf<String>()
                for (req in list) {
                    friendshipDao.insertFriendship(req)
                    if (req.status.equals("PENDING", ignoreCase = true)) {
                        pendingRequesters.add(req.requesterId)
                    }
                    if (req.requesterId.isNotBlank() && userDao.getUserByIdSync(req.requesterId) == null) {
                        fetchAndSyncUserProfile(req.requesterId)
                    }
                }
                if (pendingRequesters.isEmpty()) {
                    friendshipDao.deleteAllPendingIncoming(userId)
                } else {
                    friendshipDao.deletePendingIncomingNotIn(userId, pendingRequesters)
                }
            }
        }

    fun listenToOutgoingFriendRequests(userId: String): ListenerRegistration? =
        remoteAccountService.listenToOutgoingFriendRequests(userId) { list ->
            CoroutineScope(Dispatchers.IO).launch {
                val pendingTargets = mutableListOf<String>()
                for (req in list) {
                    friendshipDao.insertFriendship(req)
                    if (req.status.equals("PENDING", ignoreCase = true)) {
                        pendingTargets.add(req.targetId)
                    }
                    if (req.targetId.isNotBlank() && userDao.getUserByIdSync(req.targetId) == null) {
                        fetchAndSyncUserProfile(req.targetId)
                    }
                }
                if (pendingTargets.isEmpty()) {
                    friendshipDao.deleteAllPendingOutgoing(userId)
                } else {
                    friendshipDao.deletePendingOutgoingNotIn(userId, pendingTargets)
                }
            }
        }

    fun listenToUserNotifications(userId: String): ListenerRegistration? =
        remoteAccountService.listenToUserNotifications(userId) { list, removedIds ->
            CoroutineScope(Dispatchers.IO).launch {
                if (list.isNotEmpty()) {
                    notificationDao.insertNotifications(list)
                }
                removedIds.forEach { removedId ->
                    notificationDao.deleteNotification(removedId)
                }
            }
        }

    fun listenToAllUsers(): ListenerRegistration? =
        remoteAccountService.listenToAllUsers(1500) { remoteUsers, removedIds ->
            CoroutineScope(Dispatchers.IO).launch {
                val activeUid = sessionManager?.getActiveUserId().orEmpty()
                val validRemote = remoteUsers.filter { u ->
                    !u.userId.endsWith("_teacher") &&
                    u.userId != "user_len_50638964" &&
                    u.userId != "test_dummy_user_to_purge"
                }
                if (validRemote.isNotEmpty()) {
                    userDao.upsertRemoteUsersSafely(validRemote, activeUid.ifBlank { null })
                }
                for (removedId in removedIds) {
                    if (removedId != OFFICIAL_LENO_ID && removedId != "usr_officialjaiby_2026" && removedId != activeUid) {
                        userDao.deleteUser(removedId)
                        accountBackupStore?.deleteAccount(removedId)
                        sessionManager?.removeDeviceAccountId(removedId)
                    }
                }
            }
        }

    fun listenToUserLessonProgress(userId: String): ListenerRegistration? {
        val ctx = context ?: return null
        return remoteAccountService.listenToUserLessonProgress(
            userId = userId,
            onStudyProfile = { streakDays, lastStudyDate, lastOpenedClassId ->
                LessonProgressManager.applyRemoteStudyProfile(
                    context = ctx,
                    userId = userId,
                    streakDays = streakDays,
                    lastStudyDate = lastStudyDate,
                    lastOpenedClassId = lastOpenedClassId
                )
            },
            onProgressUpdate = { classId, completedIds, quizScores, lastOpenedLessonId ->
                LessonProgressManager.applyRemoteClassProgress(
                    context = ctx,
                    userId = userId,
                    classId = classId,
                    completedIds = completedIds,
                    quizScores = quizScores,
                    lastOpenedLessonId = lastOpenedLessonId
                )
            }
        )
    }

    fun listenToUserCertificates(userId: String): ListenerRegistration? {
        val ctx = context ?: return null
        return remoteAccountService.listenToUserCertificates(userId) { certs ->
            certs.forEach { cert ->
                LessonProgressManager.applyRemoteCertificate(ctx, cert)
            }
        }
    }

    fun listenToClassCertificates(classId: String): ListenerRegistration? {
        val ctx = context ?: return null
        return remoteAccountService.listenToClassCertificates(classId) { certs ->
            certs.forEach { cert ->
                LessonProgressManager.applyRemoteCertificate(ctx, cert)
            }
        }
    }

    suspend fun syncEnrollmentsFromRemote(studentUserId: String) = withContext(Dispatchers.IO) {
        try {
            val list = remoteAccountService.fetchEnrollmentsFromRemote(studentUserId)
            list.forEach { enrollmentDao.insertEnrollment(it) }
        } catch (e: Exception) {
            Log.w("LenoRepository", "syncEnrollmentsFromRemote error: ${e.message}")
        }
    }

    suspend fun deleteClass(classId: String) {
        classDao.deleteClassById(classId)
        lessonDao.deleteLessonsForClass(classId)
        enrollmentDao.deleteAllEnrollmentsForClass(classId)
        classQuestionDao.deleteQuestionsForClass(classId)
        CoroutineScope(Dispatchers.IO).launch {
            remoteAccountService.deleteClassFromRemote(classId)
        }
    }

    // Lessons
    fun getLessonsForClass(classId: String): Flow<List<LessonEntity>> {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remote = remoteAccountService.fetchLessonsForClassFromRemote(classId)
                if (remote.isNotEmpty()) {
                    lessonDao.insertLessons(remote)
                }
            } catch (_: Exception) {}
        }
        return lessonDao.getLessonsForClass(classId)
    }

    fun listenToClassLessons(classId: String, onLessons: (List<LessonEntity>) -> Unit): ListenerRegistration? =
        remoteAccountService.listenToClassLessonsWithRemovals(classId) { list, removedIds ->
            CoroutineScope(Dispatchers.IO).launch {
                if (list.isNotEmpty()) {
                    lessonDao.insertLessons(list)
                    lessonDao.deleteLessonsNotIn(classId, list.map { it.lessonId })
                } else if (removedIds.isNotEmpty()) {
                    lessonDao.deleteLessonsForClass(classId)
                }
                classDao.updateLessonCount(classId, list.size)
            }
            onLessons(list)
        }

    val lessonSeenByMap: StateFlow<Map<String, Set<String>>>
        get() = remoteAccountService.lessonSeenByMap

    suspend fun getLessonsForClassSync(classId: String): List<LessonEntity> =
        lessonDao.getLessonsForClassSync(classId)

    suspend fun recordLessonSeen(classId: String, lessonId: String, userId: String) {
        if (classId.isBlank() || lessonId.isBlank() || userId.isBlank()) return
        remoteAccountService.recordLessonSeenInRemote(classId, lessonId, userId)
    }

    suspend fun addLessonToClass(lesson: LessonEntity, callerUserId: String = ""): Result<Unit> {
        val parentClass = classDao.getClassByIdSync(lesson.classId)
            ?: return Result.failure(Exception("Parent class not found."))

        // STRICT ACCESS CONTROL: Only the creator of the class can add lessons.
        // Even if the caller is an approved teacher, they can NEVER create lessons in another teacher's class.
        if (callerUserId.isNotBlank()) {
            val caller = userDao.getUserByIdSync(callerUserId)
            val isCallerAdmin = caller?.isAdmin == true
            if (callerUserId != parentClass.instructorUserId && !isCallerAdmin) {
                return Result.failure(Exception("Unauthorized: You cannot create lessons in another teacher's class. Only the teacher who created this class can manage it."))
            }
        }

        val role = teacherRoleDao.getTeacherRoleSync(parentClass.instructorUserId)
        val user = userDao.getUserByIdSync(parentClass.instructorUserId)
        val isApproved = (user != null && user.isApprovedTeacher) ||
                (role != null && role.isApprovedTeacher) ||
                (user?.isAdmin == true) ||
                (user?.role.equals("TEACHER", ignoreCase = true))

        if (!isApproved) {
            return Result.failure(Exception("Unauthorized: Only approved Teachers can add lessons."))
        }

        lessonDao.insertLesson(lesson)
        val count = lessonDao.getLessonCountForClass(lesson.classId)
        classDao.updateLessonCount(lesson.classId, count)
        remoteAccountService.saveLessonToRemote(lesson)
        return Result.success(Unit)
    }

    suspend fun updateLessonInClass(lesson: LessonEntity, callerUserId: String = ""): Result<Unit> {
        val parentClass = classDao.getClassByIdSync(lesson.classId)
            ?: return Result.failure(Exception("Parent class not found."))
        if (callerUserId.isNotBlank()) {
            val caller = userDao.getUserByIdSync(callerUserId)
            val isCallerAdmin = caller?.isAdmin == true
            if (callerUserId != parentClass.instructorUserId && !isCallerAdmin) {
                return Result.failure(Exception("Unauthorized: Only the teacher who created this class can edit lessons."))
            }
        }
        lessonDao.insertLesson(lesson)
        remoteAccountService.updateLessonInRemote(lesson)
        return Result.success(Unit)
    }

    suspend fun deleteLessonFromClass(classId: String, lessonId: String, callerUserId: String = ""): Result<Unit> {
        val parentClass = classDao.getClassByIdSync(classId)
            ?: return Result.failure(Exception("Parent class not found."))
        if (callerUserId.isNotBlank()) {
            val caller = userDao.getUserByIdSync(callerUserId)
            val isCallerAdmin = caller?.isAdmin == true
            if (callerUserId != parentClass.instructorUserId && !isCallerAdmin) {
                return Result.failure(Exception("Unauthorized: Only the teacher who created this class can delete lessons."))
            }
        }
        lessonDao.deleteLesson(lessonId)
        val count = lessonDao.getLessonCountForClass(classId)
        classDao.updateLessonCount(classId, count)
        remoteAccountService.deleteLessonFromRemote(classId, lessonId)
        return Result.success(Unit)
    }

    // Teacher Role & Applications
    fun getTeacherRole(userId: String): Flow<TeacherRoleEntity?> =
        teacherRoleDao.getTeacherRole(userId)

    suspend fun getTeacherRoleSync(userId: String): TeacherRoleEntity? =
        teacherRoleDao.getTeacherRoleSync(userId)

    suspend fun submitTeacherApplication(application: TeacherRoleEntity): Result<Unit> {
        val existingRole = teacherRoleDao.getTeacherRoleSync(application.userId)
        if (existingRole != null && !existingRole.isNotApplied) {
            if (existingRole.isPending || existingRole.status.equals("PENDING", ignoreCase = true)) {
                return Result.failure(Exception("You already have an application under review (Pending). You cannot submit another application."))
            }
            if (existingRole.isApprovedTeacher || existingRole.status.equals("APPROVED", ignoreCase = true)) {
                return Result.failure(Exception("You are already an approved teacher for ${existingRole.effectiveApprovedSubject}. Each teacher is restricted to one subject."))
            }
            if (existingRole.status.equals("REJECTED", ignoreCase = true) || existingRole.isRejected) {
                return Result.failure(Exception("You have already applied. Teacher applications can only be submitted one time."))
            }
            return Result.failure(Exception("Teacher applications can only be submitted one time per teacher."))
        }

        val existingUser = userDao.getUserByIdSync(application.userId)
        val userTeacherStatus = existingUser?.teacherStatus?.trim().orEmpty()
        val isUserNotApplied = userTeacherStatus.isBlank() ||
                userTeacherStatus.equals("NOT APPLIED", ignoreCase = true) ||
                userTeacherStatus.equals("NOT_APPLIED", ignoreCase = true) ||
                userTeacherStatus.equals("Normal User", ignoreCase = true)

        if (existingUser != null && !isUserNotApplied) {
            if (existingUser.teacherStatus.equals("PENDING", ignoreCase = true)) {
                return Result.failure(Exception("You already have an application under review (Pending). You can only apply one time."))
            }
            if (existingUser.teacherStatus.equals("REJECTED", ignoreCase = true)) {
                return Result.failure(Exception("Teacher applications can only be submitted one time per user. Your previous application was already reviewed."))
            }
            if (existingUser.teacherStatus.equals("APPROVED", ignoreCase = true) || existingUser.isTeacher) {
                return Result.failure(Exception("You are already an approved teacher for ${existingUser.assignedSubject}. Each teacher is restricted to one subject."))
            }
            return Result.failure(Exception("Teacher applications can only be submitted one time per teacher."))
        }

        // STRICT ENFORCEMENT: ONE TEACHER = ONE SUBJECT
        val selectedSubject = application.approvedSubject.ifBlank {
            application.subjects.trim().split(",").firstOrNull()?.trim() ?: "Maths"
        }.trim().split(",").firstOrNull()?.trim().orEmpty().ifBlank { "Maths" }

        val cleanApplication = application.copy(
            status = "PENDING",
            approvedSubject = selectedSubject,
            subjects = selectedSubject,
            submittedAt = System.currentTimeMillis()
        )

        // Commit teacher application to Firestore first so cross-device Admin consoles always see it
        val user = userDao.getUserByIdSync(application.userId)
        val remoteRes = try {
            remoteAccountService.submitTeacherApplicationToFirestore(
                userId = application.userId,
                application = cleanApplication,
                applicantName = user?.displayName?.ifBlank { user.username } ?: "",
                applicantEmail = user?.email ?: "",
                applicantUsername = user?.username ?: "",
                applicantAvatarUrl = user?.avatarUrl ?: "",
                applicantLenoId = user?.linoId ?: ""
            )
        } catch (e: Exception) {
            Result.failure(e)
        }

        if (remoteRes.isFailure) {
            val err = remoteRes.exceptionOrNull()
            Log.e("LenoRepository", "Firestore teacher application submission failed: ${err?.message}", err)
            return Result.failure(
                Exception(
                    err?.message ?: "Could not submit teacher application to Leno Cloud. Please check your connection and try again.",
                    err
                )
            )
        }

        teacherRoleDao.insertOrUpdate(cleanApplication)
        userDao.updateUserTeacherStatus(application.userId, "PENDING", selectedSubject)

        return Result.success(Unit)
    }

    /**
     * Monitors 'teacher_applications' collection in Firestore in real time.
     */
    fun listenToTeacherApplications(onApplications: (List<TeacherApplicationItem>) -> Unit): ListenerRegistration? =
        remoteAccountService.listenToTeacherApplications(onApplications)

    suspend fun fetchTeacherApplications(): List<TeacherApplicationItem> = withContext(Dispatchers.IO) {
        val remoteList = try {
            remoteAccountService.fetchTeacherApplicationsFromFirestore()
        } catch (e: Exception) {
            emptyList()
        }
        val map = LinkedHashMap<String, TeacherApplicationItem>()
        remoteList.forEach { map[it.userId] = it }

        // Also merge any applications in local Room teacher_roles
        try {
            val localPending = teacherRoleDao.getPendingTeacherApplicationsSync()
            for (role in localPending) {
                if (!map.containsKey(role.userId) || map[role.userId]?.isPending != true) {
                    val user = userDao.getUserByIdSync(role.userId)
                    map[role.userId] = TeacherApplicationItem(
                        applicationId = role.userId,
                        applicantUid = role.userId,
                        lenoId = user?.linoId ?: "",
                        fullName = user?.displayName?.ifBlank { user.username } ?: "Leno Member",
                        username = user?.username ?: "",
                        subject = role.effectiveApprovedSubject,
                        qualification = role.educationQualification,
                        experience = role.teachingExperience,
                        status = role.status,
                        submittedAt = role.submittedAt,
                        userId = role.userId,
                        applicantName = user?.displayName?.ifBlank { user.username } ?: "Leno Member",
                        applicantEmail = user?.email ?: "",
                        applicantUsername = user?.username ?: "",
                        applicantAvatarUrl = user?.avatarUrl ?: "",
                        approvedSubject = role.effectiveApprovedSubject,
                        teachingLevel = role.teachingLevel,
                        educationQualification = role.educationQualification,
                        teachingExperience = role.teachingExperience,
                        teacherIntro = role.teacherIntro
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error merging local teacher roles: ${e.message}")
        }

        // Also merge any users marked with teacherStatus == "PENDING" in local Room
        try {
            val allUsers = userDao.getAllUsersSync()
            for (u in allUsers) {
                if (u.teacherStatus.equals("PENDING", ignoreCase = true) && !map.containsKey(u.userId)) {
                    val subj = u.teacherSubject.ifBlank { u.assignedSubject }.ifBlank { "General" }
                    val dispName = u.displayName.ifBlank { u.username }.ifBlank { "Leno Member" }
                    map[u.userId] = TeacherApplicationItem(
                        applicationId = u.userId,
                        applicantUid = u.userId,
                        lenoId = u.linoId,
                        fullName = dispName,
                        username = u.username,
                        subject = subj,
                        qualification = "Verified Applicant",
                        experience = "1+ years",
                        status = "PENDING",
                        submittedAt = System.currentTimeMillis(),
                        userId = u.userId,
                        applicantName = dispName,
                        applicantEmail = u.email,
                        applicantUsername = u.username,
                        applicantAvatarUrl = u.avatarUrl,
                        approvedSubject = subj,
                        teachingLevel = "All Levels",
                        educationQualification = "Degree / Certification",
                        teachingExperience = "1+ years",
                        teacherIntro = "Passionate educator on Leno platform."
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error merging pending users: ${e.message}")
        }

        map.values.sortedByDescending { it.submittedAt }
    }

    /**
     * Admin approves teacher application:
     * - Updates Firestore 'teacher_applications' document to status = "APPROVED"
     * - Updates user's document role to 'teacher' in Firestore
     * - Activates teacher role locally in Room
     */
    suspend fun approveTeacherApplicationByAdmin(
        userId: String,
        subject: String,
        adminId: String = "admin"
    ): Result<Unit> {
        val cleanSubject = subject.trim().split(",").firstOrNull()?.trim().orEmpty().ifBlank { "Maths" }
        val existing = teacherRoleDao.getTeacherRoleSync(userId)
        val updatedRole = (existing ?: TeacherRoleEntity(
            userId = userId,
            agreedToGuidelines = true,
            submittedAt = System.currentTimeMillis()
        )).copy(
            status = "APPROVED",
            approvedSubject = cleanSubject,
            subjects = cleanSubject,
            reviewedAt = System.currentTimeMillis()
        )
        teacherRoleDao.insertOrUpdate(updatedRole)
        userDao.approveUserAsTeacher(userId, cleanSubject)

        val firestoreResult = remoteAccountService.approveTeacherApplicationByAdminInFirestore(userId, cleanSubject, adminId)
        val approveNotif = NotificationEntity(
            id = "teacher_app_${userId}_${System.currentTimeMillis()}",
            userId = userId,
            senderId = OFFICIAL_LENO_ID,
            title = "Teacher Application Approved! 🎓",
            body = "Congratulations! You are now an approved teacher for $cleanSubject on Leno.",
            type = "SYSTEM"
        )
        notificationDao.insertNotification(approveNotif)
        CoroutineScope(Dispatchers.IO).launch {
            remoteAccountService.saveNotificationToRemote(approveNotif)
        }
        return firestoreResult
    }

    /**
     * Admin rejects teacher application:
     * - Updates Firestore 'teacher_applications' document to status = "REJECTED"
     * - Updates user's document in Firestore
     * - Updates local role
     */
    suspend fun rejectTeacherApplicationByAdmin(
        userId: String,
        reason: String = "Requirements not met",
        adminId: String = "admin"
    ): Result<Unit> {
        teacherRoleDao.updateStatus(userId, "REJECTED")
        userDao.updateUserTeacherStatus(userId, "REJECTED", "")
        val rejectNotif = NotificationEntity(
            id = "teacher_rej_${userId}_${System.currentTimeMillis()}",
            userId = userId,
            senderId = OFFICIAL_LENO_ID,
            title = "Teacher Application Update",
            body = "Your teacher application was not approved: $reason",
            type = "SYSTEM"
        )
        notificationDao.insertNotification(rejectNotif)
        CoroutineScope(Dispatchers.IO).launch {
            remoteAccountService.saveNotificationToRemote(rejectNotif)
        }
        return remoteAccountService.rejectTeacherApplicationByAdminInFirestore(userId, reason, adminId)
    }

    /**
     * Real-time listener on the user's document in Firestore ('users/{userId}').
     * Receives updates when the role or permissions change, updating Room and the UI immediately.
     */
    fun listenToUserDocument(userId: String, onUserUpdated: (UserEntity) -> Unit): ListenerRegistration? {
        return remoteAccountService.listenToUserDocument(userId, onUserUpdated)
    }

    /**
     * Synchronizes a remote user document update from Firestore into local Room storage,
     * triggering instant UI recomposition via Room Flow.
     */
    suspend fun handleRemoteUserDocumentUpdate(remoteUser: UserEntity) = withContext(Dispatchers.IO) {
        val currentLocal = userDao.getUserByIdSync(remoteUser.userId)
        val isTeacher = remoteUser.isTeacher || remoteUser.role.equals("TEACHER", ignoreCase = true)
        val cleanSubject = remoteUser.effectiveSubject.ifBlank { remoteUser.assignedSubject }
            .trim().split(",").firstOrNull()?.trim().orEmpty()

        if (currentLocal != null) {
            val updatedUser = currentLocal.copy(
                role = if (isTeacher) "TEACHER" else remoteUser.role,
                assignedSubject = if (isTeacher) cleanSubject else "",
                teacherStatus = remoteUser.teacherStatus,
                teacherSubject = remoteUser.teacherSubject.ifBlank { cleanSubject },
                creatorStatus = remoteUser.creatorStatus,
                updatedAt = remoteUser.updatedAt,
                displayName = remoteUser.displayName.ifBlank { currentLocal.displayName },
                bio = remoteUser.bio.ifBlank { currentLocal.bio },
                avatarUrl = remoteUser.avatarUrl.ifBlank { currentLocal.avatarUrl },
                linoId = remoteUser.linoId.ifBlank { currentLocal.linoId },
                statusMessage = remoteUser.statusMessage.ifBlank { currentLocal.statusMessage },
                isOnline = remoteUser.isOnline,
                lastSeen = maxOf(remoteUser.lastSeen, currentLocal.lastSeen),
                isRestricted = remoteUser.isRestricted,
                isVerified = remoteUser.isVerified || currentLocal.isVerified,
                isOfficial = remoteUser.isOfficial || currentLocal.isOfficial
            )
            userDao.insertUser(updatedUser)
            if (isTeacher) {
                userDao.approveUserAsTeacher(remoteUser.userId, cleanSubject, remoteUser.updatedAt)
            }
        } else {
            userDao.insertUser(remoteUser.copy(
                role = if (isTeacher) "TEACHER" else remoteUser.role,
                assignedSubject = if (isTeacher) cleanSubject else "",
                teacherStatus = remoteUser.teacherStatus,
                teacherSubject = remoteUser.teacherSubject.ifBlank { cleanSubject }
            ))
        }

        if (isTeacher) {
            val existingRole = teacherRoleDao.getTeacherRoleSync(remoteUser.userId)
            val updatedRole = (existingRole ?: TeacherRoleEntity(
                userId = remoteUser.userId,
                agreedToGuidelines = true,
                submittedAt = System.currentTimeMillis()
            )).copy(
                status = "APPROVED",
                approvedSubject = cleanSubject,
                subjects = cleanSubject,
                reviewedAt = System.currentTimeMillis()
            )
            teacherRoleDao.insertOrUpdate(updatedRole)
        } else {
            teacherRoleDao.updateStatus(remoteUser.userId, "NORMAL")
        }
    }

    /**
     * Admin updates any user's role (not only teachers, but every user).
     * Triggers the update function to set role to 'teacher' or 'user' in their profile in Firestore and Room.
     */
    suspend fun updateUserRoleByAdmin(
        userId: String,
        newRole: String, // "teacher" or "user"
        assignedSubject: String = "",
        adminId: String = "admin"
    ): Result<Unit> {
        val cleanRole = newRole.trim().lowercase()
        val isTeacher = cleanRole == "teacher"
        val cleanSubject = if (isTeacher) assignedSubject.trim().split(",").firstOrNull()?.trim().orEmpty().ifBlank { "General Education" } else ""

        if (isTeacher) {
            val existing = teacherRoleDao.getTeacherRoleSync(userId)
            val updatedRole = (existing ?: TeacherRoleEntity(
                userId = userId,
                agreedToGuidelines = true,
                submittedAt = System.currentTimeMillis()
            )).copy(
                status = "APPROVED",
                approvedSubject = cleanSubject,
                subjects = cleanSubject,
                reviewedAt = System.currentTimeMillis()
            )
            teacherRoleDao.insertOrUpdate(updatedRole)
            userDao.updateUserRole(userId, "TEACHER", cleanSubject)
        } else {
            teacherRoleDao.updateStatus(userId, "NORMAL")
            userDao.updateUserRole(userId, "USER", "")
        }

        // Sync with remote Firestore account service
        val remoteRes = remoteAccountService.updateUserRoleInFirestore(userId, cleanRole, cleanSubject, adminId)
        if (remoteRes.isFailure) {
            Log.w("LenoRepository", "Failed syncing teacher role remotely: ${remoteRes.exceptionOrNull()?.message}")
            return remoteRes
        }
        return Result.success(Unit)
    }

    /**
     * Fetches all registered users from Cloud Firestore for admin review and role assignment.
     */
    suspend fun fetchAllUsersForAdmin(): List<UserEntity> {
        val ignoredUsernames = setOf("bello_maths", "ibrahim_quran", "fatima_english", "usman_skills", "jibril")
        val remoteUsers = remoteAccountService.fetchAllRemoteUsers(1500)
        if (remoteUsers.isNotEmpty()) {
            userDao.upsertRemoteUsersSafely(remoteUsers, sessionManager?.getActiveUserId())
        }
        val allLocal = userDao.getAllUsersSync()
        return (remoteUsers + allLocal)
            .filter {
                it.userId.isNotBlank() &&
                    !it.userId.endsWith("_teacher") &&
                    it.userId != "user_len_50638964" &&
                    it.userId != "test_dummy_user_to_purge" &&
                    it.username.lowercase() !in ignoredUsernames
            }
            .distinctBy { it.userId }
    }

    suspend fun updateTeacherStatus(userId: String, status: String, subject: String? = null, isTrusted: Boolean = false) {
        val existing = teacherRoleDao.getTeacherRoleSync(userId)
        val cleanSubject = (subject ?: existing?.effectiveApprovedSubject ?: "Maths")
            .trim().split(",").firstOrNull()?.trim().orEmpty().ifBlank { "Maths" }

        val isApproved = status.equals("APPROVED", ignoreCase = true) || status.equals("Teacher", ignoreCase = true)
        if (isApproved) {
            val updatedRole = (existing ?: TeacherRoleEntity(
                userId = userId,
                agreedToGuidelines = true,
                submittedAt = System.currentTimeMillis()
            )).copy(
                status = "APPROVED",
                approvedSubject = cleanSubject,
                subjects = cleanSubject,
                isTrustedTeacher = isTrusted,
                reviewedAt = System.currentTimeMillis()
            )
            teacherRoleDao.insertOrUpdate(updatedRole)
            userDao.updateUserRole(userId, "TEACHER", cleanSubject)
            remoteAccountService.assignTeacherRoleInFirestore(userId, cleanSubject, isTrusted = isTrusted)
        } else {
            if (existing != null) {
                teacherRoleDao.updateStatus(userId, status)
            }
            userDao.updateUserRole(userId, "USER", "")
        }
    }

    suspend fun assignTeacherRole(
        userId: String,
        subject: String,
        isTrusted: Boolean = false,
        qualification: String = "",
        experience: String = ""
    ): Result<Unit> {
        val cleanSubject = subject.trim().split(",").firstOrNull()?.trim().orEmpty()
        if (cleanSubject.isBlank()) {
            return Result.failure(Exception("A valid single subject must be specified."))
        }

        val existing = teacherRoleDao.getTeacherRoleSync(userId)
        val updatedRole = (existing ?: TeacherRoleEntity(
            userId = userId,
            agreedToGuidelines = true,
            submittedAt = System.currentTimeMillis()
        )).copy(
            status = "APPROVED",
            approvedSubject = cleanSubject,
            subjects = cleanSubject,
            isTrustedTeacher = isTrusted,
            educationQualification = qualification.ifBlank { existing?.educationQualification ?: "" },
            teachingExperience = experience.ifBlank { existing?.teachingExperience ?: "" },
            reviewedAt = System.currentTimeMillis()
        )

        teacherRoleDao.insertOrUpdate(updatedRole)
        userDao.updateUserRole(userId, "TEACHER", cleanSubject)
        return remoteAccountService.assignTeacherRoleInFirestore(
            userId = userId,
            subject = cleanSubject,
            qualification = qualification,
            experience = experience,
            isTrusted = isTrusted
        )
    }

    suspend fun assignStudentRole(userId: String): Result<Unit> {
        val existing = teacherRoleDao.getTeacherRoleSync(userId)
        if (existing != null) {
            teacherRoleDao.insertOrUpdate(existing.copy(status = "NOT APPLIED", approvedSubject = "", subjects = ""))
        }
        userDao.updateUserRole(userId, "STUDENT", "")
        return remoteAccountService.assignStudentRoleInFirestore(userId)
    }

    suspend fun toggleContentCreator(userId: String, isCreator: Boolean) {
        val current = teacherRoleDao.getTeacherRoleSync(userId)
        if (current != null) {
            teacherRoleDao.updateContentCreator(userId, isCreator)
        } else {
            teacherRoleDao.insertOrUpdate(
                TeacherRoleEntity(
                    userId = userId,
                    status = "NOT APPLIED",
                    isContentCreator = isCreator,
                    agreedToGuidelines = true
                )
            )
        }
    }

    // Enrollments
    suspend fun enrollInClass(
        studentUserId: String,
        studentLinoId: String,
        classId: String,
        teacherUserId: String = ""
    ): Boolean {
        if (enrollmentDao.isStudentEnrolledSync(studentUserId, classId)) {
            return true
        }
        val targetClass = classDao.getClassByIdSync(classId)
        val resolvedTeacherId = if (teacherUserId.isNotBlank()) teacherUserId else (targetClass?.instructorUserId ?: "")

        // STRICT TEACHER RESTRICTION: A teacher cannot access or enroll in another teacher's class
        val studentUser = userDao.getUserByIdSync(studentUserId)
        val isStudentTeacher = (studentUser != null && studentUser.isTeacher) ||
                (studentUser?.role.equals("TEACHER", ignoreCase = true)) ||
                (studentUser?.teacherStatus.equals("APPROVED", ignoreCase = true))
        if (isStudentTeacher && targetClass != null && targetClass.instructorUserId != studentUserId && studentUser?.isAdmin != true) {
            Log.w("LenoRepository", "Teacher $studentUserId is restricted from enrolling in another teacher's class $classId")
            return false
        }

        val enrollment = EnrollmentEntity(
            enrollmentId = "enr_${UUID.randomUUID().toString().take(8)}",
            classId = classId,
            studentUserId = studentUserId,
            studentLinoId = studentLinoId,
            teacherUserId = resolvedTeacherId,
            status = "ACTIVE",
            enrolledAt = System.currentTimeMillis(),
            progress = 0
        )
        enrollmentDao.insertEnrollment(enrollment)
        classDao.incrementEnrolledStudents(classId)

        try {
            remoteAccountService.saveEnrollmentToRemote(enrollment)
        } catch (e: Exception) {
            Log.w("LenoRepository", "Firestore enrollment sync notice: ${e.message}")
        }

        // Real-time settlement: 100% of class fee credited to teacher's settlement balance
        if (resolvedTeacherId.isNotBlank()) {
            val priceStr = targetClass?.price ?: "₦1,500"
            val priceClean = priceStr.replace("₦", "").replace(",", "").trim().toDoubleOrNull() ?: 1500.0
            teacherRoleDao.creditTeacherEarnings(resolvedTeacherId, priceClean)
        }
        return true
    }

    suspend fun removeStudentFromClass(studentUserId: String, classId: String): Boolean {
        enrollmentDao.deleteEnrollment(studentUserId, classId)
        classDao.decrementEnrolledStudents(classId)
        try {
            remoteAccountService.deleteEnrollmentFromRemote(studentUserId, classId)
        } catch (e: Exception) {
            Log.w("LenoRepository", "Firestore enrollment removal notice: ${e.message}")
        }
        return true
    }

    fun getPendingTeacherApplications(): Flow<List<TeacherRoleEntity>> =
        teacherRoleDao.getPendingTeacherApplications()

    suspend fun updateBankSettlementAccount(userId: String, bankName: String, accountNumber: String, accountName: String) {
        teacherRoleDao.updateBankSettlementAccount(userId, bankName, accountNumber, accountName)
    }

    suspend fun recordTeacherPayout(userId: String, amount: Double) {
        teacherRoleDao.recordTeacherPayout(userId, amount)
    }

    fun isStudentEnrolled(studentUserId: String, classId: String): Flow<Boolean> =
        enrollmentDao.isStudentEnrolled(studentUserId, classId)

    fun getEnrolledClassesForStudent(studentUserId: String): Flow<List<ClassEntity>> =
        enrollmentDao.getEnrolledClasses(studentUserId)

    suspend fun getEnrollmentCountForClass(classId: String): Int =
        enrollmentDao.getEnrollmentCountForClass(classId)

    fun getEnrolledStudentsForClass(classId: String): Flow<List<UserEntity>> =
        enrollmentDao.getEnrolledStudentsForClass(classId)

    // Class Real-time Questions (Student can only ask questions, Teacher answers)
    fun getQuestionsForClass(classId: String): Flow<List<ClassQuestionEntity>> {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remote = remoteAccountService.fetchQuestionsForClassFromRemote(classId)
                if (remote.isNotEmpty()) {
                    classQuestionDao.insertQuestions(remote)
                }
            } catch (_: Exception) {}
        }
        return classQuestionDao.getQuestionsForClass(classId)
    }

    fun listenToClassQuestions(classId: String, onQuestions: (List<ClassQuestionEntity>) -> Unit): ListenerRegistration? =
        remoteAccountService.listenToClassQuestionsWithRemovals(classId) { list, removedIds ->
            CoroutineScope(Dispatchers.IO).launch {
                if (list.isNotEmpty()) {
                    classQuestionDao.insertQuestions(list)
                    classQuestionDao.deleteQuestionsNotIn(classId, list.map { it.questionId })
                } else if (removedIds.isNotEmpty()) {
                    removedIds.forEach { classQuestionDao.deleteQuestionById(it) }
                }
            }
            onQuestions(list)
        }

    suspend fun askQuestionInClass(
        classId: String,
        senderUserId: String,
        senderName: String,
        senderLinoId: String,
        questionText: String,
        audioUrl: String? = null,
        audioDurationSeconds: Int = 0,
        senderRole: String = "STUDENT"
    ): Result<Unit> {
        val cleanText = questionText.trim()
        if (cleanText.isBlank() && audioUrl.isNullOrBlank()) {
            return Result.failure(Exception("Question or message cannot be empty."))
        }
        val isTeacher = senderRole.equals("TEACHER", ignoreCase = true)
        val defaultText = if (isTeacher) "🎤 Teacher Voice Note ($audioDurationSeconds sec)" else "🎤 Voice Question ($audioDurationSeconds sec)"
        val q = ClassQuestionEntity(
            questionId = "q_" + java.util.UUID.randomUUID().toString().take(10),
            classId = classId,
            senderUserId = senderUserId,
            senderName = senderName,
            senderLinoId = senderLinoId,
            senderRole = senderRole,
            questionText = cleanText.ifBlank { defaultText },
            audioUrl = audioUrl,
            audioDurationSeconds = audioDurationSeconds,
            isAnswered = isTeacher,
            answeredAt = if (isTeacher) System.currentTimeMillis() else 0L,
            timestamp = System.currentTimeMillis()
        )
        classQuestionDao.insertQuestion(q)
        remoteAccountService.saveQuestionToRemote(classId, q)
        return Result.success(Unit)
    }

    suspend fun answerQuestionInClass(
        classId: String = "",
        questionId: String,
        answerText: String,
        audioUrl: String? = null,
        audioDurationSeconds: Int = 0
    ): Result<Unit> {
        val cleanAnswer = answerText.trim()
        if (cleanAnswer.isBlank() && audioUrl.isNullOrBlank()) {
            return Result.failure(Exception("Answer cannot be empty."))
        }
        val effectiveText = cleanAnswer.ifBlank { "🎤 Voice Answer ($audioDurationSeconds sec)" }
        classQuestionDao.answerQuestionWithAudio(
            questionId = questionId,
            answerText = effectiveText,
            answerAudioUrl = audioUrl,
            answerAudioDurationSeconds = audioDurationSeconds,
            answeredAt = System.currentTimeMillis()
        )
        if (classId.isNotBlank()) {
            remoteAccountService.answerQuestionInRemote(
                classId = classId,
                questionId = questionId,
                answerText = effectiveText,
                answerAudioUrl = audioUrl,
                answerAudioDurationSeconds = audioDurationSeconds
            )
        }
        return Result.success(Unit)
    }

    suspend fun setUserRestricted(userId: String, isRestricted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val remoteRes = remoteAccountService.setUserRestrictedInFirestore(userId, isRestricted)
            if (remoteRes.isFailure) {
                return@withContext remoteRes
            }
            userDao.setUserRestricted(userId, isRestricted)
            val ownedIds = sessionManager?.getDeviceAccountIds()
            if (ownedIds == null || userId in ownedIds) {
                userDao.getUserByIdSync(userId)?.let {
                    accountBackupStore?.saveAccount(it)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LenoRepository", "Error setting user restricted: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteUserByAdmin(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (userId == OFFICIAL_LENO_ID || userId == "usr_officialjaiby_2026") {
                return@withContext Result.failure(Exception("Cannot delete primary system account."))
            }
            val remoteRes = remoteAccountService.deleteUserInFirestore(userId)
            if (remoteRes.isFailure) {
                return@withContext remoteRes
            }
            userDao.deleteUser(userId)
            teacherRoleDao.deleteTeacherRole(userId)
            accountBackupStore?.deleteAccount(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LenoRepository", "Error deleting user: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Self-service account deletion: purges user's cloud profile/subcollections while Auth is active,
     * deletes Firebase Auth user, and wipes local caches/session.
     */
    suspend fun deleteOwnAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        val current = userDao.getCurrentUserSync()
        val fbUser = remoteAccountService.getCurrentAuthUser()
        val targetUid = current?.userId?.takeIf { it.isNotBlank() } ?: fbUser?.uid
            ?: return@withContext Result.failure(Exception("No active account found to delete."))

        if (targetUid == OFFICIAL_LENO_ID) {
            return@withContext Result.failure(Exception("System account cannot be deleted."))
        }

        isAuthTransitionInProgress = true
        try {
            setActiveChatPartnerId(null)
            unreadCountCache.clear()

            if (fbUser != null) {
                val remoteRes = remoteAccountService.deleteUserInFirestore(targetUid)
                if (remoteRes.isFailure && fbUser.uid != targetUid) {
                    remoteAccountService.deleteUserInFirestore(fbUser.uid)
                }
                try {
                    fbUser.delete().await()
                } catch (recentEx: FirebaseAuthRecentLoginRequiredException) {
                    return@withContext Result.failure(
                        Exception("For security, please sign out and sign back in with Google before permanently deleting your login account.", recentEx)
                    )
                } catch (authDelEx: Exception) {
                    Log.w("LenoRepository", "Firebase Auth user delete notice: ${authDelEx.message}")
                }
            }

            try { userDao.deleteUser(targetUid) } catch (_: Exception) {}
            try { teacherRoleDao.deleteTeacherRole(targetUid) } catch (_: Exception) {}
            try { messageDao.clearMessagesForUser(targetUid) } catch (_: Exception) {}
            accountBackupStore?.deleteAccount(targetUid)
            sessionManager?.removeDeviceAccountId(targetUid)
            sessionManager?.clearSession()
            userDao.clearCurrentUser()
            userDao.purgeDemoAccounts()
            accountBackupStore?.purgeDemoAccounts()
            ensureOfficialLenoAccountCreated()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LenoRepository", "deleteOwnAccount failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            isAuthTransitionInProgress = false
        }
    }

    suspend fun getOrCreateSupportChat(userId: String, userName: String): Result<SupportChatItem> {
        return supportChatService.getOrCreateSupportChat(userId, userName, OFFICIAL_LENO_ID)
    }

    fun listenToSupportChatMessages(chatId: String): Flow<List<SupportMessageItem>> {
        return supportChatService.listenToSupportChatMessages(chatId)
    }

    suspend fun sendSupportMessage(chatId: String, text: String, senderId: String, senderRole: String): Result<Unit> {
        return supportChatService.sendSupportMessage(chatId, text, senderId, senderRole)
    }

    fun listenToOpenSupportChats(): Flow<List<SupportChatItem>> {
        return supportChatService.listenToOpenSupportChats()
    }

    suspend fun updateSupportChatStatus(chatId: String, status: String): Result<Unit> {
        return supportChatService.updateSupportChatStatus(chatId, status)
    }
}
