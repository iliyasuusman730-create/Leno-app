package com.example

import com.example.data.entity.UserEntity
import com.example.data.model.TeacherApplicationItem
import com.example.data.service.RemoteAccountService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealCloudSyncAndDeduplicationTest {

    private val service = RemoteAccountService()

    @Test
    fun deduplicateCanonicalUsers_prefersCanonicalUidDocOverAliasAndStripsInvalidEntries() {
        val staleAliasDoc = "LEN-12345678" to UserEntity(
            userId = "usr_alice_1",
            username = "alice",
            displayName = "Alice Old",
            linoId = "LEN-12345678",
            role = "USER",
            teacherStatus = "PENDING",
            updatedAt = 2_000_000L
        )
        val canonicalDoc = "usr_alice_1" to UserEntity(
            userId = "usr_alice_1",
            username = "alice",
            displayName = "Alice Canonical",
            linoId = "LEN-12345678",
            role = "TEACHER",
            assignedSubject = "Mathematics",
            teacherStatus = "APPROVED",
            updatedAt = 1_000_000L
        )
        val demoTeacher = "usr_1_teacher" to UserEntity(
            userId = "usr_1_teacher",
            username = "fake_teacher",
            displayName = "Fake Teacher"
        )
        val legacyOwnerDuplicate = "user_len_50638964" to UserEntity(
            userId = "user_len_50638964",
            username = "officialjaiby",
            displayName = "Dup Owner"
        )
        val dummyUser = "test_dummy_user_to_purge" to UserEntity(
            userId = "test_dummy_user_to_purge",
            username = "dummy",
            displayName = "Dummy"
        )
        val demoSeedUser = "usr_demo_bello" to UserEntity(
            userId = "usr_demo_bello",
            username = "bello_maths",
            displayName = "Bello"
        )

        val result = service.deduplicateCanonicalUsers(
            listOf(
                staleAliasDoc,
                canonicalDoc,
                demoTeacher,
                legacyOwnerDuplicate,
                dummyUser,
                demoSeedUser
            )
        )

        assertEquals(1, result.size)
        val alice = result.first()
        assertEquals("usr_alice_1", alice.userId)
        assertEquals("Alice Canonical", alice.displayName)
        assertEquals("TEACHER", alice.role)
        assertEquals("APPROVED", alice.teacherStatus)
        assertEquals("Mathematics", alice.assignedSubject)
    }

    @Test
    fun deduplicateCanonicalUsers_prefersHigherUpdatedAtWhenNoDocMatchesUid() {
        val older = "alias_1" to UserEntity(
            userId = "usr_bob",
            username = "bob",
            displayName = "Bob Old",
            updatedAt = 100L
        )
        val newer = "alias_2" to UserEntity(
            userId = "usr_bob",
            username = "bob",
            displayName = "Bob New",
            updatedAt = 500L
        )

        val result = service.deduplicateCanonicalUsers(listOf(older, newer))

        assertEquals(1, result.size)
        assertEquals("Bob New", result.first().displayName)
    }

    @Test
    fun docToUser_parsesCanonicalFieldsAndNumericTimestamps() {
        val parsed = service.docToUser(
            data = mapOf(
                "userId" to "usr_musa_42",
                "username" to "musa42",
                "displayName" to "Musa Ibrahim",
                "lenId" to "LEN-42424242",
                "role" to "teacher",
                "teacherStatus" to "APPROVED",
                "approvedSubject" to "Physics",
                "createdAt" to 1700000100000L,
                "updatedAt" to 1700000200000L,
                "lastSeen" to 1700000300000L
            ),
            docId = "usr_musa_42"
        )

        assertNotNull(parsed)
        assertEquals("usr_musa_42", parsed?.userId)
        assertEquals("musa42", parsed?.username)
        assertEquals("Musa Ibrahim", parsed?.displayName)
        assertEquals("LEN-42424242", parsed?.linoId)
        assertEquals("TEACHER", parsed?.role)
        assertEquals("APPROVED", parsed?.teacherStatus)
        assertEquals("Physics", parsed?.assignedSubject)
        assertEquals(1700000100000L, parsed?.createdAt)
        assertEquals(1700000200000L, parsed?.updatedAt)
        assertEquals(1700000300000L, parsed?.lastSeen)
    }

    @Test
    fun mergeTeacherApplicationSources_combinesAllCollectionsPrefersReviewedAndSortsDescending() {
        val primaryApp = TeacherApplicationItem(
            applicationId = "usr_amina",
            applicantUid = "usr_amina",
            userId = "usr_amina",
            fullName = "Amina Yusuf",
            subject = "Chemistry",
            qualification = "B.Sc. Chemistry",
            experience = "4 years",
            status = "PENDING",
            submittedAt = 2000L
        )
        val legacyReviewedApp = TeacherApplicationItem(
            applicationId = "usr_amina",
            applicantUid = "usr_amina",
            userId = "usr_amina",
            fullName = "Amina Yusuf",
            subject = "Chemistry",
            qualification = "B.Sc. Chemistry",
            experience = "4 years",
            status = "APPROVED",
            submittedAt = 2000L,
            reviewedAt = 3000L,
            reviewedBy = "usr_officialjaiby_2026"
        )
        val legacyOnlyApp = TeacherApplicationItem(
            applicationId = "usr_sadiq",
            applicantUid = "usr_sadiq",
            userId = "usr_sadiq",
            fullName = "Sadiq Bello",
            subject = "English",
            qualification = "B.A. English",
            experience = "3 years",
            status = "PENDING",
            submittedAt = 5000L
        )
        val fallbackPendingUser = TeacherApplicationItem(
            applicationId = "usr_zainab",
            applicantUid = "usr_zainab",
            userId = "usr_zainab",
            fullName = "Zainab Musa",
            subject = "Biology",
            status = "PENDING",
            submittedAt = 4000L
        )
        // Duplicate synthesized user record must NOT overwrite detailed primary/legacy item
        val duplicateFallbackForAmina = TeacherApplicationItem(
            applicationId = "usr_amina",
            applicantUid = "usr_amina",
            userId = "usr_amina",
            fullName = "Amina Minimal",
            subject = "Chemistry",
            status = "PENDING",
            submittedAt = 2000L
        )

        val merged = service.mergeTeacherApplicationSources(
            primary = listOf(primaryApp),
            legacy = listOf(legacyReviewedApp, legacyOnlyApp),
            pendingUsers = listOf(fallbackPendingUser, duplicateFallbackForAmina)
        )

        assertEquals(3, merged.size)
        // Sorted descending by submittedAt: usr_sadiq (5000), usr_zainab (4000), usr_amina (2000)
        assertEquals(listOf("usr_sadiq", "usr_zainab", "usr_amina"), merged.map { it.userId })

        val amina = merged.first { it.userId == "usr_amina" }
        assertTrue("Reviewed record from legacy/primary must win over pending record", amina.isApproved)
        assertEquals("B.Sc. Chemistry", amina.qualification)
        assertEquals(3000L, amina.reviewedAt)
    }
}
