package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.entity.UserEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * AccountBackupStore provides resilient on-device persistent backup for Leno accounts.
 * This guarantees that even if Room database is cleared or container rebuilt,
 * accounts registered on this device can be retrieved and restored seamlessly.
 */
class AccountBackupStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    init {
        purgeDemoAccounts()
    }

    fun purgeDemoAccounts() {
        try {
            val accountsJson = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
            val array = JSONArray(accountsJson)
            val updatedArray = JSONArray()
            var modified = false
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uid = obj.optString("userId", "")
                if (uid == "test_dummy_user_to_purge") {
                    modified = true
                    continue
                }
                updatedArray.put(obj)
            }
            if (modified) {
                prefs.edit().putString(KEY_ACCOUNTS, updatedArray.toString()).apply()
                Log.i(TAG, "Purged dummy test accounts from AccountBackupStore.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to purge dummy accounts: ${e.message}")
        }
    }

    fun saveAccount(user: UserEntity) {
        try {
            val accountsJson = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
            val array = JSONArray(accountsJson)
            val updatedArray = JSONArray()

            // Remove existing entry ONLY if matching non-blank userId, username or linoId
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uid = obj.optString("userId", "").trim()
                val uname = obj.optString("username", "").trim()
                val lid = obj.optString("linoId", "").trim()

                val matchesUid = uid.isNotBlank() && user.userId.isNotBlank() && uid == user.userId
                val matchesUname = uname.isNotBlank() && user.username.isNotBlank() && uname.equals(user.username, ignoreCase = true)
                val matchesLid = lid.isNotBlank() && user.linoId.isNotBlank() && lid.equals(user.linoId, ignoreCase = true)

                // Keep all accounts except the exact one being replaced/updated
                if (!matchesUid && !matchesUname && !matchesLid) {
                    updatedArray.put(obj)
                }
            }

            val cleanAvatar = if (user.avatarUrl.contains("picsum.photos") || user.avatarUrl.contains("demo")) "" else user.avatarUrl
            val userObj = JSONObject().apply {
                put("userId", user.userId)
                put("username", user.username.lowercase())
                put("displayName", user.displayName)
                put("bio", user.bio)
                put("avatarUrl", cleanAvatar)
                put("isOnline", user.isOnline)
                put("lastSeen", user.lastSeen)
                put("statusMessage", user.statusMessage)
                put("email", user.email)
                put("phoneNumber", user.phoneNumber)
                put("normalizedPhoneNumber", user.normalizedPhoneNumber)
                put("createdAt", user.createdAt)
                put("passwordHash", user.passwordHash)
                put("passwordSalt", user.passwordSalt)
                put("recoveryCode", user.recoveryCode)
                put("isOfficial", user.isOfficial)
                put("linoId", user.linoId.uppercase())
                put("googleEmail", user.googleEmail.lowercase())
                put("recoveryEmail", user.recoveryEmail.lowercase())
                put("role", user.role.uppercase())
                put("isRestricted", user.isRestricted)
                put("isVerified", user.isVerified || user.hasVerifiedBadge)
            }

            updatedArray.put(userObj)
            prefs.edit().putString(KEY_ACCOUNTS, updatedArray.toString()).apply()
            Log.i(TAG, "Account @${user.username} saved to resilient backup store.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save account to backup store: ${e.message}")
        }
    }

    fun deleteAccount(userId: String) {
        try {
            val accountsJson = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
            val array = JSONArray(accountsJson)
            val updatedArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uid = obj.optString("userId")
                if (uid != userId) {
                    updatedArray.put(obj)
                }
            }
            prefs.edit().putString(KEY_ACCOUNTS, updatedArray.toString()).apply()
            Log.i(TAG, "Account $userId deleted from backup store.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete account from backup store: ${e.message}")
        }
    }

    fun findAccount(identifier: String): UserEntity? {
        val clean = identifier.trim().removePrefix("@")
        if (clean.isBlank()) return null

        try {
            val accountsJson = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
            val array = JSONArray(accountsJson)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uid = obj.optString("userId")
                val uname = obj.optString("username")
                val dname = obj.optString("displayName")
                val lid = obj.optString("linoId")
                val email = obj.optString("email")
                val gEmail = obj.optString("googleEmail")
                val rEmail = obj.optString("recoveryEmail")
                val phone = obj.optString("phoneNumber")

                val cleanDigits = clean.uppercase().removePrefix("LEN-").removePrefix("LEN").replace("-", "").trim()
                val lidDigits = lid.uppercase().removePrefix("LEN-").removePrefix("LEN").replace("-", "").trim()
                val formattedLeno = if (!clean.uppercase().startsWith("LEN-")) "LEN-$clean" else clean

                val matches = (uname.isNotBlank() && uname.equals(clean, ignoreCase = true)) ||
                        (dname.isNotBlank() && dname.equals(clean, ignoreCase = true)) ||
                        (lid.isNotBlank() && lid.equals(clean, ignoreCase = true)) ||
                        (lid.isNotBlank() && lid.equals(formattedLeno, ignoreCase = true)) ||
                        (cleanDigits.isNotBlank() && lidDigits.isNotBlank() && lidDigits == cleanDigits) ||
                        (uid.isNotBlank() && uid == clean) ||
                        (email.isNotBlank() && email.equals(clean, ignoreCase = true)) ||
                        (gEmail.isNotBlank() && gEmail.equals(clean, ignoreCase = true)) ||
                        (rEmail.isNotBlank() && rEmail.equals(clean, ignoreCase = true)) ||
                        (phone.isNotBlank() && phone == clean)

                if (matches) {
                    val rawAvatar = obj.optString("avatarUrl", "")
                    val cleanAvatar = if (rawAvatar.contains("picsum.photos") || rawAvatar.contains("demo")) "" else rawAvatar
                    return UserEntity(
                        userId = uid,
                        username = uname,
                        displayName = dname,
                        bio = obj.optString("bio", "Connecting on Leno ✨"),
                        avatarUrl = cleanAvatar,
                        isOnline = obj.optBoolean("isOnline", true),
                        lastSeen = obj.optLong("lastSeen", System.currentTimeMillis()),
                        statusMessage = obj.optString("statusMessage", "Hey there! I am using Leno."),
                        isCurrentUser = false,
                        email = email,
                        phoneNumber = phone,
                        normalizedPhoneNumber = obj.optString("normalizedPhoneNumber", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        password = "",
                        passwordHash = obj.optString("passwordHash", ""),
                        passwordSalt = obj.optString("passwordSalt", ""),
                        recoveryCode = obj.optString("recoveryCode", ""),
                        isOfficial = obj.optBoolean("isOfficial", false),
                        linoId = lid,
                        googleEmail = gEmail,
                        recoveryEmail = rEmail,
                        role = obj.optString("role", if (uname.equals("officialjaiby", true)) "OWNER" else "USER"),
                        isRestricted = obj.optBoolean("isRestricted", false),
                        isVerified = obj.optBoolean("isVerified", false)
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read backup accounts: ${e.message}")
        }
        return null
    }

    fun findAccountByUsername(username: String): UserEntity? {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.isBlank()) return null
        return getAllAccounts().firstOrNull { it.username.equals(clean, ignoreCase = true) }
    }

    fun getAllAccounts(): List<UserEntity> {
        val list = mutableListOf<UserEntity>()
        try {
            val accountsJson = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
            val array = JSONArray(accountsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uname = obj.optString("username")
                if (uname.isNotBlank()) {
                    val rawAvatar = obj.optString("avatarUrl", "")
                    val cleanAvatar = if (rawAvatar.contains("picsum.photos") || rawAvatar.contains("demo")) "" else rawAvatar
                    list.add(
                        UserEntity(
                            userId = obj.optString("userId"),
                            username = uname,
                            displayName = obj.optString("displayName"),
                            bio = obj.optString("bio", "Connecting on Leno ✨"),
                            avatarUrl = cleanAvatar,
                            isOnline = obj.optBoolean("isOnline", true),
                            lastSeen = obj.optLong("lastSeen", System.currentTimeMillis()),
                            statusMessage = obj.optString("statusMessage", "Hey there! I am using Leno."),
                            isCurrentUser = false,
                            email = obj.optString("email"),
                            phoneNumber = obj.optString("phoneNumber"),
                            normalizedPhoneNumber = obj.optString("normalizedPhoneNumber", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            password = "",
                            passwordHash = obj.optString("passwordHash", ""),
                            passwordSalt = obj.optString("passwordSalt", ""),
                            recoveryCode = obj.optString("recoveryCode", ""),
                            isOfficial = obj.optBoolean("isOfficial", false),
                            linoId = obj.optString("linoId"),
                            googleEmail = obj.optString("googleEmail"),
                            recoveryEmail = obj.optString("recoveryEmail"),
                            role = obj.optString("role", if (uname.equals("officialjaiby", true)) "OWNER" else "USER"),
                            isRestricted = obj.optBoolean("isRestricted", false),
                            isVerified = obj.optBoolean("isVerified", false)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get all backup accounts: ${e.message}")
        }
        return list
    }

    fun clearAll() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear backup accounts: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AccountBackupStore"
        private const val PREF_NAME = "leno_permanent_backup_prefs"
        private const val KEY_ACCOUNTS = "permanent_accounts_list"
    }
}
