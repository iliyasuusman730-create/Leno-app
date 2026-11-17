package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ClassEntity
import com.example.data.entity.ClassQuestionEntity
import com.example.data.entity.LessonEntity
import com.example.data.entity.UserEntity
import androidx.compose.material.icons.filled.HeadsetMic
import com.example.ui.components.AddLessonDialog
import com.example.ui.components.AdminTeacherApplicationsDialog
import com.example.ui.components.ClassDetailDialog
import com.example.ui.components.ClassRoomGroupDialog
import com.example.ui.components.ContactSupportDialog
import com.example.ui.components.CourseCertificateDialog
import com.example.ui.components.CreateClassDialog
import com.example.ui.components.EditClassDialog
import com.example.ui.components.JoinConfirmationDialog
import com.example.ui.components.LessonViewerDialog
import com.example.ui.components.PaymentCheckoutDialog
import androidx.compose.material.icons.filled.Edit
import com.example.ui.theme.LenoActionGreen
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.ui.theme.LocalLenoDarkTheme
import com.example.util.ContinueLearningTarget
import com.example.util.CurriculumSectionHelper
import com.example.util.LessonProgressManager
import com.example.viewmodel.LenoViewModel
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

@Composable
fun ClassScreen(
    viewModel: LenoViewModel,
    onOpenChat: (String) -> Unit,
    onOpenSupportChat: (String) -> Unit = onOpenChat
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedMainTab by remember { mutableIntStateOf(0) } // 0 = Explore Classes, 1 = My Learning

    val currentUser by viewModel.currentUser.collectAsState()
    val publishedClasses by viewModel.publishedClasses.collectAsState()
    val enrolledClasses by viewModel.currentUserEnrolledClasses.collectAsState()
    val userTeacherRole by viewModel.currentUserTeacherRole.collectAsState()
    val updateTick by LessonProgressManager.updateTick.collectAsState()

    LaunchedEffect(currentUser?.userId) {
        viewModel.syncClassesFromRemote()
    }

    val isAdmin = currentUser?.isAdmin == true

    // Conditionally check user's 'role' field in Firestore (synced in real-time to currentUser).
    // The 'Create Class' button is hidden unless the user's role in Firestore is specifically "teacher"
    // and they are confirmed as an approved teacher, or the user is an Admin / App Owner.
    val isApprovedTeacher = remember(currentUser, userTeacherRole, isAdmin) {
        if (isAdmin) return@remember true
        val user = currentUser ?: return@remember false
        val role = userTeacherRole

        val isUserApproved = user.isApprovedTeacher && !user.isPendingTeacher && !user.isRejectedTeacher
        val isRoleApproved = role != null && (role.isApprovedTeacher || role.status.equals("APPROVED", ignoreCase = true) || role.status.equals("Teacher", ignoreCase = true) || role.status.equals("Trusted Teacher", ignoreCase = true)) && !role.isPending && !role.isRejected

        isUserApproved || isRoleApproved
    }

    var showAdminTeacherApplicationsDialog by remember { mutableStateOf(false) }
    var showCreateClassDialog by remember { mutableStateOf(false) }
    var showMustBecomeTeacherDialog by remember { mutableStateOf(false) }
    var selectedClassForDetail by remember { mutableStateOf<ClassEntity?>(null) }
    var selectedClassForGroupRoom by remember { mutableStateOf<ClassEntity?>(null) }
    var selectedClassForCertificate by remember { mutableStateOf<ClassEntity?>(null) }
    var classForPaymentCheckout by remember { mutableStateOf<ClassEntity?>(null) }
    var selectedLessonForView by remember { mutableStateOf<Pair<LessonEntity, ClassEntity>?>(null) }
    var classForAddingLesson by remember { mutableStateOf<ClassEntity?>(null) }
    var classForEditing by remember { mutableStateOf<ClassEntity?>(null) }
    var joinedClassConfirmation by remember { mutableStateOf<ClassEntity?>(null) }
    var showContactSupportDialog by remember { mutableStateOf(false) }

    val enrolledClassIds = remember(enrolledClasses) {
        enrolledClasses.map { it.classId }.toSet()
    }

    val lessonsByClassId = remember { mutableStateMapOf<String, List<LessonEntity>>() }
    LaunchedEffect(enrolledClassIds) {
        enrolledClassIds.forEach { cId ->
            launch {
                viewModel.getLessonsForClass(cId).collect { list ->
                    lessonsByClassId[cId] = list
                }
            }
        }
    }

    val currentUserId = currentUser?.userId ?: ""
    val studyStreak = remember(currentUserId, updateTick) {
        if (currentUserId.isNotBlank()) LessonProgressManager.getStudyStreak(context, currentUserId) else 1
    }
    val continueLearningTarget = remember(
        enrolledClasses,
        lessonsByClassId.toMap(),
        currentUserId,
        updateTick,
        isApprovedTeacher,
        isAdmin
    ) {
        val eligibleEnrolled = enrolledClasses.filter { cls ->
            val isOwner = cls.instructorUserId == currentUserId
            !(isApprovedTeacher && !isOwner && !isAdmin)
        }
        val lastClassId = if (currentUserId.isNotBlank()) {
            LessonProgressManager.getLastOpenedClassId(context, currentUserId)
        } else null
        val completedMap = eligibleEnrolled.associate { cls ->
            cls.classId to if (currentUserId.isNotBlank()) {
                LessonProgressManager.getCompletedLessonIds(context, currentUserId, cls.classId)
            } else emptySet()
        }
        val lastLessonMap = eligibleEnrolled.associate { cls ->
            cls.classId to if (currentUserId.isNotBlank()) {
                LessonProgressManager.getLastOpenedLessonId(context, currentUserId, cls.classId)
            } else null
        }
        CurriculumSectionHelper.selectContinueLearningTarget(
            enrolledClasses = eligibleEnrolled,
            lastOpenedClassId = lastClassId,
            lessonsByClassId = lessonsByClassId,
            completedIdsByClassId = completedMap,
            lastOpenedLessonByClassId = lastLessonMap
        )
    }

    // Official subjects of Leno:
    // Maths, Physics, Chemistry, English, Biology, Quran, JAMB, Computer Science, Economics, Skills, Online Skills, Content Creator
    val filterOptions = listOf(
        "All",
        "Maths",
        "Physics",
        "Chemistry",
        "English",
        "Biology",
        "Quran",
        "JAMB",
        "Computer Science",
        "Economics",
        "Skills",
        "Online Skills",
        "Content Creator"
    )

    // Filtered classes for Explore tab (Only public published classes)
    val filteredExploreClasses = remember(publishedClasses, searchQuery, selectedFilter) {
        val trimmedQuery = searchQuery.trim()
        val isExplicitSearch = trimmedQuery.length >= 3

        publishedClasses.filter { item ->
            val formattedCode = LessonProgressManager.formatClassCode(item.classId)
            val cleanCode = formattedCode.replace("-", "").uppercase()
            val cleanQuery = trimmedQuery.replace("-", "").uppercase()

            val matchesCode = isExplicitSearch && (
                cleanCode.contains(cleanQuery) ||
                item.classId.contains(trimmedQuery, ignoreCase = true)
            )
            val matchesTeacherLinoId = isExplicitSearch && item.instructorLinoId.replace("-", "").uppercase().contains(cleanQuery)
            val matchesTeacherName = isExplicitSearch && item.instructorName.contains(trimmedQuery, ignoreCase = true)

            // Private classes remain hidden from public browsing unless a student explicitly searches the school's Class Code, Teacher Leno ID, or Teacher Name
            if (item.status.equals("PRIVATE", ignoreCase = true)) {
                if (!isExplicitSearch) return@filter false
                if (!matchesCode && !matchesTeacherLinoId && !matchesTeacherName) {
                    return@filter false
                }
            }

            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "JAMB" -> item.subject.equals("JAMB", ignoreCase = true) || item.subject.contains("JAMB", ignoreCase = true)
                "Content Creator" -> item.subject.equals("Content Creator", ignoreCase = true) || item.subject.contains("Creator", ignoreCase = true)
                else -> item.subject.equals(selectedFilter, ignoreCase = true) ||
                        item.subject.contains(selectedFilter, ignoreCase = true)
            }
            val matchesSearch = trimmedQuery.isBlank() ||
                    matchesCode ||
                    matchesTeacherLinoId ||
                    item.title.contains(trimmedQuery, ignoreCase = true) ||
                    item.subject.contains(trimmedQuery, ignoreCase = true) ||
                    item.shortDescription.contains(trimmedQuery, ignoreCase = true) ||
                    item.instructorName.contains(trimmedQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    // Filtered classes for My Learning tab
    val filteredEnrolledClasses = remember(enrolledClasses, searchQuery, selectedFilter) {
        enrolledClasses.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "JAMB" -> item.subject.equals("JAMB", ignoreCase = true) || item.subject.contains("JAMB", ignoreCase = true)
                "Content Creator" -> item.subject.equals("Content Creator", ignoreCase = true) || item.subject.contains("Creator", ignoreCase = true)
                else -> item.subject.equals(selectedFilter, ignoreCase = true) ||
                        item.subject.contains(selectedFilter, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.subject.contains(searchQuery, ignoreCase = true) ||
                    item.shortDescription.contains(searchQuery, ignoreCase = true) ||
                    item.instructorName.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    val isSmall = com.example.util.isSmallScreen()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = com.example.util.MaxContentWidth)
                .padding(horizontal = com.example.util.responsiveHorizontalPadding())
                .testTag("class_screen_root")
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Top Header: "Classes" title + "+ Create" button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Classes",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (isApprovedTeacher || isAdmin) {
                    Button(
                        onClick = {
                            showCreateClassDialog = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("top_create_class_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Create",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            showMustBecomeTeacherDialog = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LenoPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimary.copy(alpha = 0.5f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("top_create_class_button_non_teacher")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = LenoPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Create",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LenoPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Clean, rounded Capsule Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search lessons, subjects or skills...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("class_search_input")
            )

        Spacer(modifier = Modifier.height(10.dp))

        // Categories: All | Maths | Quran | JAMB | Skills | Online Skills | Content Creator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filterOptions.forEach { filterName ->
                val selected = selectedFilter == filterName
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = filterName },
                    label = {
                        Text(
                            text = filterName,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LenoPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("filter_chip_$filterName")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Row: Explore Classes | My Learning (enrolled count)
        TabRow(
            selectedTabIndex = selectedMainTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = LenoPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedMainTab]),
                    color = LenoPrimary
                )
            }
        ) {
            Tab(
                selected = selectedMainTab == 0,
                onClick = { selectedMainTab = 0 },
                text = {
                    Text(
                        text = "Explore Classes (${filteredExploreClasses.size})",
                        fontWeight = if (selectedMainTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.testTag("tab_explore_classes")
            )

            Tab(
                selected = selectedMainTab == 1,
                onClick = { selectedMainTab = 1 },
                text = {
                    Text(
                        text = "My Learning (${enrolledClasses.size})",
                        fontWeight = if (selectedMainTab == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.testTag("tab_my_learning")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val displayClasses = if (selectedMainTab == 0) filteredExploreClasses else filteredEnrolledClasses

        // Class Cards List
        if (displayClasses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = LenoPrimaryLight,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (selectedMainTab == 1) {
                            "You haven't joined any classes yet.\nExplore classes and tap Join to add to My Learning."
                        } else if (searchQuery.isNotEmpty()) {
                            "No classes match \"$searchQuery\""
                        } else {
                            "No published classes in \"$selectedFilter\""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (selectedMainTab == 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { selectedMainTab = 0 },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Browse Classes")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("classes_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 24.dp)
            ) {
                if (continueLearningTarget != null && searchQuery.isBlank() && selectedFilter == "All") {
                    item(key = "continue_learning_hero") {
                        ContinueLearningHeroCard(
                            target = continueLearningTarget,
                            studyStreak = studyStreak,
                            compact = selectedMainTab == 0,
                            onResume = {
                                val targetCls = continueLearningTarget.classItem
                                val nextLes = continueLearningTarget.nextLesson
                                if (currentUserId.isNotBlank()) {
                                    LessonProgressManager.recordLastOpenedClass(
                                        context = context,
                                        userId = currentUserId,
                                        classId = targetCls.classId,
                                        lessonId = nextLes?.lessonId.orEmpty()
                                    )
                                }
                                if (nextLes != null) {
                                    selectedLessonForView = Pair(nextLes, targetCls)
                                } else {
                                    selectedClassForGroupRoom = targetCls
                                }
                            },
                            onOpenClassroom = {
                                selectedClassForGroupRoom = continueLearningTarget.classItem
                            }
                        )
                    }
                }

                items(displayClasses, key = { it.classId }) { classItem ->
                    val isEnrolled = enrolledClassIds.contains(classItem.classId)
                    val isOwnerInstructor = classItem.instructorUserId == (currentUser?.userId ?: "")
                    val isAnotherTeacher = isApprovedTeacher && !isOwnerInstructor && !isAdmin

                    ClassCard(
                        classItem = classItem,
                        isEnrolled = isEnrolled,
                        isAdmin = isAdmin,
                        isCurrentUserTeacher = isApprovedTeacher,
                        currentUserId = currentUser?.userId ?: "",
                        onJoin = {
                            if (isAnotherTeacher) {
                                Toast.makeText(context, "Access Denied: Teachers cannot access or join another teacher's class.", Toast.LENGTH_SHORT).show()
                                return@ClassCard
                            }
                            if (classItem.isPaid && !classItem.price.equals("FREE", ignoreCase = true)) {
                                classForPaymentCheckout = classItem
                            } else {
                                viewModel.enrollInClass(classItem.classId, classItem.instructorUserId) { success ->
                                    if (success) {
                                        joinedClassConfirmation = classItem
                                        Toast.makeText(context, "Successfully joined ${classItem.title}! 🎉", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Could not join class. Please try again.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onViewDetails = {
                            if (isAnotherTeacher) {
                                Toast.makeText(context, "Access Denied: Teachers cannot access another teacher's class.", Toast.LENGTH_SHORT).show()
                            } else if (isOwnerInstructor || isEnrolled || isAdmin) {
                                selectedClassForGroupRoom = classItem
                            } else {
                                selectedClassForDetail = classItem
                            }
                        },
                        onOpenChat = {
                            onOpenChat(classItem.instructorUserId)
                        },
                        onOpenCertificate = {
                            selectedClassForCertificate = classItem
                        },
                        onEdit = {
                            classForEditing = classItem
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

    // Dialogs & Modals

    if (showMustBecomeTeacherDialog) {
        AlertDialog(
            onDismissRequest = { showMustBecomeTeacherDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = LenoPrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Become a Teacher First",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Users must become an approved Teacher before they are allowed to create or publish classes on Leno.\n\nTo become a teacher, visit your Profile tab and submit a Teacher Application for your subject.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMustBecomeTeacherDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("dismiss_must_become_teacher_dialog_btn")
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Create Class Dialog (Approved teacher's subject is locked!)
    if (showCreateClassDialog && (isApprovedTeacher || isAdmin)) {
        val approvedSubject = currentUser?.effectiveSubject?.ifBlank { null }
            ?: userTeacherRole?.effectiveApprovedSubject?.ifBlank { null }
            ?: "Mathematics"
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

    // Class Detail Dialog (with full instructor info, lessons, and enrollment preview)
    selectedClassForDetail?.let { rawDetailClass ->
        val detailClass = publishedClasses.find { it.classId == rawDetailClass.classId }
            ?: enrolledClasses.find { it.classId == rawDetailClass.classId }
            ?: rawDetailClass
        val isEnrolled = enrolledClassIds.contains(detailClass.classId)
        val isInstructor = currentUser?.userId == detailClass.instructorUserId
        val isAnotherTeacher = isApprovedTeacher && !isInstructor && !isAdmin
        if (isAnotherTeacher) {
            selectedClassForDetail = null
            Toast.makeText(context, "Access Denied: Teachers cannot access another teacher's class.", Toast.LENGTH_SHORT).show()
            return@let
        }
        DisposableEffect(detailClass.classId) {
            viewModel.startClassroomListeners(detailClass.classId)
            onDispose {
                if (selectedClassForGroupRoom?.classId != detailClass.classId &&
                    selectedLessonForView?.second?.classId != detailClass.classId
                ) {
                    viewModel.stopClassroomListeners()
                }
            }
        }
        val classLessons by viewModel.getLessonsForClass(detailClass.classId).collectAsState(initial = emptyList<LessonEntity>())

        ClassDetailDialog(
            classItem = detailClass,
            isEnrolled = isEnrolled,
            isInstructor = isInstructor,
            isAdmin = isAdmin,
            isAnotherTeacher = isAnotherTeacher,
            lessons = classLessons,
            currentUserId = currentUser?.userId ?: "",
            onDismiss = { selectedClassForDetail = null },
            onEnterClassroom = {
                if (isAnotherTeacher) {
                    Toast.makeText(context, "Unauthorized: Teachers cannot access another teacher's class.", Toast.LENGTH_SHORT).show()
                } else {
                    val currentClass = detailClass
                    selectedClassForDetail = null
                    selectedClassForGroupRoom = currentClass
                }
            },
            onJoin = {
                if (isAnotherTeacher) {
                    Toast.makeText(context, "Access Denied: Teachers cannot access or join another teacher's class.", Toast.LENGTH_SHORT).show()
                    return@ClassDetailDialog
                }
                val currentClass = detailClass
                selectedClassForDetail = null
                if (currentClass.isPaid && !currentClass.price.equals("FREE", ignoreCase = true)) {
                    classForPaymentCheckout = currentClass
                } else {
                    viewModel.enrollInClass(currentClass.classId, currentClass.instructorUserId) { success ->
                        if (success) {
                            joinedClassConfirmation = currentClass
                            Toast.makeText(context, "Successfully joined ${currentClass.title}! 🎉", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Could not join class. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onChatWithInstructor = {
                selectedClassForDetail = null
                onOpenChat(detailClass.instructorUserId)
            },
            onOpenLesson = { lesson ->
                selectedLessonForView = Pair(lesson, detailClass)
            },
            onAddLesson = {
                if (detailClass.instructorUserId == (currentUser?.userId ?: "") || isAdmin) {
                    classForAddingLesson = detailClass
                } else {
                    Toast.makeText(context, "Unauthorized: Only the creator of this class can add lessons.", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenCertificate = { certClass ->
                selectedClassForCertificate = certClass
            },
            onEditClass = {
                val target = detailClass
                selectedClassForDetail = null
                classForEditing = target
            }
        )
    }

    // Payment Checkout Dialog (for paid classes)
    classForPaymentCheckout?.let { payClass ->
        val instructorRole by viewModel.getTeacherRole(payClass.instructorUserId).collectAsState(initial = null)
        PaymentCheckoutDialog(
            classItem = payClass,
            currentUser = currentUser,
            instructorTeacherRole = instructorRole,
            onDismiss = { classForPaymentCheckout = null },
            onPaymentSuccess = {
                viewModel.enrollInClass(payClass.classId, payClass.instructorUserId) { success ->
                    if (success) {
                        classForPaymentCheckout = null
                        joinedClassConfirmation = payClass
                    } else {
                        Toast.makeText(context, "Payment failed. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Class Room Group Dialog (Active Discussion, Roster & Lessons)
    selectedClassForGroupRoom?.let { rawGroupClass ->
        val groupClass = publishedClasses.find { it.classId == rawGroupClass.classId }
            ?: enrolledClasses.find { it.classId == rawGroupClass.classId }
            ?: rawGroupClass
        val isInstructor = currentUser?.userId == groupClass.instructorUserId
        val isAnotherTeacher = isApprovedTeacher && !isInstructor && !isAdmin
        if (isAnotherTeacher) {
            selectedClassForGroupRoom = null
            Toast.makeText(context, "Unauthorized: Teachers cannot access another teacher's class.", Toast.LENGTH_SHORT).show()
            return@let
        }
        DisposableEffect(groupClass.classId) {
            viewModel.startClassroomListeners(groupClass.classId)
            onDispose {
                if (selectedClassForDetail?.classId != groupClass.classId &&
                    selectedLessonForView?.second?.classId != groupClass.classId
                ) {
                    viewModel.stopClassroomListeners()
                }
            }
        }
        val groupLessons by viewModel.getLessonsForClass(groupClass.classId).collectAsState(initial = emptyList<LessonEntity>())
        val groupQuestions by viewModel.getQuestionsForClass(groupClass.classId).collectAsState(initial = emptyList<ClassQuestionEntity>())
        val enrolledStudents by viewModel.getEnrolledStudentsForClass(groupClass.classId).collectAsState(initial = emptyList<UserEntity>())

        ClassRoomGroupDialog(
            classItem = groupClass,
            currentUser = currentUser,
            isInstructor = isInstructor,
            isAdmin = isAdmin,
            lessons = groupLessons,
            questions = groupQuestions,
            enrolledStudents = enrolledStudents,
            onAskQuestion = { qText, audioUrl, durationSec, sRole ->
                viewModel.askQuestionInClass(
                    classId = groupClass.classId,
                    questionText = qText,
                    audioUrl = audioUrl,
                    audioDurationSeconds = durationSec,
                    senderRole = sRole
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onAnswerQuestion = { qId, ansText, audioUrl, durationSec ->
                viewModel.answerQuestionInClass(
                    classId = groupClass.classId,
                    questionId = qId,
                    answerText = ansText,
                    audioUrl = audioUrl,
                    audioDurationSeconds = durationSec
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { selectedClassForGroupRoom = null },
            onChatWithInstructor = {
                selectedClassForGroupRoom = null
                onOpenChat(groupClass.instructorUserId)
            },
            onOpenLesson = { lesson ->
                selectedLessonForView = Pair(lesson, groupClass)
            },
            onAddLesson = {
                if (groupClass.instructorUserId == (currentUser?.userId ?: "") || isAdmin) {
                    classForAddingLesson = groupClass
                } else {
                    Toast.makeText(context, "Unauthorized: Only the creator of this class can add lessons.", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenCertificate = { certClass ->
                selectedClassForCertificate = certClass
            },
            viewModel = viewModel,
            onRemoveStudent = { sId, sName ->
                viewModel.removeStudentFromClass(groupClass.classId, sId, sName) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onAddStudent = { sId, sLinoId, sName ->
                viewModel.enrollStudentByTeacher(groupClass.classId, sId, sLinoId, sName) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onEditClass = {
                val target = groupClass
                selectedClassForGroupRoom = null
                classForEditing = target
            }
        )
    }

    // Add Lesson Dialog (for instructor)
    classForAddingLesson?.let { cls ->
        val isOwner = cls.instructorUserId == (currentUser?.userId ?: "")
        if (!isOwner && !isAdmin) {
            classForAddingLesson = null
            Toast.makeText(context, "Unauthorized: Teachers cannot add lessons to another teacher's class.", Toast.LENGTH_SHORT).show()
        } else {
            AddLessonDialog(
                classTitle = cls.title,
                onDismiss = { classForAddingLesson = null },
                onSubmitLesson = { title, content, summary ->
                    viewModel.addLessonToClass(cls.classId, title, content, summary) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // Edit Class Dialog (for creator teacher / instructor only)
    classForEditing?.let { editClass ->
        val isOwner = editClass.instructorUserId == (currentUser?.userId ?: "")
        if (!isOwner && !isAdmin) {
            classForEditing = null
            Toast.makeText(context, "Unauthorized: Only the teacher who published this class can edit it.", Toast.LENGTH_SHORT).show()
        } else {
            EditClassDialog(
                classItem = editClass,
                onDismiss = { classForEditing = null },
                onUpdateClass = { title, desc, level, isPaid, price, sched, type, status, img, vid ->
                    viewModel.updateClass(
                        classId = editClass.classId,
                        title = title,
                        shortDescription = desc,
                        level = level,
                        isPaid = isPaid,
                        price = price,
                        schedule = sched,
                        lessonType = type,
                        status = status,
                        imageUrl = img,
                        videoUrl = vid
                    ) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            classForEditing = null
                        }
                    }
                }
            )
        }
    }

    // Lesson Viewer Dialog (full-screen reader with Prev/Next navigation)
    selectedLessonForView?.let { (lesson, rawParentClass) ->
        val parentClass = publishedClasses.find { it.classId == rawParentClass.classId }
            ?: enrolledClasses.find { it.classId == rawParentClass.classId }
            ?: rawParentClass
        DisposableEffect(parentClass.classId) {
            viewModel.startClassroomListeners(parentClass.classId)
            onDispose {
                if (selectedClassForDetail?.classId != parentClass.classId &&
                    selectedClassForGroupRoom?.classId != parentClass.classId
                ) {
                    viewModel.stopClassroomListeners()
                }
            }
        }
        val parentLessons by viewModel.getLessonsForClass(parentClass.classId).collectAsState(initial = emptyList<LessonEntity>())
        LessonViewerDialog(
            lesson = lesson,
            parentClass = parentClass,
            currentUserId = currentUser?.userId ?: "",
            allLessons = parentLessons,
            onSelectLesson = { targetLesson ->
                selectedLessonForView = Pair(targetLesson, parentClass)
            },
            onDismiss = { selectedLessonForView = null },
            onAskTeacherInChat = { formulaQuote ->
                selectedLessonForView = null
                if (!formulaQuote.isNullOrBlank()) {
                    viewModel.sendMessage(
                        receiverId = parentClass.instructorUserId,
                        text = formulaQuote
                    )
                }
                onOpenChat(parentClass.instructorUserId)
            }
        )
    }


    // Join Confirmation Dialog
    joinedClassConfirmation?.let { confClass ->
        JoinConfirmationDialog(
            classTitle = confClass.title,
            instructorName = confClass.instructorName,
            instructorLinoId = confClass.instructorLinoId,
            onDismiss = { joinedClassConfirmation = null },
            onGoToClass = {
                joinedClassConfirmation = null
                selectedClassForGroupRoom = confClass
            },
            onChatWithInstructor = {
                onOpenChat(confClass.instructorUserId)
            }
        )
    }

    if (showAdminTeacherApplicationsDialog) {
        AdminTeacherApplicationsDialog(
            viewModel = viewModel,
            onDismiss = { showAdminTeacherApplicationsDialog = false }
        )
    }

    if (showContactSupportDialog) {
        ContactSupportDialog(
            viewModel = viewModel,
            currentUser = currentUser,
            onDismiss = { showContactSupportDialog = false },
            onOpenSupportChat = { chatId ->
                showContactSupportDialog = false
                onOpenSupportChat(chatId)
            }
        )
    }

    // Official Certificate of Completion Dialog
    selectedClassForCertificate?.let { certClass ->
        val studentName = currentUser?.displayName?.ifBlank { currentUser?.username } ?: "Leno Student"
        CourseCertificateDialog(
            classItem = certClass,
            studentName = studentName,
            studentUserId = currentUser?.userId ?: "",
            onDismiss = { selectedClassForCertificate = null }
        )
    }
}

@Composable
fun ClassCard(
    classItem: ClassEntity,
    isEnrolled: Boolean,
    isAdmin: Boolean = false,
    isCurrentUserTeacher: Boolean = false,
    currentUserId: String = "",
    onJoin: () -> Unit,
    onViewDetails: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenCertificate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = LocalLenoDarkTheme.current
    val updateTick by LessonProgressManager.updateTick.collectAsState()
    val isOwnerInstructor = classItem.instructorUserId == currentUserId
    val isAnotherTeacher = isCurrentUserTeacher && !isOwnerInstructor && !isAdmin
    val classCode = remember(classItem.classId) {
        LessonProgressManager.formatClassCode(classItem.classId)
    }
    val completedCount = remember(classItem.classId, currentUserId, updateTick) {
        if (currentUserId.isNotBlank()) {
            LessonProgressManager.getCompletedCount(context, currentUserId, classItem.classId)
        } else 0
    }
    val isCertAwarded = remember(classItem.classId, currentUserId, updateTick) {
        if (currentUserId.isNotBlank()) {
            LessonProgressManager.isCertificateAwarded(context, classItem.classId, currentUserId)
        } else false
    }

    val formattedPrice = remember(classItem.price, classItem.isPaid) {
        val raw = classItem.price.trim()
        if (raw.equals("FREE", ignoreCase = true) || raw.equals("Free", ignoreCase = true) || !classItem.isPaid) {
            "Free"
        } else if (raw.startsWith("N", ignoreCase = true) || raw.startsWith("₦")) {
            raw.replaceFirst(Regex("^[Nn]\\s*"), "₦")
        } else {
            "₦$raw"
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 140.dp)
            .clickable {
                if (isAnotherTeacher) {
                    Toast.makeText(context, "Access Denied: Teachers cannot access another teacher's class.", Toast.LENGTH_SHORT).show()
                } else {
                    onViewDetails()
                }
            }
            .testTag("class_card_${classItem.classId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Row 1: Subject badge + Price pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else LenoPrimaryVeryLight
                ) {
                    Text(
                        text = classItem.subject,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else LenoPrimaryDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                val isFree = formattedPrice.equals("Free", ignoreCase = true) || !classItem.isPaid
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isFree) {
                        if (isDark) Color(0xFF0D3828) else Color(0xFFE8F7F0)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.primaryContainer else LenoPrimaryVeryLight
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isFree) Color(0xFF10B981) else LenoPrimary.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = if (isFree) "Free" else formattedPrice,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isFree) {
                            if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                        } else {
                            if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else LenoPrimary
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Class Title
            Text(
                text = classItem.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Optional 1-line description snippet if available
            if (classItem.shortDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = classItem.shortDescription,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Compact Progress Bar (if enrolled)
            if (isEnrolled && currentUserId.isNotBlank()) {
                val totalLessons = maxOf(classItem.lessonCount, 1)
                val progressFraction = (completedCount.toFloat() / totalLessons.toFloat()).coerceIn(0f, 1f)
                val progressPercent = (progressFraction * 100).toInt()

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF22C55E),
                        trackColor = if (isDark) Color(0xFF1B3B2B) else Color(0xFFDCFCE7)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$completedCount/$totalLessons ($progressPercent%)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val hasMultipleButtons = (isOwnerInstructor && onEdit != null) || (isCertAwarded && onOpenCertificate != null)

            // Row 3: Teacher Row & Single-Button Actions (or Chat only when multi-button row follows)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Teacher Info Column
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
                            text = classItem.instructorName.take(1).uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LenoPrimaryDark
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = classItem.instructorName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (classItem.instructorIsTeacher) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = LenoPrimary,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = "Teacher ✓",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        val lessonText = if (classItem.lessonCount == 1) "1 lesson" else "${classItem.lessonCount} lessons"
                        Text(
                            text = lessonText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Actions: Chat icon button + single button (or Chat only when multi-button row renders below)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onOpenChat,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("chat_instructor_btn_${classItem.classId}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Chat with instructor",
                            tint = LenoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (!hasMultipleButtons) {
                        if (isOwnerInstructor) {
                            Button(
                                onClick = onViewDetails,
                                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("enrolled_class_button_${classItem.classId}")
                            ) {
                                Text("Classroom", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        } else if (isAnotherTeacher) {
                            OutlinedButton(
                                onClick = {
                                    Toast.makeText(context, "Access Denied: Teachers cannot access another teacher's class.", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("locked_teacher_class_button_${classItem.classId}")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Teacher Locked", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        } else if (isAdmin || isEnrolled) {
                            Button(
                                onClick = onViewDetails,
                                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("enrolled_class_button_${classItem.classId}")
                            ) {
                                Text("Classroom", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        } else {
                            val isPaid = classItem.isPaid && !classItem.price.equals("FREE", ignoreCase = true)
                            Button(
                                onClick = onJoin,
                                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("join_class_button_${classItem.classId}")
                            ) {
                                Text(
                                    text = if (isPaid) "Pay & Join" else "Join Free",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            if (hasMultipleButtons) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCertAwarded && onOpenCertificate != null) {
                        Button(
                            onClick = onOpenCertificate,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("class_cert_button_${classItem.classId}")
                        ) {
                            Text("Cert 🎓", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }

                    if (isOwnerInstructor && onEdit != null) {
                        OutlinedButton(
                            onClick = onEdit,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LenoPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimary),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("edit_class_button_${classItem.classId}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp), tint = LenoPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 11.sp, color = LenoPrimary, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }

                    if (isOwnerInstructor || isAdmin || isEnrolled) {
                        Button(
                            onClick = onViewDetails,
                            colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("enrolled_class_button_${classItem.classId}")
                        ) {
                            Text("Classroom", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueLearningHeroCard(
    target: ContinueLearningTarget,
    studyStreak: Int,
    compact: Boolean,
    onResume: () -> Unit,
    onOpenClassroom: () -> Unit
) {
    val isDark = LocalLenoDarkTheme.current
    val gradientBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF0A3628), Color(0xFF0E4D39), Color(0xFF082E22)))
    } else {
        Brush.linearGradient(listOf(LenoPrimaryDark, LenoPrimary, LenoActionGreen))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clickable(onClick = onResume)
            .testTag("continue_learning_hero_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(horizontal = 16.dp, vertical = if (compact) 12.dp else 16.dp)
        ) {
            if (compact) {
                // Sleek quick-resume strip on Explore tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "CONTINUE LEARNING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White.copy(alpha = 0.85f),
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "• ${target.progressPercent}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = target.classItem.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = target.nextLesson?.let { "Up next: Lesson ${it.orderIndex} • ${it.title}" }
                            ?: "${target.completedCount}/${target.totalLessons} lessons completed"
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = LenoPrimaryDark
                        ),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("continue_learning_resume_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Resume",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            } else {
                // Full Hero Card on My Learning tab
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "CONTINUE LEARNING",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = target.classItem.subject,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF3C7).copy(alpha = 0.95f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Study Streak",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${studyStreak}d streak",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = target.classItem.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "with ${target.classItem.instructorName}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.18f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            val nextLessonLabel = if (target.nextLesson != null) {
                                val mins = CurriculumSectionHelper.estimateReadingMinutes(target.nextLesson)
                                val prefix = if (target.progressPercent >= 100) "Review" else "Up next"
                                "$prefix: Lesson ${target.nextLesson.orderIndex} • ${target.nextLesson.title} (~$mins min)"
                            } else {
                                "Tap below to enter classroom & review lessons"
                            }
                            Text(
                                text = nextLessonLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { (target.progressPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFFA7F3D0),
                                    trackColor = Color.White.copy(alpha = 0.25f)
                                )
                                Text(
                                    text = "${target.completedCount}/${target.totalLessons} • ${target.progressPercent}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = LenoPrimaryDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("continue_learning_resume_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (target.nextLesson != null) "Resume Lesson" else "Open Classroom",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenClassroom,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("continue_learning_classroom_btn")
                        ) {
                            Text(
                                text = "Classroom",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
