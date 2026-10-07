package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val displayName: String = "",
    val bio: String = "Connecting on Leno ✨",
    val avatarUrl: String = "",
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis(),
    val statusMessage: String = "Hey there! I am using Leno.",
    val isCurrentUser: Boolean = false,
    val email: String = "",
    val password: String = "",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val recoveryCode: String = "",
    val phoneNumber: String = "",
    val normalizedPhoneNumber: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isOfficial: Boolean = false,
    val linoId: String = "",
    val googleEmail: String = "",
    val recoveryEmail: String = "",
    val role: String = "USER", // "USER", "OWNER", "ADMIN", or "TEACHER"
    val assignedSubject: String = "", // Legacy field kept for backward compatibility
    val teacherStatus: String = "NOT_APPLIED", // "NOT_APPLIED", "PENDING", "APPROVED", "REJECTED"
    val teacherSubject: String = "", // Approved single subject
    val creatorStatus: String = "INACTIVE",
    val isRestricted: Boolean = false,
    val isVerified: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val firebaseAuthUid: String get() = userId
    val fullName: String get() = displayName
    val profilePhoto: String get() = avatarUrl

    val isAccountProtected: Boolean
        get() = googleEmail.isNotBlank() || recoveryEmail.isNotBlank()

    val isTeacher: Boolean
        get() = teacherStatus.equals("APPROVED", ignoreCase = true) ||
                role.equals("TEACHER", ignoreCase = true) ||
                role.equals("teacher", ignoreCase = true)

    val isApprovedTeacher: Boolean
        get() = isTeacher

    val isStudent: Boolean
        get() = !isTeacher && !isAdmin && !isOwner

    // Platform Access Roles
    val isOwner: Boolean
        get() = role.equals("OWNER", ignoreCase = true) ||
                username.equals("iliyasuusman", ignoreCase = true) ||
                username.equals("iliyasu", ignoreCase = true) ||
                username.equals("officialjaiby", ignoreCase = true) ||
                email.equals("iliyasuusman730@gmail.com", ignoreCase = true) ||
                googleEmail.equals("iliyasuusman730@gmail.com", ignoreCase = true) ||
                email.contains("iliyasuusman", ignoreCase = true) ||
                googleEmail.contains("iliyasuusman", ignoreCase = true) ||
                linoId == "LEN-50638964" || linoId == "50638964" ||
                userId == "usr_officialjaiby_2026"

    val isCoFounder: Boolean
        get() = isOwner

    val isPlatformOwner: Boolean
        get() = isOwner

    val hasVerifiedBadge: Boolean
        get() = isVerified || isOfficial || isOwner || isAccountProtected || (isTeacher && teacherStatus == "APPROVED") || role.equals("VERIFIED", ignoreCase = true) || role.equals("OWNER", ignoreCase = true)

    val isVerifiedUser: Boolean
        get() = hasVerifiedBadge

    val isAdmin: Boolean
        get() = isOwner || role.equals("ADMIN", ignoreCase = true)

    val effectiveSubject: String
        get() = if (isTeacher) (teacherSubject.ifBlank { assignedSubject }).trim().split(",").firstOrNull()?.trim().orEmpty() else ""
}
