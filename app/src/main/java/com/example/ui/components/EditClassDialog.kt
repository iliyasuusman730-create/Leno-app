package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
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
import coil.compose.AsyncImage
import com.example.data.entity.ClassEntity
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import java.io.File
import java.io.FileOutputStream

@Composable
fun EditClassDialog(
    classItem: ClassEntity,
    onDismiss: () -> Unit,
    onUpdateClass: (
        title: String,
        shortDescription: String,
        level: String,
        isPaid: Boolean,
        price: String,
        schedule: String,
        lessonType: String,
        status: String,
        imageUrl: String,
        videoUrl: String
    ) -> Unit
) {
    val context = LocalContext.current
    val levels = listOf("All Levels", "Foundational", "Intermediate", "Advanced", "Exam Prep")
    val lessonTypes = listOf("Text lesson", "Image lesson", "Video lesson")
    val statusOptions = listOf("PUBLISHED", "DRAFT", "ARCHIVED")

    var title by remember { mutableStateOf(classItem.title) }
    var selectedLevel by remember {
        mutableStateOf(if (classItem.level in levels) classItem.level else levels[0])
    }
    var shortDescription by remember { mutableStateOf(classItem.shortDescription) }
    var selectedLessonType by remember {
        mutableStateOf(if (classItem.lessonType in lessonTypes) classItem.lessonType else lessonTypes[0])
    }
    var imageUrl by remember { mutableStateOf(classItem.imageUrl) }
    var videoUrl by remember { mutableStateOf(classItem.videoUrl) }
    var isPaid by remember { mutableStateOf(classItem.isPaid) }
    var price by remember { mutableStateOf(classItem.price.ifBlank { "₦1,500" }) }
    var schedule by remember { mutableStateOf(classItem.schedule.ifBlank { "Self-paced" }) }
    var selectedStatus by remember {
        mutableStateOf(if (classItem.status in statusOptions) classItem.status else "PUBLISHED")
    }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = LenoPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Edit Class",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    Text(
                        text = "Only the creator teacher can edit this class",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Rule notice: Locked Subject
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LenoPrimaryVeryLight,
                    border = BorderStroke(1.dp, LenoPrimaryLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Subject locked",
                            tint = LenoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Teaching Subject: ${classItem.subject}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = LenoPrimary
                            )
                            Text(
                                text = "Locked to your verified teacher profile to ensure subject expertise.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text("Class Title *") },
                    placeholder = { Text("e.g. Master Algebra Step-by-Step") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_class_title_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Short Description
                OutlinedTextField(
                    value = shortDescription,
                    onValueChange = {
                        shortDescription = it
                        errorMessage = null
                    },
                    label = { Text("Class Description *") },
                    placeholder = { Text("Explain what students will learn, expectations, and outcomes...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_class_description_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Level FilterChips
                Text(
                    text = "Target Level",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    levels.take(3).forEach { lvl ->
                        FilterChip(
                            selected = selectedLevel == lvl,
                            onClick = { selectedLevel = lvl },
                            label = { Text(lvl, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LenoPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    levels.drop(3).forEach { lvl ->
                        FilterChip(
                            selected = selectedLevel == lvl,
                            onClick = { selectedLevel = lvl },
                            label = { Text(lvl, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LenoPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Schedule
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text("Schedule / Pace") },
                    placeholder = { Text("e.g. Self-paced, Weekends, Mon-Wed-Fri") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_class_schedule_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Format / Lesson Type
                Text(
                    text = "Format / Lesson Delivery",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    lessonTypes.forEach { type ->
                        FilterChip(
                            selected = selectedLessonType == type,
                            onClick = { selectedLessonType = type },
                            label = { Text(type, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LenoPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pricing: Free vs Paid
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isPaid) "Paid Class" else "Free Class",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isPaid) "Students must pay to enroll" else "Free enrollment for all students",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isPaid,
                                onCheckedChange = { isPaid = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = LenoPrimary),
                                modifier = Modifier.testTag("edit_class_is_paid_switch")
                            )
                        }

                        if (isPaid) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = price,
                                onValueChange = { price = it },
                                label = { Text("Enrollment Fee") },
                                placeholder = { Text("e.g. ₦1,500") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_class_price_input")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Publication Status
                Text(
                    text = "Publication Status",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statusOptions.forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LenoPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cover Image Picker
                Text(
                    text = "Class Cover Image",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (imageUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Cover preview",
                            modifier = Modifier.fillMaxWidth().height(130.dp),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { imageUrl = "" },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove cover",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_class_pick_image_btn")
                ) {
                    Icon(
                        imageVector = if (imageUrl.isBlank()) Icons.Default.AddPhotoAlternate else Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (imageUrl.isBlank()) "Select Cover Image" else "Change Cover Image",
                        fontSize = 12.sp
                    )
                }

                // Optional Video URL
                if (selectedLessonType.contains("video", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = videoUrl,
                        onValueChange = { videoUrl = it },
                        label = { Text("Video Link (Optional)") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_class_video_input")
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.trim().isBlank()) {
                        errorMessage = "Please enter a class title."
                        return@Button
                    }
                    if (shortDescription.trim().isBlank()) {
                        errorMessage = "Please enter a class description."
                        return@Button
                    }
                    onUpdateClass(
                        title.trim(),
                        shortDescription.trim(),
                        selectedLevel,
                        isPaid,
                        if (isPaid) price.trim() else "Free",
                        schedule.trim(),
                        selectedLessonType,
                        selectedStatus,
                        imageUrl.trim(),
                        videoUrl.trim()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_edit_class_btn")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_edit_class_btn")
            ) {
                Text("Cancel")
            }
        }
    )
}
