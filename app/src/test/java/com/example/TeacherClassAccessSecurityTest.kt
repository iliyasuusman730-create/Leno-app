package com.example

import com.example.data.entity.ClassEntity
import com.example.data.entity.LessonEntity
import com.example.data.entity.UserEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests enforcing strict access control:
 * - A teacher can only access and add lessons to their own classes.
 * - A teacher is strictly forbidden from accessing, joining, or managing another teacher's class.
 */
class TeacherClassAccessSecurityTest {

    private val teacherAlice = UserEntity(
        userId = "usr_alice",
        username = "alice_teacher",
        displayName = "Teacher Alice",
        role = "TEACHER",
        teacherStatus = "APPROVED",
        linoId = "LEN-ALICE001"
    )

    private val teacherBob = UserEntity(
        userId = "usr_bob",
        username = "bob_teacher",
        displayName = "Teacher Bob",
        role = "TEACHER",
        teacherStatus = "APPROVED",
        linoId = "LEN-BOB00002"
    )

    private val studentCharlie = UserEntity(
        userId = "usr_charlie",
        username = "charlie_student",
        displayName = "Charlie Student",
        role = "USER",
        teacherStatus = "NOT_APPLIED",
        linoId = "LEN-CHARLIE3"
    )

    private val aliceClass = ClassEntity(
        classId = "cls_alice_math",
        title = "Calculus 101",
        subject = "Mathematics",
        shortDescription = "Introduction to Calculus",
        instructorUserId = "usr_alice",
        instructorName = "Teacher Alice",
        instructorUsername = "alice_teacher",
        instructorLinoId = "LEN-ALICE001"
    )

    @Test
    fun teacherCannotAccessAnotherTeachersClass() {
        // Condition: currentUser is teacher Bob, attempting to access teacher Alice's class
        val isOwnerInstructor = aliceClass.instructorUserId == teacherBob.userId
        val isAnotherTeacher = teacherBob.isTeacher && !isOwnerInstructor && !teacherBob.isAdmin

        assertTrue("Bob is recognized as another teacher for Alice's class", isAnotherTeacher)
        assertFalse("Bob is not the owner instructor of Alice's class", isOwnerInstructor)
    }

    @Test
    fun teacherCanAccessOwnClass() {
        // Condition: currentUser is teacher Alice, accessing her own class
        val isOwnerInstructor = aliceClass.instructorUserId == teacherAlice.userId
        val isAnotherTeacher = teacherAlice.isTeacher && !isOwnerInstructor && !teacherAlice.isAdmin

        assertTrue("Alice is the owner instructor of her class", isOwnerInstructor)
        assertFalse("Alice is not 'another teacher' for her own class", isAnotherTeacher)
    }

    @Test
    fun studentCanAccessClassAsStudent() {
        // Condition: Charlie is a student, not a teacher
        val isOwnerInstructor = aliceClass.instructorUserId == studentCharlie.userId
        val isAnotherTeacher = studentCharlie.isTeacher && !isOwnerInstructor && !studentCharlie.isAdmin

        assertFalse("Student Charlie is not owner instructor", isOwnerInstructor)
        assertFalse("Student Charlie is not another teacher", isAnotherTeacher)
    }

    @Test
    fun lessonCreationOwnershipValidation() {
        val newLesson = LessonEntity(
            lessonId = "les_123",
            classId = aliceClass.classId,
            title = "Integrals",
            orderIndex = 1,
            summary = "Intro",
            content = "Integral calculus content"
        )

        // Attempt by Bob (another teacher)
        val canBobAddLesson = aliceClass.instructorUserId == teacherBob.userId || teacherBob.isAdmin
        assertFalse("Teacher Bob cannot add lessons to Alice's class", canBobAddLesson)

        // Attempt by Alice (the owner teacher)
        val canAliceAddLesson = aliceClass.instructorUserId == teacherAlice.userId || teacherAlice.isAdmin
        assertTrue("Teacher Alice can add lessons to her own class", canAliceAddLesson)
    }

    @Test
    fun classEditOwnershipValidation() {
        // Condition: Can Bob (another teacher) edit Alice's class?
        val canBobEdit = aliceClass.instructorUserId == teacherBob.userId || teacherBob.isAdmin
        assertFalse("Teacher Bob cannot edit Alice's class", canBobEdit)

        // Condition: Can Charlie (student) edit Alice's class?
        val canCharlieEdit = aliceClass.instructorUserId == studentCharlie.userId || studentCharlie.isAdmin
        assertFalse("Student Charlie cannot edit Alice's class", canCharlieEdit)

        // Condition: Can Alice (creator teacher) edit her class?
        val canAliceEdit = aliceClass.instructorUserId == teacherAlice.userId || teacherAlice.isAdmin
        assertTrue("Teacher Alice can edit her own class", canAliceEdit)
    }

    @Test
    fun classEditFieldUpdates() {
        // Teacher Alice updates title, description, level, and price
        val updatedClass = aliceClass.copy(
            title = "Advanced Calculus & Real Analysis",
            shortDescription = "In-depth study of derivatives and multivariable calculus.",
            level = "Advanced",
            isPaid = true,
            price = "₦2,500",
            schedule = "Mon-Wed-Fri 7pm"
        )

        org.junit.Assert.assertEquals("Advanced Calculus & Real Analysis", updatedClass.title)
        org.junit.Assert.assertEquals("In-depth study of derivatives and multivariable calculus.", updatedClass.shortDescription)
        org.junit.Assert.assertEquals("Advanced", updatedClass.level)
        org.junit.Assert.assertEquals(true, updatedClass.isPaid)
        org.junit.Assert.assertEquals("₦2,500", updatedClass.price)
        org.junit.Assert.assertEquals("Mathematics", updatedClass.subject) // Subject remains intact
    }

    @Test
    fun mustBecomeTeacherBeforeCreatingClassValidation() {
        val pendingApplicant = UserEntity(
            userId = "usr_pending",
            username = "pending_teacher",
            displayName = "Pending Teacher",
            role = "USER",
            teacherStatus = "PENDING",
            linoId = "LEN-PENDING1"
        )

        val rejectedApplicant = UserEntity(
            userId = "usr_rejected",
            username = "rejected_user",
            displayName = "Rejected User",
            role = "USER",
            teacherStatus = "REJECTED",
            linoId = "LEN-REJECT01"
        )

        // 1. Regular user (studentCharlie) has not become a teacher
        val canStudentCreateClass = studentCharlie.isApprovedTeacher && !studentCharlie.isPendingTeacher && !studentCharlie.isRejectedTeacher
        assertFalse("Regular users must become teacher before allowed to create class", canStudentCreateClass)

        // 2. Pending applicant has not yet become an approved teacher
        val canPendingCreateClass = pendingApplicant.isApprovedTeacher && !pendingApplicant.isPendingTeacher && !pendingApplicant.isRejectedTeacher
        assertFalse("Pending teacher applicants cannot create class until approved", canPendingCreateClass)

        // 3. Rejected applicant cannot create class
        val canRejectedCreateClass = rejectedApplicant.isApprovedTeacher && !rejectedApplicant.isPendingTeacher && !rejectedApplicant.isRejectedTeacher
        assertFalse("Rejected applicants cannot create class", canRejectedCreateClass)

        // 4. Approved teacher (teacherAlice) who became a teacher can create class
        val canApprovedTeacherCreateClass = teacherAlice.isApprovedTeacher && !teacherAlice.isPendingTeacher && !teacherAlice.isRejectedTeacher
        assertTrue("Approved teachers who became teacher are allowed to create class", canApprovedTeacherCreateClass)
    }
}
