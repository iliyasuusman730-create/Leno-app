package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.ClassEntity
import com.example.data.entity.UserEntity
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.viewmodel.LenoViewModel

@Composable
fun AddStudentToClassDialog(
    classItem: ClassEntity,
    viewModel: LenoViewModel,
    enrolledStudentUserIds: Set<String>,
    onDismiss: () -> Unit,
    onAddStudent: (studentUserId: String, studentLinoId: String, studentName: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val contacts by viewModel.contactsList.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    // Determine list to show: if search active, show search results; otherwise show contacts
    val isSearching = searchQuery.trim().isNotBlank()
    val candidateStudents: List<UserEntity> = if (isSearching) {
        searchResults.filter { it.userId != classItem.instructorUserId }
    } else {
        contacts.filter { it.userId != classItem.instructorUserId }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f)
                .imePadding()
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
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
                                .background(LenoPrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = LenoPrimaryDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Add Student to Class",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = classItem.title,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Tip for Schools and Private Teachers
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = LenoPrimaryVeryLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimaryLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 Direct Enrollment: Add school students by username or Leno ID. They will instantly find this class in their \"My Learning\" tab.",
                        fontSize = 11.sp,
                        color = LenoPrimaryDark,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchQuery.value = it
                    },
                    placeholder = {
                        Text("Search username or Leno ID...", fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = LenoPrimary)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_student_search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isSearching) "SEARCH RESULTS (${candidateStudents.size})" else "YOUR CONTACTS (${candidateStudents.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (candidateStudents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isSearching) "No students match \"$searchQuery\"" else "No contacts found.\nType a student's username or Leno ID above to find them.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidateStudents, key = { it.userId }) { student ->
                            val isEnrolled = enrolledStudentUserIds.contains(student.userId)
                            val studentName = student.displayName.ifBlank { student.username }
                            val studentLinoId = student.linoId.ifBlank {
                                "LEN-${student.userId.replace("usr_", "").padEnd(8, '0').take(8).uppercase()}"
                            }

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isEnrolled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(LenoPrimaryLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = studentName.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = LenoPrimaryDark
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = studentName,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "ID: $studentLinoId • @${student.username}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isEnrolled) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFE8F5E9)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFF2E7D32),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "Enrolled",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                onAddStudent(student.userId, studentLinoId, studentName)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                                horizontal = 10.dp,
                                                vertical = 4.dp
                                            ),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(
                                                text = "+ Add",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
