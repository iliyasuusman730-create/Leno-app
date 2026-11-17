package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.entity.ClassEntity
import com.example.data.entity.LessonEntity
import com.example.ui.theme.LenoActionGreen
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.ui.theme.LocalLenoDarkTheme
import com.example.util.CurriculumSectionHelper
import com.example.util.LessonProgressManager

@Composable
fun ClassDetailDialog(
    classItem: ClassEntity,
    isEnrolled: Boolean,
    isInstructor: Boolean = false,
    isAdmin: Boolean = false,
    isAnotherTeacher: Boolean = false,
    lessons: List<LessonEntity> = emptyList(),
    currentUserId: String = "",
    onDismiss: () -> Unit,
    onJoin: () -> Unit,
    onChatWithInstructor: () -> Unit,
    onEnterClassroom: (() -> Unit)? = null,
    onOpenLesson: (LessonEntity) -> Unit = {},
    onAddLesson: () -> Unit = {},
    onOpenCertificate: ((ClassEntity) -> Unit)? = null,
    onEditClass: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = LocalLenoDarkTheme.current
    val updateTick by LessonProgressManager.updateTick.collectAsState()

    val classCode = remember(classItem.classId) {
        LessonProgressManager.formatClassCode(classItem.classId)
    }

    if (isAnotherTeacher) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, "Access Denied: Teachers cannot access another teacher's class.", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
        return
    }

    val curriculumSections = remember(lessons) {
        CurriculumSectionHelper.groupLessonsIntoSections(lessons)
    }
    val hasAccess = isEnrolled || isInstructor || isAdmin

    val formattedPrice = remember(classItem.price, classItem.isPaid) {
        val raw = classItem.price.trim()
        if (raw.equals("FREE", ignoreCase = true) || raw.equals("Free", ignoreCase = true) || !classItem.isPaid) {
            "Free"
        } else if (raw.startsWith("N", ignoreCase = true) || raw.startsWith("₦")) {
            raw.replaceFirst(Regex("^[Nn]\\s*"), "₦")
        } else {
            "₦$raw"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(onBack = onDismiss)

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("course_detail_fullscreen"),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("course_detail_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "Course Overview",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("Class Code", classCode)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Class Code $classCode copied! 📋", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = classCode,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy class code",
                                        tint = LenoPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            if ((isInstructor || isAdmin) && onEditClass != null) {
                                IconButton(
                                    onClick = onEditClass,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag(
                                            if (isAdmin) "dialog_admin_edit_class_btn"
                                            else "dialog_instructor_edit_class_btn"
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Class",
                                        tint = LenoPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onChatWithInstructor,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("dialog_chat_instructor_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "Chat with Instructor",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chat", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            when {
                                isAdmin -> {
                                    Button(
                                        onClick = { onEnterClassroom?.invoke() ?: onDismiss() },
                                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("dialog_admin_enter_class_btn")
                                    ) {
                                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Enter Classroom (Admin)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                                isInstructor -> {
                                    Button(
                                        onClick = { onEnterClassroom?.invoke() ?: onDismiss() },
                                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("dialog_instructor_enter_class_btn")
                                    ) {
                                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Enter Classroom", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                                isEnrolled -> {
                                    Button(
                                        onClick = { onEnterClassroom?.invoke() ?: onDismiss() },
                                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("dialog_enrolled_enter_class_btn")
                                    ) {
                                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Enter Classroom", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                                else -> {
                                    val isPaid = classItem.isPaid && !classItem.price.equals("FREE", ignoreCase = true)
                                    Button(
                                        onClick = onJoin,
                                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("dialog_join_class_btn")
                                    ) {
                                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isPaid) "Pay & Join (${classItem.price})" else "Join Free",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hero Course Banner
                    val heroBrush = if (isDark) {
                        Brush.linearGradient(listOf(Color(0xFF0A3628), Color(0xFF0F4C38), Color(0xFF082E22)))
                    } else {
                        Brush.linearGradient(listOf(LenoPrimaryDark, LenoPrimary, LenoActionGreen))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(heroBrush)
                    ) {
                        if (classItem.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = classItem.imageUrl,
                                contentDescription = classItem.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(20.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.45f),
                                                Color.Black.copy(alpha = 0.78f)
                                            )
                                        )
                                    )
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = classItem.subject,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = classItem.level,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (formattedPrice == "Free") Color(0xFFDCFCE7) else Color.White
                                ) {
                                    Text(
                                        text = if (formattedPrice == "Free") "FREE" else formattedPrice,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (formattedPrice == "Free") Color(0xFF15803D) else LenoPrimaryDark,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = classItem.title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    lineHeight = 28.sp
                                ),
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Low-Data Course • Structured Notes + Q&A + Quizzes",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.88f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Course Metrics Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val totalLessonDisplay = maxOf(lessons.size, classItem.lessonCount)
                        CourseMetricTile(
                            icon = Icons.Default.MenuBook,
                            value = "$totalLessonDisplay",
                            label = if (totalLessonDisplay == 1) "Lesson" else "Lessons",
                            modifier = Modifier.weight(1f)
                        )
                        CourseMetricTile(
                            icon = Icons.Default.Group,
                            value = "${classItem.enrolledStudentsCount}",
                            label = "Students",
                            modifier = Modifier.weight(1f)
                        )
                        CourseMetricTile(
                            icon = Icons.Default.CalendarMonth,
                            value = classItem.schedule.ifBlank { "Self-paced" },
                            label = "Schedule",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Instructor Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else LenoPrimaryVeryLight
                        ),
                        border = BorderStroke(1.dp, if (isDark) LenoPrimary.copy(alpha = 0.4f) else LenoPrimaryLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(LenoPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = classItem.instructorName.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "INSTRUCTOR",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else LenoPrimaryDark,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = classItem.instructorName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "ID: ${classItem.instructorLinoId}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                if (classItem.instructorIsTeacher) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = LenoPrimary,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = "Teacher ✓",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (classItem.instructorIsTrusted) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1976D2),
                                        contentColor = Color.White
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = "Trusted",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Trusted",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Enrolled Student Progress Banner
                    if (isEnrolled && currentUserId.isNotBlank() && lessons.isNotEmpty()) {
                        val completedCount = remember(lessons, currentUserId, updateTick) {
                            LessonProgressManager.getCompletedCount(context, currentUserId, classItem.classId)
                        }
                        val progressFraction = (completedCount.toFloat() / lessons.size.toFloat()).coerceIn(0f, 1f)
                        val progressPercent = (progressFraction * 100).toInt()
                        val doneBg = if (isDark) Color(0xFF103324) else Color(0xFFF0FDF4)
                        val doneBorder = if (isDark) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF86EFAC)
                        val doneTitle = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = doneBg,
                            border = BorderStroke(1.dp, doneBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Your Learning Progress",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = doneTitle
                                    )
                                    Text(
                                        text = "$completedCount of ${lessons.size} ($progressPercent%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = doneTitle
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF22C55E),
                                    trackColor = if (isDark) Color(0xFF1B3B2B) else Color(0xFFDCFCE7)
                                )

                                val isCertAwarded = remember(classItem.classId, currentUserId, updateTick) {
                                    LessonProgressManager.isCertificateAwarded(context, classItem.classId, currentUserId)
                                }
                                if (isCertAwarded && onOpenCertificate != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { onOpenCertificate.invoke(classItem) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 44.dp)
                                            .testTag("view_certificate_btn")
                                    ) {
                                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("View Awarded Certificate 🎓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // Course Description
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "About This Course",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = classItem.shortDescription.ifBlank { "Structured low-data lessons with interactive quizzes and direct teacher support." },
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Curriculum Sections Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = LenoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Course Curriculum (${lessons.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (isInstructor || isAdmin) {
                            TextButton(onClick = onAddLesson) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Lesson", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (!hasAccess) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = LenoPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Enroll in this class to unlock all lesson notes, exercises, and classroom Q&A.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    if (curriculumSections.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (hasAccess && classItem.lessonContent.isNotBlank()) {
                                    classItem.lessonContent
                                } else {
                                    "Curriculum modules will appear here as the teacher publishes lessons."
                                },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            curriculumSections.forEachIndexed { idx, section ->
                                CollapsibleCurriculumSectionCard(
                                    section = section,
                                    classId = classItem.classId,
                                    currentUserId = currentUserId,
                                    isLocked = !hasAccess,
                                    initiallyExpanded = idx == 0,
                                    onOpenLesson = onOpenLesson,
                                    onLockedClick = {
                                        Toast.makeText(context, "Join this class to unlock full lessons.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CourseMetricTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LenoPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
