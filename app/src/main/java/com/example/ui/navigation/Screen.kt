package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications

sealed class Screen(
    val route: String,
    val title: String = "",
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    object Splash : Screen("splash")
    object Auth : Screen("auth")
    object Main : Screen("main")

    // Bottom Navigation Tabs: Chats, Contacts, Class (3rd position), Profile
    object ChatsTab : Screen("chats_tab", "Chats", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat)
    object ContactsTab : Screen("contacts_tab", "Contacts", Icons.Filled.People, Icons.Outlined.People)
    object ClassTab : Screen("class_tab", "Class", Icons.Filled.School, Icons.Outlined.School)
    object CallsTab : Screen("calls_tab", "Calls", Icons.Filled.Call, Icons.Outlined.Call)
    object ProfileTab : Screen("profile_tab", "Profile", Icons.Filled.Person, Icons.Outlined.Person)

    // Additional Routes
    object NotificationsTab : Screen("notifications_tab", "Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications)
    object SettingsTab : Screen("settings_tab", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    object ProtectAccount : Screen("protect_account", "Protect Your Account")

    // Chat Detail route
    object ChatDetail : Screen("chat_detail/{partnerId}") {
        fun createRoute(partnerId: String) = "chat_detail/$partnerId"
    }

    // User Public Profile route
    object UserProfile : Screen("user_profile/{userId}") {
        fun createRoute(userId: String) = "user_profile/$userId"
    }

    // Direct Firestore Chat Screen route
    object LiveChat : Screen("live_chat", "Live Chat")

    companion object {
        val bottomNavItems = listOf(
            ChatsTab,
            ContactsTab,
            ClassTab,
            ProfileTab
        )
    }
}
