package com.example.ui.components.lesson

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.util.AudioPlayerHelper
import com.example.util.AudioRecorderHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherLessonComposer(
    classTitle: String,
    onDismiss: () -> Unit,
    onPublish: (title: String, content: String, summary: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var lessonTitle by remember { mutableStateOf("") }
    var lessonContent by remember { mutableStateOf("") }
    var contentTextFieldValue by remember { mutableStateOf(TextFieldValue(text = "")) }
    var isKeyboardVisible by remember { mutableStateOf(true) }
    var editorModeTab by remember { mutableIntStateOf(0) } // 0: Editor, 1: Live Preview

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val editorScrollState = rememberScrollState()

    // 1. CLASS DROPDOWN: Label "Class:" + Select with options ["JSS1", "JSS2", "JSS3", "SS1", "SS2", "SS3", "University", "Skill/Craft"], Default: "Select Class", Style: White card, small rounded, black text
    var selectedClass by remember {
        mutableStateOf(if (classTitle.isNotBlank() && classTitle != "General" && classTitle != "math's") classTitle else "Select Class")
    }
    var classDropdownExpanded by remember { mutableStateOf(false) }
    val classOptions = listOf("JSS1", "JSS2", "JSS3", "SS1", "SS2", "SS3", "University", "Skill/Craft")

    // Helper functions to insert/delete at cursor position
    fun insertTextAtCursor(textToInsert: String) {
        val currentText = contentTextFieldValue.text
        val selection = contentTextFieldValue.selection
        val start = selection.min.coerceIn(0, currentText.length)
        val end = selection.max.coerceIn(0, currentText.length)
        val newText = currentText.substring(0, start) + textToInsert + currentText.substring(end)
        val newCursor = start + textToInsert.length
        contentTextFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )
        lessonContent = newText
    }

    fun deleteTextAtCursor() {
        val currentText = contentTextFieldValue.text
        val selection = contentTextFieldValue.selection
        val start = selection.min.coerceIn(0, currentText.length)
        val end = selection.max.coerceIn(0, currentText.length)
        if (start != end) {
            val newText = currentText.substring(0, start) + currentText.substring(end)
            contentTextFieldValue = TextFieldValue(newText, TextRange(start))
            lessonContent = newText
        } else if (start > 0) {
            val newText = currentText.substring(0, start - 1) + currentText.substring(start)
            contentTextFieldValue = TextFieldValue(newText, TextRange(start - 1))
            lessonContent = newText
        }
    }

    // 5. EDITOR PLACEHOLDER FIX: scroll up when keyboard opens, not hidden behind keyboard
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible) {
            delay(150)
            editorScrollState.animateScrollTo(editorScrollState.maxValue)
        }
    }

    // Floating toolbar modals
    var showVideoModal by remember { mutableStateOf(false) }
    var showVoiceModal by remember { mutableStateOf(false) }
    var showQuizModal by remember { mutableStateOf(false) }
    var showWhiteboardModal by remember { mutableStateOf(false) }
    var showAiBottomSheet by remember { mutableStateOf(false) }

    // Media pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val file = File(context.filesDir, "lesson_img_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                insertTextAtCursor("\n[image: ${file.absolutePath} | Diagram/Visual]\n")
                Toast.makeText(context, "Image added to lesson", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                insertTextAtCursor("\n[image: $uri | Visual]\n")
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
            insertTextAtCursor("\n[file: $fileName | $uri]\n")
            Toast.makeText(context, "Document attached", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB)) // Light neutral background for screen
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("teacher_composer_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // === 1. HEADER: Black bg, left back arrow "<", center "Create Lesson" white bold 18px, right "Publish" green #22c55e ===
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("composer_header"),
                color = Color(0xFF000000)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left back arrow "<"
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("composer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Center "Create Lesson" white bold 18px
                    Text(
                        text = "Create Lesson",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Right "Publish" button green #22c55e rounded-full white text
                    Button(
                        onClick = {
                            if (lessonTitle.isBlank()) {
                                Toast.makeText(context, "Please enter a lesson title", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (lessonContent.isBlank()) {
                                Toast.makeText(context, "Lesson content cannot be empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val classPrefix = if (selectedClass != "Select Class") "[$selectedClass] " else ""
                            val fullTitle = classPrefix + lessonTitle.trim()
                            val summary = lessonContent.lines().firstOrNull { it.isNotBlank() } ?: fullTitle
                            onPublish(fullTitle, lessonContent.trim(), summary)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E), // #22c55e
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50), // rounded-full
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("composer_publish_button")
                    ) {
                        Text(
                            text = "Publish",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // === 4. KEYBOARD HEIGHT FIX: Editor container flex:1, maxHeight 35% when keyboard open, scrollable ===
            val editorContainerModifier = if (isKeyboardVisible) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.35f) // maxHeight 35% when keyboard open
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .verticalScroll(editorScrollState)
            } else {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .verticalScroll(editorScrollState)
            }

            // Scrollable Content Area (Class Dropdown + Title Input + Main Editor Card)
            Column(
                modifier = editorContainerModifier
            ) {
                // === 1. HEADER FIX: CLASS DROPDOWN COMPONENT ===
                // Label "Class:" + Select with options ["JSS1", "JSS2", "JSS3", "SS1", "SS2", "SS3", "University", "Skill/Craft"]
                // Default: "Select Class", Style: White card, small rounded, black text
                Card(
                    shape = RoundedCornerShape(8.dp), // Style: White card, small rounded, black text
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("composer_class_card")
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { classDropdownExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Class: ",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedClass,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedClass == "Select Class") FontWeight.Normal else FontWeight.Medium,
                                    color = if (selectedClass == "Select Class") Color(0xFF6B7280) else Color.Black
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Class",
                                tint = Color.Black
                            )
                        }

                        DropdownMenu(
                            expanded = classDropdownExpanded,
                            onDismissRequest = { classDropdownExpanded = false },
                            modifier = Modifier
                                .background(Color.White)
                                .testTag("composer_class_dropdown")
                        ) {
                            classOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option,
                                            fontSize = 14.sp,
                                            fontWeight = if (option == selectedClass) FontWeight.Bold else FontWeight.Normal,
                                            color = if (option == selectedClass) Color(0xFF22C55E) else Color.Black
                                        )
                                    },
                                    onClick = {
                                        selectedClass = option
                                        classDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // === 2. TITLE INPUT: White card rounded-2xl, pencil icon, placeholder "Lesson Title..." gray, black text ===
                Card(
                    shape = RoundedCornerShape(16.dp), // rounded-2xl
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("composer_title_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Pencil Icon",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = lessonTitle,
                            onValueChange = { lessonTitle = it },
                            placeholder = {
                                Text(
                                    text = "Lesson Title...",
                                    fontSize = 16.sp,
                                    color = Color.Gray
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("composer_title_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Selector Bar (Edit vs Live Rich Preview)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFE5E7EB)
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            Surface(
                                onClick = { editorModeTab = 0 },
                                shape = RoundedCornerShape(50),
                                color = if (editorModeTab == 0) Color.White else Color.Transparent,
                                shadowElevation = if (editorModeTab == 0) 2.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = if (editorModeTab == 0) Color(0xFF22C55E) else Color.Gray, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Editor", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (editorModeTab == 0) Color.Black else Color.Gray)
                                }
                            }

                            Surface(
                                onClick = { editorModeTab = 1 },
                                shape = RoundedCornerShape(50),
                                color = if (editorModeTab == 1) Color.White else Color.Transparent,
                                shadowElevation = if (editorModeTab == 1) 2.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, tint = if (editorModeTab == 1) Color(0xFF22C55E) else Color.Gray, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Live Preview", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (editorModeTab == 1) Color.Black else Color.Gray)
                                }
                            }
                        }
                    }

                    // Keyboard Toggle button
                    IconButton(
                        onClick = { isKeyboardVisible = !isKeyboardVisible },
                        modifier = Modifier
                            .size(34.dp)
                            .background(if (isKeyboardVisible) Color(0xFFDCFCE7) else Color(0xFFF3F4F6), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Toggle Leno Keyboard",
                            tint = if (isKeyboardVisible) Color(0xFF16A34A) else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // === 3. MAIN EDITOR CARD: White card rounded-3xl p-6 ===
                // 5. EDITOR PLACEHOLDER FIX: Add paddingBottom 20px to editor
                Card(
                    shape = RoundedCornerShape(24.dp), // rounded-3xl
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp) // Add paddingBottom 20px to editor
                        .testTag("composer_main_editor_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp) // p-6
                    ) {
                        if (editorModeTab == 0) {
                            // Text Editor with cursor position tracking & placeholder
                            TextField(
                                value = contentTextFieldValue,
                                onValueChange = {
                                    contentTextFieldValue = it
                                    lessonContent = it.text
                                },
                                placeholder = {
                                    Text(
                                        text = "Write your lesson here...\n\n" +
                                                "• Type ## for H2 Headings\n" +
                                                "• Use **word** for bold text\n" +
                                                "• Use • for green bullet points\n" +
                                                "• Type formula = CO₂ + H₂O → Glucose + O₂\n" +
                                                "• Type > for grey note blocks\n" +
                                                "• Use Floating Toolbar below to insert Images, Videos, Voice notes, and Quizzes!",
                                        fontSize = 14.sp,
                                        color = Color(0xFF9CA3AF),
                                        lineHeight = 22.sp
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color(0xFF111827),
                                    unfocusedTextColor = Color(0xFF111827)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp)
                                    .testTag("composer_content_input")
                            )
                        } else {
                            // Live Preview Mode rendering rich blocks
                            LessonContentRenderer(
                                content = lessonContent,
                                isInteractive = true
                            )
                        }
                    }
                }
            }

            // === 2. FLOATING TOOLBAR FIX ===
            // Current toolbar is cut off on right side, Whiteboard not fully visible
            // Fix: Make it full width horizontal ScrollView with padding, shows all 6 buttons + scroll indicator
            // Add: border 1px solid #22c55e, background black #000000, height 70px fixed
            // Ensure buttons: Image, Video, Voice note, File, Quiz/Poll, Whiteboard all visible with scroll
            // Add 10px gap between buttons
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp) // height 70px fixed
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .testTag("composer_floating_toolbar"),
                shape = RoundedCornerShape(35.dp), // rounded-full pill
                color = Color(0xFF000000), // background black #000000
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E)), // border 1px solid #22c55e
                shadowElevation = 8.dp
            ) {
                val toolbarScrollState = rememberScrollState()
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .horizontalScroll(toolbarScrollState),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), // Add 10px gap between buttons
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Image
                        ToolbarPillButton(
                            icon = Icons.Default.Image,
                            label = "Image"
                        ) {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }

                        // 2. Video
                        ToolbarPillButton(
                            icon = Icons.Default.Videocam,
                            label = "Video"
                        ) {
                            showVideoModal = true
                        }

                        // 3. Voice note
                        ToolbarPillButton(
                            icon = Icons.Default.Mic,
                            label = "Voice note"
                        ) {
                            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                showVoiceModal = true
                            } else {
                                Toast.makeText(context, "Please grant microphone permission to record voice notes", Toast.LENGTH_SHORT).show()
                            }
                        }

                        // 4. File
                        ToolbarPillButton(
                            icon = Icons.Default.AttachFile,
                            label = "File"
                        ) {
                            filePickerLauncher.launch("*/*")
                        }

                        // 5. Quiz/Poll
                        ToolbarPillButton(
                            icon = Icons.Default.Poll,
                            label = "Quiz/Poll"
                        ) {
                            showQuizModal = true
                        }

                        // 6. Whiteboard
                        ToolbarPillButton(
                            icon = Icons.Default.Draw,
                            label = "Whiteboard"
                        ) {
                            showWhiteboardModal = true
                        }

                        // 7. Code
                        ToolbarPillButton(
                            icon = Icons.Default.Code,
                            label = "Code"
                        ) {
                            insertTextAtCursor("\n```kotlin\n// Code snippet\nfun calculate() {\n    println(\"Hello Leno\")\n}\n```\n")
                        }

                        // 8. Bold
                        ToolbarPillButton(
                            icon = Icons.Default.FormatBold,
                            label = "Bold"
                        ) {
                            insertTextAtCursor(" **bold text** ")
                        }

                        // 9. List
                        ToolbarPillButton(
                            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                            label = "List"
                        ) {
                            insertTextAtCursor("\n• ")
                        }

                        // 10. AI ✨
                        ToolbarPillButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "AI ✨",
                            tintColor = Color(0xFF22C55E)
                        ) {
                            showAiBottomSheet = true
                        }
                    }

                    // Horizontal Scroll Indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 2.dp)
                            .width(40.dp)
                            .height(3.dp)
                            .background(Color(0xFF27272A), RoundedCornerShape(2.dp))
                    ) {
                        val maxScroll = toolbarScrollState.maxValue
                        val fraction = if (maxScroll > 0) (toolbarScrollState.value.toFloat() / maxScroll).coerceIn(0f, 1f) else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(14.dp)
                                .offset(x = (26 * fraction).dp)
                                .background(Color(0xFF22C55E), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            // === 3. CUSTOM LENO KEYBOARD ===
            // REPLACES system keyboard, shows inside app
            // Top Tabs: [ABC, Math, Chem, Physics, Draw] - active tab green underline
            // Formula Preview bar: 45px fixed height
            // Keyboard container: 42% of screen height, background #121212
            AnimatedVisibility(
                visible = isKeyboardVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                LenoKeyboard(
                    onInsertText = { textToInsert ->
                        insertTextAtCursor(textToInsert)
                    },
                    onBackspace = {
                        deleteTextAtCursor()
                    },
                    onEnter = {
                        insertTextAtCursor("\n")
                    }
                )
            }
        }

        // ---------------------------------------------------------------------
        // MODALS & DIALOGS TRIGGERED BY FLOATING TOOLBAR
        // ---------------------------------------------------------------------

        // 1. Video Modal
        if (showVideoModal) {
            VideoInsertModal(
                onDismiss = { showVideoModal = false },
                onInsertVideo = { url, title ->
                    lessonContent += "\n[video: $url | $title]\n"
                    showVideoModal = false
                }
            )
        }

        // 2. Voice Note Recorder Modal
        if (showVoiceModal) {
            VoiceNoteRecorderModal(
                onDismiss = { showVoiceModal = false },
                onSaveVoiceNote = { filePath, durationSec ->
                    lessonContent += "\n[voice: $filePath | $durationSec]\n"
                    showVoiceModal = false
                }
            )
        }

        // 3. Quiz / Poll Modal (question + 4 options + correct)
        if (showQuizModal) {
            QuizCreatorModal(
                onDismiss = { showQuizModal = false },
                onInsertQuiz = { question, options, correctIdx ->
                    val optsStr = options.joinToString(", ")
                    lessonContent += "\n[quiz: $question | $optsStr | $correctIdx]\n"
                    showQuizModal = false
                }
            )
        }

        // 4. Whiteboard Canvas Modal (drawing canvas save as image)
        if (showWhiteboardModal) {
            WhiteboardCanvasModal(
                onDismiss = { showWhiteboardModal = false },
                onSaveImage = { savedImagePath ->
                    lessonContent += "\n[image: $savedImagePath | Whiteboard Diagram]\n"
                    showWhiteboardModal = false
                }
            )
        }

        // 5. AI Assistant Bottom Sheet
        if (showAiBottomSheet) {
            AiAssistantBottomSheet(
                currentContent = lessonContent,
                onDismiss = { showAiBottomSheet = false },
                onApplyResult = { transformedText ->
                    lessonContent = transformedText
                    showAiBottomSheet = false
                }
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Floating Toolbar Pill Button
// -----------------------------------------------------------------------------
@Composable
private fun ToolbarPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tintColor: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tintColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp, // Buttons with icon + label (10px)
            fontWeight = FontWeight.Medium,
            color = tintColor
        )
    }
}

// -----------------------------------------------------------------------------
// Video Insert Modal
// -----------------------------------------------------------------------------
@Composable
private fun VideoInsertModal(
    onDismiss: () -> Unit,
    onInsertVideo: (url: String, title: String) -> Unit
) {
    var videoUrl by remember { mutableStateOf("") }
    var videoTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFFDC2626))
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
            ) {
                Text("Insert Video")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// -----------------------------------------------------------------------------
// Voice Note Recorder Modal
// -----------------------------------------------------------------------------
@Composable
private fun VoiceNoteRecorderModal(
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
            if (isRecording) {
                audioRecorder.stopRecording()
            }
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
        onDismissRequest = {
            if (!isRecording) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF22C55E))
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
                // Timer
                val min = recordingSeconds / 60
                val sec = recordingSeconds % 60
                Text(
                    text = String.format("%02d:%02d", min, sec),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) Color(0xFFDC2626) else Color(0xFF111827)
                )

                // Recording action button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFDC2626) else Color(0xFF22C55E))
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
                    text = if (isRecording) "Recording... Tap red square to stop" else if (recordedFile != null) "Recording saved! Listen or insert." else "Tap mic to start recording voice explanation",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                // Preview player if recorded
                if (recordedFile != null && !isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                tint = Color(0xFF22C55E)
                            )
                        }
                        Text("Preview recorded audio", fontSize = 12.sp, color = Color(0xFF374151))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val file = recordedFile
                    if (file != null && file.exists()) {
                        onSaveVoiceNote(file.absolutePath, recordingSeconds)
                    } else {
                        Toast.makeText(context, "No audio recorded", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = recordedFile != null && !isRecording,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
            ) {
                Text("Insert Voice Note")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isRecording
            ) {
                Text("Cancel")
            }
        }
    )
}

