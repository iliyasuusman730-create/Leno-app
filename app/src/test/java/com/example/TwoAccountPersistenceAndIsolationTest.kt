package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LenoRepository
import com.example.data.entity.UserEntity
import com.example.data.service.RemoteAccountService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * FakeRemoteAccountService simulates real Firebase Auth and Cloud Firestore:
 * - Permanent UID generation per account
 * - 'users' collection mapped by permanent UID (/users/{uid})
 * - 'usernames' collection ensuring atomic uniqueness claims
 * - 'leno_ids' collection ensuring unique Leno IDs
 */
class FakeRemoteAccountService : RemoteAccountService() {
    val remoteUsers = mutableMapOf<String, UserEntity>()
    val claimedUsernames = mutableMapOf<String, String>() // username -> ownerUid
    val claimedLenoIds = mutableMapOf<String, String>() // lenoId -> ownerUid
    val authAccounts = mutableMapOf<String, String>() // email -> uid
    val authPasswords = mutableMapOf<String, String>() // email -> password
    var currentSessionUid: String? = null

    override suspend fun saveUserToRemote(user: UserEntity): Result<Unit> {
        remoteUsers[user.userId] = user
        return Result.success(Unit)
    }

    override suspend fun signUpWithFirebaseAuth(email: String, password: String): Result<String> {
        val clean = email.trim().lowercase()
        if (authAccounts.containsKey(clean)) {
            return Result.failure(Exception("The email address is already in use by another account."))
        }
        val uid = "fb_uid_${clean.replace("@", "_").replace(".", "_")}"
        authAccounts[clean] = uid
        authPasswords[clean] = password
        currentSessionUid = uid
        return Result.success(uid)
    }

    override suspend fun signInWithFirebaseAuth(email: String, password: String): Result<String> {
        val clean = email.trim().lowercase()
        val uid = authAccounts[clean]
            ?: return Result.failure(Exception("There is no user record corresponding to this identifier."))
        val storedPass = authPasswords[clean]
        if (storedPass != null && storedPass != password) {
            return Result.failure(Exception("The password is invalid or the user does not have a password."))
        }
        currentSessionUid = uid
        return Result.success(uid)
    }

    override fun signOutFromFirebase() {
        currentSessionUid = null
    }

    override suspend fun findRemoteUserById(userId: String): UserEntity? {
        return remoteUsers[userId]
    }

    override suspend fun findRemoteUser(identifier: String): UserEntity? {
        val clean = identifier.trim().removePrefix("@").lowercase()
        val formattedLeno = if (!clean.startsWith("LEN-", ignoreCase = true)) "LEN-$clean" else clean
        return remoteUsers.values.firstOrNull {
            it.userId == identifier ||
            it.username.lowercase() == clean ||
            it.email.lowercase() == clean ||
            it.linoId.equals(formattedLeno, ignoreCase = true)
        }
    }

    override suspend fun claimUsernameAtomic(
        uid: String,
        newUsername: String,
        oldUsername: String?
    ): Result<Unit> {
        val cleanNew = newUsername.trim().removePrefix("@").lowercase()
        val cleanOld = oldUsername?.trim()?.removePrefix("@")?.lowercase()

        val existingOwner = claimedUsernames[cleanNew]
        if (existingOwner != null && existingOwner != uid) {
            return Result.failure(Exception("Username @$cleanNew is already taken."))
        }

        if (!cleanOld.isNullOrBlank() && cleanOld != cleanNew) {
            if (claimedUsernames[cleanOld] == uid) {
                claimedUsernames.remove(cleanOld)
            }
        }

        claimedUsernames[cleanNew] = uid
        return Result.success(Unit)
    }

