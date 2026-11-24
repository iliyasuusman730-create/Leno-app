package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LenoNavDivider
import com.example.ui.theme.LenoPrimary
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LenoRepository
import com.example.data.entity.UserEntity
import com.example.ui.components.AddContactDialog
import com.example.ui.components.LenoAvatar
import com.example.ui.components.LenoEmptyState
import com.example.ui.components.LenoHeaderActionButton
import com.example.ui.components.LenoSearchPill
import com.example.ui.components.LenoTabHeader
import com.example.ui.components.OfficialVerifiedBadge
import com.example.ui.theme.OnlineGreen
import com.example.viewmodel.LenoViewModel

@Composable
fun DiscoverScreen(
    viewModel: LenoViewModel,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit = {}
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val contactsList by viewModel.contactsList.collectAsState()
    val pendingUsers by viewModel.pendingRequestUsers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isFetchingUsers by viewModel.isFetchingUserData.collectAsState()

    val myId = currentUser?.userId ?: ""
    val isSearching = searchQuery.isNotBlank()
    val chatPartnersList by viewModel.chatPartners.collectAsState()
    val allUsersList by viewModel.allUsers.collectAsState()

    val displayList = remember(isSearching, searchResults, contactsList, chatPartnersList, allUsersList, myId) {
        if (isSearching) {
            searchResults.filter { it.userId != myId }
        } else {
            val backupPartners = viewModel.repository.messageBackupStore?.getAllChatPartnerProfiles().orEmpty()
                .filter { it.userId.isNotBlank() && it.userId != myId && !it.isCurrentUser }
            val backupAccounts = viewModel.repository.accountBackupStore?.getAllAccounts().orEmpty()
                .filter { it.userId.isNotBlank() && it.userId != myId && !it.isCurrentUser }
            val allMessages = viewModel.repository.messageBackupStore?.getAllMessages().orEmpty()
            val partnersFromMessages = allMessages
                .filter { (it.senderId == myId || it.receiverId == myId) && it.senderId != it.receiverId }
                .map { if (it.senderId == myId) it.receiverId else it.senderId }
                .filter { it.isNotBlank() && it != myId && it != LenoRepository.OFFICIAL_LENO_ID }
                .mapNotNull { pId ->
                    allUsersList.firstOrNull {
                        it.userId == pId ||
                        (it.username.isNotBlank() && it.username.equals(pId, true)) ||
                        (it.linoId.isNotBlank() && it.linoId.equals(pId, true))
                    }
                    ?: backupPartners.firstOrNull {
                        it.userId == pId ||
                        (it.username.isNotBlank() && it.username.equals(pId, true)) ||
                        (it.linoId.isNotBlank() && it.linoId.equals(pId, true))
                    }
                    ?: backupAccounts.firstOrNull {
                        it.userId == pId ||
                        (it.username.isNotBlank() && it.username.equals(pId, true)) ||
                        (it.linoId.isNotBlank() && it.linoId.equals(pId, true))
                    }
                }
            (contactsList + chatPartnersList + backupPartners + backupAccounts + partnersFromMessages + allUsersList)
                .distinctBy { it.userId }
                .filter { it.userId != myId && it.userId.isNotBlank() }
        }
    }

    var showAddContactDialog by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    val isSmall = com.example.util.isSmallScreen()

    LaunchedEffect(myId) {
        viewModel.refreshUserData()
        val msgPartnerIds = viewModel.repository.messageBackupStore?.getAllMessages().orEmpty()
            .filter { (it.senderId == myId || it.receiverId == myId) && it.senderId != it.receiverId }
            .map { if (it.senderId == myId) it.receiverId else it.senderId }
            .filter { it.isNotBlank() && it != myId && it != LenoRepository.OFFICIAL_LENO_ID }
            .distinct()
        for (pId in msgPartnerIds) {
            if (allUsersList.none { it.userId == pId }) {
                viewModel.repository.fetchAndSyncUserProfile(pId)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = com.example.util.MaxContentWidth)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            LenoTabHeader(
                title = "Contacts",
                subtitle = if (displayList.isNotEmpty()) "${displayList.size} connection${if (displayList.size == 1) "" else "s"}" else "Find peers & teachers"
            ) {
                LenoHeaderActionButton(
                    icon = if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearchOpen) "Close search" else "Search contacts",
                    isActive = isSearchOpen,
                    onClick = {
                        isSearchOpen = !isSearchOpen
                        if (!isSearchOpen) viewModel.searchQuery.value = ""
                    },
                    modifier = Modifier.testTag("discover_search_toggle_btn")
                )
            }

            LenoSearchPill(
                visible = isSearchOpen || searchQuery.isNotEmpty(),
                query = searchQuery,
                onQueryChange = { viewModel.searchQuery.value = it },
                placeholder = "Search by name, @username, or Leno ID...",
                onCloseOrClear = {
                    if (searchQuery.isNotEmpty()) viewModel.searchQuery.value = "" else isSearchOpen = false
                },
                inputTestTag = "discover_search_input"
            )

        if (isFetchingUsers && displayList.isEmpty() && pendingUsers.isEmpty()) {
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
                            .size(48.dp)
                            .testTag("discover_users_loading_progress"),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Fetching data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
            ) {
                // PENDING CONNECTION REQUESTS SECTION (Visible when not actively searching)
                if (!isSearching && pendingUsers.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Connection Requests",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(pendingUsers.size.toString(), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    items(pendingUsers, key = { "pending_${it.userId}" }) { requester ->
                        IncomingRequestCard(
                            viewModel = viewModel,
                            requester = requester,
                            onOpenProfile = onOpenProfile
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "My Contacts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                // EMPTY CONTACTS / SEARCH RESULTS STATE
                if (displayList.isEmpty()) {
                    item {
                        LenoEmptyState(
                            icon = if (isSearching) Icons.Default.Search else Icons.Default.People,
                            title = if (isSearching) "No users found matching \"$searchQuery\"" else "No contacts or connections yet",
                            subtitle = if (isSearching) {
                                "Try searching with an exact Leno ID, username or phone number."
                            } else if (pendingUsers.isNotEmpty()) {
                                "Accept pending connection requests above or search to connect with more users."
                            } else {
                                "Search by Leno ID or username to find friends and add them to your contacts."
                            },
                            actionLabel = if (!isSearching && pendingUsers.isEmpty()) "Add Someone by ID" else null,
                            onAction = if (!isSearching && pendingUsers.isEmpty()) ({ showAddContactDialog = true }) else null,
                            actionTestTag = "discover_empty_add_contact_btn",
                            modifier = Modifier.fillParentMaxWidth()
                        )
                    }
                } else {
                    // CONTACTS / SEARCH LIST matching Image 2
                    items(displayList, key = { it.userId }) { user ->
                        DiscoverUserCard(
                            viewModel = viewModel,
                            currentUserId = myId,
                            targetUser = user,
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
    }

        // Single Clean Floating Action Button for Add Contact
        FloatingActionButton(
            onClick = { showAddContactDialog = true },
            shape = RoundedCornerShape(16.dp),
            containerColor = LenoPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .size(56.dp)
                .testTag("discover_add_contact_btn")
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", tint = Color.White)
        }
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

/**
 * Card for incoming pending connection requests with one-tap Accept and Decline actions.
 */
@Composable
fun IncomingRequestCard(
    viewModel: LenoViewModel,
    requester: UserEntity,
    onOpenProfile: (String) -> Unit = {}
) {
    var isProcessing by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenProfile(requester.userId) }
            .testTag("incoming_request_card_${requester.username}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Requester Avatar
                Box(
                    modifier = Modifier.clickable { onOpenProfile(requester.userId) }
                ) {
                    LenoAvatar(
                        avatarUrl = requester.avatarUrl,
                        isOfficial = requester.isOfficial,
                        size = 54.dp,
                        contentDescription = requester.displayName,
                        username = requester.username
                    )
                    if (requester.isOnline) {
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

                // Info: Display name, @username, and Leno ID
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenProfile(requester.userId) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = requester.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Bold
                        )
                        if (requester.isOfficial || requester.userId == LenoRepository.OFFICIAL_LENO_ID) {
                            Spacer(modifier = Modifier.width(4.dp))
                            OfficialVerifiedBadge(size = 16.dp)
                        }
                    }
                    Text(
                        text = if (requester.linoId.isNotBlank()) "@${requester.username} • ${requester.linoId}" else "@${requester.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Wants to connect with you",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Profile button icon
                IconButton(
                    onClick = { onOpenProfile(requester.userId) },
                    modifier = Modifier.testTag("incoming_request_profile_btn_${requester.username}")
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "View Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (requester.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = requester.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Accept and Decline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        isProcessing = true
                        viewModel.acceptFriendRequest(requester.userId) {
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("incoming_request_accept_btn_${requester.username}")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Accept", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        isProcessing = true
                        viewModel.declineFriendRequest(requester.userId) {
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("incoming_request_decline_btn_${requester.username}")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Decline", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun DiscoverUserCard(
    viewModel: LenoViewModel,
    currentUserId: String,
    targetUser: UserEntity,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit = {}
) {
    val friendship by viewModel.repository.getFriendshipBetween(currentUserId, targetUser.userId).collectAsState(initial = null)
    val isFriend = friendship?.status == "ACCEPTED"
    val isPending = friendship?.status == "PENDING"
    val isOfficial = targetUser.isOfficial || targetUser.userId == LenoRepository.OFFICIAL_LENO_ID
    val linoIdFormatted = if (targetUser.linoId.isNotBlank()) targetUser.linoId else "LEN-${targetUser.userId.takeLast(8).uppercase()}"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenProfile(targetUser.userId) }
            .padding(horizontal = 4.dp, vertical = 12.dp)
            .testTag("discover_user_card_${targetUser.username}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar
            Box(
                modifier = Modifier.clickable { onOpenProfile(targetUser.userId) }
            ) {
                LenoAvatar(
                    avatarUrl = targetUser.avatarUrl,
                    isOfficial = isOfficial,
                    size = 50.dp,
                    contentDescription = targetUser.displayName,
                    username = targetUser.username
                )
                if (targetUser.isOnline && !isOfficial) {
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

            // Name & @username • Leno ID (Tap anywhere on row to view profile)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = targetUser.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (isOfficial) {
                        Spacer(modifier = Modifier.width(4.dp))
                        OfficialVerifiedBadge(size = 16.dp)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "@${targetUser.username} • $linoIdFormatted",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            // Single clean chat shortcut icon (row tap opens profile)
            IconButton(
                onClick = { onOpenChat(targetUser.userId) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("discover_message_button_${targetUser.username}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Message ${targetUser.displayName}",
                    tint = LenoPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}
