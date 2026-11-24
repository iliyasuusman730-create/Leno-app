package com.example.ui.components.lesson

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.theme.LenoMonoFont
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.ui.theme.LocalLenoDarkTheme
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
    isInteractive: Boolean = true,
    textScale: Float = 1.0f,
    readerTextColor: Color? = null,
    readerMutedColor: Color? = null,
    onAskAboutFormula: ((String) -> Unit)? = null,
    onQuizAnswered: ((question: String, isCorrect: Boolean) -> Unit)? = null
) {
    val blocks = remember(content) { parseLessonContentToBlocks(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (blocks.isEmpty()) {
            Text(
                text = "Lesson content will appear here...",
                fontSize = (14.5f * textScale).sp,
                color = readerMutedColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            blocks.forEach { block ->
                when (block) {
                    is LessonBlock.Heading2 -> Heading2BlockView(block.text, textScale, readerTextColor)
                    is LessonBlock.Paragraph -> ParagraphBlockView(block.text, textScale, readerTextColor)
                    is LessonBlock.BulletItem -> BulletBlockView(block.text, textScale, readerTextColor)
                    is LessonBlock.Formula -> FormulaBlockView(block.formula, textScale, onAskAboutFormula)
                    is LessonBlock.NoteBlock -> GreyNoteBlockView(block.text, textScale, readerTextColor, readerMutedColor)
                    is LessonBlock.ImageBlock -> ImageBlockView(block.urlOrPath, block.caption)
                    is LessonBlock.VideoBlock -> VideoBlockView(block.videoUrl, block.title)
                    is LessonBlock.VoiceBlock -> VoiceBlockView(block.audioUrl, block.durationSec)
                    is LessonBlock.FileBlock -> FileBlockView(block.fileName, block.filePath)
                    is LessonBlock.QuizBlock -> QuizBlockView(
                        question = block.question,
                        options = block.options,
                        correctIndex = block.correctIndex,
                        isInteractive = isInteractive,
                        onAnswerChecked = { isCorrect ->
                            onQuizAnswered?.invoke(block.question, isCorrect)
                        }
                    )
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
fun Heading2BlockView(
    text: String,
    textScale: Float = 1.0f,
    readerTextColor: Color? = null
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = (21f * textScale).sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (28f * textScale).sp
        ),
        color = readerTextColor ?: MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun ParagraphBlockView(
    text: String,
    textScale: Float = 1.0f,
    readerTextColor: Color? = null
) {
    val boldColor = readerTextColor ?: MaterialTheme.colorScheme.onSurface
    Text(
        text = parseBoldSpans(text, boldColor),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = (16.5f * textScale).sp,
            lineHeight = (26.5f * textScale).sp
        ),
        color = (readerTextColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.90f)
    )
}

@Composable
fun BulletBlockView(
    text: String,
    textScale: Float = 1.0f,
    readerTextColor: Color? = null
) {
    val boldColor = readerTextColor ?: MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = (9f * textScale).dp, end = 10.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(LenoPrimary)
        )
        Text(
            text = parseBoldSpans(text, boldColor),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = (16f * textScale).sp,
                lineHeight = (25f * textScale).sp
            ),
            color = (readerTextColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.90f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FormulaBlockView(
    formula: String,
    textScale: Float = 1.0f,
    onAskAboutFormula: ((String) -> Unit)? = null
) {
    val isDark = LocalLenoDarkTheme.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF102A20) else LenoPrimaryVeryLight,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) LenoPrimary.copy(alpha = 0.6f) else Color(0xFF86EFAC))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "FORMULA / KEY RULE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = if (isDark) Color.White else Color(0xFF111111)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(formula))
                            Toast.makeText(context, "Formula copied", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Formula",
                                tint = if (isDark) Color(0xFFA7F3D0) else LenoPrimaryDark,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Copy",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White else Color(0xFF111111)
                            )
                        }
                    }

                    if (onAskAboutFormula != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isDark) LenoPrimary.copy(alpha = 0.25f) else Color(0xFFDCFCE7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimary.copy(alpha = 0.55f)),
                            modifier = Modifier.clickable { onAskAboutFormula(formula) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ask Teacher",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF111111)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formula,
                fontSize = (17f * textScale).sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = LenoMonoFont,
                color = if (isDark) Color.White else Color(0xFF111111),
                lineHeight = (26f * textScale).sp
            )
        }
    }
}