    override suspend fun claimLenoIdAtomic(
        uid: String,
        newLenoId: String,
        oldLenoId: String?
    ): Result<Unit> {
        val cleanNew = newLenoId.trim().uppercase()
        val cleanOld = oldLenoId?.trim()?.uppercase()

        val existingOwner = claimedLenoIds[cleanNew]
        if (existingOwner != null && existingOwner != uid) {
            return Result.failure(Exception("Leno ID $cleanNew is already taken."))
        }

        if (!cleanOld.isNullOrBlank() && cleanOld != cleanNew) {
            if (claimedLenoIds[cleanOld] == uid) {
                claimedLenoIds.remove(cleanOld)
            }
        }

        claimedLenoIds[cleanNew] = uid
        return Result.success(Unit)
    }

    override suspend fun updateFirebaseEmail(newEmail: String): Result<Unit> {
        val clean = newEmail.trim().lowercase()
        val uid = currentSessionUid
        if (uid != null) {
            authAccounts.entries.removeIf { it.value == uid }
            authAccounts[clean] = uid
        }
        return Result.success(Unit)
    }

    override suspend fun isEmailTakenInFirebaseAuth(email: String): Boolean {
        val clean = email.trim().lowercase()
        return authAccounts.containsKey(clean) || remoteUsers.values.any { it.email.lowercase() == clean }
    }

    val remoteMessages = mutableMapOf<String, com.example.data.entity.MessageEntity>()

    override suspend fun saveMessageToRemote(message: com.example.data.entity.MessageEntity): Result<Unit> {
        remoteMessages[message.messageId] = message
        return Result.success(Unit)
    }

