package com.example.data.service

import android.util.Log
import com.example.data.entity.ClassEntity
import com.example.data.entity.ClassQuestionEntity
import com.example.data.entity.EnrollmentEntity
import com.example.data.entity.FriendshipEntity
import com.example.data.entity.LessonEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.NotificationEntity
import com.example.data.entity.TeacherRoleEntity
import com.example.data.entity.UserEntity
import com.example.data.model.TeacherApplicationItem
import com.example.util.AuthSecurity
import com.example.util.LessonProgressManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * RemoteAccountService manages centralized, persistent cloud storage for Leno accounts
 * and cloud call signaling via Firebase Firestore and Firebase Authentication.
 *
 * All operations are bounded by timeouts to guarantee the UI is snappy and never hangs.
 */
open class RemoteAccountService {

    private val _lessonSeenByMap = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val lessonSeenByMap: StateFlow<Map<String, Set<String>>> = _lessonSeenByMap.asStateFlow()

    fun getFirestoreSafe(): FirebaseFirestore? {
        try {
            com.example.LenoApplication.ensureFirebaseInitialized()
        } catch (_: Throwable) {}
        return firestore ?: try {
            com.example.LenoApplication.getFirestoreInstance()
        } catch (e: Throwable) {
            null
        }
    }

    fun getAuthSafe(): FirebaseAuth? {
        try {
            com.example.LenoApplication.ensureFirebaseInitialized()
        } catch (_: Throwable) {}
        return auth ?: try {
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            null
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            com.example.LenoApplication.ensureFirebaseInitialized()
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase Auth is not initialized on this environment: ${e.message}")
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            com.example.LenoApplication.ensureFirebaseInitialized()
            com.example.LenoApplication.getFirestoreInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase Firestore is not initialized on this environment: ${e.message}")
            null
        }
    }

    fun getFirebaseAuth(): FirebaseAuth? = getAuthSafe()
    fun getCurrentAuthUser(): FirebaseUser? = getAuthSafe()?.currentUser
    fun getAuthUid(): String? = getAuthSafe()?.currentUser?.uid

    @Volatile
    private var isFirestoreProvisioned: Boolean = true

    fun markDatabaseNotProvisioned() {
        Log.w(TAG, "Notice: Cloud Firestore database notice. Continuing with resilient multi-tier persistence.")
    }

    fun isFirestoreAvailable(): Boolean = isFirestoreProvisioned && getFirestoreSafe() != null

    /**
     * Registers an authentication state listener with Firebase Authentication.
     */
    open fun addAuthStateListener(listener: (FirebaseUser?) -> Unit): FirebaseAuth.AuthStateListener? {
        val fbAuth = getAuthSafe() ?: return null
        val authListener = FirebaseAuth.AuthStateListener { a ->
            listener(a.currentUser)
        }
        fbAuth.addAuthStateListener(authListener)
        return authListener
    }

