package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.entity.LessonEntity
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.ui.theme.LocalLenoDarkTheme
import com.example.util.CurriculumSection
import com.example.util.CurriculumSectionHelper
import com.example.util.LessonProgressManager

/**
 * Collapsible modern online-course Curriculum Module Card grouping related lessons
 * with per-section progress, estimated read time, quiz badges, and lock state.
 */
@Composable
fun CollapsibleCurriculumSectionCard(
    section: CurriculumSection,
    classId: String,
    currentUserId: String,
    isLocked: Boolean = false,
    initiallyExpanded: Boolean = true,
    seenLessonIds: Set<String> = emptySet(),
    seenByMap: Map<String, Set<String>> = emptyMap(),
    isInstructor: Boolean = false,
    onOpenLesson: (LessonEntity) -> Unit,
    onLockedClick: (() -> Unit)? = null,
    onEditLesson: ((LessonEntity) -> Unit)? = null,
    onDeleteLesson: ((LessonEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = LocalLenoDarkTheme.current
    val updateTick by LessonProgressManager.updateTick.collectAsState()
    var expanded by remember(section.sectionIndex, section.title) { mutableStateOf(initiallyExpanded) }

    val completedCount = remember(section.lessons, currentUserId, updateTick) {
        if (currentUserId.isBlank() || isLocked) 0
        else section.lessons.count {
            LessonProgressManager.isLessonCompleted(context, currentUserId, classId, it.lessonId)
        }
    }
    val allDone = section.lessons.isNotEmpty() && completedCount >= section.lessons.size && !isLocked
    val totalMinutes = remember(section.lessons) {
        section.lessons.sumOf { CurriculumSectionHelper.estimateReadingMinutes(it) }
    }
    val sectionProgress = if (section.lessons.isNotEmpty()) {
        (completedCount.toFloat() / section.lessons.size.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val doneContainerColor = if (isDark) Color(0xFF103324) else Color(0xFFDCFCE7)
    val doneTextColor = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)
    val doneBorderColor = if (isDark) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF86EFAC)

    val moduleBadgeBg = when {
        allDone -> doneContainerColor
        isDark -> MaterialTheme.colorScheme.primaryContainer
        else -> LenoPrimaryVeryLight
    }
    val moduleBadgeContent = when {
        allDone -> doneTextColor
        isDark -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> LenoPrimaryDark
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (allDone) doneBorderColor else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("curriculum_section_card_${section.sectionIndex}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Accordion Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 58.dp)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("curriculum_section_header_${section.sectionIndex}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = moduleBadgeBg,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (allDone) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Section Completed",
                                    tint = moduleBadgeContent,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    text = "M${section.sectionIndex}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = moduleBadgeContent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = section.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val lessonWord = if (section.lessons.size == 1) "lesson" else "lessons"
                            Text(
                                text = if (isLocked || currentUserId.isBlank()) {
                                    "${section.lessons.size} $lessonWord • ~$totalMinutes min"
                                } else {
                                    "$completedCount/${section.lessons.size} done • ~$totalMinutes min"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse section" else "Expand section",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (!isLocked && currentUserId.isNotBlank() && section.lessons.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { sectionProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = if (allDone) Color(0xFF22C55E) else LenoPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Expandable Lesson List
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    section.lessons.forEachIndexed { idx, lesson ->
                        if (idx > 0 || isLocked || currentUserId.isBlank()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                        }
                        CurriculumLessonRow(
                            lesson = lesson,
                            classId = classId,
                            currentUserId = currentUserId,
                            isLocked = isLocked,
                            isRemoteSeen = seenLessonIds.contains(lesson.lessonId),
                            seenCount = seenByMap[lesson.lessonId]?.count { it != currentUserId } ?: 0,
                            isInstructor = isInstructor,
                            updateTick = updateTick,
                            onClick = {
                                if (isLocked) {
                                    onLockedClick?.invoke()
                                } else {
                                    onOpenLesson(lesson)
                                }
                            },
                            onEdit = if (isInstructor && onEditLesson != null) {
                                { onEditLesson(lesson) }
                            } else null,
                            onDelete = if (isInstructor && onDeleteLesson != null) {
                                { onDeleteLesson(lesson) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurriculumLessonRow(
    lesson: LessonEntity,
    classId: String,
    currentUserId: String,
    isLocked: Boolean,
    isRemoteSeen: Boolean,
    seenCount: Int,
    isInstructor: Boolean,
    updateTick: Long,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = LocalLenoDarkTheme.current
    var menuExpanded by remember { mutableStateOf(false) }

    val isDone = remember(lesson.lessonId, currentUserId, updateTick) {
        if (!isLocked && currentUserId.isNotBlank()) {
            LessonProgressManager.isLessonCompleted(context, currentUserId, classId, lesson.lessonId)
        } else false
    }
    val isSeen = remember(lesson.lessonId, currentUserId, updateTick, isRemoteSeen, isDone) {
        if (isLocked || currentUserId.isBlank()) false
        else isDone || isRemoteSeen || LessonProgressManager.isLessonSeen(context, currentUserId, classId, lesson.lessonId)
    }
    val quizScore = remember(lesson.lessonId, currentUserId, updateTick) {
        if (!isLocked && currentUserId.isNotBlank()) {
            LessonProgressManager.getQuizScore(context, currentUserId, classId, lesson.lessonId)
        } else null
    }
    val hasQuiz = remember(lesson.lessonId, lesson.content) {
        lesson.content.contains("[quiz:", ignoreCase = true)
    }
    val cleanSummary = remember(lesson.summary) {
        lesson.summary.replace(Regex("""^\[[^\]]+]\s*"""), "").trim()
    }
    val estimatedMins = remember(lesson.lessonId, lesson.content) {
        CurriculumSectionHelper.estimateReadingMinutes(lesson)
    }

    val doneCircleBg = if (isDark) Color(0xFF103324) else Color(0xFFDCFCE7)
    val doneTint = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)
    val seenCircleBg = if (isDark) Color(0xFF1B2B25) else Color(0xFFECFDF5)
    val activeCircleBg = if (isDark) MaterialTheme.colorScheme.primaryContainer else LenoPrimaryVeryLight
    val activeTint = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else LenoPrimaryDark

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("curriculum_lesson_${lesson.lessonId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isLocked -> MaterialTheme.colorScheme.surfaceVariant
                            isDone -> doneCircleBg
                            isSeen -> seenCircleBg
                            else -> activeCircleBg
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLocked -> Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked Lesson",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    isDone -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed Lesson",
                        tint = doneTint,
                        modifier = Modifier.size(17.dp)
                    )
                    else -> Text(
                        text = "${lesson.orderIndex}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeTint
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (cleanSummary.isNotBlank() && !cleanSummary.equals(lesson.title, ignoreCase = true)) {
                    Text(
                        text = cleanSummary,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "~$estimatedMins min",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isInstructor && !isLocked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Seen by students",
                                tint = LenoPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$seenCount seen",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (quizScore != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color(0xFF422C08) else Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFFF59E0B).copy(alpha = 0.5f) else Color(0xFFF59E0B))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Quiz $quizScore",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFDE68A) else Color(0xFFB45309)
                                )
                            }
                        }
                    } else if (hasQuiz && !isLocked) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Quiz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Quiz",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                isLocked -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Enroll",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                isDone -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = doneCircleBg,
                        border = BorderStroke(1.dp, doneBorderColor(isDark))
                    ) {
                        Text(
                            text = "Done ✓",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = doneTint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                isSeen -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = seenCircleBg,
                        border = BorderStroke(1.dp, LenoPrimary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = activeTint,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Seen",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = LenoPrimary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "New",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            if (isInstructor && (onEdit != null || onDelete != null)) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lesson_more_${lesson.lessonId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Lesson Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (onEdit != null) {
                            DropdownMenuItem(
                                text = { Text("Edit Lesson", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = LenoPrimary)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete Lesson", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun doneBorderColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF22C55E)
