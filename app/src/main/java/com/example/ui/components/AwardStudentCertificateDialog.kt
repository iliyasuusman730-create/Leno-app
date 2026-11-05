package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.ClassEntity
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark

/**
 * Teacher Evaluation & Certificate Award Dialog.
 * Enables teachers to personally award certificates to hardworking students.
 */
@Composable
fun AwardStudentCertificateDialog(
    classItem: ClassEntity,
    studentName: String,
    studentUserId: String,
    teacherName: String,
    onDismiss: () -> Unit,
    onAward: (awardTitle: String, commendation: String) -> Unit
) {
    val presetTitles = listOf(
        "Certificate of Academic Excellence",
        "Hardworking Student Award",
        "Distinction in Subject",
        "Mastery & Diligence Award",
        "Outstanding Performance Award",
        "Custom Title"
    )

    var selectedTitle by remember { mutableStateOf(presetTitles[0]) }
    var customTitle by remember { mutableStateOf("") }
    var titleMenuExpanded by remember { mutableStateOf(false) }

    var commendation by remember {
        mutableStateOf("Awarded for exceptional commitment, active participation in class discussions, and outstanding performance.")
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, Color(0xFFD97706), RoundedCornerShape(20.dp))
                .testTag("award_student_certificate_dialog"),
            color = Color(0xFFFFFDF7)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Award Student Certificate",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Teacher Endorsement & Recognition",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF4B5563))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Student & Class Info Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Student: $studentName",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14532D)
                        )
                        Text(
                            text = "Class: ${classItem.title} (${classItem.subject})",
                            fontSize = 12.sp,
                            color = Color(0xFF166534)
                        )
                        Text(
                            text = "Instructor: $teacherName",
                            fontSize = 11.sp,
                            color = Color(0xFF166534)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Award Title / Distinction Dropdown
                Text(
                    text = "Award Distinction / Honor Title *",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1D5DB)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { titleMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Choose ▼",
                                fontSize = 11.sp,
                                color = LenoPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = titleMenuExpanded,
                        onDismissRequest = { titleMenuExpanded = false }
                    ) {
                        presetTitles.forEach { title ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (title == selectedTitle) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedTitle = title
                                    titleMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedTitle == "Custom Title") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        label = { Text("Specify Custom Honor Title") },
                        placeholder = { Text("e.g. Most Diligent Learner Award") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Teacher Commendation / Note
                Text(
                    text = "Teacher's Personal Commendation & Feedback *",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = commendation,
                    onValueChange = { commendation = it },
                    placeholder = { Text("Describe student's work, dedication, effort, or exam result...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF4B5563))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalTitle = if (selectedTitle == "Custom Title") {
                                if (customTitle.isBlank()) {
                                    errorMessage = "Please enter your custom honor title."
                                    return@Button
                                }
                                customTitle.trim()
                            } else {
                                selectedTitle
                            }

                            if (commendation.isBlank()) {
                                errorMessage = "Please enter teacher commendation."
                                return@Button
                            }

                            onAward(finalTitle, commendation.trim())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("confirm_award_cert_btn")
                    ) {
                        Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Issue Certificate 🎓", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
