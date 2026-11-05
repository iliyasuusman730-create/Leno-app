package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ClassEntity
import com.example.data.entity.LessonEntity
import com.example.ui.components.lesson.LessonContentRenderer
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.util.LessonProgressManager

import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.runtime.mutableIntStateOf
import com.example.ui.components.lesson.LessonBlock
import com.example.ui.components.lesson.QuizBlockView
import com.example.ui.components.lesson.parseLessonContentToBlocks

@Composable
fun LessonViewerDialog(
    lesson: LessonEntity,
    parentClass: ClassEntity,
    currentUserId: String = "",
    onDismiss: () -> Unit,
    onAskTeacherInChat: (formulaQuote: String?) -> Unit
) {
    val context = LocalContext.current
    var isCompleted by remember {
        mutableStateOf(
            if (currentUserId.isNotBlank()) {
                LessonProgressManager.isLessonCompleted(context, currentUserId, parentClass.classId, lesson.lessonId)
            } else false
        )
    }

    var quizScore by remember {
        mutableStateOf(
            if (currentUserId.isNotBlank()) {
                LessonProgressManager.getQuizScore(context, currentUserId, parentClass.classId, lesson.lessonId)
            } else null
        )
    }

    val parsedBlocks = remember(lesson.content) { parseLessonContentToBlocks(lesson.content) }
    val hasEmbeddedQuiz = remember(parsedBlocks) { parsedBlocks.any { it is LessonBlock.QuizBlock } }

    val classCode = remember(parentClass.classId) {
        LessonProgressManager.formatClassCode(parentClass.classId)
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = LenoPrimaryVeryLight,
                            border = BorderStroke(1.dp, LenoPrimaryLight)
                        ) {
                            Text(
                                text = "Lesson ${lesson.orderIndex}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = LenoPrimaryDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        if (isCompleted) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFDCFCE7),
                                border = BorderStroke(1.dp, Color(0xFF22C55E))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Completed",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534)
                                    )
                                }
                            }
                        }

                        if (quizScore != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Quiz $quizScore ⭐",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = classCode,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = lesson.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${parentClass.subject} • ${parentClass.title}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Teacher attribution banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = LenoPrimaryVeryLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = LenoPrimaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Teacher: ${parentClass.instructorName}",
                                fontSize = 11.sp,
                                color = LenoPrimaryDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = parentClass.instructorLinoId,
                            fontSize = 10.sp,
                            color = LenoPrimaryDark.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lesson Content Card with Rich Blocks & Formula Ask-Teacher trigger
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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

                // If the lesson has no teacher-embedded quiz block, provide an interactive practice self-check
                if (!hasEmbeddedQuiz) {
                    Spacer(modifier = Modifier.height(14.dp))
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

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Student Completion Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCompleted) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (isCompleted) Color(0xFF86EFAC) else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isCompleted) Color(0xFF22C55E) else Color(0xFFD1D5DB)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.School,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isCompleted) "Lesson Completed! 🎉" else "Track Your Progress",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isCompleted) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isCompleted) "Great job! This counts toward your course badge." else "Mark this lesson complete once you understand it.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lesson_toggle_complete_btn")
                        ) {
                            Icon(
                                imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCompleted) "Completed ✓ (Tap to Undo)" else "✓ Mark Lesson as Complete",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAskTeacherInChat("Teacher, I have a question on Lesson ${lesson.orderIndex}: ${lesson.title}")
                },
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("lesson_ask_teacher_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ask Teacher in Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
