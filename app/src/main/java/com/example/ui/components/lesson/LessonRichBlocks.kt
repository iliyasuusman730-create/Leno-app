package com.example.ui.components.lesson

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.util.AudioPlayerHelper
import java.io.File

/**
 * Rich Block Parsed Representation
 */
sealed class LessonBlock {
    data class Heading2(val text: String) : LessonBlock()
    data class Paragraph(val text: String) : LessonBlock()
    data class BulletItem(val text: String) : LessonBlock()
    data class Formula(val formula: String) : LessonBlock()
    data class NoteBlock(val text: String) : LessonBlock()
    data class ImageBlock(val urlOrPath: String, val caption: String = "") : LessonBlock()
    data class VideoBlock(val videoUrl: String, val title: String = "") : LessonBlock()
    data class VoiceBlock(val audioUrl: String, val durationSec: Int = 0) : LessonBlock()
    data class FileBlock(val fileName: String, val filePath: String = "") : LessonBlock()
    data class QuizBlock(val question: String, val options: List<String>, val correctIndex: Int) : LessonBlock()
    data class CodeBlock(val code: String, val language: String = "") : LessonBlock()
}

/**
 * Parses raw lesson text into structured blocks:
 * - ## = H2 bold 24px
 * - **bold** = bold
 * - bullet = • green dot
 * - formula = CO₂ + H₂O → Glucose + O₂
 * - grey note block: bg #f3f4f6 rounded-xl
 * - image block: [image: url|caption]
 * - video block: [video: url|title]
 * - voice block: [voice: url|duration]
 * - file block: [file: name|path]
 * - quiz block: [quiz: question | opt1, opt2, opt3, opt4 | correctIndex]
 * - code block: ```code```
 */
fun parseLessonContentToBlocks(rawContent: String): List<LessonBlock> {
    if (rawContent.isBlank()) return emptyList()

    val blocks = mutableListOf<LessonBlock>()
    val lines = rawContent.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i].trimEnd()

        when {
            // Code block start
            line.trim().startsWith("```") -> {
                val lang = line.trim().removePrefix("```").trim()
                val codeBuilder = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeBuilder.appendLine(lines[i])
                    i++
                }
                blocks.add(LessonBlock.CodeBlock(code = codeBuilder.toString().trimEnd(), language = lang))
                i++
            }

            // H2 Heading: ##
            line.trim().startsWith("## ") -> {
                val headingText = line.trim().removePrefix("## ").trim()
                blocks.add(LessonBlock.Heading2(headingText))
                i++
            }

            // Bullet: • or - or *
            line.trim().startsWith("• ") || line.trim().startsWith("- ") || line.trim().startsWith("* ") -> {
                val cleanBullet = line.trim()
                    .removePrefix("• ")
                    .removePrefix("- ")
                    .removePrefix("* ")
                    .trim()
                blocks.add(LessonBlock.BulletItem(cleanBullet))
                i++
            }

            // Grey Note Block: starts with > or [note: ...]
            line.trim().startsWith("> ") -> {
                val noteText = line.trim().removePrefix("> ").trim()
                blocks.add(LessonBlock.NoteBlock(noteText))
                i++
            }

            line.trim().startsWith("[note:") && line.trim().endsWith("]") -> {
                val noteText = line.trim().removePrefix("[note:").removeSuffix("]").trim()
                blocks.add(LessonBlock.NoteBlock(noteText))
                i++
            }

            // Formula: formula = ... or [formula: ...]
            line.trim().startsWith("formula = ", ignoreCase = true) || line.trim().startsWith("formula: ", ignoreCase = true) -> {
                val formulaText = line.trim()
                    .removePrefix("formula = ")
                    .removePrefix("formula: ")
                    .removePrefix("Formula = ")
                    .removePrefix("Formula: ")
                    .trim()
                blocks.add(LessonBlock.Formula(formulaText))
                i++
            }

            line.trim().startsWith("[formula:") && line.trim().endsWith("]") -> {
                val fText = line.trim().removePrefix("[formula:").removeSuffix("]").trim()
                blocks.add(LessonBlock.Formula(fText))
                i++
            }

            // Image block: [image: url|caption]
            line.trim().startsWith("[image:") && line.trim().endsWith("]") -> {
                val payload = line.trim().removePrefix("[image:").removeSuffix("]").trim()
                val parts = payload.split("|")
                val url = parts.getOrNull(0)?.trim() ?: ""
                val caption = parts.getOrNull(1)?.trim() ?: ""
                blocks.add(LessonBlock.ImageBlock(url, caption))
                i++
            }

            // Video block: [video: url|title]
            line.trim().startsWith("[video:") && line.trim().endsWith("]") -> {
                val payload = line.trim().removePrefix("[video:").removeSuffix("]").trim()
                val parts = payload.split("|")
                val url = parts.getOrNull(0)?.trim() ?: ""
                val title = parts.getOrNull(1)?.trim() ?: "Video Lesson"
                blocks.add(LessonBlock.VideoBlock(url, title))
                i++
            }

            // Voice block: [voice: url|duration] or [audio: url|duration]
            (line.trim().startsWith("[voice:", ignoreCase = true) || line.trim().startsWith("[audio:", ignoreCase = true)) && line.trim().endsWith("]") -> {
                val prefix = if (line.trim().startsWith("[voice:", ignoreCase = true)) "[voice:" else "[audio:"
                val payload = line.trim().removePrefix(prefix).removeSuffix("]").trim()
                val parts = payload.split("|")
                val url = parts.getOrNull(0)?.trim() ?: ""
                val dur = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                blocks.add(LessonBlock.VoiceBlock(url, dur))
                i++
            }

            // File block: [file: name|path]
            line.trim().startsWith("[file:") && line.trim().endsWith("]") -> {
                val payload = line.trim().removePrefix("[file:").removeSuffix("]").trim()
                val parts = payload.split("|")
                val name = parts.getOrNull(0)?.trim() ?: "Document"
                val path = parts.getOrNull(1)?.trim() ?: ""
                blocks.add(LessonBlock.FileBlock(name, path))
                i++
            }

            // Quiz block: [quiz: question | opt1, opt2, opt3, opt4 | correctIndex]
            line.trim().startsWith("[quiz:") && line.trim().endsWith("]") -> {
                val payload = line.trim().removePrefix("[quiz:").removeSuffix("]").trim()
                val parts = payload.split("|")
                val question = parts.getOrNull(0)?.trim() ?: "Lesson Quiz"
                val rawOptions = parts.getOrNull(1)?.split(",")?.map { it.trim() } ?: emptyList()
                val correctIdx = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                blocks.add(LessonBlock.QuizBlock(question, rawOptions, correctIdx))
                i++
            }

            // Blank line
            line.trim().isEmpty() -> {
                i++
            }

            // Regular paragraph
            else -> {
                blocks.add(LessonBlock.Paragraph(line.trim()))
                i++
            }
        }
    }

    return blocks
}

