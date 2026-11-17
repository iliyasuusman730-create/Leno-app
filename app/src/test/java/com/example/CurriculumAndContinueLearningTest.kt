package com.example

import com.example.data.entity.ClassEntity
import com.example.data.entity.LessonEntity
import com.example.util.CurriculumSectionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumAndContinueLearningTest {

    private fun sampleClass(
        classId: String,
        title: String,
        lessonCount: Int = 3
    ) = ClassEntity(
        classId = classId,
        title = title,
        subject = "Mathematics",
        shortDescription = "Modern online course",
        instructorUserId = "usr_teacher",
        instructorName = "Teacher Amina",
        instructorUsername = "amina",
        instructorLinoId = "LEN-10002000",
        lessonCount = lessonCount
    )

    private fun sampleLesson(
        lessonId: String,
        classId: String,
        orderIndex: Int,
        title: String = "Topic $orderIndex",
        summary: String = "",
        content: String = "Structured lesson explanation for topic $orderIndex."
    ) = LessonEntity(
        lessonId = lessonId,
        classId = classId,
        title = title,
        content = content,
        orderIndex = orderIndex,
        summary = summary
    )

    @Test
    fun groupLessonsIntoSections_emptyReturnsEmpty() {
        assertTrue(CurriculumSectionHelper.groupLessonsIntoSections(emptyList()).isEmpty())
    }

    @Test
    fun groupLessonsIntoSections_autoChunksIntoProgressiveModules() {
        val lessons = listOf(
            sampleLesson("l5", "cls_1", 5, "Quadratics"),
            sampleLesson("l1", "cls_1", 1, "Numbers"),
            sampleLesson("l3", "cls_1", 3, "Fractions"),
            sampleLesson("l2", "cls_1", 2, "Decimals"),
            sampleLesson("l4", "cls_1", 4, "Linear Equations")
        )

        val sections = CurriculumSectionHelper.groupLessonsIntoSections(lessons)
        assertEquals(2, sections.size)
        assertTrue(sections[0].title.startsWith("Module 1"))
        assertEquals(listOf(1, 2, 3), sections[0].lessons.map { it.orderIndex })
        assertTrue(sections[1].title.startsWith("Module 2"))
        assertEquals(listOf(4, 5), sections[1].lessons.map { it.orderIndex })
    }

    @Test
    fun groupLessonsIntoSections_respectsExplicitModuleOrBracketMarkers() {
        val lessons = listOf(
            sampleLesson("l1", "cls_1", 1, "Module 1: Foundations — Variables"),
            sampleLesson("l2", "cls_1", 2, "Module 2: Practice — Equations"),
            sampleLesson("l3", "cls_1", 3, "Module 2: Practice — Word Problems")
        )

        val sections = CurriculumSectionHelper.groupLessonsIntoSections(lessons)
        assertEquals(2, sections.size)
        assertEquals(1, sections[0].lessons.size)
        assertEquals(2, sections[1].lessons.size)
        assertTrue(sections[0].title.startsWith("Module 1"))
        assertTrue(sections[1].title.startsWith("Module 2"))
    }

    @Test
    fun estimateReadingMinutes_accountsForLengthAndInteractiveBlocks() {
        val shortLesson = sampleLesson("l1", "cls_1", 1, content = "Short intro.")
        val longWithQuiz = sampleLesson(
            "l2",
            "cls_1",
            2,
            content = (1..300).joinToString(" ") { "word" } + "\n[quiz: 2+2? | 3, 4 | 1]\n[formula: a^2 + b^2 = c^2]"
        )

        val shortMins = CurriculumSectionHelper.estimateReadingMinutes(shortLesson)
        val longMins = CurriculumSectionHelper.estimateReadingMinutes(longWithQuiz)
        assertTrue(shortMins >= 2)
        assertTrue(longMins > shortMins)
    }

    @Test
    fun selectContinueLearningTarget_returnsNullWhenNoEnrolledClasses() {
        val target = CurriculumSectionHelper.selectContinueLearningTarget(
            enrolledClasses = emptyList(),
            lastOpenedClassId = null,
            lessonsByClassId = emptyMap(),
            completedIdsByClassId = emptyMap()
        )
        assertNull(target)
    }

    @Test
    fun selectContinueLearningTarget_prefersLastOpenedIncompleteClassAndNextUncompletedLesson() {
        val cls1 = sampleClass("cls_1", "Algebra", lessonCount = 2)
        val cls2 = sampleClass("cls_2", "Geometry", lessonCount = 2)

        val lessons1 = listOf(
            sampleLesson("l1_1", "cls_1", 1, "Variables"),
            sampleLesson("l1_2", "cls_1", 2, "Expressions")
        )
        val lessons2 = listOf(
            sampleLesson("l2_1", "cls_2", 1, "Angles"),
            sampleLesson("l2_2", "cls_2", 2, "Triangles")
        )

        val target = CurriculumSectionHelper.selectContinueLearningTarget(
            enrolledClasses = listOf(cls1, cls2),
            lastOpenedClassId = "cls_2",
            lessonsByClassId = mapOf("cls_1" to lessons1, "cls_2" to lessons2),
            completedIdsByClassId = mapOf("cls_1" to emptySet(), "cls_2" to setOf("l2_1"))
        )

        assertNotNull(target)
        assertEquals("cls_2", target!!.classItem.classId)
        assertEquals("l2_2", target.nextLesson?.lessonId)
        assertEquals(1, target.completedCount)
        assertEquals(2, target.totalLessons)
        assertEquals(50, target.progressPercent)
    }

    @Test
    fun selectContinueLearningTarget_skipsCompletedLastOpenedInFavorOfIncompleteClass() {
        val cls1 = sampleClass("cls_1", "Algebra", lessonCount = 2)
        val cls2 = sampleClass("cls_2", "Geometry", lessonCount = 2)

        val lessons1 = listOf(
            sampleLesson("l1_1", "cls_1", 1, "Variables"),
            sampleLesson("l1_2", "cls_1", 2, "Expressions")
        )
        val lessons2 = listOf(
            sampleLesson("l2_1", "cls_2", 1, "Angles"),
            sampleLesson("l2_2", "cls_2", 2, "Triangles")
        )

        val target = CurriculumSectionHelper.selectContinueLearningTarget(
            enrolledClasses = listOf(cls1, cls2),
            lastOpenedClassId = "cls_2",
            lessonsByClassId = mapOf("cls_1" to lessons1, "cls_2" to lessons2),
            completedIdsByClassId = mapOf(
                "cls_1" to emptySet(),
                "cls_2" to setOf("l2_1", "l2_2")
            )
        )

        assertNotNull(target)
        assertEquals("cls_1", target!!.classItem.classId)
        assertEquals("l1_1", target.nextLesson?.lessonId)
        assertEquals(0, target.progressPercent)
    }
}
