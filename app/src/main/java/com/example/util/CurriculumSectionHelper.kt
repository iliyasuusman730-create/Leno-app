package com.example.util

import com.example.data.entity.ClassEntity
import com.example.data.entity.LessonEntity
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Structured curriculum module/section grouping lessons for modern course presentation.
 */
data class CurriculumSection(
    val sectionIndex: Int,
    val title: String,
    val lessons: List<LessonEntity>
)

/**
 * Target summary for the "Continue Learning" hero card on the Classes / My Learning view.
 */
data class ContinueLearningTarget(
    val classItem: ClassEntity,
    val nextLesson: LessonEntity?,
    val completedCount: Int,
    val totalLessons: Int,
    val progressPercent: Int
)

object CurriculumSectionHelper {

    private val explicitPrefixRegex = Regex(
        pattern = """^(?:Module|Section|Unit|Week|Chapter)\s*(\d+)\s*[:\-•]\s*(.+)$""",
        option = RegexOption.IGNORE_CASE
    )

    private val bracketPrefixRegex = Regex(
        pattern = """^\[([^\]]+)]\s*(.+)$"""
    )

    private val defaultSectionNames = listOf(
        "Core Foundations",
        "Guided Practice",
        "Applied Mastery",
        "Deep Dive & Review",
        "Exam & Project Prep"
    )

    /**
     * Groups a flat list of lessons into ordered Curriculum Sections / Modules.
     *
     * Supports explicit teacher-authored module prefixes (e.g. "Module 1: Algebra Basics",
     * "Week 2 - Kinematics", or "[Grammar] Verb Tenses"), and otherwise cleanly organizes
     * lessons into progressive 3-lesson modules so every class looks like a modern online course.
     */
    fun groupLessonsIntoSections(lessons: List<LessonEntity>): List<CurriculumSection> {
        if (lessons.isEmpty()) return emptyList()

        val sorted = lessons.sortedWith(compareBy<LessonEntity> { it.orderIndex }.thenBy { it.createdAt })

        // Check if teacher used explicit Module / Week / Section / [Bracket] naming
        val hasExplicitMarkers = sorted.any { lesson ->
            explicitPrefixRegex.containsMatchIn(lesson.title.trim()) ||
                bracketPrefixRegex.containsMatchIn(lesson.summary.trim())
        }

        if (hasExplicitMarkers) {
            val grouped = linkedMapOf<String, MutableList<LessonEntity>>()
            val displayLabels = linkedMapOf<String, String>()
            for (lesson in sorted) {
                val titleMatch = explicitPrefixRegex.find(lesson.title.trim())
                val summaryBracketMatch = bracketPrefixRegex.find(lesson.summary.trim())
                val (groupKey, sectionLabel) = when {
                    titleMatch != null -> {
                        val num = titleMatch.groupValues[1]
                        val rawAfterColon = titleMatch.groupValues[2].trim()
                        val sectionTopic = rawAfterColon
                            .substringBefore("—")
                            .substringBefore(" - ")
                            .trim()
                            .take(28)
                        val fallbackTopic = defaultSectionNames.getOrElse((num.toIntOrNull() ?: 1) - 1) { "Topics" }
                        val labelTopic = sectionTopic.ifBlank { fallbackTopic }
                        "MODULE_$num" to "Module $num • $labelTopic"
                    }
                    summaryBracketMatch != null -> {
                        val label = summaryBracketMatch.groupValues[1].trim()
                        "BRACKET_${label.lowercase()}" to label
                    }
                    else -> {
                        val bucket = ((lesson.orderIndex - 1).coerceAtLeast(0) / 3) + 1
                        val name = defaultSectionNames.getOrElse(bucket - 1) { "Module $bucket" }
                        "AUTO_$bucket" to "Module $bucket • $name"
                    }
                }
                displayLabels.putIfAbsent(groupKey, sectionLabel)
                grouped.getOrPut(groupKey) { mutableListOf() }.add(lesson)
            }
            return grouped.entries.mapIndexed { idx, entry ->
                CurriculumSection(
                    sectionIndex = idx + 1,
                    title = displayLabels[entry.key] ?: "Module ${idx + 1}",
                    lessons = entry.value
                )
            }
        }

        // Auto-group cleanly into 3 lessons per module
        val chunkSize = 3
        return sorted.chunked(chunkSize).mapIndexed { idx, chunk ->
            val sectionNum = idx + 1
            val defaultName = defaultSectionNames.getOrElse(idx) { "Extended Topics $sectionNum" }
            CurriculumSection(
                sectionIndex = sectionNum,
                title = "Module $sectionNum • $defaultName",
                lessons = chunk
            )
        }
    }