/**
 * Main Renderer for Rich Lesson Blocks
 */
@Composable
fun LessonContentRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true
) {
    val blocks = remember(content) { parseLessonContentToBlocks(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (blocks.isEmpty()) {
            Text(
                text = "Lesson content will appear here...",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            blocks.forEach { block ->
                when (block) {
                    is LessonBlock.Heading2 -> Heading2BlockView(block.text)
                    is LessonBlock.Paragraph -> ParagraphBlockView(block.text)
                    is LessonBlock.BulletItem -> BulletBlockView(block.text)
                    is LessonBlock.Formula -> FormulaBlockView(block.formula)
                    is LessonBlock.NoteBlock -> GreyNoteBlockView(block.text)
                    is LessonBlock.ImageBlock -> ImageBlockView(block.urlOrPath, block.caption)
                    is LessonBlock.VideoBlock -> VideoBlockView(block.videoUrl, block.title)
                    is LessonBlock.VoiceBlock -> VoiceBlockView(block.audioUrl, block.durationSec)
                    is LessonBlock.FileBlock -> FileBlockView(block.fileName, block.filePath)
                    is LessonBlock.QuizBlock -> QuizBlockView(block.question, block.options, block.correctIndex, isInteractive)
                    is LessonBlock.CodeBlock -> CodeBlockView(block.code, block.language)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Block Views
// -----------------------------------------------------------------------------

@Composable
fun Heading2BlockView(text: String) {
    Text(
        text = text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF111827), // Deep slate / black
        lineHeight = 30.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun ParagraphBlockView(text: String) {
    Text(
        text = parseBoldSpans(text),
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = Color(0xFF374151)
    )
}

@Composable
fun BulletBlockView(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // • Green dot
        Box(
            modifier = Modifier
                .padding(top = 7.dp, end = 10.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E))
        )
        Text(
            text = parseBoldSpans(text),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = Color(0xFF1F2937),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FormulaBlockView(formula: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF0FDF4), // Pale green accent
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF86EFAC))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "FORMULA / EQUATION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formula,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
                color = Color(0xFF14532D),
                lineHeight = 26.sp
            )
        }
    }
}

@Composable
fun GreyNoteBlockView(text: String) {
    // Grey note block: bg #f3f4f6 rounded-xl
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF3F4F6),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.FormatQuote,
                contentDescription = null,
                tint = Color(0xFF6B7280),
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = parseBoldSpans(text),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Color(0xFF4B5563),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ImageBlockView(urlOrPath: String, caption: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val isLocal = !urlOrPath.startsWith("http")
            val imageModel = if (isLocal && File(urlOrPath).exists()) File(urlOrPath) else urlOrPath

            AsyncImage(
                model = imageModel,
                contentDescription = caption.ifEmpty { "Lesson visual" },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentScale = ContentScale.Crop
            )

            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun VideoBlockView(videoUrl: String, title: String) {
    val context = LocalContext.current
    Surface(
        onClick = {
            if (videoUrl.isNotBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111827),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF374151)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDC2626)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Video",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "Watch Video Lesson" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = videoUrl,
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun VoiceBlockView(audioUrl: String, durationSec: Int) {
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayerHelper(context) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableStateOf(if (durationSec > 0) durationSec * 1000 else 0) }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF0FDF4),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
                    .clickable {
                        if (isPlaying) {
                            audioPlayer.stop()
                            isPlaying = false
                        } else {
                            isPlaying = true
                            audioPlayer.playAudio(
                                filePathOrUri = audioUrl,
                                expectedDurationSeconds = if (durationSec > 0) durationSec else 10,
                                onProgress = { cur, tot ->
                                    currentPositionMs = cur
                                    totalDurationMs = tot
                                },
                                onCompletion = {
                                    isPlaying = false
                                    currentPositionMs = 0
                                },
                                onError = {
                                    isPlaying = false
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play Voice Note",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Voice Note from Teacher",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14532D)
                )
                val curSec = currentPositionMs / 1000
                val totSec = if (totalDurationMs > 0) totalDurationMs / 1000 else durationSec
                Text(
                    text = String.format("%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, totSec / 60, totSec % 60),
                    fontSize = 11.sp,
                    color = Color(0xFF15803D)
                )
            }

            Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun FileBlockView(fileName: String, filePath: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFEFF6FF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A)
                )
                Text(
                    text = "Attached Document (PDF/DOC)",
                    fontSize = 11.sp,
                    color = Color(0xFF3B82F6)
                )
            }
        }
    }
}

@Composable
fun QuizBlockView(
    question: String,
    options: List<String>,
    correctIndex: Int,
    isInteractive: Boolean
) {
    var selectedOption by remember { mutableIntStateOf(-1) }
    var hasChecked by remember { mutableStateOf(!isInteractive) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF22C55E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LESSON QUIZ / PRACTICE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF22C55E)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = question,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(12.dp))

            options.forEachIndexed { idx, opt ->
                val isSelected = selectedOption == idx
                val isCorrect = idx == correctIndex
                val showFeedback = hasChecked

                val containerColor = when {
                    showFeedback && isCorrect -> Color(0xFFDCFCE7)
                    showFeedback && isSelected && !isCorrect -> Color(0xFFFEE2E2)
                    isSelected -> Color(0xFFF0FDF4)
                    else -> Color(0xFFF9FAFB)
                }

                val borderColor = when {
                    showFeedback && isCorrect -> Color(0xFF22C55E)
                    showFeedback && isSelected && !isCorrect -> Color(0xFFEF4444)
                    isSelected -> Color(0xFF22C55E)
                    else -> Color(0xFFE5E7EB)
                }

                Surface(
                    onClick = {
                        if (isInteractive && !hasChecked) {
                            selectedOption = idx
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = containerColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val optionLabel = ('A'.code + idx).toChar().toString()
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected || (showFeedback && isCorrect)) Color(0xFF22C55E) else Color(0xFFE5E7EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = optionLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected || (showFeedback && isCorrect)) Color.White else Color(0xFF4B5563)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = opt,
                            fontSize = 14.sp,
                            color = Color(0xFF1F2937),
                            modifier = Modifier.weight(1f)
                        )

                        if (showFeedback && isCorrect) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Correct",
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isInteractive && !hasChecked && selectedOption != -1) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { hasChecked = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Check Answer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CodeBlockView(code: String, language: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1E2E), // Slate dark code background
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF313244)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (language.isNotBlank()) {
                Text(
                    text = language.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA6ADC8),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Text(
                text = code,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFCDD6F4),
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Parses markdown **bold** syntax into AnnotatedString
 */
private fun parseBoldSpans(raw: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < raw.length) {
            val startBold = raw.indexOf("**", cursor)
            if (startBold == -1) {
                append(raw.substring(cursor))
                break
            }
            append(raw.substring(cursor, startBold))
            val endBold = raw.indexOf("**", startBold + 2)
            if (endBold == -1) {
                append(raw.substring(startBold))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF111827))) {
                append(raw.substring(startBold + 2, endBold))
            }
            cursor = endBold + 2
        }
    }
}