    override suspend fun getAllUserChatsFromRemote(myUserId: String, aliases: List<String>): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        val allIds = (listOf(myUserId) + aliases).distinct()
        remoteMessages.values.forEach { msg ->
            if (msg.senderId in allIds) {
                val cid = getChatId(myUserId, msg.receiverId)
                if (pairs.none { it.first == cid }) pairs.add(Pair(cid, msg.receiverId))
            } else if (msg.receiverId in allIds) {
                val cid = getChatId(myUserId, msg.senderId)
                if (pairs.none { it.first == cid }) pairs.add(Pair(cid, msg.senderId))
            }
        }
        return pairs
    }

    override suspend fun getChatMessagesFromRemote(chatId: String): List<com.example.data.entity.MessageEntity> {
        return remoteMessages.values.filter {
            getChatId(it.senderId, it.receiverId) == chatId
        }.sortedBy { it.timestamp }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoAccountPersistenceAndIsolationTest {

    private lateinit var database: AppDatabase
    private lateinit var fakeRemoteService: FakeRemoteAccountService
    private lateinit var repository: LenoRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeRemoteService = FakeRemoteAccountService()
        repository = LenoRepository(database, context, fakeRemoteService)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun testTwoAccountFullLifecycleAndIsolation() = runBlocking {
        // -------------------------------------------------------------
        // STEP 1: Register Account A with unique username, full name, password
        // -------------------------------------------------------------
        val regResultA = repository.registerWithLino(
            fullName = "Alice Wonderland",
            username = "alice_wonder",
            password = "Password123!"
        )
        assertTrue("Account A registration must succeed: ${regResultA.exceptionOrNull()?.message}", regResultA.isSuccess)
        val userA = regResultA.getOrThrow()
        val uidA = userA.userId

        // Confirm permanent profile is stored in backend Firestore (/users/{uid})
        val remoteDocA = fakeRemoteService.findRemoteUserById(uidA)
        assertNotNull("Account A must have permanent document in backend /users/{uid}", remoteDocA)
        assertEquals("alice_wonder", remoteDocA?.username)
        assertEquals("Alice Wonderland", remoteDocA?.displayName)
        assertTrue("Permanent Leno ID must start with LEN-", remoteDocA?.linoId?.startsWith("LEN-") == true)

        // Confirm Account A is currently logged in
        val currentA = repository.currentUser.first()
        assertNotNull("Account A must be active session", currentA)
        assertEquals(uidA, currentA?.userId)
        assertEquals("alice_wonder", currentA?.username)

        // -------------------------------------------------------------
        // STEP 2: Log out Account A completely
        // -------------------------------------------------------------
        repository.logout(uidA)
        val afterLogoutA = repository.currentUser.first()
        assertNull("After logout of Account A, current user must be null", afterLogoutA)
        assertFalse("Session must be inactive", repository.isSessionActive())

        // -------------------------------------------------------------
        // STEP 3: Register Account B with different username and details
        // -------------------------------------------------------------
        val regResultB = repository.registerWithLino(
            fullName = "Bob Builder",
            username = "bob_builder",
            password = "Password456!"
        )
        assertTrue("Account B registration must succeed: ${regResultB.exceptionOrNull()?.message}", regResultB.isSuccess)
        val userB = regResultB.getOrThrow()
        val uidB = userB.userId

        // Confirm Account B gets a completely different Firebase UID and separate profile
        assertNotEquals("Account B must have a different UID than Account A", uidA, uidB)
        assertNotEquals("Account B must have a different username", userA.username, userB.username)

        val remoteDocB = fakeRemoteService.findRemoteUserById(uidB)
        assertNotNull("Account B must have separate permanent document in /users/{uidB}", remoteDocB)
        assertEquals("bob_builder", remoteDocB?.username)
        assertEquals("Bob Builder", remoteDocB?.displayName)

        // -------------------------------------------------------------
        // STEP 4: Confirm Account B does NOT inherit Account A's state
        // -------------------------------------------------------------
        val currentB = repository.currentUser.first()
        assertNotNull("Account B must be current user", currentB)
        assertEquals(uidB, currentB?.userId)
        assertEquals("bob_builder", currentB?.username)
        assertEquals("Bob Builder", currentB?.displayName)

        val contactsB = repository.getContactsForUser(uidB).first()
        assertTrue("Account B should have no personal contacts initially", contactsB.isEmpty())

        // -------------------------------------------------------------
        // STEP 5: Log out of Account B and simulate app restart
        // -------------------------------------------------------------
        repository.logout(uidB)
        val afterLogoutB = repository.currentUser.first()
        assertNull("After logout of Account B, current user must be null", afterLogoutB)

        // Simulate app restart with a fresh repository instance
        val restartedRepo = LenoRepository(database, context, fakeRemoteService)
        val onRestartUser = restartedRepo.currentUser.first()
        assertNull("On fresh start without session, current user must be null", onRestartUser)

        // -------------------------------------------------------------
        // STEP 6: Log back into Account A and verify original data
        // -------------------------------------------------------------
        val loginResultA = restartedRepo.loginWithLino("alice_wonder", "Password123!")
        assertTrue("Login into Account A must succeed: ${loginResultA.exceptionOrNull()?.message}", loginResultA.isSuccess)
        val reloadedA = loginResultA.getOrThrow()

        assertEquals(uidA, reloadedA.userId)
        assertEquals("alice_wonder", reloadedA.username)
        assertEquals("Alice Wonderland", reloadedA.displayName)
        assertEquals(userA.linoId, reloadedA.linoId)

        val activeA = restartedRepo.currentUser.first()
        assertEquals(uidA, activeA?.userId)
        assertEquals("alice_wonder", activeA?.username)

        // -------------------------------------------------------------
        // STEP 7: Log out of A, Log into B, verify ONLY Account B appears
        // -------------------------------------------------------------
        restartedRepo.logout(uidA)
        val loginResultB = restartedRepo.loginWithLino("bob_builder", "Password456!")
        assertTrue("Login into Account B must succeed: ${loginResultB.exceptionOrNull()?.message}", loginResultB.isSuccess)
        val reloadedB = loginResultB.getOrThrow()

        assertEquals(uidB, reloadedB.userId)
        assertEquals("bob_builder", reloadedB.username)
        assertEquals("Bob Builder", reloadedB.displayName)
        assertNotEquals(uidA, reloadedB.userId)

        val activeB = restartedRepo.currentUser.first()
        assertEquals(uidB, activeB?.userId)
        assertEquals("bob_builder", activeB?.username)

        // -------------------------------------------------------------
        // STEP 8: Attempt to register Account C with Account A's username -> Rejected
        // -------------------------------------------------------------
        restartedRepo.logout(uidB)
        val regResultC = restartedRepo.registerWithLino(
            fullName = "Charlie Copycat",
            username = "alice_wonder", // Duplicate of Account A!
            password = "Password789!"
        )
        assertFalse("Registering duplicate username 'alice_wonder' must be rejected", regResultC.isSuccess)
        val cError = regResultC.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Rejection error must state username is already taken: $cError",
            cError.contains("already taken", ignoreCase = true) || cError.contains("already exists", ignoreCase = true)
        )

        // -------------------------------------------------------------
        // STEP 9: Log back into Account A and confirm it loads real profile
        // -------------------------------------------------------------
        val loginA2 = restartedRepo.loginWithLino("alice_wonder", "Password123!")
        assertTrue("Logging back into Account A must succeed", loginA2.isSuccess)
        val verifiedA = loginA2.getOrThrow()
        assertEquals(uidA, verifiedA.userId)
        assertEquals("alice_wonder", verifiedA.username)

        // -------------------------------------------------------------
        // STEP 10: Edit Account A's profile and confirm backend updates
        // -------------------------------------------------------------
        val updateResult = restartedRepo.updateProfile(
            userId = uidA,
            displayName = "Queen Alice",
            username = "alice_queen",
            bio = "Ruling the Wonderland realm 👑",
            avatarUrl = "https://example.com/alice_queen.jpg",
            statusMessage = "Busy ruling",
            phoneNumber = "+1234567890"
        )
        assertTrue("Profile update must succeed: ${updateResult.exceptionOrNull()?.message}", updateResult.isSuccess)
        val updatedUser = updateResult.getOrThrow()
        assertEquals("alice_queen", updatedUser.username)
        assertEquals("Queen Alice", updatedUser.displayName)

        // Confirm backend Firestore document reflects new username and display name
        val backendDoc = fakeRemoteService.findRemoteUserById(uidA)
        assertNotNull(backendDoc)
        assertEquals("alice_queen", backendDoc?.username)
        assertEquals("Queen Alice", backendDoc?.displayName)
        assertEquals("Ruling the Wonderland realm 👑", backendDoc?.bio)

        // Duplicate check on new username "alice_queen" should now fail
        val regResultD = restartedRepo.registerWithLino(
            fullName = "David Duplicate",
            username = "alice_queen",
            password = "Password000!"
        )
        assertFalse("Registering new username 'alice_queen' must be rejected", regResultD.isSuccess)

        // Former username "alice_wonder" should now be freed and available
        val regResultE = restartedRepo.registerWithLino(
            fullName = "Eve Explorer",
            username = "alice_wonder", // Was released by Alice
            password = "PasswordEve123!"
        )
        assertTrue("Freed username 'alice_wonder' should now be registerable: ${regResultE.exceptionOrNull()?.message}", regResultE.isSuccess)
    }

    @Test
    fun testLogoutClearsSessionAndState() = runBlocking {
        val regResult = repository.registerWithLino(
            fullName = "Logout Tester",
            username = "logout_tester",
            password = "SecurePassword123!"
        )
        assertTrue("Registration must succeed", regResult.isSuccess)
        val user = regResult.getOrThrow()

        assertTrue("Session must be active after registration", repository.isSessionActive())
        val loggedInUser = repository.currentUser.first()
        assertNotNull("Current user must be non-null after login", loggedInUser)
        assertEquals(user.userId, loggedInUser?.userId)

        // Perform logout
        repository.logout(user.userId)

        assertFalse("Session must be inactive after logout", repository.isSessionActive())
        val afterLogoutUser = repository.currentUser.first()
        assertNull("Current user must be null after logout", afterLogoutUser)

        // Calling clearCurrentSession when already logged out should be safe and remain inactive
        repository.clearCurrentSession()
        assertFalse("Session must remain inactive", repository.isSessionActive())
        assertNull("Current user must remain null", repository.currentUser.first())
    }

    @Test
    fun testChatPersistenceAcrossLogoutAndLogin() = runBlocking {
        // Step 1: Register and login User A (Alice)
        val regA = repository.registerWithLino("Alice Springs", "alice_chat", "PassAlice123!")
        assertTrue(regA.isSuccess)
        val userA = regA.getOrThrow()

        // Step 2: Register User B (Bob)
        val regB = repository.registerWithLino("Bob Chats", "bob_chat", "PassBob123!")
        assertTrue(regB.isSuccess)
        val userB = regB.getOrThrow()

        // Switch active user back to Alice
        repository.logout(userB.userId)
        val loginA = repository.loginWithLino("alice_chat", "PassAlice123!")
        assertTrue(loginA.isSuccess)

        // Step 3: Alice sends a message to Bob
        repository.sendMessage(
            senderId = userA.userId,
            receiverId = userB.userId,
            text = "Hello Bob, this message will not disappear after logout!"
        )

        // Verify message is saved for Alice
        val aliceMessagesBefore = repository.getMessagesBetween(userA.userId, userB.userId).first()
        assertEquals(1, aliceMessagesBefore.size)
        assertEquals("Hello Bob, this message will not disappear after logout!", aliceMessagesBefore[0].text)

        // Verify Bob appears in Alice's chat partners
        val alicePartnersBefore = repository.getChatPartnersForUser(userA.userId).first()
        assertTrue("Bob should be in Alice's chat partners", alicePartnersBefore.any { it.userId == userB.userId })

        // Step 4: Alice logs out!
        repository.logout(userA.userId)
        assertFalse("Session should be inactive after logout", repository.isSessionActive())

        // Step 5: Alice logs back in
        val loginA2 = repository.loginWithLino("alice_chat", "PassAlice123!")
        assertTrue("Alice login after logout should succeed", loginA2.isSuccess)

        // Verify Alice STILL has Bob as a chat partner and her sent message is preserved!
        val alicePartnersAfter = repository.getChatPartnersForUser(userA.userId).first()
        assertTrue("Bob must still be in Alice's chat partners after logout/login", alicePartnersAfter.any { it.userId == userB.userId })

        val aliceMessagesAfter = repository.getMessagesBetween(userA.userId, userB.userId).first()
        assertEquals("Alice sent message must persist across logout and login", 1, aliceMessagesAfter.size)
        assertEquals("Hello Bob, this message will not disappear after logout!", aliceMessagesAfter[0].text)

        // Step 6: Log out Alice, log in Bob
        repository.logout(userA.userId)
        val loginB = repository.loginWithLino("bob_chat", "PassBob123!")
        assertTrue(loginB.isSuccess)

        // Bob should see Alice in chat partners and see the message
        val bobPartners = repository.getChatPartnersForUser(userB.userId).first()
        assertTrue("Alice must be in Bob's chat partners", bobPartners.any { it.userId == userA.userId })

        val bobMessages = repository.getMessagesBetween(userB.userId, userA.userId).first()
        assertEquals(1, bobMessages.size)
        assertEquals("Hello Bob, this message will not disappear after logout!", bobMessages[0].text)

        // Bob replies to Alice
        repository.sendMessage(
            senderId = userB.userId,
            receiverId = userA.userId,
            text = "Got your message Alice! Stored safely!"
        )

        // Bob logs out and logs back in
        repository.logout(userB.userId)
        val loginB2 = repository.loginWithLino("bob_chat", "PassBob123!")
        assertTrue(loginB2.isSuccess)

        val bobMessagesAfter = repository.getMessagesBetween(userB.userId, userA.userId).first()
        assertEquals("Both messages must be preserved after Bob logs out and in", 2, bobMessagesAfter.size)
    }
}
