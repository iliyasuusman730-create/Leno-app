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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.example.ui.components.AddLessonDialog
import com.example.ui.components.AdminTeacherApplicationsDialog
import com.example.ui.components.ClassDetailDialog
import com.example.ui.components.ClassRoomGroupDialog
import com.example.ui.components.CreateClassDialog
import com.example.ui.components.JoinConfirmationDialog
import com.example.ui.components.LessonViewerDialog
import com.example.ui.components.PaymentCheckoutDialog
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoPrimaryDark
import com.example.ui.theme.LenoPrimaryLight
import com.example.ui.theme.LenoPrimaryVeryLight
import com.example.viewmodel.LenoViewModel
import kotlinx.coroutines.launch

@Composable
fun ClassScreen(
    viewModel: LenoViewModel,
    onOpenChat: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedMainTab by remember { mutableIntStateOf(0) } // 0 = Explore Classes, 1 = My Learning

    val currentUser by viewModel.currentUser.collectAsState()
    val publishedClasses by viewModel.publishedClasses.collectAsState()
    val enrolledClasses by viewModel.currentUserEnrolledClasses.collectAsState()
    val userTeacherRole by viewModel.currentUserTeacherRole.collectAsState()

    LaunchedEffect(currentUser?.userId) {
        viewModel.syncClassesFromRemote()
    }

    val isAdmin = currentUser?.isAdmin == true

    // Conditionally check user's 'role' field in Firestore (synced in real-time to currentUser).
    // The 'Create Class' button is hidden unless the user's role in Firestore is specifically "teacher"
    // and they are confirmed as an approved teacher, or the user is an Admin / App Owner.
    val isApprovedTeacher = remember(currentUser, userTeacherRole, isAdmin) {
        if (isAdmin) return@remember true
        val user = currentUser
        val roleField = user?.role?.trim().orEmpty()
        val hasTeacherRoleInFirestore = roleField.equals("teacher", ignoreCase = true) ||
                roleField.equals("TEACHER", ignoreCase = true) ||
                user?.isTeacher == true

        hasTeacherRoleInFirestore && (
            userTeacherRole == null ||
            userTeacherRole?.isApprovedTeacher == true ||
            userTeacherRole?.status?.trim()?.equals("APPROVED", ignoreCase = true) == true ||
            userTeacherRole?.status?.trim()?.equals("Teacher", ignoreCase = true) == true ||
            userTeacherRole?.status?.trim()?.equals("Trusted Teacher", ignoreCase = true) == true
        ) && (userTeacherRole?.status?.trim()?.equals("REJECTED", ignoreCase = true) != true) &&
             (userTeacherRole?.status?.trim()?.equals("PENDING", ignoreCase = true) != true)
    }

    var showAdminTeacherApplicationsDialog by remember { mutableStateOf(false) }
    var showCreateClassDialog by remember { mutableStateOf(false) }
    var selectedClassForDetail by remember { mutableStateOf<ClassEntity?>(null) }
    var selectedClassForGroupRoom by remember { mutableStateOf<ClassEntity?>(null) }
    var classForPaymentCheckout by remember { mutableStateOf<ClassEntity?>(null) }
    var selectedLessonForView by remember { mutableStateOf<Pair<LessonEntity, ClassEntity>?>(null) }
    var classForAddingLesson by remember { mutableStateOf<ClassEntity?>(null) }
    var joinedClassConfirmation by remember { mutableStateOf<ClassEntity?>(null) }

    val enrolledClassIds = remember(enrolledClasses) {
        enrolledClasses.map { it.classId }.toSet()
    }

    // Official subjects of Leno:
    // Maths, Quran, JAMB, Skills, Online Skills, Content Creator
    val filterOptions = listOf(
        "All",
        "Maths",
        "Quran",
        "JAMB",
        "Skills",
        "Online Skills",
        "Content Creator"
    )

    // Filtered classes for Explore tab
    val filteredExploreClasses = remember(publishedClasses, searchQuery, selectedFilter) {
        publishedClasses.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "JAMB Lesson" -> item.subject.equals("JAMB", ignoreCase = true)
                "Content Creator" -> item.subject.equals("Content Creator", ignoreCase = true)
                else -> item.subject.equals(selectedFilter, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.subject.contains(searchQuery, ignoreCase = true) ||
                    item.shortDescription.contains(searchQuery, ignoreCase = true) ||
                    item.instructorName.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    // Filtered classes for My Learning tab
    val filteredEnrolledClasses = remember(enrolledClasses, searchQuery, selectedFilter) {
        enrolledClasses.filter { item ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "JAMB Lesson" -> item.subject.equals("JAMB", ignoreCase = true)
                "Content Creator" -> item.subject.equals("Content Creator", ignoreCase = true)
                else -> item.subject.equals(selectedFilter, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.subject.contains(searchQuery, ignoreCase = true) ||
                    item.shortDescription.contains(searchQuery, ignoreCase = true) ||
                    item.instructorName.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .testTag("class_screen_root")
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Top Header: Class title + Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(LenoPrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = "Class",
                        tint = LenoPrimaryDark,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Class",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Low Data • Chat + Image Lessons",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Header Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAdmin) {
                    OutlinedButton(
                        onClick = { showAdminTeacherApplicationsDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("top_admin_portal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin Portal",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Admin",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Create Class button (Conditionally shown ONLY for approved teachers based on user's 'role' in Firestore, or admins)
                if (isApprovedTeacher) {
                    Button(
                        onClick = {
                            showCreateClassDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("top_create_class_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Create Class",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick action chips: [Content Creator] [JAMB Lesson] as requested
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isContentCreatorActive = selectedFilter == "Content Creator"
            val isJambActive = selectedFilter == "JAMB Lesson"

            // Content Creator chip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isContentCreatorActive) LenoPrimary else LenoPrimaryVeryLight,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isContentCreatorActive) LenoPrimary else LenoPrimaryLight
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        selectedFilter = if (isContentCreatorActive) "All" else "Content Creator"
                    }
                    .testTag("chip_content_creator")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Content Creator",
                        tint = if (isContentCreatorActive) Color.White else LenoPrimaryDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Content Creator",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isContentCreatorActive) Color.White else LenoPrimaryDark
                    )
                }
            }

            // JAMB Lesson chip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isJambActive) LenoPrimary else LenoPrimaryVeryLight,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isJambActive) LenoPrimary else LenoPrimaryLight
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        selectedFilter = if (isJambActive) "All" else "JAMB Lesson"
                    }
                    .testTag("chip_jamb_lesson")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = "JAMB Lesson",
                        tint = if (isJambActive) Color.White else LenoPrimaryDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "JAMB Lesson",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isJambActive) Color.White else LenoPrimaryDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar: "Search lessons, subjects or skills..."
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search lessons, subjects or skills...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = LenoPrimary
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
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = LenoPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("class_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Low Data Indicator Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Low data",
                    tint = LenoPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Low Data • Chat + Image only",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Lightweight",
                    fontSize = 11.sp,
                    color = LenoPrimaryDark,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayClasses, key = { it.classId }) { classItem ->
                    val isEnrolled = enrolledClassIds.contains(classItem.classId)

                    ClassCard(
                        classItem = classItem,
                        isEnrolled = isEnrolled,
                        isAdmin = isAdmin,
                        onJoin = {
                            // Join class is not free - students must pay to join class
                            classForPaymentCheckout = classItem
                        },
                        onViewDetails = {
                            if (isEnrolled || isAdmin) {
                                selectedClassForGroupRoom = classItem
                            } else {
                                selectedClassForDetail = classItem
                            }
                        },
                        onOpenChat = {
                            onOpenChat(classItem.instructorUserId)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Dialogs & Modals

    // Create Class Dialog (Approved teacher's subject is locked!)
    if (showCreateClassDialog && isApprovedTeacher) {
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
    selectedClassForDetail?.let { detailClass ->
        val isEnrolled = enrolledClassIds.contains(detailClass.classId)
        val isInstructor = currentUser?.userId == detailClass.instructorUserId
        val classLessons by viewModel.getLessonsForClass(detailClass.classId).collectAsState(initial = emptyList<LessonEntity>())

        ClassDetailDialog(
            classItem = detailClass,
            isEnrolled = isEnrolled,
            isInstructor = isInstructor,
            isAdmin = isAdmin,
            lessons = classLessons,
            onDismiss = { selectedClassForDetail = null },
            onEnterClassroom = {
                val currentClass = detailClass
                selectedClassForDetail = null
                selectedClassForGroupRoom = currentClass
            },
            onJoin = {
                // Join class is not free - students must pay to join class
                val currentPayClass = detailClass
                selectedClassForDetail = null
                classForPaymentCheckout = currentPayClass
            },
            onChatWithInstructor = {
                selectedClassForDetail = null
                onOpenChat(detailClass.instructorUserId)
            },
            onOpenLesson = { lesson ->
                selectedLessonForView = Pair(lesson, detailClass)
            },
            onAddLesson = {
                classForAddingLesson = detailClass
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
    selectedClassForGroupRoom?.let { groupClass ->
        val isInstructor = currentUser?.userId == groupClass.instructorUserId
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
            onAskQuestion = { qText ->
                viewModel.askQuestionInClass(groupClass.classId, qText) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onAnswerQuestion = { qId, ansText ->
                viewModel.answerQuestionInClass(qId, ansText) { success, msg ->
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
                classForAddingLesson = groupClass
            }
        )
    }

    // Add Lesson Dialog (for instructor)
    classForAddingLesson?.let { cls ->
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

    // Lesson Viewer Dialog (low data text & formulas)
    selectedLessonForView?.let { (lesson, parentClass) ->
        LessonViewerDialog(
            lesson = lesson,
            parentClass = parentClass,
            onDismiss = { selectedLessonForView = null },
            onAskTeacherInChat = {
                selectedLessonForView = null
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
}

@Composable
fun ClassCard(
    classItem: ClassEntity,
    isEnrolled: Boolean,
    isAdmin: Boolean = false,
    onJoin: () -> Unit,
    onViewDetails: () -> Unit,
    onOpenChat: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() }
            .testTag("class_card_${classItem.classId}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Subject & Price Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LenoPrimaryVeryLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, LenoPrimaryLight)
                    ) {
                        Text(
                            text = classItem.subject,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LenoPrimaryDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = classItem.level,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isEnrolled) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = "Enrolled Student ✓",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    val displayFee = if (classItem.price.isNotBlank() && classItem.price != "Free") classItem.price else "₦1,500"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Text(
                            text = displayFee,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = classItem.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Short Description
            Text(
                text = classItem.shortDescription,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Instructor Section (with permanent Leno ID e.g. LEN-50638964)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Instructor:",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = classItem.instructorName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = classItem.instructorLinoId,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = LenoPrimaryDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Badges: Teacher ✓ & Trusted ✓
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (classItem.instructorIsTeacher) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = LenoPrimary,
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = "Teacher ✓",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (classItem.instructorIsTrusted) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1976D2),
                                contentColor = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Trusted",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Trusted ✓",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: low data tag + action buttons (Chat + Join/Enrolled)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = LenoPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Low Data • ${classItem.lessonCount} lessons",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Chat button (Chat with instructor's permanent Leno ID)
                    IconButton(
                        onClick = onOpenChat,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("chat_instructor_btn_${classItem.classId}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Chat with instructor",
                            tint = LenoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Join / Enrolled / Admin button
                    if (isAdmin) {
                        Button(
                            onClick = onViewDetails,
                            colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("admin_class_button_${classItem.classId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Classroom 👥", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else if (!isEnrolled) {
                        Button(
                            onClick = onJoin,
                            colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("join_class_button_${classItem.classId}")
                        ) {
                            val displayFee = if (classItem.price.isNotBlank() && classItem.price != "Free") classItem.price else "₦1,500"
                            Text("Pay $displayFee & Join", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = onViewDetails,
                            colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("enrolled_class_button_${classItem.classId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Classroom 👥", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
