package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.theme.LenoMonoFont
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.ui.theme.LocalLenoDarkTheme
import com.example.util.CurriculumSectionHelper
import com.example.util.LessonProgressManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonViewerDialog(
    lesson: LessonEntity,
    parentClass: ClassEntity,
    currentUserId: String = "",
    allLessons: List<LessonEntity> = emptyList(),
    onSelectLesson: ((LessonEntity) -> Unit)? = null,
    onMarkLessonSeen: ((LessonEntity) -> Unit)? = null,
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

    var isBookmarked by remember(lesson.lessonId, currentUserId, updateTick) {
        mutableStateOf(
            if (currentUserId.isNotBlank()) {
                LessonProgressManager.isLessonBookmarked(context, currentUserId, lesson.lessonId)
            } else false
        )
    }

    var readerFontScale by remember { mutableFloatStateOf(LessonProgressManager.getReaderFontScale(context)) }
    var readerThemeMode by remember { mutableIntStateOf(LessonProgressManager.getReaderThemeMode(context)) }
    var showReaderSettingsSheet by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }

    var isChromeVisible by remember { mutableStateOf(true) }
    var lastScrollValue by remember(lesson.lessonId) { mutableIntStateOf(0) }

    // Track last opened class & lesson + mark lesson as Seen
    LaunchedEffect(lesson.lessonId, currentUserId) {
        if (currentUserId.isNotBlank()) {
            LessonProgressManager.markLessonSeen(context, currentUserId, parentClass.classId, lesson.lessonId)
            LessonProgressManager.recordLastOpenedClass(
                context = context,
                userId = currentUserId,
                classId = parentClass.classId,
                lessonId = lesson.lessonId
            )
            onMarkLessonSeen?.invoke(lesson)
        }
        isChromeVisible = true
        lastScrollValue = 0
        scrollState.scrollTo(0)
    }

    LaunchedEffect(scrollState.value) {
        val current = scrollState.value
        val delta = current - lastScrollValue
        if (current < 60 || (scrollState.maxValue > 0 && scrollState.maxValue - current < 100)) {
            isChromeVisible = true
        } else if (delta > 28 && current > 80) {
            isChromeVisible = false
        } else if (delta < -20) {
            isChromeVisible = true
        }
        lastScrollValue = current
    }

    val parsedBlocks = remember(lesson.lessonId, lesson.content) { parseLessonContentToBlocks(lesson.content) }
    val hasEmbeddedQuiz = remember(parsedBlocks) { parsedBlocks.any { it is LessonBlock.QuizBlock } }
    val estimatedMins = remember(lesson.lessonId, lesson.content) {
        CurriculumSectionHelper.estimateReadingMinutes(lesson)
    }
    val classCode = remember(parentClass.classId) {
        LessonProgressManager.formatClassCode(parentClass.classId)
    }
    val cleanSummary = remember(lesson.summary) {
        lesson.summary.trim().replace(Regex("""^\[[^\]]+]\s*"""), "")
    }

    // Reader palette driven by Comfort Mode (0=App Default, 1=Warm Paper, 2=Night Slate)
    val readerBgColor = when (readerThemeMode) {
        1 -> Color(0xFFF8F1E3)
        2 -> Color(0xFF0F172A)
        else -> MaterialTheme.colorScheme.background
    }
    val readerTextColor = when (readerThemeMode) {
        1 -> Color(0xFF2D241B)
        2 -> Color(0xFFE2E8F0)
        else -> MaterialTheme.colorScheme.onBackground
    }
    val readerMutedColor = when (readerThemeMode) {
        1 -> Color(0xFF6B5D4D)
        2 -> Color(0xFF94A3B8)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val doneBg = if (isDark || readerThemeMode == 2) Color(0xFF103324) else Color(0xFFF0FDF4)
    val doneBadgeBg = if (isDark || readerThemeMode == 2) Color(0xFF103324) else Color(0xFFDCFCE7)
    val doneBorder = if (isDark || readerThemeMode == 2) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF86EFAC)
    val doneText = if (isDark || readerThemeMode == 2) Color.White else Color(0xFF111111)
    val doneIconTint = if (isDark || readerThemeMode == 2) Color(0xFF6EE7B7) else Color(0xFF15803D)
    val quizBg = if (isDark || readerThemeMode == 2) Color(0xFF422C08) else Color(0xFFFEF3C7)
    val quizText = if (isDark || readerThemeMode == 2) Color(0xFFFDE68A) else Color(0xFFB45309)
    val quizIconTint = if (isDark || readerThemeMode == 2) Color(0xFFFBBF24) else Color(0xFFD97706)

    val scrollProgress = if (scrollState.maxValue > 0) {
        (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
    } else {
        ((currentIdx + 1).toFloat() / orderedLessons.size.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(readerBgColor)
                .testTag("lesson_reader_fullscreen")
        ) {
            // Distraction-free scrollable reading canvas (permanent top/bottom padding prevents scroll reflow jumps)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isChromeVisible = !isChromeVisible },
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .verticalScroll(scrollState)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 18.dp, end = 18.dp, top = 76.dp, bottom = 96.dp)
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
                                shape = RoundedCornerShape(50),
                                color = if (isDark) MaterialTheme.colorScheme.primaryContainer else LenoPrimaryVeryLight,
                                border = BorderStroke(1.dp, if (isDark) LenoPrimary.copy(alpha = 0.5f) else LenoPrimaryLight)
                            ) {
                                Text(
                                    text = "Lesson ${lesson.orderIndex}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF111111),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isCompleted) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = doneBadgeBg,
                                    border = BorderStroke(1.dp, doneBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = doneIconTint, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Completed",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = doneText
                                        )
                                    }
                                }
                            }

                            if (quizScore != null) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = quizBg,
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = quizIconTint, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Quiz $quizScore",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = quizText
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = classCode,
                                fontSize = 10.sp,
                                fontFamily = LenoMonoFont,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Large Editorial Lesson Title
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = (24f * readerFontScale).sp,
                            lineHeight = (31f * readerFontScale).sp
                        ),
                        color = readerTextColor
                    )

                    if (cleanSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (readerThemeMode == 1) Color(0xFFEFE4CE) else if (readerThemeMode == 2) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.5.dp)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(LenoPrimary)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "IN THIS LESSON",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = if (isDark || readerThemeMode == 2) Color.White else Color(0xFF111111)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = cleanSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 20.sp,
                                        color = readerMutedColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Teacher attribution strip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark || readerThemeMode == 2) Color(0xFF102A20) else LenoPrimaryVeryLight,
                        border = BorderStroke(1.dp, if (isDark || readerThemeMode == 2) LenoPrimary.copy(alpha = 0.35f) else LenoPrimaryLight),
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
                                    tint = if (isDark || readerThemeMode == 2) Color(0xFF6EE7B7) else LenoPrimaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${parentClass.subject} • By ${parentClass.instructorName}",
                                    fontSize = 12.sp,
                                    color = if (isDark || readerThemeMode == 2) Color.White else Color(0xFF111111),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = parentClass.instructorLinoId,
                                fontSize = 10.sp,
                                color = if (isDark || readerThemeMode == 2) Color(0xFFCBD5E1) else Color(0xFF334155),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = readerMutedColor.copy(alpha = 0.18f))
                    Spacer(modifier = Modifier.height(18.dp))

                    // Edge-to-Edge Editorial Rich Content Canvas (no double-boxed card)
                    LessonContentRenderer(
                        content = lesson.content,
                        isInteractive = true,
                        textScale = readerFontScale,
                        readerTextColor = readerTextColor,
                        readerMutedColor = readerMutedColor,
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

            // Always-visible slim reading progress indicator when focus mode hides top chrome
            AnimatedVisibility(
                visible = !isChromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                LinearProgressIndicator(
                    progress = { scrollProgress.coerceAtLeast(0.06f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(2.5.dp),
                    color = LenoPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Floating auto-hiding Top Reader Bar
            AnimatedVisibility(
                visible = isChromeVisible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
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
                                .padding(horizontal = 6.dp, vertical = 6.dp),
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

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (orderedLessons.size > 1 && onSelectLesson != null) {
                                            showOutlineSheet = true
                                        }
                                    }
                            ) {
                                Text(
                                    text = parentClass.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val positionLabel = if (orderedLessons.size > 1) {
                                    "Lesson ${currentIdx + 1} of ${orderedLessons.size} ▾"
                                } else {
                                    "Lesson ${lesson.orderIndex}"
                                }
                                Text(
                                    text = "$positionLabel • ~$estimatedMins min",
                                    fontSize = 11.sp,
                                    color = if (orderedLessons.size > 1 && onSelectLesson != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (orderedLessons.size > 1 && onSelectLesson != null) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }

                            IconButton(
                                onClick = { showReaderSettingsSheet = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("lesson_reader_settings_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = "Reader Text Size and Theme",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            if (currentUserId.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        isBookmarked = LessonProgressManager.toggleLessonBookmark(context, currentUserId, lesson.lessonId)
                                        Toast.makeText(
                                            context,
                                            if (isBookmarked) "Lesson bookmarked" else "Bookmark removed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("lesson_bookmark_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Bookmark Lesson",
                                        tint = if (isBookmarked) LenoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isChromeVisible = !isChromeVisible },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("lesson_focus_toggle_btn")
                            ) {
                                Icon(
                                    imageVector = if (isChromeVisible) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                                    contentDescription = "Toggle Focus Reading Mode",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    onAskTeacherInChat("Teacher, I have a question on Lesson ${lesson.orderIndex}: ${lesson.title}")
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, LenoPrimary.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 38.dp)
                                    .testTag("lesson_ask_teacher_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "Ask Teacher",
                                    tint = LenoPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ask",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { scrollProgress.coerceAtLeast(0.06f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = LenoPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // Floating auto-hiding Bottom Reader Navigation Dock
            AnimatedVisibility(
                visible = isChromeVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
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
                                shape = RoundedCornerShape(14.dp),
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
                                shape = RoundedCornerShape(14.dp),
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
                                shape = RoundedCornerShape(14.dp),
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

            // Reader Comfort Controls Bottom Sheet (Text Size + Paper Theme)
            if (showReaderSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showReaderSettingsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Reading Comfort",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Text Size",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0.9f to "Small",
                                1.0f to "Normal",
                                1.15f to "Large",
                                1.3f to "XL"
                            ).forEach { (scale, label) ->
                                val selected = kotlin.math.abs(readerFontScale - scale) < 0.05f
                                Surface(
                                    onClick = {
                                        readerFontScale = scale
                                        LessonProgressManager.setReaderFontScale(context, scale)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) LenoPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Paper Mode",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple(0, "App Theme", MaterialTheme.colorScheme.surfaceVariant),
                                Triple(1, "Warm Paper", Color(0xFFF8F1E3)),
                                Triple(2, "Night", Color(0xFF0F172A))
                            ).forEach { (mode, label, swatch) ->
                                val selected = readerThemeMode == mode
                                Surface(
                                    onClick = {
                                        readerThemeMode = mode
                                        LessonProgressManager.setReaderThemeMode(context, mode)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = swatch,
                                    border = BorderStroke(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) LenoPrimary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (mode) {
                                                1 -> Color(0xFF2D241B)
                                                2 -> Color(0xFFE2E8F0)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Course Outline Bottom Sheet for 1-Tap Lesson Jumping
            if (showOutlineSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showOutlineSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = LenoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Course Outline (${orderedLessons.size} Lessons)",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        orderedLessons.forEachIndexed { idx, item ->
                            val isCurrent = item.lessonId == lesson.lessonId
                            val itemCompleted = currentUserId.isNotBlank() &&
                                LessonProgressManager.isLessonCompleted(context, currentUserId, parentClass.classId, item.lessonId)
                            val itemSeen = currentUserId.isNotBlank() &&
                                LessonProgressManager.isLessonSeen(context, currentUserId, parentClass.classId, item.lessonId)
                            Surface(
                                onClick = {
                                    showOutlineSheet = false
                                    if (!isCurrent) {
                                        onSelectLesson?.invoke(item)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) LenoPrimary.copy(alpha = 0.12f) else Color.Transparent,
                                border = if (isCurrent) BorderStroke(1.dp, LenoPrimary.copy(alpha = 0.4f)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}.",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(26.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "~${CurriculumSectionHelper.estimateReadingMinutes(item)} min read",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    when {
                                        itemCompleted -> Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Completed",
                                            tint = Color(0xFF22C55E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        isCurrent -> Text("Reading", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        itemSeen -> Text("Seen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}
