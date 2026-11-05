package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ClassEntity
import com.example.data.entity.LessonEntity
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.example.util.LessonProgressManager

@Composable
fun ClassDetailDialog(
    classItem: ClassEntity,
    isEnrolled: Boolean,
    isInstructor: Boolean = false,
    isAdmin: Boolean = false,
    lessons: List<LessonEntity> = emptyList(),
    currentUserId: String = "",
    onDismiss: () -> Unit,
    onJoin: () -> Unit,
    onChatWithInstructor: () -> Unit,
    onEnterClassroom: (() -> Unit)? = null,
    onOpenLesson: (LessonEntity) -> Unit = {},
    onAddLesson: () -> Unit = {},
    onOpenCertificate: ((ClassEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val classCode = remember(classItem.classId) {
        LessonProgressManager.formatClassCode(classItem.classId)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LenoPrimaryVeryLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimaryLight)
                    ) {
                        Text(
                            text = classItem.subject,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LenoPrimaryDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Shareable Class Code Pill with copy on click
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3F4F6),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Class Code", classCode)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Class Code $classCode copied! 📋", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Code: $classCode",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF374151)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy code",
                                tint = LenoPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = classItem.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Low-data indicator
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = LenoPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Low Data • Chat + Image • ${classItem.level}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Instructor Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = LenoPrimaryVeryLight),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimaryLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "INSTRUCTOR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = LenoPrimaryDark,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = classItem.instructorName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "ID: ${classItem.instructorLinoId}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Badges: Teacher ✓ & Trusted ✓
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = "Trusted",
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "Trusted ✓",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Short Description
                Text(
                    text = "Description",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = classItem.shortDescription,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Schedule & Enrolled stats
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = LenoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = classItem.schedule.ifBlank { "Self-paced" },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = LenoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${classItem.enrolledStudentsCount} enrolled",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // LESSONS SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Class Lessons (${lessons.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isInstructor) {
                        TextButton(onClick = onAddLesson) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Lesson", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isEnrolled || isInstructor || isAdmin) {
                    if (lessons.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (classItem.lessonContent.isNotBlank()) classItem.lessonContent else "No extra lessons added yet. Instructor will publish lessons soon.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        // Learning progress tracker for enrolled students
                        if (isEnrolled && currentUserId.isNotBlank()) {
                            val completedCount = remember(lessons, currentUserId) {
                                LessonProgressManager.getCompletedCount(context, currentUserId, classItem.classId)
                            }
                            val progressFraction = if (lessons.isNotEmpty()) (completedCount.toFloat() / lessons.size.toFloat()).coerceIn(0f, 1f) else 0f
                            val progressPercent = (progressFraction * 100).toInt()

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF0FDF4),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Your Learning Progress",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D)
                                        )
                                        Text(
                                            text = "$completedCount of ${lessons.size} Completed ($progressPercent%)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF166534)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { progressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = Color(0xFF22C55E),
                                        trackColor = Color(0xFFDCFCE7)
                                    )

                                    val isCertAwarded = remember(classItem.classId, currentUserId) {
                                        LessonProgressManager.isCertificateAwarded(context, classItem.classId, currentUserId)
                                    }

                                    if (isCertAwarded && onOpenCertificate != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { onOpenCertificate.invoke(classItem) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("view_certificate_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WorkspacePremium,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "View Awarded Certificate 🎓",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    } else if (progressPercent == 100 && lessons.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFEF3C7),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.WorkspacePremium,
                                                    contentDescription = null,
                                                    tint = Color(0xFFD97706),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Lessons completed! Your teacher will review your work and award your official certificate.",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF92400E),
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            lessons.forEach { lesson ->
                                val isLessonDone = remember(lesson.lessonId, currentUserId) {
                                    if (currentUserId.isNotBlank()) {
                                        LessonProgressManager.isLessonCompleted(context, currentUserId, classItem.classId, lesson.lessonId)
                                    } else false
                                }

                                val quizScore = remember(lesson.lessonId, currentUserId) {
                                    if (currentUserId.isNotBlank()) {
                                        LessonProgressManager.getQuizScore(context, currentUserId, classItem.classId, lesson.lessonId)
                                    } else null
                                }

                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isLessonDone) Color(0xFF86EFAC) else LenoPrimaryLight),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenLesson(lesson) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isLessonDone) Color(0xFFDCFCE7) else LenoPrimaryVeryLight),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isLessonDone) Icons.Default.Check else Icons.Default.Book,
                                                    contentDescription = null,
                                                    tint = if (isLessonDone) Color(0xFF15803D) else LenoPrimaryDark,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Lesson ${lesson.orderIndex}: ${lesson.title}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (lesson.summary.isNotBlank()) {
                                                    Text(
                                                        text = lesson.summary,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (quizScore != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFFEF3C7),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                                                ) {
                                                    Text(
                                                        text = "Quiz $quizScore ⭐",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFB45309),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            if (isLessonDone) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFDCFCE7),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(11.dp))
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = "Done ✓",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF166534)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = "Read →",
                                                    fontSize = 11.sp,
                                                    color = LenoPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Not enrolled lock banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Join this class to access lesson notes, exercises, and discuss topics with your teacher.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onChatWithInstructor,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("dialog_chat_instructor_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Chat",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chat", fontSize = 12.sp)
                }

                if (isAdmin) {
                    Button(
                        onClick = { onEnterClassroom?.invoke() ?: onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dialog_admin_enter_class_btn")
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enter Classroom (Admin)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else if (isEnrolled) {
                    Button(
                        onClick = { onEnterClassroom?.invoke() ?: onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dialog_enrolled_enter_class_btn")
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enter Classroom 👥", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    val isPaid = classItem.isPaid && !classItem.price.equals("FREE", ignoreCase = true)
                    Button(
                        onClick = onJoin,
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dialog_join_class_btn")
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPaid) "Pay & Join (${classItem.price})" else "Join Free",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
