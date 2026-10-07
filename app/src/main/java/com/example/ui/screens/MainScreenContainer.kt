package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LenoNavBottomBg
import com.example.ui.theme.LenoNavDivider
import com.example.ui.theme.LenoNavSelectedIcon
import com.example.ui.theme.LenoNavSelectedLabel
import com.example.ui.theme.LenoNavUnselectedIcon
import com.example.ui.theme.LenoNavUnselectedLabel
import com.example.ui.theme.LenoNotificationBadge
import com.example.ui.theme.LenoNotificationBadgeText
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.components.AdminTeacherApplicationsDialog
import com.example.ui.navigation.Screen
import com.example.viewmodel.LenoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContainer(
    viewModel: LenoViewModel,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onLogout: () -> Unit,
    onNavigateToProtectAccount: () -> Unit = {}
) {
    var currentTabRoute by remember { mutableStateOf(Screen.ChatsTab.route) }
    var showAdminPortal by remember { mutableStateOf(false) }

    val currentUser by viewModel.currentUser.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val notificationMsg by viewModel.notification.collectAsState()
    val unreadNotifCount by viewModel.unreadNotificationCount.collectAsState()
    val pendingReqs by viewModel.pendingRequests.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val isFetchingUserData by viewModel.isFetchingUserData.collectAsState()

    LaunchedEffect(currentUser, isFetchingUserData) {
        if (currentUser == null && !isFetchingUserData) {
            onLogout()
        }
    }

    LaunchedEffect(notificationMsg) {
        notificationMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNotification()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_leno_sparkle),
                            contentDescription = "Leno Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Leno",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (currentUser?.isAdmin == true) {
                        IconButton(
                            onClick = { showAdminPortal = true },
                            modifier = Modifier.testTag("top_bar_admin_portal_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin / Owner Portal",
                                tint = LenoPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = { currentTabRoute = Screen.ContactsTab.route },
                        modifier = Modifier.testTag("top_bar_search_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Users",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.HorizontalDivider(
                    thickness = 1.dp,
                    color = LenoNavDivider
                )
                NavigationBar(
                    containerColor = LenoNavBottomBg,
                    contentColor = LenoNavSelectedIcon
                ) {
                    Screen.bottomNavItems.forEach { item ->
                        val selected = currentTabRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTabRoute = item.route },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = LenoNavSelectedIcon,
                                selectedTextColor = LenoNavSelectedLabel,
                                indicatorColor = LenoPrimaryLight.copy(alpha = 0.5f),
                                unselectedIconColor = LenoNavUnselectedIcon,
                                unselectedTextColor = LenoNavUnselectedLabel
                            ),
                            icon = {
                                Box {
                                    Icon(
                                        imageVector = (if (selected) item.selectedIcon else item.unselectedIcon)!!,
                                        contentDescription = item.title
                                    )
                                    // Show badge on Contacts tab for pending requests
                                    if (item.route == Screen.ContactsTab.route) {
                                        val totalBadges = pendingReqs.size
                                        if (totalBadges > 0) {
                                            Badge(
                                                containerColor = LenoNotificationBadge,
                                                contentColor = LenoNotificationBadgeText
                                            ) { Text(totalBadges.toString()) }
                                        }
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("nav_item_${item.route}")
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            Crossfade(targetState = currentTabRoute, label = "TabTransition") { route ->
                when (route) {
                    Screen.ChatsTab.route -> ChatsListScreen(
                        viewModel = viewModel,
                        onOpenChat = onOpenChat,
                        onOpenProfile = onOpenProfile,
                        onNavigateToDiscover = { currentTabRoute = Screen.ContactsTab.route }
                    )
                    Screen.ContactsTab.route -> DiscoverScreen(
                        viewModel = viewModel,
                        onOpenChat = onOpenChat,
                        onOpenProfile = onOpenProfile
                    )
                    Screen.ClassTab.route, Screen.CallsTab.route -> ClassScreen(
                        viewModel = viewModel,
                        onOpenChat = onOpenChat
                    )
                    Screen.ProfileTab.route -> ProfileScreen(
                        viewModel = viewModel,
                        onNavigateToProtectAccount = onNavigateToProtectAccount,
                        onLogout = onLogout,
                        onOpenChat = onOpenChat
                    )
                    Screen.NotificationsTab.route -> NotificationsScreen(
                        viewModel = viewModel,
                        onOpenChat = onOpenChat
                    )
                    Screen.SettingsTab.route -> SettingsScreen(
                        viewModel = viewModel,
                        onLogoutSuccess = onLogout,
                        onNavigateToProtectAccount = onNavigateToProtectAccount
                    )
                }
            }
        }
    }

    if (showAdminPortal) {
        AdminTeacherApplicationsDialog(
            viewModel = viewModel,
            onDismiss = { showAdminPortal = false }
        )
    }
}
