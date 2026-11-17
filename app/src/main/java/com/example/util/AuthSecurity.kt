package com.example.util

import com.example.data.entity.UserEntity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class PasswordStrength(val label: String, val score: Float) {
    WEAK("Weak", 0.33f),
    FAIR("Fair", 0.66f),
    STRONG("Strong", 1.0f)
}

data class PasswordStrengthResult(
    val strength: PasswordStrength,
    val hint: String
)

object AuthSecurity {
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val RECOVERY_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val secureRandom = SecureRandom()

    fun generateSalt(): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(password: String, salt: String): String {
        val cleanPass = password.trim()
        val cleanSalt = salt.trim()
        return try {
            val saltBytes = if (cleanSalt.length % 2 == 0) hexToByteArray(cleanSalt) else cleanSalt.toByteArray()
            val spec = PBEKeySpec(cleanPass.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val hash = factory.generateSecret(spec).encoded
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            fallbackSha256(cleanPass, cleanSalt)
        }
    }

    fun verifyPassword(inputPassword: String, storedHash: String, salt: String): Boolean {
        if (storedHash.isBlank()) return false
        val cleanPass = inputPassword.trim()
        val computedHash = hashPassword(cleanPass, salt)
        if (constantTimeEquals(computedHash, storedHash)) return true
        val computedSha = fallbackSha256(cleanPass, salt)
        if (constantTimeEquals(computedSha, storedHash)) return true
        // Legacy plain text check fallback during database upgrade/migration
        return constantTimeEquals(cleanPass, storedHash)
    }

    fun hashPin(pin: String, salt: String): String = hashPassword("PIN::$pin", salt)

    fun verifyPin(inputPin: String, storedHash: String, salt: String): Boolean {
        if (storedHash.isBlank() || inputPin.isBlank()) return false
        val cleanPin = inputPin.trim()
        val computed = hashPin(cleanPin, salt)
        return constantTimeEquals(computed, storedHash)
    }

    /**
     * Generates a cryptographically random backup recovery code formatted as REC-XXXX-XXXX
     * using an unambiguous uppercase alphanumeric charset.
     */
    fun generateRecoveryCode(): String {
        val part1 = buildString(4) {
            repeat(4) {
                append(RECOVERY_CHARSET[secureRandom.nextInt(RECOVERY_CHARSET.length)])
            }
        }
        val part2 = buildString(4) {
            repeat(4) {
                append(RECOVERY_CHARSET[secureRandom.nextInt(RECOVERY_CHARSET.length)])
            }
        }
        return "REC-$part1-$part2"
    }

    /**
     * Normalizes a recovery code so both legacy "REC-123456" and "REC-XXXX-XXXX"
     * match cleanly regardless of case, hyphens, or whitespace.
     */
    fun normalizeRecoveryCode(code: String): String {
        return code.trim()
            .uppercase()
            .removePrefix("REC-")
            .removePrefix("REC")
            .replace("-", "")
            .replace(" ", "")
    }

    /**
     * Strictly verifies account ownership during account recovery using ONLY private
     * recovery secrets (Recovery Code, Connected Recovery Email, Connected Google Email,
     * or personal non-@leno.chat email). Public identifiers like username, displayName,
     * or Leno ID are explicitly rejected as verification proof.
     */
    fun verifyRecoveryProof(user: UserEntity, verificationInput: String): Boolean {
        val cleanInput = verificationInput.trim()
        if (cleanInput.isBlank()) return false

        // 1. Check private backup Recovery Code
        if (user.recoveryCode.isNotBlank()) {
            val normalizedInput = normalizeRecoveryCode(cleanInput)
            val normalizedStored = normalizeRecoveryCode(user.recoveryCode)
            if (normalizedInput.isNotBlank() &&
                normalizedStored.isNotBlank() &&
                constantTimeEquals(normalizedInput, normalizedStored)
            ) {
                return true
            }
        }

        // 2. Check verified Recovery Email
        if (user.recoveryEmail.isNotBlank() &&
            constantTimeEquals(cleanInput.lowercase(), user.recoveryEmail.trim().lowercase())
        ) {
            return true
        }

        // 3. Check connected Google Email
        if (user.googleEmail.isNotBlank() &&
            constantTimeEquals(cleanInput.lowercase(), user.googleEmail.trim().lowercase())
        ) {
            return true
        }

        // 4. Check personal non-synthetic email (exclude auto-generated @leno.chat emails)
        if (user.email.isNotBlank() &&
            !user.email.trim().lowercase().endsWith("@leno.chat") &&
            constantTimeEquals(cleanInput.lowercase(), user.email.trim().lowercase())
        ) {
            return true
        }

        return false
    }

    fun evaluatePasswordStrength(password: String): PasswordStrengthResult {
        val clean = password.trim()
        if (clean.length < 6) {
            return PasswordStrengthResult(
                PasswordStrength.WEAK,
                "Use at least 6 characters"
            )
        }
        val hasUpper = clean.any { it.isUpperCase() }
        val hasLower = clean.any { it.isLowerCase() }
        val hasDigit = clean.any { it.isDigit() }
        val hasSymbol = clean.any { !it.isLetterOrDigit() }

        val variety = listOf(hasUpper, hasLower, hasDigit, hasSymbol).count { it }
        return when {
            clean.length >= 8 && variety >= 3 -> PasswordStrengthResult(
                PasswordStrength.STRONG,
                "Strong password"
            )
            clean.length >= 6 && variety >= 2 -> PasswordStrengthResult(
                PasswordStrength.FAIR,
                "Add uppercase, numbers or symbols for stronger security"
            )
            else -> PasswordStrengthResult(
                PasswordStrength.WEAK,
                "Mix letters, numbers & symbols to strengthen"
            )
        }
    }

    private fun fallbackSha256(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray())
        val digest = md.digest(password.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