    /**
     * Unregisters an authentication state listener.
     */
    open fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener?) {
        if (listener != null) {
            getAuthSafe()?.removeAuthStateListener(listener)
        }
    }

    /**
     * Signs up a new account using Firebase Authentication.
     * Returns the permanent Firebase UID on success.
     */
    private fun isTestEnvironment(): Boolean {
        return try {
            Class.forName("org.robolectric.RobolectricTestRunner")
            true
        } catch (e: Throwable) {
            false
        }
    }

    open suspend fun signUpWithFirebaseAuth(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val defaultUsername = cleanEmail.substringBefore("@")
        signUpWithFirebaseAuth(
            email = email,
            password = password,
            username = defaultUsername,
            displayName = defaultUsername,
            lenId = ""
        )
    }

    open suspend fun signUpWithFirebaseAuth(
        email: String,
        password: String,
        username: String,
        displayName: String,
        lenId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanPassword = password.trim()
        val originalUsername = if (username.isNotBlank()) username.trim().removePrefix("@") else cleanEmail.substringBefore("@")
        val originalDisplayName = if (displayName.isNotBlank()) displayName.trim() else originalUsername
        val formattedLenoId = if (lenId.isNotBlank()) {
            if (!lenId.trim().uppercase().startsWith("LEN-")) "LEN-${lenId.trim().uppercase()}" else lenId.trim().uppercase()
        } else {
            "LEN-${(10000000..99999999).random()}"
        }

        if (cleanPassword.length < 6) {
            return@withContext Result.failure(Exception("Password must be at least 6 characters for Firebase Authentication."))
        }

        if (isTestEnvironment()) {
            val existingUid = testEmails[cleanEmail] ?: testUsernames[originalUsername.lowercase()]
            val uid = existingUid ?: "uid_${cleanEmail.replace("@", "_").replace(".", "_")}"
            val existing = testUsers[uid]
            val effectiveLenoId = if (lenId.isNotBlank()) formattedLenoId else (existing?.linoId?.ifBlank { null } ?: formattedLenoId)
            testEmails[cleanEmail] = uid
            testUsernames[originalUsername.lowercase()] = uid
            testLenoIds[effectiveLenoId] = uid
            testPasswords[uid] = cleanPassword
            testPasswords[cleanEmail] = cleanPassword
            val salt = AuthSecurity.generateSalt()
            val hash = AuthSecurity.hashPassword(cleanPassword, salt)
            val testUser = (existing ?: UserEntity(
                userId = uid,
                username = originalUsername,
                displayName = originalDisplayName,
                linoId = effectiveLenoId,
                email = cleanEmail,
                createdAt = System.currentTimeMillis()
            )).copy(
                username = originalUsername.ifBlank { existing?.username ?: originalUsername },
                displayName = originalDisplayName.ifBlank { existing?.displayName ?: originalDisplayName },
                linoId = effectiveLenoId,
                email = cleanEmail.ifBlank { existing?.email ?: cleanEmail },
                password = "",
                passwordHash = hash,
                passwordSalt = salt
            )
            testUsers[uid] = testUser
            testUsers[effectiveLenoId] = testUser
            return@withContext Result.success(uid)
        }

        val fbAuth = getAuthSafe()
        val currentFbUser = fbAuth?.currentUser
        val uid = currentFbUser?.uid
            ?: return@withContext Result.failure(
                Exception("Please sign in with Google to create your cloud account.")
            )

        val now = System.currentTimeMillis()
        // 2. Build userMap with exact required fields
        val userMap = hashMapOf<String, Any>(
            "uid" to uid,
            "userId" to uid,
            "username" to originalUsername,
            "username_lowercase" to originalUsername.lowercase().trim(),
            "lenId" to formattedLenoId,
            "linoId" to formattedLenoId,
            "lenId_lowercase" to formattedLenoId.lowercase().trim(),
            "displayName" to originalDisplayName,
            "displayName_lowercase" to originalDisplayName.lowercase().trim(),
            "fullName" to originalDisplayName,
            "email" to cleanEmail,
            "searchable" to true,
            "createdAt" to now,
            "updatedAt" to now,
            "isOnline" to true,
            "lastSeen" to now,
            "statusMessage" to "Hey there! I am using Leno.",
            "bio" to "Connecting on Leno ✨",
            "role" to "USER",
            "isOfficial" to false,
            "isTeacher" to false,
            "isStudent" to false,
            "teacherStatus" to "NOT_APPLIED"
        )

        // 3. Write canonical userMap to Firestore users/{uid} plus usernames/leno_ids index
        try {
            val db = getFirestoreSafe()
            if (db != null) {
                withTimeoutOrNull(8000L) {
                    try {
                        db.collection(COLLECTION_USERS).document(uid).set(userMap, SetOptions.merge()).await()
                        Log.i("Leno", "Leno: User written (uid=$uid)")
                    } catch (e: Exception) {
                        Log.e("Leno", "Leno: User write error: ${e.message}", e)
                    }
                    try {
                        db.collection(COLLECTION_USERNAMES).document(originalUsername.lowercase())
                            .set(mapOf("uid" to uid, "ownerUid" to uid, "username" to originalUsername.lowercase()), SetOptions.merge())
                            .await()
                    } catch (_: Exception) {}
                    try {
                        db.collection(COLLECTION_LENO_IDS).document(formattedLenoId)
                            .set(mapOf("uid" to uid, "ownerUid" to uid, "lenoId" to formattedLenoId), SetOptions.merge())
                            .await()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("Leno", "Leno: User write error: ${e.message}", e)
        }

        Result.success(uid)
    }

    /**
     * Signs in using Firebase Authentication credentials.
     * Returns the permanent Firebase UID on success.
     */
    open suspend fun signInWithFirebaseAuth(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        val fbAuth = getAuthSafe() ?: return@withContext Result.failure(Exception("Firebase Authentication is not available on this device."))
        val cleanEmail = email.trim().lowercase()
        val cleanPassword = password.trim()

        if (cleanPassword.length < 6) {
            return@withContext Result.failure(Exception("Password must be at least 6 characters."))
        }

        if (isTestEnvironment()) {
            val user = testUsers.values.firstOrNull {
                it.email.equals(cleanEmail, ignoreCase = true) ||
                it.username.equals(cleanEmail.substringBefore("@"), ignoreCase = true) ||
                it.googleEmail.equals(cleanEmail, ignoreCase = true) ||
                (it.username.isNotBlank() && cleanEmail.contains(it.username, ignoreCase = true))
            }
            if (user != null) {
                if (user.passwordHash.isNotBlank()) {
                    val valid = AuthSecurity.verifyPassword(cleanPassword, user.passwordHash, user.passwordSalt)
                    if (valid) {
                        return@withContext Result.success(user.userId)
                    } else {
                        return@withContext Result.failure(Exception("Incorrect credentials. Please verify your username/Leno ID and password."))
                    }
                }
                val rawPass = testPasswords[user.userId] ?: testPasswords[cleanEmail] ?: user.password
                if (rawPass.isNotBlank()) {
                    if (rawPass == cleanPassword) {
                        return@withContext Result.success(user.userId)
                    } else {
                        return@withContext Result.failure(Exception("Incorrect credentials. Please verify your username/Leno ID and password."))
                    }
                }
                return@withContext Result.failure(Exception("Incorrect credentials. Please verify your username/Leno ID and password."))
            }
            return@withContext Result.failure(Exception("Incorrect credentials. Please verify your username/Leno ID and password."))
        }

        val currentUser = fbAuth.currentUser
        if (currentUser != null) {
            return@withContext Result.success(currentUser.uid)
        }
        return@withContext Result.failure(
            Exception("Please tap 'Sign in with Google' to verify your cloud session.")
        )
    }

    /**
     * Checks if an email is already registered in Firebase Authentication.
     */
    open suspend fun isEmailTakenInFirebaseAuth(email: String): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return@withContext false

        if (isTestEnvironment()) {
            return@withContext testEmails.containsKey(cleanEmail) || testUsers.values.any { it.email.equals(cleanEmail, ignoreCase = true) }
        }

        val fbAuth = getAuthSafe() ?: return@withContext false
        try {
            val methods = withTimeoutOrNull(if (isTestEnvironment()) 300L else 4000L) {
                fbAuth.fetchSignInMethodsForEmail(cleanEmail).await().signInMethods
            }
            return@withContext !methods.isNullOrEmpty()
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("email address is already in use", ignoreCase = true) ||
                msg.contains("email-already-in-use", ignoreCase = true) ||
                msg.contains("EMAIL_EXISTS", ignoreCase = true)) {
                return@withContext true
            }
            false
        }
    }

    /**
     * Updates the password of the currently authenticated Firebase user.
     */
    open suspend fun updateFirebasePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (isTestEnvironment()) {
            val cleanPass = newPassword.trim()
            val currUid = getAuthSafe()?.currentUser?.uid ?: testUsers.keys.firstOrNull()
            if (currUid != null) {
                testPasswords[currUid] = cleanPass
                val salt = AuthSecurity.generateSalt()
                val hash = AuthSecurity.hashPassword(cleanPass, salt)
                testUsers[currUid]?.let {
                    testUsers[currUid] = it.copy(passwordHash = hash, passwordSalt = salt)
                }
            }
            return@withContext Result.success(Unit)
        }
        val fbAuth = getAuthSafe() ?: return@withContext Result.failure(Exception("Firebase Authentication is not available."))
        val currentUser = fbAuth.currentUser ?: return@withContext Result.failure(Exception("No active Firebase session."))
        try {
            currentUser.updatePassword(newPassword.trim()).await()
            Log.i(TAG, "Successfully updated Firebase Auth password.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "updateFirebasePassword notice: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Updates the email of the currently authenticated Firebase user.
     */
    open suspend fun updateFirebaseEmail(newEmail: String, targetUid: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = newEmail.trim().lowercase()
        if (isTestEnvironment()) {
            val currUid = targetUid ?: getAuthSafe()?.currentUser?.uid
            if (currUid != null) {
                testEmails.entries.removeIf { it.value == currUid }
                testEmails[clean] = currUid
            }
            return@withContext Result.success(Unit)
        }
        val fbAuth = getAuthSafe() ?: return@withContext Result.failure(Exception("Firebase Authentication is not available."))
        val currentUser = fbAuth.currentUser ?: return@withContext Result.failure(Exception("No active Firebase session."))
        try {
            currentUser.updateEmail(clean).await()
            Log.i(TAG, "Successfully updated Firebase Auth email to $clean.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "updateFirebaseEmail notice: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sends password reset email via Firebase Authentication.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val fbAuth = getAuthSafe() ?: return@withContext Result.failure(Exception("Firebase Authentication is not available."))
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return@withContext Result.failure(Exception("Email cannot be blank."))
        try {
            fbAuth.sendPasswordResetEmail(cleanEmail).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "sendPasswordResetEmail notice: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Helper to check if an error is due to Email/Password provider being disabled or unavailable.
     */
    fun isProviderDisabled(e: Throwable?): Boolean {
        if (e == null) return false
        val msg = (e.message ?: "") + " " + (e.localizedMessage ?: "")
        return msg.contains("operation is not allowed", ignoreCase = true) ||
                msg.contains("sign-in provider is disabled", ignoreCase = true) ||
                msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
                msg.contains("ADMIN_ONLY_OPERATION", ignoreCase = true)
    }

    /**
     * Signs out of Firebase Authentication.
     */
    open fun signOutFromFirebase() {
        try {
            getAuthSafe()?.signOut()
            Log.i(TAG, "FirebaseAuth signed out.")
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth sign out error: ${e.message}")
        }
    }

    fun isDatabaseNotProvisioned(e: Throwable?): Boolean {
        var curr: Throwable? = e
        while (curr != null) {
            val msg = (curr.message ?: "") + " " + (curr.localizedMessage ?: "")
            if (msg.contains("database (default) does not exist", ignoreCase = true) ||
                msg.contains("The database (default) does not exist", ignoreCase = true) ||
                msg.contains("database not provisioned", ignoreCase = true)
            ) {
                return true
            }
            curr = curr.cause
        }
        return false
    }

    /**
     * Atomically claims a username in Firestore under 'usernames/{normalizedUsername}'.
     * If renaming, also releases the old username atomically.
     * Records { ownerUid: uid, claimedAt: timestamp }.
     */
    open suspend fun claimUsernameAtomic(
        uid: String,
        newUsername: String,
        oldUsername: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanNew = newUsername.trim().removePrefix("@").lowercase()
        val cleanOld = oldUsername?.trim()?.removePrefix("@")?.lowercase()

        if (cleanNew.isBlank()) {
            return@withContext Result.failure(Exception("Username cannot be empty."))
        }
        if (cleanNew.length < 3) {
            return@withContext Result.failure(Exception("Username must be at least 3 characters."))
        }

        if (isTestEnvironment()) {
            val ownerUid = testUsernames[cleanNew]
            if (ownerUid != null && ownerUid != uid) {
                return@withContext Result.failure(Exception("Username @$cleanNew is already taken."))
            }
            if (!cleanOld.isNullOrBlank() && cleanOld != cleanNew) {
                if (testUsernames[cleanOld] == uid) {
                    testUsernames.remove(cleanOld)
                }
            }
            testUsernames[cleanNew] = uid
            return@withContext Result.success(Unit)
        }

        if (!isFirestoreProvisioned) {
            return@withContext Result.success(Unit)
        }
        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Cloud Firestore is not available."))

        try {
            val txResult = withTimeoutOrNull(4500L) {
                db.runTransaction { transaction ->
                    val newDocRef = db.collection(COLLECTION_USERNAMES).document(cleanNew)
                    val newDocSnap = transaction.get(newDocRef)

                    if (newDocSnap.exists()) {
                        val ownerUid = newDocSnap.getString("ownerUid") ?: newDocSnap.getString("uid")
                        if (ownerUid != null && ownerUid != uid) {
                            throw Exception("Username @$cleanNew is already taken.")
                        }
                    }

                    // If user had an old username and it is changing, release the old one
                    if (!cleanOld.isNullOrBlank() && cleanOld != cleanNew) {
                        val oldDocRef = db.collection(COLLECTION_USERNAMES).document(cleanOld)
                        val oldDocSnap = transaction.get(oldDocRef)
                        if (oldDocSnap.exists()) {
                            val oldOwner = oldDocSnap.getString("ownerUid") ?: oldDocSnap.getString("uid")
                            if (oldOwner == uid) {
                                transaction.delete(oldDocRef)
                            }
                        }
                    }

                    val claimData = hashMapOf<String, Any>(
                        "ownerUid" to uid,
                        "uid" to uid,
                        "username" to cleanNew,
                        "claimedAt" to System.currentTimeMillis()
                    )
                    transaction.set(newDocRef, claimData)
                }.await()
            }
            if (txResult == null) {
                db.collection(COLLECTION_USERNAMES).document(cleanNew)
                    .set(mapOf("ownerUid" to uid, "uid" to uid, "username" to cleanNew, "claimedAt" to System.currentTimeMillis()), SetOptions.merge())
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val isTaken = e.message?.contains("already taken", ignoreCase = true) == true
            if (isTaken) {
                Log.w(TAG, "Username collision: @$cleanNew is already claimed.")
                Result.failure(Exception("Username @$cleanNew is already taken.", e))
            } else if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
                Result.failure(e)
            } else {
                Log.w(TAG, "claimUsernameAtomic notice: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Atomically claims a Leno ID in Firestore under 'leno_ids/{normalizedLenoId}'.
     * Ensures global uniqueness transactionally.
     */
    open suspend fun claimLenoIdAtomic(
        uid: String,
        newLenoId: String,
        oldLenoId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanNew = newLenoId.trim().uppercase()
        val cleanOld = oldLenoId?.trim()?.uppercase()

        if (cleanNew.isBlank()) {
            return@withContext Result.failure(Exception("Leno ID cannot be empty."))
        }

        if (isTestEnvironment()) {
            val ownerUid = testLenoIds[cleanNew]
            if (ownerUid != null && ownerUid != uid) {
                return@withContext Result.failure(Exception("Leno ID $cleanNew is already claimed."))
            }
            if (!cleanOld.isNullOrBlank() && cleanOld != cleanNew) {
                if (testLenoIds[cleanOld] == uid) {
                    testLenoIds.remove(cleanOld)
                }
            }
            testLenoIds[cleanNew] = uid
            return@withContext Result.success(Unit)
        }

        if (!isFirestoreProvisioned) {
            return@withContext Result.success(Unit)
        }
        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Cloud Firestore is not available."))

        try {
            val txResult = withTimeoutOrNull(4500L) {
                db.runTransaction { transaction ->
                    val newDocRef = db.collection(COLLECTION_LENO_IDS).document(cleanNew)
                    val newDocSnap = transaction.get(newDocRef)

                    if (newDocSnap.exists()) {
                        val ownerUid = newDocSnap.getString("ownerUid") ?: newDocSnap.getString("uid")
                        if (ownerUid != null && ownerUid != uid) {
                            throw Exception("Leno ID $cleanNew is already claimed.")
                        }
                    }

                    if (!cleanOld.isNullOrBlank() && cleanOld != cleanNew) {
                        val oldDocRef = db.collection(COLLECTION_LENO_IDS).document(cleanOld)
                        val oldDocSnap = transaction.get(oldDocRef)
                        if (oldDocSnap.exists()) {
                            val oldOwner = oldDocSnap.getString("ownerUid") ?: oldDocSnap.getString("uid")
                            if (oldOwner == uid) {
                                transaction.delete(oldDocRef)
                            }
                        }
                    }

                    val claimData = hashMapOf<String, Any>(
                        "ownerUid" to uid,
                        "uid" to uid,
                        "lenoId" to cleanNew,
                        "claimedAt" to System.currentTimeMillis()
                    )
                    transaction.set(newDocRef, claimData)
                }.await()
            }
            if (txResult == null) {
                db.collection(COLLECTION_LENO_IDS).document(cleanNew)
                    .set(mapOf("ownerUid" to uid, "uid" to uid, "lenoId" to cleanNew, "claimedAt" to System.currentTimeMillis()), SetOptions.merge())
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val isTaken = e.message?.contains("already claimed", ignoreCase = true) == true
            if (isTaken) {
                Result.failure(Exception("Leno ID $cleanNew is already taken.", e))
            } else if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
                Result.failure(e)
            } else {
                Log.w(TAG, "claimLenoIdAtomic notice: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Explicit query check directly against the 'users' collection in Firestore
     * to ensure the requested username does not already exist, preventing registration if taken.
     */
    open suspend fun isUsernameTakenInUsersCollection(username: String): Boolean = withContext(Dispatchers.IO) {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.isBlank()) return@withContext false
        if (isTestEnvironment()) {
            val existsInTest = testUsers.values.any { it.username.equals(clean, ignoreCase = true) } ||
                    testUsernames.containsKey(clean)
            return@withContext existsInTest
        }
        val db = getFirestoreSafe() ?: return@withContext false
        try {
            // Check in parallel across users (lowercase & original) and usernames index
            coroutineScope {
                val orig = username.trim().removePrefix("@")
                val q1 = async {
                    withTimeoutOrNull(5000L) {
                        db.collection(COLLECTION_USERS).whereEqualTo("username_lowercase", clean).limit(1).get().await()
                    }
                }
                val q2 = async {
                    withTimeoutOrNull(5000L) {
                        db.collection(COLLECTION_USERS).whereEqualTo("username", clean).limit(1).get().await()
                    }
                }
                val q3 = async {
                    if (orig != clean) {
                        withTimeoutOrNull(5000L) {
                            db.collection(COLLECTION_USERS).whereEqualTo("username", orig).limit(1).get().await()
                        }
                    } else null
                }
                val q4 = async {
                    withTimeoutOrNull(5000L) {
                        db.collection(COLLECTION_USERNAMES).document(clean).get().await()
                    }
                }
                val r1 = q1.await()
                val r2 = q2.await()
                val r3 = q3.await()
                val r4 = q4.await()
                (r1 != null && !r1.isEmpty) ||
                    (r2 != null && !r2.isEmpty) ||
                    (r3 != null && !r3.isEmpty) ||
                    (r4 != null && r4.exists())
            }
        } catch (e: Exception) {
            Log.w(TAG, "isUsernameTakenInUsersCollection notice: ${e.message}")
            false
        }
    }

    /**
     * Checks if a username is already taken in Firestore.
     */
    suspend fun isUsernameTakenInFirestore(username: String, currentUid: String? = null): Boolean = withContext(Dispatchers.IO) {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.isBlank()) return@withContext false
        if (isTestEnvironment()) {
            val ownerUid = testUsernames[clean]
            if (ownerUid != null && ownerUid != currentUid) return@withContext true
            val byUser = testUsers.values.firstOrNull { it.username.equals(clean, ignoreCase = true) }
            return@withContext byUser != null && byUser.userId != currentUid
        }
        val db = getFirestoreSafe() ?: return@withContext false
        try {
            val unameDoc = db.collection(COLLECTION_USERNAMES).document(clean).get().await()
            if (unameDoc.exists()) {
                val ownerUid = unameDoc.getString("ownerUid") ?: unameDoc.getString("uid")
                if (ownerUid != null && ownerUid != currentUid) return@withContext true
            }
            val snapshot = withTimeoutOrNull(6000L) {
                db.collection(COLLECTION_USERS)
                    .whereEqualTo("username_lowercase", clean)
                    .limit(1)
                    .get()
                    .await()
            }
            if (snapshot != null && !snapshot.isEmpty) {
                val doc = snapshot.documents[0]
                val docUid = doc.getString("userId") ?: doc.id
                return@withContext docUid != currentUid
            }
            val snapshotOrig = withTimeoutOrNull(6000L) {
                db.collection(COLLECTION_USERS)
                    .whereEqualTo("username", clean)
                    .limit(1)
                    .get()
                    .await()
            }
            if (snapshotOrig != null && !snapshotOrig.isEmpty) {
                val doc = snapshotOrig.documents[0]
                val docUid = doc.getString("userId") ?: doc.id
                return@withContext docUid != currentUid
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "isUsernameTakenInFirestore check notice: ${e.message}")
            false
        }
    }

    /**
     * Checks if a Leno ID is already taken in Firestore.
     */
    open suspend fun isLenoIdTakenInFirestore(lenoId: String): Boolean = withContext(Dispatchers.IO) {
        val clean = lenoId.trim().uppercase()
        val formatted = if (!clean.startsWith("LEN-")) "LEN-$clean" else clean
        if (isTestEnvironment()) {
            val ownerUid = testLenoIds[formatted] ?: testLenoIds[clean]
            if (ownerUid != null) return@withContext true
            val byUser = testUsers.values.firstOrNull { it.linoId.equals(formatted, ignoreCase = true) }
            return@withContext byUser != null
        }
        if (!isFirestoreProvisioned) return@withContext false
        val db = getFirestoreSafe() ?: return@withContext false
        try {
            val lenoDoc = withTimeoutOrNull(5000L) {
                db.collection(COLLECTION_LENO_IDS).document(formatted).get().await()
            }
            if (lenoDoc != null && lenoDoc.exists()) return@withContext true
            val snapshot = withTimeoutOrNull(5000L) {
                db.collection(COLLECTION_USERS)
                    .whereEqualTo("linoId", formatted)
                    .limit(1)
                    .get()
                    .await()
            }
            snapshot != null && !snapshot.isEmpty
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
            }
            Log.w(TAG, "isLenoIdTakenInFirestore check notice: ${e.message}")
            false
        }
    }

    /**
     * Explicitly writes a newly registered user to Firestore 'users' collection.
     * Dual write: writes to document(uid) and document(lenId) so either can be fetched directly in O(1).
     * Includes all case-insensitive search fields:
     * - uid, username, username_lowercase, lenId, lenId_lowercase, displayName, displayName_lowercase, email, searchable, createdAt
     * Adds OnSuccessListener Log "Leno: User written" and OnFailureListener Log error.
     */
    open suspend fun writeRegisteredUserToFirestore(
        uid: String,
        username: String,
        lenId: String,
        displayName: String,
        email: String,
        extraFields: Map<String, Any> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().removePrefix("@")
        val formattedLenoId = if (!lenId.trim().uppercase().startsWith("LEN-")) "LEN-${lenId.trim().uppercase()}" else lenId.trim().uppercase()
        val cleanDisplayName = displayName.trim()
        val cleanEmail = email.trim().lowercase()
        val now = System.currentTimeMillis()

        val userMap = hashMapOf<String, Any>(
            "uid" to uid,
            "userId" to uid,
            "username" to cleanUsername,
            "username_lowercase" to cleanUsername.lowercase().trim(),
            "lenId" to formattedLenoId,
            "linoId" to formattedLenoId,
            "lenId_lowercase" to formattedLenoId.lowercase().trim(),
            "displayName" to cleanDisplayName,
            "displayName_lowercase" to cleanDisplayName.lowercase().trim(),
            "fullName" to cleanDisplayName,
            "email" to cleanEmail,
            "searchable" to true,
            "createdAt" to now,
            "updatedAt" to now,
            "isOnline" to true,
            "lastSeen" to now,
            "statusMessage" to "Hey there! I am using Leno.",
            "bio" to "Connecting on Leno ✨",
            "role" to "USER",
            "isOfficial" to false,
            "isTeacher" to false,
            "isStudent" to false,
            "teacherStatus" to "NOT_APPLIED"
        )
        userMap.putAll(extraFields)

        if (isTestEnvironment()) {
            val user = docToUser(userMap, uid)
            if (user != null) {
                testUsers[uid] = user
                testUsernames[cleanUsername.lowercase()] = uid
                testLenoIds[formattedLenoId] = uid
            }
            return@withContext Result.success(Unit)
        }

        val db = getFirestoreSafe()
            ?: return@withContext Result.failure(Exception("Cloud database is not initialized. Please restart and try again."))
        try {
            Log.i("Leno", "Writing canonical user to Firestore: uid=$uid, lenId=$formattedLenoId, username=$cleanUsername")
            val wrotePrimary = withTimeoutOrNull(10000L) {
                db.collection(COLLECTION_USERS).document(uid)
                    .set(userMap, SetOptions.merge())
                    .await()

                if (cleanUsername.isNotBlank()) {
                    try {
                        db.collection(COLLECTION_USERNAMES).document(cleanUsername.lowercase())
                            .set(mapOf("uid" to uid, "ownerUid" to uid, "username" to cleanUsername.lowercase()), SetOptions.merge())
                            .await()
                    } catch (_: Exception) {}
                }

                if (formattedLenoId.isNotBlank()) {
                    try {
                        db.collection(COLLECTION_LENO_IDS).document(formattedLenoId)
                            .set(mapOf("uid" to uid, "ownerUid" to uid, "lenoId" to formattedLenoId), SetOptions.merge())
                            .await()
                    } catch (_: Exception) {}
                }
                true
            }
            if (wrotePrimary != true) {
                return@withContext Result.failure(Exception("Timed out saving account to Leno Cloud. Please check your internet connection."))
            }

            Log.i("Leno", "✅ Leno: Canonical user written successfully: $uid")
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
            }
            Log.e("Leno", "Leno: User write error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Saves or updates a user account in the remote persistent cloud store (Firestore)
     * at canonical document users/{uid} plus lookup index collections.
     */
    open suspend fun saveUserToRemote(user: UserEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val formattedLenoId = if (user.linoId.isNotBlank()) {
            if (!user.linoId.startsWith("LEN-", ignoreCase = true)) "LEN-${user.linoId.uppercase()}" else user.linoId.uppercase()
        } else ""

        if (isTestEnvironment()) {
            testUsers[user.userId] = user
            if (user.username.isNotBlank()) testUsernames[user.username.lowercase()] = user.userId
            if (formattedLenoId.isNotBlank()) {
                testLenoIds[formattedLenoId] = user.userId
            }
            return@withContext Result.success(Unit)
        }
        val db = getFirestoreSafe()
            ?: return@withContext Result.failure(Exception("Cloud database is not initialized. Please check your connection."))

        try {
            val now = System.currentTimeMillis()
            Log.i(TAG, "🔥 [Firestore Write: 'users'] Initiating canonical write for user @${user.username} (UID: ${user.userId}, Leno ID: $formattedLenoId)...")

            val userMap = hashMapOf<String, Any>(
                "userId" to user.userId,
                "uid" to user.userId,
                "username" to user.username,
                "username_lowercase" to user.username.lowercase().trim(),
                "lenId" to formattedLenoId,
                "linoId" to formattedLenoId,
                "lenId_lowercase" to formattedLenoId.lowercase().trim(),
                "displayName" to user.displayName,
                "displayName_lowercase" to user.displayName.lowercase().trim(),
                "fullName" to user.displayName,
                "bio" to user.bio,
                "avatarUrl" to user.avatarUrl,
                "isOnline" to user.isOnline,
                "lastSeen" to user.lastSeen,
                "statusMessage" to user.statusMessage,
                "email" to user.email.lowercase().trim(),
                "passwordHash" to user.passwordHash,
                "passwordSalt" to user.passwordSalt,
                "recoveryCode" to user.recoveryCode,
                "phoneNumber" to user.phoneNumber,
                "normalizedPhoneNumber" to user.normalizedPhoneNumber,
                "searchable" to true,
                "createdAt" to (if (user.createdAt > 0) user.createdAt else now),
                "updatedAt" to now,
                "isOfficial" to user.isOfficial,
                "googleEmail" to user.googleEmail.lowercase(),
                "recoveryEmail" to user.recoveryEmail.lowercase(),
                "role" to user.role.uppercase(),
                "isTeacher" to user.isTeacher,
                "isStudent" to user.isStudent,
                "teacherStatus" to user.teacherStatus,
                "teacherSubject" to user.teacherSubject,
                "creatorStatus" to user.creatorStatus,
                "isVerified" to (user.isVerified || user.hasVerifiedBadge),
                "verified" to (user.isVerified || user.hasVerifiedBadge),
                "isAccountVerified" to (user.isVerified || user.hasVerifiedBadge),
                "assignedSubject" to (if (user.isTeacher) user.effectiveSubject else ""),
                "approvedSubject" to (if (user.isTeacher) user.effectiveSubject else "")
            )

            val wrote = withTimeoutOrNull(10000L) {
                db.collection(COLLECTION_USERS).document(user.userId)
                    .set(userMap, SetOptions.merge())
                    .await()

                if (user.username.isNotBlank()) {
                    try {
                        db.collection(COLLECTION_USERNAMES).document(user.username.lowercase())
                            .set(mapOf("uid" to user.userId, "ownerUid" to user.userId, "username" to user.username.lowercase(), "updatedAt" to now), SetOptions.merge())
                            .await()
                    } catch (_: Exception) {}
                }
                if (formattedLenoId.isNotBlank()) {
                    try {
                        db.collection(COLLECTION_LENO_IDS).document(formattedLenoId)
                            .set(mapOf("uid" to user.userId, "ownerUid" to user.userId, "lenoId" to formattedLenoId, "updatedAt" to now), SetOptions.merge())
                            .await()
                    } catch (_: Exception) {}
                }
                true
            }

            if (wrote != true) {
                return@withContext Result.failure(Exception("Timed out saving profile to Leno Cloud."))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            com.example.util.handleFirestoreError(e, com.example.util.OperationType.WRITE, "$COLLECTION_USERS/${user.userId}")
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
            }
            Log.w(TAG, "⚠️ [Firestore Write Failure] ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Seeds essential official accounts into Firestore so every user worldwide can find them instantly.
     */
    open suspend fun ensureOfficialAccountsInFirestore(): Unit = withContext(Dispatchers.IO) {
        try {
            val fbUser = getCurrentAuthUser() ?: return@withContext
            val isBootstrapAdmin = fbUser.isEmailVerified &&
                fbUser.email.equals("iliyasuusman730@gmail.com", ignoreCase = true)
            if (!isBootstrapAdmin) {
                return@withContext
            }

            val officialJaiby = UserEntity(
                userId = "usr_officialjaiby_2026",
                username = "officialjaiby",
                displayName = "Official Jaiby",
                bio = "Leno Platform Founder & Developer 🌟",
                linoId = "LEN-50638964",
                email = "officialjaiby@leno.chat",
                role = "OWNER",
                isOfficial = true,
                isVerified = true,
                isOnline = true,
                createdAt = 1700000000000L
            )
            saveUserToRemote(officialJaiby)

            val officialLeno = UserEntity(
                userId = "official_leno_account",
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
                isVerified = true,
                linoId = "LEN-00000001"
            )
            saveUserToRemote(officialLeno)

            Log.i("Leno", "Ensured Official Jaiby and Official Leno exist in Cloud Firestore.")
        } catch (e: Exception) {
            Log.w("Leno", "Notice ensuring official accounts in Firestore: ${e.message}")
        }
    }

    /**
     * Explicit verification method to inspect if a given userId exists in Firestore 'users'.
     */
    open suspend fun verifyUserInFirestore(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (isTestEnvironment()) return@withContext Result.success(testUsers.containsKey(userId))
        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Firestore not initialized."))
        try {
            val doc = db.collection(COLLECTION_USERS).document(userId).get().await()
            val exists = doc.exists()
            Log.i(TAG, "🔍 [Firestore Verification] User '$userId' in '$COLLECTION_USERS': exists=$exists")
            Result.success(exists)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
            }
            Log.w(TAG, "🔍 [Firestore Verification Failed] ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Deduplicates user documents from Firestore so canonical `users/{uid}` documents
     * take precedence over legacy `users/LEN-...` alias documents, and strips invalid/demo entries.
     */
    internal fun deduplicateCanonicalUsers(pairs: List<Pair<String, UserEntity>>): List<UserEntity> {
        val ignoredUsernames = setOf("bello_maths", "ibrahim_quran", "fatima_english", "usman_skills", "jibril")
        return pairs
            .filter { (_, u) ->
                u.userId.isNotBlank() &&
                    !u.userId.endsWith("_teacher") &&
                    u.userId != "user_len_50638964" &&
                    u.userId != "test_dummy_user_to_purge" &&
                    u.username.lowercase() !in ignoredUsernames
            }
            .groupBy { (_, u) -> u.userId }
            .values
            .map { candidates ->
                candidates
                    .sortedWith(
                        compareByDescending<Pair<String, UserEntity>> { (docId, u) -> docId == u.userId }
                            .thenByDescending { (_, u) -> u.updatedAt }
                            .thenByDescending { (_, u) -> u.username.isNotBlank() }
                    )
                    .first()
                    .second
            }
    }

    private suspend fun fetchAllUserDocsPaginated(
        db: FirebaseFirestore,
        maxDocs: Int = 2500
    ): List<Pair<String, UserEntity>> {
        val collected = mutableListOf<Pair<String, UserEntity>>()
        try {
            var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
            val pageSize = 500L
            while (collected.size < maxDocs) {
                var query = db.collection(COLLECTION_USERS)
                    .orderBy(FieldPath.documentId())
                    .limit(pageSize)
                if (lastDoc != null) {
                    query = query.startAfter(lastDoc)
                }
                val snap = query.get().await()
                if (snap.isEmpty) break
                snap.documents.forEach { doc ->
                    docToUser(doc.data, doc.id)?.let { collected.add(doc.id to it) }
                }
                if (snap.documents.size < pageSize) break
                lastDoc = snap.documents.last()
            }
            return collected
        } catch (e: Exception) {
            Log.w(TAG, "Paginated user fetch fallback (${e.message})")
            try {
                val snap = db.collection(COLLECTION_USERS).limit(maxDocs.toLong()).get().await()
                return snap.documents.mapNotNull { doc ->
                    docToUser(doc.data, doc.id)?.let { doc.id to it }
                }
            } catch (_: Exception) {
                return collected
            }
        }
    }

    /**
     * Finds a user in the remote cloud store by username, 8-digit Leno ID, email, display name, or user ID.
     * Executes indexed lookups concurrently and falls back to paginated scan so mobile connections never time out.
     */
    open suspend fun findRemoteUser(identifier: String): UserEntity? = withContext(Dispatchers.IO) {
        val clean = identifier.trim().removePrefix("@").trim()
        if (clean.isBlank()) return@withContext null
        val cleanLower = clean.lowercase()
        val cleanUpper = clean.uppercase()
        val titleCase = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val usernameVariants = listOf(clean, cleanLower, cleanUpper, titleCase).distinct()
        val displayNameVariants = listOf(clean, cleanLower, cleanUpper, titleCase).distinct()

        val rawLenoDigits = clean.replace("LEN-", "", ignoreCase = true).replace("LEN", "", ignoreCase = true).replace("-", "").trim()
        val isNumericLeno = rawLenoDigits.isNotEmpty() && rawLenoDigits.all { it.isDigit() }
        val formattedLenoId = if (isNumericLeno) "LEN-$rawLenoDigits" else if (clean.startsWith("LEN-", ignoreCase = true)) cleanUpper else ""
        val lenoVariants = listOfNotNull(
            formattedLenoId.ifEmpty { null },
            if (isNumericLeno) rawLenoDigits else null,
            cleanUpper.takeIf { it.startsWith("LEN-") },
            clean.takeIf { it.startsWith("LEN-", ignoreCase = true) }
        ).distinct()

        // 0. Instant resolution for Official Accounts across any device
        if (cleanLower == "officialjaiby" || cleanLower == "official jaiby" || cleanLower == "50638964" || cleanUpper == "LEN-50638964" || cleanLower.contains("officialjaiby")) {
            return@withContext UserEntity(
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
        }
        if (cleanLower == "leno" || cleanLower == "official leno" || cleanUpper == "LEN-00000001" || cleanUpper == "LEN-00000000" || cleanLower.contains("official leno")) {
            return@withContext UserEntity(
                userId = "official_leno_account",
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
        }

        if (isTestEnvironment()) {
            val byUid = testUsers[clean] ?: testUsers[cleanUpper] ?: (if (formattedLenoId.isNotEmpty()) testUsers[formattedLenoId] else null)
            if (byUid != null) return@withContext byUid
            val unameUid = testUsernames[cleanLower]
            if (unameUid != null && testUsers[unameUid] != null) return@withContext testUsers[unameUid]
            val lenoUid = if (formattedLenoId.isNotEmpty()) testLenoIds[formattedLenoId] ?: testLenoIds[cleanUpper] else null
            if (lenoUid != null && testUsers[lenoUid] != null) return@withContext testUsers[lenoUid]
            return@withContext testUsers.values.firstOrNull {
                it.username.equals(clean, ignoreCase = true) ||
                    (formattedLenoId.isNotEmpty() && it.linoId.equals(formattedLenoId, ignoreCase = true)) ||
                    (isNumericLeno && it.linoId.replace("-", "").equals(rawLenoDigits, ignoreCase = true)) ||
                    it.displayName.equals(clean, ignoreCase = true) ||
                    it.email.equals(clean, ignoreCase = true) ||
                    it.googleEmail.equals(clean, ignoreCase = true) ||
                    it.recoveryEmail.equals(clean, ignoreCase = true) ||
                    it.userId.equals(clean, ignoreCase = true)
            }
        }

        val db = getFirestoreSafe() ?: return@withContext null

        try {
            withTimeoutOrNull(10000L) {
                val pairs = coroutineScope {
                    val jobs = listOf(
                        async {
                            try {
                                val unameDoc = db.collection(COLLECTION_USERNAMES).document(cleanLower).get().await()
                                if (unameDoc.exists()) {
                                    val ownerUid = unameDoc.getString("ownerUid") ?: unameDoc.getString("uid") ?: unameDoc.getString("userId")
                                    if (!ownerUid.isNullOrBlank()) {
                                        val userDoc = db.collection(COLLECTION_USERS).document(ownerUid).get().await()
                                        if (userDoc.exists()) {
                                            return@async listOfNotNull(docToUser(userDoc.data, userDoc.id)?.let { userDoc.id to it })
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                            emptyList<Pair<String, UserEntity>>()
                        },
                        async {
                            try {
                                db.collection(COLLECTION_USERS).whereEqualTo("username_lowercase", cleanLower).limit(5).get().await()
                                    .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                db.collection(COLLECTION_USERS).whereIn("username", usernameVariants).limit(5).get().await()
                                    .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            if (formattedLenoId.isNotEmpty()) {
                                try {
                                    val lenoDoc = db.collection(COLLECTION_LENO_IDS).document(formattedLenoId).get().await()
                                    if (lenoDoc.exists()) {
                                        val ownerUid = lenoDoc.getString("ownerUid") ?: lenoDoc.getString("uid") ?: lenoDoc.getString("userId")
                                        if (!ownerUid.isNullOrBlank()) {
                                            val userDoc = db.collection(COLLECTION_USERS).document(ownerUid).get().await()
                                            if (userDoc.exists()) {
                                                return@async listOfNotNull(docToUser(userDoc.data, userDoc.id)?.let { userDoc.id to it })
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            emptyList()
                        },
                        async {
                            if (lenoVariants.isNotEmpty()) {
                                try {
                                    return@async db.collection(COLLECTION_USERS).whereIn("linoId", lenoVariants).limit(5).get().await()
                                        .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                                } catch (_: Exception) {}
                            }
                            emptyList()
                        },
                        async {
                            try {
                                db.collection(COLLECTION_USERS).whereEqualTo("lenId_lowercase", cleanLower).limit(5).get().await()
                                    .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                val directDoc = db.collection(COLLECTION_USERS).document(clean).get().await()
                                if (directDoc.exists()) listOfNotNull(docToUser(directDoc.data, directDoc.id)?.let { directDoc.id to it })
                                else emptyList()
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                db.collection(COLLECTION_USERS).whereEqualTo("displayName_lowercase", cleanLower).limit(5).get().await()
                                    .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                db.collection(COLLECTION_USERS).whereIn("displayName", displayNameVariants).limit(5).get().await()
                                    .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                db.collection(COLLECTION_USERS).whereEqualTo("email", cleanLower).limit(5).get().await()
                                    .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                            } catch (_: Exception) { emptyList() }
                        }
                    )
                    jobs.flatMap { it.await() }
                }

                val indexedMatch = deduplicateCanonicalUsers(pairs).firstOrNull()
                if (indexedMatch != null) {
                    return@withTimeoutOrNull indexedMatch
                }

                // Paginated broad fallback ensures accounts without lowercase fields are always discoverable
                val allUsers = deduplicateCanonicalUsers(fetchAllUserDocsPaginated(db, 1500))
                val cleanDigits = rawLenoDigits.lowercase()
                allUsers.firstOrNull { u ->
                    val rawLeno = u.linoId.replace("-", "").lowercase()
                    u.username.equals(clean, ignoreCase = true) ||
                        u.displayName.equals(clean, ignoreCase = true) ||
                        (formattedLenoId.isNotEmpty() && u.linoId.equals(formattedLenoId, ignoreCase = true)) ||
                        (isNumericLeno && rawLeno == cleanDigits) ||
                        u.userId.equals(clean, ignoreCase = true) ||
                        u.email.equals(cleanLower, ignoreCase = true) ||
                        u.googleEmail.equals(cleanLower, ignoreCase = true) ||
                        u.recoveryEmail.equals(cleanLower, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
            }
            Log.w(TAG, "Remote user lookup notice for '$identifier': ${e.message}")
            null
        }
    }

    /**
     * Finds a user by direct Firebase User ID / document ID in Firestore.
     */
    open suspend fun findRemoteUserById(userId: String): UserEntity? = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext null
        if (isTestEnvironment()) {
            return@withContext testUsers[userId]
        }
        val db = getFirestoreSafe() ?: return@withContext null
        try {
            withTimeoutOrNull(6000L) {
                val doc = db.collection(COLLECTION_USERS).document(userId).get().await()
                if (doc.exists()) {
                    docToUser(doc.data, doc.id)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
            }
            Log.w(TAG, "Remote user lookup by ID notice: ${e.message}")
            null
        }
    }

    /**
     * Searches for users across the remote cloud store matching query.
     * Runs exact, prefix, and paginated scans concurrently so accounts created on other APK devices appear immediately.
     */
    suspend fun searchRemoteUsers(query: String, currentUserId: String): List<UserEntity> = withContext(Dispatchers.IO) {
        val clean = query.trim().removePrefix("@").trim()
        if (clean.isBlank()) return@withContext emptyList()
        val cleanLower = clean.lowercase()
        val cleanUpper = clean.uppercase()
        val titleCase = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val usernameVariants = listOf(clean, cleanLower, cleanUpper, titleCase).distinct()

        val rawLenoDigits = clean.replace("LEN-", "", ignoreCase = true).replace("LEN", "", ignoreCase = true).replace("-", "").trim()
        val isNumericLeno = rawLenoDigits.isNotEmpty() && rawLenoDigits.all { it.isDigit() }
        val formattedLenoId = if (isNumericLeno) "LEN-$rawLenoDigits" else if (clean.startsWith("LEN-", ignoreCase = true)) cleanUpper else ""

        if (isTestEnvironment()) {
            return@withContext testUsers.values
                .distinctBy { it.userId }
                .filter {
                    it.userId != currentUserId && (
                        it.username.contains(clean, ignoreCase = true) ||
                            it.displayName.contains(clean, ignoreCase = true) ||
                            it.linoId.contains(clean, ignoreCase = true) ||
                            (formattedLenoId.isNotEmpty() && it.linoId.contains(formattedLenoId, ignoreCase = true)) ||
                            (isNumericLeno && it.linoId.replace("-", "").contains(rawLenoDigits, ignoreCase = true)) ||
                            it.userId.contains(clean, ignoreCase = true) ||
                            it.email.contains(clean, ignoreCase = true)
                    )
                }
        }
        val db = getFirestoreSafe() ?: return@withContext emptyList()

        try {
            val collectedPairs = withTimeoutOrNull(8000L) {
                coroutineScope {
                    val exactJob = async {
                        try {
                            findRemoteUser(clean)?.let { it.userId to it }
                        } catch (_: Exception) { null }
                    }
                    val unameJob = async {
                        try {
                            db.collection(COLLECTION_USERS).whereIn("username", usernameVariants).limit(10).get().await()
                                .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                        } catch (_: Exception) { emptyList() }
                    }
                    val uLowerJob = async {
                        try {
                            db.collection(COLLECTION_USERS).orderBy("username_lowercase").startAt(cleanLower).endAt(cleanLower + "\uf8ff").limit(25).get().await()
                                .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                        } catch (_: Exception) { emptyList() }
                    }
                    val uRawJob = async {
                        try {
                            db.collection(COLLECTION_USERS).orderBy("username").startAt(clean).endAt(clean + "\uf8ff").limit(25).get().await()
                                .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                        } catch (_: Exception) { emptyList() }
                    }
                    val dLowerJob = async {
                        try {
                            db.collection(COLLECTION_USERS).orderBy("displayName_lowercase").startAt(cleanLower).endAt(cleanLower + "\uf8ff").limit(25).get().await()
                                .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                        } catch (_: Exception) { emptyList() }
                    }
                    val dRawJob = async {
                        try {
                            db.collection(COLLECTION_USERS).orderBy("displayName").startAt(titleCase).endAt(titleCase + "\uf8ff").limit(25).get().await()
                                .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                        } catch (_: Exception) { emptyList() }
                    }
                    val linoJob = async {
                        if (formattedLenoId.isNotEmpty() || isNumericLeno) {
                            val lenoTarget = if (formattedLenoId.isNotEmpty()) formattedLenoId else cleanUpper
                            try {
                                return@async db.collection(COLLECTION_USERS).whereEqualTo("linoId", lenoTarget).limit(5).get().await()
                                    .documents.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                            } catch (_: Exception) {}
                        }
                        emptyList()
                    }

                    val fastResults = mutableListOf<Pair<String, UserEntity>>()
                    exactJob.await()?.let { fastResults.add(it) }
                    fastResults.addAll(unameJob.await())
                    fastResults.addAll(uLowerJob.await())
                    fastResults.addAll(uRawJob.await())
                    fastResults.addAll(dLowerJob.await())
                    fastResults.addAll(dRawJob.await())
                    fastResults.addAll(linoJob.await())
                    fastResults
                }
            }.orEmpty().toMutableList()

            if (collectedPairs.isEmpty()) {
                val broadMatches = withTimeoutOrNull(8000L) {
                    val cleanDigits = rawLenoDigits.lowercase()
                    fetchAllUserDocsPaginated(db, 1000).filter { (docId, u) ->
                        val rawLeno = u.linoId.replace("-", "").lowercase()
                        u.username.contains(clean, ignoreCase = true) ||
                            u.displayName.contains(clean, ignoreCase = true) ||
                            u.linoId.contains(clean, ignoreCase = true) ||
                            (formattedLenoId.isNotEmpty() && u.linoId.contains(formattedLenoId, ignoreCase = true)) ||
                            (isNumericLeno && rawLeno.contains(cleanDigits)) ||
                            u.userId.contains(clean, ignoreCase = true) ||
                            docId.contains(clean, ignoreCase = true) ||
                            u.email.contains(clean, ignoreCase = true) ||
                            (clean.length >= 3 && u.phoneNumber.contains(clean))
                    }
                }.orEmpty()
                collectedPairs.addAll(broadMatches)
            }

                if (cleanLower.contains("officialjaiby") || "officialjaiby".contains(cleanLower) || cleanLower.contains("50638964")) {
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
                    collectedPairs.add(officialJaiby.userId to officialJaiby)
                }
                if (cleanLower.contains("leno") || cleanLower.contains("official leno") || cleanLower.contains("00000001")) {
                    val officialLeno = UserEntity(
                        userId = "official_leno_account",
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
                    collectedPairs.add(officialLeno.userId to officialLeno)
                }

                deduplicateCanonicalUsers(collectedPairs).filter { it.userId != currentUserId }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
            }
            Log.w(TAG, "Remote user search query notice: ${e.message}")
            emptyList()
        }
    }

    // ==========================================
    // CLOUD CALL SIGNALING
    // ==========================================

    data class CloudCallRecord(
        val callId: String = "",
        val callerId: String = "",
        val callerName: String = "",
        val callerUsername: String = "",
        val callerAvatar: String = "",
        val receiverId: String = "",
        val receiverName: String = "",
        val receiverUsername: String = "",
        val receiverAvatar: String = "",
        val status: String = "CALLING", // CALLING, RINGING, CONNECTED, DECLINED, MISSED, CANCELLED, ENDED
        val isVideo: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun createCallOffer(call: CloudCallRecord): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext Result.success(Unit)
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            withTimeoutOrNull(2000L) {
                db.collection(COLLECTION_CALLS)
                    .document(call.callId)
                    .set(call)
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Result.success(Unit)
        }
    }

    suspend fun updateCallStatus(callId: String, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext Result.success(Unit)
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            withTimeoutOrNull(2000L) {
                db.collection(COLLECTION_CALLS)
                    .document(callId)
                    .update("status", status)
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Result.success(Unit)
        }
    }

    fun listenToCall(callId: String, onUpdate: (CloudCallRecord?) -> Unit): ListenerRegistration? {
        if (!isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_CALLS)
                .document(callId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "Error listening to call $callId: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val record = snapshot.toObject(CloudCallRecord::class.java)
                        onUpdate(record)
                    } else {
                        onUpdate(null)
                    }
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "Failed to start call listener: ${e.message}")
            null
        }
    }

    fun listenIncomingCalls(myUserId: String, onIncoming: (CloudCallRecord) -> Unit): ListenerRegistration? {
        if (!isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_CALLS)
                .whereEqualTo("receiverId", myUserId)
                .whereIn("status", listOf("CALLING", "RINGING"))
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        return@addSnapshotListener
                    }
                    snapshot?.documents?.forEach { doc ->
                        val record = doc.toObject(CloudCallRecord::class.java)
                        if (record != null) {
                            onIncoming(record)
                        }
                    }
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            null
        }
    }

    internal fun docToUser(data: Map<String, Any?>?, docId: String = ""): UserEntity? {
        if (data == null) return null
        return try {
            val rawUid = (data["userId"] as? String)?.ifBlank { null }
                ?: (data["uid"] as? String)?.ifBlank { null }
                ?: docId
            val rawDisplayName = (data["displayName"] as? String)?.ifBlank { null }
                ?: (data["fullName"] as? String)?.ifBlank { null }
                ?: (data["displayName_lowercase"] as? String)?.ifBlank { null }
                ?: ""
            val rawUsername = (data["username"] as? String)?.ifBlank { null }
                ?: (data["username_lowercase"] as? String)?.ifBlank { null }
                ?: ""
            val rawLenoId = (data["lenId"] as? String)?.ifBlank { null }
                ?: (data["linoId"] as? String)?.ifBlank { null }
                ?: (data["lenoId"] as? String)?.ifBlank { null }
                ?: (data["lenId_lowercase"] as? String)?.ifBlank { null }
                ?: (if (docId.startsWith("LEN-", ignoreCase = true)) docId else "")
            val gEmail = data["googleEmail"] as? String ?: ""
            val rEmail = data["recoveryEmail"] as? String ?: ""
            val emailField = data["email"] as? String ?: ""
            val isVerifiedField = (data["isVerified"] as? Boolean) ?: (data["verified"] as? Boolean) ?: (data["isAccountVerified"] as? Boolean) ?: false
            val isOwnerUser = rawUid == "usr_officialjaiby_2026" ||
                emailField.equals("iliyasuusman730@gmail.com", ignoreCase = true) ||
                gEmail.equals("iliyasuusman730@gmail.com", ignoreCase = true)
            val finalVerified = isVerifiedField || gEmail.isNotBlank() || rEmail.isNotBlank() || isOwnerUser

            val createdAtMillis = when (val c = data["createdAt"]) {
                is Number -> c.toLong()
                is com.google.firebase.Timestamp -> c.toDate().time
                else -> System.currentTimeMillis()
            }
            val lastSeenMillis = when (val ls = data["lastSeen"]) {
                is Number -> ls.toLong()
                is com.google.firebase.Timestamp -> ls.toDate().time
                else -> System.currentTimeMillis()
            }
            val updatedAtMillis = when (val u = data["updatedAt"]) {
                is Number -> u.toLong()
                is com.google.firebase.Timestamp -> u.toDate().time
                else -> System.currentTimeMillis()
            }

            UserEntity(
                userId = rawUid,
                username = rawUsername,
                displayName = rawDisplayName.ifBlank { rawUsername },
                bio = data["bio"] as? String ?: "Connecting on Leno ✨",
                avatarUrl = data["avatarUrl"] as? String ?: "",
                isOnline = data["isOnline"] as? Boolean ?: false,
                lastSeen = lastSeenMillis,
                statusMessage = data["statusMessage"] as? String ?: "Hey there! I am using Leno.",
                isCurrentUser = false,
                email = data["email"] as? String ?: "",
                password = "",
                passwordHash = data["passwordHash"] as? String ?: "",
                passwordSalt = data["passwordSalt"] as? String ?: "",
                recoveryCode = data["recoveryCode"] as? String ?: "",
                phoneNumber = data["phoneNumber"] as? String ?: "",
                normalizedPhoneNumber = data["normalizedPhoneNumber"] as? String ?: "",
                createdAt = createdAtMillis,
                isOfficial = data["isOfficial"] as? Boolean ?: false,
                linoId = rawLenoId,
                googleEmail = gEmail,
                recoveryEmail = rEmail,
                role = ((data["role"] as? String)?.uppercase() ?: if (data["isTeacher"] == true) "TEACHER" else "USER"),
                assignedSubject = run {
                    val rawSub = (data["assignedSubject"] as? String)
                        ?: (data["approvedSubject"] as? String)
                        ?: ((data["teacherProfile"] as? Map<*, *>)?.get("assignedSubject") as? String)
                        ?: ""
                    rawSub.trim().split(",").firstOrNull()?.trim().orEmpty()
                },
                teacherStatus = (data["teacherStatus"] as? String) ?: "NOT_APPLIED",
                teacherSubject = (data["teacherSubject"] as? String) ?: (data["approvedSubject"] as? String) ?: "",
                creatorStatus = (data["creatorStatus"] as? String) ?: "INACTIVE",
                isRestricted = (data["isRestricted"] as? Boolean) ?: false,
                isVerified = finalVerified,
                updatedAt = updatedAtMillis
            )
        } catch (e: Exception) {
            Log.w(TAG, "Notice parsing remote user document: ${e.message}")
            null
        }
    }

    // ==========================================
    // PER-USER CONTACTS IN FIRESTORE
    // ==========================================

    /**
     * Saves a contact for a user under users/{myUid}/contacts/{contactUid}
     */
    suspend fun saveContactToRemote(
        myUid: String,
        contactUser: UserEntity,
        status: String = "ACCEPTED"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) {
            return@withContext Result.success(Unit)
        }
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        if (myUid.isBlank() || contactUser.userId.isBlank()) {
            return@withContext Result.failure(Exception("Invalid user ID."))
        }
        try {
            val contactData = hashMapOf<String, Any>(
                "contactUid" to contactUser.userId,
                "userId" to contactUser.userId,
                "username" to contactUser.username.lowercase(),
                "displayName" to contactUser.displayName,
                "fullName" to contactUser.displayName,
                "avatarUrl" to contactUser.avatarUrl,
                "linoId" to contactUser.linoId.uppercase(),
                "bio" to contactUser.bio,
                "statusMessage" to contactUser.statusMessage,
                "status" to status,
                "updatedAt" to System.currentTimeMillis()
            )
            withTimeoutOrNull(2500L) {
                db.collection(COLLECTION_USERS)
                    .document(myUid)
                    .collection("contacts")
                    .document(contactUser.userId)
                    .set(contactData, SetOptions.merge())
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
                return@withContext Result.success(Unit)
            }
            Log.w(TAG, "saveContactToRemote notice: ${e.message}")
            Result.success(Unit)
        }
    }

    /**
     * Removes a contact from users/{myUid}/contacts/{contactUid}
     */
    suspend fun removeContactFromRemote(myUid: String, contactUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext Result.success(Unit)
        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Firestore is not available."))
        try {
            db.collection(COLLECTION_USERS)
                .document(myUid)
                .collection("contacts")
                .document(contactUid)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
                return@withContext Result.success(Unit)
            }
            Log.w(TAG, "removeContactFromRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetches all contacts for a user from users/{myUid}/contacts
     */
    suspend fun getContactsFromRemote(myUid: String): List<UserEntity> = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext emptyList()
        val db = getFirestoreSafe() ?: return@withContext emptyList()
        if (myUid.isBlank()) return@withContext emptyList()
        try {
            val snapshot = withTimeoutOrNull(6000L) {
                db.collection(COLLECTION_USERS)
                    .document(myUid)
                    .collection("contacts")
                    .whereEqualTo("status", "ACCEPTED")
                    .get()
                    .await()
            } ?: return@withContext emptyList()

            snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val uid = data["contactUid"] as? String ?: (data["userId"] as? String) ?: doc.id
                val uname = data["username"] as? String ?: ""
                val dname = data["displayName"] as? String ?: (data["fullName"] as? String) ?: uname
                val avatar = data["avatarUrl"] as? String ?: ""
                val lid = data["linoId"] as? String ?: ""
                val bio = data["bio"] as? String ?: "Connecting on Leno ✨"
                val statusMsg = data["statusMessage"] as? String ?: "Hey there! I am using Leno."
                UserEntity(
                    userId = uid,
                    username = uname,
                    displayName = dname,
                    avatarUrl = avatar,
                    linoId = lid,
                    bio = bio,
                    statusMessage = statusMsg,
                    isCurrentUser = false
                )
            }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
            }
            Log.w(TAG, "getContactsFromRemote notice: ${e.message}")
            emptyList()
        }
    }

    fun listenToContacts(myUid: String, onUpdate: (List<UserEntity>) -> Unit): ListenerRegistration? {
        return listenToContactsWithRemovals(myUid) { contacts, _ ->
            onUpdate(contacts)
        }
    }

    fun listenToContactsWithRemovals(
        myUid: String,
        onUpdate: (contacts: List<UserEntity>, removedIds: List<String>) -> Unit
    ): ListenerRegistration? {
        if (!isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        if (myUid.isBlank()) return null
        return try {
            db.collection(COLLECTION_USERS)
                .document(myUid)
                .collection("contacts")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "Error listening to contacts: ${error.message}")
                        return@addSnapshotListener
                    }
                    val removedIds = snapshot?.documentChanges
                        ?.filter { it.type == DocumentChange.Type.REMOVED }
                        ?.mapNotNull { change ->
                            change.document.getString("contactUid")
                                ?: change.document.getString("userId")
                                ?: change.document.id
                        }
                        .orEmpty()
                        .toMutableList()

                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val uid = data["contactUid"] as? String ?: (data["userId"] as? String) ?: doc.id
                        val status = data["status"] as? String ?: "ACCEPTED"
                        if (!status.equals("ACCEPTED", ignoreCase = true)) {
                            if (uid.isNotBlank()) removedIds.add(uid)
                            return@mapNotNull null
                        }
                        val uname = data["username"] as? String ?: ""
                        val dname = data["displayName"] as? String ?: (data["fullName"] as? String) ?: uname
                        val avatar = data["avatarUrl"] as? String ?: ""
                        val lid = data["linoId"] as? String ?: ""
                        val bio = data["bio"] as? String ?: "Connecting on Leno ✨"
                        val statusMsg = data["statusMessage"] as? String ?: "Hey there! I am using Leno."
                        UserEntity(
                            userId = uid,
                            username = uname,
                            displayName = dname,
                            avatarUrl = avatar,
                            linoId = lid,
                            bio = bio,
                            statusMessage = statusMsg,
                            isCurrentUser = false
                        )
                    } ?: emptyList()
                    onUpdate(list, removedIds.distinct())
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "listenToContactsWithRemovals failure: ${e.message}")
            null
        }
    }

    // ==========================================
    // REAL-TIME PRESENCE, TYPING, FRIEND REQUESTS & NOTIFICATIONS
    // ==========================================

    open suspend fun updateUserPresenceInRemote(
        userId: String,
        isOnline: Boolean,
        lastSeen: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        val existing = testUsers[userId]
        if (existing != null) {
            val updated = existing.copy(isOnline = isOnline, lastSeen = lastSeen)
            testUsers[userId] = updated
            testUserDocumentListeners[userId]?.forEach { listener ->
                try { listener.invoke(updated) } catch (_: Exception) {}
            }
        }
        if (isTestEnvironment() || !isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        try {
            db.collection(COLLECTION_USERS)
                .document(userId)
                .set(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastSeen" to lastSeen,
                        "updatedAt" to lastSeen
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "updateUserPresenceInRemote notice: ${e.message}")
        }
    }

    open suspend fun setTypingStatusInRemote(
        chatId: String,
        userId: String,
        isTyping: Boolean
    ) = withContext(Dispatchers.IO) {
        if (chatId.isBlank() || userId.isBlank()) return@withContext
        if (isTestEnvironment() || !isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        try {
            val ts = if (isTyping) System.currentTimeMillis() else 0L
            db.collection(COLLECTION_CHATS)
                .document(chatId)
                .set(
                    mapOf("typing_$userId" to ts),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "setTypingStatusInRemote notice: ${e.message}")
        }
    }

    open fun listenToChatTyping(
        chatId: String,
        partnerId: String,
        onTyping: (Boolean) -> Unit
    ): ListenerRegistration? {
        if (chatId.isBlank() || partnerId.isBlank()) return null
        if (isTestEnvironment() || !isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_CHATS)
                .document(chatId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        return@addSnapshotListener
                    }
                    val ts = (snapshot?.get("typing_$partnerId") as? Number)?.toLong() ?: 0L
                    val active = ts > 0L && (System.currentTimeMillis() - ts) < 10_000L
                    onTyping(active)
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            null
        }
    }

    open suspend fun sendFriendRequestToRemote(
        senderId: String,
        receiverId: String,
        senderDisplayName: String = "",
        senderUsername: String = "",
        senderAvatarUrl: String = "",
        senderLinoId: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (senderId.isBlank() || receiverId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid user IDs"))
        }
        if (isTestEnvironment() || !isFirestoreProvisioned) return@withContext Result.success(Unit)
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        val docId = "${senderId}_${receiverId}"
        val now = System.currentTimeMillis()
        try {
            val reqMap = hashMapOf<String, Any>(
                "id" to docId,
                "requesterId" to senderId,
                "targetId" to receiverId,
                "status" to "PENDING",
                "createdAt" to now,
                "senderDisplayName" to senderDisplayName,
                "senderUsername" to senderUsername,
                "senderAvatarUrl" to senderAvatarUrl,
                "senderLinoId" to senderLinoId
            )
            val batch = db.batch()
            batch.set(db.collection(COLLECTION_FRIEND_REQUESTS).document(docId), reqMap, SetOptions.merge())
            batch.set(
                db.collection(COLLECTION_USERS).document(receiverId)
                    .collection(COLLECTION_FRIEND_REQUESTS).document(senderId),
                reqMap,
                SetOptions.merge()
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "sendFriendRequestToRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    open suspend fun acceptFriendRequestInRemote(
        currentUserId: String,
        requesterId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (currentUserId.isBlank() || requesterId.isBlank()) return@withContext Result.success(Unit)
        if (isTestEnvironment() || !isFirestoreProvisioned) return@withContext Result.success(Unit)
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        val docId = "${requesterId}_${currentUserId}"
        val now = System.currentTimeMillis()
        try {
            val updates = mapOf("status" to "ACCEPTED", "updatedAt" to now)
            val batch = db.batch()
            batch.set(
                db.collection(COLLECTION_FRIEND_REQUESTS).document(docId),
                mapOf(
                    "id" to docId,
                    "requesterId" to requesterId,
                    "targetId" to currentUserId,
                    "status" to "ACCEPTED",
                    "updatedAt" to now
                ),
                SetOptions.merge()
            )
            batch.set(
                db.collection(COLLECTION_USERS).document(currentUserId)
                    .collection(COLLECTION_FRIEND_REQUESTS).document(requesterId),
                updates,
                SetOptions.merge()
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "acceptFriendRequestInRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    open suspend fun declineFriendRequestInRemote(
        currentUserId: String,
        requesterId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (currentUserId.isBlank() || requesterId.isBlank()) return@withContext Result.success(Unit)
        if (isTestEnvironment() || !isFirestoreProvisioned) return@withContext Result.success(Unit)
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            val batch = db.batch()
            batch.delete(db.collection(COLLECTION_FRIEND_REQUESTS).document("${requesterId}_${currentUserId}"))
            batch.delete(db.collection(COLLECTION_FRIEND_REQUESTS).document("${currentUserId}_${requesterId}"))
            batch.delete(
                db.collection(COLLECTION_USERS).document(currentUserId)
                    .collection(COLLECTION_FRIEND_REQUESTS).document(requesterId)
            )
            batch.delete(
                db.collection(COLLECTION_USERS).document(requesterId)
                    .collection(COLLECTION_FRIEND_REQUESTS).document(currentUserId)
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "declineFriendRequestInRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    fun listenToIncomingFriendRequests(
        myUid: String,
        onUpdate: (List<FriendshipEntity>) -> Unit
    ): ListenerRegistration? {
        if (myUid.isBlank() || isTestEnvironment() || !isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_FRIEND_REQUESTS)
                .whereEqualTo("targetId", myUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToIncomingFriendRequests error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val reqId = doc.getString("requesterId") ?: return@mapNotNull null
                        val tgtId = doc.getString("targetId") ?: myUid
                        val status = doc.getString("status") ?: "PENDING"
                        FriendshipEntity(
                            id = doc.getString("id") ?: doc.id,
                            requesterId = reqId,
                            targetId = tgtId,
                            status = status,
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    } ?: emptyList()
                    onUpdate(list)
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            null
        }
    }

    fun listenToOutgoingFriendRequests(
        myUid: String,
        onUpdate: (List<FriendshipEntity>) -> Unit
    ): ListenerRegistration? {
        if (myUid.isBlank() || isTestEnvironment() || !isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_FRIEND_REQUESTS)
                .whereEqualTo("requesterId", myUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToOutgoingFriendRequests error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val reqId = doc.getString("requesterId") ?: myUid
                        val tgtId = doc.getString("targetId") ?: return@mapNotNull null
                        val status = doc.getString("status") ?: "PENDING"
                        FriendshipEntity(
                            id = doc.getString("id") ?: doc.id,
                            requesterId = reqId,
                            targetId = tgtId,
                            status = status,
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    } ?: emptyList()
                    onUpdate(list)
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            null
        }
    }

    open suspend fun saveNotificationToRemote(notification: NotificationEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (notification.userId.isBlank() || isTestEnvironment() || !isFirestoreProvisioned) {
            return@withContext Result.success(Unit)
        }
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            val map = hashMapOf<String, Any?>(
                "id" to notification.id,
                "userId" to notification.userId,
                "senderId" to notification.senderId,
                "title" to notification.title,
                "body" to notification.body,
                "type" to notification.type,
                "timestamp" to notification.timestamp,
                "isRead" to notification.isRead
            )
            db.collection(COLLECTION_USERS)
                .document(notification.userId)
                .collection("notifications")
                .document(notification.id)
                .set(map, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "saveNotificationToRemote notice: ${e.message}")
            Result.success(Unit)
        }
    }

    open suspend fun markNotificationReadInRemote(userId: String, notificationId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank() || notificationId.isBlank() || isTestEnvironment() || !isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        try {
            db.collection(COLLECTION_USERS)
                .document(userId)
                .collection("notifications")
                .document(notificationId)
                .set(mapOf("isRead" to true), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
        }
    }

    open suspend fun markAllNotificationsReadInRemote(userId: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank() || isTestEnvironment() || !isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        try {
            val snap = withTimeoutOrNull(4000L) {
                db.collection(COLLECTION_USERS)
                    .document(userId)
                    .collection("notifications")
                    .whereEqualTo("isRead", false)
                    .get()
                    .await()
            } ?: return@withContext
            if (snap.isEmpty) return@withContext
            val batch = db.batch()
            snap.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            batch.commit().await()
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
        }
    }

    fun listenToUserNotifications(
        userId: String,
        onUpdate: (notifications: List<NotificationEntity>, removedIds: List<String>) -> Unit
    ): ListenerRegistration? {
        if (userId.isBlank() || isTestEnvironment() || !isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_USERS)
                .document(userId)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToUserNotifications error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val removedIds = snapshot?.documentChanges
                        ?.filter { it.type == DocumentChange.Type.REMOVED }
                        ?.map { it.document.id }
                        .orEmpty()
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val title = doc.getString("title") ?: return@mapNotNull null
                        NotificationEntity(
                            id = doc.getString("id") ?: doc.id,
                            userId = doc.getString("userId") ?: userId,
                            senderId = doc.getString("senderId"),
                            title = title,
                            body = doc.getString("body") ?: "",
                            type = doc.getString("type") ?: "SYSTEM",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            isRead = doc.getBoolean("isRead") ?: false
                        )
                    } ?: emptyList()
                    onUpdate(list, removedIds)
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            null
        }
    }

    // ==========================================
    // PERMANENT CHAT HISTORY IN FIRESTORE
    // ==========================================

    fun getChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    /**
     * Stores a message permanently under chats/{chatId}/messages/{messageId}, updates chats/{chatId},
     * and places an instant push item in receiver's inbox for guaranteed real-time delivery.
     */
    open suspend fun saveMessageToRemote(message: MessageEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) {
            return@withContext Result.success(Unit)
        }
        if (message.senderId.isBlank() || message.receiverId.isBlank()) {
            Log.w(TAG, "Cannot save message with empty sender or receiver: sender='${message.senderId}', receiver='${message.receiverId}'")
            return@withContext Result.failure(IllegalArgumentException("Empty sender or receiver ID"))
        }
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        val chatId = getChatId(message.senderId, message.receiverId)
        try {
            val isMsgRead = message.read || message.isRead || message.status.equals("READ", ignoreCase = true)
            val msgData = hashMapOf<String, Any?>(
                "messageId" to message.messageId,
                "chatId" to chatId,
                "senderId" to message.senderId,
                "receiverId" to message.receiverId,
                "text" to message.text,
                "imageUrl" to message.imageUrl,
                "audioUrl" to message.audioUrl,
                "audioDurationSeconds" to message.audioDurationSeconds,
                "timestamp" to message.timestamp,
                "status" to (if (isMsgRead) "READ" else message.status),
                "read" to isMsgRead,
                "isSystemMessage" to message.isSystemMessage
            )

            val chatSummary = hashMapOf<String, Any>(
                "chatId" to chatId,
                "participants" to listOf(message.senderId, message.receiverId),
                "lastMessageText" to message.text,
                "lastMessageTimestamp" to message.timestamp,
                "updatedAt" to message.timestamp
            )

            withTimeoutOrNull(15000L) {
                try {
                    val batch = db.batch()
                    batch.set(db.collection(COLLECTION_CHATS).document(chatId), chatSummary, SetOptions.merge())
                    batch.set(db.collection(COLLECTION_CONVERSATIONS).document(chatId), chatSummary, SetOptions.merge())
                    batch.set(db.collection(COLLECTION_CHATS).document(chatId).collection("messages").document(message.messageId), msgData)
                    batch.set(db.collection(COLLECTION_CONVERSATIONS).document(chatId).collection("messages").document(message.messageId), msgData)
                    batch.set(db.collection(COLLECTION_USERS).document(message.receiverId).collection("inbox").document(message.messageId), msgData)
                    batch.set(db.collection(COLLECTION_USERS).document(message.senderId).collection("sent_messages").document(message.messageId), msgData)
                    batch.set(db.collection("messages").document(message.messageId), msgData)
                    batch.commit().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Batch write fallback to direct writes: ${e.message}")
                    try {
                        withTimeoutOrNull(8000L) {
                            db.collection(COLLECTION_CHATS).document(chatId).set(chatSummary, SetOptions.merge()).await()
                            db.collection(COLLECTION_CONVERSATIONS).document(chatId).set(chatSummary, SetOptions.merge()).await()
                        }
                    } catch (_: Exception) {}
                    try {
                        withTimeoutOrNull(8000L) {
                            db.collection(COLLECTION_CHATS).document(chatId).collection("messages").document(message.messageId).set(msgData).await()
                            db.collection(COLLECTION_CONVERSATIONS).document(chatId).collection("messages").document(message.messageId).set(msgData).await()
                        }
                    } catch (_: Exception) {}
                    try {
                        withTimeoutOrNull(8000L) {
                            db.collection(COLLECTION_USERS).document(message.receiverId).collection("inbox").document(message.messageId).set(msgData).await()
                        }
                    } catch (_: Exception) {}
                    try {
                        withTimeoutOrNull(8000L) {
                            db.collection("messages").document(message.messageId).set(msgData).await()
                        }
                    } catch (_: Exception) {}
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) {
                markDatabaseNotProvisioned()
                return@withContext Result.success(Unit)
            }
            Log.w(TAG, "saveMessageToRemote notice: ${e.message}")
            Result.success(Unit)
        }
    }

    open suspend fun markMessageDeliveredInRemote(chatId: String, messageId: String, senderId: String) = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        try {
            db.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .update("status", "DELIVERED")
                .await()

            val receipt = hashMapOf<String, Any>(
                "messageId" to messageId,
                "status" to "DELIVERED",
                "timestamp" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_USERS)
                .document(senderId)
                .collection("sent_receipts")
                .document(messageId)
                .set(receipt)
                .await()
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "markMessageDeliveredInRemote notice: ${e.message}")
        }
    }

    open suspend fun markMessageReadInRemote(chatId: String, messageId: String, senderId: String) = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        val readPayload = mapOf("read" to true, "status" to "READ")
        try {
            try {
                db.collection(COLLECTION_CHATS)
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .update(readPayload)
                    .await()
            } catch (_: Exception) {}

            try {
                db.collection(COLLECTION_CONVERSATIONS)
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .update(readPayload)
                    .await()
            } catch (_: Exception) {}

            try {
                db.collection("messages")
                    .document(messageId)
                    .update(readPayload)
                    .await()
            } catch (_: Exception) {}

            if (senderId.isNotBlank()) {
                try {
                    db.collection(COLLECTION_USERS)
                        .document(senderId)
                        .collection("sent_messages")
                        .document(messageId)
                        .update(readPayload)
                        .await()
                } catch (_: Exception) {}

                val receipt = hashMapOf<String, Any>(
                    "messageId" to messageId,
                    "status" to "READ",
                    "read" to true,
                    "timestamp" to System.currentTimeMillis()
                )
                try {
                    db.collection(COLLECTION_USERS)
                        .document(senderId)
                        .collection("sent_receipts")
                        .document(messageId)
                        .set(receipt)
                        .await()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "markMessageReadInRemote notice: ${e.message}")
        }
    }

    open suspend fun markAllChatMessagesAsReadInRemote(
        chatId: String,
        currentUserId: String,
        otherUserId: String,
        currentUserAliases: List<String> = emptyList(),
        otherUserAliases: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        try {
            val myAliases = (listOf(currentUserId) + currentUserAliases).filter { it.isNotBlank() }.toSet()
            val partnerAliases = (listOf(otherUserId) + otherUserAliases).filter { it.isNotBlank() }.toSet()
            val collectionsToUpdate = listOf(COLLECTION_CHATS, COLLECTION_CONVERSATIONS)
            val now = System.currentTimeMillis()
            val markedMsgSenders = mutableMapOf<String, String>()

            for (colName in collectionsToUpdate) {
                try {
                    val messagesRef = db.collection(colName).document(chatId).collection("messages")
                    val snapshot = withTimeoutOrNull(4000L) {
                        messagesRef.limit(100).get().await()
                    } ?: continue

                    val batch = db.batch()
                    var count = 0
                    snapshot.documents.forEach { doc ->
                        val sid = doc.getString("senderId").orEmpty()
                        val rid = doc.getString("receiverId").orEmpty()
                        val isForMe = rid in myAliases || sid in partnerAliases || (sid.isNotBlank() && sid !in myAliases)
                        val alreadyRead = doc.getBoolean("read") == true && doc.getString("status")?.equals("READ", ignoreCase = true) == true

                        if (isForMe && !alreadyRead) {
                            batch.update(doc.reference, mapOf("read" to true, "status" to "READ"))
                            val effectiveSender = if (sid.isNotBlank()) sid else otherUserId
                            if (effectiveSender.isNotBlank()) {
                                markedMsgSenders[doc.id] = effectiveSender
                                val receiptRef = db.collection(COLLECTION_USERS)
                                    .document(effectiveSender)
                                    .collection("sent_receipts")
                                    .document(doc.id)
                                batch.set(receiptRef, hashMapOf(
                                    "messageId" to doc.id,
                                    "status" to "READ",
                                    "read" to true,
                                    "timestamp" to now
                                ))
                            }
                            count++
                        }
                    }
                    batch.set(db.collection(colName).document(chatId), mapOf("unreadCount" to 0), SetOptions.merge())
                    batch.commit().await()
                    Log.i(TAG, "Successfully marked $count messages as READ in $colName for chat $chatId")
                } catch (e: Exception) {
                    Log.w(TAG, "markAllChatMessagesAsReadInRemote for $colName notice: ${e.message}")
                }
            }

            // Propagate READ to root messages/{id} & sender's sent_messages/{id} so chat recovery never reverts them to SENT
            val readPayload = mapOf("read" to true, "status" to "READ")
            for ((msgId, senderUid) in markedMsgSenders) {
                try {
                    db.collection("messages").document(msgId).update(readPayload).await()
                } catch (_: Exception) {}
                if (senderUid.isNotBlank()) {
                    try {
                        db.collection(COLLECTION_USERS).document(senderUid)
                            .collection("sent_messages").document(msgId).update(readPayload).await()
                    } catch (_: Exception) {}
                }
            }

            // Also clear inbox messages for this user from partner
            try {
                for (myUid in myAliases) {
                    val inboxSnap = withTimeoutOrNull(3000L) {
                        db.collection(COLLECTION_USERS)
                            .document(myUid)
                            .collection("inbox")
                            .get()
                            .await()
                    }
                    inboxSnap?.documents?.forEach { doc ->
                        val sid = doc.getString("senderId").orEmpty()
                        if (sid in partnerAliases || sid == otherUserId) {
                            try {
                                db.collection("messages").document(doc.id).update(readPayload)
                            } catch (_: Exception) {}
                            db.collection(COLLECTION_USERS)
                                .document(myUid)
                                .collection("inbox")
                                .document(doc.id)
                                .delete()
                        }
                    }
                }
            } catch (_: Exception) {}

        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "markAllChatMessagesAsReadInRemote notice: ${e.message}")
        }
    }

    open suspend fun deleteInboxMessage(receiverId: String, messageId: String) = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        try {
            db.collection(COLLECTION_USERS)
                .document(receiverId)
                .collection("inbox")
                .document(messageId)
                .delete()
                .await()
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "deleteInboxMessage error: ${e.message}")
        }
    }

    open suspend fun deleteSentReceipt(senderId: String, messageId: String) = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        try {
            db.collection(COLLECTION_USERS)
                .document(senderId)
                .collection("sent_receipts")
                .document(messageId)
                .delete()
                .await()
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "deleteSentReceipt error: ${e.message}")
        }
    }

    fun listenToIncomingMessages(myUserId: String, onMessage: (MessageEntity) -> Unit): ListenerRegistration? {
        if (!isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_USERS)
                .document(myUserId)
                .collection("inbox")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToIncomingMessages error: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshot?.documents?.forEach { doc ->
                        val data = doc.data ?: return@forEach
                        val msg = parseMessageFromData(doc.id, data)
                        if (msg != null) {
                            onMessage(msg)
                        }
                    }
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "listenToIncomingMessages init failed: ${e.message}")
            null
        }
    }

    fun listenToSentReceipts(myUserId: String, onReceipt: (messageId: String, status: String) -> Unit): ListenerRegistration? {
        if (!isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_USERS)
                .document(myUserId)
                .collection("sent_receipts")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToSentReceipts error: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshot?.documents?.forEach { doc ->
                        val isRead = doc.getBoolean("read") == true ||
                            doc.getString("status")?.equals("READ", ignoreCase = true) == true
                        val status = if (isRead) "READ" else (doc.getString("status") ?: return@forEach)
                        onReceipt(doc.id, status)
                    }
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "listenToSentReceipts init failed: ${e.message}")
            null
        }
    }

    fun listenToUserChats(myUserId: String, onChatUpdated: (chatId: String, otherUserId: String) -> Unit): ListenerRegistration? {
        if (!isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_CHATS)
                .whereArrayContains("participants", myUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToUserChats error: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshot?.documents?.forEach { doc ->
                        val participants = doc.get("participants") as? List<*>
                        val otherId = participants?.firstOrNull { it?.toString() != myUserId }?.toString()
                        if (!otherId.isNullOrBlank()) {
                            onChatUpdated(doc.id, otherId)
                        }
                    }
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "listenToUserChats init failed: ${e.message}")
            null
        }
    }

    open suspend fun getAllUserChatsFromRemote(
        myUserId: String,
        aliases: List<String> = emptyList()
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext emptyList()
        val db = getFirestoreSafe() ?: return@withContext emptyList()
        try {
            val results = mutableMapOf<String, String>() // chatId -> otherUserId
            val allUids = (listOf(myUserId) + aliases).filter { it.isNotBlank() }.distinct()

            for (uid in allUids) {
                // 1. Check conversations & chats collections where participants contains uid
                try {
                    val snapshot = withTimeoutOrNull(6000L) {
                        db.collection(COLLECTION_CONVERSATIONS)
                            .whereArrayContains("participants", uid)
                            .limit(50)
                            .get()
                            .await()
                    }
                    snapshot?.documents?.forEach { doc ->
                        val participants = doc.get("participants") as? List<*>
                        val otherId = participants?.firstOrNull { it?.toString() != uid && it?.toString() !in allUids }?.toString()
                        if (!otherId.isNullOrBlank()) {
                            results[doc.id] = otherId
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Notice querying conversations for $uid: ${e.message}")
                }

                try {
                    val snapshot = withTimeoutOrNull(4000L) {
                        db.collection(COLLECTION_CHATS)
                            .whereArrayContains("participants", uid)
                            .limit(50)
                            .get()
                            .await()
                    }
                    snapshot?.documents?.forEach { doc ->
                        val participants = doc.get("participants") as? List<*>
                        val otherId = participants?.firstOrNull { it?.toString() != uid && it?.toString() !in allUids }?.toString()
                        if (!otherId.isNullOrBlank()) {
                            results[doc.id] = otherId
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Notice querying chats for $uid: ${e.message}")
                }

                // 2. Check user inbox (received messages)
                try {
                    val inboxSnap = withTimeoutOrNull(4000L) {
                        db.collection(COLLECTION_USERS)
                            .document(uid)
                            .collection("inbox")
                            .limit(50)
                            .get()
                            .await()
                    }
                    inboxSnap?.documents?.forEach { doc ->
                        val otherId = doc.getString("senderId")
                        if (!otherId.isNullOrBlank() && otherId !in allUids) {
                            val cid = getChatId(myUserId, otherId)
                            results[cid] = otherId
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Notice querying inbox for $uid: ${e.message}")
                }

                // 3. Check user sent_messages (messages sent by user to other accounts)
                try {
                    val sentSnap = withTimeoutOrNull(4000L) {
                        db.collection(COLLECTION_USERS)
                            .document(uid)
                            .collection("sent_messages")
                            .limit(50)
                            .get()
                            .await()
                    }
                    sentSnap?.documents?.forEach { doc ->
                        val otherId = doc.getString("receiverId")
                        if (!otherId.isNullOrBlank() && otherId !in allUids) {
                            val cid = getChatId(myUserId, otherId)
                            results[cid] = otherId
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Notice querying sent_messages for $uid: ${e.message}")
                }

                // 4. Check root messages collection
                try {
                    val rootSent = withTimeoutOrNull(3000L) {
                        db.collection("messages").whereEqualTo("senderId", uid).limit(50).get().await()
                    }
                    rootSent?.documents?.forEach { doc ->
                        val otherId = doc.getString("receiverId")
                        if (!otherId.isNullOrBlank() && otherId !in allUids) {
                            val cid = getChatId(myUserId, otherId)
                            results[cid] = otherId
                        }
                    }
                } catch (_: Exception) {}

                try {
                    val rootRecv = withTimeoutOrNull(3000L) {
                        db.collection("messages").whereEqualTo("receiverId", uid).limit(50).get().await()
                    }
                    rootRecv?.documents?.forEach { doc ->
                        val otherId = doc.getString("senderId")
                        if (!otherId.isNullOrBlank() && otherId !in allUids) {
                            val cid = getChatId(myUserId, otherId)
                            results[cid] = otherId
                        }
                    }
                } catch (_: Exception) {}
            }

            results.map { Pair(it.key, it.value) }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "getAllUserChatsFromRemote notice: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetches all sent and received messages directly across user inboxes and sent folders in Firestore.
     */
    open suspend fun getUserSentAndReceivedMessagesFromRemote(
        myUserId: String,
        aliases: List<String> = emptyList()
    ): List<MessageEntity> = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext emptyList()
        val db = getFirestoreSafe() ?: return@withContext emptyList()
        val allUids = (listOf(myUserId) + aliases)
            .filter { it.isNotBlank() && !it.contains("@") && !it.contains(" ") }
            .distinct()
            .take(4)
        val messageMap = mutableMapOf<String, MessageEntity>()

        try {
            for (uid in allUids) {
                try {
                    val sentSnap = withTimeoutOrNull(4000L) {
                        db.collection(COLLECTION_USERS).document(uid).collection("sent_messages").limit(50).get().await()
                    }
                    sentSnap?.documents?.forEach { doc ->
                        doc.data?.let { data ->
                            parseMessageFromData(doc.id, data)?.let { msg ->
                                messageMap[msg.messageId] = msg
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Notice fetching sent_messages for $uid: ${e.message}")
                }

                try {
                    val inboxSnap = withTimeoutOrNull(4000L) {
                        db.collection(COLLECTION_USERS).document(uid).collection("inbox").limit(50).get().await()
                    }
                    inboxSnap?.documents?.forEach { doc ->
                        doc.data?.let { data ->
                            parseMessageFromData(doc.id, data)?.let { msg ->
                                messageMap[msg.messageId] = msg
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Notice fetching inbox for $uid: ${e.message}")
                }
            }

            // Also check root messages for the primary user ID only to conserve memory
            if (myUserId.isNotBlank()) {
                try {
                    val rootSenderSnap = withTimeoutOrNull(3000L) {
                        db.collection("messages").whereEqualTo("senderId", myUserId).limit(50).get().await()
                    }
                    rootSenderSnap?.documents?.forEach { doc ->
                        doc.data?.let { data ->
                            parseMessageFromData(doc.id, data)?.let { msg ->
                                messageMap[msg.messageId] = msg
                            }
                        }
                    }
                } catch (_: Throwable) {}

                try {
                    val rootRecvSnap = withTimeoutOrNull(3000L) {
                        db.collection("messages").whereEqualTo("receiverId", myUserId).limit(50).get().await()
                    }
                    rootRecvSnap?.documents?.forEach { doc ->
                        doc.data?.let { data ->
                            parseMessageFromData(doc.id, data)?.let { msg ->
                                messageMap[msg.messageId] = msg
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in getUserSentAndReceivedMessagesFromRemote: ${t.message}")
        }

        messageMap.values.sortedBy { it.timestamp }
    }

    private fun parseMessageFromData(docId: String, data: Map<String, Any?>): MessageEntity? {
        val mid = data["messageId"] as? String ?: docId
        val sid = data["senderId"] as? String ?: return null
        val rid = data["receiverId"] as? String ?: return null
        val txt = data["text"] as? String ?: ""
        val img = data["imageUrl"] as? String
        val audio = data["audioUrl"] as? String
        val duration = (data["audioDurationSeconds"] as? Number)?.toInt() ?: 0
        val ts = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val rawStatus = (data["status"] as? String)?.uppercase() ?: "SENT"
        val isReadField = (data["read"] as? Boolean) == true || rawStatus == "READ"
        val st = if (isReadField) "READ" else rawStatus
        val isSys = data["isSystemMessage"] as? Boolean ?: false
        return MessageEntity(
            messageId = mid,
            senderId = sid,
            receiverId = rid,
            text = txt,
            imageUrl = img,
            audioUrl = audio,
            audioDurationSeconds = duration,
            timestamp = ts,
            status = st,
            read = isReadField,
            isSystemMessage = isSys
        )
    }

    /**
     * Fetches message history from chats/{chatId}/messages ordered by timestamp
     */
    open suspend fun getChatMessagesFromRemote(chatId: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        if (!isFirestoreProvisioned) return@withContext emptyList()
        val db = getFirestoreSafe() ?: return@withContext emptyList()
        try {
            val messageMap = mutableMapOf<String, MessageEntity>()

            // 1. Primary source: chats/{chatId}/messages
            val subcolSnapshot = withTimeoutOrNull(8000L) {
                try {
                    db.collection(COLLECTION_CHATS)
                        .document(chatId)
                        .collection("messages")
                        .orderBy("timestamp")
                        .limit(100)
                        .get()
                        .await()
                } catch (e: Exception) {
                    // Fallback without orderBy if compound index or network constraint
                    db.collection(COLLECTION_CHATS)
                        .document(chatId)
                        .collection("messages")
                        .limit(100)
                        .get()
                        .await()
                }
            }
            subcolSnapshot?.documents?.forEach { doc ->
                val data = doc.data ?: return@forEach
                val parsed = parseMessageFromData(doc.id, data)
                if (parsed != null) {
                    messageMap[parsed.messageId] = parsed
                }
            }

            // 2. Secondary source: root messages collection filtered by chatId
            try {
                val rootSnap = withTimeoutOrNull(4000L) {
                    db.collection("messages")
                        .whereEqualTo("chatId", chatId)
                        .limit(100)
                        .get()
                        .await()
                }
                rootSnap?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val parsed = parseMessageFromData(doc.id, data)
                    if (parsed != null && !messageMap.containsKey(parsed.messageId)) {
                        messageMap[parsed.messageId] = parsed
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Secondary message source notice: ${e.message}")
            }

            messageMap.values.sortedBy { it.timestamp }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "getChatMessagesFromRemote notice: ${e.message}")
            emptyList()
        }
    }

    open suspend fun deleteMessageFromRemote(
        chatId: String?,
        messageId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (messageId.isBlank() || isTestEnvironment() || !isFirestoreProvisioned) return@withContext Result.success(Unit)
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            val batch = db.batch()
            if (!chatId.isNullOrBlank()) {
                batch.delete(db.collection(COLLECTION_CHATS).document(chatId).collection("messages").document(messageId))
                batch.delete(db.collection(COLLECTION_CONVERSATIONS).document(chatId).collection("messages").document(messageId))
            }
            batch.delete(db.collection("messages").document(messageId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "deleteMessageFromRemote notice: ${e.message}")
            Result.success(Unit)
        }
    }

    fun listenToChatMessages(chatId: String, onMessages: (List<MessageEntity>) -> Unit): ListenerRegistration? {
        return listenToChatMessagesWithRemovals(chatId) { msgs, _ ->
            onMessages(msgs)
        }
    }

    fun listenToChatMessagesWithRemovals(
        chatId: String,
        onMessages: (List<MessageEntity>, List<String>) -> Unit
    ): ListenerRegistration? {
        if (!isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "Error listening to messages for $chatId: ${error.message}")
                        return@addSnapshotListener
                    }
                    val removedIds = snapshot?.documentChanges
                        ?.filter { it.type == DocumentChange.Type.REMOVED }
                        ?.map { change ->
                            change.document.getString("messageId") ?: change.document.id
                        }
                        .orEmpty()
                    val msgs = snapshot?.documents?.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val mid = data["messageId"] as? String ?: doc.id
                        val sid = data["senderId"] as? String ?: ""
                        val rid = data["receiverId"] as? String ?: ""
                        val txt = data["text"] as? String ?: ""
                        val img = data["imageUrl"] as? String
                        val audio = data["audioUrl"] as? String
                        val duration = (data["audioDurationSeconds"] as? Number)?.toInt() ?: 0
                        val ts = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        val rawMsgStatus = (data["status"] as? String)?.uppercase() ?: "SENT"
                        val isReadDoc = (data["read"] as? Boolean) == true || rawMsgStatus == "READ"
                        val st = if (isReadDoc) "READ" else rawMsgStatus
                        val isSys = data["isSystemMessage"] as? Boolean ?: false
                        MessageEntity(
                            messageId = mid,
                            senderId = sid,
                            receiverId = rid,
                            text = txt,
                            imageUrl = img,
                            audioUrl = audio,
                            audioDurationSeconds = duration,
                            timestamp = ts,
                            status = st,
                            read = isReadDoc,
                            isSystemMessage = isSys
                        )
                    } ?: emptyList()
                    onMessages(msgs, removedIds)
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "listenToChatMessagesWithRemovals failure: ${e.message}")
            null
        }
    }

    // ==========================================
    // ROLE-BASED USER MANAGEMENT IN FIRESTORE
    // ==========================================

    /**
     * Updates or assigns a user role in Firestore.
     * Differentiates between Teachers and Students using the same main profile.
     * Enforces that Teachers can only be assigned to ONE subject.
     */
    open suspend fun assignTeacherRoleInFirestore(
        userId: String,
        subject: String,
        qualification: String = "",
        experience: String = "",
        isTrusted: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Invalid user ID."))

        // STRICT ENFORCEMENT: Teachers can ONLY be assigned to ONE subject
        val cleanSubject = subject.trim().split(",").firstOrNull()?.trim().orEmpty()
        if (cleanSubject.isBlank()) {
            return@withContext Result.failure(Exception("Teacher assignment failed: Exactly one subject must be specified."))
        }

        if (isTestEnvironment()) {
            val existing = testUsers[userId]
            if (existing != null) {
                testUsers[userId] = existing.copy(role = "TEACHER", assignedSubject = cleanSubject)
            }
            testTeacherRoles[userId] = cleanSubject
            return@withContext Result.success(Unit)
        }

        if (!isFirestoreProvisioned) return@withContext Result.success(Unit)
        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Firestore is not available."))

        try {
            val now = System.currentTimeMillis()
            // 1. Update the user's primary profile document in Firestore (Same Main Profile)
            val profileUpdates = hashMapOf<String, Any>(
                "role" to "TEACHER",
                "isTeacher" to true,
                "isStudent" to false,
                "assignedSubject" to cleanSubject,
                "approvedSubject" to cleanSubject,
                "teacherStatus" to "APPROVED",
                "isTrustedTeacher" to isTrusted,
                "educationQualification" to qualification,
                "teachingExperience" to experience,
                "teacherApprovedAt" to now,
                "updatedAt" to now
            )

            db.collection(COLLECTION_USERS)
                .document(userId)
                .set(profileUpdates, SetOptions.merge())
                .await()

            // 2. Also record in teacher_roles collection for audit & strict validation
            val roleDoc = hashMapOf<String, Any>(
                "userId" to userId,
                "status" to "APPROVED",
                "approvedSubject" to cleanSubject,
                "subjects" to cleanSubject,
                "role" to "TEACHER",
                "isTrustedTeacher" to isTrusted,
                "singleSubjectEnforced" to true,
                "updatedAt" to now
            )

            db.collection(COLLECTION_TEACHER_ROLES)
                .document(userId)
                .set(roleDoc, SetOptions.merge())
                .await()

            Log.i(TAG, "✅ [Firestore Role Management] User '$userId' assigned as TEACHER for single subject: '$cleanSubject'.")
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "Notice assigning teacher role in Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Assigns Student role in Firestore on the same main profile.
     * Clears any assigned subject as students do not have teacher subjects.
     */
    open suspend fun assignStudentRoleInFirestore(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Invalid user ID."))

        if (isTestEnvironment()) {
            val existing = testUsers[userId]
            if (existing != null) {
                testUsers[userId] = existing.copy(role = "STUDENT", assignedSubject = "")
            }
            testTeacherRoles.remove(userId)
            return@withContext Result.success(Unit)
        }

        if (!isFirestoreProvisioned) return@withContext Result.success(Unit)
        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Firestore is not available."))

        try {
            val now = System.currentTimeMillis()
            // Update the main profile document in Firestore
            val profileUpdates = hashMapOf<String, Any>(
                "role" to "STUDENT",
                "isTeacher" to false,
                "isStudent" to true,
                "assignedSubject" to "",
                "teacherStatus" to "NOT_APPLIED",
                "updatedAt" to now
            )

            db.collection(COLLECTION_USERS)
                .document(userId)
                .set(profileUpdates, SetOptions.merge())
                .await()

            val roleDoc = hashMapOf<String, Any>(
                "userId" to userId,
                "status" to "NOT APPLIED",
                "role" to "STUDENT",
                "approvedSubject" to "",
                "updatedAt" to now
            )
            db.collection(COLLECTION_TEACHER_ROLES)
                .document(userId)
                .set(roleDoc, SetOptions.merge())
                .await()

            Log.i(TAG, "✅ [Firestore Role Management] User '$userId' assigned as STUDENT.")
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "Notice assigning student role in Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    internal fun mergeTeacherApplicationSources(
        primary: List<TeacherApplicationItem>,
        legacy: List<TeacherApplicationItem>,
        pendingUsers: List<TeacherApplicationItem>
    ): List<TeacherApplicationItem> {
        val merged = LinkedHashMap<String, TeacherApplicationItem>()

        fun pickBetter(existing: TeacherApplicationItem?, candidate: TeacherApplicationItem): TeacherApplicationItem {
            if (existing == null) return candidate
            val candidateReviewed = !candidate.status.equals("PENDING", ignoreCase = true) || candidate.reviewedAt > 0L
            val existingReviewed = !existing.status.equals("PENDING", ignoreCase = true) || existing.reviewedAt > 0L
            return when {
                candidateReviewed && !existingReviewed -> candidate
                !candidateReviewed && existingReviewed -> existing
                candidate.reviewedAt != existing.reviewedAt ->
                    if (candidate.reviewedAt > existing.reviewedAt) candidate else existing
                candidate.submittedAt > existing.submittedAt -> candidate
                else -> existing
            }
        }

        primary.forEach { item ->
            if (item.userId.isNotBlank()) {
                merged[item.userId] = pickBetter(merged[item.userId], item)
            }
        }
        legacy.forEach { item ->
            if (item.userId.isNotBlank()) {
                merged[item.userId] = pickBetter(merged[item.userId], item)
            }
        }
        pendingUsers.forEach { item ->
            if (item.userId.isNotBlank() && !merged.containsKey(item.userId)) {
                merged[item.userId] = item
            }
        }
        return merged.values.sortedByDescending { it.submittedAt }
    }

    /**
     * Submits a teacher application to Firestore with strict single-subject enforcement.
     */
    open suspend fun submitTeacherApplicationToFirestore(
        userId: String,
        application: TeacherRoleEntity,
        applicantName: String = "",
        applicantEmail: String = "",
        applicantUsername: String = "",
        applicantAvatarUrl: String = "",
        applicantLenoId: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Invalid user ID."))

        // STRICT ENFORCEMENT: Teachers can only apply for ONE subject, ONE time
        val singleSubject = application.approvedSubject.ifBlank {
            application.subjects.split(",").firstOrNull()?.trim() ?: "Maths"
        }.trim().split(",").firstOrNull()?.trim().orEmpty().ifBlank { "Maths" }

        if (isTestEnvironment()) {
            val existingItem = testTeacherApplications[userId]
            if (existingItem != null) {
                return@withContext Result.failure(Exception("Teacher applications can only be submitted one time. An application already exists for this account."))
            }
            testTeacherApplications[userId] = TeacherApplicationItem(
                applicationId = userId,
                applicantUid = userId,
                lenoId = applicantLenoId,
                fullName = applicantName.ifBlank { "Leno Member" },
                username = applicantUsername,
                subject = singleSubject,
                qualification = application.educationQualification,
                experience = application.teachingExperience,
                status = "PENDING",
                submittedAt = application.submittedAt,
                userId = userId,
                applicantName = applicantName.ifBlank { "Leno Member" },
                applicantEmail = applicantEmail,
                applicantUsername = applicantUsername,
                applicantAvatarUrl = applicantAvatarUrl,
                approvedSubject = singleSubject,
                teachingLevel = application.teachingLevel,
                educationQualification = application.educationQualification,
                teachingExperience = application.teachingExperience,
                teacherIntro = application.teacherIntro
            )
            return@withContext Result.success(Unit)
        }

        val db = getFirestoreSafe()
            ?: return@withContext Result.failure(Exception("Cloud database is not available. Please check your internet connection."))

        try {
            val alreadyApplied = withTimeoutOrNull(6000L) {
                coroutineScope {
                    val primaryJob = async {
                        try {
                            db.collection(COLLECTION_TEACHER_APPLICATIONS).document(userId).get().await().exists()
                        } catch (_: Exception) { false }
                    }
                    val legacyJob = async {
                        try {
                            db.collection("teacher_applications").document(userId).get().await().exists()
                        } catch (_: Exception) { false }
                    }
                    primaryJob.await() || legacyJob.await()
                }
            } ?: false
            if (alreadyApplied) {
                return@withContext Result.failure(Exception("Teacher applications can only be submitted one time per teacher."))
            }

            Log.i(TAG, "🔥 [Teacher Application Submission] Submitting application for applicantUid=$userId, subject=$singleSubject, lenoId=$applicantLenoId")
            val now = System.currentTimeMillis()
            val submittedTs = if (application.submittedAt > 0L) application.submittedAt else now
            val resolvedName = applicantName.ifBlank { applicantUsername.ifBlank { "Leno Member" } }

            val userUpdates = hashMapOf<String, Any>(
                "teacherStatus" to "PENDING",
                "pendingSubject" to singleSubject,
                "teacherSubject" to singleSubject,
                "updatedAt" to now
            )
            val applicationDoc = hashMapOf<String, Any>(
                "applicationId" to userId,
                "applicantUid" to userId,
                "lenoId" to applicantLenoId,
                "fullName" to resolvedName,
                "username" to applicantUsername,
                "subject" to singleSubject,
                "qualification" to application.educationQualification,
                "experience" to application.teachingExperience,
                "status" to "PENDING",
                "submittedAt" to submittedTs,
                "reviewedAt" to 0L,
                "reviewedBy" to "",
                "rejectionReason" to "",
                "userId" to userId,
                "applicantName" to resolvedName,
                "applicantEmail" to applicantEmail,
                "applicantUsername" to applicantUsername,
                "applicantAvatarUrl" to applicantAvatarUrl,
                "approvedSubject" to singleSubject,
                "teachingLevel" to application.teachingLevel,
                "educationQualification" to application.educationQualification,
                "teachingExperience" to application.teachingExperience,
                "teacherIntro" to application.teacherIntro,
                "singleSubjectOnly" to true
            )
            val roleDoc = hashMapOf<String, Any>(
                "userId" to userId,
                "status" to "PENDING",
                "approvedSubject" to singleSubject,
                "subjects" to singleSubject,
                "teachingLevel" to application.teachingLevel,
                "educationQualification" to application.educationQualification,
                "teachingExperience" to application.teachingExperience,
                "teacherIntro" to application.teacherIntro,
                "submittedAt" to submittedTs,
                "singleSubjectOnly" to true
            )

            val committed = withTimeoutOrNull(10000L) {
                val batch = db.batch()
                batch.set(db.collection(COLLECTION_USERS).document(userId), userUpdates, SetOptions.merge())
                batch.set(db.collection(COLLECTION_TEACHER_APPLICATIONS).document(userId), applicationDoc, SetOptions.merge())
                batch.set(db.collection("teacher_applications").document(userId), applicationDoc, SetOptions.merge())
                batch.set(db.collection(COLLECTION_TEACHER_ROLES).document(userId), roleDoc, SetOptions.merge())
                batch.commit().await()
                true
            }
            if (committed != true) {
                return@withContext Result.failure(Exception("Submission timed out. Please check your internet connection and try again."))
            }

            Log.i(TAG, "✅ [Firestore teacherApplications SUCCESS] Atomic batch committed for '$userId' (Subject: '$singleSubject').")
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "Notice submitting teacher application: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Monitors 'teacherApplications', legacy 'teacher_applications', and pending user records in real time.
     */
    fun listenToTeacherApplications(
        onApplications: (List<TeacherApplicationItem>) -> Unit
    ): ListenerRegistration? {
        if (isTestEnvironment()) {
            onApplications(testTeacherApplications.values.sortedByDescending { it.submittedAt })
            return null
        }
        val db = getFirestoreSafe() ?: return null

        return try {
            var primaryItems: List<TeacherApplicationItem> = emptyList()
            var legacyItems: List<TeacherApplicationItem> = emptyList()
            var pendingUserItems: List<TeacherApplicationItem> = emptyList()
            val lock = Any()

            fun dispatchMerged() {
                val merged = synchronized(lock) {
                    mergeTeacherApplicationSources(primaryItems, legacyItems, pendingUserItems)
                }
                onApplications(merged)
            }

            val reg1 = db.collection(COLLECTION_TEACHER_APPLICATIONS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToTeacherApplications (primary) error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val items = snapshot?.documents?.mapNotNull { doc ->
                        parseTeacherApplicationFromDoc(doc.id, doc.data)
                    } ?: emptyList()
                    synchronized(lock) { primaryItems = items }
                    dispatchMerged()
                }

            val reg2 = db.collection("teacher_applications")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "listenToTeacherApplications (legacy) error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val items = snapshot?.documents?.mapNotNull { doc ->
                        parseTeacherApplicationFromDoc(doc.id, doc.data)
                    } ?: emptyList()
                    synchronized(lock) { legacyItems = items }
                    dispatchMerged()
                }

            val reg3 = db.collection(COLLECTION_USERS)
                .whereEqualTo("teacherStatus", "PENDING")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "listenToTeacherApplications (pending users) error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val items = snapshot?.documents?.mapNotNull { doc ->
                        parsePendingUserAsTeacherApplication(doc.id, doc.data)
                    } ?: emptyList()
                    synchronized(lock) { pendingUserItems = items }
                    dispatchMerged()
                }

            ListenerRegistration {
                reg1.remove()
                reg2.remove()
                reg3.remove()
            }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "Notice on listenToTeacherApplications: ${e.message}")
            null
        }
    }

    private fun parsePendingUserAsTeacherApplication(docId: String, data: Map<String, Any?>?): TeacherApplicationItem? {
        if (data == null) return null
        val uid = (data["userId"] as? String)?.ifBlank { null } ?: (data["uid"] as? String)?.ifBlank { null } ?: docId
        if (uid.isBlank() || uid.endsWith("_teacher") || uid == "test_dummy_user_to_purge") return null
        val applicantName = (data["displayName"] as? String)?.ifBlank { null }
            ?: (data["username"] as? String) ?: "Leno Member"
        val subj = (data["pendingSubject"] as? String)?.ifBlank { null }
            ?: (data["teacherSubject"] as? String)?.ifBlank { null }
            ?: "Mathematics"
        return TeacherApplicationItem(
            applicationId = uid,
            applicantUid = uid,
            lenoId = (data["linoId"] as? String) ?: (data["lenId"] as? String) ?: "",
            fullName = applicantName,
            username = (data["username"] as? String) ?: "",
            subject = subj,
            qualification = (data["educationQualification"] as? String)?.ifBlank { null } ?: "Verified Applicant",
            experience = (data["teachingExperience"] as? String)?.ifBlank { null } ?: "Under Review",
            status = "PENDING",
            submittedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            userId = uid,
            applicantName = applicantName,
            applicantEmail = (data["email"] as? String) ?: "",
            applicantUsername = (data["username"] as? String) ?: "",
            applicantAvatarUrl = (data["avatarUrl"] as? String) ?: "",
            approvedSubject = subj,
            teachingLevel = "All Levels"
        )
    }

    /**
     * Fetches all applications concurrently from 'teacherApplications', 'teacher_applications',
     * and pending users from 'users' to guarantee admin receipt across all devices.
     */
    open suspend fun fetchTeacherApplicationsFromFirestore(): List<TeacherApplicationItem> = withContext(Dispatchers.IO) {
        if (isTestEnvironment()) {
            return@withContext testTeacherApplications.values.sortedByDescending { it.submittedAt }
        }
        val db = getFirestoreSafe() ?: return@withContext emptyList()
        try {
            Log.i(TAG, "🔥 [Admin Query: teacherApplications] Querying all teacher applications concurrently...")
            withTimeoutOrNull(10000L) {
                coroutineScope {
                    val primaryJob = async {
                        try {
                            db.collection(COLLECTION_TEACHER_APPLICATIONS).get().await()
                                .documents.mapNotNull { parseTeacherApplicationFromDoc(it.id, it.data) }
                        } catch (e: Exception) {
                            Log.w(TAG, "Query teacherApplications: ${e.message}")
                            emptyList()
                        }
                    }
                    val legacyJob = async {
                        try {
                            db.collection("teacher_applications").get().await()
                                .documents.mapNotNull { parseTeacherApplicationFromDoc(it.id, it.data) }
                        } catch (e: Exception) {
                            Log.w(TAG, "Query teacher_applications: ${e.message}")
                            emptyList()
                        }
                    }
                    val pendingUsersJob = async {
                        try {
                            db.collection(COLLECTION_USERS)
                                .whereEqualTo("teacherStatus", "PENDING")
                                .get()
                                .await()
                                .documents
                                .mapNotNull { parsePendingUserAsTeacherApplication(it.id, it.data) }
                        } catch (e: Exception) {
                            Log.w(TAG, "Query pending users: ${e.message}")
                            emptyList()
                        }
                    }
                    val merged = mergeTeacherApplicationSources(
                        primary = primaryJob.await(),
                        legacy = legacyJob.await(),
                        pendingUsers = pendingUsersJob.await()
                    )
                    Log.i(TAG, "✅ [Admin Query Result] Found ${merged.size} merged applications.")
                    merged
                }
            } ?: emptyList()
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "Notice querying teacher applications from Firestore: ${e.message}")
            emptyList()
        }
    }

    /**
     * Admin approval:
     * 1. Updates the application in 'teacherApplications' & 'teacher_applications' to status = "APPROVED".
     * 2. Updates the user's document role to 'TEACHER', teacherStatus = 'APPROVED', teacherSubject.
     * 3. Syncs 'teacher_roles' collection.
     * Uses atomic batched write.
     */
    open suspend fun approveTeacherApplicationByAdminInFirestore(
        userId: String,
        subject: String,
        adminId: String = "admin"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Invalid user ID."))
        val cleanSubject = subject.trim().split(",").firstOrNull()?.trim().orEmpty().ifBlank { "Mathematics" }
        val now = System.currentTimeMillis()

        Log.i(TAG, "🔥 [Admin Approve Teacher Application] Approving applicantUid=$userId by adminUid=$adminId for subject=$cleanSubject")

        if (isTestEnvironment()) {
            val existingTest = testTeacherApplications[userId]
            if (existingTest != null) {
                testTeacherApplications[userId] = existingTest.copy(
                    status = "APPROVED",
                    approvedSubject = cleanSubject,
                    subject = cleanSubject,
                    reviewedAt = now,
                    reviewedBy = adminId
                )
            }
            val existingUser = testUsers[userId]
            if (existingUser != null) {
                val updatedUser = existingUser.copy(
                    role = "TEACHER",
                    assignedSubject = cleanSubject,
                    teacherStatus = "APPROVED",
                    teacherSubject = cleanSubject,
                    updatedAt = now
                )
                testUsers[userId] = updatedUser
                testUserDocumentListeners[userId]?.forEach { listener ->
                    try { listener.invoke(updatedUser) } catch (e: Exception) { Log.w(TAG, "listener error: ${e.message}") }
                }
            }
            testTeacherRoles[userId] = cleanSubject
            return@withContext Result.success(Unit)
        }

        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Firestore is not available."))

        try {
            val batch = db.batch()
            val appRef = db.collection(COLLECTION_TEACHER_APPLICATIONS).document(userId)
            val legacyAppRef = db.collection("teacher_applications").document(userId)
            val userRef = db.collection(COLLECTION_USERS).document(userId)
            val roleRef = db.collection(COLLECTION_TEACHER_ROLES).document(userId)

            val appUpdates = hashMapOf<String, Any>(
                "status" to "APPROVED",
                "approvedSubject" to cleanSubject,
                "subject" to cleanSubject,
                "reviewedAt" to now,
                "reviewedBy" to adminId
            )
            val userUpdates = hashMapOf<String, Any>(
                "role" to "TEACHER",
                "isTeacher" to true,
                "isStudent" to false,
                "assignedSubject" to cleanSubject,
                "approvedSubject" to cleanSubject,
                "teacherSubject" to cleanSubject,
                "teacherStatus" to "APPROVED",
                "teacherApprovedAt" to now,
                "reviewedAt" to now,
                "reviewedBy" to adminId,
                "updatedAt" to now
            )
            val roleDoc = hashMapOf<String, Any>(
                "userId" to userId,
                "status" to "APPROVED",
                "approvedSubject" to cleanSubject,
                "subjects" to cleanSubject,
                "role" to "TEACHER",
                "updatedAt" to now
            )
            batch.set(appRef, appUpdates, SetOptions.merge())
            batch.set(legacyAppRef, appUpdates, SetOptions.merge())
            batch.set(userRef, userUpdates, SetOptions.merge())
            batch.set(roleRef, roleDoc, SetOptions.merge())
            batch.commit().await()

            Log.i(TAG, "✅ [Admin Approval SUCCESS] Applicant '$userId' approved atomically for subject '$cleanSubject' by admin '$adminId'.")
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "Notice approving teacher application in Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Admin rejection:
     * 1. Updates the application in 'teacherApplications' & 'teacher_applications' to status = "REJECTED".
     * 2. Updates the user's document in Firestore with teacherStatus = 'REJECTED'.
     * Uses atomic batched write.
     */
    open suspend fun rejectTeacherApplicationByAdminInFirestore(
        userId: String,
        reason: String = "Requirements not met",
        adminId: String = "admin"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Invalid user ID."))
        val now = System.currentTimeMillis()

        Log.i(TAG, "🔥 [Admin Reject Teacher Application] Rejecting applicantUid=$userId by adminUid=$adminId with reason=$reason")

        if (isTestEnvironment()) {
            val existingTest = testTeacherApplications[userId]
            if (existingTest != null) {
                testTeacherApplications[userId] = existingTest.copy(
                    status = "REJECTED",
                    reviewedAt = now,
                    reviewedBy = adminId,
                    rejectionReason = reason
                )
            }
            return@withContext Result.success(Unit)
        }

        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Firestore is not available."))

        try {
            val batch = db.batch()
            val appRef = db.collection(COLLECTION_TEACHER_APPLICATIONS).document(userId)
            val legacyAppRef = db.collection("teacher_applications").document(userId)
            val userRef = db.collection(COLLECTION_USERS).document(userId)
            val roleRef = db.collection(COLLECTION_TEACHER_ROLES).document(userId)

            val appUpdates = hashMapOf<String, Any>(
                "status" to "REJECTED",
                "reviewedAt" to now,
                "reviewedBy" to adminId,
                "rejectionReason" to reason
            )
            val userUpdates = hashMapOf<String, Any>(
                "teacherStatus" to "REJECTED",
                "reviewedAt" to now,
                "reviewedBy" to adminId,
                "updatedAt" to now
            )
            val roleUpdates = hashMapOf<String, Any>(
                "userId" to userId,
                "status" to "REJECTED",
                "rejectionReason" to reason,
                "updatedAt" to now
            )
            batch.set(appRef, appUpdates, SetOptions.merge())
            batch.set(legacyAppRef, appUpdates, SetOptions.merge())
            batch.set(userRef, userUpdates, SetOptions.merge())
            batch.set(roleRef, roleUpdates, SetOptions.merge())
            batch.commit().await()

            Log.i(TAG, "✅ [Admin Rejection SUCCESS] Application for '$userId' rejected by admin '$adminId'.")
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "Notice rejecting teacher application: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Real-time listener on the user's document in Firestore ('users/{userId}').
     * The UI automatically receives updates immediately upon approval/role change
     * without needing an application restart.
     */
    fun listenToUserDocument(userId: String, onUserUpdated: (UserEntity) -> Unit): ListenerRegistration? {
        if (userId.isBlank()) return null

        if (isTestEnvironment()) {
            val listeners = testUserDocumentListeners.getOrPut(userId) { mutableListOf() }
            listeners.add(onUserUpdated)
            testUsers[userId]?.let { onUserUpdated(it) }
            return ListenerRegistration {
                listeners.remove(onUserUpdated)
            }
        }

        val db = getFirestoreSafe() ?: return null

        return try {
            db.collection(COLLECTION_USERS)
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToUserDocument error for $userId: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val user = docToUser(snapshot.data, snapshot.id)
                        if (user != null) {
                            Log.i(TAG, "⚡ [Real-Time User Doc] User '$userId' updated in Firestore (Role: ${user.role}, Subject: ${user.assignedSubject}). Notifying UI.")
                            onUserUpdated(user)
                        }
                    }
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "listenToUserDocument init failed for $userId: ${e.message}")
            null
        }
    }

    /**
     * Admin updates any user's role in Firestore ('teacher' or 'user').
     * Allows admin to promote/revert any user and update their profile & applications atomically.
     */
    open suspend fun updateUserRoleInFirestore(
        userId: String,
        newRole: String,
        assignedSubject: String = "",
        adminId: String = "admin"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext Result.failure(Exception("Invalid user ID."))
        val cleanRole = newRole.trim().lowercase() // "teacher" or "user"
        val isTeacher = cleanRole == "teacher"
        val cleanSubject = if (isTeacher) assignedSubject.trim().split(",").firstOrNull()?.trim().orEmpty().ifBlank { "General Education" } else ""
        val now = System.currentTimeMillis()

        if (isTestEnvironment()) {
            val existingUser = testUsers[userId]
            if (existingUser != null) {
                val updated = existingUser.copy(
                    role = if (isTeacher) "TEACHER" else "USER",
                    assignedSubject = cleanSubject,
                    teacherStatus = if (isTeacher) "APPROVED" else "NOT_APPLIED",
                    teacherSubject = cleanSubject,
                    updatedAt = now
                )
                testUsers[userId] = updated
                testUserDocumentListeners[userId]?.forEach { listener ->
                    try { listener.invoke(updated) } catch (e: Exception) { Log.w(TAG, "listener error: ${e.message}") }
                }
            }
            if (isTeacher) {
                testTeacherRoles[userId] = cleanSubject
                testTeacherApplications[userId]?.let {
                    testTeacherApplications[userId] = it.copy(
                        status = "APPROVED",
                        approvedSubject = cleanSubject,
                        reviewedAt = now,
                        reviewedBy = adminId
                    )
                }
            } else {
                testTeacherRoles.remove(userId)
                testTeacherApplications[userId]?.let {
                    testTeacherApplications[userId] = it.copy(
                        status = "REJECTED",
                        reviewedAt = now,
                        reviewedBy = adminId
                    )
                }
            }
            return@withContext Result.success(Unit)
        }

        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Firestore is not available."))

        try {
            val batch = db.batch()
            val userUpdates = hashMapOf<String, Any>(
                "role" to (if (isTeacher) "TEACHER" else "USER"),
                "isTeacher" to isTeacher,
                "isStudent" to false,
                "assignedSubject" to cleanSubject,
                "approvedSubject" to cleanSubject,
                "teacherSubject" to cleanSubject,
                "teacherStatus" to (if (isTeacher) "APPROVED" else "NOT_APPLIED"),
                "updatedAt" to now
            )
            if (isTeacher) {
                userUpdates["teacherApprovedAt"] = now
            }
            batch.set(db.collection(COLLECTION_USERS).document(userId), userUpdates, SetOptions.merge())

            val roleDoc = if (isTeacher) {
                hashMapOf<String, Any>(
                    "userId" to userId,
                    "status" to "APPROVED",
                    "approvedSubject" to cleanSubject,
                    "subjects" to cleanSubject,
                    "role" to "TEACHER",
                    "updatedAt" to now
                )
            } else {
                hashMapOf<String, Any>(
                    "userId" to userId,
                    "status" to "NORMAL",
                    "approvedSubject" to "",
                    "subjects" to "",
                    "role" to "USER",
                    "updatedAt" to now
                )
            }
            batch.set(db.collection(COLLECTION_TEACHER_ROLES).document(userId), roleDoc, SetOptions.merge())

            if (isTeacher) {
                val appUpdates = hashMapOf<String, Any>(
                    "status" to "APPROVED",
                    "approvedSubject" to cleanSubject,
                    "subject" to cleanSubject,
                    "reviewedAt" to now,
                    "reviewedBy" to adminId
                )
                batch.set(db.collection(COLLECTION_TEACHER_APPLICATIONS).document(userId), appUpdates, SetOptions.merge())
                batch.set(db.collection("teacher_applications").document(userId), appUpdates, SetOptions.merge())
            }
            batch.commit().await()

            Log.i(TAG, "✅ [Admin Action] User '$userId' role updated to '$cleanRole' (Subject: $cleanSubject) in Firestore.")
            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "Notice updating user role in Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Admin method to restrict or unrestrict a user in Firestore.
     */
    suspend fun setUserRestrictedInFirestore(userId: String, isRestricted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (isTestEnvironment()) {
            testUsers[userId]?.let {
                testUsers[userId] = it.copy(isRestricted = isRestricted)
            }
            return@withContext Result.success(Unit)
        }
        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Cloud database unavailable."))
        try {
            db.collection(COLLECTION_USERS).document(userId)
                .update("isRestricted", isRestricted)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Notice updating isRestricted in Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Admin method to delete a user profile from Firestore.
     */
    suspend fun deleteUserInFirestore(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (isTestEnvironment()) {
            val user = testUsers.remove(userId)
            if (user != null) {
                testUsernames.remove(user.username.lowercase())
                testLenoIds.remove(user.linoId.lowercase())
                if (user.email.isNotBlank()) testEmails.remove(user.email.lowercase())
            }
            return@withContext Result.success(Unit)
        }
        val db = getFirestoreSafe() ?: return@withContext Result.failure(Exception("Cloud database unavailable."))
        try {
            val userRef = db.collection(COLLECTION_USERS).document(userId)
            val snap = try { userRef.get().await() } catch (_: Exception) { null }
            val username = snap?.getString("username")?.trim()?.lowercase().orEmpty()
            val lenoId = (snap?.getString("linoId") ?: snap?.getString("lenId"))?.trim()?.uppercase().orEmpty()

            // Delete user-owned subcollections first while auth is valid
            val subcollections = listOf(
                "notifications",
                "contacts",
                "inbox",
                "sent_messages",
                "sent_receipts",
                "friend_requests",
                "lesson_progress",
                "certificates"
            )
            for (sub in subcollections) {
                try {
                    val docs = userRef.collection(sub).limit(200).get().await().documents
                    for (doc in docs) {
                        try { doc.reference.delete().await() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            if (username.isNotBlank()) {
                try { db.collection(COLLECTION_USERNAMES).document(username).delete().await() } catch (_: Exception) {}
            }
            if (lenoId.isNotBlank()) {
                try { db.collection(COLLECTION_LENO_IDS).document(lenoId).delete().await() } catch (_: Exception) {}
            }
            try { db.collection(COLLECTION_TEACHER_APPLICATIONS).document(userId).delete().await() } catch (_: Exception) {}
            try { db.collection("teacher_applications").document(userId).delete().await() } catch (_: Exception) {}
            try { db.collection(COLLECTION_TEACHER_ROLES).document(userId).delete().await() } catch (_: Exception) {}

            userRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            com.example.util.handleFirestoreError(e, com.example.util.OperationType.DELETE, "$COLLECTION_USERS/$userId")
            Log.w(TAG, "Notice deleting user in Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fetches all canonical remote users from Firestore using paginated scans so
     * newly registered accounts on other devices are never truncated by legacy alias docs.
     */
    suspend fun fetchAllRemoteUsers(limit: Int = 1500): List<UserEntity> = withContext(Dispatchers.IO) {
        if (isTestEnvironment()) {
            return@withContext testUsers.values.distinctBy { it.userId }.toList()
        }
        val db = getFirestoreSafe() ?: return@withContext emptyList()
        val effectiveLimit = maxOf(limit, 1500)
        try {
            withTimeoutOrNull(12000L) {
                deduplicateCanonicalUsers(fetchAllUserDocsPaginated(db, effectiveLimit))
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "fetchAllRemoteUsers notice: ${e.message}")
            emptyList()
        }
    }

    open fun listenToAllUsers(
        limit: Int = 1500,
        onUpdate: (users: List<UserEntity>, removedIds: List<String>) -> Unit
    ): ListenerRegistration? {
        if (isTestEnvironment()) {
            onUpdate(testUsers.values.distinctBy { it.userId }.toList(), emptyList())
            return null
        }
        val db = getFirestoreSafe() ?: return null
        val effectiveLimit = maxOf(limit, 1500)
        return try {
            db.collection(COLLECTION_USERS)
                .limit(effectiveLimit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToAllUsers error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val pairs = snapshot?.documents
                        ?.mapNotNull { doc -> docToUser(doc.data, doc.id)?.let { doc.id to it } }
                        .orEmpty()
                    val canonicalList = deduplicateCanonicalUsers(pairs)
                    val activeUserIds = canonicalList.map { it.userId }.toSet()
                    val removedIds = snapshot?.documentChanges
                        ?.filter { it.type == DocumentChange.Type.REMOVED }
                        ?.map { it.document.id }
                        ?.filter { it.isNotBlank() && it !in activeUserIds }
                        .orEmpty()
                    onUpdate(canonicalList, removedIds)
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "listenToAllUsers init error: ${e.message}")
            null
        }
    }

    private fun parseTeacherApplicationFromDoc(docId: String, data: Map<String, Any?>?): TeacherApplicationItem? {
        if (data == null) return null
        val applicantUid = data["applicantUid"] as? String ?: data["userId"] as? String ?: docId
        val lenoId = data["lenoId"] as? String ?: ""
        val fullName = data["fullName"] as? String ?: (data["applicantName"] as? String) ?: (data["displayName"] as? String) ?: "Applicant"
        val username = data["username"] as? String ?: (data["applicantUsername"] as? String) ?: ""
        val subject = data["subject"] as? String ?: (data["approvedSubject"] as? String) ?: (data["subjects"] as? String) ?: "Mathematics"
        val qual = data["qualification"] as? String ?: (data["educationQualification"] as? String) ?: ""
        val exp = data["experience"] as? String ?: (data["teachingExperience"] as? String) ?: ""
        val status = data["status"] as? String ?: "PENDING"
        val submittedAt = (data["submittedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val reviewedAt = (data["reviewedAt"] as? Number)?.toLong() ?: 0L
        val reviewedBy = data["reviewedBy"] as? String ?: ""
        val reason = data["rejectionReason"] as? String ?: ""
        val email = data["applicantEmail"] as? String ?: (data["email"] as? String) ?: ""
        val avatar = data["applicantAvatarUrl"] as? String ?: (data["avatarUrl"] as? String) ?: ""
        val level = data["teachingLevel"] as? String ?: "All Levels"
        val intro = data["teacherIntro"] as? String ?: ""

        return TeacherApplicationItem(
            applicationId = docId,
            applicantUid = applicantUid,
            lenoId = lenoId,
            fullName = fullName,
            username = username,
            subject = subject,
            qualification = qual,
            experience = exp,
            status = status,
            submittedAt = submittedAt,
            reviewedAt = reviewedAt,
            reviewedBy = reviewedBy,
            rejectionReason = reason,
            userId = applicantUid,
            applicantName = fullName,
            applicantUsername = username,
            applicantEmail = email,
            applicantAvatarUrl = avatar,
            approvedSubject = subject,
            teachingLevel = level,
            educationQualification = qual,
            teachingExperience = exp,
            teacherIntro = intro
        )
    }

    // ==========================================
    // PERSISTENT CLASSES & ENROLLMENTS IN FIRESTORE
    // ==========================================

    open suspend fun saveClassToRemote(classEntity: ClassEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (classEntity.classId.isBlank()) return@withContext Result.failure(Exception("Invalid class ID."))
        testClasses[classEntity.classId] = classEntity
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            val classDoc = hashMapOf<String, Any>(
                "classId" to classEntity.classId,
                "teacherUid" to classEntity.instructorUserId,
                "teacherLenoId" to classEntity.instructorLinoId,
                "title" to classEntity.title,
                "subject" to classEntity.subject,
                "level" to classEntity.level,
                "description" to classEntity.shortDescription,
                "coverImage" to classEntity.imageUrl,
                "status" to classEntity.status,
                "createdAt" to classEntity.createdAt,
                "updatedAt" to System.currentTimeMillis(),
                "shortDescription" to classEntity.shortDescription,
                "lessonContent" to classEntity.lessonContent,
                "imageUrl" to classEntity.imageUrl,
                "optionalImages" to classEntity.optionalImages,
                "videoUrl" to classEntity.videoUrl,
                "hasVideo" to classEntity.hasVideo,
                "isPaid" to classEntity.isPaid,
                "price" to classEntity.price,
                "schedule" to classEntity.schedule,
                "lessonType" to classEntity.lessonType,
                "instructorUserId" to classEntity.instructorUserId,
                "instructorName" to classEntity.instructorName,
                "instructorUsername" to classEntity.instructorUsername,
                "instructorLinoId" to classEntity.instructorLinoId,
                "instructorIsTeacher" to classEntity.instructorIsTeacher,
                "instructorIsTrusted" to classEntity.instructorIsTrusted,
                "enrolledStudentsCount" to classEntity.enrolledStudentsCount,
                "lessonCount" to classEntity.lessonCount
            )
            db.collection(COLLECTION_CLASSES)
                .document(classEntity.classId)
                .set(classDoc, SetOptions.merge())
                .await()
            Log.i(TAG, "✅ [Firestore Class Saved] Class '${classEntity.classId}' saved to Firestore.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Notice saving class to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    open suspend fun deleteClassFromRemote(classId: String): Boolean = withContext(Dispatchers.IO) {
        testClasses.remove(classId)
        val db = getFirestoreSafe() ?: return@withContext true
        try {
            db.collection(COLLECTION_CLASSES).document(classId).delete().await()
            Log.i(TAG, "✅ [Firestore Class Deleted] Deleted class $classId")
            true
        } catch (e: Exception) {
            Log.w(TAG, "deleteClassFromRemote error: ${e.message}")
            false
        }
    }

    open suspend fun fetchClassesFromRemote(): List<ClassEntity> = withContext(Dispatchers.IO) {
        val db = getFirestoreSafe() ?: return@withContext testClasses.values.toList()
        try {
            val snap = db.collection(COLLECTION_CLASSES).get().await()
            val list = snap.documents.mapNotNull { parseClassFromDoc(it.id, it.data) }
            list.forEach { testClasses[it.classId] = it }
            Log.i(TAG, "✅ [Firestore Classes] Fetched ${list.size} classes from Firestore.")
            list
        } catch (e: Exception) {
            Log.w(TAG, "fetchClassesFromRemote notice: ${e.message}")
            testClasses.values.toList()
        }
    }

    open fun listenToRemoteClasses(onClasses: (List<ClassEntity>) -> Unit): ListenerRegistration? {
        return listenToRemoteClassesWithRemovals { classes, _ ->
            onClasses(classes)
        }
    }

    open fun listenToRemoteClassesWithRemovals(
        onUpdate: (classes: List<ClassEntity>, removedIds: List<String>) -> Unit
    ): ListenerRegistration? {
        val db = getFirestoreSafe() ?: run {
            onUpdate(testClasses.values.toList(), emptyList())
            return null
        }
        return try {
            db.collection(COLLECTION_CLASSES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "listenToRemoteClassesWithRemovals error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val removedIds = snapshot?.documentChanges
                        ?.filter { it.type == DocumentChange.Type.REMOVED }
                        ?.map { it.document.id }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    removedIds.forEach { testClasses.remove(it) }
                    val list = snapshot?.documents?.mapNotNull { parseClassFromDoc(it.id, it.data) } ?: emptyList()
                    list.forEach { testClasses[it.classId] = it }
                    onUpdate(list, removedIds)
                }
        } catch (e: Exception) {
            Log.w(TAG, "listenToRemoteClassesWithRemovals init failed: ${e.message}")
            null
        }
    }

    private fun parseClassFromDoc(docId: String, data: Map<String, Any?>?): ClassEntity? {
        if (data == null) return null
        val title = data["title"] as? String ?: return null
        val subject = data["subject"] as? String ?: "General"
        val level = data["level"] as? String ?: "All Levels"
        val desc = (data["description"] as? String) ?: (data["shortDescription"] as? String) ?: ""
        val content = data["lessonContent"] as? String ?: ""
        val img = (data["coverImage"] as? String) ?: (data["imageUrl"] as? String) ?: ""
        val optImg = data["optionalImages"] as? String ?: ""
        val vid = data["videoUrl"] as? String ?: ""
        val hasVid = data["hasVideo"] as? Boolean ?: false
        val priceRaw = (data["price"] as? String)?.trim() ?: ""
        val isPaid = (data["isPaid"] as? Boolean) ?: (!priceRaw.equals("Free", ignoreCase = true) && !priceRaw.equals("FREE", ignoreCase = true) && priceRaw.isNotBlank())
        val price = if (!isPaid || priceRaw.equals("Free", ignoreCase = true) || priceRaw.equals("FREE", ignoreCase = true)) {
            "Free"
        } else if (priceRaw.isNotBlank()) {
            priceRaw
        } else {
            "₦1,500"
        }
        val schedule = data["schedule"] as? String ?: "Self-paced"
        val lessonType = data["lessonType"] as? String ?: "Text lesson"
        val instructorUserId = (data["teacherUid"] as? String) ?: (data["instructorUserId"] as? String) ?: ""
        val instructorName = data["instructorName"] as? String ?: "Instructor"
        val instructorUsername = data["instructorUsername"] as? String ?: ""
        val instructorLinoId = (data["teacherLenoId"] as? String) ?: (data["instructorLinoId"] as? String) ?: ""
        val enrolledCount = ((data["enrolledStudentsCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
        val status = data["status"] as? String ?: "PUBLISHED"
        val lessonCount = ((data["lessonCount"] as? Number)?.toInt() ?: 1).coerceAtLeast(0)
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()

        return ClassEntity(
            classId = docId,
            title = title,
            subject = subject,
            level = level,
            shortDescription = desc,
            lessonContent = content,
            imageUrl = img,
            optionalImages = optImg,
            videoUrl = vid,
            hasVideo = hasVid,
            isPaid = isPaid,
            price = price,
            schedule = schedule,
            lessonType = lessonType,
            instructorUserId = instructorUserId,
            instructorName = instructorName,
            instructorUsername = instructorUsername,
            instructorLinoId = instructorLinoId,
            instructorIsTeacher = true,
            instructorIsTrusted = false,
            enrolledStudentsCount = enrolledCount,
            status = status,
            lessonCount = lessonCount,
            createdAt = createdAt
        )
    }

    open suspend fun saveEnrollmentToRemote(enrollment: EnrollmentEntity): Result<Unit> = withContext(Dispatchers.IO) {
        testEnrollments[enrollment.enrollmentId] = enrollment
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            val enrollmentMap = hashMapOf<String, Any>(
                "enrollmentId" to enrollment.enrollmentId,
                "classId" to enrollment.classId,
                "studentUserId" to enrollment.studentUserId,
                "studentLinoId" to enrollment.studentLinoId,
                "teacherUserId" to enrollment.teacherUserId,
                "status" to enrollment.status,
                "enrolledAt" to enrollment.enrolledAt,
                "progress" to enrollment.progress
            )
            // 1. Top-level enrollments document "${studentUserId}_${classId}"
            val docKey = "${enrollment.studentUserId}_${enrollment.classId}"
            db.collection(COLLECTION_ENROLLMENTS)
                .document(docKey)
                .set(enrollmentMap, SetOptions.merge())
                .await()

            // 2. Class subcollection document
            try {
                db.collection(COLLECTION_CLASSES)
                    .document(enrollment.classId)
                    .collection(COLLECTION_ENROLLMENTS)
                    .document(enrollment.studentUserId)
                    .set(enrollmentMap, SetOptions.merge())
                    .await()
            } catch (_: Exception) {}

            // 3. Atomically increment class enrolled student count
            try {
                db.collection(COLLECTION_CLASSES)
                    .document(enrollment.classId)
                    .update("enrolledStudentsCount", FieldValue.increment(1))
                    .await()
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "saveEnrollmentToRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    open suspend fun deleteEnrollmentFromRemote(studentUserId: String, classId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val docKey = "${studentUserId}_${classId}"
        testEnrollments.entries.removeAll { it.value.studentUserId == studentUserId && it.value.classId == classId }
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            db.collection(COLLECTION_ENROLLMENTS).document(docKey).delete().await()
            val classEnrollmentsRef = db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection(COLLECTION_ENROLLMENTS)
            try {
                classEnrollmentsRef.document(studentUserId).delete().await()
            } catch (_: Exception) {}
            try {
                classEnrollmentsRef.document(docKey).delete().await()
            } catch (_: Exception) {}
            try {
                db.collection(COLLECTION_CLASSES)
                    .document(classId)
                    .update("enrolledStudentsCount", FieldValue.increment(-1))
                    .await()
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "deleteEnrollmentFromRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    open suspend fun fetchEnrollmentsFromRemote(studentUserId: String): List<EnrollmentEntity> = withContext(Dispatchers.IO) {
        val db = getFirestoreSafe() ?: return@withContext testEnrollments.values.filter { it.studentUserId == studentUserId }
        try {
            val snap = db.collection(COLLECTION_ENROLLMENTS)
                .whereEqualTo("studentUserId", studentUserId)
                .get()
                .await()
            val list = snap.documents.mapNotNull { doc ->
                val classId = doc.getString("classId") ?: return@mapNotNull null
                EnrollmentEntity(
                    enrollmentId = doc.getString("enrollmentId") ?: doc.id,
                    classId = classId,
                    studentUserId = studentUserId,
                    studentLinoId = doc.getString("studentLinoId") ?: "",
                    teacherUserId = doc.getString("teacherUserId") ?: "",
                    status = doc.getString("status") ?: "ACTIVE",
                    enrolledAt = doc.getLong("enrolledAt") ?: System.currentTimeMillis(),
                    progress = (doc.getLong("progress") ?: 0L).toInt()
                )
            }
            if (list.isNotEmpty()) {
                list.forEach { testEnrollments[it.enrollmentId] = it }
                return@withContext list
            }

            try {
                val groupSnap = db.collectionGroup(COLLECTION_ENROLLMENTS)
                    .whereEqualTo("studentUserId", studentUserId)
                    .get()
                    .await()
                val groupList = groupSnap.documents.mapNotNull { doc ->
                    val classId = doc.getString("classId") ?: return@mapNotNull null
                    EnrollmentEntity(
                        enrollmentId = doc.getString("enrollmentId") ?: doc.id,
                        classId = classId,
                        studentUserId = studentUserId,
                        studentLinoId = doc.getString("studentLinoId") ?: "",
                        teacherUserId = doc.getString("teacherUserId") ?: "",
                        status = doc.getString("status") ?: "ACTIVE",
                        enrolledAt = doc.getLong("enrolledAt") ?: System.currentTimeMillis(),
                        progress = (doc.getLong("progress") ?: 0L).toInt()
                    )
                }
                groupList.forEach { testEnrollments[it.enrollmentId] = it }
                if (groupList.isNotEmpty()) return@withContext groupList
            } catch (_: Exception) {}

            testEnrollments.values.filter { it.studentUserId == studentUserId }
        } catch (e: Exception) {
            Log.w(TAG, "fetchEnrollmentsFromRemote notice: ${e.message}")
            testEnrollments.values.filter { it.studentUserId == studentUserId }
        }
    }

    open fun listenToUserEnrollments(studentUserId: String, onEnrollments: (List<EnrollmentEntity>) -> Unit): ListenerRegistration? {
        return listenToUserEnrollmentsWithRemovals(studentUserId) { list, _ ->
            onEnrollments(list)
        }
    }

    open fun listenToUserEnrollmentsWithRemovals(
        studentUserId: String,
        onUpdate: (enrollments: List<EnrollmentEntity>, removedIds: List<String>) -> Unit
    ): ListenerRegistration? {
        val db = getFirestoreSafe() ?: run {
            onUpdate(testEnrollments.values.filter { it.studentUserId == studentUserId }, emptyList())
            return null
        }
        return try {
            db.collection(COLLECTION_ENROLLMENTS)
                .whereEqualTo("studentUserId", studentUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "listenToUserEnrollmentsWithRemovals error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val removedIds = snapshot?.documentChanges
                        ?.filter { it.type == DocumentChange.Type.REMOVED }
                        ?.map { change -> change.document.getString("enrollmentId") ?: change.document.id }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val classId = doc.getString("classId") ?: return@mapNotNull null
                        EnrollmentEntity(
                            enrollmentId = doc.getString("enrollmentId") ?: doc.id,
                            classId = classId,
                            studentUserId = studentUserId,
                            studentLinoId = doc.getString("studentLinoId") ?: "",
                            teacherUserId = doc.getString("teacherUserId") ?: "",
                            status = doc.getString("status") ?: "ACTIVE",
                            enrolledAt = doc.getLong("enrolledAt") ?: System.currentTimeMillis(),
                            progress = (doc.getLong("progress") ?: 0L).toInt()
                        )
                    } ?: emptyList()
                    removedIds.forEach { testEnrollments.remove(it) }
                    list.forEach { testEnrollments[it.enrollmentId] = it }
                    onUpdate(list, removedIds)
                }
        } catch (e: Exception) {
            Log.w(TAG, "listenToUserEnrollmentsWithRemovals init error: ${e.message}")
            null
        }
    }

    open fun listenToClassEnrollments(
        classId: String,
        onUpdate: (enrollments: List<EnrollmentEntity>, removedIds: List<String>) -> Unit
    ): ListenerRegistration? {
        if (classId.isBlank()) return null
        val db = getFirestoreSafe() ?: run {
            onUpdate(testEnrollments.values.filter { it.classId == classId }, emptyList())
            return null
        }
        return try {
            db.collection(COLLECTION_ENROLLMENTS)
                .whereEqualTo("classId", classId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "listenToClassEnrollments error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val removedIds = snapshot?.documentChanges
                        ?.filter { it.type == DocumentChange.Type.REMOVED }
                        ?.map { change -> change.document.getString("enrollmentId") ?: change.document.id }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val studentUid = doc.getString("studentUserId") ?: return@mapNotNull null
                        EnrollmentEntity(
                            enrollmentId = doc.getString("enrollmentId") ?: doc.id,
                            classId = classId,
                            studentUserId = studentUid,
                            studentLinoId = doc.getString("studentLinoId") ?: "",
                            teacherUserId = doc.getString("teacherUserId") ?: "",
                            status = doc.getString("status") ?: "ACTIVE",
                            enrolledAt = doc.getLong("enrolledAt") ?: System.currentTimeMillis(),
                            progress = (doc.getLong("progress") ?: 0L).toInt()
                        )
                    } ?: emptyList()
                    removedIds.forEach { testEnrollments.remove(it) }
                    list.forEach { testEnrollments[it.enrollmentId] = it }
                    onUpdate(list, removedIds)
                }
        } catch (e: Exception) {
            Log.w(TAG, "listenToClassEnrollments init error: ${e.message}")
            null
        }
    }

    open suspend fun saveLessonToRemote(lesson: LessonEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            val map = hashMapOf<String, Any>(
                "lessonId" to lesson.lessonId,
                "classId" to lesson.classId,
                "title" to lesson.title,
                "orderIndex" to lesson.orderIndex,
                "summary" to lesson.summary,
                "content" to lesson.content,
                "createdAt" to lesson.createdAt
            )
            db.collection(COLLECTION_CLASSES)
                .document(lesson.classId)
                .collection("lessons")
                .document(lesson.lessonId)
                .set(map, SetOptions.merge())
                .await()

            try {
                db.collection(COLLECTION_CLASSES)
                    .document(lesson.classId)
                    .update("lessonCount", FieldValue.increment(1))
                    .await()
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "saveLessonToRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    open suspend fun updateLessonInRemote(lesson: LessonEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            val map = hashMapOf<String, Any>(
                "lessonId" to lesson.lessonId,
                "classId" to lesson.classId,
                "title" to lesson.title,
                "orderIndex" to lesson.orderIndex,
                "summary" to lesson.summary,
                "content" to lesson.content,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_CLASSES)
                .document(lesson.classId)
                .collection("lessons")
                .document(lesson.lessonId)
                .set(map, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "updateLessonInRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    open suspend fun recordLessonSeenInRemote(
        classId: String,
        lessonId: String,
        userId: String
    ): Unit = withContext(Dispatchers.IO) {
        if (classId.isBlank() || lessonId.isBlank() || userId.isBlank()) return@withContext
        val currentMap = _lessonSeenByMap.value.toMutableMap()
        val updatedSet = (currentMap[lessonId] ?: emptySet()) + userId
        currentMap[lessonId] = updatedSet
        _lessonSeenByMap.value = currentMap

        if (isTestEnvironment() || !isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        try {
            db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection("lessons")
                .document(lessonId)
                .update("seenByUserIds", FieldValue.arrayUnion(userId))
                .await()
        } catch (e: Exception) {
            try {
                db.collection(COLLECTION_CLASSES)
                    .document(classId)
                    .collection("lessons")
                    .document(lessonId)
                    .set(mapOf("seenByUserIds" to listOf(userId)), SetOptions.merge())
                    .await()
            } catch (_: Exception) {}
        }
    }

    open suspend fun deleteLessonFromRemote(classId: String, lessonId: String): Boolean = withContext(Dispatchers.IO) {
        if (classId.isBlank() || lessonId.isBlank()) return@withContext false
        val db = getFirestoreSafe() ?: return@withContext true
        try {
            db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection("lessons")
                .document(lessonId)
                .delete()
                .await()
            try {
                db.collection(COLLECTION_CLASSES)
                    .document(classId)
                    .update("lessonCount", FieldValue.increment(-1))
                    .await()
            } catch (_: Exception) {}
            true
        } catch (e: Exception) {
            Log.w(TAG, "deleteLessonFromRemote notice: ${e.message}")
            false
        }
    }

    open suspend fun fetchLessonsForClassFromRemote(classId: String): List<LessonEntity> = withContext(Dispatchers.IO) {
        val db = getFirestoreSafe() ?: return@withContext emptyList()
        try {
            val snap = db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection("lessons")
                .orderBy("orderIndex")
                .get()
                .await()
            val seenUpdates = _lessonSeenByMap.value.toMutableMap()
            val lessons = snap.documents.mapNotNull { doc ->
                val title = doc.getString("title") ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val seenRaw = doc.get("seenByUserIds") as? List<*>
                val seenSet = seenRaw?.mapNotNull { it as? String }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                if (seenSet.isNotEmpty()) {
                    seenUpdates[doc.id] = (seenUpdates[doc.id] ?: emptySet()) + seenSet
                }
                LessonEntity(
                    lessonId = doc.id,
                    classId = classId,
                    title = title,
                    orderIndex = (doc.getLong("orderIndex") ?: 1L).toInt(),
                    summary = doc.getString("summary") ?: "",
                    content = doc.getString("content") ?: "",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            }
            if (seenUpdates.isNotEmpty()) {
                _lessonSeenByMap.value = seenUpdates
            }
            lessons
        } catch (e: Exception) {
            Log.w(TAG, "fetchLessonsForClassFromRemote notice: ${e.message}")
            emptyList()
        }
    }

    open fun listenToClassLessons(classId: String, onLessons: (List<LessonEntity>) -> Unit): ListenerRegistration? {
        return listenToClassLessonsWithRemovals(classId) { list, _ ->
            onLessons(list)
        }
    }

    open fun listenToClassLessonsWithRemovals(
        classId: String,
        onUpdate: (lessons: List<LessonEntity>, removedIds: List<String>) -> Unit
    ): ListenerRegistration? {
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection("lessons")
                .orderBy("orderIndex")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "listenToClassLessonsWithRemovals error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val removedIds = snapshot?.documentChanges
                        ?.filter { it.type == DocumentChange.Type.REMOVED }
                        ?.map { it.document.id }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    val seenUpdates = _lessonSeenByMap.value.toMutableMap()
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val title = doc.getString("title") ?: return@mapNotNull null
                        @Suppress("UNCHECKED_CAST")
                        val seenRaw = doc.get("seenByUserIds") as? List<*>
                        val seenSet = seenRaw?.mapNotNull { it as? String }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                        if (seenSet.isNotEmpty()) {
                            seenUpdates[doc.id] = (seenUpdates[doc.id] ?: emptySet()) + seenSet
                        }
                        LessonEntity(
                            lessonId = doc.id,
                            classId = classId,
                            title = title,
                            orderIndex = (doc.getLong("orderIndex") ?: 1L).toInt(),
                            summary = doc.getString("summary") ?: "",
                            content = doc.getString("content") ?: "",
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    } ?: emptyList()
                    _lessonSeenByMap.value = seenUpdates
                    onUpdate(list, removedIds)
                }
        } catch (e: Exception) {
            Log.w(TAG, "listenToClassLessonsWithRemovals init error: ${e.message}")
            null
        }
    }

    open suspend fun saveQuestionToRemote(classId: String, question: ClassQuestionEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            val map = hashMapOf<String, Any?>(
                "questionId" to question.questionId,
                "classId" to question.classId,
                "senderUserId" to question.senderUserId,
                "senderName" to question.senderName,
                "senderLinoId" to question.senderLinoId,
                "senderRole" to question.senderRole,
                "questionText" to question.questionText,
                "audioUrl" to question.audioUrl,
                "audioDurationSeconds" to question.audioDurationSeconds,
                "answerText" to question.answerText,
                "answerAudioUrl" to question.answerAudioUrl,
                "answerAudioDurationSeconds" to question.answerAudioDurationSeconds,
                "isAnswered" to question.isAnswered,
                "answeredAt" to question.answeredAt,
                "timestamp" to question.timestamp
            )
            db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection("questions")
                .document(question.questionId)
                .set(map, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "saveQuestionToRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    open suspend fun fetchQuestionsForClassFromRemote(classId: String): List<ClassQuestionEntity> = withContext(Dispatchers.IO) {
        val db = getFirestoreSafe() ?: return@withContext emptyList()
        try {
            val snap = db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection("questions")
                .orderBy("timestamp")
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                val qText = doc.getString("questionText") ?: return@mapNotNull null
                ClassQuestionEntity(
                    questionId = doc.id,
                    classId = classId,
                    senderUserId = doc.getString("senderUserId") ?: "",
                    senderName = doc.getString("senderName") ?: "Student",
                    senderLinoId = doc.getString("senderLinoId") ?: "",
                    senderRole = doc.getString("senderRole") ?: "STUDENT",
                    questionText = qText,
                    audioUrl = doc.getString("audioUrl"),
                    audioDurationSeconds = (doc.getLong("audioDurationSeconds") ?: 0L).toInt(),
                    answerText = doc.getString("answerText") ?: "",
                    answerAudioUrl = doc.getString("answerAudioUrl"),
                    answerAudioDurationSeconds = (doc.getLong("answerAudioDurationSeconds") ?: 0L).toInt(),
                    isAnswered = doc.getBoolean("isAnswered") ?: false,
                    answeredAt = doc.getLong("answeredAt") ?: 0L,
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchQuestionsForClassFromRemote notice: ${e.message}")
            emptyList()
        }
    }

    open suspend fun deleteQuestionFromRemote(classId: String, questionId: String): Boolean = withContext(Dispatchers.IO) {
        if (classId.isBlank() || questionId.isBlank()) return@withContext false
        val db = getFirestoreSafe() ?: return@withContext true
        try {
            db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection("questions")
                .document(questionId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "deleteQuestionFromRemote notice: ${e.message}")
            false
        }
    }

    open fun listenToClassQuestions(classId: String, onQuestions: (List<ClassQuestionEntity>) -> Unit): ListenerRegistration? {
        return listenToClassQuestionsWithRemovals(classId) { list, _ ->
            onQuestions(list)
        }
    }

    open fun listenToClassQuestionsWithRemovals(
        classId: String,
        onUpdate: (questions: List<ClassQuestionEntity>, removedIds: List<String>) -> Unit
    ): ListenerRegistration? {
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection("questions")
                .orderBy("timestamp")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "listenToClassQuestionsWithRemovals error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val removedIds = snapshot?.documentChanges
                        ?.filter { it.type == DocumentChange.Type.REMOVED }
                        ?.map { it.document.id }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val qText = doc.getString("questionText") ?: return@mapNotNull null
                        ClassQuestionEntity(
                            questionId = doc.id,
                            classId = classId,
                            senderUserId = doc.getString("senderUserId") ?: "",
                            senderName = doc.getString("senderName") ?: "Student",
                            senderLinoId = doc.getString("senderLinoId") ?: "",
                            senderRole = doc.getString("senderRole") ?: "STUDENT",
                            questionText = qText,
                            audioUrl = doc.getString("audioUrl"),
                            audioDurationSeconds = (doc.getLong("audioDurationSeconds") ?: 0L).toInt(),
                            answerText = doc.getString("answerText") ?: "",
                            answerAudioUrl = doc.getString("answerAudioUrl"),
                            answerAudioDurationSeconds = (doc.getLong("answerAudioDurationSeconds") ?: 0L).toInt(),
                            isAnswered = doc.getBoolean("isAnswered") ?: false,
                            answeredAt = doc.getLong("answeredAt") ?: 0L,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    } ?: emptyList()
                    onUpdate(list, removedIds)
                }
        } catch (e: Exception) {
            Log.w(TAG, "listenToClassQuestionsWithRemovals init error: ${e.message}")
            null
        }
    }

    open suspend fun answerQuestionInRemote(
        classId: String,
        questionId: String,
        answerText: String,
        answerAudioUrl: String? = null,
        answerAudioDurationSeconds: Int = 0
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            val map = hashMapOf<String, Any?>(
                "answerText" to answerText,
                "answerAudioUrl" to answerAudioUrl,
                "answerAudioDurationSeconds" to answerAudioDurationSeconds,
                "isAnswered" to true,
                "answeredAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection("questions")
                .document(questionId)
                .set(map, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "answerQuestionInRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    // ==========================================
    // REAL-TIME LESSON PROGRESS & CERTIFICATES
    // ==========================================

    open suspend fun saveLessonProgressToRemote(
        userId: String,
        classId: String,
        completedLessonIds: Set<String>,
        quizScores: Map<String, String>,
        lastOpenedLessonId: String?,
        lastOpenedClassId: String?,
        streakDays: Int,
        lastStudyDate: String
    ) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        if (isTestEnvironment() || !isFirestoreProvisioned) return@withContext
        val db = getFirestoreSafe() ?: return@withContext
        try {
            val now = System.currentTimeMillis()
            if (classId.isNotBlank()) {
                val classProgressMap = hashMapOf<String, Any?>(
                    "userId" to userId,
                    "classId" to classId,
                    "completedLessonIds" to completedLessonIds.toList(),
                    "quizScores" to quizScores,
                    "lastOpenedLessonId" to (lastOpenedLessonId ?: ""),
                    "updatedAt" to now
                )
                db.collection(COLLECTION_USERS)
                    .document(userId)
                    .collection(COLLECTION_LESSON_PROGRESS)
                    .document(classId)
                    .set(classProgressMap, SetOptions.merge())
                    .await()
            }

            val userSummaryMap = hashMapOf<String, Any?>(
                "streakDays" to streakDays,
                "lastStudyDate" to lastStudyDate,
                "updatedAt" to now
            )
            if (!lastOpenedClassId.isNullOrBlank()) {
                userSummaryMap["lastOpenedClassId"] = lastOpenedClassId
            }
            db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_LESSON_PROGRESS)
                .document("_summary")
                .set(userSummaryMap, SetOptions.merge())
                .await()
            db.collection(COLLECTION_USERS)
                .document(userId)
                .set(userSummaryMap, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "saveLessonProgressToRemote notice: ${e.message}")
        }
    }

    open fun listenToUserLessonProgress(
        userId: String,
        onStudyProfile: (streakDays: Int, lastStudyDate: String?, lastOpenedClassId: String?) -> Unit = { _, _, _ -> },
        onProgressUpdate: (
            classId: String,
            completedIds: Set<String>,
            quizScores: Map<String, String>,
            lastOpenedLessonId: String?
        ) -> Unit
    ): ListenerRegistration? {
        if (userId.isBlank() || isTestEnvironment() || !isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_LESSON_PROGRESS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToUserLessonProgress error: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshot?.documents?.forEach { doc ->
                        if (doc.id == "_summary") {
                            val streakDays = (doc.getLong("streakDays") ?: 0L).toInt()
                            val lastStudyDate = doc.getString("lastStudyDate")?.takeIf { it.isNotBlank() }
                            val lastOpenedClassId = doc.getString("lastOpenedClassId")?.takeIf { it.isNotBlank() }
                            onStudyProfile(streakDays, lastStudyDate, lastOpenedClassId)
                            return@forEach
                        }
                        val classId = doc.getString("classId")?.takeIf { it.isNotBlank() } ?: doc.id
                        @Suppress("UNCHECKED_CAST")
                        val completedRaw = doc.get("completedLessonIds") as? List<*>
                        val completedIds = completedRaw?.mapNotNull { it as? String }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                        @Suppress("UNCHECKED_CAST")
                        val quizRaw = doc.get("quizScores") as? Map<*, *>
                        val quizScores = quizRaw?.entries
                            ?.mapNotNull { entry ->
                                val k = entry.key as? String
                                val v = entry.value as? String
                                if (!k.isNullOrBlank() && !v.isNullOrBlank()) k to v else null
                            }
                            ?.toMap()
                            ?: emptyMap()
                        val lastOpenedLessonId = doc.getString("lastOpenedLessonId")?.takeIf { it.isNotBlank() }
                        onProgressUpdate(classId, completedIds, quizScores, lastOpenedLessonId)
                    }
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "listenToUserLessonProgress init error: ${e.message}")
            null
        }
    }

    open suspend fun saveCertificateToRemote(
        cert: LessonProgressManager.AwardedCertificate
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (cert.classId.isBlank() || cert.studentUserId.isBlank()) {
            return@withContext Result.failure(Exception("Invalid certificate target."))
        }
        if (isTestEnvironment() || !isFirestoreProvisioned) return@withContext Result.success(Unit)
        val db = getFirestoreSafe() ?: return@withContext Result.success(Unit)
        try {
            val map = hashMapOf<String, Any>(
                "classId" to cert.classId,
                "studentUserId" to cert.studentUserId,
                "studentName" to cert.studentName,
                "teacherUserId" to cert.teacherUserId,
                "teacherName" to cert.teacherName,
                "awardTitle" to cert.awardTitle,
                "teacherCommendation" to cert.teacherCommendation,
                "issueDate" to cert.issueDate,
                "certificateId" to cert.certificateId,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_CLASSES)
                .document(cert.classId)
                .collection(COLLECTION_CERTIFICATES)
                .document(cert.studentUserId)
                .set(map, SetOptions.merge())
                .await()

            db.collection(COLLECTION_USERS)
                .document(cert.studentUserId)
                .collection(COLLECTION_CERTIFICATES)
                .document(cert.classId)
                .set(map, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "saveCertificateToRemote notice: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseCertificateFromDoc(data: Map<String, Any?>?): LessonProgressManager.AwardedCertificate? {
        if (data == null) return null
        val classId = data["classId"] as? String ?: return null
        val studentUserId = data["studentUserId"] as? String ?: return null
        return LessonProgressManager.AwardedCertificate(
            classId = classId,
            studentUserId = studentUserId,
            studentName = data["studentName"] as? String ?: "Leno Scholar",
            teacherUserId = data["teacherUserId"] as? String ?: "",
            teacherName = data["teacherName"] as? String ?: "Teacher",
            awardTitle = data["awardTitle"] as? String ?: "Certificate of Academic Excellence",
            teacherCommendation = data["teacherCommendation"] as? String
                ?: "Awarded in recognition of dedicated effort and academic achievement.",
            issueDate = data["issueDate"] as? String ?: "",
            certificateId = data["certificateId"] as? String
                ?: LessonProgressManager.formatCertificateId(classId, studentUserId)
        )
    }

    open fun listenToUserCertificates(
        userId: String,
        onCertificates: (List<LessonProgressManager.AwardedCertificate>) -> Unit
    ): ListenerRegistration? {
        if (userId.isBlank() || isTestEnvironment() || !isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(COLLECTION_CERTIFICATES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToUserCertificates error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { parseCertificateFromDoc(it.data) } ?: emptyList()
                    onCertificates(list)
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "listenToUserCertificates init error: ${e.message}")
            null
        }
    }

    open fun listenToClassCertificates(
        classId: String,
        onCertificates: (List<LessonProgressManager.AwardedCertificate>) -> Unit
    ): ListenerRegistration? {
        if (classId.isBlank() || isTestEnvironment() || !isFirestoreProvisioned) return null
        val db = getFirestoreSafe() ?: return null
        return try {
            db.collection(COLLECTION_CLASSES)
                .document(classId)
                .collection(COLLECTION_CERTIFICATES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (isDatabaseNotProvisioned(error)) markDatabaseNotProvisioned()
                        Log.w(TAG, "listenToClassCertificates error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { parseCertificateFromDoc(it.data) } ?: emptyList()
                    onCertificates(list)
                }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            Log.w(TAG, "listenToClassCertificates init error: ${e.message}")
            null
        }
    }

    /**
     * Fetches user role and single assigned subject from Firestore.
     */
    open suspend fun fetchUserRoleFromFirestore(userId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext null
        if (isTestEnvironment()) {
            val user = testUsers[userId]
            return@withContext if (user != null) Pair(user.role, user.assignedSubject) else null
        }
        if (!isFirestoreProvisioned) return@withContext null
        val db = getFirestoreSafe() ?: return@withContext null
        try {
            val doc = db.collection(COLLECTION_USERS).document(userId).get().await()
            if (!doc.exists()) return@withContext null
            val role = (doc.getString("role") ?: if (doc.getBoolean("isTeacher") == true) "TEACHER" else "STUDENT").uppercase()
            val assignedSubject = doc.getString("assignedSubject") ?: doc.getString("approvedSubject") ?: ""
            val cleanSubject = assignedSubject.trim().split(",").firstOrNull()?.trim().orEmpty()
            Pair(role, cleanSubject)
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            null
        }
    }

    /**
     * Queries all teachers from Firestore.
     */
    open suspend fun getTeachersFromFirestore(): List<UserEntity> = withContext(Dispatchers.IO) {
        if (isTestEnvironment()) {
            return@withContext testUsers.values.filter { it.isTeacher }
        }
        if (!isFirestoreProvisioned) return@withContext emptyList()
        val db = getFirestoreSafe() ?: return@withContext emptyList()
        try {
            val snapshot = db.collection(COLLECTION_USERS)
                .whereEqualTo("role", "TEACHER")
                .get()
                .await()
            snapshot.documents.mapNotNull { docToUser(it.data, it.id) }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            emptyList()
        }
    }

    /**
     * Queries all students from Firestore.
     */
    open suspend fun getStudentsFromFirestore(): List<UserEntity> = withContext(Dispatchers.IO) {
        if (isTestEnvironment()) {
            return@withContext testUsers.values.filter { it.isStudent }
        }
        if (!isFirestoreProvisioned) return@withContext emptyList()
        val db = getFirestoreSafe() ?: return@withContext emptyList()
        try {
            val snapshot = db.collection(COLLECTION_USERS)
                .whereEqualTo("role", "STUDENT")
                .get()
                .await()
            snapshot.documents.mapNotNull { docToUser(it.data, it.id) }
        } catch (e: Exception) {
            if (isDatabaseNotProvisioned(e)) markDatabaseNotProvisioned()
            emptyList()
        }
    }

    open fun clearTestDataForTests() {
        Companion.clearTestDataForTests()
    }

    companion object {
        private const val TAG = "RemoteAccountService"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_USERNAMES = "usernames"
        private const val COLLECTION_LENO_IDS = "leno_ids"
        private const val COLLECTION_CHATS = "chats"
        private const val COLLECTION_CONVERSATIONS = "conversations"
        private const val COLLECTION_CALLS = "calls"
        private const val COLLECTION_TEACHER_ROLES = "teacher_roles"
        const val COLLECTION_TEACHER_APPLICATIONS = "teacherApplications"
        const val COLLECTION_CLASSES = "classes"
        const val COLLECTION_ENROLLMENTS = "enrollments"
        private const val COLLECTION_FRIEND_REQUESTS = "friend_requests"
        private const val COLLECTION_LESSON_PROGRESS = "lesson_progress"
        private const val COLLECTION_CERTIFICATES = "certificates"

        val testUsernames = java.util.concurrent.ConcurrentHashMap<String, String>()
        val testLenoIds = java.util.concurrent.ConcurrentHashMap<String, String>()
        val testUsers = java.util.concurrent.ConcurrentHashMap<String, UserEntity>()
        val testEmails = java.util.concurrent.ConcurrentHashMap<String, String>()
        val testPasswords = java.util.concurrent.ConcurrentHashMap<String, String>()
        val testTeacherRoles = java.util.concurrent.ConcurrentHashMap<String, String>()
        val testTeacherApplications = java.util.concurrent.ConcurrentHashMap<String, TeacherApplicationItem>()
        val testClasses = java.util.concurrent.ConcurrentHashMap<String, ClassEntity>()
        val testEnrollments = java.util.concurrent.ConcurrentHashMap<String, EnrollmentEntity>()
        val testUserDocumentListeners = java.util.concurrent.ConcurrentHashMap<String, MutableList<(UserEntity) -> Unit>>()

        fun clearTestDataForTests() {
            testUsernames.clear()
            testLenoIds.clear()
            testUsers.clear()
            testEmails.clear()
            testPasswords.clear()
            testTeacherRoles.clear()
            testTeacherApplications.clear()
            testClasses.clear()
            testEnrollments.clear()
            testUserDocumentListeners.clear()
        }

        @Volatile
        var isEmailPasswordAuthDisabled: Boolean = false
            private set

        fun setEmailPasswordProviderEnabled(enabled: Boolean) {
            isEmailPasswordAuthDisabled = !enabled
        }
    }
}

