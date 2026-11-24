package com.example.ui.screens

import com.example.ui.components.AddContactDialog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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

import com.example.ui.components.LenoEmptyState
import com.example.ui.components.LenoHeaderActionButton
import com.example.ui.components.LenoSearchPill
import com.example.ui.components.LenoTabHeader
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
    val allUsersList by viewModel.allUsers.collectAsState()
    val unreadSenderIds by viewModel.unreadSenderIds.collectAsState()
    val isFetchingUsers by viewModel.isFetchingUserData.collectAsState()
    val pinnedChatIds by viewModel.pinnedChatIds.collectAsState()
    var chatFilterQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedChatFilter by remember { mutableStateOf("All") }
    val isSmall = isSmallScreen()
    val context = LocalContext.current

    val myId = currentUser?.userId ?: ""
    val chatPartners = remember(chatPartnersList, chatFilterQuery, myId, pinnedChatIds, selectedChatFilter, unreadSenderIds) {
        val base = chatPartnersList
            .distinctBy { it.userId }
            .filter { it.userId != myId && it.userId.isNotBlank() }

        val byCategory = when (selectedChatFilter) {
            "Unread" -> base.filter { partner ->
                partner.userId in unreadSenderIds ||
                    (partner.username.isNotBlank() && partner.username in unreadSenderIds) ||
                    (partner.linoId.isNotBlank() && partner.linoId in unreadSenderIds) ||
                    viewModel.repository.getCachedUnreadCount(partner.userId) > 0
            }
            "Teachers" -> base.filter { it.isTeacher || it.teacherStatus.equals("APPROVED", ignoreCase = true) || it.role.equals("TEACHER", ignoreCase = true) }
            "Pinned" -> base.filter { pinnedChatIds.contains(it.userId) }
            else -> base
        }

        val matches = if (chatFilterQuery.isBlank()) {
            byCategory
        } else {
            byCategory.filter {
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
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            val isDark = LocalLenoDarkTheme.current
            LenoTabHeader(
                title = "Chats",
                subtitle = if (chatPartners.isNotEmpty()) "${chatPartners.size} conversation${if (chatPartners.size == 1) "" else "s"}" else null,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                LenoHeaderActionButton(
                    icon = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearchOpen) "Close search" else "Search chats",
                    isActive = isSearchOpen,
                    onClick = {
                        isSearchOpen = !isSearchOpen
                        if (!isSearchOpen) chatFilterQuery = ""
                    },
                    modifier = Modifier.testTag("chats_search_toggle_btn")
                )

                Box {
                    LenoHeaderActionButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        onClick = { showChatsMenu = true },
                        modifier = Modifier.testTag("chats_more_menu_btn")
                    )

                    DropdownMenu(
                        expanded = showChatsMenu,
                        onDismissRequest = { showChatsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New chat") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                            onClick = {
                                showChatsMenu = false
                                showNewChatDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add contact") },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
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
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showChatsMenu = false
                                viewModel.refreshUserData()
                            }
                        )
                    }
                }
            }

            LenoSearchPill(
                visible = isSearchOpen || chatFilterQuery.isNotEmpty(),
                query = chatFilterQuery,
                onQueryChange = { chatFilterQuery = it },
                placeholder = "Search messages & friends...",
                onCloseOrClear = {
                    if (chatFilterQuery.isNotEmpty()) chatFilterQuery = "" else isSearchOpen = false
                },
                inputTestTag = "chats_search_input"
            )

            if (isFetchingUsers && chatPartners.isEmpty() && !fetchTimeout && selectedChatFilter == "All") {
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 88.dp)
                ) {
                    // Scrolling Telegram/WhatsApp-style quick filter strip
                    item(key = "chats_filter_strip") {
                        val filters = listOf("All", "Unread", "Teachers", "Pinned")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            items(filters) { f ->
                                val selected = selectedChatFilter == f
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedChatFilter = f },
                                    label = {
                                        Text(
                                            text = f,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        selectedBorderColor = LenoPrimary
                                    ),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LenoPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    if (chatPartners.isEmpty()) {
                        item(key = "chats_empty_state") {
                            LenoEmptyState(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                title = if (selectedChatFilter != "All") "No $selectedChatFilter conversations" else "No chats yet",
                                subtitle = if (selectedChatFilter != "All") "Switch back to All above to view every conversation." else "Start a conversation with a classmate, teacher, or friend.",
                                actionLabel = if (selectedChatFilter != "All") "Show All" else "New Chat",
                                onAction = {
                                    if (selectedChatFilter != "All") selectedChatFilter = "All" else showNewChatDialog = true
                                }
                            )
                        }
                    } else {
                        items(chatPartners, key = { it.userId }) { partner ->
                            SwipeableChatRow(
                                viewModel = viewModel,
                                partner = partner,
                                onTogglePin = {
                                    val pinned = viewModel.togglePinChat(partner.userId)
                                    Toast.makeText(context, if (pinned) "Chat pinned 📌" else "Chat unpinned", Toast.LENGTH_SHORT).show()
                                },
                                onMarkRead = {
                                    viewModel.markChatRead(partner.userId)
                                    Toast.makeText(context, "Marked as read", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                ChatItemRow(
                                    viewModel = viewModel,
                                    myId = myId,
                                    partner = partner,
                                    onOpenChat = onOpenChat,
                                    onOpenProfile = onOpenProfile
                                )
                            }
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 74.dp)
                            )
                        }
                    }
                }
            }
        }

        // Single Clean Floating Action Button
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
        val allKnownUsers = (contactsList + chatPartnersList + allUsersList)
            .distinctBy { it.userId }
            .filter { it.userId != myId && it.userId.isNotBlank() }
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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
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
    val unreadCount by unreadFlow.collectAsState(initial = 0)

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
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
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
                        if (timeFormatted.isNotBlank()) {
                            Text(
                                text = timeFormatted,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (unreadCount > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
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

                    // Capsule unread badge & official lock indicator
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                .clip(RoundedCornerShape(50))
                                .background(LenoActionGreen)
                                .padding(horizontal = 6.dp)
                                .testTag("unread_badge_${partner.userId}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else if (isOfficialPartner) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(50))
                                .background(LenoActionGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Protected",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
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
                    Text("Close", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableChatRow(
    viewModel: LenoViewModel,
    partner: UserEntity,
    onTogglePin: () -> Unit,
    onMarkRead: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onTogglePin()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onMarkRead()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val bgColor = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFFF59E0B)
                SwipeToDismissBoxValue.EndToStart -> LenoPrimary
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            val alignment = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.Settled -> Alignment.CenterStart
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(Icons.Default.PushPin, contentDescription = "Pin Chat", tint = Color.White)
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Icon(Icons.Default.DoneAll, contentDescription = "Mark Read", tint = Color.White)
                }
            }
        },
        content = {
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                content()
            }
        }
    )
}
