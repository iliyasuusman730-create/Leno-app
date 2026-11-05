package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.ClassEntity
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.util.LessonProgressManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Official Digital Certificate of Completion for Leno Academy students
 * awarded upon mastering 100% of coursework in a published class.
 */
@Composable
fun CourseCertificateDialog(
    classItem: ClassEntity,
    studentName: String,
    studentUserId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val awardedCert = remember(classItem.classId, studentUserId) {
        LessonProgressManager.getAwardedCertificate(context, classItem.classId, studentUserId)
    }
    val certificateTitle = awardedCert?.awardTitle ?: "CERTIFICATE OF EXCELLENCE"
    val commendationText = awardedCert?.teacherCommendation
        ?: "Awarded by instructor in recognition of outstanding commitment, academic diligence, and coursework performance in"
    val issueDate = awardedCert?.issueDate?.ifBlank { null } ?: remember {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
    }
    val certificateId = awardedCert?.certificateId?.ifBlank { null } ?: remember(classItem.classId, studentUserId) {
        LessonProgressManager.formatCertificateId(classItem.classId, studentUserId)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, Color(0xFFD97706), RoundedCornerShape(20.dp))
                .testTag("course_certificate_dialog"),
            color = Color(0xFFFFFDF7)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header with close icon
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "LENO ACADEMY",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF065F46),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Teacher-Awarded Credential",
                                fontSize = 10.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF4B5563)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Certificate Ribbon & Title
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = certificateTitle.uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E293B),
                    letterSpacing = 1.1.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "This officially certifies that",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Student Name Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = studentName.ifBlank { "Leno Scholar" },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = commendationText,
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Class Title
                Text(
                    text = "${classItem.title} (${classItem.subject})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(12.dp))

                // Instructor & Verification Meta
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "Verified Instructor:",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = classItem.instructorName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = LenoPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = classItem.instructorLinoId,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = LenoPrimaryDark
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Date of Completion:",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = issueDate,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = certificateId,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons: Share & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "Certificate of Completion - ${classItem.title}"
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "🎓 I just completed the course '${classItem.title}' (${classItem.subject}) on Leno Academy!\n" +
                                            "Verified Certificate ID: $certificateId\n" +
                                            "Teacher: ${classItem.instructorName} (${classItem.instructorLinoId})\n" +
                                            "Learn online with Leno!"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Certificate"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share 📤", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
