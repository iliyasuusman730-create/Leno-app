package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.data.LenoRepository
import com.example.ui.components.OfficialVerifiedBadge
import com.example.ui.theme.LenoOfficialBadge
import androidx.compose.material.icons.filled.Campaign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.ClassEntity
import com.example.data.entity.LessonEntity
import com.example.ui.components.AdminTeacherApplicationsDialog
import com.example.ui.components.ClassDetailDialog
import com.example.ui.components.CreateClassDialog
import com.example.ui.components.LenoAvatar
import com.example.ui.components.TeacherApplicationDialog
import com.example.ui.theme.OnlineGreen
import com.example.viewmodel.LenoViewModel
import java.io.File
import java.io.FileOutputStream

@Composable
fun ProfileScreen(
    viewModel: LenoViewModel,
    onNavigateToProtectAccount: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenChat: (String) -> Unit = {}
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val isFetchingUsers by viewModel.isFetchingUserData.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(currentUser, isFetchingUsers) {
        if (currentUser == null && !isFetchingUsers) {
            onLogout()
        }
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var showAvatarOptionsDialog by remember { mutableStateOf(false) }

    val userTeacherRole by viewModel.currentUserTeacherRole.collectAsState()
    val createdClasses by viewModel.currentUserCreatedClasses.collectAsState()
    val enrolledClasses by viewModel.currentUserEnrolledClasses.collectAsState()
    val pendingTeacherApplications by viewModel.pendingTeacherApplications.collectAsState()
    val teacherApplications by viewModel.teacherApplications.collectAsState()

    var showTeacherAppDialog by remember { mutableStateOf(false) }
    var showAdminApplicationsDialog by remember { mutableStateOf(false) }
    var showCreateClassDialog by remember { mutableStateOf(false) }
    var manageClassesExpanded by remember { mutableStateOf(false) }
    var selectedClassForDetail by remember { mutableStateOf<ClassEntity?>(null) }
    var showBankSettlementDialog by remember { mutableStateOf(false) }
    var showPayoutDialog by remember { mutableStateOf(false) }

    val user = currentUser
    val linoIdValue = user?.linoId?.ifBlank { "LEN-${user.userId.replace("usr_", "").padEnd(8, '0').take(8).uppercase()}" } ?: ""
    var isUploadingAvatar by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && user != null) {
            isUploadingAvatar = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val photoFile = File(context.filesDir, "profile_avatar_${System.currentTimeMillis()}.jpg")
                photoFile.outputStream().use { out ->
                    inputStream?.copyTo(out)
                }
                val newAvatarUrl = photoFile.absolutePath
                viewModel.updateProfile(
                    displayName = user.displayName,
                    username = user.username,
                    bio = user.bio,
                    avatarUrl = newAvatarUrl,
                    statusMessage = user.statusMessage
                ) { result ->
                    isUploadingAvatar = false
                    if (result.isSuccess) {
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, result.exceptionOrNull()?.message ?: "Failed to save profile picture.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Error saving gallery photo: ${e.message}")
                viewModel.updateProfile(
                    displayName = user.displayName,
                    username = user.username,
                    bio = user.bio,
                    avatarUrl = uri.toString(),
                    statusMessage = user.statusMessage
                ) { result ->
                    isUploadingAvatar = false
                    if (result.isSuccess) {
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, result.exceptionOrNull()?.message ?: "Failed to save profile picture.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        user?.let { currUser ->
            val newAvatarUrl = if (bitmap != null) {
                try {
                    val photoFile = File(context.filesDir, "profile_photo_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(photoFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    photoFile.absolutePath
                } catch (e: Exception) {
                    Log.e("ProfileScreen", "Error saving profile photo: ${e.message}")
                    currUser.avatarUrl
                }
            } else {
                currUser.avatarUrl
            }

            isUploadingAvatar = true
            viewModel.updateProfile(
                displayName = currUser.displayName,
                username = currUser.username,
                bio = currUser.bio,
                avatarUrl = newAvatarUrl,
                statusMessage = currUser.statusMessage
            ) { result ->
                isUploadingAvatar = false
                if (result.isSuccess) {
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Failed to save photo.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                cameraLauncher.launch(null)
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Error launching camera activity: ${e.message}")
            }
        } else {
            Toast.makeText(context, "Camera permission needed to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "My Profile",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isFetchingUsers) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("profile_fetching_indicator"),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = { viewModel.refreshUserData() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("profile_refresh_button")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { showEditDialog = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("profile_edit_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Profile")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (user == null && isFetchingUsers) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("profile_loading_progress"),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Fetching profile data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier.clickable {
                        if (user?.isOfficial != true) {
                            showAvatarOptionsDialog = true
                        }
                    }
                ) {
                    LenoAvatar(
                        avatarUrl = user?.avatarUrl,
                        isOfficial = user?.isOfficial == true,
                        size = 110.dp,
                        contentDescription = "Profile Avatar",
                        username = user?.username
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (user?.isOnline == true) OnlineGreen else MaterialTheme.colorScheme.outline)
                            .align(Alignment.BottomStart)
                    )
                    if (user?.isOfficial != true) {
                        FilledIconButton(
                            onClick = { showAvatarOptionsDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.BottomEnd)
                                .testTag("profile_camera_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Change Profile Photo",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = user?.displayName ?: "User",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    )
                    if (user?.hasVerifiedBadge == true) {
                        Spacer(modifier = Modifier.width(6.dp))
                        OfficialVerifiedBadge(size = 20.dp)
                    }
                }

                Text(
                    text = "@${user?.username ?: "username"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                // Role Differentiation: Only approved teachers show who they are (Leno is for everyone, not just students)
                val isTeacher = user?.isTeacher == true || userTeacherRole?.isTeacher == true || userTeacherRole?.isApprovedTeacher == true || userTeacherRole?.status == "Teacher" || userTeacherRole?.status == "Trusted Teacher" || userTeacherRole?.status == "APPROVED"
                val isTrusted = userTeacherRole?.isTrustedTeacher == true
                val isCreator = userTeacherRole?.isContentCreator == true
                val approvedSubject = user?.effectiveSubject?.ifBlank { userTeacherRole?.effectiveApprovedSubject ?: "" } ?: (userTeacherRole?.effectiveApprovedSubject ?: "")

                if (isTeacher || isTrusted || isCreator) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isTeacher) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = com.example.ui.theme.LenoPrimary,
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (approvedSubject.isNotBlank()) "Teacher • $approvedSubject ✓" else "Teacher ✓",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (isTrusted) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1976D2),
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Trusted",
                                        modifier = Modifier.size(13.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Trusted ✓",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (isCreator) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF7B1FA2),
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Content Creator",
                                        modifier = Modifier.size(13.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Content Creator",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status message pill
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = user?.statusMessage ?: "Hey there! I am using Leno.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = user?.bio ?: "No bio added yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Leno ID Identity Banner Card
        if (linoIdValue.isNotBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LenoBorderDefault)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = com.example.ui.theme.LenoIdBadgeBg,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "YOUR LENO ID",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.LenoIdBadgeText,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = linoIdValue,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Share with friends so they can find you",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(linoIdValue))
                            Toast.makeText(context, "Leno ID copied: $linoIdValue", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = com.example.ui.theme.LenoPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LenoBorderDefault),
                        modifier = Modifier.testTag("profile_copy_lino_id_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Leno ID", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Owner & Admin Console Card: strictly verified via authenticated Firebase Auth UID or authoritative role in database
        // Never granted based on displayName, username, or public Leno ID alone
        val isOwnerOrAdmin = user?.isAdmin == true || user?.isOwner == true

        if (isOwnerOrAdmin) {
            val pendingAppsCount = teacherApplications.count { it.isPending }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdminApplicationsDialog = true }
                    .testTag("profile_admin_portal_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, com.example.ui.theme.LenoPrimary)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(com.example.ui.theme.LenoPrimaryVeryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Admin Portal",
                                    tint = com.example.ui.theme.LenoPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Owner & Admin Console",
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = com.example.ui.theme.LenoPrimaryVeryLight
                                    ) {
                                        Text(
                                            text = "App Owner",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = com.example.ui.theme.LenoPrimaryDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Teacher approvals, user directory & live activities",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (pendingAppsCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFD97706)
                            ) {
                                Text(
                                    text = "$pendingAppsCount Pending",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = com.example.ui.theme.LenoPrimaryVeryLight
                            ) {
                                Text(
                                    text = "All Clear",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.example.ui.theme.LenoPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showAdminApplicationsDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_admin_portal_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.LenoPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Owner Console (Approvals & Activities)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Quick Online Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Online Visibility",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (user?.isOnline == true) "Friends can see you're active" else "You appear offline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = user?.isOnline == true,
                    onCheckedChange = { viewModel.toggleOnlineStatus(it) },
                    modifier = Modifier.testTag("profile_online_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = OnlineGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Content Creator Role Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEDE7F6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Content Creator",
                            tint = Color(0xFF7B1FA2),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Content Creator Role",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (userTeacherRole?.isContentCreator == true) "Creator badge active on your profile" else "Publish specialized tutorials and media",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = userTeacherRole?.isContentCreator == true,
                    onCheckedChange = { viewModel.toggleContentCreator(it) },
                    modifier = Modifier.testTag("profile_creator_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF7B1FA2)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Teacher Role & Dashboard Card
        val isTeacherProfile = user?.isTeacher == true || userTeacherRole?.isApprovedTeacher == true || userTeacherRole?.status == "Teacher" || userTeacherRole?.status == "Trusted Teacher" || userTeacherRole?.status == "APPROVED"
        val teacherStatus = if (userTeacherRole?.isTrustedTeacher == true) "Trusted Teacher" else if (isTeacherProfile) "Approved Teacher" else (userTeacherRole?.status ?: "Normal User")
        if (isTeacherProfile) {
            // Full Teacher Dashboard
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LenoPrimaryLight)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(com.example.ui.theme.LenoPrimaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = "Teacher",
                                    tint = com.example.ui.theme.LenoPrimaryDark,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Teacher Dashboard",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Manage your Leno classes & students",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (teacherStatus == "Trusted Teacher") Color(0xFF1976D2) else com.example.ui.theme.LenoPrimary,
                            contentColor = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (teacherStatus == "Trusted Teacher") {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = "$teacherStatus ✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Subjects and About
                    val teacherSingleSubject = user?.effectiveSubject?.ifBlank { userTeacherRole?.effectiveApprovedSubject ?: "" } ?: (userTeacherRole?.effectiveApprovedSubject ?: "")
                    if (teacherSingleSubject.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Assigned Subject: ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$teacherSingleSubject (1 Subject Assigned)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (!userTeacherRole?.subjects.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Assigned Subject: ${userTeacherRole?.subjects}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!userTeacherRole?.teacherIntro.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "About: ${userTeacherRole?.teacherIntro}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Basic Class Statistics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = com.example.ui.theme.LenoPrimaryVeryLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = createdClasses.size.toString(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.example.ui.theme.LenoPrimaryDark
                                )
                                Text(
                                    text = "Classes",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = com.example.ui.theme.LenoPrimaryVeryLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val totalStudents = createdClasses.sumOf { it.enrolledStudentsCount }
                                Text(
                                    text = totalStudents.toString(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.example.ui.theme.LenoPrimaryDark
                                )
                                Text(
                                    text = "Students",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = com.example.ui.theme.LenoPrimaryVeryLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Active",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.example.ui.theme.LenoPrimaryDark
                                )
                                Text(
                                    text = "Status",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Real-time Settlement & Bank Account Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LenoPrimaryLight)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalance,
                                        contentDescription = null,
                                        tint = com.example.ui.theme.LenoPrimaryDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Settlement & Payouts",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = com.example.ui.theme.LenoPrimaryLight
                                ) {
                                    Text(
                                        text = "100% Student Fee Payout",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.LenoPrimaryDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Available Balance",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "₦" + String.format("%,.2f", userTeacherRole?.availableBalance ?: 0.0),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.LenoPrimaryDark
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Total Earned",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "₦" + String.format("%,.2f", userTeacherRole?.totalEarnings ?: 0.0),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Settlement Account status
                            val hasBank = !userTeacherRole?.accountNumber.isNullOrBlank()
                            if (hasBank) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${userTeacherRole?.bankName} • ${userTeacherRole?.accountNumber}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = userTeacherRole?.accountName ?: "",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = { showBankSettlementDialog = true },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Edit Bank", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFFF3E0),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Link bank account to receive 100% student enrollment fees",
                                            fontSize = 11.sp,
                                            color = Color(0xFFE65100),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = { showBankSettlementDialog = true },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Link Bank", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Request Payout Button
                            Button(
                                onClick = {
                                    if (!hasBank) {
                                        showBankSettlementDialog = true
                                    } else {
                                        showPayoutDialog = true
                                    }
                                },
                                enabled = (userTeacherRole?.availableBalance ?: 0.0) > 0.0,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.LenoPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Withdraw to Bank (Payout)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dashboard Action Buttons: Create Class & Manage Classes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showCreateClassDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.LenoPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("teacher_create_class_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Class", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { manageClassesExpanded = !manageClassesExpanded },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("teacher_manage_classes_btn")
                        ) {
                            Text(
                                text = if (manageClassesExpanded) "Hide Classes" else "Manage Classes",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // My Classes List (when expanded or if classes exist)
                    if (manageClassesExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "My Classes (${createdClasses.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (createdClasses.isEmpty()) {
                            Text(
                                text = "You haven't created any classes yet. Tap \"Create Class\" above to get started!",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                createdClasses.forEach { cls ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cls.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${cls.subject} • ${cls.level} • ${cls.enrolledStudentsCount} students",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteClass(cls.classId) {
                                                        Toast.makeText(context, "Class deleted", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Class",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
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
        } else if (teacherStatus == "Pending" || userTeacherRole?.status == "PENDING") {
            // Pending application card with real-time verification and approval
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFF3E0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "Pending",
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Teacher Application: Under Review ⏳",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val appliedSubj = userTeacherRole?.effectiveApprovedSubject?.ifBlank { userTeacherRole?.subjects ?: "" } ?: (userTeacherRole?.subjects ?: "Maths")
                            Text(
                                text = "Your application to teach $appliedSubj is under review by administrator. Only one application is permitted. Teacher roles are never automatically granted.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showTeacherAppDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Application Details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else if (teacherStatus.equals("REJECTED", ignoreCase = true) || userTeacherRole?.status.equals("REJECTED", ignoreCase = true)) {
            // Rejected application card - no re-apply
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Declined",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Teacher Application: Not Approved",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Your application to become a teacher was reviewed and was not approved. Applications can only be submitted once.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Become a Teacher Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LenoPrimaryLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(com.example.ui.theme.LenoPrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "Become a Teacher",
                                tint = com.example.ui.theme.LenoPrimaryDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Become a Teacher",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Share your knowledge with the Leno community.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { showTeacherAppDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.LenoPrimary),
                        modifier = Modifier.testTag("profile_become_teacher_btn")
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // My Enrolled Classes (Learning Area)
        if (enrolledClasses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "My Enrolled Classes (${enrolledClasses.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        enrolledClasses.forEach { cls ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = com.example.ui.theme.LenoPrimaryVeryLight,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedClassForDetail = cls }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cls.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${cls.subject} • Instructor: ${cls.instructorName}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { selectedClassForDetail = cls },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Go to Class", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Info details card - NO phone number
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileInfoRow(
                    icon = Icons.Default.Badge,
                    label = "Leno ID",
                    value = linoIdValue.ifBlank { "Not generated" },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(linoIdValue))
                        Toast.makeText(context, "Leno ID copied: $linoIdValue", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
                ProfileInfoRow(icon = Icons.Default.AlternateEmail, label = "Username", value = "@${user?.username ?: ""}")
                Spacer(modifier = Modifier.height(14.dp))
                ProfileInfoRow(icon = Icons.Default.Person, label = "Full Name", value = user?.displayName ?: "")
                Spacer(modifier = Modifier.height(14.dp))
                ProfileInfoRow(icon = Icons.Default.Info, label = "Status Message", value = user?.statusMessage ?: "Hey there! I am using Leno.")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Protect Your Account Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToProtectAccount() }
                .testTag("profile_protect_account_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LenoBorderDefault)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isProtected = user?.isAccountProtected == true
                val statusColor = if (isProtected) com.example.ui.theme.LenoSuccess else com.example.ui.theme.LenoPrimary
                val statusBgColor = if (isProtected) com.example.ui.theme.LenoSuccessBg else com.example.ui.theme.LenoPrimaryVeryLight

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(statusBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isProtected) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = "Protect Your Account",
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Protect Your Account",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isProtected) {
                            Text(
                                text = "Protected ✓",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.LenoSuccess
                            )
                        } else {
                            Text(
                                text = "Recommended",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.LenoPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Add a recovery method so you can regain access to your Leno account if you forget your password.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Account & Logout Card
        var showLogoutConfirmDialog by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogoutConfirmDialog = true }
                    .padding(16.dp)
                    .testTag("profile_logout_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Log Out",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Log Out",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Sign out of your Leno account on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showLogoutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmDialog = false },
                title = { Text("Log Out?") },
                text = { Text("Are you sure you want to log out of your Leno account?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutConfirmDialog = false
                            viewModel.logout {
                                onLogout()
                            }
                            onLogout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_logout_button")
                    ) {
                        Text("Log Out", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // Avatar Selection Options Dialog
    if (showAvatarOptionsDialog && user != null && user.isOfficial != true) {
        AlertDialog(
            onDismissRequest = { showAvatarOptionsDialog = false },
            title = { Text("Profile Picture", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose how you would like to update your Leno profile photo:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAvatarOptionsDialog = false
                                galleryLauncher.launch("image/*")
                            }
                            .testTag("upload_gallery_option"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Upload from Gallery", fontWeight = FontWeight.Bold)
                                Text("Pick any photo from your device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAvatarOptionsDialog = false
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                            .testTag("take_photo_option"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Take Photo", fontWeight = FontWeight.Bold)
                                Text("Use camera to snap a new photo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAvatarOptionsDialog = false
                                showEditDialog = true
                            }
                            .testTag("edit_profile_dialog_option"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Edit Profile & Presets", fontWeight = FontWeight.Bold)
                                Text("Choose avatar presets or change bio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Profile Modal Dialog
    if (showEditDialog && user != null && user.isOfficial != true) {
        var editName by remember { mutableStateOf(user.displayName) }
        var editUsername by remember { mutableStateOf(user.username) }
        var editBio by remember { mutableStateOf(user.bio) }
        var editStatus by remember { mutableStateOf(user.statusMessage) }
        var editAvatarUrl by remember { mutableStateOf(user.avatarUrl) }
        var isSavingProfile by remember { mutableStateOf(false) }

        val dialogGalleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val photoFile = File(context.filesDir, "profile_avatar_${System.currentTimeMillis()}.jpg")
                    photoFile.outputStream().use { out ->
                        inputStream?.copyTo(out)
                    }
                    editAvatarUrl = photoFile.absolutePath
                } catch (e: Exception) {
                    editAvatarUrl = uri.toString()
                }
            }
        }

        val dialogCameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview()
        ) { bitmap: Bitmap? ->
            val photoPath = if (bitmap != null) {
                try {
                    val photoFile = File(context.filesDir, "profile_photo_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(photoFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    photoFile.absolutePath
                } catch (e: Exception) {
                    editAvatarUrl
                }
            } else {
                editAvatarUrl
            }
            editAvatarUrl = photoPath
        }

        val dialogCameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                try {
                    dialogCameraLauncher.launch(null)
                } catch (_: Exception) {}
            }
        }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Profile Photo section with Gallery and Camera options
                    Text("Profile Photo", fontWeight = FontWeight.SemiBold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { dialogGalleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f).testTag("dialog_gallery_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gallery", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { dialogCameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.weight(1f).testTag("dialog_camera_button")
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Camera", fontSize = 12.sp)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LenoAvatar(
                            avatarUrl = editAvatarUrl,
                            size = 56.dp,
                            isOfficial = false,
                            contentDescription = "Selected Avatar"
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (editAvatarUrl.isNotBlank()) "Photo selected" else "No custom photo set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (editAvatarUrl.isNotBlank()) {
                                TextButton(
                                    onClick = { editAvatarUrl = "" },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                                ) {
                                    Text("Remove Photo", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Display Name
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("edit_display_name_input")
                    )

                    // Username
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("edit_username_input")
                    )

                    // Leno ID (Read-only with Copy button)
                    OutlinedTextField(
                        value = linoIdValue,
                        onValueChange = { /* Read only */ },
                        readOnly = true,
                        label = { Text("Leno ID") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(linoIdValue))
                                    Toast.makeText(context, "Leno ID copied: $linoIdValue", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("edit_profile_copy_lino_id")
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy Leno ID",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        supportingText = {
                            Text("Permanent & unique identity for Leno (Read-only)")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("edit_lino_id_field")
                    )

                    // Status Message
                    OutlinedTextField(
                        value = editStatus,
                        onValueChange = { editStatus = it },
                        label = { Text("Status Message") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("edit_status_input")
                    )

                    // Bio
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Bio") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_bio_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSavingProfile = true
                        viewModel.updateProfile(
                            displayName = editName,
                            username = editUsername.trim().replace("@", ""),
                            bio = editBio,
                            avatarUrl = editAvatarUrl,
                            statusMessage = editStatus
                        ) { result ->
                            isSavingProfile = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                showEditDialog = false
                            } else {
                                Toast.makeText(
                                    context,
                                    result.exceptionOrNull()?.message ?: "Failed to save profile changes.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isSavingProfile,
                    modifier = Modifier.testTag("save_profile_button")
                ) {
                    if (isSavingProfile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Text("Save Changes")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditDialog = false },
                    enabled = !isSavingProfile
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAdminApplicationsDialog) {
        AdminTeacherApplicationsDialog(
            viewModel = viewModel,
            onDismiss = { showAdminApplicationsDialog = false }
        )
    }

    if (showTeacherAppDialog) {
        val existingApp = teacherApplications.firstOrNull { it.userId == currentUser?.userId }
        TeacherApplicationDialog(
            currentUser = currentUser,
            teacherRole = userTeacherRole,
            existingApplication = existingApp,
            onDismiss = { showTeacherAppDialog = false },
            onSubmit = { subject, level, qual, exp, intro, cert, sampleTeaching, agreed ->
                viewModel.submitTeacherApplication(
                    subject = subject,
                    teachingLevel = level,
                    educationQualification = qual,
                    teachingExperience = exp,
                    teacherIntro = intro,
                    certificateDocumentName = cert,
                    sampleTeachingInfo = sampleTeaching,
                    agreedToGuidelines = agreed
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    if (showCreateClassDialog) {
        val approvedSubject = userTeacherRole?.effectiveApprovedSubject ?: "Mathematics"
        CreateClassDialog(
            approvedSubject = approvedSubject,
            onDismiss = { showCreateClassDialog = false },
            onCreateClass = { title, subject, level, shortDesc, content, img, optImgs, vid, isPaid, price, sched, lType, status ->
                viewModel.createClass(
                    title = title,
                    subject = subject,
                    level = level,
                    shortDescription = shortDesc,
                    lessonContent = content,
                    imageUrl = img,
                    optionalImages = optImgs,
                    videoUrl = vid,
                    isPaid = isPaid,
                    price = price,
                    schedule = sched,
                    lessonType = lType,
                    status = status
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    selectedClassForDetail?.let { detailClass ->
        val classLessons by viewModel.getLessonsForClass(detailClass.classId).collectAsState(initial = emptyList<LessonEntity>())
        ClassDetailDialog(
            classItem = detailClass,
            isEnrolled = true,
            isInstructor = currentUser?.userId == detailClass.instructorUserId,
            lessons = classLessons,
            onDismiss = { selectedClassForDetail = null },
            onJoin = {},
            onChatWithInstructor = {
                selectedClassForDetail = null
                onOpenChat(detailClass.instructorUserId)
            }
        )
    }

    if (showBankSettlementDialog) {
        var bankName by remember { mutableStateOf(userTeacherRole?.bankName?.ifBlank { "OPay" } ?: "OPay") }
        var accountNumber by remember { mutableStateOf(userTeacherRole?.accountNumber ?: "") }
        var accountName by remember { mutableStateOf(userTeacherRole?.accountName?.ifBlank { currentUser?.displayName ?: "" } ?: (currentUser?.displayName ?: "")) }
        val nigerianBanks = listOf("OPay", "Kuda Bank", "PalmPay", "Moniepoint", "GTBank", "Zenith Bank", "Access Bank", "First Bank", "UBA")

        AlertDialog(
            onDismissRequest = { showBankSettlementDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = com.example.ui.theme.LenoPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bank Settlement Account", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "100% of student enrollment fees for your classes will be credited directly to this Nigerian bank account.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("Popular Banks", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(nigerianBanks) { bank ->
                            val isSelected = bankName.equals(bank, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) com.example.ui.theme.LenoPrimary else com.example.ui.theme.LenoPrimaryVeryLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) com.example.ui.theme.LenoPrimary else com.example.ui.theme.LenoPrimaryLight),
                                modifier = Modifier.clickable { bankName = bank }
                            ) {
                                Text(
                                    text = bank,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else com.example.ui.theme.LenoPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { if (it.length <= 10 && it.all { ch -> ch.isDigit() }) accountNumber = it },
                        label = { Text("10-Digit Account Number (NUBAN)") },
                        supportingText = { Text("${accountNumber.length}/10 digits") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Account Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateBankSettlementAccount(bankName, accountNumber, accountName) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                showBankSettlementDialog = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.LenoPrimary),
                    enabled = bankName.isNotBlank() && accountNumber.length == 10 && accountName.isNotBlank()
                ) {
                    Text("Save Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBankSettlementDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPayoutDialog) {
        val available = userTeacherRole?.availableBalance ?: 0.0
        var payoutAmountText by remember { mutableStateOf(if (available > 0) String.format("%.0f", available) else "0") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showPayoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = com.example.ui.theme.LenoPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Request Payout Transfer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Transfer your earnings directly to your linked settlement bank account in real time.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = com.example.ui.theme.LenoPrimaryVeryLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Destination Bank Account:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${userTeacherRole?.bankName} • ${userTeacherRole?.accountNumber}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = userTeacherRole?.accountName ?: "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Available Balance:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "₦" + String.format("%,.2f", available),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.LenoPrimaryDark
                        )
                    }

                    OutlinedTextField(
                        value = payoutAmountText,
                        onValueChange = { payoutAmountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Payout Amount (₦)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick percentage chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.25 to "25%", 0.50 to "50%", 1.0 to "100%").forEach { (fraction, label) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val calc = (available * fraction)
                                        payoutAmountText = String.format("%.0f", calc)
                                    }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val amt = payoutAmountText.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        isSubmitting = true
                        viewModel.requestTeacherPayout(amt) { success, msg ->
                            isSubmitting = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) {
                                showPayoutDialog = false
                            }
                        }
                    },
                    enabled = !isSubmitting && amt > 0.0 && amt <= available,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.LenoPrimary)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Confirm Transfer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPayoutDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        if (onCopy != null) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
