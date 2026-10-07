package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.TeacherRoleEntity
import com.example.data.entity.UserEntity
import com.example.data.model.TeacherApplicationItem
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight

@Composable
fun TeacherApplicationDialog(
    currentUser: UserEntity?,
    teacherRole: TeacherRoleEntity? = null,
    existingApplication: TeacherApplicationItem? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        subject: String,
        teachingLevel: String,
        educationQualification: String,
        teachingExperience: String,
        teacherIntro: String,
        certificateDocumentName: String,
        sampleTeachingInfo: String,
        agreedToGuidelines: Boolean
    ) -> Unit
) {
    val allowedSubjects = listOf(
        "Maths",
        "Quran",
        "JAMB",
        "Skills",
        "Online Skills",
        "Content Creator"
    )

    val isPending = (teacherRole?.isPending == true) ||
            (teacherRole?.status?.equals("PENDING", ignoreCase = true) == true) ||
            (existingApplication?.isPending == true)

    val isApproved = (teacherRole?.isApprovedTeacher == true) ||
            (teacherRole?.status?.equals("APPROVED", ignoreCase = true) == true) ||
            (currentUser?.isTeacher == true) ||
            (currentUser?.role?.equals("TEACHER", ignoreCase = true) == true) ||
            (!currentUser?.assignedSubject.isNullOrBlank()) ||
            (existingApplication?.isApproved == true)

    val displayedSubject = when {
        !currentUser?.assignedSubject.isNullOrBlank() -> currentUser?.assignedSubject.orEmpty()
        teacherRole?.effectiveApprovedSubject?.isNotBlank() == true -> teacherRole.effectiveApprovedSubject
        existingApplication?.effectiveSubject?.isNotBlank() == true -> existingApplication.effectiveSubject
        else -> "Selected Subject"
    }

    val displayedLevel = teacherRole?.teachingLevel?.ifBlank { null }
        ?: existingApplication?.teachingLevel?.ifBlank { null }
        ?: "Secondary & JAMB Prep"

    val displayedQualification = teacherRole?.educationQualification?.ifBlank { null }
        ?: existingApplication?.educationQualification?.ifBlank { null }
        ?: ""

    // CASE 1: User already submitted application and status is PENDING
    if (isPending) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFF8E1)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Application Under Review",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Status: PENDING REVIEW ⏳",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Your Submitted Application:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Subject: $displayedSubject",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (displayedLevel.isNotBlank()) {
                                Text(
                                    text = "Level: $displayedLevel",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (displayedQualification.isNotBlank()) {
                                Text(
                                    text = "Qualification: $displayedQualification",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "You can only apply one time. Your application is currently under review by Leno administrators. You will be notified once a decision has been made.",
                                fontSize = 12.sp,
                                color = Color(0xFF5D4037),
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Understood", fontWeight = FontWeight.Bold)
                }
            }
        )
        return
    }

    // CASE 2: User is already an APPROVED Teacher
    if (isApproved) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(LenoPrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = LenoPrimaryDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Approved Teacher ✓",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Status: APPROVED TEACHER",
                            fontSize = 12.sp,
                            color = LenoPrimaryDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Accredited Subject:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = displayedSubject,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Text(
                        text = "You can only apply one time. You are already an approved teacher for $displayedSubject. You can create and publish lessons under your approved subject directly in the Class tab.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
        return
    }

    // CASE 3: Normal Form - User can apply
    var selectedSubject by remember { mutableStateOf(allowedSubjects[0]) }
    var subjectMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var teachingLevel by remember { mutableStateOf("Secondary & JAMB Prep") }
    var educationQualification by remember { mutableStateOf("") }
    var teachingExperience by remember { mutableStateOf("") }
    var teacherIntro by remember { mutableStateOf("") }
    var certificateDocumentName by remember { mutableStateOf("") }
    var certificatePhotoPath by remember { mutableStateOf("") }
    var sampleTeachingInfo by remember { mutableStateOf("") }
    var agreedToGuidelines by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val file = File(context.filesDir, "teacher_credential_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                certificatePhotoPath = file.absolutePath
                if (certificateDocumentName.isBlank()) {
                    certificateDocumentName = "Credential Document (${file.name})"
                }
            } catch (e: Exception) {
                certificatePhotoPath = uri.toString()
                if (certificateDocumentName.isBlank()) {
                    certificateDocumentName = "Credential Document Attached"
                }
            }
        }
    }

    val linoId = currentUser?.linoId?.ifBlank {
        "LEN-${(currentUser?.userId ?: "USER").replace("usr_", "").padEnd(8, '0').take(8).uppercase()}"
    } ?: "LEN-00000000"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LenoPrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = LenoPrimaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Become a Teacher",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    Text(
                        text = "Share your knowledge on Leno",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Pre-filled existing Leno identity banner (ONE PROFILE ONLY)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Leno Profile Identity (Attached Role)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LenoPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Name: ${currentUser?.displayName?.ifBlank { currentUser.username } ?: "Leno User"}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Leno ID: $linoId",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Note: No separate teacher account will be created. Your existing account and permanent Leno ID will be used.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // STRICT RULE: ONE TEACHER = ONE SUBJECT CALLOUT
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LenoPrimaryVeryLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimaryLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = LenoPrimaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Rule: One Teacher = One Subject",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LenoPrimaryDark
                            )
                            Text(
                                text = "Select the single subject you specialize in. Once approved, you can publish classes exclusively for this subject.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Subject Selection Dropdown
                Text(
                    text = "Specialized Subject *",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { subjectMenuExpanded = true }
                            .testTag("teacher_subject_dropdown")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedSubject,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Change ▼",
                                fontSize = 12.sp,
                                color = LenoPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = subjectMenuExpanded,
                        onDismissRequest = { subjectMenuExpanded = false }
                    ) {
                        allowedSubjects.forEach { subject ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = subject,
                                        fontWeight = if (subject == selectedSubject) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedSubject = subject
                                    subjectMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Teaching Level
                OutlinedTextField(
                    value = teachingLevel,
                    onValueChange = { teachingLevel = it },
                    label = { Text("Teaching Level *") },
                    placeholder = { Text("e.g., Secondary & JAMB Prep, Tertiary, Beginners") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Educational Qualification
                OutlinedTextField(
                    value = educationQualification,
                    onValueChange = { educationQualification = it },
                    label = { Text("Educational Qualification *") },
                    placeholder = { Text("e.g., B.Sc Mathematics, NCE, Diploma") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Teaching Experience
                OutlinedTextField(
                    value = teachingExperience,
                    onValueChange = { teachingExperience = it },
                    label = { Text("Teaching Experience *") },
                    placeholder = { Text("e.g., 4 years secondary school teacher") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Teacher Intro / Bio
                OutlinedTextField(
                    value = teacherIntro,
                    onValueChange = { teacherIntro = it },
                    label = { Text("Teacher Intro (Public)") },
                    placeholder = { Text("Brief introduction students will see on your class cards") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Certificate / Credentials (Private)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Verification Document (Private & Confidential)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = certificateDocumentName,
                            onValueChange = { certificateDocumentName = it },
                            placeholder = { Text("Degree cert, TRCN ID or institution name") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (certificatePhotoPath.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                            ) {
                                AsyncImage(
                                    model = certificatePhotoPath,
                                    contentDescription = "Credential Photo Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                IconButton(
                                    onClick = { certificatePhotoPath = "" },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove Document Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
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
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = LenoPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload Certificate Photo from Gallery", fontSize = 11.sp, color = LenoPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sample Teaching Info
                OutlinedTextField(
                    value = sampleTeachingInfo,
                    onValueChange = { sampleTeachingInfo = it },
                    label = { Text("Sample Teaching Method / Link") },
                    placeholder = { Text("Brief description of how you teach or reference link") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Guidelines agreement
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { agreedToGuidelines = !agreedToGuidelines }
                ) {
                    Checkbox(
                        checked = agreedToGuidelines,
                        onCheckedChange = { agreedToGuidelines = it },
                        colors = CheckboxDefaults.colors(checkedColor = LenoPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I agree to Leno's Teacher Code of Conduct and Low-Data Teaching Guidelines.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentUser?.isRestricted == true) {
                        errorMessage = "Your account is restricted by administration. You cannot submit teacher applications."
                        return@Button
                    }
                    if (educationQualification.isBlank()) {
                        errorMessage = "Please enter your educational qualification."
                        return@Button
                    }
                    if (teachingExperience.isBlank()) {
                        errorMessage = "Please enter your teaching experience."
                        return@Button
                    }
                    if (!agreedToGuidelines) {
                        errorMessage = "Please agree to the guidelines."
                        return@Button
                    }

                    val finalDoc = if (certificatePhotoPath.isNotBlank()) {
                        "${certificateDocumentName.ifBlank { "Credential Document" }} [Photo: $certificatePhotoPath]"
                    } else certificateDocumentName

                    onSubmit(
                        selectedSubject,
                        teachingLevel,
                        educationQualification,
                        teachingExperience,
                        teacherIntro,
                        finalDoc,
                        sampleTeachingInfo,
                        agreedToGuidelines
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("submit_teacher_app_btn")
            ) {
                Text("Submit Application", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
