package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ClassEntity
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.util.LessonProgressManager

@Composable
fun EnterClassCodeDialog(
    allPublishedClasses: List<ClassEntity>,
    onDismiss: () -> Unit,
    onJoinClass: (ClassEntity) -> Unit
) {
    val context = LocalContext.current
    var codeInput by remember { mutableStateOf("") }
    var matchedClass by remember { mutableStateOf<ClassEntity?>(null) }
    var hasSearched by remember { mutableStateOf(false) }

    fun searchCode() {
        val input = codeInput.trim()
        if (input.isBlank()) {
            Toast.makeText(context, "Please enter a class code", Toast.LENGTH_SHORT).show()
            return
        }
        val found = allPublishedClasses.firstOrNull { cls ->
            LessonProgressManager.matchesClassCode(input, cls.classId)
        }
        matchedClass = found
        hasSearched = true
        if (found == null) {
            Toast.makeText(context, "No class found with code '$input'", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(LenoPrimaryVeryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        tint = LenoPrimaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Join with Class Code",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "For schools, academies & private tutors",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ask your teacher or school for the 6-character Class Code (e.g. LEN-84920A) and type it below:",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it.uppercase()
                        hasSearched = false
                        matchedClass = null
                    },
                    placeholder = {
                        Text(
                            text = "e.g. LEN-84920A",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = Color.Gray
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = LenoPrimary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LenoPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enter_class_code_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { searchCode() },
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_class_code_btn")
                ) {
                    Text("Find Class", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Search Results Preview
                if (hasSearched) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val found = matchedClass
                    if (found != null) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                            border = BorderStroke(1.5.dp, Color(0xFF86EFAC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = LenoPrimaryVeryLight
                                    ) {
                                        Text(
                                            text = found.subject,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LenoPrimaryDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Text(
                                        text = LessonProgressManager.formatClassCode(found.classId),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = LenoPrimaryDark
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = found.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF14532D)
                                )

                                Text(
                                    text = "Instructor: ${found.instructorName} (${found.instructorLinoId})",
                                    fontSize = 11.sp,
                                    color = Color(0xFF15803D)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        onJoinClass(found)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("confirm_join_by_code_btn")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Join This Class 🎉", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "❌ No class found for code '$codeInput'. Please check the code with your teacher and try again.",
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
