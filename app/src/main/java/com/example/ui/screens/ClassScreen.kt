package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
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
import com.example.ui.components.LenoEmptyState
import com.example.ui.components.LenoHeaderActionButton
import com.example.ui.components.LenoSearchPill
import com.example.ui.components.LenoTabHeader
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
    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedMainTab by remember { mutableIntStateOf(0) } // 0 = Explore Classes, 1 = My Learning

    val currentUser by viewModel.currentUser.collectAsState()
    val publishedClasses by viewModel.publishedClasses.collectAsState()
    val enrolledClasses by viewModel.currentUserEnrolledClasses.collectAsState()
    val userTeacherRole by viewModel.currentUserTeacherRole.collectAsState()
    val lessonSeenByMap by viewModel.lessonSeenByMap.collectAsState()
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
    var lessonForEditing by remember { mutableStateOf<Pair<ClassEntity, LessonEntity>?>(null) }
    var lessonToDelete by remember { mutableStateOf<Pair<ClassEntity, LessonEntity>?>(null) }
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
            Spacer(modifier = Modifier.height(6.dp))

            LenoTabHeader(
                title = "Classes",
                subtitle = if (selectedMainTab == 1) {
                    "${enrolledClasses.size} enrolled course${if (enrolledClasses.size == 1) "" else "s"}"
                } else {
                    "${filteredExploreClasses.size} published course${if (filteredExploreClasses.size == 1) "" else "s"}"
                }
            ) {
                LenoHeaderActionButton(
                    icon = if (isSearchOpen) Icons.Default.Clear else Icons.Default.Search,
                    contentDescription = if (isSearchOpen) "Close search" else "Search classes",
                    isActive = isSearchOpen,
                    onClick = {
                        isSearchOpen = !isSearchOpen
                        if (!isSearchOpen) searchQuery = ""
                    },
                    modifier = Modifier.testTag("class_search_toggle_btn")
                )
            }

            LenoSearchPill(
                visible = isSearchOpen || searchQuery.isNotEmpty(),
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search lessons, subjects or skills...",
                onCloseOrClear = {
                    if (searchQuery.isNotEmpty()) searchQuery = "" else isSearchOpen = false
                },
                inputTestTag = "class_search_input"
            )

            Spacer(modifier = Modifier.height(4.dp))

        // Segmented Pill Switcher: Explore Classes | My Learning
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isExploreSelected = selectedMainTab == 0
                Surface(
                    onClick = { selectedMainTab = 0 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isExploreSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isExploreSelected) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_explore_classes")
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Explore (${filteredExploreClasses.size})",
                            fontWeight = if (isExploreSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isExploreSelected) {
                                if (LocalLenoDarkTheme.current) MaterialTheme.colorScheme.secondary else LenoPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val isLearningSelected = selectedMainTab == 1
                Surface(
                    onClick = { selectedMainTab = 1 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isLearningSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isLearningSelected) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_my_learning")
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "My Learning (${enrolledClasses.size})",
                            fontWeight = if (isLearningSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isLearningSelected) {
                                if (LocalLenoDarkTheme.current) MaterialTheme.colorScheme.secondary else LenoPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val displayClasses = if (selectedMainTab == 0) filteredExploreClasses else filteredEnrolledClasses

        // Class Cards List (subject filter chips scroll smoothly inside)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("classes_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
        ) {
            item(key = "subject_filters_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    filterOptions.forEach { filterName ->
                        val selected = selectedFilter == filterName
                        val chipIcon = when (filterName) {
                            "All" -> Icons.Default.AutoAwesome
                            "Computer Science", "Online Skills" -> Icons.Default.Code
                            "JAMB", "Maths", "Physics", "Chemistry", "Biology", "Economics" -> Icons.Default.School
                            "English", "Quran" -> Icons.Default.MenuBook
                            "Content Creator" -> Icons.Default.Star
                            else -> Icons.Default.Book
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = filterName },
                            leadingIcon = {
                                Icon(
                                    imageVector = chipIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = filterName,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            shape = RoundedCornerShape(50),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                selectedBorderColor = LenoPrimary
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LenoPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconColor = LenoPrimary
                            ),
                            modifier = Modifier.testTag("filter_chip_$filterName")
                        )
                    }
                }
            }

            if (displayClasses.isEmpty()) {
                item(key = "classes_empty_state") {
                    LenoEmptyState(
                        icon = if (searchQuery.isNotEmpty()) Icons.Default.Search else Icons.Default.School,
                        title = when {
                            selectedMainTab == 1 -> "No enrolled classes yet"
                            searchQuery.isNotEmpty() -> "No matching classes"
                            else -> "No classes in $selectedFilter"
                        },
                        subtitle = when {
                            selectedMainTab == 1 -> "Explore published classes and tap one to join your classroom."
                            searchQuery.isNotEmpty() -> "No classes match \"$searchQuery\". Try another keyword or subject."
                            else -> "Check back soon or switch to All subjects to discover courses."
                        },
                        actionLabel = if (selectedMainTab == 1) "Browse Classes" else null,
                        onAction = if (selectedMainTab == 1) ({ selectedMainTab = 0 }) else null,
                        modifier = Modifier.fillParentMaxWidth()
                    )
                }
            } else {
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
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

        // Single Floating Action Button for Create Class
        FloatingActionButton(
            onClick = {
                if (isApprovedTeacher || isAdmin) {
                    showCreateClassDialog = true
                } else {
                    showMustBecomeTeacherDialog = true
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = LenoPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .size(56.dp)
                .testTag(if (isApprovedTeacher || isAdmin) "top_create_class_button" else "top_create_class_button_non_teacher")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Class",
                tint = Color.White
            )
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

    // Paid Class Enrollment Inquiry Dialog
    classForPaymentCheckout?.let { payClass ->
        val instructorRole by viewModel.getTeacherRole(payClass.instructorUserId).collectAsState(initial = null)
        PaymentCheckoutDialog(
            classItem = payClass,
            currentUser = currentUser,
            instructorTeacherRole = instructorRole,
            onDismiss = { classForPaymentCheckout = null },
            onMessageInstructor = {
                val targetId = payClass.instructorUserId
                classForPaymentCheckout = null
                if (targetId.isNotBlank()) {
                    onOpenChat(targetId)
                } else {
                    showContactSupportDialog = true
                }
            },
            onContactSupport = {
                classForPaymentCheckout = null
                showContactSupportDialog = true
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
            },
            seenByMap = lessonSeenByMap,
            onEditLesson = { targetLesson ->
                if (groupClass.instructorUserId == (currentUser?.userId ?: "") || isAdmin) {
                    lessonForEditing = Pair(groupClass, targetLesson)
                }
            },
            onDeleteLesson = { targetLesson ->
                if (groupClass.instructorUserId == (currentUser?.userId ?: "") || isAdmin) {
                    lessonToDelete = Pair(groupClass, targetLesson)
                }
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
            val clsLessons by viewModel.getLessonsForClass(cls.classId).collectAsState(initial = emptyList<LessonEntity>())
            val existingModules = remember(clsLessons) {
                val bracketRegex = Regex("""^\[([^\]]+)]\s*""")
                clsLessons.mapNotNull { bracketRegex.find(it.summary.trim())?.groupValues?.getOrNull(1)?.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            }
            AddLessonDialog(
                classId = cls.classId,
                classTitle = cls.title,
                existingModules = existingModules,
                onDismiss = { classForAddingLesson = null },
                onSubmitLesson = { title, content, summary, onResult ->
                    viewModel.addLessonToClass(cls.classId, title, content, summary) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        onResult(success, msg)
                    }
                }
            )
        }
    }

    // Edit Lesson Dialog (for instructor)
    lessonForEditing?.let { (cls, targetLesson) ->
        val isOwner = cls.instructorUserId == (currentUser?.userId ?: "")
        if (!isOwner && !isAdmin) {
            lessonForEditing = null
            Toast.makeText(context, "Unauthorized: Only the creator of this class can edit lessons.", Toast.LENGTH_SHORT).show()
        } else {
            val clsLessons by viewModel.getLessonsForClass(cls.classId).collectAsState(initial = emptyList<LessonEntity>())
            val existingModules = remember(clsLessons) {
                val bracketRegex = Regex("""^\[([^\]]+)]\s*""")
                clsLessons.mapNotNull { bracketRegex.find(it.summary.trim())?.groupValues?.getOrNull(1)?.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            }
            AddLessonDialog(
                classId = cls.classId,
                classTitle = cls.title,
                initialLesson = targetLesson,
                existingModules = existingModules,
                onDismiss = { lessonForEditing = null },
                onSubmitLesson = { title, content, summary, onResult ->
                    viewModel.updateLessonInClass(cls.classId, targetLesson.lessonId, title, content, summary) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        onResult(success, msg)
                    }
                }
            )
        }
    }

    // Delete Lesson Confirmation Dialog
    lessonToDelete?.let { (cls, targetLesson) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { lessonToDelete = null },
            title = { Text("Delete Lesson?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove \"${targetLesson.title}\" from ${cls.title}? Students will no longer see it.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        lessonToDelete = null
                        viewModel.deleteLessonFromClass(cls.classId, targetLesson.lessonId) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { lessonToDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
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
            onMarkLessonSeen = { viewedLesson ->
                viewModel.markLessonSeen(context, parentClass.classId, viewedLesson.lessonId)
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
    val isFree = formattedPrice.equals("Free", ignoreCase = true) || !classItem.isPaid

    val headerGradientColors = remember(classItem.subject, isDark) {
        val s = classItem.subject.lowercase()
        when {
            "math" in s -> if (isDark) listOf(Color(0xFF1E293B), Color(0xFF1D4ED8).copy(alpha = 0.55f)) else listOf(Color(0xFFEEF2FF), Color(0xFFDBEAFE))
            "phys" in s -> if (isDark) listOf(Color(0xFF0F292E), Color(0xFF0E7490).copy(alpha = 0.55f)) else listOf(Color(0xFFECFEFF), Color(0xFFCFFAFE))
            "chem" in s -> if (isDark) listOf(Color(0xFF281A3B), Color(0xFF6D28D9).copy(alpha = 0.5f)) else listOf(Color(0xFFF5F3FF), Color(0xFFEDE9FE))
            "eng" in s || "lit" in s -> if (isDark) listOf(Color(0xFF2E2314), Color(0xFFB45309).copy(alpha = 0.5f)) else listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7))
            "comp" in s || "code" in s || "skill" in s -> if (isDark) listOf(Color(0xFF142B24), Color(0xFF0F766E).copy(alpha = 0.55f)) else listOf(Color(0xFFECFDF5), Color(0xFFCCFBF1))
            else -> if (isDark) listOf(Color(0xFF0D2E22), Color(0xFF065F46).copy(alpha = 0.65f)) else listOf(LenoPrimaryVeryLight, Color(0xFFD1FAE5))
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.5f)
        ),
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
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Visual Header (Cover Image Banner or Subject Gradient Strip)
            if (classItem.imageUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                ) {
                    AsyncImage(
                        model = classItem.imageUrl,
                        contentDescription = classItem.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Black.copy(alpha = 0.1f),
                                        Color.Black.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Text(
                                text = classItem.subject,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isFree) Color(0xFF047857).copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        ) {
                            Text(
                                text = if (isFree) "Free" else formattedPrice,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFree) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(headerGradientColors))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
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
                                shape = RoundedCornerShape(50),
                                color = if (isDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = classItem.subject,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else com.example.ui.theme.LenoBlack,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (classItem.level.isNotBlank()) {
                                Text(
                                    text = classItem.level,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isFree) {
                                if (isDark) Color(0xFF0D3828) else Color(0xFFE8F7F0)
                            } else {
                                if (isDark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isFree) Color(0xFF10B981).copy(alpha = 0.7f) else LenoPrimary.copy(alpha = 0.45f)
                            )
                        ) {
                            Text(
                                text = if (isFree) "Free" else formattedPrice,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color.White else com.example.ui.theme.LenoBlack,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Clean Card Body (Tap anywhere on card for full Course Overview / Classroom)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Class Title
                Text(
                    text = classItem.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 17.5.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Single Compact Metadata Row (Avatar + Name + Verified + Lesson Count + Status Pill)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (isDark) LenoPrimaryDark.copy(alpha = 0.45f) else LenoPrimaryLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = classItem.instructorName.take(1).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else com.example.ui.theme.LenoBlack
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = classItem.instructorName,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (classItem.instructorIsTeacher) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified Teacher",
                                tint = LenoPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        val lessonText = if (classItem.lessonCount == 1) "1 lesson" else "${classItem.lessonCount} lessons"
                        Text(
                            text = " • $lessonText",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (isAnotherTeacher) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    } else if (isCertAwarded) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFD97706).copy(alpha = if (isDark) 0.25f else 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFD97706))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Certified",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
                                )
                            }
                        }
                    } else if (isOwnerInstructor) {
                        Text(
                            text = "Teaching",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else if (isEnrolled || isAdmin) {
                        Text(
                            text = "Enrolled",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Compact Progress Bar (if enrolled)
                if (isEnrolled && currentUserId.isNotBlank()) {
                    val totalLessons = maxOf(classItem.lessonCount, 1)
                    val progressFraction = (completedCount.toFloat() / totalLessons.toFloat()).coerceIn(0f, 1f)
                    val progressPercent = (progressFraction * 100).toInt()

                    Spacer(modifier = Modifier.height(10.dp))
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
                                .clip(RoundedCornerShape(50)),
                            color = LenoPrimary,
                            trackColor = if (isDark) Color(0xFF1B3B2B) else Color(0xFFDCFCE7)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$progressPercent%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
                                color = Color.White
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
