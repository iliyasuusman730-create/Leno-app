package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTetheringError
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.UserEntity
import com.example.ui.components.LenoAvatar
import com.example.ui.components.OfficialVerifiedBadge
import com.example.ui.theme.OnlineGreen

@Composable
fun HomeScreen(
    currentUser: UserEntity?,
    contacts: List<UserEntity>,
    lowDataMode: Boolean,
    onToggleOnlineStatus: (Boolean) -> Unit,
    onNavigateToTab: (String) -> Unit,
    onOpenChat: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val friends = contacts.filter { !it.isCurrentUser && it.userId != currentUser?.userId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Welcome Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    LenoAvatar(
                        avatarUrl = currentUser?.avatarUrl,
                        isOfficial = currentUser?.isOfficial == true,
                        size = 60.dp,
                        contentDescription = "Profile Avatar",
                        username = currentUser?.username
                    )
                    if (currentUser?.isOfficial != true) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (currentUser?.isOnline == true) OnlineGreen
                                    else MaterialTheme.colorScheme.outline
                                )
                                .align(Alignment.BottomEnd)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hello, ${currentUser?.displayName ?: "User"}!",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (currentUser?.hasVerifiedBadge == true) {
                            Spacer(modifier = Modifier.width(6.dp))
                            OfficialVerifiedBadge(size = 20.dp)
                        }
                    }
                    Text(
                        text = currentUser?.statusMessage ?: "Welcome back to Leno",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Online/Offline Status Switch
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (currentUser?.isOnline == true) "Online" else "Offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (currentUser?.isOnline == true) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = currentUser?.isOnline == true,
                        onCheckedChange = onToggleOnlineStatus,
                        modifier = Modifier.testTag("home_online_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = OnlineGreen
                        )
                    )
                }
            }
        }

        // Low-Data Mode Alert Banner
        if (lowDataMode) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WifiTetheringError,
                        contentDescription = "Low Data",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Low-Data Saver Active. High-res images & auto-downloads minimized.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Active Friends / Online Users Row
        Text(
            text = "Online Friends",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(friends) { user ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onOpenChat(user.userId) }
                        .testTag("home_friend_avatar_${user.username}")
                ) {
                    Box {
                        LenoAvatar(
                            avatarUrl = user.avatarUrl,
                            isOfficial = user.isOfficial,
                            size = 60.dp,
                            contentDescription = user.displayName,
                            username = user.username
                        )
                        if (user.isOnline && !user.isOfficial) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen)
                                    .align(Alignment.BottomEnd)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = user.displayName.split(" ").firstOrNull() ?: user.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (user.hasVerifiedBadge) {
                            Spacer(modifier = Modifier.width(3.dp))
                            OfficialVerifiedBadge(size = 12.dp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Navigation Grid Cards
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeQuickCard(
                title = "Chats",
                subtitle = "Start messaging",
                icon = Icons.AutoMirrored.Filled.Chat,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab("chats_tab") },
                tag = "home_quick_chats"
            )
            HomeQuickCard(
                title = "Discover",
                subtitle = "Find new friends",
                icon = Icons.Default.GroupAdd,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab("discover_tab") },
                tag = "home_quick_discover"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeQuickCard(
                title = "Profile",
                subtitle = "Edit bio & status",
                icon = Icons.Default.Person,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab("profile_tab") },
                tag = "home_quick_profile"
            )
            HomeQuickCard(
                title = "Settings",
                subtitle = "App preferences",
                icon = Icons.Default.Settings,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab("settings_tab") },
                tag = "home_quick_settings"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Conversations Direct Access
        Text(
            text = "Recent Contacts",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        friends.take(4).forEach { friend ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onOpenChat(friend.userId) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = friend.avatarUrl,
                        contentDescription = friend.displayName,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = friend.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = friend.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Message",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeQuickCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
