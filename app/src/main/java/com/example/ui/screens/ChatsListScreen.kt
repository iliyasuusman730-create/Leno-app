package com.example.ui.screens

import com.example.ui.components.AddContactDialog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                .padding(horizontal = responsiveHorizontalPadding(), vertical = 12.dp)
        ) {
            // Screen Header with Refresh, Add Contact & Find People Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Chats",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = responsiveTitleSize(22.sp, 28.sp), fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(if (isSmall) 4.dp else 8.dp))
                    if (isFetchingUsers) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(if (isSmall) 16.dp else 20.dp)
                                .testTag("chats_fetching_indicator"),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.refreshUserData() },
                            modifier = Modifier
                                .size(if (isSmall) 28.dp else 32.dp)
                                .testTag("chats_refresh_button")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh User Data",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (isSmall) 18.dp else 20.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { showAddContactDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (isSmall) 8.dp else 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("chats_add_contact_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(if (isSmall) 16.dp else 18.dp))
                        Spacer(modifier = Modifier.width(if (isSmall) 2.dp else 4.dp))
                        Text(if (isSmall) "Add" else "Add Contact", fontWeight = FontWeight.SemiBold, fontSize = if (isSmall) 12.sp else 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = chatFilterQuery,
                onValueChange = { chatFilterQuery = it },
                placeholder = { Text("Search messages & friends...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chats_search_input"),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatPartners, key = { it.userId }) { partner ->
                        ChatItemRow(
                            viewModel = viewModel,
                            myId = myId,
                            partner = partner,
                            onOpenChat = onOpenChat,
                            onOpenProfile = onOpenProfile
                        )
                    }
                }
            }
        }

        // New Chat FAB
        FloatingActionButton(
            onClick = { showNewChatDialog = true },
            containerColor = LenoButtonPrimaryBg,
            contentColor = LenoButtonPrimaryText,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("new_chat_fab")
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "New Chat")
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

    Card(
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
            .testTag("chat_row_${partner.username}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPinned) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPinned) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Online indicator - clicking opens profile
            Box(
                modifier = Modifier.clickable { onOpenProfile(partner.userId) }
            ) {
                LenoAvatar(
                    avatarUrl = partner.avatarUrl,
                    isOfficial = partner.hasVerifiedBadge || partner.isOfficial || partner.userId == LenoRepository.OFFICIAL_LENO_ID,
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (partner.hasVerifiedBadge || partner.isOfficial || partner.userId == LenoRepository.OFFICIAL_LENO_ID) {
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
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (unreadCount > 0) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkmarks for messages sent by me
                    val msg = lastMessage
                    if (msg != null && msg.senderId == myId) {
                        val isLastMsgRead = msg.isRead || msg.read || msg.status.equals("READ", ignoreCase = true)
                        val statusUpper = msg.status.uppercase()
                        val isLastMsgDelivered = statusUpper == "DELIVERED"
                        val icon = when {
                            isLastMsgRead || isLastMsgDelivered -> Icons.Default.DoneAll
                            statusUpper == "SENT" -> Icons.Default.Done
                            else -> Icons.Default.Done
                        }
                        val tint = when {
                            isLastMsgRead -> Color(0xFF34B7F1) // WhatsApp blue double tick
                            else -> Color(0xFF8696A0) // WhatsApp gray tick
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = if (isLastMsgRead) "Read" else statusUpper,
                            tint = tint,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                    }

                    val messageSnippet = when {
                        lastMessage == null -> partner.statusMessage.ifBlank { "Tap to start conversation ✨" }
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

                    // Fix 3: Badge: unread count badge on chat list
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF25D366))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
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

