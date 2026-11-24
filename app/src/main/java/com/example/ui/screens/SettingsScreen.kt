package com.example.ui.screens

import android.widget.Toast
import com.example.data.LenoRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.AdminTeacherApplicationsDialog
import com.example.ui.components.ChangePasswordDialog
import com.example.ui.components.ContactSupportDialog
import com.example.ui.components.DisableSecurityPinDialog
import com.example.ui.components.SetupSecurityPinDialog
import com.example.util.ThemeMode
import com.example.viewmodel.LenoViewModel

@Composable
fun SettingsScreen(
    viewModel: LenoViewModel,
    onLogoutSuccess: () -> Unit,
    onNavigateToProtectAccount: () -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onOpenSupportChat: (String) -> Unit = onOpenChat
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isDarkSystem = isSystemInDarkTheme()
    val isDarkActive = com.example.ui.theme.LocalLenoDarkTheme.current
    val accentColor = if (isDarkActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    val blockedList by viewModel.blockedUsers.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val isPinEnabled by viewModel.isSecurityPinEnabled.collectAsState()

    LaunchedEffect(currentUser?.userId) {
        if (currentUser != null) {
            viewModel.refreshSecurityState()
        }
    }

    var lowDataMode by remember(userSettings) { mutableStateOf(userSettings?.lowDataMode ?: false) }
    var notificationsEnabled by remember(userSettings) { mutableStateOf(userSettings?.notificationsEnabled ?: true) }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var showLegalInfoDialog by remember { mutableStateOf(false) }
    var showBlockedUsersModal by remember { mutableStateOf(false) }
    var showAdminTeacherApplicationsDialog by remember { mutableStateOf(false) }
    var showContactSupportDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showSetupPinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Preference Settings Section
        Text(
            text = "App Preferences",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchRow(
                    icon = Icons.Default.DataSaverOn,
                    title = "Low-Data Mode",
                    subtitle = "Optimize for 2G/3G networks and reduce bandwidth",
                    checked = lowDataMode,
                    onCheckedChange = {
                        lowDataMode = it
                        viewModel.updateUserSettings(lowDataMode, notificationsEnabled, isDarkActive)
                    },
                    tag = "setting_low_data_switch"
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                SettingSwitchRow(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Receive alerts for new messages & friend requests",
                    checked = notificationsEnabled,
                    onCheckedChange = {
                        notificationsEnabled = it
                        viewModel.updateUserSettings(lowDataMode, notificationsEnabled, isDarkActive)
                    },
                    tag = "setting_notifications_switch"
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Theme Mode Selector Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (themeMode) {
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            ThemeMode.DARK -> Icons.Default.DarkMode
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                        },
                        contentDescription = "Theme Appearance",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Theme Appearance",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (themeMode) {
                                ThemeMode.SYSTEM -> "Syncing with system (${if (isDarkSystem) "Dark" else "Light"})"
                                ThemeMode.DARK -> "Dark theme active"
                                ThemeMode.LIGHT -> "Light theme active"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // M3 3-option Segmented Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(
                        Triple(ThemeMode.SYSTEM, "System", Icons.Default.BrightnessAuto),
                        Triple(ThemeMode.LIGHT, "Light", Icons.Default.LightMode),
                        Triple(ThemeMode.DARK, "Dark", Icons.Default.DarkMode)
                    )

                    options.forEach { (mode, label, icon) ->
                        val isSelected = themeMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                }
                                .testTag("theme_mode_${mode.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 14.dp)
                )

                SettingSwitchRow(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Theme",
                    subtitle = if (themeMode == ThemeMode.SYSTEM) "Device system setting active" else "Reduce eye strain in low-light environments",
                    checked = isDarkActive,
                    onCheckedChange = { checked ->
                        viewModel.setThemeMode(if (checked) ThemeMode.DARK else ThemeMode.LIGHT)
                    },
                    tag = "setting_dark_theme_switch"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy & Safety Section
        Text(
            text = "Privacy & Security",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = accentColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingActionRow(
                    icon = Icons.Default.Lock,
                    title = "Protect Your Account",
                    subtitle = "Manage backup recovery code, Google & email recovery, and activity log.",
                    badge = if (currentUser?.isAccountProtected == true) "Protected ✓" else "Recommended",
                    badgeColor = if (currentUser?.isAccountProtected == true) com.example.ui.theme.LenoSuccess else com.example.ui.theme.LenoPrimary,
                    onClick = { onNavigateToProtectAccount() },
                    tag = "setting_protect_account_row"
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                SettingActionRow(
                    icon = Icons.Default.Password,
                    title = "Change Password",
                    subtitle = "Verify your current password and strengthen your sign-in credentials",
                    onClick = { showChangePasswordDialog = true },
                    tag = "setting_change_password_row"
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                SettingActionRow(
                    icon = Icons.Default.Pin,
                    title = "App Security PIN",
                    subtitle = if (isPinEnabled) "Require 4–6 digit PIN on app launch" else "Add an extra PIN lock layer to protect your chats",
                    badge = if (isPinEnabled) "Active ✓" else "Off",
                    badgeColor = if (isPinEnabled) com.example.ui.theme.LenoSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        if (isPinEnabled) {
                            showDisablePinDialog = true
                        } else {
                            showSetupPinDialog = true
                        }
                    },
                    tag = "setting_security_pin_row"
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                SettingActionRow(
                    icon = Icons.Default.Lock,
                    title = "Leno ID Account Security",
                    subtitle = "Account secured via Leno unique ID (${currentUser?.linoId?.ifBlank { "Active" } ?: "Active"})",
                    onClick = { onNavigateToProtectAccount() },
                    tag = "setting_id_sec_row"
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                SettingActionRow(
                    icon = Icons.Default.Block,
                    title = "Blocked Users (${blockedList.size})",
                    subtitle = "Manage contacts you have blocked",
                    onClick = { showBlockedUsersModal = true },
                    tag = "setting_blocked_users_row"
                )
            }
        }

        if (currentUser?.isAdmin == true) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "App Owner & Administration",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingActionRow(
                        icon = Icons.Default.AdminPanelSettings,
                        title = "Teacher Applications & Approvals",
                        subtitle = "Review submissions, verify credentials & grant teacher privileges",
                        badge = "Owner Console",
                        badgeColor = com.example.ui.theme.LenoPrimary,
                        onClick = { showAdminTeacherApplicationsDialog = true },
                        tag = "setting_admin_teacher_applications_row"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Help & Support Section
        Text(
            text = "Help & Support",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingActionRow(
                    icon = Icons.Default.HeadsetMic,
                    title = "Contact Support",
                    subtitle = "Submit inquiries, report bugs, view FAQs, or reach out to our team",
                    badge = "24/7 Desk",
                    badgeColor = accentColor,
                    onClick = { showContactSupportDialog = true },
                    tag = "setting_contact_support_row"
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                SettingActionRow(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "Leno Official Support Chat",
                    subtitle = "Live direct messaging with verified @Leno platform assistant",
                    badge = "Official",
                    badgeColor = accentColor,
                    onClick = { onOpenChat(LenoRepository.OFFICIAL_LENO_ID) },
                    tag = "setting_official_chat_row"
                )

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                SettingActionRow(
                    icon = Icons.Default.Policy,
                    title = "Privacy Policy & Terms",
                    subtitle = "Review how Leno handles your data, permissions, and account deletion",
                    onClick = { showLegalInfoDialog = true },
                    tag = "setting_privacy_terms_row"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Account Actions Section
        Text(
            text = "Account Actions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingActionRow(
                    icon = Icons.Default.SwitchAccount,
                    title = "Switch Account / Sign In as Other",
                    subtitle = "Currently logged in as @${currentUser?.username ?: ""}",
                    onClick = { viewModel.logout { onLogoutSuccess() } },
                    tag = "setting_switch_account_row"
                )

                val altAccounts by viewModel.alternativeAccounts.collectAsState()
                if (altAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Available Accounts on this device:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    for (alt in altAccounts) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${alt.displayName} (@${alt.username})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = when {
                                            alt.isApprovedTeacher -> "🎓 Approved Teacher (${alt.assignedSubject})"
                                            else -> "👤 Leno ID: ${alt.linoId}"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.switchToAccount(alt.userId) {
                                            Toast.makeText(context, "Switched to @${alt.username}!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Switch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogoutDialog = true }
                        .padding(vertical = 4.dp)
                        .testTag("setting_logout_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Log Out",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Log out from Leno on this device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (currentUser?.isOfficial != true) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteAccountDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("setting_delete_account_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = "Delete Account",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Delete My Account",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Permanently remove your profile and account data",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // About Leno Info Footer
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = "About", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Leno v1.0.0", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Simple, fast & friendly real-time social messaging.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Blocked Users Modal
    if (showBlockedUsersModal) {
        AlertDialog(
            onDismissRequest = { showBlockedUsersModal = false },
            title = { Text("Blocked Users", fontWeight = FontWeight.Bold) },
            text = {
                if (blockedList.isEmpty()) {
                    Text("You have not blocked any users.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(blockedList) { block ->
                            val blockedUser = allUsers.find { it.userId == block.targetId }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = blockedUser?.avatarUrl,
                                        contentDescription = "Avatar",
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(blockedUser?.displayName ?: block.targetId, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(onClick = { viewModel.unblockUser(block.targetId) }) {
                                    Text("Unblock", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedUsersModal = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out?") },
            text = { Text("Are you sure you want to log out of Leno?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout {
                            onLogoutSuccess()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
            title = {
                Text(
                    "Delete Account Permanently?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    "This action permanently deletes your Leno profile (@${currentUser?.username ?: ""}), username reservation, and cloud session. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingAccount = true
                        viewModel.deleteMyAccount { res ->
                            isDeletingAccount = false
                            if (res.isSuccess) {
                                showDeleteAccountDialog = false
                                onLogoutSuccess()
                            } else {
                                showDeleteAccountDialog = false
                                Toast.makeText(
                                    context,
                                    res.exceptionOrNull()?.message ?: "Could not delete account. Please try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text(if (isDeletingAccount) "Deleting..." else "Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !isDeletingAccount
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    if (showLegalInfoDialog) {
        com.example.ui.components.LegalInfoDialog(
            onDismiss = { showLegalInfoDialog = false }
        )
    }

    if (showAdminTeacherApplicationsDialog) {
        AdminTeacherApplicationsDialog(
            viewModel = viewModel,
            onDismiss = { showAdminTeacherApplicationsDialog = false }
        )
    }

    if (showContactSupportDialog) {
        ContactSupportDialog(
            viewModel = viewModel,
            currentUser = currentUser,
            onDismiss = { showContactSupportDialog = false },
            onOpenSupportChat = { partnerId ->
                showContactSupportDialog = false
                onOpenSupportChat(partnerId)
            }
        )
    }

    if (showChangePasswordDialog) {
        val hasExistingPassword = (currentUser?.passwordHash?.isNotBlank() == true) ||
            (currentUser?.password?.isNotBlank() == true)
        ChangePasswordDialog(
            hasExistingPassword = hasExistingPassword,
            onDismiss = { showChangePasswordDialog = false },
            onSubmit = { currentPass, newPass, callback ->
                viewModel.changePasswordVerified(currentPass, newPass) { res ->
                    if (res.isSuccess) {
                        Toast.makeText(context, "Password updated ✓", Toast.LENGTH_SHORT).show()
                    }
                    callback(res)
                }
            }
        )
    }

    if (showSetupPinDialog) {
        SetupSecurityPinDialog(
            onDismiss = { showSetupPinDialog = false },
            onSubmit = { pin, callback ->
                viewModel.setSecurityPin(pin) { res ->
                    if (res.isSuccess) {
                        Toast.makeText(context, "App Security PIN enabled ✓", Toast.LENGTH_SHORT).show()
                    }
                    callback(res)
                }
            }
        )
    }

    if (showDisablePinDialog) {
        DisableSecurityPinDialog(
            onDismiss = { showDisablePinDialog = false },
            onSubmit = { currentPin, callback ->
                viewModel.disableSecurityPin(currentPin) { res ->
                    if (res.isSuccess) {
                        Toast.makeText(context, "App Security PIN disabled", Toast.LENGTH_SHORT).show()
                    }
                    callback(res)
                }
            }
        )
    }
}

@Composable
fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            val iconTint = if (com.example.ui.theme.LocalLenoDarkTheme.current) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
fun SettingActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String,
    badge: String? = null,
    badgeColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconTint = if (com.example.ui.theme.LocalLenoDarkTheme.current) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
        Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold)
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = badgeColor)
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
