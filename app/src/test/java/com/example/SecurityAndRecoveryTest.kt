package com.example

import android.content.SharedPreferences
import com.example.data.entity.UserEntity
import com.example.util.AuthSecurity
import com.example.util.PasswordStrength
import com.example.util.SessionManager
import com.example.util.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndRecoveryTest {

    private fun sampleUser(
        recoveryCode: String = "REC-7K9M-2P4X",
        recoveryEmail: String = "alice.recovery@gmail.com",
        googleEmail: String = "alice.google@gmail.com",
        email: String = "alice@leno.chat"
    ) = UserEntity(
        userId = "usr_alice_1",
        username = "alice_dev",
        displayName = "Alice Johnson",
        linoId = "LEN-12345678",
        email = email,
        recoveryCode = recoveryCode,
        recoveryEmail = recoveryEmail,
        googleEmail = googleEmail
    )

    @Test
    fun recoveryCode_isGeneratedWithSecureFormatAndUnambiguousCharset() {
        val codes = (1..20).map { AuthSecurity.generateRecoveryCode() }
        val pattern = Regex("^REC-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}$")
        for (code in codes) {
            assertTrue("Unexpected recovery code format: $code", pattern.matches(code))
        }
        assertTrue("Generated codes should be distinct", codes.toSet().size > 15)
    }

    @Test
    fun verifyRecoveryProof_rejectsBlankAndPublicIdentifiers() {
        val user = sampleUser()

        // Blank inputs must never recover an account
        assertFalse(AuthSecurity.verifyRecoveryProof(user, ""))
        assertFalse(AuthSecurity.verifyRecoveryProof(user, "   "))

        // Public identifiers must be strictly rejected
        assertFalse(AuthSecurity.verifyRecoveryProof(user, "alice_dev"))
        assertFalse(AuthSecurity.verifyRecoveryProof(user, "Alice Johnson"))
        assertFalse(AuthSecurity.verifyRecoveryProof(user, "Alice"))
        assertFalse(AuthSecurity.verifyRecoveryProof(user, "LEN-12345678"))
        assertFalse(AuthSecurity.verifyRecoveryProof(user, "12345678"))

        // Synthetic @leno.chat internal address must be rejected
        assertFalse(AuthSecurity.verifyRecoveryProof(user, "alice@leno.chat"))
    }

    @Test
    fun verifyRecoveryProof_acceptsPrivateRecoverySecrets() {
        val user = sampleUser()

        // Exact, lowercase, and hyphen-less variants of private recovery code
        assertTrue(AuthSecurity.verifyRecoveryProof(user, "REC-7K9M-2P4X"))
        assertTrue(AuthSecurity.verifyRecoveryProof(user, "rec-7k9m-2p4x"))
        assertTrue(AuthSecurity.verifyRecoveryProof(user, "7K9M2P4X"))

        // Legacy REC-123456 format support
        val legacyUser = sampleUser(recoveryCode = "REC-482910")
        assertTrue(AuthSecurity.verifyRecoveryProof(legacyUser, "REC-482910"))
        assertTrue(AuthSecurity.verifyRecoveryProof(legacyUser, "482910"))

        // Connected recovery email & Google email
        assertTrue(AuthSecurity.verifyRecoveryProof(user, "alice.recovery@gmail.com"))
        assertTrue(AuthSecurity.verifyRecoveryProof(user, "ALICE.GOOGLE@GMAIL.COM"))

        // Personal non-@leno.chat email
        val personalEmailUser = sampleUser(
            recoveryCode = "",
            recoveryEmail = "",
            googleEmail = "",
            email = "alice.personal@example.com"
        )
        assertTrue(AuthSecurity.verifyRecoveryProof(personalEmailUser, "alice.personal@example.com"))
    }

    @Test
    fun passwordAndPinHashing_verifyCorrectAndRejectWrongInputs() {
        val salt = AuthSecurity.generateSalt()
        val passHash = AuthSecurity.hashPassword("S3cur3!Pass", salt)
        assertTrue(AuthSecurity.verifyPassword("S3cur3!Pass", passHash, salt))
        assertFalse(AuthSecurity.verifyPassword("WrongPass", passHash, salt))

        val pinHash = AuthSecurity.hashPin("2580", salt)
        assertNotEquals(passHash, pinHash)
        assertTrue(AuthSecurity.verifyPin("2580", pinHash, salt))
        assertFalse(AuthSecurity.verifyPin("1234", pinHash, salt))
        assertFalse(AuthSecurity.verifyPin("", pinHash, salt))
    }

    @Test
    fun passwordStrength_evaluatesWeakFairAndStrong() {
        assertEquals(PasswordStrength.WEAK, AuthSecurity.evaluatePasswordStrength("12345").strength)
        assertEquals(PasswordStrength.WEAK, AuthSecurity.evaluatePasswordStrength("abcdef").strength)
        assertEquals(PasswordStrength.FAIR, AuthSecurity.evaluatePasswordStrength("abc123").strength)
        assertEquals(PasswordStrength.STRONG, AuthSecurity.evaluatePasswordStrength("Leno#2026!").strength)
    }

    @Test
    fun sessionManager_pinAndBruteForceLockoutWorkEndToEnd() {
        val session = SessionManager(InMemorySharedPreferences())
        val userId = "usr_sec_test"

        assertFalse(session.isSecurityPinEnabled(userId))
        session.setSecurityPin(userId, "4920")
        assertTrue(session.isSecurityPinEnabled(userId))
        assertTrue(session.verifySecurityPin(userId, "4920"))
        assertFalse(session.verifySecurityPin(userId, "0000"))

        session.clearSecurityPin(userId)
        assertFalse(session.isSecurityPinEnabled(userId))
        assertFalse(session.verifySecurityPin(userId, "4920"))

        // Brute-force lockout after MAX_FAILED_ATTEMPTS
        val scope = "recover_alice_dev"
        assertEquals(0L, session.getLockoutRemainingSeconds(scope))
        repeat(SessionManager.MAX_FAILED_ATTEMPTS - 1) { idx ->
            val count = session.recordFailedAttempt(scope)
            assertEquals(idx + 1, count)
            assertEquals(0L, session.getLockoutRemainingSeconds(scope))
        }
        val lockCount = session.recordFailedAttempt(scope)
        assertEquals(SessionManager.MAX_FAILED_ATTEMPTS, lockCount)
        assertTrue(session.getLockoutRemainingSeconds(scope) > 0L)

        session.clearFailedAttempts(scope)
        assertEquals(0L, session.getLockoutRemainingSeconds(scope))

        // Recovery password pending flag isolates stale Firebase Auth password post-recovery
        assertFalse(session.isRecoveryPasswordPending(userId))
        session.markRecoveryPasswordPending(userId)
        assertTrue(session.isRecoveryPasswordPending(userId))
        assertFalse(session.isRecoveryPasswordPending("usr_other"))
        session.clearRecoveryPasswordPending(userId)
        assertFalse(session.isRecoveryPasswordPending(userId))
    }

    @Test
    fun sessionManager_themeModePersistsAcrossLightDarkAndSystem() {
        val prefs = InMemorySharedPreferences()
        val session = SessionManager(prefs)

        assertEquals(ThemeMode.LIGHT, session.getThemeMode())

        session.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, session.getThemeMode())

        session.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, session.getThemeMode())

        session.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, session.getThemeMode())

        // Corrupt stored preference gracefully falls back to LIGHT
        prefs.edit().putString("app_theme_mode", "UNKNOWN_MODE").commit()
        assertEquals(ThemeMode.LIGHT, session.getThemeMode())
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val map = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? MutableSet<String>) ?: defValues
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
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
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
