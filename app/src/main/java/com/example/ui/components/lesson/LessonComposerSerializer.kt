package com.example.ui.components.lesson

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.util.UUID

sealed class ComposerMediaAttachment {
    abstract val id: String

    data class ImageAttachment(
        override val id: String = UUID.randomUUID().toString(),
        val urlOrPath: String,
        val caption: String
    ) : ComposerMediaAttachment()

    data class VideoAttachment(
        override val id: String = UUID.randomUUID().toString(),
        val videoUrl: String,
        val title: String
    ) : ComposerMediaAttachment()

    data class VoiceAttachment(
        override val id: String = UUID.randomUUID().toString(),
        val audioUrl: String,
        val durationSec: Int
    ) : ComposerMediaAttachment()

    data class QuizAttachment(
        override val id: String = UUID.randomUUID().toString(),
        val question: String,
        val options: List<String>,
        val correctIndex: Int
    ) : ComposerMediaAttachment()

    data class FileAttachment(
        override val id: String = UUID.randomUUID().toString(),
        val fileName: String,
        val filePath: String
    ) : ComposerMediaAttachment()
}

data class ParsedComposerState(
    val proseText: String,
    val attachments: List<ComposerMediaAttachment>
)

data class LessonComposerStats(
    val wordCount: Int,
    val estimatedMinutes: Int,
    val blockCount: Int,
    val quizCount: Int
)

/**
 * Extracts media, voice, video, document, and quiz tags out of raw lesson content so teachers
 * see clean visual attachment cards instead of raw base64/path markup in the text editor.
 */
fun parseRawLessonToComposerState(rawContent: String): ParsedComposerState {
    if (rawContent.isBlank()) return ParsedComposerState("", emptyList())

    val proseLines = mutableListOf<String>()
    val attachments = mutableListOf<ComposerMediaAttachment>()

    val lines = rawContent.lines()
    var inCodeBlock = false

    for (rawLine in lines) {
        val trimmed = rawLine.trim()
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            proseLines.add(rawLine)
            continue
        }
        if (inCodeBlock) {
            proseLines.add(rawLine)
            continue
        }

        when {
            trimmed.startsWith("[image:", ignoreCase = true) && trimmed.endsWith("]") -> {
                val inner = trimmed.removeRange(0, 7).dropLast(1).trim()
                val parts = inner.split("|", limit = 2)
                val url = parts.getOrNull(0)?.trim().orEmpty()
                val caption = parts.getOrNull(1)?.trim().orEmpty()
                if (url.isNotBlank()) {
                    attachments.add(ComposerMediaAttachment.ImageAttachment(urlOrPath = url, caption = caption))
                }
            }
            trimmed.startsWith("[video:", ignoreCase = true) && trimmed.endsWith("]") -> {
                val inner = trimmed.removeRange(0, 7).dropLast(1).trim()
                val parts = inner.split("|", limit = 2)
                val url = parts.getOrNull(0)?.trim().orEmpty()
                val title = parts.getOrNull(1)?.trim().orEmpty()
                if (url.isNotBlank()) {
                    attachments.add(ComposerMediaAttachment.VideoAttachment(videoUrl = url, title = title))
                }
            }
            (trimmed.startsWith("[voice:", ignoreCase = true) || trimmed.startsWith("[audio:", ignoreCase = true)) && trimmed.endsWith("]") -> {
                val inner = trimmed.substringAfter(":").dropLast(1).trim()
                val parts = inner.split("|", limit = 2)
                val url = parts.getOrNull(0)?.trim().orEmpty()
                val duration = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 10
                if (url.isNotBlank()) {
                    attachments.add(ComposerMediaAttachment.VoiceAttachment(audioUrl = url, durationSec = duration))
                }
            }
            trimmed.startsWith("[file:", ignoreCase = true) && trimmed.endsWith("]") -> {
                val inner = trimmed.removeRange(0, 6).dropLast(1).trim()
                val parts = inner.split("|", limit = 2)
                val name = parts.getOrNull(0)?.trim()?.ifBlank { "Attachment" } ?: "Attachment"
                val path = parts.getOrNull(1)?.trim().orEmpty()
                if (path.isNotBlank()) {
                    attachments.add(ComposerMediaAttachment.FileAttachment(fileName = name, filePath = path))
                }
            }
            trimmed.startsWith("[quiz:", ignoreCase = true) && trimmed.endsWith("]") -> {
                val inner = trimmed.removeRange(0, 6).dropLast(1).trim()
                val parts = inner.split("|")
                val question = parts.getOrNull(0)?.trim().orEmpty()
                val options = parts.getOrNull(1)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                val correct = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                if (question.isNotBlank() && options.size >= 2) {
                    attachments.add(
                        ComposerMediaAttachment.QuizAttachment(
                            question = question,
                            options = options,
                            correctIndex = correct.coerceIn(0, options.lastIndex)
                        )
                    )
                }
            }
            else -> proseLines.add(rawLine)
        }
    }

    val cleanedProse = proseLines.joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    return ParsedComposerState(proseText = cleanedProse, attachments = attachments)
}

