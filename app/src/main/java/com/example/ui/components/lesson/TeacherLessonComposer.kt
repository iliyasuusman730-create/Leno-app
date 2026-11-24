package com.example.ui.components.lesson

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.entity.LessonEntity
import com.example.data.service.FirebaseStorageService
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.ui.theme.LocalLenoDarkTheme
import com.example.util.AudioPlayerHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val DRAFT_PREFS_NAME = "leno_lesson_drafts"

private fun resolveUriDisplayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    } catch (_: Exception) {
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "document.pdf" } ?: "document.pdf"
}

@Composable
fun TeacherLessonComposer(
    classId: String = "",
    classTitle: String,
    initialLesson: LessonEntity? = null,
    existingModules: List<String> = emptyList(),
    isPublishing: Boolean = false,
    onDismiss: () -> Unit,
    onPublish: (title: String, content: String, summary: String, clearDraft: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalLenoDarkTheme.current
    val coroutineScope = rememberCoroutineScope()
    val storageService = remember { FirebaseStorageService() }
    val isEditing = initialLesson != null
    val draftPrefs = remember(context) {
        context.getSharedPreferences(DRAFT_PREFS_NAME, Context.MODE_PRIVATE)
    }
    val draftKeyPrefix = remember(classId) {
        if (classId.isNotBlank()) "draft_${classId}_" else "draft_global_"
    }

    val bracketRegex = remember { Regex("""^\[([^\]]+)]\s*(.*)$""") }
    val parsedInitialSummary = remember(initialLesson?.lessonId, initialLesson?.summary) {
        val raw = initialLesson?.summary?.trim().orEmpty()
        val match = bracketRegex.find(raw)
        if (match != null) {
            match.groupValues[1].trim() to match.groupValues[2].trim()
        } else {
            "Auto" to raw
        }
    }

    val savedDraftAvailable = remember(initialLesson?.lessonId, draftKeyPrefix) {
        if (isEditing) false
        else {
            !draftPrefs.getString("${draftKeyPrefix}title", "").isNullOrBlank() ||
                !draftPrefs.getString("${draftKeyPrefix}content", "").isNullOrBlank() ||
                !draftPrefs.getString("${draftKeyPrefix}summary", "").isNullOrBlank()
        }
    }

    val initialRawContent = remember(initialLesson?.lessonId, draftKeyPrefix) {
        if (isEditing) initialLesson?.content.orEmpty()
        else draftPrefs.getString("${draftKeyPrefix}content", "").orEmpty()
    }
    val initialParsedComposer = remember(initialRawContent) {
        parseRawLessonToComposerState(initialRawContent)
    }

    var showDraftBanner by remember(initialLesson?.lessonId) {
        mutableStateOf(savedDraftAvailable)
    }
    var lessonTitle by remember(initialLesson?.lessonId) {
        mutableStateOf(
            if (isEditing) initialLesson?.title.orEmpty()
            else draftPrefs.getString("${draftKeyPrefix}title", "").orEmpty()
        )
    }
    var lessonSummary by remember(initialLesson?.lessonId) {
        mutableStateOf(
            if (isEditing) parsedInitialSummary.second
            else draftPrefs.getString("${draftKeyPrefix}summary", "").orEmpty()
        )
    }
    var selectedModule by remember(initialLesson?.lessonId) {
        mutableStateOf(
            if (isEditing) parsedInitialSummary.first.ifBlank { "Auto" }
            else draftPrefs.getString("${draftKeyPrefix}module", "Auto").orEmpty().ifBlank { "Auto" }
        )
    }

    var contentTextFieldValue by remember(initialLesson?.lessonId) {
        mutableStateOf(
            TextFieldValue(
                text = initialParsedComposer.proseText,
                selection = TextRange(initialParsedComposer.proseText.length)
            )
        )
    }
    val attachments = remember(initialLesson?.lessonId) {
        mutableStateListOf<ComposerMediaAttachment>().apply {
            addAll(initialParsedComposer.attachments)
        }
    }

    var customModules by remember { mutableStateOf<List<String>>(emptyList()) }
    var isKeyboardVisible by remember { mutableStateOf(false) }
    var editorModeTab by remember { mutableIntStateOf(0) } // 0 = Write, 1 = Preview
    var isUploadingMedia by remember { mutableStateOf(false) }
    var uploadStatusText by remember { mutableStateOf("") }

    var showModuleSheet by remember { mutableStateOf(false) }
    var showInsertGridSheet by remember { mutableStateOf(false) }
    var showVideoModal by remember { mutableStateOf(false) }
    var editingVideoAttachment by remember { mutableStateOf<ComposerMediaAttachment.VideoAttachment?>(null) }
    var showVoiceModal by remember { mutableStateOf(false) }
    var showQuizModal by remember { mutableStateOf(false) }
    var editingQuizAttachment by remember { mutableStateOf<ComposerMediaAttachment.QuizAttachment?>(null) }
    var showWhiteboardModal by remember { mutableStateOf(false) }
    var showTemplatesSheet by remember { mutableStateOf(false) }
    var showUnsavedExitDialog by remember { mutableStateOf(false) }

    val editorScrollState = rememberScrollState()

    val currentSerializedContent = remember(contentTextFieldValue.text, attachments.toList()) {
        serializeComposerState(contentTextFieldValue.text, attachments.toList())
    }
    val stats = remember(contentTextFieldValue.text, attachments.toList()) {
        computeLessonComposerStats(contentTextFieldValue.text, attachments.toList())
    }

    val baselineTitle = if (isEditing) initialLesson?.title.orEmpty() else ""
    val baselineSummary = if (isEditing) parsedInitialSummary.second else ""
    val baselineContent = if (isEditing) initialLesson?.content.orEmpty() else ""
    val hasChanges = lessonTitle.trim() != baselineTitle.trim() ||
        lessonSummary.trim() != baselineSummary.trim() ||
        currentSerializedContent.trim() != baselineContent.trim()

    fun clearDraft() {
        draftPrefs.edit()
            .remove("${draftKeyPrefix}title")
            .remove("${draftKeyPrefix}summary")
            .remove("${draftKeyPrefix}module")
            .remove("${draftKeyPrefix}content")
            .apply()
    }

    // Auto-save draft while creating a new lesson
    LaunchedEffect(lessonTitle, lessonSummary, selectedModule, currentSerializedContent, isEditing) {
        if (!isEditing) {
            delay(450)
            if (lessonTitle.isBlank() && lessonSummary.isBlank() && currentSerializedContent.isBlank()) {
                clearDraft()
            } else {
                draftPrefs.edit()
                    .putString("${draftKeyPrefix}title", lessonTitle)
                    .putString("${draftKeyPrefix}summary", lessonSummary)
                    .putString("${draftKeyPrefix}module", selectedModule)
                    .putString("${draftKeyPrefix}content", currentSerializedContent)
                    .apply()
            }
        }
    }

    // Real module list only (no hardcoded Week 1 / Week 2 items)
    val moduleOptions = remember(existingModules, customModules, selectedModule) {
        buildList {
            add("Auto")
            addAll(existingModules.filter { it.isNotBlank() })
            addAll(customModules.filter { it.isNotBlank() })
            if (selectedModule.isNotBlank() && selectedModule != "Auto") {
                add(selectedModule)
            }
        }.distinct()
    }

    fun insertTextAtCursor(textToInsert: String) {
        val currentText = contentTextFieldValue.text
        val selection = contentTextFieldValue.selection
        val start = selection.min.coerceIn(0, currentText.length)
        val end = selection.max.coerceIn(0, currentText.length)
        val newText = currentText.substring(0, start) + textToInsert + currentText.substring(end)
        val newCursor = start + textToInsert.length
        contentTextFieldValue = TextFieldValue(text = newText, selection = TextRange(newCursor))
    }

    fun deleteTextAtCursor() {
        val currentText = contentTextFieldValue.text
        val selection = contentTextFieldValue.selection
        val start = selection.min.coerceIn(0, currentText.length)
        val end = selection.max.coerceIn(0, currentText.length)
        if (start != end) {
            val newText = currentText.substring(0, start) + currentText.substring(end)
            contentTextFieldValue = TextFieldValue(newText, TextRange(start))
        } else if (start > 0) {
            val newText = currentText.substring(0, start - 1) + currentText.substring(start)
            contentTextFieldValue = TextFieldValue(newText, TextRange(start - 1))
        }
    }

    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible) {
            delay(150)
            editorScrollState.animateScrollTo(editorScrollState.maxValue)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showVoiceModal = true
        } else {
            Toast.makeText(context, "Microphone permission is needed to record voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingMedia = true
            uploadStatusText = "Optimizing & uploading image..."
            coroutineScope.launch {
                val res = storageService.uploadLessonImage(uri, context, classId)
                isUploadingMedia = false
                uploadStatusText = ""
                res.onSuccess { uploadedUrl ->
                    attachments.add(
                        ComposerMediaAttachment.ImageAttachment(
                            urlOrPath = uploadedUrl,
                            caption = "Lesson Visual"
                        )
                    )
                    Toast.makeText(context, "Image attached for all students", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Failed to process image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = resolveUriDisplayName(context, uri)
            isUploadingMedia = true
            uploadStatusText = "Attaching $fileName..."
            coroutineScope.launch {
                val res = storageService.uploadLessonFile(uri, context, fileName, classId)
                isUploadingMedia = false
                uploadStatusText = ""
                res.onSuccess { fileUrlOrData ->
                    attachments.add(
                        ComposerMediaAttachment.FileAttachment(
                            fileName = fileName,
                            filePath = fileUrlOrData
                        )
                    )
                    Toast.makeText(context, "Document attached", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Unable to attach document", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun handleCloseRequest() {
        when {
            isPublishing -> Unit
            isKeyboardVisible -> isKeyboardVisible = false
            hasChanges -> showUnsavedExitDialog = true
            else -> onDismiss()
        }
    }

    BackHandler(onBack = ::handleCloseRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
            .testTag("teacher_composer_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // === 1. MODERN LESSON STUDIO HEADER ===
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("composer_header"),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = ::handleCloseRequest,
                                enabled = !isPublishing,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("composer_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text(
                                    text = if (isEditing) "Edit Lesson" else "Lesson Studio",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = classTitle.ifBlank { "Online Class" },
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val cleanTitle = lessonTitle.trim()
                                val cleanContent = currentSerializedContent.trim()
                                if (cleanTitle.isBlank()) {
                                    Toast.makeText(context, "Please enter a lesson title", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (cleanContent.isBlank()) {
                                    Toast.makeText(context, "Add lesson text or an attachment before publishing", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val fallbackSummary = contentTextFieldValue.text.trim().lines()
                                    .firstOrNull { line ->
                                        val t = line.trim()
                                        t.isNotBlank() && !t.startsWith("[") && !t.startsWith("#") && !t.startsWith("```")
                                    }
                                    ?.take(120)
                                    ?: cleanTitle
                                val cleanSummary = lessonSummary.trim().ifBlank { fallbackSummary }
                                val finalSummary = if (selectedModule != "Auto") {
                                    "[$selectedModule] $cleanSummary"
                                } else {
                                    cleanSummary
                                }
                                onPublish(cleanTitle, cleanContent, finalSummary, ::clearDraft)
                            },
                            enabled = !isPublishing && !isUploadingMedia,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LenoPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 42.dp)
                                .testTag("composer_publish_button")
                        ) {
                            if (isPublishing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Saving...",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isEditing) "Save" else "Publish",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (isUploadingMedia || isPublishing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.5.dp),
                            color = LenoPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    }
                }
            }

            // === 2. SCROLLABLE WRITING STUDIO ===
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(editorScrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showDraftBanner && !isEditing) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) LenoPrimary.copy(alpha = 0.16f) else LenoPrimaryVeryLight,
                        border = BorderStroke(1.dp, LenoPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Unfinished draft restored",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    clearDraft()
                                    lessonTitle = ""
                                    lessonSummary = ""
                                    selectedModule = "Auto"
                                    contentTextFieldValue = TextFieldValue("")
                                    attachments.clear()
                                    showDraftBanner = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Clear Draft", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                if (isUploadingMedia && uploadStatusText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LenoPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uploadStatusText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Compact Single-Row Metadata & Helpers Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { showModuleSheet = true },
                        shape = RoundedCornerShape(50),
                        color = if (selectedModule != "Auto") {
                            if (isDark) LenoPrimary.copy(alpha = 0.2f) else LenoPrimaryVeryLight
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selectedModule != "Auto") LenoPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = LenoPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (selectedModule == "Auto") "Module: Auto" else selectedModule,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "Choose module",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { showTemplatesSheet = true },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                        modifier = Modifier.testTag("composer_templates_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = null,
                                tint = LenoPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Templates",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        onClick = { isKeyboardVisible = !isKeyboardVisible },
                        shape = RoundedCornerShape(50),
                        color = if (isKeyboardVisible) {
                            if (isDark) LenoPrimary.copy(alpha = 0.22f) else LenoPrimaryVeryLight
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isKeyboardVisible) LenoPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isKeyboardVisible) Icons.Default.Close else Icons.Default.Functions,
                                contentDescription = "Toggle Math & Science Symbols Pad",
                                tint = if (isKeyboardVisible) LenoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isKeyboardVisible) "Hide Pad" else "Symbols",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isKeyboardVisible) LenoPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Editorial Lesson Title + Learning Goal Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Lesson Title",
                                tint = LenoPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                            TextField(
                                value = lessonTitle,
                                onValueChange = { lessonTitle = it },
                                placeholder = {
                                    Text(
                                        text = "Lesson Title (e.g. Quadratic Equations)",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    )
                                },
                                textStyle = TextStyle(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("composer_title_input")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = "Learning Goal",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            TextField(
                                value = lessonSummary,
                                onValueChange = { lessonSummary = it },
                                placeholder = {
                                    Text(
                                        text = "Learning goal / short summary for students...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                    )
                                },
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 2,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("composer_summary_input")
                            )
                        }
                    }
                }

                // Mode Switcher (Write vs Preview) + Live Lesson Stats Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            listOf(0 to Pair("Write", Icons.Default.Edit), 1 to Pair("Preview", Icons.Default.Visibility)).forEach { (idx, meta) ->
                                val selected = editorModeTab == idx
                                Surface(
                                    onClick = { editorModeTab = idx },
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shadowElevation = if (selected) 1.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = meta.second,
                                            contentDescription = null,
                                            tint = if (selected) LenoPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = meta.first,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = LenoPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val statsLabel = buildString {
                                append(if (stats.estimatedMinutes == 0) "0 min" else "~${stats.estimatedMinutes} min")
                                append(" · ${stats.blockCount} blocks")
                                if (stats.quizCount > 0) append(" · ${stats.quizCount} quiz")
                            }
                            Text(
                                text = statsLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // === 3. MAIN PROSE WRITING / LIVE PREVIEW CANVAS ===
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("composer_main_editor_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (editorModeTab == 0) {
                            TextField(
                                value = contentTextFieldValue,
                                onValueChange = { contentTextFieldValue = it },
                                placeholder = {
                                    Text(
                                        text = "Write your lesson clearly for students...\n\n" +
                                            "Tips:\n" +
                                            "• ## Section Heading\n" +
                                            "• **Highlight key terms**\n" +
                                            "• > Important study note\n" +
                                            "• formula = E = mc²\n" +
                                            "• Highlight text and tap Bold or Heading below to wrap it automatically.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                        lineHeight = 22.sp
                                    )
                                },
                                textStyle = TextStyle(
                                    fontSize = 15.5.sp,
                                    lineHeight = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 240.dp)
                                    .testTag("composer_content_input")
                            )
                        } else {
                            if (currentSerializedContent.isBlank()) {
                                Text(
                                    text = "Nothing to preview yet. Switch to Write or pick a template to start building your lesson.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp)
                                )
                            } else {
                                LessonContentRenderer(
                                    content = currentSerializedContent,
                                    isInteractive = true
                                )
                            }
                        }
                    }
                }

                // === VISUAL ATTACHMENT CARDS (IMAGES, QUIZZES, VOICE, VIDEO, DOCS) ===
                if (editorModeTab == 0 && attachments.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "ATTACHED LESSON BLOCKS (${attachments.size})",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        attachments.forEach { item ->
                            ComposerAttachmentCard(
                                attachment = item,
                                onEditQuiz = { quiz ->
                                    editingQuizAttachment = quiz
                                    showQuizModal = true
                                },
                                onEditVideo = { video ->
                                    editingVideoAttachment = video
                                    showVideoModal = true
                                },
                                onRemove = {
                                    attachments.removeAll { it.id == item.id }
                                }
                            )
                        }
                    }
                }
            }

            // === 4. THEME-MATCHED BOTTOM STUDIO TOOLBAR (5 QUICK TOOLS + "+ INSERT") ===
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("composer_floating_toolbar"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.85f else 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarPillButton(icon = Icons.Default.Title, label = "Heading") {
                        contentTextFieldValue = applySelectionFormatting(
                            value = contentTextFieldValue,
                            prefix = if (contentTextFieldValue.selection.min == 0) "## " else "\n## ",
                            suffix = "",
                            placeholder = "Section Title"
                        )
                    }
                    ToolbarPillButton(icon = Icons.Default.FormatBold, label = "Bold") {
                        contentTextFieldValue = applySelectionFormatting(
                            value = contentTextFieldValue,
                            prefix = "**",
                            suffix = "**",
                            placeholder = "key term"
                        )
                    }
                    ToolbarPillButton(icon = Icons.AutoMirrored.Filled.FormatListBulleted, label = "List") {
                        contentTextFieldValue = applySelectionFormatting(
                            value = contentTextFieldValue,
                            prefix = if (contentTextFieldValue.selection.min == 0) "• " else "\n• ",
                            suffix = "",
                            placeholder = "Point"
                        )
                    }
                    ToolbarPillButton(icon = Icons.Default.Image, label = "Image") {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    ToolbarPillButton(icon = Icons.Default.Poll, label = "Quiz") {
                        editingQuizAttachment = null
                        showQuizModal = true
                    }

                    Surface(
                        onClick = { showInsertGridSheet = true },
                        shape = RoundedCornerShape(50),
                        color = LenoPrimary,
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "More insert options",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Insert",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // === 5. OPTIONAL STEM SYMBOLS PAD ===
            AnimatedVisibility(
                visible = isKeyboardVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                LenoKeyboard(
                    onInsertText = { insertTextAtCursor(it) },
                    onBackspace = { deleteTextAtCursor() },
                    onEnter = { insertTextAtCursor("\n") }
                )
            }
        }

        // === SHEETS & MODALS ===
        if (showModuleSheet) {
            LessonModulePickerSheet(
                moduleOptions = moduleOptions,
                selectedModule = selectedModule,
                onSelectModule = {
                    selectedModule = it
                    showModuleSheet = false
                },
                onCreateModule = { created ->
                    customModules = (customModules + created).distinct()
                    selectedModule = created
                    showModuleSheet = false
                },
                onDismiss = { showModuleSheet = false }
            )
        }

        if (showInsertGridSheet) {
            LessonInsertGridSheet(
                onDismiss = { showInsertGridSheet = false },
                onInsertFormula = {
                    showInsertGridSheet = false
                    contentTextFieldValue = applySelectionFormatting(
                        value = contentTextFieldValue,
                        prefix = "\nformula = ",
                        suffix = "",
                        placeholder = "a² + b² = c²"
                    )
                },
                onInsertNote = {
                    showInsertGridSheet = false
                    contentTextFieldValue = applySelectionFormatting(
                        value = contentTextFieldValue,
                        prefix = "\n> ",
                        suffix = "",
                        placeholder = "Key takeaway for students"
                    )
                },
                onInsertVideo = {
                    showInsertGridSheet = false
                    editingVideoAttachment = null
                    showVideoModal = true
                },
                onInsertVoice = {
                    showInsertGridSheet = false
                    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) showVoiceModal = true else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onInsertWhiteboard = {
                    showInsertGridSheet = false
                    showWhiteboardModal = true
                },
                onInsertFile = {
                    showInsertGridSheet = false
                    filePickerLauncher.launch("*/*")
                },
                onInsertCode = { lang ->
                    showInsertGridSheet = false
                    contentTextFieldValue = applySelectionFormatting(
                        value = contentTextFieldValue,
                        prefix = "\n```$lang\n",
                        suffix = "\n```\n",
                        placeholder = "// Write $lang code here"
                    )
                }
            )
        }

        if (showVideoModal) {
            VideoInsertModal(
                initialUrl = editingVideoAttachment?.videoUrl.orEmpty(),
                initialTitle = editingVideoAttachment?.title.orEmpty(),
                onDismiss = {
                    showVideoModal = false
                    editingVideoAttachment = null
                },
                onInsertVideo = { url, title ->
                    val editing = editingVideoAttachment
                    if (editing != null) {
                        val idx = attachments.indexOfFirst { it.id == editing.id }
                        if (idx >= 0) {
                            attachments[idx] = editing.copy(videoUrl = url, title = title)
                        }
                    } else {
                        attachments.add(ComposerMediaAttachment.VideoAttachment(videoUrl = url, title = title))
                    }
                    showVideoModal = false
                    editingVideoAttachment = null
                }
            )
        }

        if (showVoiceModal) {
            VoiceNoteRecorderModal(
                onDismiss = { showVoiceModal = false },
                onSaveVoiceNote = { filePath, durationSec ->
                    showVoiceModal = false
                    isUploadingMedia = true
                    uploadStatusText = "Uploading voice explanation..."
                    coroutineScope.launch {
                        val file = File(filePath)
                        val res = storageService.uploadVoiceNoteFile(file, classId)
                        isUploadingMedia = false
                        uploadStatusText = ""
                        res.onSuccess { audioUrl ->
                            attachments.add(
                                ComposerMediaAttachment.VoiceAttachment(
                                    audioUrl = audioUrl,
                                    durationSec = durationSec
                                )
                            )
                            Toast.makeText(context, "Voice explanation attached", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "Failed to attach voice note", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        if (showQuizModal) {
            QuizCreatorModal(
                initialQuiz = editingQuizAttachment,
                onDismiss = {
                    showQuizModal = false
                    editingQuizAttachment = null
                },
                onInsertQuiz = { question, options, correctIdx ->
                    val editing = editingQuizAttachment
                    if (editing != null) {
                        val idx = attachments.indexOfFirst { it.id == editing.id }
                        if (idx >= 0) {
                            attachments[idx] = editing.copy(
                                question = question,
                                options = options,
                                correctIndex = correctIdx
                            )
                        }
                    } else {
                        attachments.add(
                            ComposerMediaAttachment.QuizAttachment(
                                question = question,
                                options = options,
                                correctIndex = correctIdx
                            )
                        )
                    }
                    showQuizModal = false
                    editingQuizAttachment = null
                }
            )
        }

        if (showWhiteboardModal) {
            WhiteboardCanvasModal(
                onDismiss = { showWhiteboardModal = false },
                onSaveFile = { savedFile ->
                    showWhiteboardModal = false
                    isUploadingMedia = true
                    uploadStatusText = "Uploading whiteboard diagram..."
                    coroutineScope.launch {
                        val res = storageService.uploadLessonImageFile(savedFile, classId)
                        isUploadingMedia = false
                        uploadStatusText = ""
                        res.onSuccess { uploadedUrl ->
                            attachments.add(
                                ComposerMediaAttachment.ImageAttachment(
                                    urlOrPath = uploadedUrl,
                                    caption = "Whiteboard Diagram"
                                )
                            )
                            Toast.makeText(context, "Whiteboard diagram attached", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "Failed to save diagram", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        if (showTemplatesSheet) {
            LessonTemplatesBottomSheet(
                currentProse = contentTextFieldValue.text,
                onDismiss = { showTemplatesSheet = false },
                onApplyProse = { updatedProse ->
                    contentTextFieldValue = TextFieldValue(updatedProse, TextRange(updatedProse.length))
                    showTemplatesSheet = false
                },
                onInsertQuiz = { q, opts, correct ->
                    attachments.add(
                        ComposerMediaAttachment.QuizAttachment(
                            question = q,
                            options = opts,
                            correctIndex = correct
                        )
                    )
                }
            )
        }

        if (showUnsavedExitDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedExitDialog = false },
                title = {
                    Text(
                        text = if (isEditing) "Discard lesson edits?" else "Leave Lesson Studio?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = if (isEditing) {
                            "You have unsaved changes to this lesson."
                        } else {
                            "Your progress is saved as a draft so you can resume anytime, or you can discard it."
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnsavedExitDialog = false
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary)
                    ) {
                        Text(if (isEditing) "Discard" else "Save Draft & Exit", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!isEditing) {
                            TextButton(
                                onClick = {
                                    clearDraft()
                                    showUnsavedExitDialog = false
                                    onDismiss()
                                }
                            ) {
                                Text("Discard", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { showUnsavedExitDialog = false }) {
                            Text("Keep Editing")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ToolbarPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ComposerAttachmentCard(
    attachment: ComposerMediaAttachment,
    onEditQuiz: (ComposerMediaAttachment.QuizAttachment) -> Unit,
    onEditVideo: (ComposerMediaAttachment.VideoAttachment) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalLenoDarkTheme.current
    val audioPlayer = remember { AudioPlayerHelper(context) }
    var isPlayingVoice by remember { mutableStateOf(false) }

    DisposableEffect(attachment.id) {
        onDispose { audioPlayer.stop() }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = attachment is ComposerMediaAttachment.QuizAttachment || attachment is ComposerMediaAttachment.VideoAttachment
                ) {
                    when (attachment) {
                        is ComposerMediaAttachment.QuizAttachment -> onEditQuiz(attachment)
                        is ComposerMediaAttachment.VideoAttachment -> onEditVideo(attachment)
                        else -> Unit
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (attachment) {
                is ComposerMediaAttachment.ImageAttachment -> {
                    val isLocal = !attachment.urlOrPath.startsWith("http") && !attachment.urlOrPath.startsWith("data:")
                    val model = if (isLocal && File(attachment.urlOrPath).exists()) File(attachment.urlOrPath) else attachment.urlOrPath
                    AsyncImage(
                        model = model,
                        contentDescription = attachment.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.caption.ifBlank { "Visual Diagram" },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Image block • Ready for students",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ComposerMediaAttachment.VideoAttachment -> {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.title.ifBlank { "Video Lesson" },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${attachment.videoUrl} • Tap to edit",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                is ComposerMediaAttachment.VoiceAttachment -> {
                    Surface(
                        onClick = {
                            if (isPlayingVoice) {
                                audioPlayer.stop()
                                isPlayingVoice = false
                            } else {
                                isPlayingVoice = true
                                audioPlayer.playAudio(
                                    filePathOrUri = attachment.audioUrl,
                                    expectedDurationSeconds = attachment.durationSec,
                                    onProgress = { _, _ -> },
                                    onCompletion = { isPlayingVoice = false },
                                    onError = { isPlayingVoice = false }
                                )
                            }
                        },
                        shape = CircleShape,
                        color = LenoPrimary,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlayingVoice) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Preview Voice Note",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Explanation (${attachment.durationSec}s)",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isPlayingVoice) "Playing preview..." else "Tap play to preview audio",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ComposerMediaAttachment.QuizAttachment -> {
                    Surface(
                        shape = CircleShape,
                        color = if (isDark) LenoPrimary.copy(alpha = 0.2f) else LenoPrimaryVeryLight,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Poll, contentDescription = null, tint = LenoPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.question,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${attachment.options.size} options • Tap to edit",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ComposerMediaAttachment.FileAttachment -> {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.fileName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Attached document • Ready for students",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
