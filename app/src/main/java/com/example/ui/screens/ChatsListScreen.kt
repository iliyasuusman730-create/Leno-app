package com.example.ui.screens

import com.example.ui.components.AddContactDialog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.LenoRepository
import com.example.data.entity.UserEntity
import com.example.ui.components.LenoAvatar
import com.example.ui.components.OfficialProfileDialog
import com.example.ui.components.OfficialVerifiedBadge
import com.example.ui.theme.LenoBadgeNotificationBg
import com.example.ui.theme.LenoBadgeNotificationText
import com.example.ui.theme.LenoButtonPrimaryBg
import com.example.ui.theme.LenoButtonPrimaryText
import com.example.ui.theme.LenoMessageStatusSeen
import com.example.ui.theme.LenoMessageStatusSent
import com.example.ui.theme.LenoPrimaryLight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoActionGreen
import com.example.ui.theme.LocalLenoDarkTheme
import com.example.ui.theme.OnlineGreen
import com.example.viewmodel.LenoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Badge
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton

import com.example.util.isSmallScreen
import com.example.util.responsiveHorizontalPadding
import com.example.util.responsiveTitleSize
import com.example.util.MaxContentWidth
import androidx.compose.foundation.layout.widthIn

@Composable
fun ChatsListScreen(
    viewModel: LenoViewModel,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onNavigateToDiscover: () -> Unit = {}
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val chatPartnersList by viewModel.chatPartners.collectAsState()
    val contactsList by viewModel.contactsList.collectAsState()
    val isFetchingUsers by viewModel.isFetchingUserData.collectAsState()
    val pinnedChatIds by viewModel.pinnedChatIds.collectAsState()
    var chatFilterQuery by remember { mutableStateOf("") }
    val isSmall = isSmallScreen()

    val myId = currentUser?.userId ?: ""
    val chatPartners = remember(chatPartnersList, chatFilterQuery, myId, pinnedChatIds) {
        val filtered = chatPartnersList
            .distinctBy { it.userId }
            .filter { it.userId != myId && it.userId.isNotBlank() }

        val matches = if (chatFilterQuery.isBlank()) {
            filtered
        } else {
            filtered.filter {
                it.displayName.contains(chatFilterQuery, ignoreCase = true) ||
                it.username.contains(chatFilterQuery, ignoreCase = true) ||
                it.linoId.contains(chatFilterQuery, ignoreCase = true)
            }
        }
        matches.sortedWith(compareByDescending<UserEntity> { pinnedChatIds.contains(it.userId) })
    }

    androidx.compose.runtime.LaunchedEffect(currentUser?.userId) {
        if (!currentUser?.userId.isNullOrBlank()) {
            viewModel.syncChatsAndMessages()
        }
    }

    var fetchTimeout by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        fetchTimeout = true
    }

    var showNewChatDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }

    var showChatsMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = MaxContentWidth)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Clean Header matching Image 1: "Chats" title + Camera icon + 3-dots More Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chats",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                val isDark = LocalLenoDarkTheme.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleLightDarkTheme(isDark) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("quick_theme_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                            tint = if (isDark) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { showNewChatDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chats_camera_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Camera",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showChatsMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chats_more_menu_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showChatsMenu,
                            onDismissRequest = { showChatsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New chat") },
                                onClick = {
                                    showChatsMenu = false
                                    showNewChatDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add contact") },
                                onClick = {
                                    showChatsMenu = false
                                    showAddContactDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isDark) "Switch to Light Mode" else "Switch to Dark Mode") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showChatsMenu = false
                                    viewModel.toggleLightDarkTheme(isDark)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = {
                                    showChatsMenu = false
                                    viewModel.refreshUserData()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Capsule/Pill Search Bar matching Image 1
            OutlinedTextField(
                value = chatFilterQuery,
                onValueChange = { chatFilterQuery = it },
                placeholder = { Text("Search messages & friends...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chats_search_input"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isFetchingUsers && chatPartners.isEmpty() && !fetchTimeout) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("chats_list_loading_progress"),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading conversations...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (chatPartners.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "No Chats",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No chats yet. Start a new conversation",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the chat icon below to start a message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(chatPartners, key = { it.userId }) { partner ->
                        ChatItemRow(
                            viewModel = viewModel,
                            myId = myId,
                            partner = partner,
                            onOpenChat = onOpenChat,
                            onOpenProfile = onOpenProfile
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 74.dp)
                        )
                    }
                }
            }
        }

        // New Chat FAB matching Image 1
        FloatingActionButton(
            onClick = { showNewChatDialog = true },
            shape = RoundedCornerShape(16.dp),
            containerColor = LenoPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .size(56.dp)
                .testTag("new_chat_fab")
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "New Chat", tint = Color.White)
        }
    }

    if (showNewChatDialog) {
        var pickerQuery by remember { mutableStateOf("") }
        val searchResults by viewModel.repository.searchUsers(pickerQuery, myId).collectAsState(initial = emptyList())
        val allKnownUsers = (contactsList + chatPartnersList).distinctBy { it.userId }.filter { it.userId != myId }
        val displayUsers = if (pickerQuery.isBlank()) allKnownUsers else searchResults

        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("Start New Chat", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Leno style "New Contact" entry at the top
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showNewChatDialog = false
                                showAddContactDialog = true
                            }
                            .testTag("dialog_add_new_contact_option"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = "New Contact",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "New Contact",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Find someone by Leno ID, username or name",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = pickerQuery,
                        onValueChange = { pickerQuery = it },
                        placeholder = { Text("Search by Leno ID, username or name...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (displayUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (pickerQuery.isBlank()) "No contacts yet. Search above to find users." else "No users found matching \"$pickerQuery\"",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(displayUsers, key = { it.userId }) { u ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showNewChatDialog = false
                                            viewModel.markChatRead(u.userId)
                                            onOpenChat(u.userId)
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.clickable {
                                                showNewChatDialog = false
                                                onOpenProfile(u.userId)
                                            }
                                        ) {
                                            LenoAvatar(
                                                avatarUrl = u.avatarUrl,
                                                isOfficial = u.isOfficial || u.userId == LenoRepository.OFFICIAL_LENO_ID,
                                                size = 40.dp,
                                                contentDescription = u.displayName,
                                                username = u.username
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(u.displayName, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (u.linoId.isNotBlank()) "@${u.username} • ${u.linoId}" else "@${u.username}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Select", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddContactDialog) {
        AddContactDialog(
            viewModel = viewModel,
            onDismiss = { showAddContactDialog = false },
            onContactAdded = { newUserId ->
                showAddContactDialog = false
                onOpenChat(newUserId)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatItemRow(
    viewModel: LenoViewModel,
    myId: String,
    partner: UserEntity,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lastMsgFlow = remember(myId, partner.userId) {
        viewModel.repository.getLastMessageBetween(myId, partner.userId)
    }
    val lastMessage by lastMsgFlow.collectAsState(initial = null)

    val unreadFlow = remember(myId, partner.userId) {
        viewModel.repository.getUnreadCount(myId, partner.userId)
    }
    val unreadCount by unreadFlow.collectAsState(
        initial = viewModel.repository.getCachedUnreadCount(partner.userId)
    )

    val pinnedChatIds by viewModel.pinnedChatIds.collectAsState()
    val isPinned = remember(pinnedChatIds, partner.userId) {
        pinnedChatIds.contains(partner.userId)
    }

    var showContextMenu by remember { mutableStateOf(false) }

    val timeFormatted = remember(lastMessage?.timestamp) {
        if (lastMessage == null) ""
        else {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            sdf.format(Date(lastMessage!!.timestamp))
        }
    }

    val isOfficialPartner = partner.userId == LenoRepository.OFFICIAL_LENO_ID || partner.hasVerifiedBadge || partner.isOfficial
    val isVoiceMessage = !lastMessage?.audioUrl.isNullOrBlank() || 
                         lastMessage?.text?.contains("Voice Note", ignoreCase = true) == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    viewModel.markChatRead(partner.userId)
                    onOpenChat(partner.userId)
                },
                onLongClick = {
                    showContextMenu = true
                }
            )
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .testTag("chat_row_${partner.username}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Online indicator - clicking opens profile
            Box(
                modifier = Modifier.clickable { onOpenProfile(partner.userId) }
            ) {
                LenoAvatar(
                    avatarUrl = partner.avatarUrl,
                    isOfficial = isOfficialPartner,
                    size = 52.dp,
                    contentDescription = partner.displayName,
                    username = partner.username
                )
                if (partner.isOnline && !partner.isOfficial && partner.userId != LenoRepository.OFFICIAL_LENO_ID) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = partner.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isOfficialPartner) {
                            Spacer(modifier = Modifier.width(4.dp))
                            OfficialVerifiedBadge(size = 16.dp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(end = 3.dp)
                            )
                        }
                        Text(
                            text = if (timeFormatted.isNotBlank()) timeFormatted else "12:24 AM",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (unreadCount > 0) LenoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isVoiceMessage) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Note",
                            tint = LenoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "🎤 Voice Note (0:08)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else if (isOfficialPartner) {
                        Text(
                            text = "🔒 Security & Account Protection Alert...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Checkmarks for messages sent by me or general delivered
                        val msg = lastMessage
                        if (msg != null && msg.senderId == myId) {
                            val isLastMsgRead = msg.isRead || msg.read || msg.status.equals("READ", ignoreCase = true)
                            val statusUpper = msg.status.uppercase()
                            val isLastMsgDelivered = statusUpper == "DELIVERED"
                            val icon = when {
                                isLastMsgRead || isLastMsgDelivered -> Icons.Default.DoneAll
                                else -> Icons.Default.Done
                            }
                            val tint = when {
                                isLastMsgRead -> Color(0xFF34B7F1)
                                else -> Color(0xFF8696A0)
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = if (isLastMsgRead) "Read" else statusUpper,
                                tint = tint,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                tint = Color(0xFF8696A0),
                                modifier = Modifier
                                    .size(15.dp)
                                    .padding(end = 3.dp)
                            )
                        }

                        val messageSnippet = when {
                            lastMessage == null -> partner.statusMessage.ifBlank { "good" }
                            !lastMessage?.imageUrl.isNullOrBlank() -> "📷 Photo message"
                            else -> lastMessage!!.text
                        }

                        Text(
                            text = messageSnippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Badges matching Image 1
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(LenoActionGreen)
                                .testTag("unread_badge_${partner.userId}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else if (isOfficialPartner) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(LenoActionGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Protected",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showContextMenu) {
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LenoAvatar(
                        avatarUrl = partner.avatarUrl,
                        isOfficial = partner.hasVerifiedBadge || partner.isOfficial || partner.userId == LenoRepository.OFFICIAL_LENO_ID,
                        size = 36.dp,
                        contentDescription = partner.displayName,
                        username = partner.username
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = partner.displayName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            val pinned = viewModel.togglePinChat(partner.userId)
                            val msg = if (pinned) "Chat pinned to top 📌" else "Chat unpinned"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ctx_pin_${partner.userId}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isPinned) "Unpin Chat" else "Pin Chat to Top (Max 3)",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            showContextMenu = false
                            viewModel.markChatRead(partner.userId)
                            Toast.makeText(context, "Marked as read", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ctx_read_${partner.userId}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = Color(0xFF34B7F1),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Mark as Read",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onOpenProfile(partner.userId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ctx_profile_${partner.userId}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "View Profile",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContextMenu = false }) {
                    Text("Close")
                }
            }
        )
    }
}

