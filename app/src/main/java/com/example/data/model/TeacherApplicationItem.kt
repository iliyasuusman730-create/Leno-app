package com.example.data.model

data class TeacherApplicationItem(
    val applicationId: String = "",
    val applicantUid: String = "",
    val lenoId: String = "",
    val fullName: String = "",
    val username: String = "",
    val subject: String = "",
    val qualification: String = "",
    val experience: String = "",
    val status: String = "PENDING", // "NOT_APPLIED", "PENDING", "APPROVED", "REJECTED"
    val submittedAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long = 0L,
    val reviewedBy: String = "",
    val rejectionReason: String = "",
    // Compatibility fields with existing UI
    val userId: String = applicantUid,
    val applicantName: String = fullName,
    val applicantUsername: String = username,
    val applicantEmail: String = "",
    val applicantAvatarUrl: String = "",
    val approvedSubject: String = subject,
    val teachingLevel: String = "All Levels",
    val educationQualification: String = qualification,
    val teachingExperience: String = experience,
    val teacherIntro: String = ""
) {
    val isPending: Boolean
        get() = status.equals("PENDING", ignoreCase = true)

    val isApproved: Boolean
        get() = status.equals("APPROVED", ignoreCase = true)

    val isRejected: Boolean
        get() = status.equals("REJECTED", ignoreCase = true)

    val effectiveSubject: String
        get() = approvedSubject.ifBlank { subject.split(",").firstOrNull()?.trim() ?: "Mathematics" }
}
