package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.ClassEntity
import com.example.data.entity.LessonEntity
import com.example.ui.components.lesson.LessonBlock
import com.example.ui.components.lesson.LessonContentRenderer
import com.example.ui.components.lesson.QuizBlockView
import com.example.ui.components.lesson.parseLessonContentToBlocks
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.ui.theme.LocalLenoDarkTheme
import com.example.util.CurriculumSectionHelper
import com.example.util.LessonProgressManager

@Composable
fun LessonViewerDialog(
    lesson: LessonEntity,
    parentClass: ClassEntity,
    currentUserId: String = "",
    allLessons: List<LessonEntity> = emptyList(),
    onSelectLesson: ((LessonEntity) -> Unit)? = null,
    onDismiss: () -> Unit,
    onAskTeacherInChat: (formulaQuote: String?) -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalLenoDarkTheme.current
    val updateTick by LessonProgressManager.updateTick.collectAsState()
    val scrollState = rememberScrollState()

    val orderedLessons = remember(allLessons, lesson) {
        if (allLessons.isEmpty()) listOf(lesson)
        else allLessons.sortedWith(compareBy<LessonEntity> { it.orderIndex }.thenBy { it.createdAt })
    }
    val currentIdx = remember(orderedLessons, lesson.lessonId) {
        orderedLessons.indexOfFirst { it.lessonId == lesson.lessonId }.takeIf { it >= 0 } ?: 0
    }
    val prevLesson = remember(orderedLessons, currentIdx) {
        if (currentIdx > 0) orderedLessons[currentIdx - 1] else null
    }
    val nextLesson = remember(orderedLessons, currentIdx) {
        if (currentIdx in 0 until orderedLessons.lastIndex) orderedLessons[currentIdx + 1] else null
    }

    var isCompleted by remember(lesson.lessonId, currentUserId, updateTick) {
        mutableStateOf(
            if (currentUserId.isNotBlank()) {
                LessonProgressManager.isLessonCompleted(context, currentUserId, parentClass.classId, lesson.lessonId)
            } else false
        )
    }

    var quizScore by remember(lesson.lessonId, currentUserId, updateTick) {
        mutableStateOf(
            if (currentUserId.isNotBlank()) {
                LessonProgressManager.getQuizScore(context, currentUserId, parentClass.classId, lesson.lessonId)
            } else null
        )
    }

    // Track last opened class & lesson for the "Continue Learning" hero card
    LaunchedEffect(lesson.lessonId, currentUserId) {
        if (currentUserId.isNotBlank()) {
            LessonProgressManager.recordLastOpenedClass(
                context = context,
                userId = currentUserId,
                classId = parentClass.classId,
                lessonId = lesson.lessonId
            )
        }
        scrollState.scrollTo(0)
    }

    val parsedBlocks = remember(lesson.lessonId, lesson.content) { parseLessonContentToBlocks(lesson.content) }
    val hasEmbeddedQuiz = remember(parsedBlocks) { parsedBlocks.any { it is LessonBlock.QuizBlock } }
    val estimatedMins = remember(lesson.lessonId, lesson.content) {
        CurriculumSectionHelper.estimateReadingMinutes(lesson)
    }
    val classCode = remember(parentClass.classId) {
        LessonProgressManager.formatClassCode(parentClass.classId)
    }

    val doneBg = if (isDark) Color(0xFF103324) else Color(0xFFF0FDF4)
    val doneBadgeBg = if (isDark) Color(0xFF103324) else Color(0xFFDCFCE7)
    val doneBorder = if (isDark) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF86EFAC)
    val doneText = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)
    val quizBg = if (isDark) Color(0xFF422C08) else Color(0xFFFEF3C7)
    val quizText = if (isDark) Color(0xFFFDE68A) else Color(0xFFB45309)
    val quizIconTint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)

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
                .testTag("lesson_reader_fullscreen"),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("lesson_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Classroom",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = parentClass.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val positionLabel = if (orderedLessons.size > 1) {
                                        "Lesson ${currentIdx + 1} of ${orderedLessons.size}"
                                    } else {
                                        "Lesson ${lesson.orderIndex}"
                                    }
                                    Text(
                                        text = "$positionLabel • ~$estimatedMins min read",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            OutlinedButton(
                                onClick = {
                                    onAskTeacherInChat("Teacher, I have a question on Lesson ${lesson.orderIndex}: ${lesson.title}")
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, LenoPrimary.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .testTag("lesson_ask_teacher_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "Ask Teacher",
                                    tint = LenoPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Ask",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LenoPrimary
                                )
                            }
                        }

                        // Course position progress bar
                        val courseStepProgress = ((currentIdx + 1).toFloat() / orderedLessons.size.coerceAtLeast(1).toFloat())
                            .coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { courseStepProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = LenoPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
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
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (prevLesson != null) {
                                        onSelectLesson?.invoke(prevLesson)
                                    }
                                },
                                enabled = prevLesson != null && onSelectLesson != null,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("lesson_prev_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Lesson",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prev", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (currentUserId.isNotBlank()) {
                                        val newStatus = !isCompleted
                                        isCompleted = newStatus
                                        LessonProgressManager.setLessonCompleted(
                                            context = context,
                                            userId = currentUserId,
                                            classId = parentClass.classId,
                                            lessonId = lesson.lessonId,
                                            completed = newStatus
                                        )
                                        val toastMsg = if (newStatus) "Lesson ${lesson.orderIndex} marked complete! 🎉" else "Lesson progress updated"
                                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isCompleted) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isCompleted) doneBadgeBg else Color.Transparent,
                                    contentColor = if (isCompleted) doneText else MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("lesson_bottom_complete_btn")
                            ) {
                                Icon(
                                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCompleted) "Done ✓" else "Complete",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    if (nextLesson != null && onSelectLesson != null) {
                                        if (currentUserId.isNotBlank() && !isCompleted) {
                                            isCompleted = true
                                            LessonProgressManager.setLessonCompleted(
                                                context = context,
                                                userId = currentUserId,
                                                classId = parentClass.classId,
                                                lessonId = lesson.lessonId,
                                                completed = true
                                            )
                                        }
                                        onSelectLesson(nextLesson)
                                    } else {
                                        if (currentUserId.isNotBlank() && !isCompleted) {
                                            LessonProgressManager.setLessonCompleted(
                                                context = context,
                                                userId = currentUserId,
                                                classId = parentClass.classId,
                                                lessonId = lesson.lessonId,
                                                completed = true
                                            )
                                            Toast.makeText(context, "Course lesson completed! 🎉", Toast.LENGTH_SHORT).show()
                                        }
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("lesson_next_btn")
                            ) {
                                Text(
                                    text = if (nextLesson != null && onSelectLesson != null) "Next Lesson" else "Finish ✓",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (nextLesson != null && onSelectLesson != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
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
                        .widthIn(max = 720.dp)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Status & Metadata Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) MaterialTheme.colorScheme.primaryContainer else LenoPrimaryVeryLight,
                                border = BorderStroke(1.dp, if (isDark) LenoPrimary.copy(alpha = 0.5f) else LenoPrimaryLight)
                            ) {
                                Text(
                                    text = "Lesson ${lesson.orderIndex}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else LenoPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$estimatedMins min",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isCompleted) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = doneBadgeBg,
                                    border = BorderStroke(1.dp, doneBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = doneText, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Completed",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = doneText
                                        )
                                    }
                                }
                            }

                            if (quizScore != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = quizBg,
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = quizIconTint, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Quiz $quizScore",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = quizText
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = classCode,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Large Editorial Lesson Title
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            lineHeight = 28.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (lesson.summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lesson.summary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Teacher attribution strip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else LenoPrimaryVeryLight,
                        border = BorderStroke(1.dp, if (isDark) LenoPrimary.copy(alpha = 0.35f) else LenoPrimaryLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = null,
                                    tint = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else LenoPrimaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${parentClass.subject} • By ${parentClass.instructorName}",
                                    fontSize = 12.sp,
                                    color = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else LenoPrimaryDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = parentClass.instructorLinoId,
                                fontSize = 10.sp,
                                color = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else LenoPrimaryDark.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Lesson Content Card with Rich Blocks & Formula Ask-Teacher trigger
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            LessonContentRenderer(
                                content = lesson.content,
                                isInteractive = true,
                                onAskAboutFormula = { formula ->
                                    onAskTeacherInChat("Teacher, I have a question on this formula from Lesson ${lesson.orderIndex}: $formula")
                                },
                                onQuizAnswered = { _, isCorrect ->
                                    if (isCorrect) {
                                        quizScore = "1/1"
                                        if (currentUserId.isNotBlank()) {
                                            LessonProgressManager.saveQuizScore(context, currentUserId, parentClass.classId, lesson.lessonId, 1, 1)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Fallback practice self-check quiz when teacher did not embed one
                    if (!hasEmbeddedQuiz) {
                        Spacer(modifier = Modifier.height(16.dp))
                        val defaultQuestion = remember(parentClass.subject, lesson.title) {
                            when {
                                parentClass.subject.contains("Math", ignoreCase = true) -> {
                                    Triple(
                                        "Maths Drill: When evaluating algebraic expressions and equations, which order of operations applies?",
                                        listOf("BODMAS / PEMDAS (Brackets & Powers first)", "Evaluate random terms first", "Addition always precedes parentheses", "Left-to-right ignoring signs"),
                                        0
                                    )
                                }
                                parentClass.subject.contains("Physic", ignoreCase = true) -> {
                                    Triple(
                                        "Physics Concept Check: According to Newton's Second Law, how is net force related to mass and acceleration?",
                                        listOf("F = m × a (Net force equals mass times acceleration)", "Force is independent of acceleration", "Force decreases as mass increases for constant acceleration", "Acceleration is zero whenever force is applied"),
                                        0
                                    )
                                }
                                parentClass.subject.contains("Chem", ignoreCase = true) -> {
                                    Triple(
                                        "Chemistry Check: In balanced chemical equations, which fundamental law requires atom count to be conserved on both sides?",
                                        listOf("Law of Conservation of Mass", "Law of Multiple Proportions", "Avogadro's hypothesis only", "Graham's Law of Diffusion"),
                                        0
                                    )
                                }
                                parentClass.subject.contains("English", ignoreCase = true) || parentClass.subject.contains("Grammar", ignoreCase = true) || parentClass.subject.contains("Literature", ignoreCase = true) -> {
                                    Triple(
                                        "English Drill: In sentence structure and syntax, which grammatical rule ensures proper agreement between subject and verb?",
                                        listOf("Subject-Verb Concord (singular subject takes singular verb)", "Random capitalization of nouns", "Hyperbolic figurative language", "Inverting tense midway through a paragraph"),
                                        0
                                    )
                                }
                                parentClass.subject.contains("Bio", ignoreCase = true) -> {
                                    Triple(
                                        "Biology Check: In cellular biology, which organelle is known as the powerhouse of the cell for generating ATP?",
                                        listOf("Mitochondria", "Ribosome", "Endoplasmic Reticulum", "Centrosome"),
                                        0
                                    )
                                }
                                parentClass.subject.contains("Quran", ignoreCase = true) || parentClass.subject.contains("Islam", ignoreCase = true) -> {
                                    Triple(
                                        "Comprehension Check: What is the primary objective of studying the rules and verses covered in this lesson?",
                                        listOf("Reciting with precise Tajweed pronunciation and deep reflection", "Reciting as fast as possible without pauses", "Skipping elongation and stop signs", "Memorizing without understanding"),
                                        0
                                    )
                                }
                                parentClass.subject.contains("JAMB", ignoreCase = true) -> {
                                    Triple(
                                        "JAMB Exam Drill: In examination preparation for this topic, what approach gives the highest score?",
                                        listOf("Active recall, solving past questions, and unit verification", "Guessing without eliminating wrong options", "Spending 25 minutes on a single question", "Skipping all practice drills"),
                                        0
                                    )
                                }
                                parentClass.subject.contains("Computer", ignoreCase = true) || parentClass.subject.contains("Online", ignoreCase = true) || parentClass.subject.contains("Skill", ignoreCase = true) -> {
                                    Triple(
                                        "Practical Check: In technology and digital skills, what ensures project reliability and success?",
                                        listOf("Clean structure, descriptive naming, and iterative testing", "Shipping code without testing", "Hardcoding temporary values permanently", "Ignoring system errors"),
                                        0
                                    )
                                }
                                else -> {
                                    Triple(
                                        "Self-Check: Based on Lesson ${lesson.orderIndex} (${lesson.title}), what is most critical for mastery?",
                                        listOf("Understanding core concepts and actively applying the steps", "Memorizing text without understanding principles", "Ignoring diagrams and notes", "Never reviewing past lessons"),
                                        0
                                    )
                                }
                            }
                        }

                        QuizBlockView(
                            question = defaultQuestion.first,
                            options = defaultQuestion.second,
                            correctIndex = defaultQuestion.third,
                            isInteractive = true,
                            onAnswerChecked = { isCorrect ->
                                if (isCorrect) {
                                    quizScore = "1/1"
                                    if (currentUserId.isNotBlank()) {
                                        LessonProgressManager.saveQuizScore(context, currentUserId, parentClass.classId, lesson.lessonId, 1, 1)
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Interactive Student Completion Box
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isCompleted) doneBg else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (isCompleted) doneBorder else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isCompleted) Color(0xFF22C55E) else MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.School,
                                        contentDescription = null,
                                        tint = if (isCompleted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isCompleted) "Lesson Completed! 🎉" else "Track Your Progress",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isCompleted) doneText else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isCompleted) "Great job! This counts toward your course completion." else "Mark this lesson complete once you've mastered it.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (currentUserId.isNotBlank()) {
                                        val newStatus = !isCompleted
                                        isCompleted = newStatus
                                        LessonProgressManager.setLessonCompleted(
                                            context = context,
                                            userId = currentUserId,
                                            classId = parentClass.classId,
                                            lessonId = lesson.lessonId,
                                            completed = newStatus
                                        )
                                        val toastMsg = if (newStatus) "Lesson ${lesson.orderIndex} marked complete! 🎉" else "Lesson progress updated"
                                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCompleted) Color(0xFF16A34A) else LenoPrimary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("lesson_toggle_complete_btn")
                            ) {
                                Icon(
                                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isCompleted) "Completed ✓ (Tap to Undo)" else "Mark Lesson as Complete",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
