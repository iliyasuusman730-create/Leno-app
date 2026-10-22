package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.UserEntity
import com.example.data.model.SupportChatItem
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.viewmodel.LenoViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContactSupportDialog(
    viewModel: LenoViewModel,
    currentUser: UserEntity?,
    onDismiss: () -> Unit,
    onOpenSupportChat: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isOwnerOrAdmin = currentUser?.isAdmin == true || currentUser?.isOwner == true
    val openSupportChats by viewModel.openSupportChats.collectAsState()

    // Tabs differentiation: Owner/Admin has Complaints Inbox as primary tab
    val tabTitles = if (isOwnerOrAdmin) {
        listOf("Complaints Inbox (${openSupportChats.size})", "Support Channel", "FAQ", "Hotline")
    } else {
        listOf("Submit Complaint", "FAQ", "Hotline")
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var isOpeningChat by remember { mutableStateOf(false) }

    // User complaint form state
    var complaintCategory by remember { mutableStateOf("General Complaint") }
    var complaintMessage by remember { mutableStateOf("") }
    var isSubmittingComplaint by remember { mutableStateOf(false) }

    // Admin inline reply states per chat ID
    val replyTextMap = remember { mutableStateMapOf<String, String>() }
    val isSendingReplyMap = remember { mutableStateMapOf<String, Boolean>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp)
                .testTag("contact_support_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = LenoPrimaryVeryLight,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.HeadsetMic,
                                    contentDescription = "Contact Support",
                                    tint = LenoPrimaryDark,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isOwnerOrAdmin) "Support & Complaints Hub" else "Contact Support",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                OfficialVerifiedBadge(size = 14.dp)
                            }
                            Text(
                                text = if (isOwnerOrAdmin) "Owner Complaints Inbox • Two-Way Replies" else "Leno Help Desk • Free Beta",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.coerceIn(0, tabTitles.size - 1),
                    containerColor = Color.Transparent,
                    contentColor = LenoPrimary,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        if (selectedTab in tabPositions.indices) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = LenoPrimary,
                                height = 2.5.dp
                            )
                        }
                    },
                    divider = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) LenoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content View
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isOwnerOrAdmin) {
                        // OWNER / ADMIN VIEW
                        when (selectedTab) {
                            0 -> {
                                // TAB 0: OWNER COMPLAINTS INBOX
                                OwnerComplaintsInboxTab(
                                    openChats = openSupportChats,
                                    replyTextMap = replyTextMap,
                                    isSendingReplyMap = isSendingReplyMap,
                                    currentUserId = currentUser?.userId ?: "owner",
                                    onSendReply = { chat, text ->
                                        if (text.isBlank()) {
                                            Toast.makeText(context, "Please write a reply message", Toast.LENGTH_SHORT).show()
                                            return@OwnerComplaintsInboxTab
                                        }
                                        isSendingReplyMap[chat.chatId] = true
                                        viewModel.sendSupportMessage(
                                            chatId = chat.chatId,
                                            text = text.trim(),
                                            senderId = currentUser?.userId ?: "owner",
                                            senderRole = "owner"
                                        ) {
                                            isSendingReplyMap[chat.chatId] = false
                                            replyTextMap[chat.chatId] = ""
                                            Toast.makeText(context, "Reply sent to ${chat.userName}!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onOpenFullChat = { chat ->
                                        onDismiss()
                                        onOpenSupportChat(chat.chatId)
                                    },
                                    onResolveChat = { chat ->
                                        viewModel.updateSupportChatStatus(chat.chatId, "closed")
                                        Toast.makeText(context, "Marked ticket #${chat.chatId.take(6)} as resolved", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            1 -> {
                                // TAB 1: OFFICIAL SUPPORT OVERVIEW
                                SupportChannelOverviewCard(
                                    currentUser = currentUser,
                                    isOpeningChat = isOpeningChat,
                                    onOpenChat = {
                                        val userId = currentUser?.userId ?: "guest_${System.currentTimeMillis()}"
                                        val userName = currentUser?.displayName?.ifBlank { currentUser.username } ?: "Leno Admin"
                                        isOpeningChat = true
                                        viewModel.getOrCreateSupportChat(userId, userName) { chatId ->
                                            isOpeningChat = false
                                            onDismiss()
                                            onOpenSupportChat(chatId)
                                        }
                                    }
                                )
                            }
                            2 -> FaqListTab()
                            else -> HotlineTab(onGoToChat = { selectedTab = 0 })
                        }
                    } else {
                        // REGULAR USER / STUDENT VIEW
                        when (selectedTab) {
                            0 -> {
                                // TAB 0: SUBMIT COMPLAINT & LIVE SUPPORT
                                UserComplaintFormTab(
                                    complaintCategory = complaintCategory,
                                    onCategorySelected = { complaintCategory = it },
                                    complaintMessage = complaintMessage,
                                    onMessageChanged = { complaintMessage = it },
                                    isSubmitting = isSubmittingComplaint,
                                    onSubmitComplaint = {
                                        if (complaintMessage.isBlank()) {
                                            Toast.makeText(context, "Please write your complaint or message", Toast.LENGTH_SHORT).show()
                                            return@UserComplaintFormTab
                                        }
                                        val userId = currentUser?.userId ?: "guest_${System.currentTimeMillis()}"
                                        val userName = currentUser?.displayName?.ifBlank { currentUser.username }
                                            ?: currentUser?.username?.ifBlank { "Leno User" }
                                            ?: "Leno User"

                                        isSubmittingComplaint = true
                                        viewModel.getOrCreateSupportChat(userId, userName) { chatId ->
                                            val fullMessage = "[$complaintCategory] ${complaintMessage.trim()}"
                                            viewModel.sendSupportMessage(
                                                chatId = chatId,
                                                text = fullMessage,
                                                senderId = userId,
                                                senderRole = "user"
                                            ) {
                                                isSubmittingComplaint = false
                                                complaintMessage = ""
                                                Toast.makeText(context, "Complaint submitted! Connecting to support...", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                                onOpenSupportChat(chatId)
                                            }
                                        }
                                    },
                                    onOpenDirectChat = {
                                        val userId = currentUser?.userId ?: "guest_${System.currentTimeMillis()}"
                                        val userName = currentUser?.displayName?.ifBlank { currentUser.username } ?: "Leno User"
                                        isOpeningChat = true
                                        viewModel.getOrCreateSupportChat(userId, userName) { chatId ->
                                            isOpeningChat = false
                                            onDismiss()
                                            onOpenSupportChat(chatId)
                                        }
                                    },
                                    isOpeningChat = isOpeningChat
                                )
                            }
                            1 -> FaqListTab()
                            else -> HotlineTab(onGoToChat = { selectedTab = 0 })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Close Button
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Tab showing owner the list of all user complaints with instant reply
 */
@Composable
private fun OwnerComplaintsInboxTab(
    openChats: List<SupportChatItem>,
    replyTextMap: MutableMap<String, String>,
    isSendingReplyMap: MutableMap<String, Boolean>,
    currentUserId: String,
    onSendReply: (SupportChatItem, String) -> Unit,
    onOpenFullChat: (SupportChatItem) -> Unit,
    onResolveChat: (SupportChatItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = LenoPrimaryVeryLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = LenoPrimaryDark, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "You are in Owner Mode. Read all user complaints, send direct replies, or open the real-time chat.",
                    fontSize = 12.sp,
                    color = LenoPrimaryDark,
                    lineHeight = 16.sp
                )
            }
        }

        if (openChats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = LenoPrimaryVeryLight,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = LenoPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Pending Complaints! 🎉",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "When users file a complaint or send a support message, it will immediately show here for you to reply.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        } else {
            openChats.forEach { chat ->
                val replyText = replyTextMap[chat.chatId] ?: ""
                val isSending = isSendingReplyMap[chat.chatId] == true
                val timeFormatted = remember(chat.lastUpdatedAt) {
                    val date = Date(if (chat.lastUpdatedAt > 0) chat.lastUpdatedAt else chat.createdAt)
                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = LenoPrimary,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = chat.userName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = chat.userName.ifBlank { "User ${chat.userId.take(6)}" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$timeFormatted • ID: ${chat.userId.take(8)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFD97706).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "OPEN",
                                    color = Color(0xFFD97706),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // User Complaint Text
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "User Complaint / Message:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = chat.lastMessage.ifBlank { "No message body available." },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Reply input
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyTextMap[chat.chatId] = it },
                            placeholder = { Text("Write your reply to ${chat.userName}...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LenoPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = { onSendReply(chat, replyText) },
                                    enabled = !isSending && replyText.isNotBlank()
                                ) {
                                    if (isSending) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Send Reply",
                                            tint = if (replyText.isNotBlank()) LenoPrimary else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onOpenFullChat(chat) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Live Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onResolveChat(chat) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mark Resolved", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab for regular users to submit complaints or open direct support chat
 */
@Composable
private fun UserComplaintFormTab(
    complaintCategory: String,
    onCategorySelected: (String) -> Unit,
    complaintMessage: String,
    onMessageChanged: (String) -> Unit,
    isSubmitting: Boolean,
    onSubmitComplaint: () -> Unit,
    onOpenDirectChat: () -> Unit,
    isOpeningChat: Boolean
) {
    val categories = listOf("Teacher Application", "Class & Lesson", "Account", "Payment", "General Complaint")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LenoPrimaryVeryLight),
            border = BorderStroke(1.dp, LenoPrimaryLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = LenoPrimary,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.HeadsetMic, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Leno Support & Complaints",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = LenoPrimaryDark
                        )
                        Text(
                            text = "Send your complaint directly to the owner/support team",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Select Category:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(3).forEach { cat ->
                        val isSelected = cat == complaintCategory
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) LenoPrimary else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) LenoPrimary else Color(0xFFD1D5DB)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onCategorySelected(cat) }
                        ) {
                            Text(
                                text = cat,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color.Black,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.drop(3).forEach { cat ->
                        val isSelected = cat == complaintCategory
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) LenoPrimary else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) LenoPrimary else Color(0xFFD1D5DB)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onCategorySelected(cat) }
                        ) {
                            Text(
                                text = cat,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color.Black,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Your Complaint / Message:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = complaintMessage,
                    onValueChange = onMessageChanged,
                    placeholder = {
                        Text(
                            text = "Describe your issue, report an error, or ask a question. The team will read and reply directly.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = LenoPrimary,
                        unfocusedBorderColor = Color(0xFFD1D5DB)
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onSubmitComplaint,
                    enabled = !isSubmitting && complaintMessage.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("submit_complaint_btn")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submitting Complaint...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit Complaint & Chat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Direct live chat shortcut
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Prefer Live Chat?", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Skip the form and chat with support now", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onOpenDirectChat,
                    enabled = !isOpeningChat,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimaryDark),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isOpeningChat) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    } else {
                        Text("Open Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportChannelOverviewCard(
    currentUser: UserEntity?,
    isOpeningChat: Boolean,
    onOpenChat: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LenoPrimaryVeryLight),
        border = BorderStroke(1.dp, LenoPrimaryLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = LenoPrimary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Leno Official Support",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = LenoPrimaryDark
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        OfficialVerifiedBadge(size = 14.dp)
                    }
                    Text(
                        text = "Verified Support Channel • Always Active",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Chat directly with Leno support. You can ask questions, verify teacher approvals, inquire about classes (including Quran & Online Skills), or request technical assistance in real-time.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenChat,
                enabled = !isOpeningChat,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_support_chat_btn")
            ) {
                if (isOpeningChat) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting to Support...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Support Chat Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun FaqListTab() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FaqItemCard(
            question = "How do I become an approved Teacher?",
            answer = "Go to your Profile and tap 'Apply to Become Teacher'. Select your single subject (e.g. Maths, Quran, JAMB, Skills, Online Skills, Content Creator), enter your qualifications and experience. You can apply once per account. Once submitted, your application status will show PENDING until reviewed by our administrators."
        )

        FaqItemCard(
            question = "Why does it say 'Pending Review' after applying?",
            answer = "Every teacher application is verified by Leno administrators to maintain high educational quality. Each user is strictly permitted one application. You will receive a direct notification once approved."
        )

        FaqItemCard(
            question = "Where are Quran and Online Skills classes?",
            answer = "Tap the 'Class' tab on the bottom bar, and tap the 'Quran' or 'Online Skills' filter chip at the top. You can explore classes, read lessons, and enroll for free during our early launch period."
        )

        FaqItemCard(
            question = "How do I protect my Leno account?",
            answer = "In Settings, select 'Protect Your Account'. You can link a Google Account or recovery email so you can safely reset your password anytime."
        )

        FaqItemCard(
            question = "What is my Leno ID?",
            answer = "Your Leno ID (e.g. LEN-50638964) is your permanent unique platform identifier. Friends and students can use it to find and chat with you directly."
        )
    }
}

@Composable
private fun HotlineTab(onGoToChat: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LenoPrimaryVeryLight),
        border = BorderStroke(1.dp, LenoPrimaryLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = LenoPrimary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Leno Beta Support",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = LenoPrimaryDark
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We are in FREE Beta - Chat support only for now. Email support coming soon.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onGoToChat,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("hotline_go_to_chat_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Go to Support Chat",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun FaqItemCard(
    question: String,
    answer: String
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = LenoPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = question,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = answer,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
fun OfficialVerifiedBadge(size: androidx.compose.ui.unit.Dp = 14.dp) {
    Icon(
        imageVector = Icons.Default.Verified,
        contentDescription = "Verified Official",
        tint = LenoPrimary,
        modifier = Modifier.size(size)
    )
}
