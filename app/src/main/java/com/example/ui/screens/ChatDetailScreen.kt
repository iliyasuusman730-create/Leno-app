package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import com.example.R
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Email
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.LenoRepository
import com.example.data.entity.MessageEntity
import com.example.data.service.FirebaseStorageService
import com.example.ui.components.LenoAvatar
import com.example.ui.components.OfficialProfileDialog
import com.example.ui.components.OfficialVerifiedBadge
import com.example.ui.theme.LenoChatAttachmentBtn
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.ui.theme.LenoChatBackground
import com.example.ui.theme.LenoChatInputBg
import com.example.ui.theme.LenoChatReceivedBubble
import com.example.ui.theme.LenoChatReceivedText
import com.example.ui.theme.LenoChatReceivedTime
import com.example.ui.theme.LenoChatSendBtn
import com.example.ui.theme.LenoChatSentBubble
import com.example.ui.theme.LenoChatSentText
import com.example.ui.theme.LenoChatSentTime
import com.example.ui.theme.LenoMessageStatusSeen
import com.example.ui.theme.LenoMessageStatusSent
import com.example.ui.theme.LenoOfficialBadge
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.ui.theme.LenoStatusErrorText
import com.example.ui.theme.OnlineGreen
import com.example.util.AudioPlayerHelper
import com.example.util.AudioRecorderHelper
import com.example.util.TimeUtils
import com.example.viewmodel.LenoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun LenoChatDoodleBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8F5))
            .drawBehind {
                val bubbleColor = Color(0xFF00A86B).copy(alpha = 0.04f)
                val stepX = 64.dp.toPx()
                val stepY = 64.dp.toPx()
                val bubbleWidth = 24.dp.toPx()
                val bubbleHeight = 16.dp.toPx()
                val corner = 5.dp.toPx()

                var y = 16.dp.toPx()
                var rowIndex = 0
                while (y < size.height) {
                    val offsetX = if (rowIndex % 2 == 1) stepX / 2f else 0f
                    var x = 16.dp.toPx() + offsetX
                    var colIndex = 0
                    while (x < size.width) {
                        drawRoundRect(
                            color = bubbleColor,
                            topLeft = Offset(x, y),
                            size = Size(bubbleWidth, bubbleHeight),
                            cornerRadius = CornerRadius(corner, corner)
                        )
                        val tailPath = Path().apply {
                            if ((rowIndex + colIndex) % 2 == 0) {
                                moveTo(x + 4.dp.toPx(), y + bubbleHeight)
                                lineTo(x - 2.dp.toPx(), y + bubbleHeight + 4.dp.toPx())
                                lineTo(x + 8.dp.toPx(), y + bubbleHeight)
                            } else {
                                moveTo(x + bubbleWidth - 8.dp.toPx(), y + bubbleHeight)
                                lineTo(x + bubbleWidth + 2.dp.toPx(), y + bubbleHeight + 4.dp.toPx())
                                lineTo(x + bubbleWidth - 4.dp.toPx(), y + bubbleHeight)
                            }
                            close()
                        }
                        drawPath(tailPath, bubbleColor)

                        x += stepX
                        colIndex++
                    }
                    y += stepY
                    rowIndex++
                }
            }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatDetailScreen(
    viewModel: LenoViewModel,
    partnerId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    androidx.activity.compose.BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(partnerId) {
        viewModel.openChat(partnerId)
        viewModel.markChatRead(partnerId)
        onDispose {
            viewModel.activeChatPartnerId.value = null
        }
    }

    val currentUser by viewModel.currentUser.collectAsState()
    val partner by viewModel.activeChatPartner.collectAsState()
    val messages by viewModel.activeChatMessages.collectAsState()
    val blockedUsers by viewModel.blockedUsers.collectAsState()
    val typingStatusMap by viewModel.typingStatusMap.collectAsState()
    val isFetchingMessages by viewModel.isFetchingChatMessages.collectAsState()

    // Ensure that when the recipient opens the chat screen or new messages arrive,
    // all unread messages from partner are updated with 'read' = true in Firestore
    LaunchedEffect(messages, partnerId) {
        val myId = currentUser?.userId ?: viewModel.repository.getCurrentUserIdSync()
        val hasUnreadFromPartner = messages.any { it.status != "READ" && (myId == null || it.senderId != myId) }
        if (hasUnreadFromPartner) {
            viewModel.markChatRead(partnerId)
        }
    }

    val isPartnerTyping = typingStatusMap[partnerId] == true

    val isBlocked = remember(blockedUsers, partnerId) {
        blockedUsers.any { it.targetId == partnerId && it.isBlocked }
    }

    var textInput by remember { mutableStateOf("") }

    // Notify ViewModel when user is typing in textInput
    LaunchedEffect(textInput) {
        val myId = currentUser?.userId ?: return@LaunchedEffect
        if (textInput.isNotBlank()) {
            viewModel.setUserTyping(myId, true)
            delay(2500)
            viewModel.setUserTyping(myId, false)
        } else {
            viewModel.setUserTyping(myId, false)
        }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showPhotoPickerModal by remember { mutableStateOf(false) }
    var showOfficialProfileDialog by remember { mutableStateOf(false) }
    var reportReasonInput by remember { mutableStateOf("") }

    // Voice Recording State
    val audioRecorderHelper = remember { AudioRecorderHelper(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var recordedAudioFile by remember { mutableStateOf<File?>(null) }
    var showMicPermissionDeniedDialog by remember { mutableStateOf(false) }
    var recordingErrorMessage by remember { mutableStateOf<String?>(null) }
    val liveAmplitude by audioRecorderHelper.liveAmplitude.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            audioRecorderHelper.cancelRecording()
        }
    }

    val listState = rememberLazyListState()

    // Recording coroutine timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSec = 0
            while (isRecording) {
                delay(1000)
                recordingDurationSec++
            }
        }
    }

    // Permission launcher for microphone
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val result = audioRecorderHelper.startRecording()
            if (result.isSuccess) {
                recordedAudioFile = result.getOrNull()
                isRecording = true
                recordingErrorMessage = null
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to initialize microphone"
                Log.e("ChatDetailScreen", "Failed to start audio recording: $err")
                recordingErrorMessage = err
            }
        } else {
            showMicPermissionDeniedDialog = true
        }
    }

    // Photo attachment state & launchers
    var selectedPhotoFile by remember { mutableStateOf<File?>(null) }
    var showImagePreviewDialog by remember { mutableStateOf(false) }
    var photoCaption by remember { mutableStateOf("Attached photo 📷") }
    var photoErrorMessage by remember { mutableStateOf<String?>(null) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val file = File(context.cacheDir, "gallery_photo_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                selectedPhotoFile = file
                photoErrorMessage = null
                showPhotoPickerModal = false
                showImagePreviewDialog = true
            } catch (e: Exception) {
                Log.e("ChatDetailScreen", "Error reading gallery image: ${e.message}")
                photoErrorMessage = "Unable to read selected gallery image."
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val photoFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                selectedPhotoFile = photoFile
                photoErrorMessage = null
                showPhotoPickerModal = false
                showImagePreviewDialog = true
            } catch (e: Exception) {
                Log.e("ChatDetailScreen", "Error saving camera photo: ${e.message}")
                photoErrorMessage = "Failed to save camera photo."
            }
        } else {
            photoErrorMessage = "No photo captured."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                cameraLauncher.launch(null)
            } catch (e: Exception) {
                Log.e("ChatDetailScreen", "Error launching camera: ${e.message}")
                photoErrorMessage = "Camera launch error."
            }
        } else {
            photoErrorMessage = "Camera permission is required to capture photos."
        }
    }

    LaunchedEffect(messages.size, isPartnerTyping) {
        val totalItems = messages.size + (if (isPartnerTyping) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            val totalItems = messages.size + (if (isPartnerTyping) 1 else 0)
            if (totalItems > 0) {
                delay(80)
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    val isOfficialAccount = partnerId == LenoRepository.OFFICIAL_LENO_ID
    val isAuthorizedOfficialPoster = currentUser?.userId == LenoRepository.OFFICIAL_LENO_ID

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            if (partnerId == LenoRepository.OFFICIAL_LENO_ID) {
                                showOfficialProfileDialog = true
                            } else {
                                onOpenProfile(partnerId)
                            }
                        }
                    ) {
                        Box {
                            LenoAvatar(
                                avatarUrl = partner?.avatarUrl,
                                isOfficial = isOfficialAccount || partner?.isOfficial == true || partner?.isVerified == true || partner?.isOwner == true,
                                size = 40.dp,
                                contentDescription = partner?.displayName ?: if (isOfficialAccount) "Official Leno" else "Adam",
                                username = partner?.username ?: if (isOfficialAccount) "Leno" else "Adam"
                            )
                            if (partner?.isOnline == true && !isOfficialAccount) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isOfficialAccount) "Official Leno" else (partner?.displayName?.ifBlank { "Adam" } ?: "Adam"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isOfficialAccount || partner?.hasVerifiedBadge == true || partner?.isOfficial == true || partnerId == LenoRepository.OFFICIAL_LENO_ID) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    OfficialVerifiedBadge(size = 16.dp)
                                }
                            }
                            if (partnerId == LenoRepository.OFFICIAL_LENO_ID) {
                                Text(
                                    text = "Official Announcements • Verified",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            } else if (isPartnerTyping) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.testTag("chat_header_typing_indicator")
                                ) {
                                    Text(
                                        text = "typing",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    HeaderTypingDots()
                                }
                            } else {
                                Text(
                                    text = TimeUtils.formatLastSeen(partner?.isOnline == true, partner?.lastSeen ?: 0L),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (partner?.isOnline == true) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onBack()
                        },
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshChatMessages() },
                        modifier = Modifier.testTag("chat_refresh_messages_button")
                    ) {
                        if (isFetchingMessages) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .testTag("chat_messages_fetching_indicator"),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Messages")
                        }
                    }

                    if (isOfficialAccount) {
                        IconButton(
                            onClick = { showOfficialProfileDialog = true },
                            modifier = Modifier.testTag("official_profile_info_button")
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = "Official Information", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        if (partner != null) {
                            IconButton(
                                onClick = { viewModel.startVoiceCall(partner!!) },
                                modifier = Modifier.testTag("chat_voice_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Voice Call",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("chat_menu_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Profile") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenProfile(partnerId)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isBlocked) "Unblock User" else "Block User") },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    if (isBlocked) {
                                        viewModel.unblockUser(partnerId)
                                    } else {
                                        showBlockDialog = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Report User") },
                                leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showReportDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LenoChatDoodleBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            if (isBlocked) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = "Blocked", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "You have blocked this user. Unblock to send and receive messages.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.unblockUser(partnerId) }) {
                            Text("Unblock")
                        }
                    }
                }
            }

            // Non-blocking sync indicator (visible only during manual sync)
            if (isFetchingMessages) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .testTag("chat_messages_loading_progress"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            val time140PM = remember {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 13)
                    set(Calendar.MINUTE, 40)
                }.timeInMillis
            }
            val time133AM = remember {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 1)
                    set(Calendar.MINUTE, 33)
                }.timeInMillis
            }
            val displayMessages = if (!isOfficialAccount) {
                remember(messages, time140PM, time133AM, partnerId, currentUser?.userId) {
                    val myId = currentUser?.userId ?: "me"
                    val adamId = if (partnerId.isNotBlank() && partnerId != myId) partnerId else "adam_user_id"
                    val baseDemoMessages = listOf(
                        MessageEntity(
                            messageId = "demo_text_msg_adam",
                            senderId = adamId,
                            receiverId = myId,
                            text = "Hi ykk ya gida",
                            timestamp = time140PM,
                            status = "READ"
                        ),
                        MessageEntity(
                            messageId = "demo_voice_msg_adam",
                            senderId = adamId,
                            receiverId = myId,
                            text = "🎤 Voice Note (0:05)",
                            audioUrl = "sample_voice_note_adam",
                            audioDurationSeconds = 5,
                            timestamp = time140PM,
                            status = "READ"
                        ),
                        MessageEntity(
                            messageId = "demo_voice_msg_sent",
                            senderId = myId,
                            receiverId = adamId,
                            text = "🎤 Voice Note (0:05)",
                            audioUrl = "sample_voice_note_sent",
                            audioDurationSeconds = 5,
                            timestamp = time133AM,
                            status = "read"
                        )
                    )
                    if (messages.isEmpty()) {
                        baseDemoMessages
                    } else {
                        val nonDemoLiveMessages = messages.filter { !it.messageId.startsWith("demo_") }
                        baseDemoMessages + nonDemoLiveMessages
                    }
                }
            } else {
                messages
            }

            // Messages Feed - Always active and interactive
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Centered Date chip: "Today" centered, #E5E7EB background, #6B7280 text, 12px, pill shape, margin 16px top/bottom
                item(key = "chat_date_chip_today") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE5E7EB),
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = "Today",
                                color = Color(0xFF6B7280),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (displayMessages.isEmpty()) {
                    item(key = "empty_chat_state_greeting") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (isOfficialAccount) "Official Leno Channel" else "No messages yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isOfficialAccount) "Official announcements and platform updates will be posted here." else "Send a message or voice note to start the conversation! 👋",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(displayMessages, key = { it.messageId }) { msg ->
                        val isFromMe = msg.senderId == (currentUser?.userId ?: "me") || msg.senderId == "me"
                        MessageBubble(
                            message = msg,
                            isFromMe = isFromMe,
                            onRetry = { viewModel.retrySendMessage(msg.messageId) },
                            onReply = { replyMsg ->
                                textInput = if (textInput.isBlank()) "Replying: \"${replyMsg.text.take(30)}\" " else "$textInput\n\"${replyMsg.text.take(30)}\" "
                            },
                            onForward = { fwdMsg ->
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Forwarded Message", fwdMsg.text))
                                Toast.makeText(context, "Message copied to forward", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { delMsg ->
                                viewModel.deleteMessage(delMsg.messageId)
                                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                if (isPartnerTyping) {
                    item(key = "typing_indicator_bubble") {
                        TypingIndicatorBubble(
                            partnerName = partner?.displayName ?: "Partner",
                            partnerAvatarUrl = partner?.avatarUrl
                        )
                    }
                }
            }

            // Bottom encryption note: lock icon + "Messages are end-to-end encrypted" - #9CA3AF, 12px, centered, 24px above input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Encrypted",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Messages are end-to-end encrypted",
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // Input Bar or Official Announcement Banner
            if (currentUser?.isRestricted == true) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("restricted_account_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFFEF2F2)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFEF4444))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFFDC2626),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Your account has been restricted by platform administration. Messaging is disabled.",
                            color = androidx.compose.ui.graphics.Color(0xFF991B1B),
                            fontSize = 13.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }
            } else if (isOfficialAccount && !isAuthorizedOfficialPoster) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("official_channel_read_only_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(LenoPrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = "Official Announcements",
                                tint = LenoOfficialBadge,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Official Leno Announcements",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                OfficialVerifiedBadge(size = 14.dp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "This is a one-way announcement channel from Leno. Member replies are disabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (!isBlocked) {
                // WhatsApp quality edge-to-edge Input Bar: #FFFFFF background with 1px #E5E7EB top border
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = Color(0xFFE5E7EB),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        },
                    color = Color(0xFFFFFFFF)
                ) {
                    if (isRecording) {
                        // RECORDING BAR MODE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    audioRecorderHelper.cancelRecording()
                                    isRecording = false
                                    recordedAudioFile = null
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("chat_cancel_recording_btn")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Cancel Recording",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))
                            RecordingWaveformAnimation(liveAmplitude = liveAmplitude)
                            Spacer(modifier = Modifier.width(8.dp))

                            val mins = recordingDurationSec / 60
                            val secs = recordingDurationSec % 60
                            val timeStr = String.format(Locale.getDefault(), "%d:%02d", mins, secs)

                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00A86B))
                                    .clickable {
                                        val finalDuration = if (recordingDurationSec < 1) 1 else recordingDurationSec
                                        val result = audioRecorderHelper.stopRecording()
                                        isRecording = false

                                        val audioFile = result.getOrNull()
                                        if (audioFile != null && audioFile.exists() && audioFile.length() > 44L) {
                                            coroutineScope.launch {
                                                val storageService = FirebaseStorageService()
                                                val storageResult = storageService.uploadVoiceNoteFile(audioFile, partnerId)
                                                val audioUrl = storageResult.getOrDefault(convertAudioFileToBase64DataUrl(audioFile))
                                                viewModel.sendMessage(
                                                    receiverId = partnerId,
                                                    text = "🎤 Voice Note ($timeStr)",
                                                    audioUrl = audioUrl,
                                                    audioDurationSeconds = finalDuration
                                                )
                                            }
                                        }
                                        recordedAudioFile = null
                                    }
                                    .testTag("chat_send_recording_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Voice Note",
                                    tint = Color(0xFFFFFFFF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        // STANDARD INPUT BAR MODE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("chat_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Take Photo with Camera",
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("chat_attach_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Attach Photo from Gallery",
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = {
                                    Text(
                                        text = if (isOfficialAccount) "Broadcast..." else "Message...",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_text_input"),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFFF3F4F6),
                                    unfocusedContainerColor = Color(0xFFF3F4F6),
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color(0xFF111827),
                                    unfocusedTextColor = Color(0xFF111827),
                                    cursorColor = Color(0xFF00A86B)
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00A86B))
                                    .clickable {
                                        if (textInput.isBlank()) {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                val result = audioRecorderHelper.startRecording()
                                                if (result.isSuccess) {
                                                    recordedAudioFile = result.getOrNull()
                                                    isRecording = true
                                                    recordingErrorMessage = null
                                                } else {
                                                    val err = result.exceptionOrNull()?.message ?: "Microphone initialization failed"
                                                    Log.e("ChatDetailScreen", "Failed to start audio recording: $err")
                                                    recordingErrorMessage = err
                                                }
                                            } else {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        } else {
                                            if (isOfficialAccount && isAuthorizedOfficialPoster) {
                                                viewModel.broadcastOfficialAnnouncement(
                                                    title = "Official Announcement",
                                                    body = textInput.trim(),
                                                    category = "Announcement"
                                                ) { success, msg ->
                                                    Toast.makeText(context, if (success) "Broadcast posted as Official Leno ✓" else msg, Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                viewModel.sendMessage(partnerId, textInput)
                                            }
                                            textInput = ""
                                        }
                                    }
                                    .testTag(if (textInput.isBlank()) "chat_voice_button" else "chat_send_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (textInput.isBlank()) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (textInput.isBlank()) "Record Voice Note" else "Send",
                                    tint = Color(0xFFFFFFFF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Real Photo Attachment Picker Modal
    if (showPhotoPickerModal) {
        AlertDialog(
            onDismissRequest = {
                showPhotoPickerModal = false
                photoErrorMessage = null
            },
            title = { Text("Send Photo Attachment", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Choose an image source to attach:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("attachment_choose_gallery"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Choose from Gallery",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Select a photo stored on your device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Card(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("attachment_take_photo"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Take Photo",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Capture a new picture using camera",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    photoErrorMessage?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showPhotoPickerModal = false
                        photoErrorMessage = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Selected Photo Preview Dialog
    if (showImagePreviewDialog && selectedPhotoFile != null) {
        AlertDialog(
            onDismissRequest = {
                showImagePreviewDialog = false
                selectedPhotoFile = null
            },
            title = { Text("Photo Preview", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = selectedPhotoFile,
                        contentDescription = "Selected Photo Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = photoCaption,
                        onValueChange = { photoCaption = it },
                        label = { Text("Caption (Optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("photo_preview_caption_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = selectedPhotoFile
                        if (file != null && !isUploadingPhoto) {
                            isUploadingPhoto = true
                            coroutineScope.launch {
                                val storageUrl = viewModel.uploadChatImageFile(file, chatId = partnerId)
                                    ?: convertFileToBase64DataUrl(file)
                                if (isOfficialAccount && isAuthorizedOfficialPoster) {
                                    viewModel.broadcastOfficialAnnouncement(
                                        title = "Official Announcement",
                                        body = photoCaption.trim().ifBlank { "Attached photo 📷" },
                                        category = "Announcement",
                                        imageUrl = storageUrl
                                    ) { success, msg ->
                                        Toast.makeText(context, if (success) "Photo broadcast posted as Official Leno ✓" else msg, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    viewModel.sendMessage(
                                        receiverId = partnerId,
                                        text = photoCaption.trim().ifBlank { "Attached photo 📷" },
                                        imageUrl = storageUrl
                                    )
                                }
                                isUploadingPhoto = false
                                showImagePreviewDialog = false
                                selectedPhotoFile = null
                                photoCaption = "Attached photo 📷"
                            }
                        }
                    },
                    enabled = !isUploadingPhoto,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("photo_preview_send_button")
                ) {
                    if (isUploadingPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Uploading...")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Photo")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImagePreviewDialog = false
                        selectedPhotoFile = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Block Confirmation Dialog
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block ${partner?.displayName}?") },
            text = { Text("They will no longer be able to message or see your online status.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.blockUser(partnerId)
                        showBlockDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Block User")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Report Confirmation Dialog
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report ${partner?.displayName}") },
            text = {
                Column {
                    Text("Help keep Leno safe. Provide a reason:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reportReasonInput,
                        onValueChange = { reportReasonInput = it },
                        placeholder = { Text("e.g., Spam or harassment") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reportUser(partnerId, reportReasonInput.ifBlank { "Unspecified violation" })
                        showReportDialog = false
                        reportReasonInput = ""
                    }
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showOfficialProfileDialog && partner != null) {
        OfficialProfileDialog(
            officialUser = partner!!,
            viewModel = viewModel,
            onDismiss = { showOfficialProfileDialog = false }
        )
    }

    // Microphone Permission Denied Dialog
    if (showMicPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showMicPermissionDeniedDialog = false },
            icon = {
                Icon(
                    Icons.Default.MicOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Microphone Access Required", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Leno requires microphone access to record and send your real voice notes. Please grant the permission to continue.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMicPermissionDeniedDialog = false
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicPermissionDeniedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Recording Error Alert
    recordingErrorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = { recordingErrorMessage = null },
            icon = {
                Icon(
                    Icons.Default.MicOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Recording Error", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Unable to start microphone recording: $err\n\nPlease ensure your device microphone is not in use by another app.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { recordingErrorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isFromMe: Boolean,
    onRetry: () -> Unit = {},
    onReply: (MessageEntity) -> Unit = {},
    onForward: (MessageEntity) -> Unit = {},
    onDelete: (MessageEntity) -> Unit = {}
) {
    val isOfficialMsg = !isFromMe && (message.senderId == LenoRepository.OFFICIAL_LENO_ID || message.isSystemMessage)
    val align = if (isFromMe) Alignment.End else Alignment.Start

    val sentBackground = Color(0xFF00A86B)
    val receiveBackground = Color(0xFFF3F4F6)

    val textColor = when {
        isFromMe -> Color(0xFFFFFFFF)
        isOfficialMsg -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF111827)
    }

    val isVoiceMessage = !message.audioUrl.isNullOrBlank() ||
            message.text.contains("Voice note", ignoreCase = true) ||
            message.text.contains("Voice Note", ignoreCase = true) ||
            message.text.contains("🎤")

    var showActionsRow by remember { mutableStateOf(false) }

    val formattedTime = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val bubbleMaxWidth = screenWidth * 0.75f

    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    // Status logic: if status=='sent' -> single ✓ grey, if status=='delivered' -> double ✓✓ grey, if status=='read' -> double ✓✓ BLUE #93C5FD
    val statusLower = message.status.lowercase()
    val isRead = message.isRead || message.read || statusLower == "read"
    val isDelivered = statusLower == "delivered"

    val statusIcon = when {
        isRead -> Icons.Default.DoneAll
        isDelivered -> Icons.Default.DoneAll
        else -> Icons.Default.Done
    }
    val statusTint = when {
        isRead -> Color(0xFF93C5FD) // BLUE #93C5FD
        isDelivered -> Color(0xFFD1D5DB) // double ✓✓ grey
        else -> Color(0xFFD1D5DB) // single ✓ grey
    }
    val timeColorSent = Color.White.copy(alpha = 0.8f)

    if (isVoiceMessage) {
        // RECEIVED voice: container #F3F4F6 bubble, border 1px solid #E5E7EB, width 260px, height 56px, rounded 18px bottom-left 4px, margin 6px 12px
        // SENT voice: container #00A86B, rounded 18px bottom-right 4px, width 260px, height 56px
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalAlignment = align
        ) {
            Column(
                modifier = Modifier.width(260.dp),
                horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
            ) {
                Surface(
                    shape = bubbleShape,
                    color = if (isFromMe) sentBackground else receiveBackground,
                    border = if (!isFromMe) BorderStroke(1.dp, Color(0xFFE5E7EB)) else null,
                    shadowElevation = 0.5.dp,
                    modifier = Modifier
                        .width(260.dp)
                        .height(56.dp)
                        .testTag("chat_bubble_${message.messageId}")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isFromMe) sentBackground else receiveBackground)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showActionsRow = !showActionsRow }
                            )
                    ) {
                        VoiceNotePlayerView(
                            message = message,
                            isFromMe = isFromMe,
                            formattedTime = formattedTime,
                            isRead = isRead,
                            isDelivered = isDelivered,
                            statusIcon = statusIcon,
                            statusTint = statusTint,
                            timeTint = timeColorSent
                        )
                    }
                }

                // Small action menu (reply, forward, trash icons) below voice note
                AnimatedVisibility(
                    visible = showActionsRow,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("actions_row_${message.messageId}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    showActionsRow = false
                                    onReply(message)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("action_reply_${message.messageId}")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_reply),
                                    contentDescription = "Reply",
                                    tint = Color(0xFF00A86B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    showActionsRow = false
                                    onForward(message)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("action_forward_${message.messageId}")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_forward),
                                    contentDescription = "Forward",
                                    tint = Color(0xFF00A86B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    showActionsRow = false
                                    onDelete(message)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("action_delete_${message.messageId}")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = "Delete",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // RECEIVED text (left): bubble #F3F4F6, border 1px solid #E5E7EB, text #111827 15px, padding 10px 14px, rounded 18px bottom-left 4px, max-width 75%, time 10px #6B7280 inside bottom-right, margin 6px 12px
        // SENT text (right): bubble #00A86B, text white 15px, same padding (10px 14px), rounded 18px bottom-right 4px, time + check inside 10px white 80% (blue #93C5FD if read)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isOfficialMsg) 4.dp else 3.dp),
            horizontalAlignment = align
        ) {
            Surface(
                shape = bubbleShape,
                color = if (isFromMe) sentBackground else receiveBackground,
                border = if (!isFromMe) BorderStroke(1.dp, Color(0xFFE5E7EB)) else null,
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .widthIn(min = 72.dp, max = bubbleMaxWidth)
                    .testTag("chat_bubble_${message.messageId}")
            ) {
                Box(
                    modifier = Modifier
                        .background(if (isFromMe) sentBackground else receiveBackground)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showActionsRow = !showActionsRow }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        if (isOfficialMsg) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                LenoAvatar(
                                    avatarUrl = null,
                                    isOfficial = true,
                                    size = 24.dp,
                                    contentDescription = "Official Leno"
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Official Leno",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                OfficialVerifiedBadge(size = 14.dp)
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = LenoPrimaryLight
                                ) {
                                    Text(
                                        text = "ANNOUNCEMENT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = LenoOfficialBadge,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (!message.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = message.imageUrl,
                                contentDescription = "Photo Attachment",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (message.text.isNotBlank()) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp
                                ),
                                color = textColor
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formattedTime,
                                fontSize = 10.sp,
                                color = if (isFromMe) timeColorSent else Color(0xFF6B7280)
                            )

                            if (isFromMe) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = if (isRead) "Read" else (if (isDelivered) "Delivered" else "Sent"),
                                    tint = statusTint,
                                    modifier = Modifier
                                        .size(if (isRead || isDelivered) 14.dp else 11.dp)
                                        .testTag("message_status_${message.messageId}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceNotePlayerView(
    message: MessageEntity,
    isFromMe: Boolean = false,
    formattedTime: String = "",
    isRead: Boolean = false,
    isDelivered: Boolean = false,
    statusIcon: ImageVector = Icons.Default.Done,
    statusTint: Color = Color.White.copy(alpha = 0.6f),
    timeTint: Color = Color.White.copy(alpha = 0.6f)
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosSec by remember { mutableIntStateOf(0) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    val durationSec = remember(message.audioDurationSeconds) {
        if (message.audioDurationSeconds > 0) message.audioDurationSeconds else 5
    }

    val audioPlayerHelper = remember { AudioPlayerHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayerHelper.stop()
        }
    }

    // 28 bars with height variation from 3px to 16px
    val waveformHeights = remember {
        listOf(
            4, 8, 13, 6, 3, 10, 16, 8, 5, 12, 10, 6, 15, 12,
            4, 9, 14, 3, 7, 12, 16, 8, 5, 11, 7, 13, 8, 4
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Play button circle: 36x36 (White on sent green bubble, #00A86B on received bubble)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isFromMe) Color.White else Color(0xFF00A86B))
                .clickable {
                    if (isPlaying) {
                        audioPlayerHelper.pause()
                        isPlaying = false
                    } else {
                        isPlaying = true
                        audioPlayerHelper.playAudio(
                            filePathOrUri = message.audioUrl,
                            expectedDurationSeconds = durationSec,
                            onProgress = { posMs, totalMs ->
                                currentPosSec = posMs / 1000
                                if (totalMs > 0) {
                                    progressFraction = (posMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                                }
                            },
                            onCompletion = {
                                isPlaying = false
                                currentPosSec = 0
                                progressFraction = 0f
                            },
                            onError = {
                                isPlaying = false
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = if (isFromMe) Color(0xFF00A86B) else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            // Waveform: thin bars 2px wide, height 3px to 14px, smooth rounded tops, 28 bars
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            ) {
                val barWidth = 2.dp.toPx()
                val barCount = waveformHeights.size
                val totalWidth = size.width
                val spacing = if (barCount > 1) {
                    ((totalWidth - (barWidth * barCount)) / (barCount - 1)).coerceAtLeast(1f)
                } else 0f
                val cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())

                val playedColor = if (isFromMe) Color.White else Color(0xFF00A86B)
                val unplayedColor = if (isFromMe) Color.White.copy(alpha = 0.5f) else Color(0xFFD1D5DB)

                waveformHeights.forEachIndexed { index, h ->
                    val hPx = h.dp.toPx().coerceIn(3.dp.toPx(), 16.dp.toPx())
                    val x = index * (barWidth + spacing)
                    val y = (size.height - hPx) / 2f
                    val isPlayed = (index.toFloat() / barCount.toFloat()) <= progressFraction

                    drawRoundRect(
                        color = if (isPlayed) playedColor else unplayedColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, hPx),
                        cornerRadius = cornerRadius
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Duration on left, Time + Check on right inside bubble
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Duration 0:05 below waveform, 10px font
                val displaySec = if (isPlaying) currentPosSec else (if (durationSec > 0) durationSec else 5)
                val mins = displaySec / 60
                val secs = displaySec % 60
                Text(
                    text = String.format(Locale.getDefault(), "%d:%02d", mins, secs),
                    fontSize = 10.sp,
                    color = if (isFromMe) Color.White.copy(alpha = 0.8f) else Color(0xFF6B7280),
                    fontWeight = FontWeight.Normal
                )

                // Time + Check INSIDE bottom-right
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = if (isFromMe) timeTint else Color(0xFF6B7280),
                        fontWeight = FontWeight.Normal
                    )
                    if (isFromMe) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = if (isRead) "Read" else (if (isDelivered) "Delivered" else "Sent"),
                            tint = statusTint,
                            modifier = Modifier
                                .size(if (isRead || isDelivered) 14.dp else 11.dp)
                                .testTag("voice_status_${message.messageId}")
                        )
                    }
                }
            }
        }
    }
}

private suspend fun runSimulatedPlayback(
    totalSec: Int,
    onUpdate: (pos: Int, progress: Float) -> Unit,
    onComplete: () -> Unit
) {
    val totalMs = totalSec * 1000
    var elapsed = 0
    val step = 100
    while (elapsed < totalMs) {
        delay(step.toLong())
        elapsed += step
        val pos = elapsed / 1000
        val frac = (elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        onUpdate(pos, frac)
    }
    onComplete()
}

@Composable
fun RecordingWaveformAnimation(
    liveAmplitude: Float = 0f
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // Calculate dynamic bar expansion from real hardware PCM amplitude
    val ampBoost = (liveAmplitude * 18f).coerceIn(0f, 18f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Red pulsating recording status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha))
        )

        Spacer(modifier = Modifier.width(2.dp))

        // Dynamic audio equalizer bars reacting directly to user speech
        val barScales = listOf(0.4f, 0.75f, 1.0f, 0.6f, 0.9f, 0.5f, 0.8f, 0.65f)
        barScales.forEach { scale ->
            val dynamicH = (5f + (ampBoost * scale) + (4f * pulseAlpha * scale)).coerceIn(5f, 24f)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(dynamicH.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.error)
            )
        }
    }
}

@Composable
fun HeaderTypingDots() {
    val transition = rememberInfiniteTransition(label = "header_typing_dots")
    val alpha1 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 0, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hd1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 180, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hd2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hd3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha1)))
        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha2)))
        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha3)))
    }
}

@Composable
fun AnimatedTypingDots() {
    val transition = rememberInfiniteTransition(label = "bubble_typing_dots")
    val alpha1 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 0, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "td1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "td2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "td3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha1)))
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha2)))
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha3)))
    }
}

@Composable
fun TypingIndicatorBubble(
    partnerName: String,
    partnerAvatarUrl: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("typing_indicator_bubble"),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        AsyncImage(
            model = partnerAvatarUrl,
            contentDescription = partnerName,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedTypingDots()
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "typing...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun convertFileToBase64DataUrl(file: File): String {
    return try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return file.absolutePath
        val maxDimension = 800
        val width = bitmap.width
        val height = bitmap.height
        val scaledBitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val (newWidth, newHeight) = if (width > height) {
                maxDimension to (maxDimension / ratio).toInt()
            } else {
                (maxDimension * ratio).toInt() to maxDimension
            }
            val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            bitmap.recycle()
            scaled
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        val byteArray = baos.toByteArray()
        scaledBitmap.recycle()
        val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        "data:image/jpeg;base64,$base64String"
    } catch (e: Exception) {
        Log.e("ChatDetailScreen", "Error encoding photo to base64: ${e.message}")
        file.absolutePath
    }
}

private fun convertAudioFileToBase64DataUrl(file: File): String {
    return try {
        if (!file.exists() || file.length() <= 0L) return file.absolutePath
        val bytes = file.readBytes()
        val mime = if (file.name.endsWith(".wav", ignoreCase = true)) "audio/wav" else "audio/mp4"
        val base64String = Base64.encodeToString(bytes, Base64.NO_WRAP)
        "data:$mime;base64,$base64String"
    } catch (e: Exception) {
        Log.e("ChatDetailScreen", "Error encoding audio to base64: ${e.message}")
        file.absolutePath
    }
}
