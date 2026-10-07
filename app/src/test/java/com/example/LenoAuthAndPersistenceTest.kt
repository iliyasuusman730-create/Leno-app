package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LenoRepository
import com.example.util.AuthSecurity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LenoAuthAndPersistenceTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: LenoRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        com.example.util.AccountBackupStore(context).clearAll()
        com.example.util.SessionManager(context).clearSession()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LenoRepository(database, context)
        repository.remoteAccountService.clearTestDataForTests()
    }

    @After
    fun teardown() {
        repository.remoteAccountService.clearTestDataForTests()
        com.example.util.AccountBackupStore(context).clearAll()
        com.example.util.SessionManager(context).clearSession()
        database.close()
    }

    @Test
    fun testPasswordHashingSecurity() {
        val plainPassword = "SecretPassword123"
        val salt = AuthSecurity.generateSalt()
        val hash = AuthSecurity.hashPassword(plainPassword, salt)

        assertNotEquals("Plain password must not equal hash", plainPassword, hash)
        assertTrue("Hash must not be empty", hash.isNotEmpty())
        assertTrue("Valid password matches hash", AuthSecurity.verifyPassword(plainPassword, hash, salt))
        assertFalse("Wrong password must fail verification", AuthSecurity.verifyPassword("WrongPassword", hash, salt))
    }

    @Test
    fun testTenStepPersistenceAndAuthenticationScenario(): Unit = runBlocking {
        // Step 1: Create Account A
        val regResult = repository.registerWithLino(
            fullName = "Alice Smith",
            username = "alicesmith",
            password = "SecurePassword2026"
        )
        assertTrue("Registration must succeed", regResult.isSuccess)
        val accountA = regResult.getOrThrow()

        // Step 2: Record username and Leno ID
        val usernameA = accountA.username
        val lenoIdA = accountA.linoId
        val uidA = accountA.userId
        assertEquals("alicesmith", usernameA)
        assertTrue("Leno ID should start with LEN-", lenoIdA.startsWith("LEN-"))
        assertEquals("Leno ID must have exactly 8 digits after LEN-", 12, lenoIdA.length)
        assertTrue("Password hash must be populated", accountA.passwordHash.isNotEmpty())
        assertTrue("Password salt must be populated", accountA.passwordSalt.isNotEmpty())
        assertEquals("Plaintext password should be empty", "", accountA.password)

        // Step 3: Current user is logged in
        val currentBeforeLogout = database.userDao().getCurrentUser().first()
        assertNotNull("Current user must be set after registration", currentBeforeLogout)
        assertEquals(uidA, currentBeforeLogout?.userId)

        // Step 4: Add custom profile attributes (Bio, Status, Avatar)
        repository.updateProfile(
            userId = uidA,
            displayName = "Alice S.",
            username = usernameA,
            bio = "Love coding and chatting!",
            avatarUrl = "https://example.com/alice.jpg",
            statusMessage = "Available"
        )

        // Step 5: Log out
        repository.logout(uidA)
        val currentAfterLogout = database.userDao().getCurrentUser().first()
        assertNull("Current user must be null after logout", currentAfterLogout)

        // Step 6: Verify Account A still exists in the permanent database after logout
        val persistedAccount = database.userDao().getUserByUsernameSync("alicesmith")
        assertNotNull("Account must persist permanently in database after logout", persistedAccount)
        assertEquals("Alice S.", persistedAccount?.displayName)
        assertEquals(lenoIdA, persistedAccount?.linoId)
        assertEquals("Love coding and chatting!", persistedAccount?.bio)

        // Step 7: Attempt to register with the same username (MUST FAIL)
        val duplicateUsernameResult = repository.registerWithLino(
            fullName = "Alice Imposter",
            username = "alicesmith",
            password = "AnotherPassword999"
        )
        assertTrue("Duplicate registration must fail", duplicateUsernameResult.isFailure)

        // Step 8: Log in using Username and Password
        val loginWithUsernameResult = repository.loginWithLino("alicesmith", "SecurePassword2026")
        assertTrue("Login with username must succeed", loginWithUsernameResult.isSuccess)
        val loggedInUser1 = loginWithUsernameResult.getOrThrow()
        assertEquals(uidA, loggedInUser1.userId)
        assertEquals(lenoIdA, loggedInUser1.linoId)
        assertEquals("Alice S.", loggedInUser1.displayName)
        assertEquals("Love coding and chatting!", loggedInUser1.bio)

        // Step 9: Log out again and log in using Leno ID and Password
        repository.logout(uidA)
        val loginWithLenoIdResult = repository.loginWithLino(lenoIdA, "SecurePassword2026")
        assertTrue("Login with Leno ID must succeed", loginWithLenoIdResult.isSuccess)
        val loggedInUser2 = loginWithLenoIdResult.getOrThrow()
        assertEquals(uidA, loggedInUser2.userId)
        assertEquals(usernameA, loggedInUser2.username)

        // Step 10: Attempt login with wrong password (MUST FAIL)
        repository.logout(uidA)
        val wrongPasswordResult = repository.loginWithLino("alicesmith", "WrongPassword!")
        assertTrue("Login with wrong password must fail", wrongPasswordResult.isFailure)
    }

    @Test
    fun testAccountRecoveryFlow(): Unit = runBlocking {
        // Create user
        val reg = repository.registerWithLino("Bob Jones", "bobjones", "OriginalPass1")
        assertTrue(reg.isSuccess)
        val user = reg.getOrThrow()

        // Logout
        repository.logout(user.userId)

        // Recover account with new password
        val recoveryRes = repository.recoverAccount("bobjones", "Bob Jones", "NewSecurePass2026")
        assertTrue("Account recovery must succeed", recoveryRes.isSuccess)

        // Login with old password must fail
        val oldLogin = repository.loginWithLino("bobjones", "OriginalPass1")
        assertTrue("Old password must no longer work", oldLogin.isFailure)

        // Login with new password must succeed
        val newLogin = repository.loginWithLino("bobjones", "NewSecurePass2026")
        assertTrue("New password must succeed", newLogin.isSuccess)
        assertEquals(user.linoId, newLogin.getOrThrow().linoId)
    }

    @Test
    fun testOfficialLenoAccountProtection(): Unit = runBlocking {
        repository.ensureOfficialLenoAccountCreated()

        val officialAccount = database.userDao().getUserByIdSync(LenoRepository.OFFICIAL_LENO_ID)
        assertNotNull("Official Leno account must exist", officialAccount)
        assertEquals("Official Leno", officialAccount?.displayName)
        assertTrue("Official Leno must be official", officialAccount?.isOfficial == true)

        // Attempting to register username "official_leno" or "leno" must fail
        val imposterReg = repository.registerWithLino("Hacker", "official_leno", "Password123")
        assertTrue("Registering reserved official username must fail", imposterReg.isFailure)

        val imposterReg2 = repository.registerWithLino("Hacker", "leno", "Password123")
        assertTrue("Registering reserved 'leno' username must fail", imposterReg2.isFailure)
    }

    @Test
    fun testProtectAccountWithGoogleAndRestoreExistingAccount(): Unit = runBlocking {
        // Step 1: Create a Leno account with standard registration
        val reg = repository.registerWithLino("Grace Hopper", "gracehopper", "PioneerPass2026")
        assertTrue(reg.isSuccess)
        val originalUser = reg.getOrThrow()
        val originalUserId = originalUser.userId
        val originalLenoId = originalUser.linoId
        val originalUsername = originalUser.username

        // Step 2: Open Protect Your Account and connect Google
        val connectGoogleRes = repository.connectGoogleAccount(originalUserId, "grace.hopper@gmail.com")
        assertTrue("Connecting Google must succeed", connectGoogleRes.isSuccess)
        val protectedUser = connectGoogleRes.getOrThrow()
        assertEquals("grace.hopper@gmail.com", protectedUser.googleEmail)
        assertTrue("Account must be marked as protected", protectedUser.isAccountProtected)

        // Step 3: Log out
        repository.logout(originalUserId)
        val currentUserAfterLogout = database.userDao().getCurrentUser().first()
        assertNull("Current user must be null after logout", currentUserAfterLogout)

        // Step 4: Recover / Log in using the connected Google account
        val googleLoginRes = repository.loginWithGoogle("grace.hopper@gmail.com")
        assertTrue("Login with connected Google must succeed", googleLoginRes.isSuccess)
        val restoredUser = googleLoginRes.getOrThrow()

        // Step 5: Confirm it opens the SAME Leno account with SAME Leno ID, username, profile
        assertEquals("Must restore exact same user ID", originalUserId, restoredUser.userId)
        assertEquals("Must restore exact same Leno ID", originalLenoId, restoredUser.linoId)
        assertEquals("Must restore exact same username", originalUsername, restoredUser.username)
        assertEquals("Grace Hopper", restoredUser.displayName)
        assertTrue("Restored user must be current user", restoredUser.isCurrentUser)

        // Step 6: Verify no duplicate account was created
        val allUsersWithUsername = database.userDao().getAllUsersSync().filter { it.username == "gracehopper" }
        assertEquals("There must only be 1 user account in the database", 1, allUsersWithUsername.size)
    }

    @Test
    fun testProtectAccountWithEmailAndRecoveryFlow(): Unit = runBlocking {
        // Step 1: Create a Leno account
        val reg = repository.registerWithLino("Alan Turing", "alanturing", "EnigmaPass999")
        assertTrue(reg.isSuccess)
        val originalUser = reg.getOrThrow()
        val originalUserId = originalUser.userId
        val originalLenoId = originalUser.linoId

        // Step 2: Add recovery email
        val connectEmailRes = repository.connectRecoveryEmail(originalUserId, "alan.turing@cambridge.edu")
        assertTrue("Connecting recovery email must succeed", connectEmailRes.isSuccess)
        val protectedUser = connectEmailRes.getOrThrow()
        assertEquals("alan.turing@cambridge.edu", protectedUser.recoveryEmail)
        assertTrue("Account must be marked as protected", protectedUser.isAccountProtected)

        // Step 3: Log out
        repository.logout(originalUserId)

        // Step 4: Login using recovery email as identifier
        val loginWithEmailRes = repository.loginWithLino("alan.turing@cambridge.edu", "EnigmaPass999")
        assertTrue("Login with recovery email must succeed", loginWithEmailRes.isSuccess)
        val loggedInUser = loginWithEmailRes.getOrThrow()
        assertEquals(originalUserId, loggedInUser.userId)
        assertEquals(originalLenoId, loggedInUser.linoId)
        assertEquals("alanturing", loggedInUser.username)

        // Step 5: Account recovery / reset password using recovery email as identifier
        repository.logout(originalUserId)
        val recoverRes = repository.recoverAccount("alan.turing@cambridge.edu", "Alan Turing", "NewSecretEnigma2026")
        assertTrue("Recovery with recovery email must succeed", recoverRes.isSuccess)

        // Step 6: Log in with new password
        val newLoginRes = repository.loginWithLino("alanturing", "NewSecretEnigma2026")
        assertTrue("Login with new password must succeed", newLoginRes.isSuccess)
        assertEquals(originalUserId, newLoginRes.getOrThrow().userId)
    }

    @Test
    fun testUniqueGoogleAndRecoveryEmailConstraint(): Unit = runBlocking {
        // Register User 1 and link Google
        val user1 = repository.registerWithLino("User One", "userone", "Pass1234").getOrThrow()
        repository.connectGoogleAccount(user1.userId, "shared@gmail.com")

        // Register User 2 and try linking same Google
        val user2 = repository.registerWithLino("User Two", "usertwo", "Pass5678").getOrThrow()
        val duplicateGoogleRes = repository.connectGoogleAccount(user2.userId, "shared@gmail.com")
        assertTrue("Linking same Google account to another user must fail", duplicateGoogleRes.isFailure)

        // Register User 3 and try linking same recovery email
        repository.connectRecoveryEmail(user1.userId, "recovery@email.com")
        val duplicateEmailRes = repository.connectRecoveryEmail(user2.userId, "recovery@email.com")
        assertTrue("Linking same recovery email to another user must fail", duplicateEmailRes.isFailure)
    }

    @Test
    fun testUserScenariosMusaAndCallingFlow(): Unit = runBlocking {
        // TEST 1: Register @musa -> logout -> try registering @musa again -> fails
        val regMusa = repository.registerWithLino("Musa Ibrahim", "musa", "MusaSecret2026")
        assertTrue("Initial registration of @musa must succeed", regMusa.isSuccess)
        val musaUser = regMusa.getOrThrow()
        assertEquals("musa", musaUser.username)
        assertEquals("Musa Ibrahim", musaUser.displayName)

        repository.logout(musaUser.userId)

        // Re-register with username musa (MUST FAIL)
        val dupMusa = repository.registerWithLino("Musa Duplicate", "musa", "AnotherPassword")
        assertTrue("Duplicate registration of @musa must fail", dupMusa.isFailure)
        assertTrue(
            dupMusa.exceptionOrNull()?.message?.contains("taken", ignoreCase = true) == true ||
            dupMusa.exceptionOrNull()?.message?.contains("exists", ignoreCase = true) == true
        )

        // TEST 2: Login as @musa after app restart simulation -> account still exists with same profile
        val loginMusa = repository.loginWithLino("musa", "MusaSecret2026")
        assertTrue("Login as @musa must succeed", loginMusa.isSuccess)
        val loggedInMusa = loginMusa.getOrThrow()
        assertEquals(musaUser.userId, loggedInMusa.userId)
        assertEquals(musaUser.linoId, loggedInMusa.linoId)
        assertEquals("Musa Ibrahim", loggedInMusa.displayName)
        repository.logout(musaUser.userId)

        // TEST 3 & 4 & 5 & 6: Create User A and User B, perform call flow and verify call history
        val userARes = repository.registerWithLino("Alice Developer", "alice", "AlicePass2026")
        assertTrue(userARes.isSuccess)
        val userA = userARes.getOrThrow()

        val userBRes = repository.registerWithLino("Bob Engineer", "bob", "BobPass2026")
        assertTrue(userBRes.isSuccess)
        val userB = userBRes.getOrThrow()

        // Call from User A to User B
        val callEntity = com.example.data.entity.CallEntity(
            callId = "call_" + java.util.UUID.randomUUID().toString().take(8),
            callerId = userA.userId,
            callerName = userA.displayName,
            callerUsername = userA.username,
            callerAvatar = userA.avatarUrl,
            receiverId = userB.userId,
            receiverName = userB.displayName,
            receiverUsername = userB.username,
            receiverAvatar = userB.avatarUrl,
            timestamp = System.currentTimeMillis(),
            durationSeconds = 42,
            status = "COMPLETED"
        )
        database.callDao().insertCall(callEntity)

        // Verify call appears in history for both User A and User B
        val callsUserA = database.callDao().getCallsForUser(userA.userId).first()
        val callsUserB = database.callDao().getCallsForUser(userB.userId).first()
        assertEquals(1, callsUserA.size)
        assertEquals(1, callsUserB.size)
        assertEquals(42, callsUserA.first().durationSeconds)
        assertEquals("COMPLETED", callsUserA.first().status)

        // TEST 7: Logout User A and Login User B -> User A's private data is not visible as current user
        repository.logout(userA.userId)
        val loginB = repository.loginWithLino("bob", "BobPass2026").getOrThrow()
        val currentLoggedIn = database.userDao().getCurrentUser().first()
        assertNotNull(currentLoggedIn)
        assertEquals(userB.userId, currentLoggedIn?.userId)
        assertNotEquals(userA.userId, currentLoggedIn?.userId)
    }

    @Test
    fun testPersistentDiskAppReloadAndMusaLookup(): Unit = runBlocking {
        val testDbFile = context.getDatabasePath("test_real_disk_persistence.db")
        testDbFile.delete()
        com.example.util.AccountBackupStore(context).clearAll()
        com.example.util.SessionManager(context).clearSession()

        // 1. App Launch 1: Register Musa on persistent disk database
        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, "test_real_disk_persistence.db")
            .allowMainThreadQueries()
            .build()
        val repo1 = LenoRepository(db1, context)

        val regResult = repo1.registerWithLino("Musa Ibrahim", "musa_disk", "MusaPassword123")
        assertTrue("Registration of Musa must succeed", regResult.isSuccess)
        val registeredMusa = regResult.getOrThrow()
        val originalLenoId = registeredMusa.linoId
        val originalUserId = registeredMusa.userId

        assertTrue("Leno ID should start with LEN-", originalLenoId.startsWith("LEN-"))
        assertEquals("Leno ID should be 12 chars (LEN- + 8 digits)", 12, originalLenoId.length)

        // Close app / simulate process termination
        db1.close()

        // 2. App Launch 2: New database & repository instance loading the SAME disk database file
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, "test_real_disk_persistence.db")
            .allowMainThreadQueries()
            .build()
        val repo2 = LenoRepository(db2, context)

        // Verify Musa is found by:
        // a) exact lowercase username
        val loginUsername = repo2.loginWithLino("musa_disk", "MusaPassword123")
        assertTrue("Login via 'musa_disk' must succeed after reload", loginUsername.isSuccess)
        val userFromLogin = loginUsername.getOrThrow()
        assertEquals(originalUserId, userFromLogin.userId)
        assertEquals(originalLenoId, userFromLogin.linoId)
        assertEquals("Musa Ibrahim", userFromLogin.displayName)

        // b) uppercase / mixed case username
        val loginUpper = repo2.loginWithLino("MUSA_DISK", "MusaPassword123")
        assertTrue("Login via 'MUSA_DISK' must succeed", loginUpper.isSuccess)

        // c) username with @ prefix
        val loginAt = repo2.loginWithLino("@musa_disk", "MusaPassword123")
        assertTrue("Login via '@musa_disk' must succeed", loginAt.isSuccess)

        // d) exact 8-digit Leno ID
        val loginLenoId = repo2.loginWithLino(originalLenoId, "MusaPassword123")
        assertTrue("Login via Leno ID ($originalLenoId) must succeed", loginLenoId.isSuccess)

        // e) Leno ID without prefix
        val rawLenoNumber = originalLenoId.removePrefix("LEN-")
        val loginRawNum = repo2.loginWithLino(rawLenoNumber, "MusaPassword123")
        assertTrue("Login via raw Leno digits ($rawLenoNumber) must succeed", loginRawNum.isSuccess)

        // f) full display name
        val loginDisplayName = repo2.loginWithLino("Musa Ibrahim", "MusaPassword123")
        assertTrue("Login via Display Name 'Musa Ibrahim' must succeed", loginDisplayName.isSuccess)

        db2.close()
        testDbFile.delete()
        Unit
    }

    @Test
    fun testTwoAccountIsolationPersistenceUniquenessAndSearchFlow(): Unit = runBlocking {
        // Step 1: Create Account A, save and change its profile, log out
        val regAResult = repository.registerWithLino(
            fullName = "Sarah Connor",
            username = "sarahconnor",
            password = "SarahSecurePass2026"
        )
        assertTrue("Account A registration must succeed", regAResult.isSuccess)
        val accountA = regAResult.getOrThrow()
        val uidA = accountA.userId
        val usernameA = accountA.username
        val lenoIdA = accountA.linoId

        // Update Account A's profile with custom details
        val updateAResult = repository.updateProfile(
            userId = uidA,
            displayName = "Sarah C. (Verified)",
            username = usernameA,
            bio = "Protecting the future. Active on Leno.",
            avatarUrl = "https://example.com/sarah_avatar.jpg",
            statusMessage = "Ready for anything",
            phoneNumber = "+14155550199"
        )
        assertTrue("Account A profile update must succeed", updateAResult.isSuccess)

        // Verify Account A is current user
        val currentUserA = database.userDao().getCurrentUser().first()
        assertEquals(uidA, currentUserA?.userId)
        assertEquals("Sarah C. (Verified)", currentUserA?.displayName)

        // Log out Account A
        repository.logout(uidA)
        val noUserAfterLogoutA = database.userDao().getCurrentUser().first()
        assertNull("Current user must be null after logout", noUserAfterLogoutA)

        // Step 2: Create Account B with a different username, confirm B does not see A's contacts/profile/data
        val regBResult = repository.registerWithLino(
            fullName = "John Connor",
            username = "johnconnor",
            password = "JohnSecurePass2026"
        )
        assertTrue("Account B registration must succeed", regBResult.isSuccess)
        val accountB = regBResult.getOrThrow()
        val uidB = accountB.userId

        // Confirm B is active current user and does NOT have A's data
        val currentUserB = database.userDao().getCurrentUser().first()
        assertNotNull(currentUserB)
        assertEquals(uidB, currentUserB?.userId)
        assertEquals("johnconnor", currentUserB?.username)
        assertEquals("John Connor", currentUserB?.displayName)
        assertNotEquals(uidA, currentUserB?.userId)

        // Confirm B has 0 contacts (isolated from A)
        val contactsB = database.userDao().getContactsForUser(uidB).first()
        assertTrue("Account B must not see Account A's contacts", contactsB.isEmpty())

        // Step 3: Attempt to register A's username and Leno ID from B / new registration -> must be rejected
        val duplicateUsernameAttempt = repository.registerWithLino(
            fullName = "Imposter Sarah",
            username = "sarahconnor",
            password = "AnotherPassword123"
        )
        assertTrue("Attempt to register already-owned username must fail", duplicateUsernameAttempt.isFailure)

        val checkAvailabilityResult = repository.checkUsernameAvailability("sarahconnor")
        assertTrue("Username check must fail for taken username", checkAvailabilityResult.isFailure)

        // Step 4: Search for Account A from Account B and confirm A's public profile is loaded correctly
        val searchForAResult = repository.searchUserByLinoOrUsername("sarahconnor")
        assertTrue("Search for Account A must succeed", searchForAResult.isSuccess)
        val foundUserA = searchForAResult.getOrThrow()
        assertEquals(uidA, foundUserA.userId)
        assertEquals("sarahconnor", foundUserA.username)
        assertEquals("Sarah C. (Verified)", foundUserA.displayName)
        assertEquals(lenoIdA, foundUserA.linoId)
        assertEquals("Protecting the future. Active on Leno.", foundUserA.bio)
        assertEquals("https://example.com/sarah_avatar.jpg", foundUserA.avatarUrl)
        assertEquals("Ready for anything", foundUserA.statusMessage)
        // Public profile safety: sensitive fields must not be exposed
        assertEquals("", foundUserA.password)

        // Search for Account A by Leno ID from B
        val searchByLenoIdResult = repository.searchUserByLinoOrUsername(lenoIdA)
        assertTrue("Search by Leno ID must succeed", searchByLenoIdResult.isSuccess)
        assertEquals(uidA, searchByLenoIdResult.getOrThrow().userId)

        // Step 5: Log out B, log back into A, confirm A's previously saved data is fully intact
        repository.logout(uidB)
        val loginAResult = repository.loginWithLino("sarahconnor", "SarahSecurePass2026")
        assertTrue("Log back into Account A must succeed", loginAResult.isSuccess)
        val restoredA = loginAResult.getOrThrow()
        assertEquals(uidA, restoredA.userId)
        assertEquals("sarahconnor", restoredA.username)
        assertEquals("Sarah C. (Verified)", restoredA.displayName)
        assertEquals(lenoIdA, restoredA.linoId)
        assertEquals("Protecting the future. Active on Leno.", restoredA.bio)
        assertEquals("https://example.com/sarah_avatar.jpg", restoredA.avatarUrl)
        assertEquals("Ready for anything", restoredA.statusMessage)
        assertTrue("Account A must be active current user", restoredA.isCurrentUser)
    }

    @Test
    fun testFullAuthenticationLifecycle(): Unit = runBlocking {
        // 1. Registration
        val regRes = repository.registerWithLino(
            fullName = "Marcus Aurelius",
            username = "marcusaurelius",
            password = "Meditations2026"
        )
        assertTrue("Registration must succeed", regRes.isSuccess)
        val registeredUser = regRes.getOrThrow()
        val uid = registeredUser.userId
        val lenoId = registeredUser.linoId

        // 2. Logout
        repository.logout(uid)
        assertNull("Current user must be null after logout", database.userDao().getCurrentUser().first())

        // 3. Login again
        val loginRes1 = repository.loginWithLino("marcusaurelius", "Meditations2026")
        assertTrue("Login after logout must succeed", loginRes1.isSuccess)
        val loggedIn1 = loginRes1.getOrThrow()
        assertEquals(uid, loggedIn1.userId)
        assertEquals(lenoId, loggedIn1.linoId)
        assertEquals("Marcus Aurelius", loggedIn1.displayName)

        // 4. Profile update
        val updatedBio = "Philosopher and Roman Emperor. Leno power user."
        val updatedName = "Marcus A. (Updated)"
        val updatedStatus = "Writing Meditations 📜"
        val updatedAvatar = "https://example.com/marcus.png"

        val updateRes = repository.updateProfile(
            userId = uid,
            displayName = updatedName,
            username = "marcusaurelius",
            bio = updatedBio,
            avatarUrl = updatedAvatar,
            statusMessage = updatedStatus
        )
        assertTrue("Profile update must succeed", updateRes.isSuccess)

        // 5. Logout again
        repository.logout(uid)
        assertNull("Current user must be null after second logout", database.userDao().getCurrentUser().first())

        // 6. Login again
        val loginRes2 = repository.loginWithLino(lenoId, "Meditations2026")
        assertTrue("Second login using Leno ID must succeed", loginRes2.isSuccess)
        val loggedIn2 = loginRes2.getOrThrow()

        // 7. Confirm the updated information is still there
        assertEquals("UID must remain unchanged", uid, loggedIn2.userId)
        assertEquals("Leno ID must remain unchanged", lenoId, loggedIn2.linoId)
        assertEquals("Username must remain unchanged", "marcusaurelius", loggedIn2.username)
        assertEquals("Updated display name must persist", updatedName, loggedIn2.displayName)
        assertEquals("Updated bio must persist", updatedBio, loggedIn2.bio)
        assertEquals("Updated avatar URL must persist", updatedAvatar, loggedIn2.avatarUrl)
        assertEquals("Updated status message must persist", updatedStatus, loggedIn2.statusMessage)
        assertTrue("Must be marked as current user", loggedIn2.isCurrentUser)
    }
}