/**
 * Serializes prose text + structured visual attachment cards back into standard rich-block syntax
 * consumed seamlessly by [parseLessonContentToBlocks] and [LessonContentRenderer].
 */
fun serializeComposerState(
    proseText: String,
    attachments: List<ComposerMediaAttachment>
): String {
    val cleanProse = proseText.trim()
    val attachmentLines = attachments.map { item ->
        when (item) {
            is ComposerMediaAttachment.ImageAttachment -> {
                val cap = item.caption.trim().ifBlank { "Visual Diagram" }
                "[image: ${item.urlOrPath} | $cap]"
            }
            is ComposerMediaAttachment.VideoAttachment -> {
                val ttl = item.title.trim().ifBlank { "Video Lesson" }
                "[video: ${item.videoUrl} | $ttl]"
            }
            is ComposerMediaAttachment.VoiceAttachment -> {
                "[voice: ${item.audioUrl} | ${item.durationSec.coerceAtLeast(1)}]"
            }
            is ComposerMediaAttachment.FileAttachment -> {
                "[file: ${item.fileName} | ${item.filePath}]"
            }
            is ComposerMediaAttachment.QuizAttachment -> {
                val optsStr = item.options.joinToString(", ") { it.replace("|", "/") }
                "[quiz: ${item.question.replace("|", "/")} | $optsStr | ${item.correctIndex}]"
            }
        }
    }

    return buildString {
        if (cleanProse.isNotEmpty()) {
            append(cleanProse)
        }
        if (attachmentLines.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append(attachmentLines.joinToString("\n\n"))
        }
    }
}

fun computeLessonComposerStats(
    proseText: String,
    attachments: List<ComposerMediaAttachment>
): LessonComposerStats {
    val serialized = serializeComposerState(proseText, attachments)
    val blocks = parseLessonContentToBlocks(serialized)
    val words = proseText.trim()
        .split(Regex("\\s+"))
        .count { it.isNotBlank() }
    val mediaWeightMinutes = attachments.size * 0.5
    val readingMinutes = ((words / 150.0) + mediaWeightMinutes).let {
        if (serialized.isBlank()) 0 else kotlin.math.ceil(it).toInt().coerceAtLeast(1)
    }
    val quizCount = blocks.count { it is LessonBlock.QuizBlock }
    return LessonComposerStats(
        wordCount = words,
        estimatedMinutes = readingMinutes,
        blockCount = blocks.size,
        quizCount = quizCount
    )
}

/**
 * Selection-aware text formatting: wraps highlighted selection when present, or inserts clean marker/placeholder.
 */
fun applySelectionFormatting(
    value: TextFieldValue,
    prefix: String,
    suffix: String = "",
    placeholder: String = ""
): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)
    return if (start != end) {
        val selected = text.substring(start, end)
        val replacement = "$prefix$selected$suffix"
        val newText = text.substring(0, start) + replacement + text.substring(end)
        TextFieldValue(
            text = newText,
            selection = TextRange(start + prefix.length, start + prefix.length + selected.length)
        )
    } else {
        val insertion = "$prefix$placeholder$suffix"
        val newText = text.substring(0, start) + insertion + text.substring(end)
        val cursorStart = start + prefix.length
        val cursorEnd = cursorStart + placeholder.length
        TextFieldValue(
            text = newText,
            selection = TextRange(cursorStart, cursorEnd)
        )
    }
}
