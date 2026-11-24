package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.components.lesson.ComposerMediaAttachment
import com.example.ui.components.lesson.LessonBlock
import com.example.ui.components.lesson.applySelectionFormatting
import com.example.ui.components.lesson.computeLessonComposerStats
import com.example.ui.components.lesson.parseLessonContentToBlocks
import com.example.ui.components.lesson.parseRawLessonToComposerState
import com.example.ui.components.lesson.serializeComposerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonComposerSerializerTest {

    @Test
    fun parseRawLessonToComposerState_extractsAttachmentsAndKeepsCodeFencesUntouched() {
        val raw = """
            ## Quadratic Equations
            Solve step by step.
            formula = x = (-b ± √(b² - 4ac)) / 2a
            
            ```kotlin
            // Literal inside code fence must not be extracted
            val sample = "[quiz: ignored | a, b | 0]"
            ```
            
            [image: data:image/jpeg;base64,ABC123 | Parabola Curve]
            [video: https://youtu.be/dQw4w9WgXcQ | Intro Video]
            [voice: data:audio/mp4;base64,XYZ | 45]
            [file: Notes.pdf | https://example.com/notes.pdf]
            [quiz: What is 2 + 2? | 3, 4, 5, 6 | 1]
        """.trimIndent()

        val parsed = parseRawLessonToComposerState(raw)

        assertTrue(parsed.proseText.contains("## Quadratic Equations"))
        assertTrue(parsed.proseText.contains("val sample = \"[quiz: ignored | a, b | 0]\""))
        assertEquals(5, parsed.attachments.size)

        val img = parsed.attachments[0] as ComposerMediaAttachment.ImageAttachment
        assertEquals("data:image/jpeg;base64,ABC123", img.urlOrPath)
        assertEquals("Parabola Curve", img.caption)

        val vid = parsed.attachments[1] as ComposerMediaAttachment.VideoAttachment
        assertEquals("https://youtu.be/dQw4w9WgXcQ", vid.videoUrl)
        assertEquals("Intro Video", vid.title)

        val voice = parsed.attachments[2] as ComposerMediaAttachment.VoiceAttachment
        assertEquals("data:audio/mp4;base64,XYZ", voice.audioUrl)
        assertEquals(45, voice.durationSec)

        val file = parsed.attachments[3] as ComposerMediaAttachment.FileAttachment
        assertEquals("Notes.pdf", file.fileName)
        assertEquals("https://example.com/notes.pdf", file.filePath)

        val quiz = parsed.attachments[4] as ComposerMediaAttachment.QuizAttachment
        assertEquals("What is 2 + 2?", quiz.question)
        assertEquals(listOf("3", "4", "5", "6"), quiz.options)
        assertEquals(1, quiz.correctIndex)
    }

    @Test
    fun serializeComposerState_roundTripsIntoRichLessonBlocks() {
        val prose = "## Key Takeaways\n• Practice daily\n> Remember to check domain constraints"
        val attachments = listOf(
            ComposerMediaAttachment.ImageAttachment(
                urlOrPath = "https://cdn.example.com/diagram.jpg",
                caption = "Unit Circle"
            ),
            ComposerMediaAttachment.QuizAttachment(
                question = "Sin(90°) equals?",
                options = listOf("0", "1", "-1"),
                correctIndex = 1
            )
        )

        val serialized = serializeComposerState(prose, attachments)
        val blocks = parseLessonContentToBlocks(serialized)

        assertTrue(blocks.any { it is LessonBlock.Heading2 && it.text == "Key Takeaways" })
        assertTrue(blocks.any { it is LessonBlock.BulletItem && it.text == "Practice daily" })
        assertTrue(blocks.any { it is LessonBlock.NoteBlock && it.text == "Remember to check domain constraints" })
        assertTrue(blocks.any { it is LessonBlock.ImageBlock && it.urlOrPath == "https://cdn.example.com/diagram.jpg" })
        assertTrue(blocks.any { it is LessonBlock.QuizBlock && it.question == "Sin(90°) equals?" && it.correctIndex == 1 })
    }

    @Test
    fun computeLessonComposerStats_computesCountsAccurately() {
        val emptyStats = computeLessonComposerStats("", emptyList())
        assertEquals(0, emptyStats.wordCount)
        assertEquals(0, emptyStats.estimatedMinutes)
        assertEquals(0, emptyStats.blockCount)
        assertEquals(0, emptyStats.quizCount)

        val prose = "## Intro\nThis lesson covers linear functions and graphs with real examples."
        val attachments = listOf(
            ComposerMediaAttachment.QuizAttachment(
                question = "Slope of y = 3x + 1?",
                options = listOf("1", "3"),
                correctIndex = 1
            )
        )
        val stats = computeLessonComposerStats(prose, attachments)
        assertTrue(stats.wordCount >= 10)
        assertTrue(stats.estimatedMinutes >= 1)
        assertTrue(stats.blockCount >= 3)
        assertEquals(1, stats.quizCount)
    }

    @Test
    fun applySelectionFormatting_wrapsSelectedTextAndInsertsCollapsedPlaceholder() {
        val selectedInput = TextFieldValue(
            text = "Hello Leno World",
            selection = TextRange(6, 10)
        )
        val bolded = applySelectionFormatting(selectedInput, prefix = "**", suffix = "**", placeholder = "bold")
        assertEquals("Hello **Leno** World", bolded.text)
        assertEquals(TextRange(8, 12), bolded.selection)

        val collapsedInput = TextFieldValue(
            text = "Notes:\n",
            selection = TextRange(7)
        )
        val insertedHeading = applySelectionFormatting(collapsedInput, prefix = "## ", suffix = "", placeholder = "Section Title")
        assertEquals("Notes:\n## Section Title", insertedHeading.text)
        assertEquals(TextRange(10, 23), insertedHeading.selection)
    }
}
