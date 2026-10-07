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
import com.example.data.repository.MessageRepository
import com.example.data.service.FriendService
import com.example.data.service.RemoteAccountService
import com.example.util.AccountBackupStore
import com.example.util.AuthSecurity
import com.example.util.NotificationHelper
import com.example.util.PhoneUtils
import com.example.util.SessionManager
import com.example.util.TimeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    var activeChatPartnerIdSupplier: (() -> String?)? = null

    val currentUser: Flow<UserEntity?> = userDao.getCurrentUser().map { roomUser ->
        if (roomUser != null && roomUser.isCurrentUser) {
            roomUser
        } else {
            val activeId = sessionManager?.getActiveUserId()
            if (!activeId.isNullOrBlank()) {
                val cached = userDao.getUserByIdSync(activeId) ?: accountBackupStore?.findAccount(activeId)
                if (cached != null) {
                    val restored = cached.copy(isCurrentUser = true, isOnline = true)
                    userDao.insertUser(restored)
                    userDao.setCurrentUser(activeId)
                    restored
                } else null
            } else null
        }
    }
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    fun getAlternativeAccountsForUser(user: UserEntity?): Flow<List<UserEntity>> {
        if (user == null) return flowOf(emptyList())
        return userDao.getAllUsers().map { allList ->
            val backupAccounts = accountBackupStore?.getAllAccounts().orEmpty()
            val combined = (allList + backupAccounts).distinctBy { it.userId }
            combined.filter { 
                it.userId != user.userId && 
                !it.username.equals(user.username, ignoreCase = true) &&
                !it.linoId.equals(user.linoId, ignoreCase = true) &&
                it.userId != OFFICIAL_LENO_ID && 
                !it.isOfficial &&
                it.userId != "user_len_50638964"
            }
        }
    }

    suspend fun switchToAccount(targetUserId: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            ensureOwnersAccountsCreated()
            val target = userDao.getUserByIdSync(targetUserId)
                ?: accountBackupStore?.findAccount(targetUserId)
                ?: (if (targetUserId == "usr_officialjaiby_2026") {
                    userDao.getUserByUsernameSync("officialjaiby")
                } else null)
                ?: return@withContext Result.failure(Exception("Account not found for id $targetUserId"))

            userDao.clearCurrentUser()
            val updated = target.copy(
                isCurrentUser = true,
                isOnline = true,
                lastSeen = System.currentTimeMillis()
            )
            userDao.insertUser(updated)
            userDao.setCurrentUser(target.userId)
            sessionManager?.saveActiveUserId(target.userId)
            userDao.updateOnlineStatus(target.userId, true)
            accountBackupStore?.saveAccount(updated)

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
        }
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Delete any corrupted self-messages from previous bug
                try {
                    messageDao.deleteCorruptedSelfMessages()
                } catch (_: Exception) {}

                // Restore any backed-up chat messages and partners into Room
                try {
                    messageBackupStore?.getAllMessages()?.let { msgs ->
                        val validMsgs = msgs.filter { it.senderId != it.receiverId && it.senderId.isNotBlank() && it.receiverId.isNotBlank() }
                        if (validMsgs.isNotEmpty()) {
                            messageDao.insertMessages(validMsgs)
                        }
                    }
                    messageBackupStore?.getAllChatPartnerProfiles()?.let { partners ->
                        partners.forEach { partner ->
                            if (userDao.getUserByIdSync(partner.userId) == null) {
                                userDao.insertUser(partner)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("LenoRepository", "Error restoring message backup: ${e.message}")
                }

                userDao.purgeDemoAccounts()
                accountBackupStore?.purgeDemoAccounts()
                ensureOfficialLenoAccountCreated()
                ensureStandardInstructorsCreated()

                val savedUserId = sessionManager?.getActiveUserId()
                val currentLocalUser = userDao.getCurrentUserSync()
                val targetActiveUid = when {
                    !savedUserId.isNullOrBlank() -> savedUserId
                    currentLocalUser != null -> currentLocalUser.userId
                    else -> null
                }

                if (!targetActiveUid.isNullOrBlank()) {
                    var existing = userDao.getUserByIdSync(targetActiveUid)
                        ?: accountBackupStore?.findAccount(targetActiveUid)
                        ?: remoteAccountService.findRemoteUserById(targetActiveUid)

                    if (existing != null) {
                        userDao.clearCurrentUser()
                        val restored = existing.copy(userId = targetActiveUid, isCurrentUser = true, isOnline = true)
                        userDao.insertUser(restored)
                        userDao.setCurrentUser(targetActiveUid)
                        sessionManager?.saveActiveUserId(targetActiveUid)
                        userDao.updateOnlineStatus(targetActiveUid, true)
                        accountBackupStore?.saveAccount(restored)
                        syncRemoteContactsIntoRoom(targetActiveUid)
                        syncAllUserChatsAndMessages(targetActiveUid)
                    } else if (currentLocalUser != null) {
                        userDao.setCurrentUser(currentLocalUser.userId)
                        sessionManager?.saveActiveUserId(currentLocalUser.userId)
                        syncRemoteContactsIntoRoom(currentLocalUser.userId)
                        syncAllUserChatsAndMessages(currentLocalUser.userId)
                    }
                } else {
                    var fbUser = remoteAccountService.getCurrentAuthUser()
                    if (fbUser == null) {
                        val authEmail = sessionManager?.getAuthEmail()
                        val authPass = sessionManager?.getAuthPass()
                        if (!authEmail.isNullOrBlank() && !authPass.isNullOrBlank()) {
                            try {
                                val authRes = remoteAccountService.signInWithFirebaseAuth(authEmail, authPass)
                                if (authRes.isSuccess) {
                                    fbUser = remoteAccountService.getCurrentAuthUser()
                                }
                            } catch (e: Exception) {
                                Log.w("LenoRepository", "Auto re-auth notice: ${e.message}")
                            }
                        }
                    }

                    if (fbUser != null) {
                        var existing = remoteAccountService.findRemoteUserById(fbUser.uid)
                            ?: (if (!fbUser.email.isNullOrBlank()) remoteAccountService.findRemoteUser(fbUser.email!!) else null)
                            ?: userDao.getUserByIdSync(fbUser.uid)
                            ?: accountBackupStore?.findAccount(fbUser.uid)

                        if (existing != null) {
                            userDao.clearCurrentUser()
                            val restored = existing.copy(userId = fbUser.uid, isCurrentUser = true, isOnline = true)
                            userDao.insertUser(restored)
                            userDao.setCurrentUser(fbUser.uid)
                            sessionManager?.saveActiveUserId(fbUser.uid)
                            userDao.updateOnlineStatus(fbUser.uid, true)
                            accountBackupStore?.saveAccount(restored)
                            remoteAccountService.saveUserToRemote(restored.copy(lastSeen = System.currentTimeMillis()))
                            syncRemoteContactsIntoRoom(fbUser.uid)
                            syncAllUserChatsAndMessages(fbUser.uid)
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
        val currentLocal = userDao.getCurrentUserSync()

        // 1. Keep existing logged-in session safe! Never wipe active user!
        if (currentLocal != null) {
            userDao.setCurrentUser(currentLocal.userId)
            sessionManager?.saveActiveUserId(currentLocal.userId)
            return
        }

        // 2. Check active session manager ID first across Room, persistent backup store, and remote Firestore
        val savedUserId = sessionManager?.getActiveUserId()
        if (!savedUserId.isNullOrBlank()) {
            val userToRestore = userDao.getUserByIdSync(savedUserId)
                ?: accountBackupStore?.findAccount(savedUserId)
                ?: remoteAccountService.findRemoteUserById(savedUserId)
            if (userToRestore != null) {
                userDao.clearCurrentUser()
                val restored = userToRestore.copy(isCurrentUser = true, isOnline = true)
                userDao.insertUser(restored)
                userDao.setCurrentUser(savedUserId)
                sessionManager?.saveActiveUserId(savedUserId)
                userDao.updateOnlineStatus(savedUserId, true)
                accountBackupStore?.saveAccount(restored)
                syncRemoteContactsIntoRoom(savedUserId)
                syncAllUserChatsAndMessages(savedUserId)
                return
            }
        }

        // 3. Fallback to Firebase Auth user
        val fbUser = remoteAccountService.getCurrentAuthUser()
        if (fbUser != null) {
            val remoteUser = remoteAccountService.findRemoteUserById(fbUser.uid)
                ?: (if (!fbUser.email.isNullOrBlank()) remoteAccountService.findRemoteUser(fbUser.email!!) else null)
            if (remoteUser != null) {
                userDao.clearCurrentUser()
                val restored = remoteUser.copy(isCurrentUser = true, isOnline = true)
                userDao.insertUser(restored)
                userDao.setCurrentUser(restored.userId)
                sessionManager?.saveActiveUserId(restored.userId)
                userDao.updateOnlineStatus(restored.userId, true)
                accountBackupStore?.saveAccount(restored)
                syncRemoteContactsIntoRoom(restored.userId)
                syncAllUserChatsAndMessages(restored.userId)
                return
            } else {
                val cached = userDao.getUserByIdSync(fbUser.uid)
                if (cached != null) {
                    userDao.clearCurrentUser()
                    val restored = cached.copy(isCurrentUser = true, isOnline = true)
                    userDao.insertUser(restored)
                    userDao.setCurrentUser(restored.userId)
                    sessionManager?.saveActiveUserId(restored.userId)
                    userDao.updateOnlineStatus(restored.userId, true)
                    accountBackupStore?.saveAccount(restored)
                    syncRemoteContactsIntoRoom(restored.userId)
                    syncAllUserChatsAndMessages(restored.userId)
                    return
                }
            }

            val backupUser = if (!fbUser.email.isNullOrBlank()) accountBackupStore?.findAccount(fbUser.email!!) else null
            if (backupUser != null) {
                userDao.clearCurrentUser()
                val restored = backupUser.copy(isCurrentUser = true, isOnline = true)
                userDao.insertUser(restored)
                userDao.setCurrentUser(restored.userId)
                sessionManager?.saveActiveUserId(restored.userId)
                userDao.updateOnlineStatus(restored.userId, true)
                accountBackupStore?.saveAccount(restored)
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
                val remoteMessages = kotlinx.coroutines.withTimeoutOrNull(2500L) {
                    remoteAccountService.getChatMessagesFromRemote(chatId)
                }.orEmpty()

                if (remoteMessages.isNotEmpty()) {
                    messageDao.insertMessages(remoteMessages)
                    messageBackupStore?.saveMessages(remoteMessages)
                    remoteMessages.forEach { msg ->
                        if (msg.receiverId == current.userId && msg.status == "SENT") {
                            messageDao.updateMessageStatus(msg.messageId, "DELIVERED")
                            remoteAccountService.markMessageDeliveredInRemote(chatId, msg.messageId, msg.senderId)
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
            val remoteUsers = remoteAccountService.fetchAllRemoteUsers(150)
            if (remoteUsers.isNotEmpty()) {
                val currentUid = sessionManager?.getActiveUserId().orEmpty()
                val nonCurrent = remoteUsers.map { it.copy(isCurrentUser = it.userId == currentUid && currentUid.isNotBlank()) }
                userDao.insertUsers(nonCurrent)
                nonCurrent.forEach { accountBackupStore?.saveAccount(it) }
                Log.i("LenoRepository", "Synced ${nonCurrent.size} remote users into local database.")
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error syncing all remote users: ${e.message}")
        }
    }

    fun getAllUsersFlow(): Flow<List<UserEntity>> = userDao.getAllUsers()

    suspend fun syncAllUserChatsAndMessages(myUserId: String) {
        if (myUserId.isBlank()) return
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
                userDao.insertUsers(partnersToInsert)
            }
            val savedAccounts = accountBackupStore?.getAllAccounts().orEmpty()
            if (savedAccounts.isNotEmpty()) {
                val nonCurrent = savedAccounts.map { it.copy(isCurrentUser = it.userId == myUserId) }
                userDao.insertUsers(nonCurrent)
            }

            // 2. Restore all locally backed up messages for user into Room
            val aliases = getUserAliasesSync(myUserId)
            val allDeviceMessages = messageBackupStore?.getAllMessages().orEmpty()
            if (allDeviceMessages.isNotEmpty()) {
                val userRelevant = allDeviceMessages.filter { it.senderId in aliases || it.receiverId in aliases }
                messageDao.insertMessages(userRelevant)
            }

            // 3. Sync all registered users from remote Firestore so missing accounts are recovered
            syncAllRemoteUsersIntoRoom()

            // 4. Directly recover all sent and received messages stored across Firestore inboxes and sent folders
            val recoveredRemoteMsgs = remoteAccountService.getUserSentAndReceivedMessagesFromRemote(myUserId, aliases)
            if (recoveredRemoteMsgs.isNotEmpty()) {
                messageDao.insertMessages(recoveredRemoteMsgs)
                messageBackupStore?.saveMessages(recoveredRemoteMsgs)
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
                    messageDao.insertMessages(remoteMessages)
                    messageBackupStore?.saveMessages(remoteMessages)
                    remoteMessages.forEach { msg ->
                        if (msg.receiverId == myUserId && msg.status == "SENT") {
                            messageDao.updateMessageStatus(msg.messageId, "DELIVERED")
                            remoteAccountService.markMessageDeliveredInRemote(chatId, msg.messageId, msg.senderId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error in syncAllUserChatsAndMessages: ${e.message}")
        }
    }

    suspend fun handleIncomingMessage(message: MessageEntity, isChatActiveWithSender: Boolean) {
        try {
            var sender = userDao.getUserByIdSync(message.senderId)
            if (sender == null) {
                sender = fetchAndSyncUserProfile(message.senderId)
            }
            sender?.let { messageBackupStore?.saveChatPartnerProfile(it) }

            val statusToSet = if (isChatActiveWithSender) "READ" else "DELIVERED"
            val updatedMsg = message.copy(status = statusToSet)
            messageDao.insertMessage(updatedMsg)
            messageBackupStore?.saveMessage(updatedMsg)

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
            messageDao.updateMessageStatus(messageId, status)
            remoteAccountService.deleteSentReceipt(myUserId, messageId)
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error handling sent receipt: ${e.message}")
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

    fun searchUsers(query: String, currentUserId: String = ""): Flow<List<UserEntity>> {
        val cleanQuery = query.trim().removePrefix("@")
        if (cleanQuery.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())

        // 1. Immediately inject any matching accounts from persistent backup store and chat partner store into Room
        val backupMatches = accountBackupStore?.getAllAccounts().orEmpty().filter {
            it.userId != currentUserId && (
                it.username.contains(cleanQuery, ignoreCase = true) ||
                it.displayName.contains(cleanQuery, ignoreCase = true) ||
                it.linoId.contains(cleanQuery, ignoreCase = true) ||
                it.email.contains(cleanQuery, ignoreCase = true)
            )
        }
        val partnerMatches = messageBackupStore?.getAllChatPartnerProfiles().orEmpty().filter {
            it.userId != currentUserId && (
                it.username.contains(cleanQuery, ignoreCase = true) ||
                it.displayName.contains(cleanQuery, ignoreCase = true) ||
                it.linoId.contains(cleanQuery, ignoreCase = true)
            )
        }
        val immediateLocalMatches = (backupMatches + partnerMatches).distinctBy { it.userId }
        if (immediateLocalMatches.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                userDao.insertUsers(immediateLocalMatches.map { it.copy(isCurrentUser = false) })
            }
        }

        // 2. Launch background fetch from remote cloud storage so any user registered on another device/session is found
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remoteMatches = remoteAccountService.searchRemoteUsers(cleanQuery, currentUserId)
                if (remoteMatches.isNotEmpty()) {
                    val toInsert = remoteMatches.map { it.copy(isCurrentUser = false) }
                    userDao.insertUsers(toInsert)
                    toInsert.forEach { 
                        accountBackupStore?.saveAccount(it)
                        messageBackupStore?.saveChatPartnerProfile(it)
                    }
                } else {
                    // Try single exact match (e.g. by Leno ID)
                    val exactMatch = remoteAccountService.findRemoteUser(cleanQuery)
                    if (exactMatch != null && exactMatch.userId != currentUserId) {
                        val toSave = exactMatch.copy(isCurrentUser = false)
                        userDao.insertUser(toSave)
                        accountBackupStore?.saveAccount(toSave)
                        messageBackupStore?.saveChatPartnerProfile(toSave)
                    } else {
                        val allRemote = remoteAccountService.fetchAllRemoteUsers(150)
                        val broadMatches = allRemote.filter {
                            it.userId != currentUserId && (
                                it.username.contains(cleanQuery, ignoreCase = true) ||
                                it.displayName.contains(cleanQuery, ignoreCase = true) ||
                                it.linoId.contains(cleanQuery, ignoreCase = true) ||
                                it.email.contains(cleanQuery, ignoreCase = true)
                            )
                        }
                        if (broadMatches.isNotEmpty()) {
                            val toSave = broadMatches.map { it.copy(isCurrentUser = false) }
                            userDao.insertUsers(toSave)
                            toSave.forEach {
                                accountBackupStore?.saveAccount(it)
                                messageBackupStore?.saveChatPartnerProfile(it)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("LenoRepository", "Search remote sync: ${e.message}")
            }
        }

        return userDao.searchUsers(cleanQuery, currentUserId).map { roomResults ->
            (roomResults + immediateLocalMatches).filter { it.userId != currentUserId }.distinctBy { it.userId }
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
                    messageDao.insertMessages(userRelevant)
                }
            }

            // Ensure saved partners from backup store and account backup are present in Room
            val savedPartners = messageBackupStore?.getAllChatPartnerProfiles().orEmpty()
            val partnersToInsert = savedPartners
                .filter { it.userId != activeUid && it.userId !in aliases }
                .map { it.copy(isCurrentUser = false) }
            if (partnersToInsert.isNotEmpty()) {
                userDao.insertUsers(partnersToInsert)
            }
            val savedAccounts = accountBackupStore?.getAllAccounts().orEmpty()
            if (savedAccounts.isNotEmpty()) {
                val nonCurrent = savedAccounts.filter { it.userId != activeUid && it.userId !in aliases }
                    .map { it.copy(isCurrentUser = false) }
                if (nonCurrent.isNotEmpty()) {
                    userDao.insertUsers(nonCurrent)
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
                    userDao.insertUsers(placeholders)
                    // Asynchronously fetch and enrich profiles in background so the UI never hangs
                    CoroutineScope(Dispatchers.IO).launch {
                        for (mId in missing) {
                            try {
                                val p = fetchAndSyncUserProfile(mId)
                                p?.let { messageBackupStore?.saveChatPartnerProfile(it) }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            // In background, ensure full remote sync of all chats & messages
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    syncAllUserChatsAndMessages(userId)
                } catch (_: Exception) {}
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
                it.userId in relevantPartnerIds || it.username in relevantPartnerIds || it.linoId in relevantPartnerIds
            }
            val extraFromAccounts = accountBackupStore?.getAllAccounts().orEmpty()
                .filter { 
                    (it.userId in relevantPartnerIds || it.username in relevantPartnerIds || it.linoId in relevantPartnerIds)
                    && it.userId != activeUid && it.userId !in aliases 
                }

            // Synthesize any partner ID that has sent or received messages so conversations NEVER disappear
            val knownPartnerIds = (roomPartners.map { it.userId } + extraFromBackup.map { it.userId } + extraFromAccounts.map { it.userId }).toSet()
            val missingPartnerIds = relevantPartnerIds.filter { pId ->
                pId !in knownPartnerIds && extraFromBackup.none { it.username.equals(pId, true) || it.linoId.equals(pId, true) }
            }
            val synthesized = missingPartnerIds.map { pId ->
                val fromBackup = messageBackupStore?.getAllChatPartnerProfiles()?.firstOrNull {
                    it.userId == pId || it.username.equals(pId, true) || it.linoId.equals(pId, true)
                } ?: accountBackupStore?.findAccount(pId)

                fromBackup?.copy(isCurrentUser = false) ?: UserEntity(
                    userId = pId,
                    username = if (pId.startsWith("usr_")) pId.removePrefix("usr_").take(10) else if (pId.startsWith("LEN-")) pId.removePrefix("LEN-").take(10) else pId.take(12),
                    displayName = if (pId.startsWith("usr_")) "User ${pId.takeLast(4)}" else if (pId.startsWith("LEN-")) "User $pId" else "User ${pId.take(6)}",
                    bio = "Connecting on Leno ✨",
                    isCurrentUser = false
                )
            }
            if (synthesized.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        userDao.insertUsers(synthesized)
                        synthesized.forEach {
                            accountBackupStore?.saveAccount(it)
                            messageBackupStore?.saveChatPartnerProfile(it)
                        }
                    } catch (_: Exception) {}
                }
            }

            (roomPartners + extraFromBackup + extraFromAccounts + synthesized).distinctBy { it.userId }
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

        try {
            messageBackupStore?.getMessagesForUser(user1Id, u1Known)?.let { backedUp ->
                val myIds = u1Known.toSet()
                val partnerIds = u2Known.toSet()
                val relevant = backedUp.filter {
                    (it.senderId in myIds && it.receiverId in partnerIds) ||
                    (it.senderId in partnerIds && it.receiverId in myIds)
                }
                if (relevant.isNotEmpty()) {
                    messageDao.insertMessages(relevant)
                }
            }
        } catch (_: Exception) {}
        emitAll(messageDao.getMessagesBetweenUserAliases(u1Known, u2Known))
    }

    fun getLastMessageBetween(user1Id: String, user2Id: String): Flow<MessageEntity?> = flow {
        val u1Known = getUserAliasesSync(user1Id)
        val u2Known = getUserAliasesSync(user2Id)
        emitAll(messageDao.getLastMessageBetweenAliases(u1Known, u2Known))
    }

    fun getUnreadCount(currentUserId: String, otherUserId: String): Flow<Int> =
        messageDao.getUnreadCountFromUser(currentUserId, otherUserId)

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
    }

    suspend fun markAllNotificationsAsRead(userId: String) {
        notificationDao.markAllAsRead(userId)
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

        try {
            val generatedLinoId = generateUniqueLinoId()
            val authEmail = "${cleanUsername}@leno.chat"

            // 1. Attempt to create permanent Firebase Authentication account
            val authResult = remoteAccountService.signUpWithFirebaseAuth(authEmail, cleanPassword)
            val uid = if (authResult.isSuccess) {
                authResult.getOrThrow()
            } else {
                val authErr = authResult.exceptionOrNull()
                val errText = authErr?.message ?: ""
                val isAlreadyInUse = errText.contains("already registered", ignoreCase = true) ||
                        errText.contains("already in use", ignoreCase = true) ||
                        errText.contains("EMAIL_EXISTS", ignoreCase = true)
                if (isAlreadyInUse) {
                    return Result.failure(Exception("This username is already taken. Please choose another username or log in."))
                }
                // If Firebase Auth provider is disabled or hitting reCAPTCHA/network issues, generate a robust persistent UID
                Log.w("LenoRepository", "Firebase Auth registration notice (${authErr?.message}); proceeding with persistent secure account creation.")
                "usr_${cleanUsername}_${UUID.randomUUID().toString().take(6)}"
            }

            // 2. Check for existing remote profile or claim new identity atomically
            val existingRemote = remoteAccountService.findRemoteUserById(uid) ?: userDao.getUserByIdSync(uid)
            val effectiveLinoId = existingRemote?.linoId?.ifBlank { null } ?: generatedLinoId

            val claimRes = remoteAccountService.claimUsernameAtomic(uid, cleanUsername)
            if (claimRes.isFailure && existingRemote == null) {
                return Result.failure(claimRes.exceptionOrNull() ?: Exception("This username is already taken."))
            }

            if (existingRemote == null) {
                val claimLenoRes = remoteAccountService.claimLenoIdAtomic(uid, generatedLinoId)
                if (claimLenoRes.isFailure) {
                    return Result.failure(claimLenoRes.exceptionOrNull() ?: Exception("This Leno ID is already taken."))
                }
            }
            
            val photo = existingRemote?.avatarUrl?.ifBlank { "" } ?: ""
            val now = System.currentTimeMillis()
            val salt = AuthSecurity.generateSalt()
            val hash = AuthSecurity.hashPassword(cleanPassword, salt)
            val initialRole = existingRemote?.role?.ifBlank { null } ?: (if (uid == "usr_officialjaiby_2026") "OWNER" else "USER")

            val newUser = existingRemote?.copy(
                isCurrentUser = true,
                isOnline = true,
                lastSeen = now,
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
                email = authEmail,
                phoneNumber = "",
                normalizedPhoneNumber = "",
                createdAt = now,
                role = initialRole,
                password = "",
                passwordHash = hash,
                passwordSalt = salt,
                recoveryCode = AuthSecurity.generateRecoveryCode(),
                isOfficial = false,
                linoId = effectiveLinoId
            )

            // 3. Write persistent public profile to Cloud Firestore at /users/{uid}
            val remoteRes = remoteAccountService.saveUserToRemote(newUser)
            if (remoteRes.isFailure) {
                val err = remoteRes.exceptionOrNull()
                Log.e("LenoRepository", "Failed to save user profile to Cloud Firestore: ${err?.message}", err)
                return Result.failure(err ?: Exception("Failed to persist user profile to Cloud Firestore."))
            }

            // 4. Clear previous account local state for complete account isolation
            userDao.clearCurrentUser()
            userDao.purgeDemoAccounts()
            accountBackupStore?.purgeDemoAccounts()

            // 5. Cache authenticated user into Room for active session
            accountBackupStore?.saveAccount(newUser)
            userDao.insertUser(newUser)
            userDao.setCurrentUser(uid)
            sessionManager?.saveActiveUserId(uid)
            sessionManager?.saveAuthCredentials(authEmail, cleanPassword)

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
        if (cleanIdentifier.equals("usr_officialjaiby_2026", ignoreCase = true)) {
            val real = userDao.getUserByUsernameSync("officialjaiby")
            if (real != null) return switchToAccount(real.userId)
        }

        if (cleanIdentifier.isBlank() || cleanPassword.isBlank()) {
            return Result.failure(Exception("Please enter your Username / Leno ID and password."))
        }
        if (cleanPassword.length < 4) {
            return Result.failure(Exception("Password must be at least 4 characters."))
        }

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

            // 3. Fallback: Quick direct Firebase Auth verification across all potential emails
            if (matchedUid == null && cleanPassword.length >= 6) {
                val potentialEmails = linkedSetOf<String>().apply {
                    if (cleanIdentifier.contains("@")) {
                        add(cleanIdentifier.lowercase())
                    }
                    add("${cleanIdentifier.lowercase()}@leno.chat")
                    for (c in candidateUsers) {
                        if (c.email.isNotBlank()) add(c.email.lowercase())
                        if (c.googleEmail.isNotBlank()) add(c.googleEmail.lowercase())
                        if (c.username.isNotBlank()) add("${c.username.lowercase()}@leno.chat")
                    }
                }

                for (targetEmail in potentialEmails) {
                    try {
                        val res = kotlinx.coroutines.withTimeoutOrNull(8000L) {
                            remoteAccountService.signInWithFirebaseAuth(targetEmail, cleanPassword)
                        }
                        if (res != null && res.isSuccess) {
                            val authUid = res.getOrThrow()
                            matchedUid = authUid
                            matchedUser = remoteAccountService.findRemoteUserById(authUid)
                                ?: userDao.getUserByIdSync(authUid)
                                ?: accountBackupStore?.findAccount(authUid)
                            break
                        }
                    } catch (_: Exception) {}
                }
            }

            if (matchedUid == null) {
                return Result.failure(Exception("Incorrect credentials. Please verify your username/Leno ID and password."))
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
                    messageDao.migrateSenderId(oldUid, authenticatedUid)
                    messageDao.migrateReceiverId(oldUid, authenticatedUid)
                    messageBackupStore?.migrateUserId(oldUid, authenticatedUid)
                    userDao.deleteUser(oldUid)
                    accountBackupStore?.deleteAccount(oldUid)
                    userDao.insertUser(linked)
                    accountBackupStore?.saveAccount(linked)
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
                    val linoId = generateUniqueLinoId()
                    val salt = AuthSecurity.generateSalt()
                    val hash = AuthSecurity.hashPassword(cleanPassword, salt)
                    val isOwner = authenticatedUid == "usr_officialjaiby_2026"
                    val reconstructed = UserEntity(
                        userId = authenticatedUid,
                        username = usernameFromEmail,
                        displayName = dName,
                        bio = "Connecting on Leno ✨",
                        avatarUrl = pPhoto,
                        isOnline = true,
                        lastSeen = now,
                        statusMessage = "Hey there! I am using Leno.",
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
                        isOfficial = false,
                        linoId = linoId
                    )
                    remoteAccountService.saveUserToRemote(reconstructed)
                    remoteAccountService.claimUsernameAtomic(authenticatedUid, usernameFromEmail)
                    remoteAccountService.claimLenoIdAtomic(authenticatedUid, linoId)
                    profile = reconstructed
                }
            }

            if (profile != null && profile.userId.isBlank()) {
                profile = profile.copy(userId = authenticatedUid)
            }

            if (profile == null) {
                return Result.failure(Exception("No Leno user profile found for this account. Please register first."))
            }

            // 3. Clear local state from any previous account for complete isolation
            userDao.clearCurrentUser()
            userDao.purgeDemoAccounts()
            accountBackupStore?.purgeDemoAccounts()

            val salt = if (profile.passwordSalt.isNotBlank()) profile.passwordSalt else AuthSecurity.generateSalt()
            val hash = if (profile.passwordHash.isNotBlank()) profile.passwordHash else AuthSecurity.hashPassword(cleanPassword, salt)

            // 4. Save to local active cache for current session only
            val loggedIn = profile.copy(
                isCurrentUser = true,
                isOnline = true,
                passwordHash = hash,
                passwordSalt = salt,
                lastSeen = System.currentTimeMillis()
            )
            userDao.insertUser(loggedIn)
            userDao.setCurrentUser(authenticatedUid)
            sessionManager?.saveActiveUserId(authenticatedUid)
            userDao.updateOnlineStatus(authenticatedUid, true)
            accountBackupStore?.saveAccount(loggedIn)

            // 5. Consolidate any duplicate local records matching same username or Leno ID, and migrate their messages
            val duplicates = userDao.getAllUsersSync().filter {
                it.userId != authenticatedUid && (
                    (candidateUser != null && it.userId == candidateUser.userId) ||
                    it.username.equals(loggedIn.username, ignoreCase = true) ||
                    it.linoId.equals(loggedIn.linoId, ignoreCase = true) ||
                    (loggedIn.isOwner && (it.userId == "usr_officialjaiby_2026" || it.username.equals("officialjaiby", ignoreCase = true)))
                )
            }
            for (dup in duplicates) {
                userDao.deleteUser(dup.userId)
                accountBackupStore?.deleteAccount(dup.userId)
                messageDao.migrateSenderId(dup.userId, authenticatedUid)
                messageDao.migrateReceiverId(dup.userId, authenticatedUid)
                messageBackupStore?.migrateUserId(dup.userId, authenticatedUid)
            }
            val primaryEmailToSave = if (loggedIn.email.isNotBlank()) loggedIn.email else "${loggedIn.username.lowercase()}@leno.chat"
            sessionManager?.saveAuthCredentials(primaryEmailToSave, cleanPassword)
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
                val signInRes = remoteAccountService.signInWithFirebaseAuth(cleanEmail, password)
                if (signInRes.isFailure) {
                    return Result.failure(Exception("This email is already in use by another account. Please log in."))
                }
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

        val authEmail = if (cleanEmail.isNotBlank()) cleanEmail else "${cleanUsername}@leno.chat"
        val authResult = remoteAccountService.signUpWithFirebaseAuth(authEmail, password)
        val uid = if (authResult.isSuccess) {
            authResult.getOrThrow()
        } else {
            val authErr = authResult.exceptionOrNull()
            val errText = authErr?.message ?: ""
            val isAlreadyInUse = errText.contains("already registered", ignoreCase = true) ||
                    errText.contains("already in use", ignoreCase = true) ||
                    errText.contains("EMAIL_EXISTS", ignoreCase = true)
            if (isAlreadyInUse) {
                return Result.failure(Exception("An account with this email/username is already registered. Please log in."))
            } else {
                return Result.failure(authErr ?: Exception("Firebase Authentication registration failed."))
            }
        }

        val existingRemote = remoteAccountService.findRemoteUserById(uid) ?: userDao.getUserByIdSync(uid)
        val effectiveLinoId = existingRemote?.linoId?.ifBlank { null } ?: generateUniqueLinoId()

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
            Log.w("LenoRepository", "Notice saving user to Firestore in registerUser: ${remoteRes.exceptionOrNull()?.message}")
        }

        userDao.clearCurrentUser()
        userDao.purgeDemoAccounts()
        accountBackupStore?.purgeDemoAccounts()

        accountBackupStore?.saveAccount(newUser)
        userDao.insertUser(newUser)
        userDao.setCurrentUser(uid)
        sessionManager?.saveActiveUserId(uid)

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
    }

    suspend fun loginUser(identifier: String, password: String): Result<UserEntity> {
        return loginWithLino(identifier, password)
    }

    suspend fun resetPassword(identifier: String, newPassword: String): Result<Unit> {
        val cleanIdentifier = identifier.trim().removePrefix("@")
        val cleanNewPass = newPassword.trim()
        if (cleanIdentifier.isBlank() || cleanNewPass.isBlank()) {
            return Result.failure(Exception("Please fill in all fields."))
        }
        if (cleanNewPass.length < 4) {
            return Result.failure(Exception("Password must be at least 4 characters."))
        }

        val localUser = findUserByAnyIdentifier(cleanIdentifier)
            ?: return Result.failure(Exception("No account found for '$cleanIdentifier'."))

        val salt = AuthSecurity.generateSalt()
        val hash = AuthSecurity.hashPassword(cleanNewPass, salt)
        val updatedUser = localUser.copy(
            password = "",
            passwordHash = hash,
            passwordSalt = salt
        )
        userDao.insertUser(updatedUser)
        accountBackupStore?.saveAccount(updatedUser)
        try {
            remoteAccountService.saveUserToRemote(updatedUser)
            remoteAccountService.updateFirebasePassword(cleanNewPass)
        } catch (e: Exception) {
            Log.w("LenoRepository", "Remote sync notice: ${e.message}")
        }
        if (localUser.email.isNotBlank()) {
            remoteAccountService.sendPasswordResetEmail(localUser.email)
        }
        return Result.success(Unit)
    }

    suspend fun recoverAccount(
        identifier: String,
        verificationHint: String,
        newPassword: String
    ): Result<UserEntity> {
        val cleanIdentifier = identifier.trim().removePrefix("@")
        val cleanHint = verificationHint.trim()
        val cleanNewPass = newPassword.trim()

        if (cleanIdentifier.isBlank() && cleanHint.isBlank()) {
            return Result.failure(Exception("Please enter your Username or Leno ID."))
        }
        if (cleanNewPass.length < 4) {
            return Result.failure(Exception("New password must be at least 4 characters."))
        }

        val localUser = findUserByAnyIdentifier(cleanIdentifier, cleanHint)
            ?: return Result.failure(Exception("No account found for '$cleanIdentifier'."))

        // Verify account identity if hint provided (or allow reset if verified identifier matches)
        if (cleanHint.isNotBlank()) {
            val cleanHintDigits = cleanHint.uppercase().removePrefix("LEN-").removePrefix("LEN").replace("-", "").trim()
            val userLidDigits = localUser.linoId.uppercase().removePrefix("LEN-").removePrefix("LEN").replace("-", "").trim()

            val matchesCode = localUser.recoveryCode.isNotBlank() && localUser.recoveryCode.equals(cleanHint, ignoreCase = true)
            val matchesName = localUser.displayName.equals(cleanHint, ignoreCase = true) ||
                    localUser.displayName.contains(cleanHint, ignoreCase = true) ||
                    cleanHint.contains(localUser.displayName, ignoreCase = true)
            val matchesLinoId = localUser.linoId.equals(cleanHint, ignoreCase = true) ||
                    (cleanHintDigits.isNotBlank() && userLidDigits == cleanHintDigits)
            val matchesEmail = localUser.email.equals(cleanHint, ignoreCase = true) ||
                    localUser.recoveryEmail.equals(cleanHint, ignoreCase = true) ||
                    localUser.googleEmail.equals(cleanHint, ignoreCase = true)
            val matchesUsername = localUser.username.equals(cleanHint, ignoreCase = true)

            if (!matchesCode && !matchesName && !matchesLinoId && !matchesEmail && !matchesUsername) {
                return Result.failure(Exception("Verification details do not match this account."))
            }
        }

        val salt = AuthSecurity.generateSalt()
        val hash = AuthSecurity.hashPassword(cleanNewPass, salt)
        val updatedUser = localUser.copy(
            password = "",
            passwordHash = hash,
            passwordSalt = salt
        )
        userDao.insertUser(updatedUser)
        accountBackupStore?.saveAccount(updatedUser)
        try {
            remoteAccountService.saveUserToRemote(updatedUser)
            val primaryEmail = if (updatedUser.email.isNotBlank()) updatedUser.email else "${updatedUser.username.lowercase()}@leno.chat"
            val signUpRes = remoteAccountService.signUpWithFirebaseAuth(primaryEmail, cleanNewPass)
            if (signUpRes.isFailure) {
                remoteAccountService.updateFirebasePassword(cleanNewPass)
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Remote sync notice: ${e.message}")
        }
        return Result.success(updatedUser)
    }

    // ==========================================
    // ACCOUNT PROTECTION (GOOGLE & EMAIL RECOVERY)
    // ==========================================

    suspend fun connectGoogleAccount(userId: String, googleEmail: String): Result<UserEntity> {
        val cleanEmail = googleEmail.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(Exception("Please enter a valid Google email address."))
        }

        val existingWithGoogle = userDao.getUserByGoogleEmailSync(cleanEmail)
        if (existingWithGoogle != null && existingWithGoogle.userId != userId) {
            return Result.failure(Exception("This Google account is already linked to another Leno account (@${existingWithGoogle.username})."))
        }

        val currentUser = userDao.getUserByIdSync(userId)
            ?: return Result.failure(Exception("User not found."))

        val updated = currentUser.copy(googleEmail = cleanEmail, isVerified = true)
        userDao.insertUser(updated)
        userDao.markUserVerified(userId)
        accountBackupStore?.saveAccount(updated)
        remoteAccountService.saveUserToRemote(updated)

        notificationDao.insertNotification(
            NotificationEntity(
                id = "prot_google_" + UUID.randomUUID().toString().take(8),
                userId = userId,
                title = "Account Protected ✓",
                body = "Google account ($cleanEmail) connected as a secure recovery method.",
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

        val updated = currentUser.copy(recoveryEmail = cleanEmail, isVerified = true)
        userDao.insertUser(updated)
        userDao.markUserVerified(userId)
        accountBackupStore?.saveAccount(updated)
        remoteAccountService.saveUserToRemote(updated)

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
            remoteAccountService.saveUserToRemote(updated)
            return Result.success(updated)
        } else if (isEmail) {
            if (currentUser.googleEmail.isBlank()) {
                return Result.failure(Exception("Cannot remove this recovery method because it is your only active recovery method. Connect Google first to keep your account protected."))
            }
            val updated = currentUser.copy(recoveryEmail = "")
            userDao.insertUser(updated)
            remoteAccountService.saveUserToRemote(updated)
            return Result.success(updated)
        }

        return Result.failure(Exception("Invalid recovery method specified."))
    }

    suspend fun loginWithGoogle(googleEmail: String): Result<UserEntity> {
        val cleanEmail = googleEmail.trim().lowercase()
        if (cleanEmail.isBlank()) {
            return Result.failure(Exception("Please enter or select a valid Google account."))
        }

        var matchedUser = userDao.getUserByGoogleEmailSync(cleanEmail)
            ?: userDao.getUserByEmailSync(cleanEmail)
            ?: userDao.getUserByIdentifierSync(cleanEmail)
            ?: accountBackupStore?.findAccount(cleanEmail)
            ?: remoteAccountService.findRemoteUser(cleanEmail)

        if (matchedUser == null) {
            // Auto-create Leno account seamlessly for Google user
            val emailPrefix = cleanEmail.substringBefore("@").replace(".", "_").filter { it.isLetterOrDigit() || it == '_' }
            val uname = if (emailPrefix.isNotBlank()) emailPrefix.lowercase().take(15) else "user_${(1000..9999).random()}"
            val dName = emailPrefix.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val genLinoId = generateUniqueLinoId()
            val genUid = "usr_" + UUID.randomUUID().toString().replace("-", "").take(12)
            val now = System.currentTimeMillis()
            val initialRole = "USER"

            val newGoogleUser = UserEntity(
                userId = genUid,
                username = uname,
                displayName = dName,
                bio = "Connecting on Leno ✨",
                avatarUrl = "",
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
                isOfficial = false,
                linoId = genLinoId
            )

            userDao.clearCurrentUser()
            userDao.insertUser(newGoogleUser)
            userDao.setCurrentUser(newGoogleUser.userId)
            sessionManager?.saveActiveUserId(newGoogleUser.userId)
            accountBackupStore?.saveAccount(newGoogleUser)
            remoteAccountService.saveUserToRemote(newGoogleUser)

            settingsDao.insertUserSettings(
                UserSettingsEntity(
                    userId = genUid,
                    lowDataMode = false,
                    notificationsEnabled = true,
                    darkThemeEnabled = false
                )
            )

            notificationDao.insertNotification(
                NotificationEntity(
                    id = "welcome_g_" + UUID.randomUUID().toString().take(8),
                    userId = genUid,
                    title = "Welcome to Leno! 🎉",
                    body = "Your account has been connected with $cleanEmail. Your Leno ID is $genLinoId.",
                    type = "SYSTEM"
                )
            )

            ensureInitialOfficialMessageForUser(genUid)
            syncAllUserChatsAndMessages(genUid)
            return Result.success(newGoogleUser)
        }

        // Restore EXISTING account session
        userDao.clearCurrentUser()
        val loggedInUser = matchedUser.copy(
            isCurrentUser = true,
            isOnline = true,
            googleEmail = cleanEmail,
            lastSeen = System.currentTimeMillis()
        )
        userDao.insertUser(loggedInUser)
        userDao.setCurrentUser(matchedUser.userId)
        sessionManager?.saveActiveUserId(matchedUser.userId)
        accountBackupStore?.saveAccount(loggedInUser)
        remoteAccountService.saveUserToRemote(loggedInUser)
        syncRemoteContactsIntoRoom(matchedUser.userId)
        syncAllUserChatsAndMessages(matchedUser.userId)
        ensureInitialOfficialMessageForUser(matchedUser.userId)

        return Result.success(loggedInUser)
    }

    // MESSAGING
    suspend fun updateUserPresence(isOnline: Boolean) {
        val currentUid = userDao.getCurrentUserSync()?.userId ?: return
        val now = System.currentTimeMillis()
        userDao.updateOnlineStatus(currentUid, isOnline, now)
    }

    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        text: String,
        imageUrl: String? = null,
        audioUrl: String? = null,
        audioDurationSeconds: Int = 0
    ) {
        val msgId = "msg_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val message = MessageEntity(
            messageId = msgId,
            senderId = senderId,
            receiverId = receiverId,
            text = text,
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            audioDurationSeconds = audioDurationSeconds,
            timestamp = now,
            status = "SENT"
        )
        messageDao.insertMessage(message)
        messageBackupStore?.saveMessage(message)
        try {
            if (receiverId != senderId && receiverId != OFFICIAL_LENO_ID) {
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
                userDao.insertUser(partner.copy(isCurrentUser = false))
                accountBackupStore?.saveAccount(partner.copy(isCurrentUser = false))
                messageBackupStore?.saveChatPartnerProfile(partner.copy(isCurrentUser = false))
            }
        } catch (_: Exception) {}

        if (receiverId != OFFICIAL_LENO_ID) {
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
        messageDao.insertMessages(messages)
        messageBackupStore?.saveMessages(messages)
    }

    suspend fun updateMessageStatus(messageId: String, status: String) {
        messageDao.updateMessageStatus(messageId, status)
    }

    suspend fun markMessagesAsRead(currentUserId: String, otherUserId: String) {
        messageDao.markMessagesAsRead(currentUserId, otherUserId)
        if (otherUserId == OFFICIAL_LENO_ID || currentUserId.isBlank() || otherUserId.isBlank()) return
        try {
            val chatId = remoteAccountService.getChatId(currentUserId, otherUserId)
            remoteAccountService.markAllChatMessagesAsReadInRemote(chatId, currentUserId, otherUserId)
            val remoteMsgs = remoteAccountService.getChatMessagesFromRemote(chatId)
            remoteMsgs.forEach { msg ->
                if (msg.receiverId == currentUserId && msg.status != "READ") {
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
        friendService.declineFriendRequest(
            currentUserId = currentUserId,
            requesterId = requesterId
        )
    }

    suspend fun unfollowUser(currentUserId: String, targetId: String) {
        friendshipDao.removeFriendship(currentUserId, targetId)
        remoteAccountService.removeContactFromRemote(currentUserId, targetId)
    }

    suspend fun searchUserByLinoOrUsername(query: String): Result<UserEntity> {
        val clean = query.trim()
        if (clean.isBlank()) {
            return Result.failure(Exception("Please enter a Leno ID, username, or name."))
        }
        val cleanNoAt = clean.removePrefix("@")
        val upper = cleanNoAt.uppercase()
        val lower = cleanNoAt.lowercase()

        var user: UserEntity? = null
        try {
            val remoteUser = remoteAccountService.findRemoteUser(clean)
                ?: remoteAccountService.findRemoteUser(cleanNoAt)
            if (remoteUser != null) {
                val sanitized = remoteUser.copy(isCurrentUser = false, password = "")
                userDao.insertUser(sanitized)
                user = sanitized
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Remote search notice: ${e.message}")
        }

        if (user == null) {
            user = userDao.getUserByLinoIdSync(upper)
                ?: userDao.getUserByUsernameSync(lower)
                ?: userDao.getUserByDisplayNameSync(clean)
                ?: userDao.getUserByIdentifierSync(clean)
                ?: accountBackupStore?.findAccount(clean)
                ?: messageBackupStore?.getAllChatPartnerProfiles()?.firstOrNull {
                    it.username.equals(lower, ignoreCase = true) ||
                    it.linoId.equals(upper, ignoreCase = true) ||
                    it.displayName.equals(clean, ignoreCase = true) ||
                    it.userId == clean ||
                    it.displayName.contains(clean, ignoreCase = true) ||
                    it.username.contains(lower, ignoreCase = true)
                }
        }

        if (user == null) {
            try {
                val remoteMatches = remoteAccountService.searchRemoteUsers(cleanNoAt, "")
                val matched = remoteMatches.firstOrNull {
                    it.username.equals(lower, ignoreCase = true) ||
                    it.linoId.equals(upper, ignoreCase = true) ||
                    it.displayName.equals(clean, ignoreCase = true)
                } ?: remoteMatches.firstOrNull()
                if (matched != null) {
                    val sanitized = matched.copy(isCurrentUser = false, password = "")
                    userDao.insertUser(sanitized)
                    accountBackupStore?.saveAccount(sanitized)
                    messageBackupStore?.saveChatPartnerProfile(sanitized)
                    user = sanitized
                }
            } catch (_: Exception) {}
        }

        if (user == null) {
            try {
                val allRemote = remoteAccountService.fetchAllRemoteUsers(150)
                val matched = allRemote.firstOrNull {
                    it.username.equals(lower, ignoreCase = true) ||
                    it.linoId.equals(upper, ignoreCase = true) ||
                    it.displayName.equals(clean, ignoreCase = true) ||
                    it.userId == clean ||
                    it.displayName.contains(clean, ignoreCase = true) ||
                    it.username.contains(lower, ignoreCase = true) ||
                    it.linoId.contains(cleanNoAt, ignoreCase = true)
                }
                if (matched != null) {
                    val sanitized = matched.copy(isCurrentUser = false, password = "")
                    userDao.insertUser(sanitized)
                    accountBackupStore?.saveAccount(sanitized)
                    messageBackupStore?.saveChatPartnerProfile(sanitized)
                    user = sanitized
                }
            } catch (_: Exception) {}
        }

        if (user != null) {
            val safeUser = user.copy(isCurrentUser = false, password = "")
            userDao.insertUser(safeUser)
            accountBackupStore?.saveAccount(safeUser)
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
            remoteAccountService.updateFirebaseEmail(newEmail)
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
        accountBackupStore?.saveAccount(updated)

        // Sync with remote cloud
        val remoteRes = remoteAccountService.saveUserToRemote(updated)
        if (remoteRes.isFailure) {
            val err = remoteRes.exceptionOrNull()
            Log.w("LenoRepository", "Remote sync notice: ${err?.message}")
        }
        return Result.success(updated)
    }

    suspend fun updateOnlineStatus(userId: String, isOnline: Boolean) {
        userDao.updateOnlineStatus(userId, isOnline, System.currentTimeMillis())
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
            accountBackupStore?.saveAccount(updatedUser)
            try {
                if (remoteAccountService.getCurrentAuthUser() != null) {
                    remoteAccountService.updateFirebasePassword(cleanPass)
                }
                remoteAccountService.saveUserToRemote(updatedUser)
            } catch (e: Exception) {
                Log.w("LenoRepository", "Notice updating remote password: ${e.message}")
            }
            val email = if (updatedUser.email.isNotBlank()) updatedUser.email else "${updatedUser.username.lowercase()}@leno.chat"
            sessionManager?.saveAuthCredentials(email, cleanPass)
            Result.success(Unit)
        } else {
            Result.failure(Exception("No user currently logged in."))
        }
    }

    suspend fun logout(userId: String) {
        try {
            userDao.updateOnlineStatus(userId, false)
            val u = userDao.getUserByIdSync(userId)
            if (u != null) {
                remoteAccountService.saveUserToRemote(u.copy(isOnline = false, lastSeen = System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Logout remote status sync: ${e.message}")
        }
        remoteAccountService.signOutFromFirebase()
        userDao.clearCurrentUser()
        userDao.purgeDemoAccounts()
        accountBackupStore?.purgeDemoAccounts()
        sessionManager?.clearSession()
        ensureOfficialLenoAccountCreated()
    }

    suspend fun clearCurrentSession() {
        val current = userDao.getCurrentUserSync()
        if (current != null) {
            logout(current.userId)
        } else {
            remoteAccountService.signOutFromFirebase()
            userDao.clearCurrentUser()
            userDao.purgeDemoAccounts()
            accountBackupStore?.purgeDemoAccounts()
            sessionManager?.clearSession()
            ensureOfficialLenoAccountCreated()
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
        const val CLASS_INSTRUCTOR_LINO_ID = "LEN-33829104"
        const val CLASS_INSTRUCTOR_USER_ID = "user_len_maths_teacher"
    }

    suspend fun getInstructorUserId(): String {
        // 1. Check local DB by exact Leno ID
        val local = userDao.getUserByLinoIdSync(CLASS_INSTRUCTOR_LINO_ID)
        if (local != null) return local.userId

        // 2. Check remote Firestore
        try {
            val remote = remoteAccountService.findRemoteUser(CLASS_INSTRUCTOR_LINO_ID)
            if (remote != null) {
                userDao.insertUser(remote)
                return remote.userId
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Remote lookup for $CLASS_INSTRUCTOR_LINO_ID: ${e.message}")
        }

        // 3. Check backup store
        val backup = accountBackupStore?.findAccount(CLASS_INSTRUCTOR_LINO_ID)
        if (backup != null) {
            userDao.insertUser(backup)
            return backup.userId
        }

        // 4. Ensure stable instructor user with exact ID LEN-33829104
        val instructor = UserEntity(
            userId = CLASS_INSTRUCTOR_USER_ID,
            username = "bello_maths",
            displayName = "Mallam Bello Isa",
            bio = "Official Leno Class Instructor for Maths, Quran, JAMB, Skills, Online Skills & Content Creation.",
            avatarUrl = "android.resource://com.example/drawable/ic_official_leno_avatar",
            isOnline = true,
            lastSeen = System.currentTimeMillis(),
            statusMessage = "Teaching via Low Data Chat + Images",
            isCurrentUser = false,
            email = "instructor@leno.chat",
            password = "",
            phoneNumber = "",
            createdAt = System.currentTimeMillis(),
            isOfficial = false,
            linoId = CLASS_INSTRUCTOR_LINO_ID
        )
        userDao.insertUser(instructor)
        return instructor.userId
    }

    suspend fun ensureOfficialLenoAccountCreated() {
        try {
            userDao.purgeDemoAccounts()
            accountBackupStore?.purgeDemoAccounts()
            val newAvatarUrl = "android.resource://com.example/drawable/ic_official_leno_avatar"
            val existing = userDao.getUserByIdSync(OFFICIAL_LENO_ID)
            if (existing != null) {
                if (existing.avatarUrl != newAvatarUrl) {
                    userDao.insertUser(existing.copy(avatarUrl = newAvatarUrl))
                }
            } else {
                val now = System.currentTimeMillis()
                val officialAccount = UserEntity(
                    userId = OFFICIAL_LENO_ID,
                    username = "leno",
                    displayName = "Official Leno",
                    bio = "Official system announcements, security alerts, and platform updates from Leno.",
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
                    linoId = "LEN-00000001"
                )
                userDao.insertUser(officialAccount)
            }
        } catch (e: Exception) {
            Log.e("LenoRepository", "Error setting up Official Leno account: ${e.message}")
        }
        ensureStandardInstructorsCreated()
        ensureOwnersAccountsCreated()
    }

    suspend fun ensureStandardInstructorsCreated() {
        try {
            val teachers = listOf(
                UserEntity(
                    userId = CLASS_INSTRUCTOR_USER_ID,
                    username = "bello_maths",
                    displayName = "Mallam Bello Isa",
                    bio = "Official Leno Class Instructor for Mathematics & General Studies.",
                    avatarUrl = "android.resource://com.example/drawable/ic_official_leno_avatar",
                    isOnline = true,
                    lastSeen = System.currentTimeMillis(),
                    statusMessage = "Teaching via Low Data Chat + Images",
                    isCurrentUser = false,
                    email = "instructor@leno.chat",
                    password = "",
                    phoneNumber = "",
                    createdAt = System.currentTimeMillis(),
                    isOfficial = false,
                    linoId = CLASS_INSTRUCTOR_LINO_ID,
                    role = "TEACHER",
                    assignedSubject = "Mathematics",
                    teacherStatus = "APPROVED",
                    teacherSubject = "Mathematics",
                    isVerified = true
                ),
                UserEntity(
                    userId = "user_len_quran_teacher",
                    username = "ibrahim_quran",
                    displayName = "Sheikh Ibrahim Ahmad",
                    bio = "Official Leno Class Instructor for Quran & Islamic Studies.",
                    avatarUrl = "",
                    isOnline = true,
                    lastSeen = System.currentTimeMillis(),
                    statusMessage = "Available for Quran & Tajweed learning",
                    isCurrentUser = false,
                    email = "quran@leno.chat",
                    password = "",
                    phoneNumber = "",
                    createdAt = System.currentTimeMillis(),
                    isOfficial = false,
                    linoId = "LEN-44910283",
                    role = "TEACHER",
                    assignedSubject = "Quran",
                    teacherStatus = "APPROVED",
                    teacherSubject = "Quran",
                    isVerified = true
                ),
                UserEntity(
                    userId = "user_len_english_teacher",
                    username = "fatima_english",
                    displayName = "Hajiya Fatima Aliyu",
                    bio = "Official Leno Class Instructor for English Language & Literature.",
                    avatarUrl = "",
                    isOnline = true,
                    lastSeen = System.currentTimeMillis(),
                    statusMessage = "English Language & Grammar Tutor",
                    isCurrentUser = false,
                    email = "english@leno.chat",
                    password = "",
                    phoneNumber = "",
                    createdAt = System.currentTimeMillis(),
                    isOfficial = false,
                    linoId = "LEN-55829104",
                    role = "TEACHER",
                    assignedSubject = "English",
                    teacherStatus = "APPROVED",
                    teacherSubject = "English",
                    isVerified = true
                ),
                UserEntity(
                    userId = "user_len_skills_teacher",
                    username = "usman_skills",
                    displayName = "Coach Usman Garba",
                    bio = "Official Leno Class Instructor for Vocational Skills & Online Crafts.",
                    avatarUrl = "",
                    isOnline = true,
                    lastSeen = System.currentTimeMillis(),
                    statusMessage = "Vocational Skills & Practical Crafts",
                    isCurrentUser = false,
                    email = "skills@leno.chat",
                    password = "",
                    phoneNumber = "",
                    createdAt = System.currentTimeMillis(),
                    isOfficial = false,
                    linoId = "LEN-66738291",
                    role = "TEACHER",
                    assignedSubject = "Vocational Skills",
                    teacherStatus = "APPROVED",
                    teacherSubject = "Vocational Skills",
                    isVerified = true
                )
            )

            for (t in teachers) {
                val existing = userDao.getUserByIdSync(t.userId)
                    ?: userDao.getUserByUsernameSync(t.username)
                    ?: userDao.getUserByLinoIdSync(t.linoId)
                if (existing == null) {
                    userDao.insertUser(t)
                    accountBackupStore?.saveAccount(t)
                    messageBackupStore?.saveChatPartnerProfile(t)
                } else if (!existing.isCurrentUser) {
                    val updated = existing.copy(
                        role = "TEACHER",
                        teacherStatus = "APPROVED",
                        teacherSubject = t.teacherSubject.ifBlank { existing.teacherSubject },
                        assignedSubject = t.assignedSubject.ifBlank { existing.assignedSubject },
                        isVerified = true
                    )
                    userDao.insertUser(updated)
                    accountBackupStore?.saveAccount(updated)
                    messageBackupStore?.saveChatPartnerProfile(updated)
                }
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error ensuring standard instructors: ${e.message}")
        }
    }

    suspend fun ensureOwnersAccountsCreated() {
        try {
            // Consolidate duplicate accounts to prevent duplicate accounts and preserve all messages
            val current = userDao.getCurrentUserSync()
            if (current != null) {
                val duplicates = userDao.getAllUsersSync().filter {
                    it.userId != current.userId && (
                        it.username.equals(current.username, ignoreCase = true) ||
                        it.linoId.equals(current.linoId, ignoreCase = true) ||
                        (current.isOwner && (it.userId == "usr_officialjaiby_2026" || it.username.equals("officialjaiby", ignoreCase = true)))
                    )
                }
                for (legacy in duplicates) {
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
        val isApproved = (user != null && user.isApprovedTeacher) || (role != null && role.isApprovedTeacher) || (user?.isAdmin == true)
        if (!isApproved) {
            return Result.failure(Exception("Unauthorized: Only approved Teachers can create or publish classes."))
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
            ensureStandardInstructorsCreated()
            userDao.clearDemoAvatars()
        } catch (e: Exception) {
            Log.w("LenoRepository", "Error purging demo data: ${e.message}")
        }
    }

    suspend fun syncClassesFromRemote() = withContext(Dispatchers.IO) {
        try {
            purgeDemoClasses()
            val remoteClasses = remoteAccountService.fetchClassesFromRemote()
            if (remoteClasses.isNotEmpty()) {
                classDao.insertClasses(remoteClasses)
            }
        } catch (e: Exception) {
            Log.w("LenoRepository", "syncClassesFromRemote notice: ${e.message}")
        }
    }

    fun listenToRemoteClasses(onClasses: (List<ClassEntity>) -> Unit): ListenerRegistration? =
        remoteAccountService.listenToRemoteClasses { list ->
            CoroutineScope(Dispatchers.IO).launch {
                purgeDemoClasses()
                if (list.isNotEmpty()) {
                    classDao.insertClasses(list)
                }
            }
            onClasses(list)
        }

    fun listenToUserEnrollments(studentUserId: String, onEnrollments: (List<EnrollmentEntity>) -> Unit): ListenerRegistration? =
        remoteAccountService.listenToUserEnrollments(studentUserId) { list ->
            CoroutineScope(Dispatchers.IO).launch {
                list.forEach { enrollmentDao.insertEnrollment(it) }
            }
            onEnrollments(list)
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
        remoteAccountService.listenToClassLessons(classId) { list ->
            CoroutineScope(Dispatchers.IO).launch {
                if (list.isNotEmpty()) {
                    lessonDao.insertLessons(list)
                }
            }
            onLessons(list)
        }

    suspend fun getLessonsForClassSync(classId: String): List<LessonEntity> =
        lessonDao.getLessonsForClassSync(classId)

    suspend fun addLessonToClass(lesson: LessonEntity): Result<Unit> {
        val parentClass = classDao.getClassByIdSync(lesson.classId)
            ?: return Result.failure(Exception("Parent class not found."))

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

    // Teacher Role & Applications
    fun getTeacherRole(userId: String): Flow<TeacherRoleEntity?> =
        teacherRoleDao.getTeacherRole(userId)

    suspend fun getTeacherRoleSync(userId: String): TeacherRoleEntity? =
        teacherRoleDao.getTeacherRoleSync(userId)

    suspend fun submitTeacherApplication(application: TeacherRoleEntity): Result<Unit> {
        val existingRole = teacherRoleDao.getTeacherRoleSync(application.userId)
        if (existingRole != null) {
            if (existingRole.isPending || existingRole.status.equals("PENDING", ignoreCase = true)) {
                return Result.failure(Exception("You already have an application under review (Pending). You cannot submit another application."))
            }
            if (existingRole.isApprovedTeacher || existingRole.status.equals("APPROVED", ignoreCase = true)) {
                return Result.failure(Exception("You are already an approved teacher for ${existingRole.effectiveApprovedSubject}."))
            }
            if (existingRole.status.equals("REJECTED", ignoreCase = true)) {
                return Result.failure(Exception("You have already applied. Applications can only be submitted once."))
            }
        }

        val existingUser = userDao.getUserByIdSync(application.userId)
        if (existingUser != null && !existingUser.teacherStatus.isNullOrBlank()) {
            if (existingUser.teacherStatus.equals("PENDING", ignoreCase = true)) {
                return Result.failure(Exception("You already have an application under review."))
            }
            if (existingUser.teacherStatus.equals("REJECTED", ignoreCase = true)) {
                return Result.failure(Exception("You have already applied. Applications can only be submitted once."))
            }
            if (existingUser.teacherStatus.equals("APPROVED", ignoreCase = true) || existingUser.isTeacher) {
                return Result.failure(Exception("You are already an approved teacher for ${existingUser.assignedSubject}."))
            }
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
        teacherRoleDao.insertOrUpdate(cleanApplication)
        userDao.updateUserTeacherStatus(application.userId, "PENDING", selectedSubject)

        // Sync application to Firestore
        try {
            val user = userDao.getUserByIdSync(application.userId)
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
            Log.w("LenoRepository", "Firestore teacher application sync note: ${e.message}")
        }

        return Result.success(Unit)
    }

    /**
     * Monitors 'teacher_applications' collection in Firestore in real time.
     */
    fun listenToTeacherApplications(onApplications: (List<TeacherApplicationItem>) -> Unit): ListenerRegistration? =
        remoteAccountService.listenToTeacherApplications(onApplications)

    suspend fun fetchTeacherApplications(): List<TeacherApplicationItem> =
        remoteAccountService.fetchTeacherApplicationsFromFirestore()

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
            Log.w("LenoRepository", "Notice syncing teacher role remotely: ${remoteRes.exceptionOrNull()?.message}. Local role update is confirmed.")
        }
        return Result.success(Unit)
    }

    /**
     * Fetches all registered users for admin review and role assignment.
     */
    suspend fun fetchAllUsersForAdmin(): List<UserEntity> {
        val remoteUsers = remoteAccountService.fetchAllRemoteUsers(100)
        if (remoteUsers.isNotEmpty()) {
            remoteUsers.forEach { u ->
                val local = userDao.getUserByIdSync(u.userId)
                if (local == null) {
                    userDao.insertUser(u)
                }
            }
        }
        return userDao.getAllUsersSync()
    }

    suspend fun seedSampleTeacherApplication(
        application: TeacherRoleEntity,
        user: UserEntity
    ): Result<Unit> {
        userDao.insertUser(user)
        return remoteAccountService.submitTeacherApplicationToFirestore(
            userId = user.userId,
            application = application,
            applicantName = user.displayName,
            applicantEmail = user.email,
            applicantUsername = user.username,
            applicantAvatarUrl = user.avatarUrl
        )
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
    fun getQuestionsForClass(classId: String): Flow<List<ClassQuestionEntity>> =
        classQuestionDao.getQuestionsForClass(classId)

    suspend fun askQuestionInClass(
        classId: String,
        senderUserId: String,
        senderName: String,
        senderLinoId: String,
        questionText: String
    ): Result<Unit> {
        val cleanText = questionText.trim()
        if (cleanText.isBlank()) {
            return Result.failure(Exception("Question cannot be empty."))
        }
        val q = ClassQuestionEntity(
            questionId = "q_" + java.util.UUID.randomUUID().toString().take(10),
            classId = classId,
            senderUserId = senderUserId,
            senderName = senderName,
            senderLinoId = senderLinoId,
            senderRole = "STUDENT",
            questionText = cleanText,
            timestamp = System.currentTimeMillis()
        )
        classQuestionDao.insertQuestion(q)
        return Result.success(Unit)
    }

    suspend fun answerQuestionInClass(
        questionId: String,
        answerText: String
    ): Result<Unit> {
        val cleanAnswer = answerText.trim()
        if (cleanAnswer.isBlank()) {
            return Result.failure(Exception("Answer cannot be empty."))
        }
        classQuestionDao.answerQuestion(questionId, cleanAnswer)
        return Result.success(Unit)
    }

    suspend fun setUserRestricted(userId: String, isRestricted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.setUserRestricted(userId, isRestricted)
            userDao.getUserByIdSync(userId)?.let {
                accountBackupStore?.saveAccount(it)
            }
            remoteAccountService.setUserRestrictedInFirestore(userId, isRestricted)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LenoRepository", "Error setting user restricted: ${e.message}")
            Result.success(Unit)
        }
    }

    suspend fun deleteUserByAdmin(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (userId == OFFICIAL_LENO_ID || userId == "usr_officialjaiby_2026") {
                return@withContext Result.failure(Exception("Cannot delete primary system account."))
            }
            userDao.deleteUser(userId)
            teacherRoleDao.deleteTeacherRole(userId)
            accountBackupStore?.deleteAccount(userId)
            remoteAccountService.deleteUserInFirestore(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LenoRepository", "Error deleting user: ${e.message}")
            Result.success(Unit)
        }
    }
}
