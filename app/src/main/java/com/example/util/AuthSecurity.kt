package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AuthSecurity {
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
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

    fun generateRecoveryCode(): String {
        val num = (100000..999999).random()
        return "REC-$num"
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

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
