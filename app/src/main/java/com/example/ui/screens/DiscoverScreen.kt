package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LenoNavDivider
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
                .map { pId ->
                    backupPartners.firstOrNull { 
                        it.userId == pId || 
                        (it.username.isNotBlank() && it.username.equals(pId, true)) || 
                        (it.linoId.isNotBlank() && it.linoId.equals(pId, true)) 
                    }
                    ?: backupAccounts.firstOrNull { 
                        it.userId == pId || 
                        (it.username.isNotBlank() && it.username.equals(pId, true)) || 
                        (it.linoId.isNotBlank() && it.linoId.equals(pId, true)) 
                    }
                    ?: UserEntity(
                        userId = pId,
                        username = if (pId.startsWith("usr_")) pId.removePrefix("usr_").take(10) else if (pId.startsWith("LEN-")) pId.removePrefix("LEN-").take(10) else pId.take(12),
                        displayName = if (pId.startsWith("usr_")) "User ${pId.takeLast(4)}" else if (pId.startsWith("LEN-")) "User $pId" else "User ${pId.take(6)}",
                        bio = "Connecting on Leno ✨",
                        isCurrentUser = false
                    )
                }
            (contactsList + chatPartnersList + backupPartners + backupAccounts + partnersFromMessages + allUsersList)
                .distinctBy { it.userId }
                .filter { it.userId != myId && it.userId.isNotBlank() }
        }
    }

    var showAddContactDialog by remember { mutableStateOf(false) }
    val isSmall = com.example.util.isSmallScreen()

    LaunchedEffect(Unit) {
        viewModel.refreshUserData()
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Top Bar matching Image 2: + Add Contact button on the top right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showAddContactDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("discover_add_contact_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+ Add Contact",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Capsule/Pill Search Input matching Image 2
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = {
                    Text(
                        "Search by name, @username, or Leno ID...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discover_search_input"),
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

            Spacer(modifier = Modifier.height(14.dp))

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    color = MaterialTheme.colorScheme.primary
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (!isSearching && pendingUsers.isNotEmpty()) 20.dp else 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSearching) Icons.Default.Search else Icons.Default.People,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isSearching) "No users found matching \"$searchQuery\"" else "No contacts or connections yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isSearching) {
                                        "Try searching with an exact Leno ID, username or phone number."
                                    } else if (pendingUsers.isNotEmpty()) {
                                        "Accept pending connection requests above or search to connect with more users."
                                    } else {
                                        "Search by Leno ID or username to find friends and add them to your contacts."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                if (!isSearching && pendingUsers.isEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { showAddContactDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("discover_empty_add_contact_btn")
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Someone by ID")
                                    }
                                }
                            }
                        }
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
                        color = MaterialTheme.colorScheme.primary,
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
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Name & @username • Leno ID
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenProfile(targetUser.userId) }
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

            Spacer(modifier = Modifier.width(8.dp))

            // Action Pill Buttons matching Image 2
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onOpenChat(targetUser.userId) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                    modifier = Modifier.testTag("discover_message_button_${targetUser.username}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Message",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = { onOpenProfile(targetUser.userId) },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.testTag("discover_view_profile_btn_${targetUser.username}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Profile",
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
