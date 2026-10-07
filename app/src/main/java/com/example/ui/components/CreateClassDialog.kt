package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight

@Composable
fun CreateClassDialog(
    approvedSubject: String,
    onDismiss: () -> Unit,
    onCreateClass: (
        title: String,
        subject: String,
        level: String,
        shortDescription: String,
        lessonContent: String,
        imageUrl: String,
        optionalImages: String,
        videoUrl: String,
        isPaid: Boolean,
        price: String,
        schedule: String,
        lessonType: String,
        status: String
    ) -> Unit
) {
    val context = LocalContext.current
    val levels = listOf("All Levels", "Foundational", "Intermediate", "Advanced", "Exam Prep")
    val lessonTypes = listOf("Text lesson", "Image lesson", "Video lesson")

    var title by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf(levels[0]) }
    var shortDescription by remember { mutableStateOf("") }
    var lessonContent by remember { mutableStateOf("") }
    var selectedLessonType by remember { mutableStateOf(lessonTypes[0]) }
    var imageUrl by remember { mutableStateOf("") }
    var optionalImages by remember { mutableStateOf("") }
    var videoUrl by remember { mutableStateOf("") }
    var isPaid by remember { mutableStateOf(true) }
    var price by remember { mutableStateOf("₦1,500") }
    var schedule by remember { mutableStateOf("Self-paced") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val file = File(context.filesDir, "class_cover_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                imageUrl = file.absolutePath
            } catch (e: Exception) {
                imageUrl = uri.toString()
            }
        }
    }

    val lockedSubject = approvedSubject.ifBlank { "Maths" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = LenoPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Create Class",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Rule notice: Locked Subject
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LenoPrimaryVeryLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimaryLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked Subject",
                            tint = LenoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Approved Subject: $lockedSubject",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "One Teacher = One Subject rule. Every class you create belongs strictly to your approved subject.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Class Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Class Title *") },
                    placeholder = { Text("e.g. Modern Algebra & Geometry Drills") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_class_title_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Level Selection
                Text("Target Level", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    levels.take(3).forEach { lvl ->
                        FilterChip(
                            selected = selectedLevel == lvl,
                            onClick = { selectedLevel = lvl },
                            label = { Text(lvl, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    levels.drop(3).forEach { lvl ->
                        FilterChip(
                            selected = selectedLevel == lvl,
                            onClick = { selectedLevel = lvl },
                            label = { Text(lvl, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Short Description
                OutlinedTextField(
                    value = shortDescription,
                    onValueChange = { shortDescription = it },
                    label = { Text("Short Description *") },
                    placeholder = { Text("What students will learn in this class...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 70.dp),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Cover Image from Gallery
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Class Banner / Cover Picture", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (imageUrl.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            ) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Cover Image Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                IconButton(
                                    onClick = { imageUrl = "" },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove Cover",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("upload_class_cover_gallery_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = LenoPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload Cover Picture from Gallery", fontSize = 12.sp, color = LenoPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lesson Content (Lesson 1)
                OutlinedTextField(
                    value = lessonContent,
                    onValueChange = { lessonContent = it },
                    label = { Text("Initial Lesson Content / Notes (Low Data)") },
                    placeholder = { Text("Write Lesson 1 notes, formulas, explanations, or outline here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Schedule / Timing
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text("Schedule / Pace") },
                    placeholder = { Text("e.g. Self-paced, Daily at 5 PM, Weekends") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Paid vs Free Toggle
                // Enrollment Fee (Required: students must pay to join class)
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Class Enrollment Fee *") },
                    placeholder = { Text("e.g. ₦1,500") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        Text(
                            text = "Students must pay this enrollment fee to join your class.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (title.isBlank()) {
                            errorMessage = "Please enter a class title."
                            return@OutlinedButton
                        }
                        if (shortDescription.isBlank()) {
                            errorMessage = "Please enter a short description."
                            return@OutlinedButton
                        }
                        errorMessage = null
                        onCreateClass(
                            title,
                            lockedSubject,
                            selectedLevel,
                            shortDescription,
                            lessonContent,
                            imageUrl,
                            optionalImages,
                            videoUrl,
                            true,
                            price.trim().ifBlank { "₦1,500" },
                            schedule,
                            selectedLessonType,
                            "DRAFT"
                        )
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Draft", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (title.isBlank()) {
                            errorMessage = "Please enter a class title."
                            return@Button
                        }
                        if (shortDescription.isBlank()) {
                            errorMessage = "Please enter a short description."
                            return@Button
                        }
                        errorMessage = null
                        onCreateClass(
                            title,
                            lockedSubject,
                            selectedLevel,
                            shortDescription,
                            lessonContent,
                            imageUrl,
                            optionalImages,
                            videoUrl,
                            true,
                            price.trim().ifBlank { "₦1,500" },
                            schedule,
                            selectedLessonType,
                            "PUBLISHED"
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("submit_create_class_btn")
                ) {
                    Text("Publish Class", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