    /**
     * Estimates reading & practice duration in minutes for a lesson.
     */
    fun estimateReadingMinutes(lesson: LessonEntity): Int {
        val wordCount = lesson.content
            .trim()
            .split(Regex("""\s+"""))
            .count { it.isNotBlank() }
        val blockBonus = listOf("[quiz:", "[video:", "[voice:", "[formula:", "```")
            .count { marker -> lesson.content.contains(marker, ignoreCase = true) }
        val minutes = (wordCount / 130.0).roundToInt() + max(1, blockBonus)
        return minutes.coerceIn(2, 45)
    }

    /**
     * Selects the best enrolled class & next uncompleted lesson for the "Continue Learning" card.
     */
    fun selectContinueLearningTarget(
        enrolledClasses: List<ClassEntity>,
        lastOpenedClassId: String?,
        lessonsByClassId: Map<String, List<LessonEntity>>,
        completedIdsByClassId: Map<String, Set<String>>,
        lastOpenedLessonByClassId: Map<String, String?> = emptyMap()
    ): ContinueLearningTarget? {
        if (enrolledClasses.isEmpty()) return null

        fun buildTarget(cls: ClassEntity): ContinueLearningTarget {
            val classLessons = (lessonsByClassId[cls.classId] ?: emptyList())
                .sortedWith(compareBy<LessonEntity> { it.orderIndex }.thenBy { it.createdAt })
            val completedIds = completedIdsByClassId[cls.classId] ?: emptySet()
            val totalLessons = maxOf(classLessons.size, cls.lessonCount, 1)
            val completedCount = if (classLessons.isNotEmpty()) {
                classLessons.count { completedIds.contains(it.lessonId) }
            } else {
                completedIds.size.coerceAtMost(totalLessons)
            }
            val progressPercent = ((completedCount.toFloat() / totalLessons.toFloat()) * 100f)
                .toInt()
                .coerceIn(0, 100)

            val lastLessonId = lastOpenedLessonByClassId[cls.classId]
            val nextLesson = classLessons.firstOrNull { !completedIds.contains(it.lessonId) }
                ?: classLessons.firstOrNull { it.lessonId == lastLessonId }
                ?: classLessons.firstOrNull()

            return ContinueLearningTarget(
                classItem = cls,
                nextLesson = nextLesson,
                completedCount = completedCount,
                totalLessons = totalLessons,
                progressPercent = progressPercent
            )
        }

        val candidates = enrolledClasses.map(::buildTarget)

        // 1. Prefer the student's most recently opened class if it still has lessons remaining
        if (!lastOpenedClassId.isNullOrBlank()) {
            val recent = candidates.firstOrNull { it.classItem.classId == lastOpenedClassId }
            if (recent != null && recent.progressPercent < 100) {
                return recent
            }
        }

        // 2. Otherwise pick the first enrolled class with remaining lessons
        val inProgress = candidates.firstOrNull { it.progressPercent < 100 }
        if (inProgress != null) return inProgress

        // 3. Fallback: most recently opened or first enrolled class (even if 100% completed)
        if (!lastOpenedClassId.isNullOrBlank()) {
            candidates.firstOrNull { it.classItem.classId == lastOpenedClassId }?.let { return it }
        }
        return candidates.firstOrNull()
    }
}
