package com.example.ui.components.lesson

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.ui.theme.LocalLenoDarkTheme
import com.example.util.AudioPlayerHelper
import com.example.util.AudioRecorderHelper
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

@Composable
internal fun VideoInsertModal(
    initialUrl: String = "",
    initialTitle: String = "",
    onDismiss: () -> Unit,
    onInsertVideo: (url: String, title: String) -> Unit
) {
    var videoUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var videoTitle by remember(initialTitle) { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Insert Video Link", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = videoTitle,
                    onValueChange = { videoTitle = it },
                    label = { Text("Video Title") },
                    placeholder = { Text("e.g. Photosynthesis Explained") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("YouTube or Video URL") },
                    placeholder = { Text("https://youtu.be/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (videoUrl.isNotBlank()) {
                        onInsertVideo(videoUrl.trim(), videoTitle.ifBlank { "Video Lesson" })
                    }
                },
                enabled = videoUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary)
            ) {
                Text("Insert Video", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun VoiceNoteRecorderModal(
    onDismiss: () -> Unit,
    onSaveVoiceNote: (filePath: String, durationSec: Int) -> Unit
) {
    val context = LocalContext.current
    val audioRecorder = remember { AudioRecorderHelper(context) }
    val audioPlayer = remember { AudioPlayerHelper(context) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var isPlayingPreview by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) audioRecorder.stopRecording()
            audioPlayer.stop()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isRecording) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = LenoPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Record Voice Note", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val min = recordingSeconds / 60
                val sec = recordingSeconds % 60
                Text(
                    text = String.format("%02d:%02d", min, sec),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) MaterialTheme.colorScheme.error else LenoPrimary)
                        .clickable {
                            if (isRecording) {
                                val res = audioRecorder.stopRecording()
                                isRecording = false
                                recordedFile = res.getOrNull()
                            } else {
                                val res = audioRecorder.startRecording()
                                if (res.isSuccess) {
                                    isRecording = true
                                    recordedFile = null
                                } else {
                                    Toast.makeText(context, "Recording failed to start", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop" else "Record",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = when {
                        isRecording -> "Recording... Tap red square to stop"
                        recordedFile != null -> "Recording ready! Listen or attach to lesson."
                        else -> "Tap mic to start recording voice explanation"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (recordedFile != null && !isRecording) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlayingPreview) {
                                        audioPlayer.stop()
                                        isPlayingPreview = false
                                    } else {
                                        isPlayingPreview = true
                                        audioPlayer.playAudio(
                                            filePathOrUri = recordedFile?.absolutePath,
                                            expectedDurationSeconds = recordingSeconds,
                                            onProgress = { _, _ -> },
                                            onCompletion = { isPlayingPreview = false },
                                            onError = { isPlayingPreview = false }
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Preview",
                                    tint = LenoPrimary
                                )
                            }
                            Text(
                                text = "Preview recorded audio",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val file = recordedFile
                    if (file != null && file.exists()) {
                        onSaveVoiceNote(file.absolutePath, recordingSeconds.coerceAtLeast(1))
                    } else {
                        Toast.makeText(context, "No audio recorded", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = recordedFile != null && !isRecording,
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary)
            ) {
                Text("Attach Voice Note", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRecording) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun QuizCreatorModal(
    initialQuiz: ComposerMediaAttachment.QuizAttachment? = null,
    onDismiss: () -> Unit,
    onInsertQuiz: (question: String, options: List<String>, correctIndex: Int) -> Unit
) {
    var questionText by remember(initialQuiz?.id) { mutableStateOf(initialQuiz?.question ?: "") }
    var opt0 by remember(initialQuiz?.id) { mutableStateOf(initialQuiz?.options?.getOrNull(0) ?: "") }
    var opt1 by remember(initialQuiz?.id) { mutableStateOf(initialQuiz?.options?.getOrNull(1) ?: "") }
    var opt2 by remember(initialQuiz?.id) { mutableStateOf(initialQuiz?.options?.getOrNull(2) ?: "") }
    var opt3 by remember(initialQuiz?.id) { mutableStateOf(initialQuiz?.options?.getOrNull(3) ?: "") }
    var correctIndex by remember(initialQuiz?.id) { mutableIntStateOf(initialQuiz?.correctIndex ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Poll, contentDescription = null, tint = LenoPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (initialQuiz != null) "Edit Quiz" else "Create Quiz / Check",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("Question") },
                    placeholder = { Text("What is the chemical formula for water?") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Options (select radio for correct answer):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                val options = listOf(opt0, opt1, opt2, opt3)
                val setters = listOf<(String) -> Unit>(
                    { opt0 = it },
                    { opt1 = it },
                    { opt2 = it },
                    { opt3 = it }
                )

                options.forEachIndexed { index, optVal ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = correctIndex == index,
                            onClick = { correctIndex = index },
                            colors = RadioButtonDefaults.colors(selectedColor = LenoPrimary)
                        )
                        OutlinedTextField(
                            value = optVal,
                            onValueChange = setters[index],
                            label = { Text("Option ${('A'.code + index).toChar()}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (questionText.isBlank()) return@Button
                    val opts = listOf(
                        opt0.ifBlank { "Option A" },
                        opt1.ifBlank { "Option B" },
                        opt2.ifBlank { "Option C" },
                        opt3.ifBlank { "Option D" }
                    )
                    onInsertQuiz(questionText.trim(), opts, correctIndex)
                },
                enabled = questionText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary)
            ) {
                Text(if (initialQuiz != null) "Update Quiz" else "Attach Quiz", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class DrawingLine(
    val path: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
internal fun WhiteboardCanvasModal(
    onDismiss: () -> Unit,
    onSaveFile: (file: File) -> Unit
) {
    val context = LocalContext.current
    val lines = remember { mutableStateListOf<DrawingLine>() }
    var currentLine by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var strokeColor by remember { mutableStateOf(Color.Black) }
    val strokeWidth = 6f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Draw, contentDescription = null, tint = LenoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Teacher Whiteboard",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFFAFAFA))
                        .pointerInput(strokeColor, strokeWidth) {
                            detectDragGestures(
                                onDragStart = { offset -> currentLine = listOf(offset) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentLine = currentLine + change.position
                                },
                                onDragEnd = {
                                    if (currentLine.isNotEmpty()) {
                                        lines.add(DrawingLine(currentLine, strokeColor, strokeWidth))
                                        currentLine = emptyList()
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        lines.forEach { line ->
                            if (line.path.size > 1) {
                                for (k in 0 until line.path.size - 1) {
                                    drawLine(
                                        color = line.color,
                                        start = line.path[k],
                                        end = line.path[k + 1],
                                        strokeWidth = line.strokeWidth,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                        if (currentLine.size > 1) {
                            for (k in 0 until currentLine.size - 1) {
                                drawLine(
                                    color = strokeColor,
                                    start = currentLine[k],
                                    end = currentLine[k + 1],
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Color.Black,
                            LenoPrimary,
                            Color(0xFFEF4444),
                            Color(0xFF3B82F6),
                            Color(0xFFF59E0B)
                        ).forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (strokeColor == col) 2.5.dp else 0.dp,
                                        color = if (strokeColor == col) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { strokeColor = col }
                            )
                        }
                    }

                    TextButton(onClick = { lines.clear(); currentLine = emptyList() }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (lines.isEmpty()) {
                                Toast.makeText(context, "Draw something on the board first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            try {
                                val width = 900
                                val height = 640
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                canvas.drawColor(android.graphics.Color.WHITE)

                                val paint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    strokeCap = android.graphics.Paint.Cap.ROUND
                                }

                                lines.forEach { line ->
                                    paint.color = android.graphics.Color.argb(
                                        (line.color.alpha * 255).toInt(),
                                        (line.color.red * 255).toInt(),
                                        (line.color.green * 255).toInt(),
                                        (line.color.blue * 255).toInt()
                                    )
                                    paint.strokeWidth = line.strokeWidth * 1.5f
                                    for (k in 0 until line.path.size - 1) {
                                        canvas.drawLine(
                                            line.path[k].x, line.path[k].y,
                                            line.path[k + 1].x, line.path[k + 1].y,
                                            paint
                                        )
                                    }
                                }

                                val file = File(context.cacheDir, "whiteboard_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(file).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                }
                                bitmap.recycle()
                                onSaveFile(file)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to save drawing: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Save Diagram", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonTemplatesBottomSheet(
    currentProse: String,
    onDismiss: () -> Unit,
    onApplyProse: (String) -> Unit,
    onInsertQuiz: (question: String, options: List<String>, correctIdx: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val templates = remember {
        listOf(
            Triple(
                "Standard Lesson Structure",
                "Overview, key concepts, worked example, and summary note",
                "## Overview\nExplain what this topic is and why it matters.\n\n## Key Concepts\n• **Main Idea 1** — clear definition\n• **Main Idea 2** — practical rule\n\n## Worked Example\nWalk through one clear step-by-step problem.\n\n> Summary Takeaway: Remember the core rule before attempting the quiz."
            ),
            Triple(
                "STEM / Science & Maths Guide",
                "Formula block, variables breakdown, worked solution, and check",
                "## Core Principle\nIntroduce the law or formula clearly.\n\nformula = y = mx + c\n\n## Variable Guide\n• **y** — dependent quantity\n• **m** — rate of change (gradient)\n• **c** — initial intercept"
            ),
            Triple(
                "Quick Recap & Self-Check",
                "Bullet checklist and takeaway note",
                "## Quick Recap\n• First key point students must remember\n• Second common mistake to avoid\n\n> Tip: Review your notes aloud or ask a question in Q&A if anything is unclear."
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    tint = LenoPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lesson Templates & Formatting",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Insert clean course layouts or tidy up line capitalization.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            templates.forEachIndexed { idx, (title, subtitle, body) ->
                Surface(
                    onClick = {
                        val merged = if (currentProse.isBlank()) body else "$currentProse\n\n$body"
                        onApplyProse(merged)
                        if (idx == 1) {
                            onInsertQuiz(
                                "What does 'm' represent in y = mx + c?",
                                listOf("Y-intercept", "Gradient / Slope", "Constant term", "Origin"),
                                1
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (currentProse.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        val cleaned = currentProse.lines().joinToString("\n") { line ->
                            if (line.isNotEmpty() && line[0].isLowerCase()) {
                                line.replaceFirstChar { it.uppercase() }
                            } else {
                                line
                            }
                        }
                        onApplyProse(cleaned)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Auto-Capitalize Sentence Starts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonInsertGridSheet(
    onDismiss: () -> Unit,
    onInsertFormula: () -> Unit,
    onInsertNote: () -> Unit,
    onInsertVideo: () -> Unit,
    onInsertVoice: () -> Unit,
    onInsertWhiteboard: () -> Unit,
    onInsertFile: () -> Unit,
    onInsertCode: (language: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCodeLanguageChooser by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (showCodeLanguageChooser) "Choose Code Language" else "Insert Lesson Block",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (showCodeLanguageChooser) {
                val languages = listOf("kotlin", "python", "javascript", "java", "html", "sql", "text")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    languages.chunked(3).forEach { rowLangs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowLangs.forEach { lang ->
                                Surface(
                                    onClick = { onInsertCode(lang) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = lang.uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                    )
                                }
                            }
                            repeat(3 - rowLangs.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                TextButton(onClick = { showCodeLanguageChooser = false }) {
                    Text("Back to Insert Blocks")
                }
            } else {
                val items = listOf(
                    Triple(Icons.Default.Functions, "Formula / Rule", "Highlighted equation block") to onInsertFormula,
                    Triple(Icons.Default.FormatQuote, "Key Study Note", "Callout takeaway box") to onInsertNote,
                    Triple(Icons.Default.Videocam, "Video Link", "YouTube, Vimeo or web video") to onInsertVideo,
                    Triple(Icons.Default.Mic, "Voice Note", "Record audio explanation") to onInsertVoice,
                    Triple(Icons.Default.Draw, "Whiteboard", "Sketch diagram & save") to onInsertWhiteboard,
                    Triple(Icons.Default.AttachFile, "Attach Document", "PDF, slides or notes file") to onInsertFile,
                    Triple(Icons.Default.Code, "Code Snippet", "Formatted syntax block") to { showCodeLanguageChooser = true }
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { (meta, action) ->
                                InsertGridTile(
                                    icon = meta.first,
                                    title = meta.second,
                                    subtitle = meta.third,
                                    onClick = action,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun InsertGridTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalLenoDarkTheme.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isDark) LenoPrimary.copy(alpha = 0.22f) else LenoPrimaryVeryLight,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDark) LenoPrimary else LenoPrimaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonModulePickerSheet(
    moduleOptions: List<String>,
    selectedModule: String,
    onSelectModule: (String) -> Unit,
    onCreateModule: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreateField by remember { mutableStateOf(false) }
    var newModuleName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Curriculum Module / Section",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Group this lesson cleanly under a course module or keep Auto Group.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            moduleOptions.forEach { mod ->
                val isSelected = mod == selectedModule
                val displayName = if (mod == "Auto") "Auto Group (Recommended)" else mod
                Surface(
                    onClick = { onSelectModule(mod) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) LenoPrimaryVeryLight.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) LenoPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = LenoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (showCreateField) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newModuleName,
                        onValueChange = { newModuleName = it },
                        label = { Text("New Module Name") },
                        placeholder = { Text("e.g. Module 1: Foundations") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val clean = newModuleName.trim()
                            if (clean.isNotBlank()) {
                                onCreateModule(clean)
                            }
                        },
                        enabled = newModuleName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary)
                    ) {
                        Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showCreateField = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = LenoPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create New Module", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
