package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teacher_roles")
data class TeacherRoleEntity(
    @PrimaryKey val userId: String,
    val status: String = "NOT APPLIED", // "NOT APPLIED", "PENDING", "APPROVED", "REJECTED"
    val approvedSubject: String = "", // ONE TEACHER = ONE SUBJECT
    val isTrustedTeacher: Boolean = false,
    val isContentCreator: Boolean = false,
    val subjects: String = "", // Submitted subject
    val teachingLevel: String = "All Levels",
    val educationQualification: String = "",
    val teachingExperience: String = "",
    val teacherIntro: String = "",
    val certificateDocumentName: String = "", // PRIVATE - kept strictly confidential
    val sampleTeachingInfo: String = "",
    val agreedToGuidelines: Boolean = true,
    val submittedAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long = 0L,
    val rejectionReason: String = "",
    // Real-Time Teacher Settlement & Bank Payout Fields:
    val bankName: String = "",
    val accountNumber: String = "",
    val accountName: String = "",
    val totalEarnings: Double = 0.0,
    val availableBalance: Double = 0.0,
    val totalPaidOut: Double = 0.0
) {
    val isApprovedTeacher: Boolean
        get() = status.equals("APPROVED", ignoreCase = true) ||
                status.equals("Teacher", ignoreCase = true) ||
                status.equals("Trusted Teacher", ignoreCase = true)

    val isTeacher: Boolean
        get() = isApprovedTeacher

    val isPending: Boolean
        get() = status.equals("PENDING", ignoreCase = true) ||
                status.equals("Pending", ignoreCase = true)

    val isRejected: Boolean
        get() = status.equals("REJECTED", ignoreCase = true) ||
                status.equals("Rejected", ignoreCase = true)

    val isNotApplied: Boolean
        get() = status.equals("NOT APPLIED", ignoreCase = true) ||
                status.equals("Normal User", ignoreCase = true) ||
                status.isBlank()

    /**
     * Effective approved subject.
     * Guaranteed single subject.
     */
    val effectiveApprovedSubject: String
        get() = approvedSubject.ifBlank {
            subjects.split(",").firstOrNull()?.trim() ?: "Mathematics"
        }
}
