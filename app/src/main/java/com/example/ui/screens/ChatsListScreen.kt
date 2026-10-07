package com.example.ui.screens

import com.example.ui.components.AddContactDialog

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
    var chatFilterQuery by remember { mutableStateOf("") }

    val myId = currentUser?.userId ?: ""
    val chatPartners = remember(chatPartnersList, contactsList, chatFilterQuery, myId) {
        val backupPartners = viewModel.repository.messageBackupStore?.getAllChatPartnerProfiles().orEmpty()
            .filter { it.userId.isNotBlank() && it.userId != myId && !it.isCurrentUser }
        val backupAccounts = viewModel.repository.accountBackupStore?.getAllAccounts().orEmpty()
            .filter { it.userId.isNotBlank() && it.userId != myId && !it.isCurrentUser }
        val allMessages = viewModel.repository.messageBackupStore?.getAllMessages().orEmpty()
        val partnersFromMessages = allMessages
            .filter { (it.senderId == myId || it.receiverId == myId) && it.senderId != it.receiverId }
            .map { if (it.senderId == myId) it.receiverId else it.senderId }
            .filter { it.isNotBlank() && it != myId && it != LenoRepository.OFFICIAL_LENO_ID }
            .map { pId ->
                backupPartners.firstOrNull { it.userId == pId || it.username.equals(pId, true) || it.linoId.equals(pId, true) }
                    ?: backupAccounts.firstOrNull { it.userId == pId || it.username.equals(pId, true) || it.linoId.equals(pId, true) }
                    ?: UserEntity(
                        userId = pId,
                        username = if (pId.startsWith("usr_")) pId.removePrefix("usr_").take(10) else if (pId.startsWith("LEN-")) pId.removePrefix("LEN-").take(10) else pId.take(12),
                        displayName = if (pId.startsWith("usr_")) "User ${pId.takeLast(4)}" else if (pId.startsWith("LEN-")) "User $pId" else "User ${pId.take(6)}",
                        bio = "Connecting on Leno ✨",
                        isCurrentUser = false
                    )
            }
        val combined = (chatPartnersList + contactsList + backupPartners + backupAccounts + partnersFromMessages)
            .distinctBy { it.userId }
            .filter { it.userId != myId && it.userId.isNotBlank() }
        if (chatFilterQuery.isBlank()) {
            combined
        } else {
            combined.filter {
                it.displayName.contains(chatFilterQuery, ignoreCase = true) ||
                it.username.contains(chatFilterQuery, ignoreCase = true) ||
                it.linoId.contains(chatFilterQuery, ignoreCase = true)
            }
        }
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
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
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isFetchingUsers) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("chats_fetching_indicator"),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.refreshUserData() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("chats_refresh_button")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh User Data",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showAddContactDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("chats_add_contact_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Contact", fontWeight = FontWeight.SemiBold)
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
                            text = "No conversations found",
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
        val backupPartnersForPicker = viewModel.repository.messageBackupStore?.getAllChatPartnerProfiles().orEmpty()
            .filter { it.userId.isNotBlank() && it.userId != myId && !it.isCurrentUser }
        val backupAccountsForPicker = viewModel.repository.accountBackupStore?.getAllAccounts().orEmpty()
            .filter { it.userId.isNotBlank() && it.userId != myId && !it.isCurrentUser }
        val allKnownUsers = (contactsList + chatPartnersList + backupPartnersForPicker + backupAccountsForPicker + chatPartners).distinctBy { it.userId }.filter { it.userId != myId }
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

@Composable
fun ChatItemRow(
    viewModel: LenoViewModel,
    myId: String,
    partner: UserEntity,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit = {}
) {
    val lastMessage by viewModel.repository.getLastMessageBetween(myId, partner.userId).collectAsState(initial = null)
    val unreadCount by viewModel.repository.getUnreadCount(myId, partner.userId).collectAsState(initial = 0)

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
            .clickable { onOpenChat(partner.userId) }
            .testTag("chat_row_${partner.username}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkmarks for messages sent by me
                    if (lastMessage?.senderId == myId) {
                        val statusUpper = lastMessage?.status?.uppercase() ?: "SENT"
                        val icon = when (statusUpper) {
                            "READ", "DELIVERED" -> Icons.Default.DoneAll
                            "SENT" -> Icons.Default.Done
                            else -> Icons.Default.Schedule
                        }
                        val tint = when (statusUpper) {
                            "READ" -> LenoMessageStatusSeen
                            "DELIVERED", "SENT" -> LenoMessageStatusSent
                            else -> LenoMessageStatusSent.copy(alpha = 0.50f)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = statusUpper,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = LenoBadgeNotificationBg,
                            contentColor = LenoBadgeNotificationText
                        ) {
                            Text(unreadCount.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
