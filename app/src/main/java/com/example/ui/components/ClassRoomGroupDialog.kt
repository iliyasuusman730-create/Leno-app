package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.ClassEntity
import com.example.data.entity.ClassQuestionEntity
import com.example.data.entity.LessonEntity
import com.example.data.entity.UserEntity
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.data.service.FirebaseStorageService
import com.example.util.AudioPlayerHelper
import com.example.util.AudioRecorderHelper
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClassRoomGroupDialog(
    classItem: ClassEntity,
    currentUser: UserEntity?,
    isInstructor: Boolean,
    isAdmin: Boolean = currentUser?.isAdmin == true,
    lessons: List<LessonEntity>,
    questions: List<ClassQuestionEntity> = emptyList(),
    enrolledStudents: List<UserEntity> = emptyList(),
    onAskQuestion: (text: String, audioUrl: String?, durationSec: Int) -> Unit = { _, _, _ -> },
    onAnswerQuestion: (questionId: String, text: String, audioUrl: String?, durationSec: Int) -> Unit = { _, _, _, _ -> },
    onDismiss: () -> Unit,
    onChatWithInstructor: () -> Unit,
    onOpenLesson: (LessonEntity) -> Unit,
    onAddLesson: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("❓ Q&A (${questions.size})", "📚 Lessons (${lessons.size})", "👥 Members", "ℹ️ About")

    val currentUserName = currentUser?.displayName?.ifBlank { currentUser.username } ?: "You"
    val currentUserLinoId = currentUser?.linoId?.ifBlank {
        "LEN-${(currentUser.userId).replace("usr_", "").padEnd(8, '0').take(8).uppercase()}"
    } ?: "LEN-00000000"

    val effectiveInstructor = isInstructor || isAdmin

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(onBack = onDismiss)

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar with visible navigation back button on top-left
                Surface(
                    color = LenoPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Clear Back Button on the top left
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("classroom_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Classes",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = classItem.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val roleLabel = if (isInstructor) {
                                    "Your Class"
                                } else if (isAdmin) {
                                    "Admin / Owner Access 👑"
                                } else {
                                    "Enrolled Student"
                                }
                                Text(
                                    text = "${classItem.subject} • $roleLabel",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("classroom_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Sub-header stats row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "👥 Group: ${classItem.enrolledStudentsCount + 1} Members (You included)",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = "Active Group ✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Tabs Navigation
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = LenoPrimary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        0 -> ClassQuestionsTab(
                            classItem = classItem,
                            questions = questions,
                            isInstructor = effectiveInstructor,
                            currentUserId = currentUser?.userId ?: "",
                            onAskQuestion = onAskQuestion,
                            onAnswerQuestion = onAnswerQuestion,
                            onAddLessonNote = onAddLesson
                        )

                        1 -> ClassLessonsTab(
                            classItem = classItem,
                            lessons = lessons,
                            isInstructor = effectiveInstructor,
                            onOpenLesson = onOpenLesson,
                            onAddLesson = onAddLesson
                        )

                        2 -> ClassMembersTab(
                            classItem = classItem,
                            currentUser = currentUser,
                            currentUserName = currentUserName,
                            currentUserLinoId = currentUserLinoId,
                            enrolledStudents = enrolledStudents,
                            isInstructor = effectiveInstructor,
                            onChatWithInstructor = onChatWithInstructor
                        )

                        3 -> ClassAboutTab(classItem = classItem)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// VOICE NOTE PLAYER COMPONENT FOR CLASSROOM
// ----------------------------------------------------
@Composable
fun ClassVoiceNotePlayer(
    audioUrl: String,
    durationSec: Int,
    accentColor: Color = LenoPrimary,
    label: String = "Voice Note"
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosSec by remember { mutableIntStateOf(0) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    val effectiveDuration = remember(durationSec) { if (durationSec > 0) durationSec else 10 }
    val audioPlayerHelper = remember { AudioPlayerHelper(context) }

    DisposableEffect(audioUrl) {
        onDispose {
            audioPlayerHelper.stop()
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        audioPlayerHelper.pause()
                        isPlaying = false
                    } else {
                        isPlaying = true
                        audioPlayerHelper.playAudio(
                            filePathOrUri = audioUrl,
                            expectedDurationSeconds = effectiveDuration,
                            onProgress = { posMs, totalMs ->
                                if (totalMs > 0) {
                                    progressFraction = (posMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                                    currentPosSec = (posMs / 1000).toInt()
                                }
                            },
                            onCompletion = {
                                isPlaying = false
                                progressFraction = 0f
                                currentPosSec = 0
                            },
                            onError = {
                                isPlaying = false
                            }
                        )
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause Voice Note" else "Play Voice Note",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎤 $label",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                    val displaySec = if (isPlaying) currentPosSec else effectiveDuration
                    val mins = displaySec / 60
                    val secs = displaySec % 60
                    Text(
                        text = String.format(Locale.getDefault(), "%d:%02d", mins, secs),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.2f)
                )
            }
        }
    }
}

// ----------------------------------------------------
// TAB 0: CLASS QUESTIONS & TEACHER ANSWERS (Q&A)
// ----------------------------------------------------
@Composable
private fun ClassQuestionsTab(
    classItem: ClassEntity,
    questions: List<ClassQuestionEntity>,
    isInstructor: Boolean,
    currentUserId: String,
    onAskQuestion: (text: String, audioUrl: String?, durationSec: Int) -> Unit,
    onAnswerQuestion: (questionId: String, text: String, audioUrl: String?, durationSec: Int) -> Unit,
    onAddLessonNote: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    var questionInput by remember { mutableStateOf("") }
    var answeringQuestionId by remember { mutableStateOf<String?>(null) }
    var answerInput by remember { mutableStateOf("") }

    // Student Voice Recording State
    val audioRecorderHelper = remember { AudioRecorderHelper(context) }
    var isRecordingStudentQuestion by remember { mutableStateOf(false) }
    var studentRecordingDurationSec by remember { mutableIntStateOf(0) }
    var isUploadingStudentAudio by remember { mutableStateOf(false) }

    // Teacher Voice Recording State (when answering)
    var isRecordingTeacherAnswer by remember { mutableStateOf(false) }
    var teacherRecordingDurationSec by remember { mutableIntStateOf(0) }
    var recordedTeacherAudioFile by remember { mutableStateOf<File?>(null) }
    var teacherAudioDurationSec by remember { mutableIntStateOf(0) }

    // Permission launcher for microphone
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingMicAction?.invoke()
        } else {
            Toast.makeText(context, "Microphone permission is required to record voice notes in class", Toast.LENGTH_SHORT).show()
        }
        pendingMicAction = null
    }

    fun checkAndRequestMic(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            pendingMicAction = onGranted
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Timer for student recording
    LaunchedEffect(isRecordingStudentQuestion) {
        if (isRecordingStudentQuestion) {
            studentRecordingDurationSec = 0
            while (isRecordingStudentQuestion) {
                delay(1000L)
                studentRecordingDurationSec++
            }
        }
    }

    // Timer for teacher recording
    LaunchedEffect(isRecordingTeacherAnswer) {
        if (isRecordingTeacherAnswer) {
            teacherRecordingDurationSec = 0
            while (isRecordingTeacherAnswer) {
                delay(1000L)
                teacherRecordingDurationSec++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (isRecordingStudentQuestion || isRecordingTeacherAnswer) {
                    audioRecorderHelper.stopRecording()
                }
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Notice Banner
        Surface(
            color = if (isInstructor) LenoPrimaryVeryLight else Color(0xFFE8F5E9),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isInstructor) LenoPrimaryDark else Color(0xFF2E7D32),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isInstructor) {
                        "Teacher Role: Review and answer student questions below. Tap 'Add Lesson Note' to post new syllabus materials."
                    } else {
                        "Student Rule: In this class, students can only ask questions. Your teacher will reply with verified answers and lesson notes."
                    },
                    fontSize = 11.sp,
                    color = if (isInstructor) LenoPrimaryDark else Color(0xFF1B5E20),
                    lineHeight = 15.sp
                )
            }
        }

        // Questions List
        if (questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = LenoPrimaryLight,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isInstructor) "No Student Questions Yet" else "Have a Question for Your Teacher?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isInstructor) {
                            "Questions asked by enrolled students will appear here for you to answer."
                        } else {
                            "Ask any question regarding '${classItem.title}' below. The teacher will respond with verified solutions."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(questions, key = { it.questionId }) { q ->
                    val isMyQuestion = q.senderUserId == currentUserId
                    val isAnsweringThis = answeringQuestionId == q.questionId

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (q.isAnswered) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (q.isAnswered) Color(0xFF81C784) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Question Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(LenoPrimaryLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = q.senderName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = LenoPrimaryDark
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isMyQuestion) "You (${q.senderName})" else q.senderName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFE0E0E0)
                                            ) {
                                                Text(
                                                    text = "Student",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF424242),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${q.senderLinoId} • ${timeFormat.format(Date(q.timestamp))}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (q.isAnswered) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                ) {
                                    Text(
                                        text = if (q.isAnswered) "Answered ✓" else "Pending ⏳",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (q.isAnswered) Color(0xFF2E7D32) else Color(0xFFE65100),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Question Content
                            if (q.questionText.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = "Q: ",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = LenoPrimaryDark
                                    )
                                    Text(
                                        text = q.questionText,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            // Voice Question Playback (if audio exists)
                            if (!q.audioUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                ClassVoiceNotePlayer(
                                    audioUrl = q.audioUrl,
                                    durationSec = q.audioDurationSeconds,
                                    accentColor = LenoPrimary,
                                    label = "Student Voice Question"
                                )
                            }

                            // Teacher's Answer (if answered)
                            if (q.isAnswered && (q.answerText.isNotBlank() || !q.answerAudioUrl.isNullOrBlank())) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF1F8E9),
                                    border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Verified,
                                                    contentDescription = null,
                                                    tint = Color(0xFF2E7D32),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Teacher's Verified Answer",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                            if (q.answeredAt > 0) {
                                                Text(
                                                    text = timeFormat.format(Date(q.answeredAt)),
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF558B2F)
                                                )
                                            }
                                        }

                                        if (q.answerText.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = q.answerText,
                                                fontSize = 12.sp,
                                                color = Color(0xFF1B5E20),
                                                lineHeight = 17.sp
                                            )
                                        }

                                        // Teacher Voice Answer Playback
                                        if (!q.answerAudioUrl.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            ClassVoiceNotePlayer(
                                                audioUrl = q.answerAudioUrl,
                                                durationSec = q.answerAudioDurationSeconds,
                                                accentColor = Color(0xFF2E7D32),
                                                label = "Teacher Voice Explanation"
                                            )
                                        }
                                    }
                                }
                            } else if (!q.isAnswered && !isInstructor) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Teacher has not replied to this question yet.",
                                    fontSize = 11.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Instructor Action: Answer this question
                            if (isInstructor) {
                                Spacer(modifier = Modifier.height(8.dp))
                                if (!isAnsweringThis) {
                                    Button(
                                        onClick = {
                                            answeringQuestionId = q.questionId
                                            answerInput = if (q.isAnswered) q.answerText else ""
                                            recordedTeacherAudioFile = null
                                            teacherAudioDurationSec = 0
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (q.isAnswered) MaterialTheme.colorScheme.surfaceVariant else LenoPrimary
                                        ),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text(
                                            text = if (q.isAnswered) "Edit Answer" else "Answer Question",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (q.isAnswered) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                                        )
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = answerInput,
                                            onValueChange = { answerInput = it },
                                            placeholder = { Text("Write your verified answer/explanation here...", fontSize = 12.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            maxLines = 4
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Voice Note recording for teacher answer
                                        if (isRecordingTeacherAnswer) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val mins = teacherRecordingDurationSec / 60
                                                    val secs = teacherRecordingDurationSec % 60
                                                    Text(
                                                        text = "🔴 Recording explanation: ${String.format(Locale.getDefault(), "%d:%02d", mins, secs)}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.error,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = {
                                                            audioRecorderHelper.stopRecording()
                                                            isRecordingTeacherAnswer = false
                                                            recordedTeacherAudioFile = null
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            val finalDur = if (teacherRecordingDurationSec < 1) 1 else teacherRecordingDurationSec
                                                            val res = audioRecorderHelper.stopRecording()
                                                            isRecordingTeacherAnswer = false
                                                            recordedTeacherAudioFile = res.getOrNull()
                                                            teacherAudioDurationSec = finalDur
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.Stop, contentDescription = "Done Recording", tint = LenoPrimaryDark, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                        } else if (recordedTeacherAudioFile != null) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFE8F5E9),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Voice note attached (${teacherAudioDurationSec}s)", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    IconButton(
                                                        onClick = {
                                                            recordedTeacherAudioFile = null
                                                            teacherAudioDurationSec = 0
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Mic button for teacher
                                            OutlinedButton(
                                                onClick = {
                                                    checkAndRequestMic {
                                                        audioRecorderHelper.startRecording()
                                                        isRecordingTeacherAnswer = true
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                enabled = !isRecordingTeacherAnswer
                                            ) {
                                                Icon(Icons.Default.Mic, contentDescription = null, tint = LenoPrimary, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    if (recordedTeacherAudioFile != null) "Re-record Voice" else "Record Voice",
                                                    fontSize = 11.sp,
                                                    color = LenoPrimary
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                TextButton(
                                                    onClick = {
                                                        answeringQuestionId = null
                                                        answerInput = ""
                                                        recordedTeacherAudioFile = null
                                                        teacherAudioDurationSec = 0
                                                    }
                                                ) {
                                                    Text("Cancel", fontSize = 11.sp)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Button(
                                                    onClick = {
                                                        if (answerInput.isNotBlank() || recordedTeacherAudioFile != null) {
                                                            coroutineScope.launch {
                                                                val audioUrl = recordedTeacherAudioFile?.let { file ->
                                                                    val storageService = FirebaseStorageService()
                                                                    val res = storageService.uploadVoiceNoteFile(file, classItem.classId)
                                                                    res.getOrDefault(AudioRecorderHelper.convertAudioFileToBase64DataUrl(file))
                                                                }
                                                                val finalAnsText = if (answerInput.isNotBlank()) answerInput.trim() else "🎤 Voice Explanation"
                                                                onAnswerQuestion(q.questionId, finalAnsText, audioUrl, teacherAudioDurationSec)
                                                                answeringQuestionId = null
                                                                answerInput = ""
                                                                recordedTeacherAudioFile = null
                                                                teacherAudioDurationSec = 0
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary)
                                                ) {
                                                    Text("Submit Answer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }

        // Bottom Action Area
        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isInstructor) {
                // STUDENT: CAN ASK QUESTIONS VIA TEXT OR VOICE NOTE
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (isRecordingStudentQuestion) {
                        // LIVE VOICE RECORDING UI FOR STUDENT
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val mins = studentRecordingDurationSec / 60
                                val secs = studentRecordingDurationSec % 60
                                val timeStr = String.format(Locale.getDefault(), "%d:%02d", mins, secs)

                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recording Voice Question: $timeStr",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                // Discard recording
                                IconButton(
                                    onClick = {
                                        audioRecorderHelper.stopRecording()
                                        isRecordingStudentQuestion = false
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Cancel Recording",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Send voice question
                                IconButton(
                                    onClick = {
                                        val finalDuration = if (studentRecordingDurationSec < 1) 1 else studentRecordingDurationSec
                                        val recResult = audioRecorderHelper.stopRecording()
                                        isRecordingStudentQuestion = false
                                        val audioFile = recResult.getOrNull()
                                        if (audioFile != null && audioFile.exists() && audioFile.length() > 44L) {
                                            isUploadingStudentAudio = true
                                            coroutineScope.launch {
                                                val storageService = FirebaseStorageService()
                                                val uploadRes = storageService.uploadVoiceNoteFile(audioFile, classItem.classId)
                                                val audioUrl = uploadRes.getOrDefault(AudioRecorderHelper.convertAudioFileToBase64DataUrl(audioFile))
                                                val durMins = finalDuration / 60
                                                val durSecs = finalDuration % 60
                                                val durationStr = String.format(Locale.getDefault(), "%d:%02d", durMins, durSecs)
                                                onAskQuestion("🎤 Voice Question ($durationStr)", audioUrl, finalDuration)
                                                isUploadingStudentAudio = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(LenoPrimary)
                                        .testTag("student_send_voice_question_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Voice Question",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else if (isUploadingStudentAudio) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = LenoPrimaryVeryLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = LenoPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Uploading voice question...", fontSize = 12.sp, color = LenoPrimaryDark, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        // Standard input bar with text field + mic button + send button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = questionInput,
                                onValueChange = { questionInput = it },
                                placeholder = { Text("Ask teacher a question...", fontSize = 12.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("student_ask_question_input"),
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LenoPrimary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Mic Button to record voice question
                            IconButton(
                                onClick = {
                                    checkAndRequestMic {
                                        audioRecorderHelper.startRecording()
                                        isRecordingStudentQuestion = true
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .testTag("student_record_voice_question_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Record Voice Question",
                                    tint = LenoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    if (questionInput.isNotBlank()) {
                                        onAskQuestion(questionInput.trim(), null, 0)
                                        questionInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(LenoPrimary)
                                    .testTag("student_ask_question_submit_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Ask Question",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🎙️ Voice notes allowed: Tap the mic icon to ask your question by voice, or type above.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // TEACHER: Quick action to add new notes / syllabus
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Teacher Management Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = LenoPrimaryDark
                        )
                        Text(
                            text = "Respond to questions above or post syllabus notes",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onAddLessonNote,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                        modifier = Modifier.testTag("teacher_add_note_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Lesson Note", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 1: LESSONS / SYLLABUS
// ----------------------------------------------------
@Composable
private fun ClassLessonsTab(
    classItem: ClassEntity,
    lessons: List<LessonEntity>,
    isInstructor: Boolean,
    onOpenLesson: (LessonEntity) -> Unit,
    onAddLesson: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        if (isInstructor) {
            Button(
                onClick = onAddLesson,
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_lesson_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add New Lesson to Class", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (lessons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = LenoPrimaryLight,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Lessons are being prepared by teacher.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(lessons) { lesson ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenLesson(lesson) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(LenoPrimaryVeryLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${lesson.orderIndex}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = LenoPrimaryDark
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = lesson.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (lesson.summary.isNotBlank()) lesson.summary else "Tap to read structured notes & exercises",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LenoPrimary
                            ) {
                                Text(
                                    text = "Read →",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 2: CLASS MEMBERS / ROSTER (SEE YOURSELF IN THE CLASS)
// ----------------------------------------------------
@Composable
private fun ClassMembersTab(
    classItem: ClassEntity,
    currentUser: UserEntity?,
    currentUserName: String,
    currentUserLinoId: String,
    enrolledStudents: List<UserEntity> = emptyList(),
    isInstructor: Boolean,
    onChatWithInstructor: () -> Unit
) {
    val otherEnrolled = enrolledStudents.filter { it.userId != currentUser?.userId && it.userId != classItem.instructorUserId }
    val displayedClassmates: List<Pair<String, String>> = if (otherEnrolled.isNotEmpty()) {
        otherEnrolled.map { student ->
            val name = student.displayName.ifBlank { student.username }
            val linoId = student.linoId.ifBlank { "LEN-${student.userId.replace("usr_", "").padEnd(8, '0').take(8).uppercase()}" }
            Pair(name, linoId)
        }
    } else {
        listOf(
            Pair("Fatima Dahiru", "LEN-84920153"),
            Pair("Abubakar Sani", "LEN-93041285"),
            Pair("Maryam Bello", "LEN-47103829"),
            Pair("Usman Aliyu", "LEN-62947103"),
            Pair("Zainab Kabir", "LEN-51829470")
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "CLASS INSTRUCTOR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = LenoPrimaryDark,
                letterSpacing = 1.sp
            )
        }

        // Instructor Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = LenoPrimaryVeryLight),
                border = BorderStroke(1.dp, LenoPrimaryLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(LenoPrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = LenoPrimaryDark,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = classItem.instructorName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = LenoPrimary
                                ) {
                                    Text(
                                        text = "Teacher ✓",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Leno ID: ${classItem.instructorLinoId}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onChatWithInstructor,
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Chat",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chat", fontSize = 11.sp)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(6.dp)) }

        item {
            Text(
                text = "ENROLLED STUDENTS IN THIS CLASS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }

        // YOU (Current User) Member Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                border = BorderStroke(1.5.dp, Color(0xFF81C784)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUserName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUserName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1B5E20)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2E7D32)
                                ) {
                                    Text(
                                        text = "YOU (Enrolled) ✓",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Leno ID: $currentUserLinoId",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Active Member",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Fellow Enrolled Classmates
        items(displayedClassmates) { classmate ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = classmate.first.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = classmate.first,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Student",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = "Leno ID: ${classmate.second}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 3: ABOUT / CLASS INFO
// ----------------------------------------------------
@Composable
private fun ClassAboutTab(classItem: ClassEntity) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Description",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = classItem.shortDescription,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = LenoPrimaryVeryLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Class Schedule & Details",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = LenoPrimaryDark
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Level: ${classItem.level}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• Format: Low-Data Chat + Image Lessons",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• Schedule: ${classItem.schedule.ifBlank { "Flexible self-paced + active group discussions" }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• Fee: ${if (classItem.price.isNotBlank() && classItem.price != "Free") classItem.price else "₦1,500"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
