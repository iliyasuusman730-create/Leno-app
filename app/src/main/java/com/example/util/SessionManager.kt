package com.example.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode {
    LIGHT,  // Light/White tone theme (default)
    DARK,   // Forced dark theme
    SYSTEM  // Respect system settings
}

data class SecurityEvent(
    val id: String,
    val title: String,
    val detail: String,
    val timestamp: Long
)

class SessionManager(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE))

    fun saveActiveUserId(userId: String) {
        prefs.edit().putString(KEY_ACTIVE_USER_ID, userId).commit()
    }

    fun getActiveUserId(): String? {
        return prefs.getString(KEY_ACTIVE_USER_ID, null)
    }

    fun saveAuthCredentials(email: String, pass: String) {
        prefs.edit()
            .putString(KEY_AUTH_EMAIL, email)
            .putString(KEY_AUTH_PASS, pass)
            .commit()
    }

    fun getAuthEmail(): String? = prefs.getString(KEY_AUTH_EMAIL, null)

    fun getAuthPass(): String? = prefs.getString(KEY_AUTH_PASS, null)

    fun clearAuthCredentials() {
        prefs.edit()
            .remove(KEY_AUTH_EMAIL)
            .remove(KEY_AUTH_PASS)
            .commit()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACTIVE_USER_ID)
            .remove(KEY_AUTH_EMAIL)
            .remove(KEY_AUTH_PASS)
            .commit()
    }

    fun getThemeMode(): ThemeMode {
        val saved = prefs.getString(KEY_THEME_MODE, ThemeMode.LIGHT.name)
        return try {
            ThemeMode.valueOf(saved ?: ThemeMode.LIGHT.name)
        } catch (e: Exception) {
            ThemeMode.LIGHT
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    // ==========================================
    // APP SECURITY PIN (PER USER, HASHED)
    // ==========================================

    fun isSecurityPinEnabled(userId: String): Boolean {
        if (userId.isBlank()) return false
        return prefs.getBoolean(KEY_PIN_ENABLED_PREFIX + userId, false) &&
            !prefs.getString(KEY_PIN_HASH_PREFIX + userId, null).isNullOrBlank()
    }

    fun setSecurityPin(userId: String, pin: String) {
        if (userId.isBlank()) return
        val salt = AuthSecurity.generateSalt()
        val hash = AuthSecurity.hashPin(pin, salt)
        prefs.edit()
            .putBoolean(KEY_PIN_ENABLED_PREFIX + userId, true)
            .putString(KEY_PIN_HASH_PREFIX + userId, hash)
            .putString(KEY_PIN_SALT_PREFIX + userId, salt)
            .commit()
    }

    fun verifySecurityPin(userId: String, pin: String): Boolean {
        if (userId.isBlank()) return false
        val storedHash = prefs.getString(KEY_PIN_HASH_PREFIX + userId, "") ?: ""
        val storedSalt = prefs.getString(KEY_PIN_SALT_PREFIX + userId, "") ?: ""
        return AuthSecurity.verifyPin(pin, storedHash, storedSalt)
    }

    fun clearSecurityPin(userId: String) {
        if (userId.isBlank()) return
        prefs.edit()
            .remove(KEY_PIN_ENABLED_PREFIX + userId)
            .remove(KEY_PIN_HASH_PREFIX + userId)
            .remove(KEY_PIN_SALT_PREFIX + userId)
            .commit()
    }

    // ==========================================
    // BRUTE-FORCE RATE LIMITING
    // ==========================================

    fun getLockoutRemainingSeconds(scopeKey: String): Long {
        val cleanKey = scopeKey.trim().lowercase()
        if (cleanKey.isBlank()) return 0L
        val lockedUntil = prefs.getLong(KEY_LOCK_UNTIL_PREFIX + cleanKey, 0L)
        val now = System.currentTimeMillis()
        return if (lockedUntil > now) {
            ((lockedUntil - now) / 1000L) + 1L
        } else {
            0L
        }
    }

    fun recordFailedAttempt(scopeKey: String): Int {
        val cleanKey = scopeKey.trim().lowercase()
        if (cleanKey.isBlank()) return 0
        val now = System.currentTimeMillis()
        val lastFail = prefs.getLong(KEY_FAIL_TIME_PREFIX + cleanKey, 0L)
        val prevCount = if (now - lastFail > ATTEMPT_WINDOW_MS) 0 else prefs.getInt(KEY_FAIL_COUNT_PREFIX + cleanKey, 0)
        val nextCount = prevCount + 1

        val editor = prefs.edit()
            .putInt(KEY_FAIL_COUNT_PREFIX + cleanKey, nextCount)
            .putLong(KEY_FAIL_TIME_PREFIX + cleanKey, now)

        if (nextCount >= MAX_FAILED_ATTEMPTS) {
            editor.putLong(KEY_LOCK_UNTIL_PREFIX + cleanKey, now + LOCKOUT_DURATION_MS)
        }
        editor.apply()
        return nextCount
    }

    fun clearFailedAttempts(scopeKey: String) {
        val cleanKey = scopeKey.trim().lowercase()
        if (cleanKey.isBlank()) return
        prefs.edit()
            .remove(KEY_FAIL_COUNT_PREFIX + cleanKey)
            .remove(KEY_FAIL_TIME_PREFIX + cleanKey)
            .remove(KEY_LOCK_UNTIL_PREFIX + cleanKey)
            .apply()
    }

    // ==========================================
    // SECURITY ACTIVITY LOG (PER USER)
    // ==========================================

    fun logSecurityEvent(userId: String, title: String, detail: String) {
        if (userId.isBlank()) return
        try {
            val existing = getSecurityEvents(userId).toMutableList()
            val newEvent = SecurityEvent(
                id = "sec_${System.currentTimeMillis()}_${(100..999).random()}",
                title = title,
                detail = detail,
                timestamp = System.currentTimeMillis()
            )
            existing.add(0, newEvent)
            val trimmed = existing.take(MAX_SECURITY_EVENTS)
            val arr = JSONArray()
            for (ev in trimmed) {
                arr.put(JSONObject().apply {
                    put("id", ev.id)
                    put("title", ev.title)
                    put("detail", ev.detail)
                    put("timestamp", ev.timestamp)
                })
            }
            prefs.edit().putString(KEY_SEC_LOG_PREFIX + userId, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getSecurityEvents(userId: String): List<SecurityEvent> {
        if (userId.isBlank()) return emptyList()
        return try {
            val raw = prefs.getString(KEY_SEC_LOG_PREFIX + userId, "[]") ?: "[]"
            val arr = JSONArray(raw)
            val list = mutableListOf<SecurityEvent>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SecurityEvent(
                        id = obj.optString("id", ""),
                        title = obj.optString("title", ""),
                        detail = obj.optString("detail", ""),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun markRecoveryPasswordPending(userId: String) {
        if (userId.isBlank()) return
        prefs.edit().putBoolean(KEY_RECOVERY_AUTH_PENDING_PREFIX + userId, true).apply()
    }

    fun isRecoveryPasswordPending(userId: String): Boolean {
        if (userId.isBlank()) return false
        return prefs.getBoolean(KEY_RECOVERY_AUTH_PENDING_PREFIX + userId, false)
    }

    fun clearRecoveryPasswordPending(userId: String) {
        if (userId.isBlank()) return
        prefs.edit().remove(KEY_RECOVERY_AUTH_PENDING_PREFIX + userId).apply()
    }

    companion object {
        private const val PREF_NAME = "leno_session_prefs"
        private const val KEY_ACTIVE_USER_ID = "active_user_id"
        private const val KEY_AUTH_EMAIL = "active_auth_email"
        private const val KEY_AUTH_PASS = "active_auth_pass"
        private const val KEY_THEME_MODE = "app_theme_mode"

        private const val KEY_PIN_ENABLED_PREFIX = "sec_pin_enabled_"
        private const val KEY_PIN_HASH_PREFIX = "sec_pin_hash_"
        private const val KEY_PIN_SALT_PREFIX = "sec_pin_salt_"

        private const val KEY_FAIL_COUNT_PREFIX = "auth_fail_count_"
        private const val KEY_FAIL_TIME_PREFIX = "auth_fail_time_"
        private const val KEY_LOCK_UNTIL_PREFIX = "auth_lock_until_"
        private const val KEY_SEC_LOG_PREFIX = "sec_events_log_"
        private const val KEY_RECOVERY_AUTH_PENDING_PREFIX = "recovery_auth_pending_"

        const val MAX_FAILED_ATTEMPTS = 5
        private const val ATTEMPT_WINDOW_MS = 10 * 60 * 1000L
        private const val LOCKOUT_DURATION_MS = 60 * 1000L
        private const val MAX_SECURITY_EVENTS = 15
    }
}
