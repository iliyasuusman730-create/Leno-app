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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
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
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Badge
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
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
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoEmeraldSent
import com.example.ui.theme.LenoEmeraldSentText
import com.example.ui.theme.LenoEmeraldSentTime
import com.example.ui.theme.LenoSoftMintReceived
import com.example.ui.theme.LenoSoftMintReceivedBorder
import com.example.ui.theme.LenoDarkGreenText
import com.example.ui.theme.LenoDarkGreenTime
import com.example.ui.theme.LenoActionGreen
import com.example.ui.theme.LocalLenoDarkTheme
import com.example.ui.theme.LenoTextPrimary
import com.example.ui.theme.LenoTextSecondary
import com.example.ui.theme.LenoTickCyan
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
            .background(LenoChatBackground)
            .drawBehind {
                val bubbleColor = LenoPrimary.copy(alpha = 0.035f)
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
            viewModel.closeChat()
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
    val messageReactions = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }

    // Notify ViewModel when user is typing in textInput
    LaunchedEffect(textInput, partnerId) {
        if (currentUser?.userId == null) return@LaunchedEffect
        if (textInput.isNotBlank()) {
            viewModel.setUserTyping(partnerId, true)
            delay(2500)
            viewModel.setUserTyping(partnerId, false)
        } else {
            viewModel.setUserTyping(partnerId, false)
        }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showPhotoPickerModal by remember { mutableStateOf(false) }
    var showOfficialProfileDialog by remember { mutableStateOf(false) }
    var showStarredMessagesDialog by remember { mutableStateOf(false) }
    val starredMessageIds by viewModel.starredMessageIds.collectAsState()
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

    val displayMessages = messages

    val isDarkChat = LocalLenoDarkTheme.current

    Scaffold(
        containerColor = if (isDarkChat) Color(0xFF111A16) else Color(0xFFE8ECE9),
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
                                contentDescription = partner?.displayName ?: "Official Leno",
                                username = partner?.username
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
                                    text = if (isOfficialAccount) "Official Leno" else (partner?.displayName?.ifBlank { "Aisha" } ?: "Aisha"),
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
                                text = { Text("Starred Messages") },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                onClick = {
                                    menuExpanded = false
                                    showStarredMessagesDialog = true
                                }
                            )
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

            // Messages Feed with Floating Scroll-Down & New Message Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            val isMsgStarred = starredMessageIds.contains(msg.messageId)
                            MessageBubble(
                                message = msg,
                                isFromMe = isFromMe,
                                isStarred = isMsgStarred,
                                onToggleStar = { viewModel.toggleStarMessage(it.messageId) },
                                reaction = messageReactions[msg.messageId],
                                onReact = { emoji ->
                                    if (messageReactions[msg.messageId] == emoji) {
                                        messageReactions.remove(msg.messageId)
                                    } else {
                                        messageReactions[msg.messageId] = emoji
                                    }
                                },
                                onRetry = { viewModel.retrySendMessage(msg.messageId) },
                                onReply = { replyMsg ->
                                    textInput = if (textInput.isBlank()) "Replying: \"${replyMsg.text.take(30)}\" " else "$textInput\n\"${replyMsg.text.take(30)}\" "
                                },
                                onForward = { fwdMsg ->
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Forwarded Message", fwdMsg.text))
                                    Toast.makeText(context, "Message copied to forward", Toast.LENGTH_SHORT).show()
                                },
                                onCopy = { copyMsg ->
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val contentToCopy = if (copyMsg.text.isNotBlank()) copyMsg.text else if (!copyMsg.audioUrl.isNullOrBlank()) "Voice note" else "Photo attachment"
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Leno Message", contentToCopy))
                                    Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
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

                // In-Chat Floating Scroll Down & New Messages Pill
                val showScrollDown by remember {
                    derivedStateOf { listState.canScrollForward }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollDown,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 12.dp, end = 16.dp)
                ) {
                    val unreadBelowCount = remember(displayMessages, currentUser?.userId) {
                        val myId = currentUser?.userId
                        displayMessages.count { it.senderId != myId && it.status != "READ" && !it.read }
                    }
                    Surface(
                        onClick = {
                            coroutineScope.launch {
                                val targetIndex = (displayMessages.size + 1).coerceAtLeast(0)
                                listState.animateScrollToItem(targetIndex)
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("scroll_down_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            if (unreadBelowCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF25D366))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = unreadBelowCount.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
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
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
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
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Your account has been restricted by platform administration. Messaging is disabled.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
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
                // Theme-aware edge-to-edge Input Bar
                val composerBorderColor = MaterialTheme.colorScheme.outlineVariant
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = composerBorderColor,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        },
                    color = MaterialTheme.colorScheme.surface
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
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

    if (showStarredMessagesDialog) {
        val starredMsgs = remember(displayMessages, starredMessageIds) {
            displayMessages.filter { starredMessageIds.contains(it.messageId) }
        }
        AlertDialog(
            onDismissRequest = { showStarredMessagesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Starred Messages (${starredMsgs.size})", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                if (starredMsgs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No starred messages yet.\nLong-press any message to star it!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(starredMsgs, key = { it.messageId }) { sm ->
                            val isSmFromMe = sm.senderId == currentUser?.userId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showStarredMessagesDialog = false
                                        val targetIdx = displayMessages.indexOfFirst { it.messageId == sm.messageId }
                                        if (targetIdx >= 0) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(targetIdx + 1)
                                            }
                                        }
                                    }
                                    .testTag("starred_item_${sm.messageId}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isSmFromMe) "You" else partner?.displayName ?: "Partner",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val smTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(sm.timestamp))
                                        Text(
                                            text = smTime,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val textPreview = sm.text.ifBlank {
                                        if (!sm.audioUrl.isNullOrBlank()) "🎤 Voice Note" else "📷 Photo"
                                    }
                                    Text(
                                        text = textPreview,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStarredMessagesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun MessageActionsToolbar(
    message: MessageEntity,
    isStarred: Boolean = false,
    onToggleStar: (MessageEntity) -> Unit = {},
    onReact: (String) -> Unit = {},
    onReply: (MessageEntity) -> Unit,
    onForward: (MessageEntity) -> Unit,
    onCopy: (MessageEntity) -> Unit,
    onDelete: (MessageEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val quickEmojis = listOf("❤️", "👍", "🔥", "😂", "😮", "🙏")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .padding(top = 4.dp, bottom = 4.dp)
            .testTag("actions_row_${message.messageId}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick Emoji Reaction Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                quickEmojis.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable {
                                onDismiss()
                                onReact(emoji)
                            }
                            .testTag("reaction_opt_${emoji}_${message.messageId}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 18.sp)
                    }
                }
            }

            // Subtle divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )

            // Action Buttons
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        onDismiss()
                        onToggleStar(message)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("action_star_${message.messageId}")
                ) {
                    Icon(
                        imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isStarred) "Unstar" else "Star",
                        tint = if (isStarred) Color(0xFFF59E0B) else LenoEmeraldSent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        onDismiss()
                        onReply(message)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("action_reply_${message.messageId}")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_reply),
                        contentDescription = "Reply",
                        tint = LenoEmeraldSent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        onDismiss()
                        onForward(message)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("action_forward_${message.messageId}")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_forward),
                        contentDescription = "Forward",
                        tint = LenoEmeraldSent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        onDismiss()
                        onCopy(message)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("action_copy_${message.messageId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = LenoEmeraldSent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = {
                        onDismiss()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isFromMe: Boolean,
    isStarred: Boolean = false,
    onToggleStar: (MessageEntity) -> Unit = {},
    reaction: String? = null,
    onReact: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onReply: (MessageEntity) -> Unit = {},
    onForward: (MessageEntity) -> Unit = {},
    onCopy: (MessageEntity) -> Unit = {},
    onDelete: (MessageEntity) -> Unit = {}
) {
    val isOfficialMsg = !isFromMe && (message.senderId == LenoRepository.OFFICIAL_LENO_ID || message.isSystemMessage)
    val align = if (isFromMe) Alignment.End else Alignment.Start

    // Determine dark mode accurately from current theme background
    val isDark = LocalLenoDarkTheme.current

    // Modern Sleek Chat Bubble Geometry:
    // Outgoing: 14dp rounded corners with 3dp bottom-end anchor pointing toward sender
    // Incoming: 14dp rounded corners with 3dp bottom-start anchor pointing toward contact
    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 3.dp)
    } else {
        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 3.dp, bottomEnd = 14.dp)
    }

    // Modern vibrant emerald gradient for sent messages; crisp clean white with subtle border for received messages
    val sentBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(Color(0xFF087F5B), Color(0xFF066347))
        } else {
            listOf(Color(0xFF087F5B), Color(0xFF0B9E71))
        },
        start = Offset(0f, 0f),
        end = Offset(400f, 400f)
    )

    val receiveBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(Color(0xFF1B2822), Color(0xFF15221C))
        } else {
            listOf(Color(0xFFFFFFFF), Color(0xFFFFFFFF))
        },
        start = Offset(0f, 0f),
        end = Offset(400f, 400f)
    )

    // Clear, visible border so received messages are crisp and never blend into background
    val receiveBorder = if (isDark) Color(0xFF2C3E35) else Color(0xFFD0DCD5)

    val textColor = when {
        isFromMe -> Color.White
        isDark -> Color(0xFFECFDF5)
        else -> Color(0xFF14241F)
    }

    // Time inside bubble (clear readable contrast)
    val timeColor = if (isFromMe) {
        Color.White.copy(alpha = 0.80f)
    } else {
        if (isDark) Color(0xFFA2B5AB) else Color(0xFF5D7269)
    }

    // Status delivery ticks
    val isRead = message.isRead || message.read || message.status.equals("READ", ignoreCase = true)
    val isDelivered = message.status.equals("DELIVERED", ignoreCase = true)

    val tickMuted = Color.White.copy(alpha = 0.78f)
    val tickRead = LenoTickCyan

    val (statusIcon, statusTint, statusDesc) = when {
        isRead -> Triple(Icons.Default.DoneAll, tickRead, "Read")
        isDelivered -> Triple(Icons.Default.DoneAll, tickMuted, "Delivered")
        else -> Triple(Icons.Default.Done, tickMuted, "Sent")
    }

    val isVoiceMessage = !message.audioUrl.isNullOrBlank() ||
            message.text.contains("Voice note", ignoreCase = true) ||
            message.text.contains("Voice Note", ignoreCase = true) ||
            message.text.contains("🎤")

    var showActionsRow by remember(message.messageId) {
        mutableStateOf(false)
    }

    val formattedTime = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val bubbleMaxWidth = (screenWidth * 0.74f).coerceAtMost(310.dp)

    if (isOfficialMsg) {
        // ========================================================
        // OFFICIAL LENO BROADCAST CARD (VIP Verified Broadcast Design)
        // High contrast, clear typography, authoritative card styling
        // ========================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = screenWidth * 0.90f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF162720) else Color.White,
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF244033) else Color(0xFFD6E6DE)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .testTag("chat_bubble_${message.messageId}")
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showActionsRow = !showActionsRow }
                        )
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Vertical signature emerald accent stripe on left edge
                        Box(
                            modifier = Modifier
                                .width(4.5.dp)
                                .defaultMinSize(minHeight = 80.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(LenoPrimary, LenoActionGreen)
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
                        ) {
                            // Official Channel Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LenoAvatar(
                                    avatarUrl = null,
                                    isOfficial = true,
                                    size = 28.dp,
                                    contentDescription = "Official Leno"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Official Leno",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isDark) Color(0xFFE8F5E9) else LenoTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        OfficialVerifiedBadge(size = 14.dp)
                                    }
                                    Text(
                                        text = "@leno • Official Channel",
                                        fontSize = 10.5.sp,
                                        color = LenoPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) Color(0xFF1F3D2F) else Color(0xFFE8F7F0),
                                    border = BorderStroke(0.5.dp, if (isDark) Color(0xFF2C5642) else Color(0xFFBBE5D2))
                                ) {
                                    Text(
                                        text = "ANNOUNCEMENT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = if (isDark) Color(0xFF80E5BA) else LenoPrimary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (!message.imageUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    AsyncImage(
                                        model = message.imageUrl,
                                        contentDescription = "Photo Attachment",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Announcement Body Text - High Contrast & Crystal Clear
                            if (message.text.isNotBlank()) {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.5.sp,
                                        lineHeight = 21.sp,
                                        letterSpacing = 0.1.sp
                                    ),
                                    color = if (isDark) Color(0xFFF1FAF5) else LenoTextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Official Footer: Shield tag + Formatted time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Verified Broadcast",
                                        tint = LenoPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Verified Broadcast",
                                        fontSize = 10.sp,
                                        color = if (isDark) Color(0xFFA5B8AF) else LenoTextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = formattedTime,
                                    fontSize = 10.5.sp,
                                    color = if (isDark) Color(0xFFA5B8AF) else LenoTextSecondary,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Reaction Chip anchored to the bubble
                if (reaction != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF1E2D26) else Color.White,
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C3E35) else Color(0xFFE2E8F0)),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { onReact(reaction) }
                            .testTag("reaction_badge_${message.messageId}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(text = reaction, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "1",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White.copy(alpha = 0.9f) else Color(0xFF4B5563)
                            )
                        }
                    }
                }

                // Message Actions Toolbar
                AnimatedVisibility(
                    visible = showActionsRow,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    MessageActionsToolbar(
                        message = message,
                        isStarred = isStarred,
                        onToggleStar = onToggleStar,
                        onReact = onReact,
                        onReply = onReply,
                        onForward = onForward,
                        onCopy = onCopy,
                        onDelete = onDelete,
                        onDismiss = { showActionsRow = false }
                    )
                }
            }
        }
    } else if (isVoiceMessage) {
        // ========================================================
        // VOICE NOTE BUBBLE (Compact single-flow, visible audio wave)
        // ========================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.5.dp),
            horizontalAlignment = align
        ) {
            Column(
                modifier = Modifier.widthIn(min = 185.dp, max = (screenWidth * 0.70f).coerceAtMost(250.dp)),
                horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
            ) {
                Surface(
                    shape = bubbleShape,
                    color = Color.Transparent,
                    border = if (isFromMe) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f)) else BorderStroke(1.dp, receiveBorder),
                    shadowElevation = 1.5.dp,
                    modifier = Modifier.testTag("chat_bubble_${message.messageId}")
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (isFromMe) sentBrush else receiveBrush)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showActionsRow = !showActionsRow }
                            )
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        VoiceNotePlayerView(
                            message = message,
                            isFromMe = isFromMe,
                            formattedTime = formattedTime,
                            isStarred = isStarred,
                            statusIcon = statusIcon,
                            statusDesc = statusDesc,
                            statusTint = statusTint
                        )
                    }
                }

                // Reaction Chip anchored to the bubble
                if (reaction != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isFromMe) Color(0xFF064E3B) else (if (isDark) Color(0xFF1E2D26) else Color.White),
                        border = BorderStroke(1.dp, if (isFromMe) Color(0xFF10B981).copy(alpha = 0.6f) else (if (isDark) Color(0xFF2C3E35) else Color(0xFFE2E8F0))),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .padding(top = 2.dp, start = if (isFromMe) 0.dp else 8.dp, end = if (isFromMe) 8.dp else 0.dp)
                            .clickable { onReact(reaction) }
                            .testTag("reaction_badge_${message.messageId}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(text = reaction, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "1",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFromMe) Color.White.copy(alpha = 0.9f) else (if (isDark) Color.White.copy(alpha = 0.9f) else Color(0xFF4B5563))
                            )
                        }
                    }
                }

                // Message actions: Reply, Forward, Copy, Delete + Emoji reactions
                AnimatedVisibility(
                    visible = showActionsRow,
                    modifier = Modifier.align(if (isFromMe) Alignment.End else Alignment.Start)
                ) {
                    MessageActionsToolbar(
                        message = message,
                        isStarred = isStarred,
                        onToggleStar = onToggleStar,
                        onReact = onReact,
                        onReply = onReply,
                        onForward = onForward,
                        onCopy = onCopy,
                        onDelete = onDelete,
                        onDismiss = { showActionsRow = false }
                    )
                }
            }
        }
    } else {
        // ========================================================
        // TEXT & PHOTO BUBBLE (Compact inline layout, crisp contrast)
        // ========================================================
        val isShortSingleLine = remember(message.text, message.imageUrl) {
            message.imageUrl.isNullOrBlank() &&
                    message.text.length <= 26 &&
                    !message.text.contains('\n')
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            horizontalAlignment = align
        ) {
            Column(
                modifier = Modifier.widthIn(max = bubbleMaxWidth),
                horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
            ) {
                Surface(
                    shape = bubbleShape,
                    color = Color.Transparent,
                    border = if (isFromMe) BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)) else BorderStroke(1.dp, receiveBorder),
                    shadowElevation = 1.5.dp,
                    modifier = Modifier.testTag("chat_bubble_${message.messageId}")
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (isFromMe) sentBrush else receiveBrush)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showActionsRow = !showActionsRow }
                            )
                    ) {
                        if (isShortSingleLine) {
                            // Ultra-compact single-line layout: Text and Time inline with minimal padding
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp)
                            ) {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.5.sp,
                                        lineHeight = 18.5.sp
                                    ),
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 0.5.dp)
                                ) {
                                    if (isStarred) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Starred",
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier
                                                .size(9.dp)
                                                .padding(end = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = formattedTime,
                                        fontSize = 9.5.sp,
                                        color = timeColor,
                                        fontWeight = FontWeight.Normal
                                    )
                                    if (isFromMe) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = statusIcon,
                                            contentDescription = statusDesc,
                                            tint = statusTint,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .testTag("message_status_${message.messageId}")
                                        )
                                    }
                                }
                            }
                        } else {
                            // Multi-line or Photo layout with tight spacing
                            Column(
                                modifier = Modifier.padding(
                                    start = 9.dp,
                                    end = 9.dp,
                                    top = if (!message.imageUrl.isNullOrBlank()) 5.dp else 5.5.dp,
                                    bottom = 4.dp
                                )
                            ) {
                                if (!message.imageUrl.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    ) {
                                        AsyncImage(
                                            model = message.imageUrl,
                                            contentDescription = "Photo Attachment",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f))
                                                    )
                                                )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (message.text.isNotBlank()) {
                                    Text(
                                        text = message.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 14.5.sp,
                                            lineHeight = 19.5.sp,
                                            letterSpacing = 0.1.sp
                                        ),
                                        color = textColor
                                    )
                                }

                                // Bottom right time and check mark
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(top = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isStarred) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Starred",
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier
                                                .size(9.dp)
                                                .padding(end = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = formattedTime,
                                        fontSize = 9.5.sp,
                                        color = timeColor,
                                        fontWeight = FontWeight.Normal
                                    )

                                    if (isFromMe) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = statusIcon,
                                            contentDescription = statusDesc,
                                            tint = statusTint,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .testTag("message_status_${message.messageId}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Reaction Chip anchored to the bubble
                if (reaction != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isFromMe) Color(0xFF064E3B) else (if (isDark) Color(0xFF1E2D26) else Color.White),
                        border = BorderStroke(1.dp, if (isFromMe) Color(0xFF10B981).copy(alpha = 0.6f) else (if (isDark) Color(0xFF2C3E35) else Color(0xFFE2E8F0))),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .padding(top = 2.dp, start = if (isFromMe) 0.dp else 8.dp, end = if (isFromMe) 8.dp else 0.dp)
                            .clickable { onReact(reaction) }
                            .testTag("reaction_badge_${message.messageId}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(text = reaction, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "1",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFromMe) Color.White.copy(alpha = 0.9f) else (if (isDark) Color.White.copy(alpha = 0.9f) else Color(0xFF4B5563))
                            )
                        }
                    }
                }

                // Simple message actions: Reply, Forward, Copy, Delete + Quick Reactions
                AnimatedVisibility(
                    visible = showActionsRow,
                    modifier = Modifier.align(if (isFromMe) Alignment.End else Alignment.Start)
                ) {
                    MessageActionsToolbar(
                        message = message,
                        isStarred = isStarred,
                        onToggleStar = onToggleStar,
                        onReact = onReact,
                        onReply = onReply,
                        onForward = onForward,
                        onCopy = onCopy,
                        onDelete = onDelete,
                        onDismiss = { showActionsRow = false }
                    )
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
    isStarred: Boolean = false,
    statusIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    statusDesc: String = "",
    statusTint: Color = Color.Unspecified
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

    // Playback Speed Toggle (1.0x, 1.5x, 2.0x)
    val speeds = listOf(1.0f to "1.0x", 1.5f to "1.5x", 2.0f to "2.0x")
    var speedIndex by remember { mutableIntStateOf(0) }

    // 22 sculpted frequency bars for clean compact waveform
    val waveformHeights = remember {
        listOf(
            6, 11, 16, 12, 8, 15, 18, 12,
            7, 15, 19, 14, 9, 17, 13, 8,
            14, 18, 11, 7, 12, 6
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Compact Play/Pause Circular Button (34dp)
        Surface(
            shape = CircleShape,
            color = if (isFromMe) Color.White else LenoEmeraldSent,
            shadowElevation = 2.dp,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
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
                        audioPlayerHelper.setPlaybackSpeed(speeds[speedIndex].first)
                    }
                }
                .testTag("voice_play_pause_btn_${message.messageId}"),
            contentColor = if (isFromMe) LenoEmeraldSent else Color.White
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isFromMe) LenoEmeraldSent else Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Center & Right: Waveform and Metadata
        Column(modifier = Modifier.weight(1f)) {
            // Interactive Waveform
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            progressFraction = frac
                            val targetMs = (frac * (durationSec * 1000)).toInt()
                            currentPosSec = targetMs / 1000
                            audioPlayerHelper.seekTo(targetMs)
                        }
                    }
            ) {
                val barWidth = 2.2.dp.toPx()
                val barCount = waveformHeights.size
                val totalWidth = size.width
                val spacing = if (barCount > 1) {
                    ((totalWidth - (barWidth * barCount)) / (barCount - 1)).coerceAtLeast(1f)
                } else 0f
                val cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())

                val playedColor = if (isFromMe) Color.White else LenoPrimary
                // Sharp, clearly visible sage green for received unplayed bars:
                val unplayedColor = if (isFromMe) Color.White.copy(alpha = 0.40f) else Color(0xFF97AEA2)

                waveformHeights.forEachIndexed { index, h ->
                    val hPx = h.dp.toPx().coerceIn(3.dp.toPx(), 15.dp.toPx())
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

            // Metadata row: [Duration] [1.0x] -------- [Time] [Checks]
            val isDarkVoice = LocalLenoDarkTheme.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val elapsedMins = currentPosSec / 60
                    val elapsedSecs = currentPosSec % 60
                    val totalMins = durationSec / 60
                    val totalSecs = durationSec % 60
                    val durationText = if (isPlaying || progressFraction > 0f) {
                        String.format(Locale.getDefault(), "%d:%02d / %d:%02d", elapsedMins, elapsedSecs, totalMins, totalSecs)
                    } else {
                        String.format(Locale.getDefault(), "%d:%02d", totalMins, totalSecs)
                    }

                    Text(
                        text = durationText,
                        fontSize = 10.sp,
                        color = when {
                            isFromMe -> Color.White.copy(alpha = 0.88f)
                            isDarkVoice -> Color(0xFFECFDF5)
                            else -> Color(0xFF14241F)
                        },
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Compact Speed Toggle Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isFromMe -> Color.White.copy(alpha = 0.22f)
                            isDarkVoice -> Color(0xFF263830)
                            else -> Color(0xFFE5EFE9)
                        },
                        border = when {
                            isFromMe -> null
                            isDarkVoice -> BorderStroke(0.5.dp, Color(0xFF3A5247))
                            else -> BorderStroke(0.5.dp, Color(0xFFB5CAC0))
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                speedIndex = (speedIndex + 1) % speeds.size
                                audioPlayerHelper.setPlaybackSpeed(speeds[speedIndex].first)
                            }
                            .testTag("voice_speed_toggle_${message.messageId}")
                    ) {
                        Text(
                            text = speeds[speedIndex].second,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isFromMe -> Color.White
                                isDarkVoice -> Color(0xFF80E5BA)
                                else -> LenoPrimary
                            },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Time + Checks
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isStarred) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Starred",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier
                                .size(9.dp)
                                .padding(end = 2.dp)
                        )
                    }
                    Text(
                        text = formattedTime,
                        fontSize = 9.5.sp,
                        color = when {
                            isFromMe -> Color.White.copy(alpha = 0.80f)
                            isDarkVoice -> Color(0xFFA2B5AB)
                            else -> Color(0xFF5D7269)
                        },
                        fontWeight = FontWeight.Normal
                    )
                    if (isFromMe && statusIcon != null) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = statusDesc,
                            tint = statusTint,
                            modifier = Modifier
                                .size(12.dp)
                                .testTag("voice_message_status_${message.messageId}")
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
