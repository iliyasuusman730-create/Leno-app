package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.example.ui.theme.LocalLenoDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SupportMessageItem
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.ui.theme.LenoDarkGreenText
import com.example.ui.theme.LenoDarkGreenTime
import com.example.ui.theme.LenoEmeraldSent
import com.example.ui.theme.LenoEmeraldSentText
import com.example.ui.theme.LenoEmeraldSentTime
import com.example.ui.theme.LenoSoftMintReceived
import com.example.ui.theme.LenoSoftMintReceivedBorder
import com.example.ui.theme.LenoTickCyan
import com.example.viewmodel.LenoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(
    viewModel: LenoViewModel,
    chatId: String,
    onBack: () -> Unit,
    isOwnerMode: Boolean = false
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val messages by viewModel.listenToSupportMessages(chatId).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val effectiveSenderRole = if (isOwnerMode || currentUser?.role?.uppercase() == "OWNER" || currentUser?.role?.uppercase() == "ADMIN") {
        "owner"
    } else {
        "user"
    }
    val currentUserId = currentUser?.userId ?: if (isOwnerMode) "leno_owner" else "guest"
    val isDark = LocalLenoDarkTheme.current

    Scaffold(
        containerColor = if (isDark) Color(0xFF111A16) else Color(0xFFF4F7F5),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = LenoPrimaryVeryLight,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.HeadsetMic,
                                    contentDescription = "Support",
                                    tint = LenoPrimaryDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isOwnerMode) "Support Chat (Owner Reply)" else "Leno Beta Support",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Official",
                                    tint = LenoPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = "Free Beta • Real-time Chat Support",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("support_chat_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Low Data Mode Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Low Data",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Message input field
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = if (isOwnerMode) "Reply as Leno Official..." else "Type your question or issue...",
                                fontSize = 14.sp
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LenoPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    val textToSend = inputText
                                    inputText = ""
                                    viewModel.sendSupportMessage(
                                        chatId = chatId,
                                        text = textToSend,
                                        senderId = currentUserId,
                                        senderRole = effectiveSenderRole
                                    )
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("support_chat_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val textToSend = inputText
                                inputText = ""
                                viewModel.sendSupportMessage(
                                    chatId = chatId,
                                    text = textToSend,
                                    senderId = currentUserId,
                                    senderRole = effectiveSenderRole
                                )
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (inputText.isNotBlank()) LenoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                            .testTag("support_chat_send_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Message",
                            tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Beta Banner
            Surface(
                color = LenoPrimaryVeryLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HeadsetMic,
                        contentDescription = null,
                        tint = LenoPrimaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "We are in FREE Beta — Chat support is live. Low-data mode enabled.",
                        fontSize = 11.sp,
                        color = com.example.ui.theme.LenoBlack,
                        lineHeight = 15.sp
                    )
                }
            }

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = LenoPrimaryVeryLight,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = LenoPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Welcome to Leno Beta Support!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Send your questions, teacher application queries, or bug reports here. Our team will assist you shortly.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = messages, key = { it.messageId.ifBlank { "${it.timestamp}_${it.senderId}" } }) { message ->
                        val isSelf = message.senderId == currentUserId ||
                                (isOwnerMode && message.isFromOwner) ||
                                (!isOwnerMode && !message.isFromOwner && message.senderId == currentUserId)

                        SupportMessageBubble(
                            message = message,
                            isSelf = isSelf
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportMessageBubble(
    message: SupportMessageItem,
    isSelf: Boolean
) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    val bubbleShape = remember(isSelf) {
        if (isSelf) {
            RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = 14.dp,
                bottomEnd = 3.dp
            )
        } else {
            RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = 3.dp,
                bottomEnd = 14.dp
            )
        }
    }

    val isDarkTheme = LocalLenoDarkTheme.current
    val sentBackground = if (isDarkTheme) Color(0xFF087F5B) else Color(0xFF087F5B)
    val receiveBackground = if (isDarkTheme) Color(0xFF1B2822) else Color(0xFFFFFFFF)
    val receiveBorder = if (isDarkTheme) Color(0xFF2C3E35) else Color(0xFFD0DCD5)
    val bubbleBorder = if (isSelf) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)) else BorderStroke(1.dp, receiveBorder)

    // Delivery check marks: single tick sent, double tick delivered, vibrant cyan double tick read
    val isRead = message.read
    val tickMuted = Color.White.copy(alpha = 0.80f)
    val tickRead = LenoTickCyan
    val statusIcon = if (isRead) Icons.Default.DoneAll else Icons.Default.Done
    val statusTint = if (isRead) tickRead else tickMuted

    val textColor = when {
        isSelf -> Color.White
        isDarkTheme -> Color(0xFFF8FAFC)
        else -> Color(0xFF111111)
    }
    val timeColor = if (isSelf) {
        Color.White.copy(alpha = 0.80f)
    } else {
        if (isDarkTheme) Color(0xFFA2B5AB) else Color(0xFF5D7269)
    }

    val isShortSingleLine = remember(message.text) {
        message.text.length <= 26 && !message.text.contains('\n')
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isSelf) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            // Label above bubble
            if (!isSelf && message.isFromOwner) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 3.dp, start = 4.dp)
                ) {
                    Text(
                        text = "Leno Support",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = LenoPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            } else if (!isSelf && !message.isFromOwner) {
                Text(
                    text = "User",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp, start = 4.dp)
                )
            }

            Surface(
                shape = bubbleShape,
                color = if (isSelf) sentBackground else receiveBackground,
                border = bubbleBorder,
                shadowElevation = 1.5.dp,
                modifier = Modifier.testTag("support_message_bubble")
            ) {
                Box(
                    modifier = Modifier.background(if (isSelf) sentBackground else receiveBackground)
                ) {
                    if (isShortSingleLine) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp)
                        ) {
                            Text(
                                text = message.text,
                                color = textColor,
                                fontSize = 14.5.sp,
                                lineHeight = 18.5.sp
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 0.5.dp)
                            ) {
                                Text(
                                    text = formattedTime,
                                    fontSize = 9.5.sp,
                                    color = timeColor
                                )
                                if (isSelf) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = "Status",
                                        tint = statusTint,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(start = 9.dp, end = 9.dp, top = 5.5.dp, bottom = 4.dp)
                        ) {
                        Text(
                            text = message.text,
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = formattedTime,
                                fontSize = 10.sp,
                                color = timeColor
                            )
                            if (isSelf) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = if (isRead) "Read" else "Sent",
                                    tint = statusTint,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}