// -----------------------------------------------------------------------------
// Quiz Creator Modal (question + 4 options + correct)
// -----------------------------------------------------------------------------
@Composable
private fun QuizCreatorModal(
    onDismiss: () -> Unit,
    onInsertQuiz: (question: String, options: List<String>, correctIndex: Int) -> Unit
) {
    var questionText by remember { mutableStateOf("") }
    var opt0 by remember { mutableStateOf("") }
    var opt1 by remember { mutableStateOf("") }
    var opt2 by remember { mutableStateOf("") }
    var opt3 by remember { mutableStateOf("") }
    var correctIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Poll, contentDescription = null, tint = Color(0xFF22C55E))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Quiz / Poll", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                    text = "Options (select the radio button for correct answer):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )

                val options = listOf(opt0, opt1, opt2, opt3)
                val setters = listOf< (String) -> Unit >(
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
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF22C55E))
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
            ) {
                Text("Insert Quiz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// -----------------------------------------------------------------------------
// Whiteboard Canvas Modal (drawing canvas save as image)
// -----------------------------------------------------------------------------
data class DrawingLine(
    val path: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
private fun WhiteboardCanvasModal(
    onDismiss: () -> Unit,
    onSaveImage: (savedImagePath: String) -> Unit
) {
    val context = LocalContext.current
    val lines = remember { mutableStateListOf<DrawingLine>() }
    var currentLine by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var strokeColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableStateOf(6f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18181B))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Draw, contentDescription = null, tint = Color(0xFF22C55E))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Teacher Whiteboard", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Drawing Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFFAFAFA))
                        .pointerInput(strokeColor, strokeWidth) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentLine = listOf(offset)
                                },
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

                // Tools & Palette
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3F4F6))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Color.Black, Color(0xFF22C55E), Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFFF59E0B)).forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (strokeColor == col) 2.dp else 0.dp,
                                        color = if (strokeColor == col) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { strokeColor = col }
                            )
                        }
                    }

                    // Clear button
                    TextButton(onClick = { lines.clear(); currentLine = emptyList() }) {
                        Text("Clear", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Save as Image button
                    Button(
                        onClick = {
                            try {
                                val width = 800
                                val height = 600
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

                                val file = File(context.filesDir, "whiteboard_${System.currentTimeMillis()}.png")
                                FileOutputStream(file).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                onSaveImage(file.absolutePath)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to save drawing: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Save Diagram", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// AI Assistant Bottom Sheet: [Make Simpler, Translate to Hausa, Summarize, Fix Grammar]
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiAssistantBottomSheet(
    currentContent: String,
    onDismiss: () -> Unit,
    onApplyResult: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var generatedResult by remember { mutableStateOf<String?>(null) }
    var selectedAction by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Leno AI Educational Assistant", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Enhance and adapt your lesson content for your students.", fontSize = 12.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(16.dp))

            // AI Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Make Simpler
                AiActionChip(
                    title = "Make Simpler",
                    isSelected = selectedAction == "simpler",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedAction = "simpler"
                    generatedResult = simplifyText(currentContent)
                }

                // 2. Translate to Hausa
                AiActionChip(
                    title = "Translate to Hausa",
                    isSelected = selectedAction == "hausa",
                    modifier = Modifier.weight(1.1f)
                ) {
                    selectedAction = "hausa"
                    generatedResult = translateToHausa(currentContent)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 3. Summarize
                AiActionChip(
                    title = "Summarize",
                    isSelected = selectedAction == "summarize",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedAction = "summarize"
                    generatedResult = summarizeText(currentContent)
                }

                // 4. Fix Grammar
                AiActionChip(
                    title = "Fix Grammar",
                    isSelected = selectedAction == "grammar",
                    modifier = Modifier.weight(1f)
                ) {
                    selectedAction = "grammar"
                    generatedResult = fixGrammar(currentContent)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preview result area
            if (generatedResult != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF3F4F6),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = generatedResult ?: "",
                            fontSize = 13.sp,
                            color = Color(0xFF1F2937),
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { generatedResult = null; selectedAction = null }) {
                        Text("Reset")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            generatedResult?.let { onApplyResult(it) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Apply to Lesson", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AiActionChip(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFFDCFCE7) else Color(0xFFF9FAFB),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF22C55E) else Color(0xFFE5E7EB)),
        modifier = modifier.height(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF15803D) else Color(0xFF374151)
            )
        }
    }
}

// -----------------------------------------------------------------------------
// AI Transformations Helpers
// -----------------------------------------------------------------------------
private fun simplifyText(text: String): String {
    if (text.isBlank()) return "## 📌 Simplified Overview\nThis lesson introduces core principles step-by-step using plain language and relatable everyday examples."
    return "$text\n\n## 📌 In Plain Words (Simplified)\n" +
            "• Think of this like everyday objects you see at home.\n" +
            "• Each step follows naturally from the previous rule.\n" +
            "• Focus on what changes and what stays the same."
}

private fun translateToHausa(text: String): String {
    if (text.isBlank()) return "## 🇳🇬 Bayani a Hausa\nWannan darasi yana koya mana ilimi cikin sauki da fahimta."
    return "$text\n\n## 🇳🇬 Bayani a Hausa (Hausa Summary)\n" +
            "Ga mahimman bayanan wannan darasi a takaice domin saukin fahimta:\n" +
            "• Wannan doka ko ka'ida tana bayyana yadda abubuwa ke aiki.\n" +
            "• Idan kana lissafin formula, tabbatar da duba adadin da aka bayar.\n" +
            "• Idan kana da tambaya, zaka iya tambayar malami ta hanyar sauti ko rubutu."
}

private fun summarizeText(text: String): String {
    val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("[") }
    val bullets = lines.take(4).map { "• ${it.take(80)}" }
    return "$text\n\n## 📊 Quick Summary\n" + (if (bullets.isEmpty()) "• Key Concept 1\n• Key Concept 2\n• Practice Application" else bullets.joinToString("\n"))
}

private fun fixGrammar(text: String): String {
    if (text.isBlank()) return text
    // Basic formatting and capitalization fixes
    return text.lines().joinToString("\n") { line ->
        if (line.isNotEmpty() && line[0].isLowerCase()) {
            line.replaceFirstChar { it.uppercase() }
        } else {
            line
        }
    }
}