@Composable
fun GreyNoteBlockView(
    text: String,
    textScale: Float = 1.0f,
    readerTextColor: Color? = null,
    readerMutedColor: Color? = null
) {
    val isDark = LocalLenoDarkTheme.current
    val boldColor = readerTextColor ?: MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color(0xFFF3F4F6).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.FormatQuote,
                contentDescription = null,
                tint = if (isDark) MaterialTheme.colorScheme.secondary else LenoPrimary,
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = parseBoldSpans(text, boldColor),
                fontSize = (14.5f * textScale).sp,
                lineHeight = (22f * textScale).sp,
                color = readerMutedColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ImageBlockView(urlOrPath: String, caption: String) {
    var showZoomViewer by remember { mutableStateOf(false) }
    val isLocalFile = !urlOrPath.startsWith("http") && !urlOrPath.startsWith("data:")
    val imageModel = remember(urlOrPath) {
        if (isLocalFile && File(urlOrPath).exists()) File(urlOrPath) else urlOrPath
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showZoomViewer = true }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = caption.ifEmpty { "Lesson visual" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 320.dp)
                        .clip(
                            if (caption.isNotBlank()) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            else RoundedCornerShape(16.dp)
                        ),
                    contentScale = ContentScale.Fit
                )

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom diagram",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Zoom", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }

            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }

    if (showZoomViewer) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Dialog(
            onDismissRequest = { showZoomViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = caption.ifEmpty { "Full view visual" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = caption.ifBlank { "Pinch to zoom" },
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = { showZoomViewer = false },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Zoom", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoBlockView(videoUrl: String, title: String) {
    val context = LocalContext.current
    val sourceLabel = remember(videoUrl) {
        when {
            videoUrl.contains("youtu", ignoreCase = true) -> "YouTube Lesson"
            videoUrl.contains("vimeo", ignoreCase = true) -> "Vimeo Lesson"
            else -> "Video Lesson"
        }
    }
    Surface(
        onClick = {
            if (videoUrl.isNotBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Unable to open video link", Toast.LENGTH_SHORT).show()
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F172A),
        border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(146.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0B2E22),
                                Color(0xFF0F172A),
                                Color(0xFF1E293B)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(sourceLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(LenoPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "Watch Video Lesson" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = videoUrl,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Watch →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun VoiceBlockView(audioUrl: String, durationSec: Int) {
    val context = LocalContext.current
    val isDark = LocalLenoDarkTheme.current
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
        color = if (isDark) Color(0xFF103324) else Color(0xFFF0FDF4),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) LenoPrimary.copy(alpha = 0.6f) else LenoPrimary.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(LenoPrimary)
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
                    text = "Voice Explanation from Teacher",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF111111)
                )
                val curSec = currentPositionMs / 1000
                val totSec = if (totalDurationMs > 0) totalDurationMs / 1000 else durationSec
                Text(
                    text = String.format("%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, totSec / 60, totSec % 60),
                    fontSize = 11.sp,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
                )
            }

            Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                tint = if (isDark) Color(0xFF34D399) else LenoPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun FileBlockView(fileName: String, filePath: String) {
    val isDark = LocalLenoDarkTheme.current
    val context = LocalContext.current
    var showTextPreview by remember { mutableStateOf<String?>(null) }

    fun saveDataUrlToDownloads(dataUrl: String) {
        try {
            val commaIdx = dataUrl.indexOf(',')
            if (commaIdx <= 0) return
            val header = dataUrl.substring(5, commaIdx)
            val mime = header.substringBefore(';').ifBlank { "application/octet-stream" }
            val b64 = dataUrl.substring(commaIdx + 1)
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val destUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (destUri != null) {
                    resolver.openOutputStream(destUri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(destUri, values, null, null)
                    Toast.makeText(context, "Saved $fileName to Downloads", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            val outFile = File(context.filesDir, fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            outFile.writeBytes(bytes)
            Toast.makeText(context, "Saved $fileName locally", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to save attachment: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        onClick = {
            if (filePath.startsWith("data:")) {
                val commaIdx = filePath.indexOf(',')
                val header = if (commaIdx > 5) filePath.substring(5, commaIdx) else ""
                if (header.contains("text/", ignoreCase = true) || header.contains("json", ignoreCase = true)) {
                    try {
                        val b64 = filePath.substring(commaIdx + 1)
                        val decoded = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                        showTextPreview = decoded
                    } catch (_: Exception) {
                        saveDataUrlToDownloads(filePath)
                    }
                } else {
                    saveDataUrlToDownloads(filePath)
                }
            } else if (filePath.isNotBlank()) {
                val parsed = Uri.parse(filePath)
                val scheme = parsed.scheme?.lowercase().orEmpty()
                if (scheme == "http" || scheme == "https" || scheme == "content") {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
                            if (scheme == "content") {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "No app available to open $fileName", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val localPath = if (scheme == "file") parsed.path.orEmpty() else filePath
                    val localFile = File(localPath)
                    if (localFile.exists() && localFile.canRead()) {
                        try {
                            val bytes = localFile.readBytes()
                            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            saveDataUrlToDownloads("data:application/octet-stream;base64,$b64")
                        } catch (_: Exception) {
                            Toast.makeText(context, "Unable to export attachment: $fileName", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Attachment unavailable locally: $fileName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF132238) else Color(0xFFEFF6FF),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) Color(0xFF3B82F6).copy(alpha = 0.55f) else Color(0xFF93C5FD)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFDBEAFE) else Color(0xFF1E3A8A)
                )
                Text(
                    text = if (filePath.startsWith("data:")) "Attached Document • Tap to view or download" else "Attached Document • Tap to open",
                    fontSize = 11.sp,
                    color = if (isDark) Color(0xFF93C5FD) else Color(0xFF3B82F6)
                )
            }
        }
    }

    if (showTextPreview != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTextPreview = null },
            title = { Text(fileName, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = showTextPreview.orEmpty(),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        saveDataUrlToDownloads(filePath)
                        showTextPreview = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary)
                ) {
                    Text("Save to Downloads")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showTextPreview = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun QuizBlockView(
    question: String,
    options: List<String>,
    correctIndex: Int,
    isInteractive: Boolean,
    onAnswerChecked: ((isCorrect: Boolean) -> Unit)? = null
) {
    val isDark = LocalLenoDarkTheme.current
    var selectedOption by remember { mutableIntStateOf(-1) }
    var hasChecked by remember { mutableStateOf(!isInteractive) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, LenoPrimary.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF34D399) else LenoPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LESSON QUIZ / PRACTICE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (hasChecked) {
                    val isCorrect = selectedOption == correctIndex
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isCorrect) {
                            if (isDark) Color(0xFF103324) else Color(0xFFDCFCE7)
                        } else {
                            if (isDark) Color(0xFF451414) else Color(0xFFFEE2E2)
                        }
                    ) {
                        Text(
                            text = if (isCorrect) "Passed ✓" else "Try Again",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCorrect) {
                                if (isDark) Color.White else Color(0xFF111111)
                            } else {
                                if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = question,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            options.forEachIndexed { idx, opt ->
                val isSelected = selectedOption == idx
                val isCorrect = idx == correctIndex
                val showFeedback = hasChecked

                val containerColor = when {
                    showFeedback && isCorrect -> if (isDark) Color(0xFF103324) else Color(0xFFDCFCE7)
                    showFeedback && isSelected && !isCorrect -> if (isDark) Color(0xFF451414) else Color(0xFFFEE2E2)
                    isSelected -> if (isDark) Color(0xFF134030) else LenoPrimaryVeryLight
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                }

                val borderColor = when {
                    showFeedback && isCorrect -> Color(0xFF22C55E)
                    showFeedback && isSelected && !isCorrect -> Color(0xFFEF4444)
                    isSelected -> LenoPrimary
                    else -> MaterialTheme.colorScheme.outlineVariant
                }

                Surface(
                    onClick = {
                        if (isInteractive && !hasChecked) {
                            selectedOption = idx
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = containerColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val optionLabel = ('A'.code + idx).toChar().toString()
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected || (showFeedback && isCorrect)) LenoPrimary
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = optionLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected || (showFeedback && isCorrect)) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = opt,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        if (showFeedback && isCorrect) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Correct",
                                tint = if (isDark) Color(0xFF6EE7B7) else Color(0xFF16A34A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isInteractive) {
                Spacer(modifier = Modifier.height(10.dp))

                if (!hasChecked && selectedOption != -1) {
                    Button(
                        onClick = {
                            hasChecked = true
                            onAnswerChecked?.invoke(selectedOption == correctIndex)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Check Answer", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else if (hasChecked) {
                    val isCorrect = selectedOption == correctIndex
                    if (isCorrect) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDark) Color(0xFF103324) else Color(0xFFDCFCE7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🎉 Correct! You mastered this question!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White else Color(0xFF111111),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDark) Color(0xFF451414) else Color(0xFFFEE2E2),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Not quite! The correct answer is Option ${('A'.code + correctIndex).toChar()}: ${options.getOrElse(correctIndex) { "" }}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    hasChecked = false
                                    selectedOption = -1
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Try Again 🔄", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CodeBlockView(code: String, language: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1E2E), // Slate dark code background
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF313244)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "CODE" }.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA6ADC8)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(code))
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Code",
                            tint = Color(0xFFA6ADC8),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Copy", fontSize = 10.sp, color = Color(0xFFA6ADC8))
                    }
                }
            }
            Text(
                text = code,
                fontSize = 13.sp,
                fontFamily = LenoMonoFont,
                color = Color(0xFFCDD6F4),
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * Parses markdown **bold** syntax into AnnotatedString
 */
private fun parseBoldSpans(
    raw: String,
    boldColor: Color = Color(0xFF111827)
): androidx.compose.ui.text.AnnotatedString {
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
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                append(raw.substring(startBold + 2, endBold))
            }
            cursor = endBold + 2
        }
    }
}
