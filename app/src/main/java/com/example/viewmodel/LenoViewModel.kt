package com.example.viewmodel

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.LenoRepository
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
import com.example.data.service.RemoteAccountService
import com.example.util.CallAudioHelper
import com.example.util.ThemeMode
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed class CallState {
    object Idle : CallState()
    data class Outgoing(
        val partner: UserEntity,
        val statusText: String = "Calling...",
        val isRinging: Boolean = false,
        val canSimulateAnswer: Boolean = false
    ) : CallState()
    data class Incoming(val caller: UserEntity) : CallState()
    data class Connected(
        val partner: UserEntity,
        val durationSeconds: Int = 0,
        val isMuted: Boolean = false,
        val isSpeakerOn: Boolean = false,
        val isIncoming: Boolean = false
    ) : CallState()
}

class LenoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val repository = LenoRepository(database, application)
    private val callAudioHelper = CallAudioHelper(application)

    val currentUser: StateFlow<UserEntity?> = repository.currentUser.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val alternativeAccounts: StateFlow<List<UserEntity>> = currentUser
        .flatMapLatest { user ->
            repository.getAlternativeAccountsForUser(user)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _loggedOutEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOutEvent: SharedFlow<Unit> = _loggedOutEvent.asSharedFlow()

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _themeMode = MutableStateFlow<ThemeMode>(
        repository.sessionManager?.getThemeMode() ?: ThemeMode.LIGHT
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private var callTimerJob: Job? = null
    private var callConnectJob: Job? = null
    private var activeCallListener: ListenerRegistration? = null
    private var incomingCallsListener: ListenerRegistration? = null
    private var incomingMessagesListener: ListenerRegistration? = null
    private var sentReceiptsListener: ListenerRegistration? = null
    private var userChatsListener: ListenerRegistration? = null
    private var activeChatMessagesListener: ListenerRegistration? = null
    private var currentCloudCallId: String? = null
    private var authStateListener: com.google.firebase.auth.FirebaseAuth.AuthStateListener? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val userCalls: StateFlow<List<CallEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getCallsForUser(user.userId)
            else flowOf(emptyList())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allClasses: StateFlow<List<ClassEntity>> = repository.allClasses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val publishedClasses: StateFlow<List<ClassEntity>> = repository.publishedClasses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUserTeacherRole: StateFlow<TeacherRoleEntity?> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getTeacherRole(user.userId)
            else flowOf(null)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val pendingTeacherApplications: StateFlow<List<TeacherRoleEntity>> = repository
        .getPendingTeacherApplications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getTeacherRole(userId: String): Flow<TeacherRoleEntity?> = repository.getTeacherRole(userId)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUserEnrolledClasses: StateFlow<List<ClassEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getEnrolledClassesForStudent(user.userId)
            else flowOf(emptyList())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUserCreatedClasses: StateFlow<List<ClassEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getClassesByInstructor(user.userId)
            else flowOf(emptyList())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isFetchingUserData = MutableStateFlow(true)
    val isFetchingUserData: StateFlow<Boolean> = _isFetchingUserData.asStateFlow()

    private val _isFetchingChatMessages = MutableStateFlow(false)
    val isFetchingChatMessages: StateFlow<Boolean> = _isFetchingChatMessages.asStateFlow()

    // Real-Time 'teacher_applications' collection monitoring for Admins & Background Sync
    private val _teacherApplications = MutableStateFlow<List<TeacherApplicationItem>>(emptyList())
    val teacherApplications: StateFlow<List<TeacherApplicationItem>> = _teacherApplications.asStateFlow()

    private val _isMonitoringApplications = MutableStateFlow(false)
    val isMonitoringApplications: StateFlow<Boolean> = _isMonitoringApplications.asStateFlow()

    private var teacherApplicationsListener: ListenerRegistration? = null
    private var userDocumentListener: ListenerRegistration? = null
    private var classesListener: ListenerRegistration? = null
    private var enrollmentsListener: ListenerRegistration? = null

    // Admin directory of all users for role management
    private val _adminDirectoryUsers = MutableStateFlow<List<UserEntity>>(emptyList())
    val adminDirectoryUsers: StateFlow<List<UserEntity>> = _adminDirectoryUsers.asStateFlow()

    init {
        repository.activeChatPartnerIdSupplier = { activeChatPartnerId.value }
        authStateListener = repository.registerAuthStateListener { authUid ->
            viewModelScope.launch {
                if (authUid != null) {
                    if (currentUser.value == null || currentUser.value?.userId != authUid) {
                        repository.syncCurrentUserFromFirebase()
                    }
                }
            }
        }
        viewModelScope.launch {
            _isFetchingUserData.value = true
            repository.ensureOfficialLenoAccountCreated()
            repository.syncCurrentUserFromFirebase()
            delay(600)
            _isFetchingUserData.value = false
        }
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    repository.ensureInitialOfficialMessageForUser(user.userId)
                    // Start real-time Firestore incoming call listener
                    incomingCallsListener?.remove()
                    incomingCallsListener = repository.remoteAccountService.listenIncomingCalls(user.userId) { offer ->
                        if (_callState.value is CallState.Idle) {
                            currentCloudCallId = offer.callId
                            val caller = UserEntity(
                                userId = offer.callerId,
                                username = offer.callerUsername,
                                displayName = offer.callerName,
                                avatarUrl = offer.callerAvatar,
                                isOnline = true
                            )
                            receiveIncomingCall(caller)
                            // Acknowledge receipt to caller
                            viewModelScope.launch {
                                repository.remoteAccountService.updateCallStatus(offer.callId, "RINGING")
                            }
                        }
                    }

                    // Start real-time Firestore incoming messages listener
                    incomingMessagesListener?.remove()
                    incomingMessagesListener = repository.remoteAccountService.listenToIncomingMessages(user.userId) { msg ->
                        viewModelScope.launch {
                            val isChatActive = activeChatPartnerId.value == msg.senderId
                            repository.handleIncomingMessage(msg, isChatActive)
                        }
                    }

                    // Start real-time Firestore sent receipts listener
                    sentReceiptsListener?.remove()
                    sentReceiptsListener = repository.remoteAccountService.listenToSentReceipts(user.userId) { msgId, status ->
                        viewModelScope.launch {
                            repository.handleSentReceipt(msgId, status, user.userId)
                        }
                    }

                    // Start real-time Firestore user chats listener
                    userChatsListener?.remove()
                    userChatsListener = repository.remoteAccountService.listenToUserChats(user.userId) { _, otherUserId ->
                        viewModelScope.launch {
                            repository.syncRemoteChatMessages(otherUserId)
                        }
                    }

                    // Start real-time Firestore user document listener so permissions and role update immediately without restart
                    userDocumentListener?.remove()
                    userDocumentListener = repository.listenToUserDocument(user.userId) { updatedRemoteUser ->
                        viewModelScope.launch {
                            repository.handleRemoteUserDocumentUpdate(updatedRemoteUser)
                        }
                    }

                    // Start real-time Firestore classes listener
                    classesListener?.remove()
                    classesListener = repository.listenToRemoteClasses { }

                    // Start real-time Firestore enrollments listener
                    enrollmentsListener?.remove()
                    enrollmentsListener = repository.listenToUserEnrollments(user.userId) { }

                    // Initial background sync for all historical user chats, contacts, classes, and enrollments
                    viewModelScope.launch {
                        repository.syncAllUserChatsAndMessages(user.userId)
                        repository.syncRemoteContactsIntoRoom(user.userId)
                        repository.syncClassesFromRemote()
                        repository.syncEnrollmentsFromRemote(user.userId)
                    }

                    if (user.isAdmin || user.isOwner) {
                        startTeacherApplicationsMonitor()
                    }
                } else {
                    classesListener?.remove()
                    classesListener = null
                    enrollmentsListener?.remove()
                    enrollmentsListener = null
                    viewModelScope.launch {
                        repository.purgeDemoClasses()
                    }
                    teacherApplicationsListener?.remove()
                    teacherApplicationsListener = null
                    _isMonitoringApplications.value = false
                    userDocumentListener?.remove()
                    userDocumentListener = null
                    incomingCallsListener?.remove()
                    incomingCallsListener = null
                    incomingMessagesListener?.remove()
                    incomingMessagesListener = null
                    sentReceiptsListener?.remove()
                    sentReceiptsListener = null
                    userChatsListener?.remove()
                    userChatsListener = null
                    activeChatMessagesListener?.remove()
                    activeChatMessagesListener = null
                }
            }
        }
    }

    val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<UserEntity>> = combine(currentUser, searchQuery) { user, query ->
        Pair(user?.userId, query.trim())
    }.flatMapLatest { (myId, query) ->
        if (query.isBlank()) {
            flowOf(emptyList())
        } else {
            repository.searchUsers(query, myId ?: "")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val contactsList: StateFlow<List<UserEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getContactsForUser(user.userId)
            else flowOf(emptyList())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val chatPartners: StateFlow<List<UserEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getChatPartnersForUser(user.userId)
            else flowOf(emptyList())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun syncChatsAndMessages() {
        val uid = currentUser.value?.userId ?: return
        viewModelScope.launch {
            try {
                repository.syncAllUserChatsAndMessages(uid)
            } catch (e: Exception) {
                Log.w("LenoViewModel", "Error syncing chats and messages: ${e.message}")
            }
        }
    }

    val activeChatPartnerId = MutableStateFlow<String?>(null)

    private val _typingStatusMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingStatusMap: StateFlow<Map<String, Boolean>> = _typingStatusMap.asStateFlow()

    fun setUserTyping(partnerId: String, isTyping: Boolean) {
        val current = _typingStatusMap.value.toMutableMap()
        if (isTyping) {
            current[partnerId] = true
        } else {
            current.remove(partnerId)
        }
        _typingStatusMap.value = current
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatPartner: StateFlow<UserEntity?> = activeChatPartnerId
        .flatMapLatest { partnerId ->
            if (partnerId != null) repository.getUserById(partnerId)
            else flowOf(null)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatMessages: StateFlow<List<MessageEntity>> = combine(currentUser, activeChatPartnerId) { user, partnerId ->
        Pair(user?.userId, partnerId)
    }.flatMapLatest { (myId, partnerId) ->
        if (myId != null && partnerId != null) {
            repository.getMessagesBetween(myId, partnerId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatFriendship: StateFlow<FriendshipEntity?> = combine(currentUser, activeChatPartnerId) { user, partnerId ->
        Pair(user?.userId, partnerId)
    }.flatMapLatest { (myId, partnerId) ->
        if (myId != null && partnerId != null) {
            repository.getFriendshipBetween(myId, partnerId)
        } else {
            flowOf(null)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val userSettings: StateFlow<UserSettingsEntity?> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getUserSettings(user.userId)
            else flowOf(null)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val blockedUsers: StateFlow<List<BlockReportEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getBlockedUsers(user.userId)
            else flowOf(emptyList())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val userNotifications: StateFlow<List<NotificationEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getNotificationsForUser(user.userId)
            else flowOf(emptyList())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingRequests: StateFlow<List<FriendshipEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getPendingRequestsForUser(user.userId)
            else flowOf(emptyList())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingRequestUsers: StateFlow<List<UserEntity>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getPendingRequestUsers(user.userId)
            else flowOf(emptyList())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadNotificationCount: StateFlow<Int> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getUnreadNotificationCount(user.userId)
            else flowOf(0)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _notification = MutableStateFlow<String?>(null)
    val notification: StateFlow<String?> = _notification.asStateFlow()

    fun showNotification(message: String) {
        _notification.value = message
    }

    fun clearNotification() {
        _notification.value = null
    }

    fun markChatRead(partnerId: String) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.markMessagesAsRead(myId, partnerId)
        }
    }

    fun openChat(partnerId: String) {
        activeChatPartnerId.value = partnerId
        _isFetchingChatMessages.value = false
        activeChatMessagesListener?.remove()
        val myId = currentUser.value?.userId ?: repository.getCurrentUserIdSync()

        viewModelScope.launch {
            repository.fetchAndSyncUserProfile(partnerId)
        }

        if (myId != null && partnerId != LenoRepository.OFFICIAL_LENO_ID) {
            val chatId = repository.remoteAccountService.getChatId(myId, partnerId)
            activeChatMessagesListener = repository.remoteAccountService.listenToChatMessages(chatId) { remoteMsgs ->
                viewModelScope.launch {
                    if (remoteMsgs.isNotEmpty()) {
                        repository.insertMessages(remoteMsgs)
                        remoteMsgs.forEach { msg ->
                            if (msg.receiverId == myId && msg.status != "READ") {
                                repository.updateMessageStatus(msg.messageId, "READ")
                                repository.remoteAccountService.markMessageReadInRemote(chatId, msg.messageId, msg.senderId)
                            }
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            try {
                if (myId != null) {
                    repository.markMessagesAsRead(myId, partnerId)
                    kotlinx.coroutines.withTimeoutOrNull(2500L) {
                        repository.syncRemoteChatMessages(partnerId)
                    }
                }
            } catch (e: Exception) {
                Log.w("LenoViewModel", "Notice syncing chat: ${e.message}")
            } finally {
                _isFetchingChatMessages.value = false
            }
        }
    }

    fun refreshUserData() {
        viewModelScope.launch {
            _isFetchingUserData.value = true
            repository.syncCurrentUserFromFirebase()
            val myId = currentUser.value?.userId
            if (myId != null) {
                repository.syncAllUserChatsAndMessages(myId)
                repository.syncRemoteContactsIntoRoom(myId)
            }
            delay(600)
            _isFetchingUserData.value = false
        }
    }

    fun refreshChatMessages() {
        val partnerId = activeChatPartnerId.value ?: return
        viewModelScope.launch {
            _isFetchingChatMessages.value = true
            try {
                kotlinx.coroutines.withTimeoutOrNull(2000L) {
                    repository.syncRemoteChatMessages(partnerId)
                }
            } catch (e: Exception) {
                Log.w("LenoViewModel", "Error refreshing chat messages: ${e.message}")
            } finally {
                _isFetchingChatMessages.value = false
            }
        }
    }

    fun closeChat() {
        activeChatPartnerId.value = null
        activeChatMessagesListener?.remove()
        activeChatMessagesListener = null
    }

    fun setUserPresence(isOnline: Boolean) {
        viewModelScope.launch {
            repository.updateUserPresence(isOnline)
        }
    }

    fun retrySendMessage(messageId: String) {
        viewModelScope.launch {
            repository.retrySendMessage(messageId)
        }
    }

    fun sendMessage(
        receiverId: String,
        text: String,
        imageUrl: String? = null,
        audioUrl: String? = null,
        audioDurationSeconds: Int = 0
    ) {
        val me = currentUser.value
        val myId = me?.userId ?: repository.getCurrentUserIdSync() ?: return
        if (me?.isRestricted == true) {
            showNotification("Your account has been restricted by administration.")
            return
        }
        if (text.isBlank() && imageUrl.isNullOrBlank() && audioUrl.isNullOrBlank()) return

        // Clear current user's typing status when message is sent
        setUserTyping(myId, false)

        viewModelScope.launch {
            repository.sendMessage(
                senderId = myId,
                receiverId = receiverId,
                text = text.trim(),
                imageUrl = imageUrl,
                audioUrl = audioUrl,
                audioDurationSeconds = audioDurationSeconds
            )
        }
    }

    suspend fun uploadChatImageFile(file: File, chatId: String = ""): String? {
        val result = repository.uploadChatImageFile(file, chatId)
        return result.getOrNull()
    }

    fun sendFriendRequest(targetId: String, onComplete: () -> Unit = {}) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.sendFriendRequest(myId, targetId)
            showNotification("Connection request sent!")
            onComplete()
        }
    }

    fun acceptFriendRequest(requesterId: String, onComplete: () -> Unit = {}) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.acceptFriendRequest(myId, requesterId)
            showNotification("Connection request accepted! Added to contacts.")
            onComplete()
        }
    }

    fun declineFriendRequest(requesterId: String, onComplete: () -> Unit = {}) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.declineFriendRequest(myId, requesterId)
            showNotification("Connection request declined.")
            onComplete()
        }
    }

    fun unfollowUser(targetId: String, onComplete: () -> Unit = {}) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.unfollowUser(myId, targetId)
            showNotification("Contact removed.")
            onComplete()
        }
    }

    fun checkUsernameAvailable(username: String, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val res = repository.checkUsernameAvailability(username)
            onResult(res)
        }
    }

    fun registerWithLino(
        fullName: String,
        username: String,
        password: String,
        onResult: (Result<UserEntity>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val res = repository.registerWithLino(
                    fullName = fullName,
                    username = username,
                    password = password
                )
                onResult(res)
                if (res.isSuccess) {
                    val u = res.getOrNull()
                    showNotification("Welcome to Leno, ${u?.displayName ?: fullName}!")
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun switchToAccount(targetUserId: String, onResult: (Result<UserEntity>) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val res = repository.switchToAccount(targetUserId)
                if (res.isSuccess) {
                    val u = res.getOrNull()
                    showNotification("Switched to @${u?.username ?: "user"} (${u?.displayName ?: ""})")
                }
                onResult(res)
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun loginWithLino(identifier: String, password: String, targetUserId: String? = null, onResult: (Result<UserEntity>) -> Unit) {
        viewModelScope.launch {
            try {
                val res = repository.loginWithLino(identifier, password, targetUserId)
                onResult(res)
                if (res.isSuccess) {
                    val u = res.getOrNull()
                    showNotification("Welcome back, ${u?.displayName ?: "User"}!")
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun searchUser(query: String, onResult: (Result<UserEntity>) -> Unit) {
        viewModelScope.launch {
            val res = repository.searchUserByLinoOrUsername(query)
            onResult(res)
        }
    }

    fun searchUserByPhone(phoneNumber: String, onResult: (Result<UserEntity>) -> Unit) {
        viewModelScope.launch {
            val result = repository.searchUserByPhone(phoneNumber)
            onResult(result)
        }
    }

    fun addNewContact(
        displayName: String,
        phoneNumber: String,
        username: String = "",
        email: String = "",
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            val result = repository.addNewContact(myId, displayName, phoneNumber, username, email)
            result.onSuccess { contact ->
                showNotification("Added ${contact.displayName} to contacts!")
                onSuccess(contact.userId)
            }.onFailure { err ->
                val errorMsg = err.message ?: "Failed to add contact"
                showNotification(errorMsg)
                onError(errorMsg)
            }
        }
    }

    fun markNotificationAsRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.markAllNotificationsAsRead(myId)
            showNotification("All notifications marked as read")
        }
    }

    fun blockUser(targetId: String, reason: String? = null) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.blockUser(myId, targetId, reason)
            showNotification("User blocked")
        }
    }

    fun unblockUser(targetId: String) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.unblockUser(myId, targetId)
            showNotification("User unblocked")
        }
    }

    fun reportUser(targetId: String, reason: String) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.reportUser(myId, targetId, reason)
            showNotification("User reported. Thank you for keeping Leno safe!")
        }
    }

    fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        avatarUrl: String,
        statusMessage: String,
        phoneNumber: String = "",
        onComplete: (Result<UserEntity>) -> Unit = {}
    ) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            val result = repository.updateProfile(myId, displayName, username, bio, avatarUrl, statusMessage, phoneNumber)
            result.onSuccess {
                showNotification("Profile updated successfully")
                onComplete(result)
            }.onFailure { err ->
                val errorMsg = err.localizedMessage ?: "Failed to update profile"
                showNotification(errorMsg)
                onComplete(result)
            }
        }
    }

    fun fetchAndSyncUserProfile(userId: String) {
        viewModelScope.launch {
            repository.fetchAndSyncUserProfile(userId)
        }
    }

    fun toggleOnlineStatus(isOnline: Boolean) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.updateOnlineStatus(myId, isOnline)
        }
    }

    fun updateUserSettings(lowDataMode: Boolean, notificationsEnabled: Boolean, darkThemeEnabled: Boolean, muteOfficialLeno: Boolean = false) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.updateUserSettings(myId, lowDataMode, notificationsEnabled, darkThemeEnabled, muteOfficialLeno)
            val newMode = if (darkThemeEnabled) ThemeMode.DARK else ThemeMode.LIGHT
            repository.sessionManager?.setThemeMode(newMode)
            _themeMode.value = newMode
            showNotification("Settings updated")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        repository.sessionManager?.setThemeMode(mode)
        _themeMode.value = mode
        val myId = currentUser.value?.userId
        if (myId != null) {
            viewModelScope.launch {
                val current = userSettings.value
                val isDark = when (mode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> false
                }
                repository.updateUserSettings(
                    userId = myId,
                    lowDataMode = current?.lowDataMode ?: false,
                    notificationsEnabled = current?.notificationsEnabled ?: true,
                    darkThemeEnabled = isDark,
                    muteOfficialLeno = current?.muteOfficialLeno ?: false
                )
            }
        }
    }

    fun changePassword(newPass: String, onComplete: (Result<Unit>) -> Unit) {
        val myId = currentUser.value?.userId ?: return
        viewModelScope.launch {
            val res = repository.changePassword(myId, newPass)
            onComplete(res)
            if (res.isSuccess) showNotification("Password updated successfully!")
        }
    }

    fun registerUser(
        displayName: String,
        username: String,
        email: String,
        password: String,
        phoneNumber: String = "",
        avatarUrl: String,
        bio: String,
        onResult: (Result<UserEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.registerUser(
                displayName = displayName,
                username = username,
                email = email,
                password = password,
                phoneNumber = phoneNumber,
                avatarUrl = avatarUrl,
                bio = bio
            )
            onResult(res)
            if (res.isSuccess) showNotification("Welcome to Leno, ${displayName.ifBlank { username }}!")
        }
    }

    fun loginUser(identifier: String, password: String, onResult: (Result<UserEntity>) -> Unit) {
        viewModelScope.launch {
            val res = repository.loginUser(identifier, password)
            onResult(res)
            if (res.isSuccess) showNotification("Welcome back!")
        }
    }

    fun recoverAccount(
        identifier: String,
        verificationHint: String,
        newPassword: String,
        onResult: (Result<UserEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.recoverAccount(identifier, verificationHint, newPassword)
            onResult(res)
            if (res.isSuccess) {
                showNotification("Account recovered successfully! Password updated.")
            }
        }
    }

    fun resetPassword(identifier: String, newPassword: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.resetPassword(identifier, newPassword)
            onResult(res)
            if (res.isSuccess) showNotification("Password reset successful! Please log in.")
        }
    }

    fun sendOfficialAnnouncement(
        title: String,
        body: String,
        category: String,
        targetUserIds: List<String>,
        onResult: (Result<Int>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.sendOfficialAnnouncement(title, body, category, targetUserIds)
            onResult(result)
            result.onSuccess { count ->
                showNotification("Official announcement sent to $count users!")
            }.onFailure { err ->
                showNotification(err.message ?: "Failed to send announcement")
            }
        }
    }

    fun connectGoogleAccount(googleEmail: String, onResult: (Result<UserEntity>) -> Unit) {
        val uid = currentUser.value?.userId ?: run {
            onResult(Result.failure(Exception("No active user logged in.")))
            return
        }
        viewModelScope.launch {
            val res = repository.connectGoogleAccount(uid, googleEmail)
            onResult(res)
            if (res.isSuccess) {
                showNotification("Account Protected: Google account connected ✓")
            }
        }
    }

    fun connectRecoveryEmail(recoveryEmail: String, onResult: (Result<UserEntity>) -> Unit) {
        val uid = currentUser.value?.userId ?: run {
            onResult(Result.failure(Exception("No active user logged in.")))
            return
        }
        viewModelScope.launch {
            val res = repository.connectRecoveryEmail(uid, recoveryEmail)
            onResult(res)
            if (res.isSuccess) {
                showNotification("Account Protected: Recovery email added ✓")
            }
        }
    }

    fun removeRecoveryMethod(methodType: String, onResult: (Result<UserEntity>) -> Unit) {
        val uid = currentUser.value?.userId ?: run {
            onResult(Result.failure(Exception("No active user logged in.")))
            return
        }
        viewModelScope.launch {
            val res = repository.removeRecoveryMethod(uid, methodType)
            onResult(res)
            if (res.isSuccess) {
                showNotification("Recovery method removed.")
            }
        }
    }

    fun loginWithGoogle(googleEmail: String, onResult: (Result<UserEntity>) -> Unit) {
        viewModelScope.launch {
            val res = repository.loginWithGoogle(googleEmail)
            onResult(res)
            if (res.isSuccess) {
                showNotification("Welcome back to Leno!")
            }
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        val myId = currentUser.value?.userId
        activeChatPartnerId.value = null
        searchQuery.value = ""
        _typingStatusMap.value = emptyMap()
        endCallInternal(saveLog = false)
        viewModelScope.launch {
            if (myId != null) {
                repository.logout(myId)
            } else {
                repository.clearCurrentSession()
            }
            showNotification("Logged out")
            _loggedOutEvent.emit(Unit)
            onComplete()
        }
    }

    // ==========================================
    // VOICE CALL CONTROLS & SIGNALING
    // ==========================================

    fun startVoiceCall(partner: UserEntity) {
        val me = currentUser.value
        if (me?.isRestricted == true) {
            showNotification("Your account is restricted from placing voice calls.")
            return
        }
        if (partner.isRestricted) {
            showNotification("Cannot call this user. The account is currently restricted.")
            return
        }
        val currentUid = me?.userId
        if (currentUid != null && partner.userId == currentUid) {
            showNotification("Cannot call your own account.")
            return
        }
        if (partner.userId == LenoRepository.OFFICIAL_LENO_ID) {
            showNotification("Official Leno is a broadcast channel and cannot be called.")
            return
        }

        callConnectJob?.cancel()
        callTimerJob?.cancel()
        activeCallListener?.remove()

        // Check if recipient is offline / inactive
        if (!partner.isOnline) {
            _callState.value = CallState.Outgoing(
                partner = partner,
                statusText = "${partner.displayName} is offline",
                isRinging = false,
                canSimulateAnswer = false
            )
            viewModelScope.launch {
                delay(3000)
                showNotification("${partner.displayName} is offline. Unable to connect.")
                endCallInternal(saveLog = true)
            }
            return
        }

        val callId = "call_" + UUID.randomUUID().toString().take(10)
        currentCloudCallId = callId

        _callState.value = CallState.Outgoing(
            partner = partner,
            statusText = "Calling...",
            isRinging = false
        )
        callAudioHelper.startOutgoingRing()

        // Create remote call offer in Firestore
        if (me != null) {
            val offer = RemoteAccountService.CloudCallRecord(
                callId = callId,
                callerId = me.userId,
                callerName = me.displayName,
                callerUsername = me.username,
                callerAvatar = me.avatarUrl,
                receiverId = partner.userId,
                receiverName = partner.displayName,
                receiverUsername = partner.username,
                receiverAvatar = partner.avatarUrl,
                status = "CALLING",
                isVideo = false,
                timestamp = System.currentTimeMillis()
            )
            viewModelScope.launch {
                repository.remoteAccountService.createCallOffer(offer)
            }

            // Real-time Firestore call state listener
            activeCallListener = repository.remoteAccountService.listenToCall(callId) { record ->
                if (record == null) return@listenToCall
                when (record.status) {
                    "RINGING" -> {
                        val current = _callState.value
                        if (current is CallState.Outgoing) {
                            _callState.value = current.copy(statusText = "Ringing...", isRinging = true)
                        }
                    }
                    "CONNECTED" -> {
                        val current = _callState.value
                        if (current is CallState.Outgoing) {
                            callAudioHelper.playConnectedTone()
                            _callState.value = CallState.Connected(
                                partner = partner,
                                durationSeconds = 0,
                                isMuted = false,
                                isSpeakerOn = false,
                                isIncoming = false
                            )
                            startCallTimer()
                        }
                    }
                    "DECLINED" -> {
                        showNotification("${partner.displayName} declined the call")
                        endCallInternal(saveLog = true)
                    }
                    "ENDED", "CANCELLED", "MISSED" -> {
                        endCallInternal(saveLog = true)
                    }
                }
            }
        }

        // Call timeout after 30 seconds if not answered
        callConnectJob = viewModelScope.launch {
            delay(30000)
            val finalCheck = _callState.value
            if (finalCheck is CallState.Outgoing && finalCheck.partner.userId == partner.userId) {
                showNotification("No answer from ${partner.displayName}")
                currentCloudCallId?.let { id ->
                    repository.remoteAccountService.updateCallStatus(id, "MISSED")
                }
                endCallInternal(saveLog = true)
            }
        }
    }

    fun answerCallForTesting() {
        val current = _callState.value
        if (current is CallState.Outgoing) {
            val partner = current.partner
            callConnectJob?.cancel()
            currentCloudCallId?.let { id ->
                viewModelScope.launch {
                    repository.remoteAccountService.updateCallStatus(id, "CONNECTED")
                }
            }
            callAudioHelper.playConnectedTone()
            _callState.value = CallState.Connected(
                partner = partner,
                durationSeconds = 0,
                isMuted = false,
                isSpeakerOn = false,
                isIncoming = false
            )
            startCallTimer()
        }
    }

    fun receiveIncomingCall(caller: UserEntity) {
        val me = currentUser.value ?: return
        if (caller.userId == me.userId) return

        callConnectJob?.cancel()
        callTimerJob?.cancel()

        _callState.value = CallState.Incoming(caller)
        callAudioHelper.startIncomingRing()
    }

    fun acceptIncomingCall() {
        val currentState = _callState.value
        if (currentState is CallState.Incoming) {
            val caller = currentState.caller
            currentCloudCallId?.let { id ->
                viewModelScope.launch {
                    repository.remoteAccountService.updateCallStatus(id, "CONNECTED")
                }
            }
            callAudioHelper.playConnectedTone()
            _callState.value = CallState.Connected(
                partner = caller,
                durationSeconds = 0,
                isMuted = false,
                isSpeakerOn = false,
                isIncoming = true
            )
            startCallTimer()
        }
    }

    fun declineIncomingCall() {
        val currentState = _callState.value
        val me = currentUser.value
        if (currentState is CallState.Incoming && me != null) {
            val caller = currentState.caller
            currentCloudCallId?.let { id ->
                viewModelScope.launch {
                    repository.remoteAccountService.updateCallStatus(id, "DECLINED")
                }
            }
            callAudioHelper.playEndCallTone()
            callAudioHelper.resetAudio()

            val callLog = CallEntity(
                callId = "call_" + UUID.randomUUID().toString().take(8),
                callerId = caller.userId,
                callerName = caller.displayName,
                callerUsername = caller.username,
                callerAvatar = caller.avatarUrl,
                receiverId = me.userId,
                receiverName = me.displayName,
                receiverUsername = me.username,
                receiverAvatar = me.avatarUrl,
                timestamp = System.currentTimeMillis(),
                durationSeconds = 0,
                status = "MISSED",
                callType = "VOICE"
            )

            viewModelScope.launch {
                repository.saveCallLog(callLog)
            }
            _callState.value = CallState.Idle
        } else {
            endCallInternal(saveLog = false)
        }
    }

    fun toggleMute() {
        val current = _callState.value
        if (current is CallState.Connected) {
            val newMuted = !current.isMuted
            callAudioHelper.setMicrophoneMute(newMuted)
            _callState.value = current.copy(isMuted = newMuted)
        }
    }

    fun toggleSpeaker() {
        val current = _callState.value
        if (current is CallState.Connected) {
            val newSpeaker = !current.isSpeakerOn
            callAudioHelper.setSpeakerphoneOn(newSpeaker)
            _callState.value = current.copy(isSpeakerOn = newSpeaker)
        }
    }

    fun endCall() {
        endCallInternal(saveLog = true)
    }

    private fun endCallInternal(saveLog: Boolean) {
        callConnectJob?.cancel()
        callTimerJob?.cancel()
        activeCallListener?.remove()
        activeCallListener = null

        val callIdToEnd = currentCloudCallId
        currentCloudCallId = null
        if (callIdToEnd != null) {
            viewModelScope.launch {
                repository.remoteAccountService.updateCallStatus(callIdToEnd, "ENDED")
            }
        }

        val current = _callState.value
        val me = currentUser.value

        if (saveLog && me != null) {
            when (current) {
                is CallState.Connected -> {
                    val partner = current.partner
                    val isIncoming = current.isIncoming
                    val callLog = CallEntity(
                        callId = "call_" + UUID.randomUUID().toString().take(8),
                        callerId = if (isIncoming) partner.userId else me.userId,
                        callerName = if (isIncoming) partner.displayName else me.displayName,
                        callerUsername = if (isIncoming) partner.username else me.username,
                        callerAvatar = if (isIncoming) partner.avatarUrl else me.avatarUrl,
                        receiverId = if (isIncoming) me.userId else partner.userId,
                        receiverName = if (isIncoming) me.displayName else partner.displayName,
                        receiverUsername = if (isIncoming) me.username else partner.username,
                        receiverAvatar = if (isIncoming) me.avatarUrl else partner.avatarUrl,
                        timestamp = System.currentTimeMillis() - (current.durationSeconds * 1000L),
                        durationSeconds = current.durationSeconds,
                        status = "COMPLETED",
                        callType = "VOICE"
                    )
                    viewModelScope.launch {
                        repository.saveCallLog(callLog)
                    }
                }
                is CallState.Outgoing -> {
                    val partner = current.partner
                    val callLog = CallEntity(
                        callId = "call_" + UUID.randomUUID().toString().take(8),
                        callerId = me.userId,
                        callerName = me.displayName,
                        callerUsername = me.username,
                        callerAvatar = me.avatarUrl,
                        receiverId = partner.userId,
                        receiverName = partner.displayName,
                        receiverUsername = partner.username,
                        receiverAvatar = partner.avatarUrl,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 0,
                        status = "OUTGOING",
                        callType = "VOICE"
                    )
                    viewModelScope.launch {
                        repository.saveCallLog(callLog)
                    }
                }
                is CallState.Incoming -> {
                    val caller = current.caller
                    val callLog = CallEntity(
                        callId = "call_" + UUID.randomUUID().toString().take(8),
                        callerId = caller.userId,
                        callerName = caller.displayName,
                        callerUsername = caller.username,
                        callerAvatar = caller.avatarUrl,
                        receiverId = me.userId,
                        receiverName = me.displayName,
                        receiverUsername = me.username,
                        receiverAvatar = me.avatarUrl,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = 0,
                        status = "MISSED",
                        callType = "VOICE"
                    )
                    viewModelScope.launch {
                        repository.saveCallLog(callLog)
                    }
                }
                CallState.Idle -> {}
            }
        }

        callAudioHelper.playEndCallTone()
        callAudioHelper.resetAudio()
        _callState.value = CallState.Idle
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = _callState.value
                if (current is CallState.Connected) {
                    _callState.value = current.copy(durationSeconds = current.durationSeconds + 1)
                } else {
                    break
                }
            }
        }
    }

    fun deleteCallLog(callId: String) {
        viewModelScope.launch {
            repository.deleteCall(callId)
        }
    }

    fun clearAllCallLogs() {
        val uid = currentUser.value?.userId ?: return
        viewModelScope.launch {
            repository.clearCallsForUser(uid)
            showNotification("Call history cleared.")
        }
    }

    // ----------------------------------------------------
    // LENO EDUCATION ACTIONS
    // ----------------------------------------------------

    fun enrollInClass(classId: String, teacherUserId: String = "", onResult: (Boolean) -> Unit = {}) {
        val user = currentUser.value ?: return
        if (user.isRestricted) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val linoId = user.linoId.ifBlank { "LEN-${user.userId.replace("usr_", "").padEnd(8, '0').take(8).uppercase()}" }
            val success = repository.enrollInClass(user.userId, linoId, classId, teacherUserId)
            onResult(success)
        }
    }

    fun submitTeacherApplication(
        subject: String,
        teachingLevel: String,
        educationQualification: String,
        teachingExperience: String,
        teacherIntro: String,
        certificateDocumentName: String,
        sampleTeachingInfo: String = "",
        agreedToGuidelines: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        val user = currentUser.value
        if (user == null) {
            onResult(false, "You must be signed in to apply.")
            return
        }
        if (user.isRestricted) {
            onResult(false, "Your account has been restricted by platform administration.")
            return
        }
        val currentRole = currentUserTeacherRole.value
        val hasPendingInList = _teacherApplications.value.any { it.userId == user.userId && it.isPending }
        if ((currentRole != null && currentRole.isPending) || hasPendingInList) {
            onResult(false, "You already have an application under review (Pending). You can only apply one time.")
            return
        }
        val isApprovedInList = _teacherApplications.value.any { it.userId == user.userId && it.isApproved }
        if ((currentRole != null && currentRole.isApprovedTeacher) || user.isTeacher || !user.assignedSubject.isNullOrBlank() || isApprovedInList) {
            val approvedSubj = user.effectiveSubject.ifBlank { currentRole?.effectiveApprovedSubject ?: "your approved subject" }
            onResult(false, "You are already an approved teacher for $approvedSubj. You can only apply one time.")
            return
        }
        if (!agreedToGuidelines) {
            onResult(false, "You must agree to Leno's teacher guidelines.")
            return
        }
        if (subject.isBlank()) {
            onResult(false, "Please select your teaching subject (One Teacher = One Subject).")
            return
        }
        if (educationQualification.isBlank()) {
            onResult(false, "Please enter your educational qualification.")
            return
        }
        if (teachingExperience.isBlank()) {
            onResult(false, "Please enter your teaching experience.")
            return
        }

        viewModelScope.launch {
            val cleanSubject = subject.trim()
            val application = TeacherRoleEntity(
                userId = user.userId,
                status = "PENDING",
                approvedSubject = cleanSubject,
                isTrustedTeacher = false,
                isContentCreator = currentUserTeacherRole.value?.isContentCreator == true,
                subjects = cleanSubject,
                teachingLevel = teachingLevel.trim().ifBlank { "All Levels" },
                educationQualification = educationQualification.trim(),
                teachingExperience = teachingExperience.trim(),
                teacherIntro = teacherIntro.trim(),
                certificateDocumentName = certificateDocumentName.trim(),
                sampleTeachingInfo = sampleTeachingInfo.trim(),
                agreedToGuidelines = true,
                submittedAt = System.currentTimeMillis()
            )
            val res = repository.submitTeacherApplication(application)
            if (res.isSuccess) {
                onResult(true, "Teacher application submitted! Status: PENDING review.")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to submit application.")
            }
        }
    }

    fun toggleContentCreator(isCreator: Boolean) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.toggleContentCreator(user.userId, isCreator)
        }
    }

    fun assignTeacherRole(
        userId: String,
        subject: String,
        isTrusted: Boolean = false,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.assignTeacherRole(userId, subject, isTrusted)
            if (res.isSuccess) {
                onResult(true, "Teacher role assigned successfully for subject: $subject")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to assign teacher role.")
            }
        }
    }

    fun assignStudentRole(
        userId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.assignStudentRole(userId)
            if (res.isSuccess) {
                onResult(true, "Role reverted to normal user.")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to change role.")
            }
        }
    }

    fun updateBankSettlementAccount(
        bankName: String,
        accountNumber: String,
        accountName: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val user = currentUser.value
        if (user == null) {
            onResult(false, "User not authenticated.")
            return
        }
        if (bankName.isBlank() || accountNumber.length != 10 || accountName.isBlank()) {
            onResult(false, "Please provide valid 10-digit account number, account name, and bank.")
            return
        }
        viewModelScope.launch {
            repository.updateBankSettlementAccount(user.userId, bankName.trim(), accountNumber.trim(), accountName.trim())
            onResult(true, "Bank settlement account updated successfully.")
        }
    }

    fun requestTeacherPayout(
        amount: Double,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val user = currentUser.value
        if (user == null) {
            onResult(false, "User not authenticated.")
            return
        }
        val role = currentUserTeacherRole.value
        if (role == null || !role.isApprovedTeacher) {
            onResult(false, "Only approved teachers can request payout.")
            return
        }
        if (role.accountNumber.isBlank() || role.bankName.isBlank()) {
            onResult(false, "Please link your settlement bank account first.")
            return
        }
        if (amount <= 0 || amount > role.availableBalance) {
            onResult(false, "Invalid payout amount. Available: ₦${String.format("%,.2f", role.availableBalance)}")
            return
        }
        viewModelScope.launch {
            repository.recordTeacherPayout(user.userId, amount)
            val ref = "PAYOUT-LEN-${UUID.randomUUID().toString().take(8).uppercase()}"
            onResult(true, "₦${String.format("%,.2f", amount)} transferred to ${role.bankName} (${role.accountNumber})! Reference: $ref")
        }
    }

    /**
     * Starts active real-time monitoring of the Firestore 'teacher_applications' collection.
     */
    fun startTeacherApplicationsMonitor() {
        if (teacherApplicationsListener != null) return
        _isMonitoringApplications.value = true
        teacherApplicationsListener = repository.listenToTeacherApplications { list ->
            _teacherApplications.value = list
        }
        viewModelScope.launch {
            val fetched = repository.fetchTeacherApplications()
            if (fetched.isNotEmpty()) {
                _teacherApplications.value = fetched
            }
        }
    }

    fun refreshTeacherApplications() {
        viewModelScope.launch {
            val list = repository.fetchTeacherApplications()
            _teacherApplications.value = list
        }
    }

    /**
     * Admin approves application:
     * - Marks application 'APPROVED' in Firestore 'teacher_applications' collection
     * - Updates the user's document role to 'teacher' in Firestore
     * - Activates teacher privileges locally in Room
     */
    fun approveTeacherApplicationByAdmin(
        userId: String,
        subject: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val adminId = currentUser.value?.userId ?: "admin"
        viewModelScope.launch {
            val res = repository.approveTeacherApplicationByAdmin(userId, subject, adminId)
            if (res.isSuccess) {
                _teacherApplications.value = _teacherApplications.value.map { app ->
                    if (app.userId == userId) app.copy(
                        status = "APPROVED",
                        approvedSubject = subject,
                        reviewedAt = System.currentTimeMillis(),
                        reviewedBy = adminId
                    ) else app
                }
                onResult(true, "Application approved! User document role updated to 'teacher' in Firestore.")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to approve application.")
            }
        }
    }

    /**
     * Admin rejects application:
     * - Marks application 'REJECTED' in Firestore 'teacher_applications' collection
     * - Updates user's document status in Firestore
     */
    fun rejectTeacherApplicationByAdmin(
        userId: String,
        reason: String = "Requirements not met",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val adminId = currentUser.value?.userId ?: "admin"
        viewModelScope.launch {
            val res = repository.rejectTeacherApplicationByAdmin(userId, reason, adminId)
            if (res.isSuccess) {
                _teacherApplications.value = _teacherApplications.value.map { app ->
                    if (app.userId == userId) app.copy(
                        status = "REJECTED",
                        rejectionReason = reason,
                        reviewedAt = System.currentTimeMillis(),
                        reviewedBy = adminId
                    ) else app
                }
                onResult(true, "Application marked as rejected in Firestore.")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to reject application.")
            }
        }
    }

    /**
     * Seeds a realistic applicant to Firestore 'teacher_applications' collection
     * allowing admins to test application monitoring, approval, and role changes immediately.
     */
    fun seedSampleTeacherApplication(
        subject: String = "Mathematics",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val randomNum = (1000..9999).random()
            val sampleUid = "applicant_$randomNum"
            val sampleRole = TeacherRoleEntity(
                userId = sampleUid,
                status = "PENDING",
                approvedSubject = subject,
                subjects = subject,
                teachingLevel = "Senior Secondary & University",
                educationQualification = "B.Sc. Mathematics & Education (Second Class Upper)",
                teachingExperience = "4+ years preparing students for WAEC, JAMB & university calculus",
                teacherIntro = "Passionate educator dedicated to demystifying complex concepts with relatable real-world applications.",
                submittedAt = System.currentTimeMillis()
            )
            val dummyNames = listOf("Dr. Amina Bello", "Mr. Tunde Bakare", "Engr. Emeka Obi", "Mrs. Zainab Yusuf", "Prof. Dayo Adeyemi")
            val dummyName = dummyNames.random()
            val dummyUsername = dummyName.lowercase().replace(" ", "_").replace(".", "")
            val dummyEmail = "$dummyUsername@example.com"

            val dummyUser = UserEntity(
                userId = sampleUid,
                username = dummyUsername,
                displayName = dummyName,
                email = dummyEmail,
                bio = "Passionate educator on Leno ✨",
                role = "USER"
            )
            val res = repository.seedSampleTeacherApplication(sampleRole, dummyUser)
            if (res.isSuccess) {
                val updated = repository.fetchTeacherApplications()
                _teacherApplications.value = updated
                onResult(true, "Sample application created for $dummyName ($subject) in 'teacher_applications'!")
            } else {
                onResult(false, "Failed to submit sample application.")
            }
        }
    }

    fun approveTeacherApplication(
        userId: String,
        subject: String,
        isTrusted: Boolean = false,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        approveTeacherApplicationByAdmin(userId, subject, onResult)
    }

    fun rejectTeacherApplication(
        userId: String,
        reason: String = "Requirements not met",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        rejectTeacherApplicationByAdmin(userId, reason, onResult)
    }

    /**
     * Loads all registered users from Firestore & Room for admin role review and management.
     */
    fun loadAdminDirectoryUsers() {
        viewModelScope.launch {
            val users = repository.fetchAllUsersForAdmin()
            _adminDirectoryUsers.value = users
        }
    }

    /**
     * Admin updates any user's role (not only teachers, but every user).
     * Triggers the update function to set role to 'teacher' or 'user' in their profile in Firestore and Room.
     */
    fun updateUserRoleByAdmin(
        userId: String,
        newRole: String, // "teacher" or "user"
        assignedSubject: String = "",
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val adminId = currentUser.value?.userId ?: "admin"
        viewModelScope.launch {
            val res = repository.updateUserRoleByAdmin(userId, newRole, assignedSubject, adminId)
            if (res.isSuccess) {
                _adminDirectoryUsers.value = _adminDirectoryUsers.value.map { u ->
                    if (u.userId == userId) {
                        val isTeacher = newRole.equals("teacher", ignoreCase = true)
                        u.copy(
                            role = if (isTeacher) "TEACHER" else "USER",
                            assignedSubject = if (isTeacher) assignedSubject else ""
                        )
                    } else u
                }
                val roleTitle = if (newRole.equals("teacher", ignoreCase = true)) "Teacher ($assignedSubject)" else "Standard User"
                onResult(true, "Role updated to $roleTitle in Firestore profile successfully.")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to update user role.")
            }
        }
    }

    fun setUserRestricted(userId: String, isRestricted: Boolean, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = repository.setUserRestricted(userId, isRestricted)
            _adminDirectoryUsers.value = _adminDirectoryUsers.value.map { u ->
                if (u.userId == userId) u.copy(isRestricted = isRestricted) else u
            }
            loadAdminDirectoryUsers()
            val msg = if (isRestricted) "User account has been restricted." else "User restriction has been lifted."
            onResult(true, msg)
        }
    }

    fun deleteUserByAdmin(userId: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = repository.deleteUserByAdmin(userId)
            if (res.isSuccess) {
                _adminDirectoryUsers.value = _adminDirectoryUsers.value.filter { it.userId != userId }
                loadAdminDirectoryUsers()
                refreshTeacherApplications()
                onResult(true, "User account successfully deleted.")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to delete user.")
            }
        }
    }

    fun getLessonsForClass(classId: String): Flow<List<LessonEntity>> =
        repository.getLessonsForClass(classId)

    fun createClass(
        title: String,
        subject: String,
        level: String,
        shortDescription: String,
        lessonContent: String = "",
        imageUrl: String = "",
        optionalImages: String = "",
        videoUrl: String = "",
        isPaid: Boolean = true,
        price: String = "₦1,500",
        schedule: String = "Self-paced",
        lessonType: String = "Text lesson",
        status: String = "PUBLISHED",
        onResult: (Boolean, String) -> Unit
    ) {
        val user = currentUser.value
        if (user == null) {
            onResult(false, "You must be logged in to create a class.")
            return
        }
        if (user.isRestricted) {
            onResult(false, "Your account is restricted. You cannot publish classes.")
            return
        }

        val role = currentUserTeacherRole.value
        // Strict Security Check: Must be APPROVED Teacher
        if (role == null || !role.isApprovedTeacher) {
            onResult(false, "Only APPROVED Teachers can create and publish classes. Please apply from your Profile.")
            return
        }

        // ONE TEACHER = ONE SUBJECT: Locked strictly to approved subject
        val lockedSubject = role.effectiveApprovedSubject
        if (title.isBlank()) {
            onResult(false, "Please enter a class title.")
            return
        }
        if (shortDescription.isBlank()) {
            onResult(false, "Please enter a short description.")
            return
        }

        val hasVideo = videoUrl.isNotBlank() || lessonType.contains("video", ignoreCase = true)
        val linoId = user.linoId.ifBlank { "LEN-${user.userId.replace("usr_", "").padEnd(8, '0').take(8).uppercase()}" }
        val newClassId = "cls_${UUID.randomUUID().toString().take(8)}"

        val effectiveFee = price.trim().let { if (it.isBlank() || it.equals("Free", ignoreCase = true)) "₦1,500" else it }

        val newClass = ClassEntity(
            classId = newClassId,
            title = title.trim(),
            subject = lockedSubject, // Strict enforcement of approved subject
            level = level.trim().ifBlank { "All Levels" },
            shortDescription = shortDescription.trim(),
            lessonContent = lessonContent.trim(),
            imageUrl = imageUrl.trim(),
            optionalImages = optionalImages.trim(),
            videoUrl = videoUrl.trim(),
            hasVideo = hasVideo,
            isPaid = true,
            price = effectiveFee,
            schedule = schedule.trim().ifBlank { "Self-paced" },
            lessonType = lessonType.trim(),
            instructorUserId = user.userId,
            instructorName = user.displayName.ifBlank { user.username },
            instructorUsername = user.username,
            instructorLinoId = linoId,
            instructorIsTeacher = true,
            instructorIsTrusted = role.isTrustedTeacher,
            enrolledStudentsCount = 0,
            status = status,
            lessonCount = if (lessonContent.isNotBlank()) 1 else 0,
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            val result = repository.createOrUpdateClass(newClass)
            if (result.isSuccess) {
                // If initial lesson provided, insert it
                if (lessonContent.isNotBlank()) {
                    val lesson = LessonEntity(
                        lessonId = "les_${UUID.randomUUID().toString().take(8)}",
                        classId = newClassId,
                        title = "Lesson 1: Introduction",
                        orderIndex = 1,
                        summary = shortDescription.take(100),
                        content = lessonContent.trim()
                    )
                    repository.addLessonToClass(lesson)
                }
                onResult(true, if (status == "DRAFT") "Class saved as Draft." else "Class published successfully!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to create class.")
            }
        }
    }

    fun addLessonToClass(
        classId: String,
        title: String,
        content: String,
        summary: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        val user = currentUser.value
        if (user == null) {
            onResult(false, "You must be logged in.")
            return
        }
        if (user.isRestricted) {
            onResult(false, "Your account is restricted from adding lessons.")
            return
        }
        val role = currentUserTeacherRole.value
        if (role == null || !role.isApprovedTeacher) {
            onResult(false, "Only approved Teachers can add lessons.")
            return
        }
        if (title.isBlank()) {
            onResult(false, "Please enter a lesson title.")
            return
        }
        if (content.isBlank()) {
            onResult(false, "Please enter lesson content.")
            return
        }

        viewModelScope.launch {
            val existingLessons = repository.getLessonsForClassSync(classId)
            val orderIndex = existingLessons.size + 1
            val lesson = LessonEntity(
                lessonId = "les_${UUID.randomUUID().toString().take(8)}",
                classId = classId,
                title = title.trim(),
                orderIndex = orderIndex,
                summary = summary.ifBlank { content.take(80) },
                content = content.trim()
            )
            val res = repository.addLessonToClass(lesson)
            if (res.isSuccess) {
                onResult(true, "Lesson published successfully!")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to add lesson.")
            }
        }
    }

    fun publishClassDraft(classId: String, onResult: (Boolean, String) -> Unit) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val res = repository.publishClassDraft(classId, user.userId)
            if (res.isSuccess) {
                onResult(true, "Class published successfully!")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to publish class.")
            }
        }
    }

    fun deleteClass(classId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteClass(classId)
            onResult(true)
        }
    }

    fun syncClassesFromRemote() {
        viewModelScope.launch {
            repository.syncClassesFromRemote()
            currentUser.value?.let { repository.syncEnrollmentsFromRemote(it.userId) }
        }
    }

    fun listenToClassLessons(classId: String, onLessons: (List<LessonEntity>) -> Unit): ListenerRegistration? =
        repository.listenToClassLessons(classId, onLessons)

    fun getQuestionsForClass(classId: String): Flow<List<ClassQuestionEntity>> =
        repository.getQuestionsForClass(classId)

    fun getEnrolledStudentsForClass(classId: String): Flow<List<UserEntity>> =
        repository.getEnrolledStudentsForClass(classId)

    fun askQuestionInClass(classId: String, questionText: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val user = currentUser.value ?: run {
            onResult(false, "User not signed in")
            return
        }
        viewModelScope.launch {
            val res = repository.askQuestionInClass(
                classId = classId,
                senderUserId = user.userId,
                senderName = user.displayName.ifBlank { user.linoId },
                senderLinoId = user.linoId,
                questionText = questionText
            )
            if (res.isSuccess) {
                onResult(true, "Question submitted successfully.")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to post question.")
            }
        }
    }

    fun answerQuestionInClass(questionId: String, answerText: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = repository.answerQuestionInClass(questionId, answerText)
            if (res.isSuccess) {
                onResult(true, "Answer posted successfully.")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to post answer.")
            }
        }
    }

    fun broadcastOfficialAnnouncement(
        title: String,
        body: String,
        category: String = "Announcement",
        imageUrl: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.sendOfficialAnnouncement(
                title = title,
                body = body,
                category = category,
                targetUserIds = emptyList(),
                imageUrl = imageUrl
            )
            if (res.isSuccess) {
                val count = res.getOrDefault(0)
                onResult(true, "Broadcast published to $count members as Official Leno ✓")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Failed to broadcast announcement.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.unregisterAuthStateListener(authStateListener)
        authStateListener = null
        incomingCallsListener?.remove()
        incomingMessagesListener?.remove()
        sentReceiptsListener?.remove()
        userChatsListener?.remove()
        activeChatMessagesListener?.remove()
        userDocumentListener?.remove()
        teacherApplicationsListener?.remove()
        callConnectJob?.cancel()
        callTimerJob?.cancel()
        callAudioHelper.resetAudio()
    }
}
