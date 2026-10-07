package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.LenoOfficialBadge
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.AlertDialog
import com.example.data.LenoRepository
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.ClassEntity
import com.example.data.entity.UserEntity
import com.example.data.model.TeacherApplicationItem
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.viewmodel.LenoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTeacherApplicationsDialog(
    viewModel: LenoViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val applications by viewModel.teacherApplications.collectAsState()
    val isMonitoring by viewModel.isMonitoringApplications.collectAsState()

    var selectedFilter by remember { mutableStateOf("PENDING") } // "PENDING", "APPROVED", "REJECTED", "ALL"
    var showSeedDialog by remember { mutableStateOf(false) }
    var applicationToReject by remember { mutableStateOf<TeacherApplicationItem?>(null) }
    var processingUserId by remember { mutableStateOf<String?>(null) }

    // Admin multi-tab view: 0 = Teacher Applications, 1 = All Users & Roles, 2 = Activities & Overview
    var activeAdminTab by remember { mutableStateOf(0) }
    val directoryUsers by viewModel.adminDirectoryUsers.collectAsState()
    val allClasses by viewModel.allClasses.collectAsState()
    var userSearchQuery by remember { mutableStateOf("") }
    var selectedUserRoleFilter by remember { mutableStateOf("ALL") } // "ALL", "TEACHERS", "USERS"
    var userToManageRole by remember { mutableStateOf<UserEntity?>(null) }
    var userToConfirmDelete by remember { mutableStateOf<UserEntity?>(null) }
    var userToConfirmRestrict by remember { mutableStateOf<UserEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.startTeacherApplicationsMonitor()
        viewModel.refreshTeacherApplications()
        viewModel.loadAdminDirectoryUsers()
    }

    LaunchedEffect(activeAdminTab) {
        if (activeAdminTab >= 1) {
            viewModel.loadAdminDirectoryUsers()
        }
    }

    val pendingCount = applications.count { it.isPending }
    val approvedCount = applications.count { it.isApproved }
    val rejectedCount = applications.count { it.isRejected }

    val filteredList = when (selectedFilter) {
        "PENDING" -> applications.filter { it.isPending }
        "APPROVED" -> applications.filter { it.isApproved }
        "REJECTED" -> applications.filter { it.isRejected }
        else -> applications
    }

    // Pulsing live indicator for Firestore listener
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = LenoPrimaryVeryLight,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = LenoPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Owner & Admin Console",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Approvals, Users Directory & Activities",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                viewModel.refreshTeacherApplications()
                                Toast.makeText(context, "Refreshing teacher_applications collection...", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = LenoPrimary
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Live Firestore Connection Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LenoPrimaryVeryLight,
                    border = BorderStroke(1.dp, LenoPrimaryLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = alphaAnim))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Monitoring 'teacher_applications' collection",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LenoPrimaryDark
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Firestore Live",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Tabs: Applications vs Users & Roles vs Activities & Stats
                TabRow(
                    selectedTabIndex = activeAdminTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = LenoPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeAdminTab]),
                            color = LenoPrimary
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Tab(
                        selected = activeAdminTab == 0,
                        onClick = { activeAdminTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Applications",
                                    fontWeight = if (activeAdminTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                                if (pendingCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFD97706)
                                    ) {
                                        Text(
                                            text = "$pendingCount",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = activeAdminTab == 1,
                        onClick = {
                            activeAdminTab = 1
                            viewModel.loadAdminDirectoryUsers()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Users & Roles",
                                    fontWeight = if (activeAdminTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )
                    Tab(
                        selected = activeAdminTab == 2,
                        onClick = {
                            activeAdminTab = 2
                            viewModel.loadAdminDirectoryUsers()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Activities",
                                    fontWeight = if (activeAdminTab == 2) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )
                }

                when (activeAdminTab) {
                    0 -> {
                        // TAB 0: Teacher Applications Monitoring View
                    // Metrics Dashboard Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            title = "Pending",
                            count = pendingCount,
                            color = Color(0xFFD97706),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Approved",
                            count = approvedCount,
                            color = Color(0xFF059669),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Rejected",
                            count = rejectedCount,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Bar: Filter Chips & Seed Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedFilter == "PENDING",
                                    onClick = { selectedFilter = "PENDING" },
                                    label = { Text("Pending ($pendingCount)", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LenoPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == "APPROVED",
                                    onClick = { selectedFilter = "APPROVED" },
                                    label = { Text("Approved ($approvedCount)", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LenoPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == "REJECTED",
                                    onClick = { selectedFilter = "REJECTED" },
                                    label = { Text("Rejected ($rejectedCount)", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LenoPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == "ALL",
                                    onClick = { selectedFilter = "ALL" },
                                    label = { Text("All (${applications.size})", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LenoPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        TextButton(
                            onClick = { showSeedDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = LenoPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Test Sample", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Applications List
                    if (filteredList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.HourglassEmpty,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (selectedFilter == "PENDING") "No pending applications" else "No applications in this category",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "New submissions to the 'teacher_applications' collection will automatically appear here in real-time.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showSeedDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary)
                                ) {
                                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Generate Test Application", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredList, key = { it.applicationId.ifBlank { it.userId } }) { item ->
                                val isProcessing = processingUserId == item.userId
                                ApplicationCard(
                                    application = item,
                                    isProcessing = isProcessing,
                                    onApprove = {
                                        processingUserId = item.userId
                                        viewModel.approveTeacherApplicationByAdmin(item.userId, item.effectiveSubject) { success, message ->
                                            processingUserId = null
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onReject = {
                                        applicationToReject = item
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: All Users & Role Management (Not only teachers, every user)
                    val teachersCount = directoryUsers.count { it.isTeacher || it.role.equals("TEACHER", ignoreCase = true) }
                    val membersCount = directoryUsers.count { !it.isTeacher && !it.role.equals("TEACHER", ignoreCase = true) }

                    OutlinedTextField(
                        value = userSearchQuery,
                        onValueChange = { userSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        placeholder = { Text("Search any user by name, @username, or email...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = LenoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (userSearchQuery.isNotBlank()) {
                                IconButton(onClick = { userSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Role filter chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedUserRoleFilter == "ALL",
                                onClick = { selectedUserRoleFilter = "ALL" },
                                label = { Text("All Users (${directoryUsers.size})", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LenoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedUserRoleFilter == "TEACHERS",
                                onClick = { selectedUserRoleFilter = "TEACHERS" },
                                label = { Text("Teachers ($teachersCount)", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LenoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedUserRoleFilter == "USERS",
                                onClick = { selectedUserRoleFilter = "USERS" },
                                label = { Text("Members ($membersCount)", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LenoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    val filteredUsers = remember(directoryUsers, userSearchQuery, selectedUserRoleFilter) {
                        val q = userSearchQuery.trim().lowercase()
                        directoryUsers.filter { u ->
                            val isT = u.isTeacher || u.role.equals("TEACHER", ignoreCase = true)
                            val roleMatch = when (selectedUserRoleFilter) {
                                "TEACHERS" -> isT
                                "USERS" -> !isT
                                else -> true
                            }
                            val queryMatch = q.isEmpty() ||
                                u.displayName.lowercase().contains(q) ||
                                u.username.lowercase().contains(q) ||
                                u.email.lowercase().contains(q) ||
                                u.userId.lowercase().contains(q) ||
                                u.assignedSubject.lowercase().contains(q)
                            roleMatch && queryMatch
                        }
                    }

                    if (filteredUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Group,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No users found",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Try adjusting your search query or role filter.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredUsers, key = { it.userId }) { u ->
                                val isProcessing = processingUserId == u.userId
                                UserAdminCard(
                                    user = u,
                                    isProcessing = isProcessing,
                                    onPromoteToTeacher = { userToManageRole = u },
                                    onDemoteToUser = {
                                        processingUserId = u.userId
                                        viewModel.updateUserRoleByAdmin(u.userId, "user") { success, msg ->
                                            processingUserId = null
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onToggleRestrict = {
                                        userToConfirmRestrict = u
                                    },
                                    onDeleteUser = {
                                        userToConfirmDelete = u
                                    }
                                )
                            }
                        }
                    }
                }
                else -> {
                    // TAB 2: Owner Activities, Approved Teachers & Platform Metrics
                        OwnerActivitiesOverview(
                            directoryUsers = directoryUsers,
                            applications = applications,
                            allClasses = allClasses,
                            onManageUser = { u -> userToManageRole = u },
                            onFilterApplications = { filter ->
                                activeAdminTab = 0
                                selectedFilter = filter
                            },
                            onRefreshData = {
                                viewModel.refreshTeacherApplications()
                                viewModel.loadAdminDirectoryUsers()
                                Toast.makeText(context, "Refreshed platform data", Toast.LENGTH_SHORT).show()
                            },
                            onBroadcast = { title, body, cat, photo, onResult ->
                                viewModel.broadcastOfficialAnnouncement(title, body, cat, photo, onResult)
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog to generate a sample application
    if (showSeedDialog) {
        var sampleSubject by remember { mutableStateOf("Maths") }
        val subjects = listOf("Maths", "Quran", "JAMB", "Skills", "Online Skills", "Content Creator")

        AlertDialog(
            onDismissRequest = { showSeedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = LenoPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Test Application", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This will write a test applicant to the 'teacher_applications' collection in Firestore so you can test real-time monitoring and role elevation to 'teacher'.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Select Subject:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(subjects) { subj ->
                            val isSelected = sampleSubject == subj
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) LenoPrimary else LenoPrimaryVeryLight,
                                border = BorderStroke(1.dp, if (isSelected) LenoPrimary else LenoPrimaryLight),
                                modifier = Modifier.clickable { sampleSubject = subj }
                            ) {
                                Text(
                                    text = subj,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else LenoPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.seedSampleTeacherApplication(sampleSubject) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                showSeedDialog = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary)
                ) {
                    Text("Submit to teacher_applications", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSeedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for rejection reason
    if (applicationToReject != null) {
        val target = applicationToReject!!
        var rejectionReason by remember { mutableStateOf("Academic credentials could not be verified") }

        AlertDialog(
            onDismissRequest = { applicationToReject = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Cancel, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reject Application", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Reject teacher application for ${target.applicantName} (${target.effectiveSubject}). Status in 'teacher_applications' will be marked as REJECTED.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("Reason for Rejection") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rejectTeacherApplicationByAdmin(target.userId, rejectionReason) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            applicationToReject = null
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Confirm Rejection", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { applicationToReject = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for Admin to assign or modify user roles and subjects (Rule: One Teacher = One Subject)
    val targetRoleUser = userToManageRole
    if (targetRoleUser != null) {
        val target = targetRoleUser
        val isCurrentlyTeacher = target.isTeacher || target.role.equals("TEACHER", ignoreCase = true)
        val standardSubjects = listOf(
            "Maths",
            "Quran",
            "JAMB",
            "Skills",
            "Online Skills",
            "Content Creator"
        )
        var selectedSubject by remember(target.userId) {
            mutableStateOf(target.assignedSubject.ifBlank { "Maths" })
        }
        var customSubject by remember(target.userId) { mutableStateOf("") }
        var isCustomSubject by remember(target.userId) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { userToManageRole = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = LenoPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCurrentlyTeacher) "Edit Teacher Subject" else "Assign Teacher Role",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "User: ${target.displayName.ifBlank { target.userId }} (@${target.username.ifBlank { "user" }})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Update this user's profile document role to 'teacher' in Firestore. Rule: 'One Teacher = One Subject' is strictly enforced.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Select Assigned Subject:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = LenoPrimary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(standardSubjects) { subj ->
                            FilterChip(
                                selected = !isCustomSubject && selectedSubject == subj,
                                onClick = {
                                    isCustomSubject = false
                                    selectedSubject = subj
                                },
                                label = { Text(subj, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LenoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = isCustomSubject,
                            onClick = { isCustomSubject = true },
                            label = { Text("Custom Subject", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LenoPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    if (isCustomSubject) {
                        OutlinedTextField(
                            value = customSubject,
                            onValueChange = { customSubject = it },
                            label = { Text("Custom Subject Name") },
                            placeholder = { Text("e.g. Further Mathematics") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalSubject = if (isCustomSubject && customSubject.isNotBlank()) customSubject.trim() else selectedSubject
                        processingUserId = target.userId
                        viewModel.updateUserRoleByAdmin(target.userId, "teacher", finalSubject) { success, msg ->
                            processingUserId = null
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            userToManageRole = null
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary)
                ) {
                    Text("Confirm & Update Role", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToManageRole = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (userToConfirmRestrict != null) {
        val target = userToConfirmRestrict!!
        val isRestricting = !target.isRestricted
        AlertDialog(
            onDismissRequest = { userToConfirmRestrict = null },
            title = {
                Text(if (isRestricting) "Restrict User Account?" else "Lift Account Restriction?")
            },
            text = {
                Text(
                    if (isRestricting)
                        "Are you sure you want to restrict @${target.username}? The user will be barred from messaging, posting, and broadcasting on Leno."
                    else
                        "Lift restriction on @${target.username}? Their account privileges will be fully restored."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = target.userId
                        userToConfirmRestrict = null
                        processingUserId = uid
                        viewModel.setUserRestricted(uid, isRestricting) { success, msg ->
                            processingUserId = null
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRestricting) Color(0xFFD97706) else LenoPrimary
                    )
                ) {
                    Text(if (isRestricting) "Restrict User" else "Lift Restriction")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToConfirmRestrict = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (userToConfirmDelete != null) {
        val target = userToConfirmDelete!!
        AlertDialog(
            onDismissRequest = { userToConfirmDelete = null },
            title = {
                Text("Delete User Account?")
            },
            text = {
                Text(
                    "Are you sure you want to permanently delete user @${target.username} (${target.displayName})? All user data, permissions, and profile records will be permanently erased."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = target.userId
                        userToConfirmDelete = null
                        processingUserId = uid
                        viewModel.deleteUserByAdmin(uid) { success, msg ->
                            processingUserId = null
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete User", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToConfirmDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun ApplicationCard(
    application: TeacherApplicationItem,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val dateStr = remember(application.submittedAt) {
        if (application.submittedAt > 0) {
            SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(application.submittedAt))
        } else "Recently"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Applicant info & Status chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = LenoPrimaryVeryLight,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = LenoPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = application.applicantName.ifBlank { "Leno Applicant" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (application.applicantEmail.isNotBlank()) application.applicantEmail else application.userId,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Pill
                StatusBadge(status = application.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subject and Teaching Level banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = LenoPrimaryVeryLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = LenoPrimaryDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Subject: ${application.effectiveSubject}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = LenoPrimaryDark
                        )
                    }

                    Text(
                        text = application.teachingLevel,
                        fontSize = 11.sp,
                        color = LenoPrimaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Qualification & Experience
            if (application.educationQualification.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("🎓", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = application.educationQualification,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (application.teachingExperience.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("⏱️", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = application.teachingExperience,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (application.teacherIntro.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${application.teacherIntro}\"",
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Timestamp footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Submitted: $dateStr",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (application.isApproved && application.reviewedAt > 0) {
                    val approvedDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(application.reviewedAt))
                    Text(
                        text = "Approved: $approvedDate",
                        fontSize = 10.sp,
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Action Buttons for PENDING application
            if (application.isPending) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onApprove,
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier.weight(2f)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "Approve (Role -> 'teacher')",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bgColor, textColor, label) = when (status.uppercase()) {
        "APPROVED" -> Triple(Color(0xFFD1FAE5), Color(0xFF065F46), "APPROVED")
        "REJECTED" -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), "REJECTED")
        else -> Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "PENDING REVIEW")
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun UserAdminCard(
    user: UserEntity,
    isProcessing: Boolean,
    onPromoteToTeacher: () -> Unit,
    onDemoteToUser: () -> Unit,
    onToggleRestrict: () -> Unit,
    onDeleteUser: () -> Unit
) {
    val isTeacher = user.isTeacher || user.role.equals("TEACHER", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LenoAvatar(
                    avatarUrl = user.avatarUrl,
                    size = 44.dp,
                    isOfficial = user.isOfficial,
                    username = user.username,
                    userId = user.userId
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.displayName.ifBlank { "User ${user.userId.take(6)}" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (user.isOfficial) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = LenoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = if (user.username.isNotBlank()) "@${user.username}" else user.email.ifBlank { user.userId },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Current Role and Restriction Badges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (user.isRestricted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = "RESTRICTED",
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isTeacher) Color(0xFFD1FAE5) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (isTeacher) "TEACHER" else "USER",
                            color = if (isTeacher) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (isTeacher && user.assignedSubject.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = LenoPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Assigned Subject: ${user.assignedSubject}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LenoPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LenoPrimary)
                } else {
                    if (isTeacher) {
                        OutlinedButton(
                            onClick = onPromoteToTeacher,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Subject", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(
                            onClick = onDemoteToUser,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Demote", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onPromoteToTeacher,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Make Teacher", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Restrict / Unrestrict button
                    OutlinedButton(
                        onClick = onToggleRestrict,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (user.isRestricted) LenoPrimary else Color(0xFFD97706)
                        ),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = if (user.isRestricted) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (user.isRestricted) "Unrestrict" else "Restrict",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Delete User button (for non-owner/non-system accounts)
                    if (!user.isOfficial && user.userId != "usr_officialjaiby_2026" && !user.isOwner) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onDeleteUser,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete User",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ApprovedTeacherRecord(
    val userId: String,
    val name: String,
    val username: String,
    val email: String,
    val subject: String,
    val userEntity: UserEntity?
)

data class PlatformActivityEvent(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: Long,
    val type: String, // "TEACHER_APPROVED", "TEACHER_PENDING", "CLASS_CREATED", "USER_REGISTERED"
    val badge: String,
    val badgeColor: Color
)

private fun formatPlatformRelativeTime(millis: Long): String {
    if (millis <= 0) return "Recently"
    val diff = System.currentTimeMillis() - millis
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(millis))
    }
}

@Composable
private fun OwnerActivitiesOverview(
    directoryUsers: List<UserEntity>,
    applications: List<TeacherApplicationItem>,
    allClasses: List<ClassEntity>,
    onManageUser: (UserEntity) -> Unit,
    onFilterApplications: (String) -> Unit,
    onRefreshData: () -> Unit,
    onBroadcast: (title: String, body: String, category: String, photoPath: String?, onResult: (Boolean, String) -> Unit) -> Unit
) {
    val pendingCount = applications.count { it.isPending }

    // Aggregate unique approved teachers from applications and users table
    val approvedTeachers = remember(directoryUsers, applications) {
        val seen = mutableSetOf<String>()
        val list = mutableListOf<ApprovedTeacherRecord>()
        applications.filter { it.isApproved }.forEach { app ->
            if (seen.add(app.userId)) {
                val user = directoryUsers.find { it.userId == app.userId }
                list.add(
                    ApprovedTeacherRecord(
                        userId = app.userId,
                        name = app.applicantName.ifBlank { user?.displayName ?: user?.username ?: "Teacher" },
                        username = app.applicantUsername.ifBlank { user?.username ?: "" },
                        email = app.applicantEmail.ifBlank { user?.email ?: "" },
                        subject = app.effectiveSubject,
                        userEntity = user
                    )
                )
            }
        }
        directoryUsers.filter { it.isTeacher || it.role.equals("TEACHER", true) }.forEach { u ->
            if (seen.add(u.userId)) {
                list.add(
                    ApprovedTeacherRecord(
                        userId = u.userId,
                        name = u.displayName.ifBlank { u.username },
                        username = u.username,
                        email = u.email,
                        subject = u.assignedSubject.ifBlank { "Accredited Teacher" },
                        userEntity = u
                    )
                )
            }
        }
        list
    }

    // Build timeline of platform activity events
    val activityEvents = remember(applications, allClasses, directoryUsers) {
        val events = mutableListOf<PlatformActivityEvent>()

        applications.filter { it.isApproved }.forEach { app ->
            events.add(
                PlatformActivityEvent(
                    id = "appr_${app.applicationId.ifBlank { app.userId }}",
                    title = "Teacher Approved ✓",
                    description = "${app.applicantName} accredited for ${app.effectiveSubject}",
                    timestamp = if (app.submittedAt > 0) app.submittedAt else System.currentTimeMillis() - 3600000,
                    type = "TEACHER_APPROVED",
                    badge = "Approved",
                    badgeColor = Color(0xFF059669)
                )
            )
        }

        applications.filter { it.isPending }.forEach { app ->
            events.add(
                PlatformActivityEvent(
                    id = "pend_${app.applicationId.ifBlank { app.userId }}",
                    title = "Teacher Application Submitted",
                    description = "${app.applicantName} applied for ${app.effectiveSubject}",
                    timestamp = if (app.submittedAt > 0) app.submittedAt else System.currentTimeMillis() - 1800000,
                    type = "TEACHER_PENDING",
                    badge = "Pending Review",
                    badgeColor = Color(0xFFD97706)
                )
            )
        }

        allClasses.forEach { cls ->
            events.add(
                PlatformActivityEvent(
                    id = "cls_${cls.classId}",
                    title = "Class Published",
                    description = "'${cls.title}' in ${cls.subject} by ${cls.instructorName.ifBlank { cls.instructorUsername }}",
                    timestamp = if (cls.createdAt > 0) cls.createdAt else System.currentTimeMillis() - 7200000,
                    type = "CLASS_CREATED",
                    badge = cls.level,
                    badgeColor = LenoPrimary
                )
            )
        }

        directoryUsers.take(8).forEach { u ->
            events.add(
                PlatformActivityEvent(
                    id = "usr_${u.userId}",
                    title = "User Registered",
                    description = "@${u.username} joined Leno",
                    timestamp = if (u.createdAt > 0) u.createdAt else System.currentTimeMillis() - 14400000,
                    type = "USER_REGISTERED",
                    badge = u.role,
                    badgeColor = Color(0xFF6B7280)
                )
            )
        }

        events.sortedByDescending { it.timestamp }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Platform Metrics Grid
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Platform Summary Metrics",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = onRefreshData,
                            colors = ButtonDefaults.textButtonColors(contentColor = LenoPrimary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            title = "Total Users",
                            count = directoryUsers.size,
                            color = LenoPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Approved Teachers",
                            count = approvedTeachers.size,
                            color = Color(0xFF059669),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            title = "Published Classes",
                            count = allClasses.size,
                            color = Color(0xFF4F46E5),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Pending Applications",
                            count = pendingCount,
                            color = Color(0xFFD97706),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Official Leno Studio & Broadcast Composer
        item {
            OfficialLenoBroadcastStudio(
                onBroadcast = onBroadcast
            )
        }

        // Approved Teachers Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Teacher Management (${approvedTeachers.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        fontSize = 15.sp
                    )
                }
                TextButton(
                    onClick = { onFilterApplications("APPROVED") },
                    colors = ButtonDefaults.textButtonColors(contentColor = LenoPrimary)
                ) {
                    Text("View Applications", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (approvedTeachers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, tint = LenoPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No approved teachers yet.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Review and approve submissions in the Applications tab.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(approvedTeachers, key = { it.userId }) { teacher ->
                val teacherClassesCount = allClasses.count { it.instructorUserId == teacher.userId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, LenoPrimaryLight)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(LenoPrimaryVeryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = LenoPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = teacher.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFD1FAE5)
                                    ) {
                                        Text(
                                            text = teacher.subject,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF047857),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "@${teacher.username} • $teacherClassesCount classes created",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val targetUser = teacher.userEntity ?: UserEntity(
                            userId = teacher.userId,
                            username = teacher.username,
                            displayName = teacher.name,
                            email = teacher.email,
                            role = "TEACHER",
                            assignedSubject = teacher.subject
                        )
                        OutlinedButton(
                            onClick = { onManageUser(targetUser) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manage", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Live Platform Activities Section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = LenoPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Recent Platform Activities (${activityEvents.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    fontSize = 15.sp
                )
            }
        }

        items(activityEvents, key = { it.id }) { event ->
            val icon = when (event.type) {
                "TEACHER_APPROVED" -> Icons.Default.Verified
                "TEACHER_PENDING" -> Icons.Default.HourglassEmpty
                "CLASS_CREATED" -> Icons.Default.Book
                else -> Icons.Default.Person
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(event.badgeColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = event.badgeColor, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = event.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = event.badgeColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = event.badge,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = event.badgeColor,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = event.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = formatPlatformRelativeTime(event.timestamp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficialLenoBroadcastStudio(
    onBroadcast: (title: String, body: String, category: String, photoPath: String?, onResult: (Boolean, String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var postTitle by remember { mutableStateOf("") }
    var postBody by remember { mutableStateOf("") }
    val categories = listOf("Announcement", "Feature Update", "Class Notice", "Security Alert")
    var selectedCategory by remember { mutableStateOf(categories[0]) }
    var attachedPhotoPath by remember { mutableStateOf<String?>(null) }
    var isBroadcasting by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val file = File(context.filesDir, "broadcast_photo_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                attachedPhotoPath = file.absolutePath
            } catch (e: Exception) {
                attachedPhotoPath = uri.toString()
            }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = LenoOfficialBadge.copy(alpha = 0.08f)),
        border = BorderStroke(1.5.dp, LenoOfficialBadge.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("official_leno_broadcast_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OfficialVerifiedBadge(size = 22.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Official Leno Broadcast Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LenoOfficialBadge
                        )
                        Text(
                            text = "Publish updates & gallery media as @leno to all members",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = postTitle,
                onValueChange = { postTitle = it },
                label = { Text("Announcement Title *", fontSize = 12.sp) },
                placeholder = { Text("e.g. New Mathematics & JAMB Classes Published") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("official_broadcast_title_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = postBody,
                onValueChange = { postBody = it },
                label = { Text("Message Body *", fontSize = 12.sp) },
                placeholder = { Text("Write official message broadcast to all users...") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("official_broadcast_body_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LenoOfficialBadge,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Attached Photo Preview or Upload Button
            val currentPhoto = attachedPhotoPath
            if (currentPhoto != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    AsyncImage(
                        model = currentPhoto,
                        contentDescription = "Attached Broadcast Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                    IconButton(
                        onClick = { attachedPhotoPath = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove Photo", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("broadcast_upload_gallery_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = LenoOfficialBadge, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Attach Picture from Gallery", fontSize = 11.sp, color = LenoOfficialBadge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    if (postTitle.isBlank() || postBody.isBlank()) {
                        Toast.makeText(context, "Please enter title and message body.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isBroadcasting = true
                    onBroadcast(postTitle.trim(), postBody.trim(), selectedCategory, attachedPhotoPath) { success, msg ->
                        isBroadcasting = false
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) {
                            postTitle = ""
                            postBody = ""
                            attachedPhotoPath = null
                        }
                    }
                },
                enabled = !isBroadcasting,
                colors = ButtonDefaults.buttonColors(containerColor = LenoOfficialBadge),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("publish_official_broadcast_btn")
            ) {
                if (isBroadcasting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Broadcasting...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publish Broadcast to All Users", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

