package com.example.util

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode {
    LIGHT,  // Light/White tone theme (default)
    DARK,   // Forced dark theme
    SYSTEM  // Respect system settings
}

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveActiveUserId(userId: String) {
        prefs.edit().putString(KEY_ACTIVE_USER_ID, userId).apply()
    }

    fun getActiveUserId(): String? {
        return prefs.getString(KEY_ACTIVE_USER_ID, null)
    }

    fun saveAuthCredentials(email: String, pass: String) {
        prefs.edit()
            .putString(KEY_AUTH_EMAIL, email)
            .putString(KEY_AUTH_PASS, pass)
            .apply()
    }

    fun getAuthEmail(): String? = prefs.getString(KEY_AUTH_EMAIL, null)

    fun getAuthPass(): String? = prefs.getString(KEY_AUTH_PASS, null)

    fun clearAuthCredentials() {
        prefs.edit()
            .remove(KEY_AUTH_EMAIL)
            .remove(KEY_AUTH_PASS)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACTIVE_USER_ID)
            .remove(KEY_AUTH_EMAIL)
            .remove(KEY_AUTH_PASS)
            .apply()
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

    companion object {
        private const val PREF_NAME = "leno_session_prefs"
        private const val KEY_ACTIVE_USER_ID = "active_user_id"
        private const val KEY_AUTH_EMAIL = "active_auth_email"
        private const val KEY_AUTH_PASS = "active_auth_pass"
        private const val KEY_THEME_MODE = "app_theme_mode"
    }
}
