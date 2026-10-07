package com.example.util

object PhoneUtils {
    /**
     * Normalizes a phone number input string to E.164 format.
     * Handles Nigerian phone numbers specifically:
     * - "08012345678" -> "+2348012345678"
     * - "+2348012345678" -> "+2348012345678"
     * - "2348012345678" -> "+2348012345678"
     * Also strips non-digit characters (spaces, dashes, brackets).
     */
    fun normalizePhoneNumber(rawInput: String, defaultCountryCode: String = "+234"): String {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return ""

        val hasLeadingPlus = trimmed.startsWith("+")
        // Remove all non-digit characters
        val digitsOnly = trimmed.replace(Regex("[^0-9]"), "")

        if (digitsOnly.isBlank()) return ""

        if (hasLeadingPlus) {
            return "+$digitsOnly"
        }

        val cleanCode = if (defaultCountryCode.startsWith("+")) defaultCountryCode else "+$defaultCountryCode"

        // If it starts with "234" (Nigerian prefix without +)
        if (digitsOnly.startsWith("234") && digitsOnly.length >= 12) {
            return "+$digitsOnly"
        }

        // If it starts with "0" (e.g. 08012345678, 07012345678, 09012345678, 08112345678)
        if (digitsOnly.startsWith("0")) {
            val withoutLeadingZero = digitsOnly.substring(1)
            return "$cleanCode$withoutLeadingZero"
        }

        // If it's a 10-digit number starting with 7, 8, or 9 (local number without leading zero)
        if (digitsOnly.length == 10 && (digitsOnly.startsWith("7") || digitsOnly.startsWith("8") || digitsOnly.startsWith("9"))) {
            return "$cleanCode$digitsOnly"
        }

        // Default fallback: prepend '+' to digits if not present
        return "+$digitsOnly"
    }
}
