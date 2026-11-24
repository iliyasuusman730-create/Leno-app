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
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.data.service.FirebaseStorageService
import com.example.util.AudioPlayerHelper
import com.example.util.AudioRecorderHelper
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.example.util.LessonProgressManager
import com.example.util.CurriculumSectionHelper
import com.example.ui.theme.LocalLenoDarkTheme
import com.example.viewmodel.LenoViewModel
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState

@Composable
fun ClassRoomGroupDialog(
    classItem: ClassEntity,
    currentUser: UserEntity?,
    isInstructor: Boolean,
    isAdmin: Boolean = currentUser?.isAdmin == true,
    lessons: List<LessonEntity>,
    questions: List<ClassQuestionEntity> = emptyList(),
    enrolledStudents: List<UserEntity> = emptyList(),
    onAskQuestion: (text: String, audioUrl: String?, durationSec: Int, senderRole: String) -> Unit = { _, _, _, _ -> },
    onAnswerQuestion: (questionId: String, text: String, audioUrl: String?, durationSec: Int) -> Unit = { _, _, _, _ -> },
    onDismiss: () -> Unit,
    onChatWithInstructor: () -> Unit,
    onOpenLesson: (LessonEntity) -> Unit,
    onAddLesson: () -> Unit = {},
    onOpenCertificate: ((ClassEntity) -> Unit)? = null,
    viewModel: LenoViewModel? = null,
    onRemoveStudent: ((studentUserId: String, studentName: String) -> Unit)? = null,
    onAddStudent: ((studentUserId: String, studentLinoId: String, studentName: String) -> Unit)? = null,
    onEditClass: (() -> Unit)? = null,
    seenByMap: Map<String, Set<String>> = emptyMap(),
    onEditLesson: ((LessonEntity) -> Unit)? = null,
    onDeleteLesson: ((LessonEntity) -> Unit)? = null,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val isDark = LocalLenoDarkTheme.current
    var selectedTab by remember(classItem.classId) { mutableIntStateOf(initialTab) }
    var studentForCertificateAward by remember { mutableStateOf<Pair<String, String>?>(null) }
    var studentToRemove by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    val tabs = listOf("📚 Lessons (${lessons.size})", "❓ Q&A (${questions.size})", "👥 Members", "ℹ️ About")

    val currentUserName = currentUser?.displayName?.ifBlank { currentUser.username } ?: "You"
    val currentUserLinoId = currentUser?.linoId?.ifBlank {
        "LEN-${(currentUser.userId).replace("usr_", "").padEnd(8, '0').take(8).uppercase()}"
    } ?: "LEN-00000000"

    val isOwnerInstructor = classItem.instructorUserId == (currentUser?.userId ?: "")
    val isAnotherTeacher = (currentUser?.isTeacher == true || currentUser?.role.equals("TEACHER", true) || currentUser?.teacherStatus.equals("APPROVED", true)) && !isOwnerInstructor && !isAdmin

    if (isAnotherTeacher) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Unauthorized: Teachers cannot access another teacher's class.", Toast.LENGTH_LONG).show()
            onDismiss()
        }
        return
    }

    LaunchedEffect(classItem.classId, currentUser?.userId) {
        val uid = currentUser?.userId.orEmpty()
        if (uid.isNotBlank()) {
            LessonProgressManager.recordLastOpenedClass(context, uid, classItem.classId)
        }
    }

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
                .fillMaxSize()
                .imePadding()
                .testTag("classroom_fullscreen_root"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Slim Full-Screen Classroom Header Bar
                Surface(
                    color = if (isDark) Color(0xFF0A3B2C) else LenoPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("classroom_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Classes",
                                tint = Color.White
                            )
                        }

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

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = classItem.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val roleLabel = when {
                                isInstructor -> "Teacher"
                                isAdmin -> "Admin"
                                else -> "Enrolled"
                            }
                            Text(
                                text = "${classItem.subject} • ${classItem.enrolledStudentsCount + 1} members • $roleLabel",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if ((isOwnerInstructor || isAdmin) && onEditClass != null) {
                            IconButton(
                                onClick = onEditClass,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("classroom_header_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Class",
                                    tint = Color.White
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
                        0 -> ClassLessonsTab(
                            classItem = classItem,
                            lessons = lessons,
                            isInstructor = effectiveInstructor,
                            currentUserId = currentUser?.userId ?: "",
                            seenByMap = seenByMap,
                            onOpenLesson = onOpenLesson,
                            onAddLesson = onAddLesson,
                            onEditLesson = onEditLesson,
                            onDeleteLesson = onDeleteLesson,
                            onOpenCertificate = onOpenCertificate
                        )

                        1 -> ClassQuestionsTab(
                            classItem = classItem,
                            questions = questions,
                            isInstructor = effectiveInstructor,
                            currentUserId = currentUser?.userId ?: "",
                            onAskQuestion = onAskQuestion,
                            onAnswerQuestion = onAnswerQuestion,
                            onAddLessonNote = onAddLesson
                        )

                        2 -> ClassMembersTab(
                            classItem = classItem,
                            currentUser = currentUser,
                            currentUserName = currentUserName,
                            currentUserLinoId = currentUserLinoId,
                            enrolledStudents = enrolledStudents,
                            isInstructor = effectiveInstructor,
                            onChatWithInstructor = onChatWithInstructor,
                            onOpenCertificate = onOpenCertificate,
                            onAwardCertificate = { sId, sName ->
                                studentForCertificateAward = Pair(sId, sName)
                            },
                            onShowAddStudent = { showAddStudentDialog = true },
                            onPromptRemoveStudent = { sId, sName ->
                                studentToRemove = Pair(sId, sName)
                            }
                        )

                        3 -> ClassAboutTab(
                            classItem = classItem,
                            canEdit = isOwnerInstructor || isAdmin,
                            onEditClass = onEditClass
                        )
                    }
                }
            }
        }

        studentForCertificateAward?.let { (studentId, studentName) ->
            AwardStudentCertificateDialog(
                classItem = classItem,
                studentName = studentName,
                studentUserId = studentId,
                teacherName = classItem.instructorName,
                onDismiss = { studentForCertificateAward = null },
                onAward = { awardTitle, commendation ->
                    LessonProgressManager.awardCertificate(
                        context = context,
                        classId = classItem.classId,
                        studentUserId = studentId,
                        studentName = studentName,
                        teacherUserId = currentUser?.userId ?: classItem.instructorUserId,
                        teacherName = classItem.instructorName,
                        awardTitle = awardTitle,
                        teacherCommendation = commendation
                    )
                    Toast.makeText(context, "Certificate awarded to $studentName! 🎓", Toast.LENGTH_SHORT).show()
                }
            )
        }

        studentToRemove?.let { (studentId, studentName) ->
            AlertDialog(
                onDismissRequest = { studentToRemove = null },
                title = {
                    Text(
                        text = "Remove Student",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove $studentName from ${classItem.title}?\n\nThey will no longer have access to classroom lessons, Q&A, and discussions.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            studentToRemove = null
                            onRemoveStudent?.invoke(studentId, studentName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Remove Student", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { studentToRemove = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddStudentDialog && viewModel != null) {
            AddStudentToClassDialog(
                classItem = classItem,
                viewModel = viewModel,
                enrolledStudentUserIds = enrolledStudents.map { it.userId }.toSet(),
                onDismiss = { showAddStudentDialog = false },
                onAddStudent = { sId, sLinoId, sName ->
                    onAddStudent?.invoke(sId, sLinoId, sName)
                }
            )
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
    onAskQuestion: (text: String, audioUrl: String?, durationSec: Int, senderRole: String) -> Unit,
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

    // Teacher Voice Recording State (when answering existing questions)
    var isRecordingTeacherAnswer by remember { mutableStateOf(false) }
    var teacherRecordingDurationSec by remember { mutableIntStateOf(0) }
    var recordedTeacherAudioFile by remember { mutableStateOf<File?>(null) }
    var teacherAudioDurationSec by remember { mutableIntStateOf(0) }

    // Teacher Board Note & Voice Recording State (creating board announcement/message/voice note)
    var isRecordingTeacherBoardNote by remember { mutableStateOf(false) }
    var teacherBoardRecordingDurationSec by remember { mutableIntStateOf(0) }
    var recordedTeacherBoardAudioFile by remember { mutableStateOf<File?>(null) }
    var teacherBoardAudioDurationSec by remember { mutableIntStateOf(0) }
    var isUploadingTeacherBoardAudio by remember { mutableStateOf(false) }
    var teacherBoardMessageInput by remember { mutableStateOf("") }

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

    // Timer for teacher recording (answering)
    LaunchedEffect(isRecordingTeacherAnswer) {
        if (isRecordingTeacherAnswer) {
            teacherRecordingDurationSec = 0
            while (isRecordingTeacherAnswer) {
                delay(1000L)
                teacherRecordingDurationSec++
            }
        }
    }

    // Timer for teacher board voice note recording
    LaunchedEffect(isRecordingTeacherBoardNote) {
        if (isRecordingTeacherBoardNote) {
            teacherBoardRecordingDurationSec = 0
            while (isRecordingTeacherBoardNote) {
                delay(1000L)
                teacherBoardRecordingDurationSec++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (isRecordingStudentQuestion || isRecordingTeacherAnswer || isRecordingTeacherBoardNote) {
                    audioRecorderHelper.stopRecording()
                }
            } catch (_: Exception) {}
        }
    }

    val isDarkQna = LocalLenoDarkTheme.current
    Column(modifier = Modifier.fillMaxSize()) {
        // Notice Banner
        Surface(
            color = if (isDarkQna) {
                if (isInstructor) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color(0xFF103324)
            } else {
                if (isInstructor) LenoPrimaryVeryLight else Color(0xFFE8F5E9)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isDarkQna) Color(0xFF6EE7B7) else if (isInstructor) LenoPrimaryDark else Color(0xFF2E7D32),
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
                    color = if (isDarkQna) Color.White else com.example.ui.theme.LenoBlack,
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
                                            color = com.example.ui.theme.LenoBlack
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val isTeacherPost = q.senderRole.equals("TEACHER", ignoreCase = true)
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
                                                color = if (isTeacherPost) Color(0xFFE8F5E9) else Color(0xFFE0E0E0)
                                            ) {
                                                Text(
                                                    text = if (isTeacherPost) "Teacher 🎓" else "Student",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = com.example.ui.theme.LenoBlack,
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

                                val isTeacherPost = q.senderRole.equals("TEACHER", ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isTeacherPost) Color(0xFFE8F5E9) else if (q.isAnswered) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                ) {
                                    Text(
                                        text = if (isTeacherPost) "Board Note 📢" else if (q.isAnswered) "Answered ✓" else "Pending ⏳",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTeacherPost || q.isAnswered) com.example.ui.theme.LenoBlack else Color(0xFFE65100),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val isTeacherPost = q.senderRole.equals("TEACHER", ignoreCase = true)
                            // Question / Announcement Content
                            if (q.questionText.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    if (!isTeacherPost) {
                                        Text(
                                            text = "Q: ",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = q.questionText,
                                        fontSize = 13.sp,
                                        fontWeight = if (isTeacherPost) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            // Voice Question/Note Playback (if audio exists)
                            if (!q.audioUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                ClassVoiceNotePlayer(
                                    audioUrl = q.audioUrl,
                                    durationSec = q.audioDurationSeconds,
                                    accentColor = if (isTeacherPost) Color(0xFF2E7D32) else LenoPrimary,
                                    label = if (isTeacherPost) "Teacher Voice Note" else "Student Voice Question"
                                )
                            }

                            // Teacher's Answer (if answered)
                            if (q.isAnswered && (q.answerText.isNotBlank() || !q.answerAudioUrl.isNullOrBlank())) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDarkQna) Color(0xFF103324) else Color(0xFFF1F8E9),
                                    border = BorderStroke(1.dp, if (isDarkQna) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFA5D6A7)),
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
                                                    tint = if (isDarkQna) Color(0xFF6EE7B7) else Color(0xFF2E7D32),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Teacher's Verified Answer",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = if (isDarkQna) Color.White else com.example.ui.theme.LenoBlack
                                                )
                                            }
                                            if (q.answeredAt > 0) {
                                                Text(
                                                    text = timeFormat.format(Date(q.answeredAt)),
                                                    fontSize = 9.sp,
                                                    color = if (isDarkQna) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (q.answerText.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = q.answerText,
                                                fontSize = 12.sp,
                                                color = if (isDarkQna) Color.White else com.example.ui.theme.LenoBlack,
                                                lineHeight = 17.sp
                                            )
                                        }

                                        // Teacher Voice Answer Playback
                                        if (!q.answerAudioUrl.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            ClassVoiceNotePlayer(
                                                audioUrl = q.answerAudioUrl,
                                                durationSec = q.answerAudioDurationSeconds,
                                                accentColor = if (isDarkQna) Color(0xFF34D399) else Color(0xFF2E7D32),
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
                                                    Text("Voice note attached (${teacherAudioDurationSec}s)", fontSize = 11.sp, color = com.example.ui.theme.LenoBlack, fontWeight = FontWeight.SemiBold)
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
                                                    color = MaterialTheme.colorScheme.onSurface
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
                                                onAskQuestion("🎤 Voice Question ($durationStr)", audioUrl, finalDuration, "STUDENT")
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
                                Text("Uploading voice question...", fontSize = 12.sp, color = com.example.ui.theme.LenoBlack, fontWeight = FontWeight.SemiBold)
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
                                        onAskQuestion(questionInput.trim(), null, 0, "STUDENT")
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
                // TEACHER: Can post announcements, messages, or voice notes to the board + Add Lesson Note
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (isRecordingTeacherBoardNote) {
                        // LIVE VOICE RECORDING UI FOR TEACHER BOARD NOTE
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
                                val mins = teacherBoardRecordingDurationSec / 60
                                val secs = teacherBoardRecordingDurationSec % 60
                                val timeStr = String.format(Locale.getDefault(), "%d:%02d", mins, secs)

                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🔴 Recording Voice Note: $timeStr",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                // Discard recording
                                IconButton(
                                    onClick = {
                                        audioRecorderHelper.stopRecording()
                                        isRecordingTeacherBoardNote = false
                                        recordedTeacherBoardAudioFile = null
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

                                // Stop recording & save
                                IconButton(
                                    onClick = {
                                        val finalDuration = if (teacherBoardRecordingDurationSec < 1) 1 else teacherBoardRecordingDurationSec
                                        val recResult = audioRecorderHelper.stopRecording()
                                        isRecordingTeacherBoardNote = false
                                        val audioFile = recResult.getOrNull()
                                        if (audioFile != null && audioFile.exists() && audioFile.length() > 44L) {
                                            recordedTeacherBoardAudioFile = audioFile
                                            teacherBoardAudioDurationSec = finalDuration
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2E7D32))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Done Recording",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else if (isUploadingTeacherBoardAudio) {
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
                                Text("Posting voice note to board...", fontSize = 12.sp, color = com.example.ui.theme.LenoBlack, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        // Attached voice note chip
                        if (recordedTeacherBoardAudioFile != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE8F5E9),
                                border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Voice note attached (${teacherBoardAudioDurationSec}s)", fontSize = 11.sp, color = com.example.ui.theme.LenoBlack, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            recordedTeacherBoardAudioFile = null
                                            teacherBoardAudioDurationSec = 0
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Input bar: Type announcement / message + Mic button + Send button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = teacherBoardMessageInput,
                                onValueChange = { teacherBoardMessageInput = it },
                                placeholder = { Text("Post message or voice note to class board...", fontSize = 12.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("teacher_board_message_input"),
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LenoPrimary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Mic Button for Teacher Voice Note
                            IconButton(
                                onClick = {
                                    checkAndRequestMic {
                                        audioRecorderHelper.startRecording()
                                        isRecordingTeacherBoardNote = true
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F5E9))
                                    .testTag("teacher_record_board_voice_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Record Teacher Voice Note",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Send Button
                            IconButton(
                                onClick = {
                                    if (teacherBoardMessageInput.isNotBlank() || recordedTeacherBoardAudioFile != null) {
                                        isUploadingTeacherBoardAudio = true
                                        coroutineScope.launch {
                                            val audioUrl = recordedTeacherBoardAudioFile?.let { file ->
                                                val storageService = FirebaseStorageService()
                                                val uploadRes = storageService.uploadVoiceNoteFile(file, classItem.classId)
                                                uploadRes.getOrDefault(AudioRecorderHelper.convertAudioFileToBase64DataUrl(file))
                                            }
                                            val finalText = if (teacherBoardMessageInput.isNotBlank()) teacherBoardMessageInput.trim() else "🎤 Voice Note"
                                            onAskQuestion(finalText, audioUrl, teacherBoardAudioDurationSec, "TEACHER")
                                            teacherBoardMessageInput = ""
                                            recordedTeacherBoardAudioFile = null
                                            teacherBoardAudioDurationSec = 0
                                            isUploadingTeacherBoardAudio = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(LenoPrimary)
                                    .testTag("teacher_board_send_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Post to Board",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick Syllabus Note Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎙️ Teacher Mode: Post messages & voice notes to the board",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedButton(
                                onClick = onAddLessonNote,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("teacher_add_note_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Add Lesson Note", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 0: CURRICULUM SECTIONS & LESSONS
// ----------------------------------------------------
@Composable
private fun ClassLessonsTab(
    classItem: ClassEntity,
    lessons: List<LessonEntity>,
    isInstructor: Boolean,
    currentUserId: String = "",
    seenByMap: Map<String, Set<String>> = emptyMap(),
    onOpenLesson: (LessonEntity) -> Unit,
    onAddLesson: () -> Unit,
    onEditLesson: ((LessonEntity) -> Unit)? = null,
    onDeleteLesson: ((LessonEntity) -> Unit)? = null,
    onOpenCertificate: ((ClassEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = LocalLenoDarkTheme.current
    val updateTick by LessonProgressManager.updateTick.collectAsState()
    var lessonFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Unfinished, 2 = Quizzes

    val sortedLessons = remember(lessons) {
        lessons.sortedWith(compareBy<LessonEntity> { it.orderIndex }.thenBy { it.createdAt })
    }
    val seenLessonIds = remember(seenByMap, currentUserId, sortedLessons, updateTick) {
        sortedLessons.filter { lesson ->
            seenByMap[lesson.lessonId]?.contains(currentUserId) == true ||
                (currentUserId.isNotBlank() && LessonProgressManager.isLessonSeen(context, currentUserId, classItem.classId, lesson.lessonId))
        }.map { it.lessonId }.toSet()
    }
    val filteredLessons = remember(sortedLessons, lessonFilter, currentUserId, updateTick) {
        when (lessonFilter) {
            1 -> sortedLessons.filter { lesson ->
                currentUserId.isBlank() ||
                    !LessonProgressManager.isLessonCompleted(context, currentUserId, classItem.classId, lesson.lessonId)
            }
            2 -> sortedLessons.filter { lesson ->
                lesson.content.contains("[quiz:", ignoreCase = true)
            }
            else -> sortedLessons
        }
    }
    val curriculumSections = remember(filteredLessons) {
        CurriculumSectionHelper.groupLessonsIntoSections(filteredLessons)
    }

    val nextLesson = remember(sortedLessons, currentUserId, updateTick) {
        if (sortedLessons.isEmpty()) null
        else sortedLessons.firstOrNull { lesson ->
            currentUserId.isBlank() ||
                !LessonProgressManager.isLessonCompleted(context, currentUserId, classItem.classId, lesson.lessonId)
        } ?: sortedLessons.firstOrNull()
    }

    val doneBg = if (isDark) Color(0xFF103324) else Color(0xFFF0FDF4)
    val doneBorder = if (isDark) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF86EFAC)
    val doneText = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (isInstructor) {
            Button(
                onClick = onAddLesson,
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("add_lesson_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add New Lesson to Curriculum", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
        } else if (nextLesson != null && currentUserId.isNotBlank()) {
            // Up Next / Resume Card for students
            val hasSeenNext = seenLessonIds.contains(nextLesson.lessonId)
            val completedCount = remember(sortedLessons, currentUserId, updateTick) {
                sortedLessons.count {
                    LessonProgressManager.isLessonCompleted(context, currentUserId, classItem.classId, it.lessonId)
                }
            }
            val progressFraction = (completedCount.toFloat() / sortedLessons.size.toFloat()).coerceIn(0f, 1f)
            val isCertAwarded = remember(classItem.classId, currentUserId, updateTick) {
                LessonProgressManager.isCertificateAwarded(context, classItem.classId, currentUserId)
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = doneBg,
                border = BorderStroke(1.dp, doneBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clickable { onOpenLesson(nextLesson) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (completedCount >= sortedLessons.size) {
                                "ALL LESSONS COMPLETED"
                            } else if (hasSeenNext) {
                                "CONTINUE LESSON"
                            } else {
                                "UP NEXT"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = doneText,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "$completedCount/${sortedLessons.size} done",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = doneText
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nextLesson.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "~${CurriculumSectionHelper.estimateReadingMinutes(nextLesson)} min read",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { onOpenLesson(nextLesson) },
                            colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                            shape = RoundedCornerShape(50),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasSeenNext) "Resume" else "Start",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = LenoPrimary,
                        trackColor = if (isDark) Color(0xFF1B3B2B) else Color(0xFFDCFCE7)
                    )

                    if (isCertAwarded && onOpenCertificate != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onOpenCertificate(classItem) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("classroom_view_cert_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "View Teacher-Awarded Certificate",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Filter Pills Row when class has lessons
        if (sortedLessons.isNotEmpty()) {
            val filterItems = listOf(
                0 to "All (${sortedLessons.size})",
                1 to "Unfinished",
                2 to "Quizzes"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                filterItems.forEach { (idx, label) ->
                    val selected = lessonFilter == idx
                    Surface(
                        onClick = { lessonFilter = idx },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) {
                            if (isDark) MaterialTheme.colorScheme.primaryContainer else LenoPrimaryVeryLight
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) LenoPrimary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else LenoPrimaryDark
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        if (curriculumSections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (lessonFilter == 2) Icons.Default.Quiz else Icons.Default.Book,
                        contentDescription = null,
                        tint = LenoPrimaryLight,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = when {
                            lessons.isEmpty() -> "Lessons are being prepared by teacher."
                            lessonFilter == 1 -> "All caught up! Every lesson is finished."
                            lessonFilter == 2 -> "No quiz lessons in this class yet."
                            else -> "No lessons found."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val firstIncompleteSectionIdx = remember(curriculumSections, currentUserId, updateTick) {
                if (currentUserId.isBlank()) 0
                else curriculumSections.indexOfFirst { sec ->
                    sec.lessons.any { !LessonProgressManager.isLessonCompleted(context, currentUserId, classItem.classId, it.lessonId) }
                }.takeIf { it >= 0 } ?: 0
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("classroom_curriculum_list")
            ) {
                items(curriculumSections.size) { index ->
                    val section = curriculumSections[index]
                    CollapsibleCurriculumSectionCard(
                        section = section,
                        classId = classItem.classId,
                        currentUserId = currentUserId,
                        isLocked = false,
                        initiallyExpanded = index == firstIncompleteSectionIdx || curriculumSections.size <= 2 || lessonFilter != 0,
                        seenLessonIds = seenLessonIds,
                        seenByMap = seenByMap,
                        isInstructor = isInstructor,
                        onOpenLesson = onOpenLesson,
                        onEditLesson = onEditLesson,
                        onDeleteLesson = onDeleteLesson
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
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
    onChatWithInstructor: () -> Unit,
    onOpenCertificate: ((ClassEntity) -> Unit)? = null,
    onAwardCertificate: (studentId: String, studentName: String) -> Unit = { _, _ -> },
    onShowAddStudent: () -> Unit = {},
    onPromptRemoveStudent: (studentId: String, studentName: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val progressUpdateTick by LessonProgressManager.updateTick.collectAsState()
    val otherEnrolled = enrolledStudents.filter { it.userId != currentUser?.userId && it.userId != classItem.instructorUserId }
    val displayedClassmates: List<Triple<String, String, String>> = otherEnrolled.map { student ->
        val name = student.displayName.ifBlank { student.username }
        val linoId = student.linoId.ifBlank { "LEN-${student.userId.replace("usr_", "").padEnd(8, '0').take(8).uppercase()}" }
        Triple(student.userId, name, linoId)
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
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.sp
            )
        }

        // Instructor Card
        item {
            val isDarkMembers = LocalLenoDarkTheme.current
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkMembers) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else LenoPrimaryVeryLight
                ),
                border = BorderStroke(1.dp, if (isDarkMembers) LenoPrimary.copy(alpha = 0.4f) else LenoPrimaryLight),
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

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = classItem.instructorName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
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
                                        maxLines = 1,
                                        softWrap = false,
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

        // Enrolled Students Header with Add Student button for teachers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ENROLLED STUDENTS (${displayedClassmates.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                if (isInstructor) {
                    Button(
                        onClick = onShowAddStudent,
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add Student",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Add Student", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // YOU (Current User) Member Card
        item {
            val isDarkYou = LocalLenoDarkTheme.current
            val myCertAwarded = remember(classItem.classId, currentUser?.userId, progressUpdateTick) {
                if (currentUser?.userId.isNullOrBlank()) false
                else LessonProgressManager.isCertificateAwarded(context, classItem.classId, currentUser?.userId!!)
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkYou) Color(0xFF103324) else Color(0xFFE8F5E9)),
                border = BorderStroke(1.5.dp, if (isDarkYou) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF81C784)),
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

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUserName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isDarkYou) Color.White else com.example.ui.theme.LenoBlack,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
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
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Leno ID: $currentUserLinoId",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isDarkYou) Color.White.copy(alpha = 0.8f) else com.example.ui.theme.LenoBlack
                            )
                        }
                    }

                    if (myCertAwarded && onOpenCertificate != null) {
                        Button(
                            onClick = { onOpenCertificate(classItem) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Cert 🎓", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active Member",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (displayedClassmates.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isInstructor) "No students enrolled yet. Tap + Add Student or share the class code."
                        else "No other students enrolled yet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
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
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = classmate.second.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = classmate.second,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
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
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Leno ID: ${classmate.third}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isInstructor) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isCertAwarded = remember(classItem.classId, classmate.first, progressUpdateTick) {
                                LessonProgressManager.isCertificateAwarded(context, classItem.classId, classmate.first)
                            }
                            if (isCertAwarded) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                    modifier = Modifier.clickable { onOpenCertificate?.invoke(classItem) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Awarded 🎓", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onAwardCertificate(classmate.first, classmate.second) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = LenoPrimary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Award Cert", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            // Teacher Remove Student Button
                            IconButton(
                                onClick = { onPromptRemoveStudent(classmate.first, classmate.second) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(Color(0xFFFEE2E2), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remove student",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp)
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
// TAB 3: ABOUT / CLASS INFO & SCHOOL SHARING
// ----------------------------------------------------
@Composable
private fun ClassAboutTab(
    classItem: ClassEntity,
    canEdit: Boolean = false,
    onEditClass: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isDarkAbout = LocalLenoDarkTheme.current
    val classCode = remember(classItem.classId) {
        LessonProgressManager.formatClassCode(classItem.classId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // School & Private Class Share Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkAbout) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else LenoPrimaryVeryLight
            ),
            border = BorderStroke(1.dp, if (isDarkAbout) LenoPrimary.copy(alpha = 0.4f) else LenoPrimaryLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "School & Class Access Code",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkAbout) Color.White else com.example.ui.theme.LenoBlack
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (classItem.status.equals("PRIVATE", ignoreCase = true)) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else if (isDarkAbout) {
                            LenoPrimary
                        } else {
                            LenoPrimaryLight
                        }
                    ) {
                        Text(
                            text = if (classItem.status.equals("PRIVATE", ignoreCase = true)) "🔒 Private Class" else "🌐 Public Class",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (classItem.status.equals("PRIVATE", ignoreCase = true)) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else if (isDarkAbout) {
                                Color.White
                            } else {
                                LenoPrimaryDark
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Students can find this class easily by searching the Class Code or your Teacher ID in the Class Search Bar.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Class Code", classCode)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Class Code $classCode copied! 📋", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Class Code", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    classCode,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = LenoPrimary, modifier = Modifier.size(14.dp))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Teacher ID", classItem.instructorLinoId)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Teacher ID ${classItem.instructorLinoId} copied! 📋", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Teacher ID", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    classItem.instructorLinoId,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = LenoPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Class Schedule & Details",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
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
                    text = "• Access: ${if (classItem.isPaid) classItem.price else "Free"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (canEdit && onEditClass != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onEditClass,
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("classroom_edit_class_btn")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Class Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
