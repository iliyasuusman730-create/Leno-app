package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages student lesson completion progress, quiz attempts, and class announcements locally
 * with reactive StateFlow triggers so UI updates immediately across all screens.
 */
object LessonProgressManager {
    private const val PREF_NAME = "leno_lesson_progress"
    private const val KEY_COMPLETED_LESSONS = "completed_lessons_"
    private const val KEY_ANNOUNCEMENTS = "class_announcements_"
    
    private var prefs: SharedPreferences? = null
    private val _updateTick = MutableStateFlow(0L)
    val updateTick: StateFlow<Long> = _updateTick.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(context: Context? = null): SharedPreferences? {
        if (prefs == null && context != null) {
            init(context)
        }
        return prefs
    }

    private fun makeClassKey(userId: String, classId: String): String =
        "$KEY_COMPLETED_LESSONS${userId.trim()}_${classId.trim()}"

    /**
     * Checks if a specific lesson is completed by the user.
     */
    fun isLessonCompleted(context: Context, userId: String, classId: String, lessonId: String): Boolean {
        val p = getPrefs(context) ?: return false
        val key = makeClassKey(userId, classId)
        val set = p.getStringSet(key, emptySet()) ?: emptySet()
        return set.contains(lessonId)
    }

    /**
     * Toggles or sets lesson completion status.
     */
    fun setLessonCompleted(context: Context, userId: String, classId: String, lessonId: String, completed: Boolean) {
        val p = getPrefs(context) ?: return
        val key = makeClassKey(userId, classId)
        val currentSet = p.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (completed) {
            currentSet.add(lessonId)
            recordStudyActivity(context, userId)
        } else {
            currentSet.remove(lessonId)
        }
        p.edit().putStringSet(key, currentSet).apply()
        _updateTick.value = System.currentTimeMillis()
    }

    /**
     * Returns the set of completed lesson IDs for a class.
     */
    fun getCompletedLessonIds(context: Context, userId: String, classId: String): Set<String> {
        val p = getPrefs(context) ?: return emptySet()
        val key = makeClassKey(userId, classId)
        return p.getStringSet(key, emptySet()) ?: emptySet()
    }

    /**
     * Returns the count of completed lessons.
     */
    fun getCompletedCount(context: Context, userId: String, classId: String): Int {
        return getCompletedLessonIds(context, userId, classId).size
    }

    /**
     * Computes completion percentage (0 to 100).
     */
    fun getProgressPercentage(context: Context, userId: String, classId: String, totalLessons: Int): Int {
        if (totalLessons <= 0) return 0
        val completed = getCompletedCount(context, userId, classId)
        return ((completed.toFloat() / totalLessons.toFloat()) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Generates a standard 6-character Class Code for easy sharing and blackboard entry.
     * e.g. classId "cls_84920a1b" -> "LEN-84920A"
     */
    fun formatClassCode(classId: String): String {
        val clean = classId.replace("cls_", "").replace("-", "").uppercase()
        val codePart = if (clean.length >= 6) clean.take(6) else clean.padEnd(6, 'X')
        return "LEN-$codePart"
    }

    /**
     * Matches user-entered code against classes.
     * Supports formats like "LEN-84920A", "84920A", or exact "cls_84920a1b".
     */
    fun matchesClassCode(userCode: String, classId: String): Boolean {
        val cleanInput = userCode.trim().uppercase().replace("LEN-", "").replace("LEN", "").replace("-", "")
        if (cleanInput.isBlank()) return false
        val formatted = formatClassCode(classId).replace("LEN-", "")
        val rawClassId = classId.uppercase()
        return rawClassId.contains(cleanInput) || formatted.equals(cleanInput, ignoreCase = true)
    }

    private const val KEY_QUIZ_RESULTS = "quiz_results_"

    /**
     * Saves quiz score for a student's lesson (e.g. 2 out of 2).
     */
    fun saveQuizScore(context: Context, userId: String, classId: String, lessonId: String, score: Int, total: Int) {
        val p = getPrefs(context) ?: return
        val key = "$KEY_QUIZ_RESULTS${userId.trim()}_${classId.trim()}_${lessonId.trim()}"
        p.edit().putString(key, "$score/$total").apply()
        _updateTick.value = System.currentTimeMillis()
    }

    /**
     * Returns saved quiz score (e.g. "2/2" or null if not yet taken).
     */
    fun getQuizScore(context: Context, userId: String, classId: String, lessonId: String): String? {
        val p = getPrefs(context) ?: return null
        val key = "$KEY_QUIZ_RESULTS${userId.trim()}_${classId.trim()}_${lessonId.trim()}"
        return p.getString(key, null)
    }

    /**
     * Checks if a quiz was passed.
     */
    fun isQuizPassed(context: Context, userId: String, classId: String, lessonId: String): Boolean {
        val score = getQuizScore(context, userId, classId, lessonId) ?: return false
        val parts = score.split("/")
        val correct = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val total = parts.getOrNull(1)?.toIntOrNull() ?: 1
        return correct > 0 && correct >= (total / 2)
    }

    private const val KEY_STREAK_DAYS = "learning_streak_days_"
    private const val KEY_LAST_STUDY_DATE = "last_study_date_"

    /**
     * Records a study activity for today and updates the student's daily streak.
     */
    fun recordStudyActivity(context: Context, userId: String): Int {
        val p = getPrefs(context) ?: return 1
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDateKey = "$KEY_LAST_STUDY_DATE${userId.trim()}"
        val streakKey = "$KEY_STREAK_DAYS${userId.trim()}"

        val lastDate = p.getString(lastDateKey, null)
        val currentStreak = p.getInt(streakKey, 0)

        if (lastDate == todayStr) {
            return maxOf(currentStreak, 1)
        }

        val newStreak = if (lastDate != null && isYesterday(lastDate)) {
            currentStreak + 1
        } else {
            1
        }

        p.edit()
            .putString(lastDateKey, todayStr)
            .putInt(streakKey, newStreak)
            .apply()
        _updateTick.value = System.currentTimeMillis()
        return newStreak
    }

    private fun isYesterday(dateStr: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return false
            val calLast = java.util.Calendar.getInstance().apply { time = date }
            val calToday = java.util.Calendar.getInstance()
            calToday.add(java.util.Calendar.DAY_OF_YEAR, -1)
            calLast.get(java.util.Calendar.YEAR) == calToday.get(java.util.Calendar.YEAR) &&
                    calLast.get(java.util.Calendar.DAY_OF_YEAR) == calToday.get(java.util.Calendar.DAY_OF_YEAR)
        } catch (e: Exception) {
            false
        }
    }

    fun getStudyStreak(context: Context, userId: String): Int {
        val p = getPrefs(context) ?: return 1
        val streakKey = "$KEY_STREAK_DAYS${userId.trim()}"
        return maxOf(p.getInt(streakKey, 1), 1)
    }

    fun formatCertificateId(classId: String, userId: String): String {
        val cPart = classId.replace("cls_", "").replace("-", "").take(4).uppercase()
        val uPart = userId.replace("usr_", "").replace("-", "").take(4).uppercase()
        return "LEN-CERT-$cPart$uPart"
    }

    private const val KEY_AWARDED_CERT_PREFIX = "teacher_awarded_cert_"

    data class AwardedCertificate(
        val classId: String,
        val studentUserId: String,
        val studentName: String,
        val teacherUserId: String,
        val teacherName: String,
        val awardTitle: String,
        val teacherCommendation: String,
        val issueDate: String,
        val certificateId: String
    )

    /**
     * Teacher awards an official Certificate to a hardworking student.
     */
    fun awardCertificate(
        context: Context,
        classId: String,
        studentUserId: String,
        studentName: String,
        teacherUserId: String,
        teacherName: String,
        awardTitle: String,
        teacherCommendation: String
    ): AwardedCertificate {
        val certId = formatCertificateId(classId, studentUserId)
        val todayStr = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())

        val cert = AwardedCertificate(
            classId = classId.trim(),
            studentUserId = studentUserId.trim(),
            studentName = studentName.trim().ifBlank { "Leno Scholar" },
            teacherUserId = teacherUserId.trim(),
            teacherName = teacherName.trim().ifBlank { "Teacher" },
            awardTitle = awardTitle.trim().ifBlank { "Certificate of Academic Excellence" },
            teacherCommendation = teacherCommendation.trim().ifBlank { "Awarded in recognition of dedicated effort, academic diligence, and outstanding achievement." },
            issueDate = todayStr,
            certificateId = certId
        )

        val p = getPrefs(context)
        if (p != null) {
            val json = org.json.JSONObject().apply {
                put("classId", cert.classId)
                put("studentUserId", cert.studentUserId)
                put("studentName", cert.studentName)
                put("teacherUserId", cert.teacherUserId)
                put("teacherName", cert.teacherName)
                put("awardTitle", cert.awardTitle)
                put("teacherCommendation", cert.teacherCommendation)
                put("issueDate", cert.issueDate)
                put("certificateId", cert.certificateId)
            }.toString()

            val key = "$KEY_AWARDED_CERT_PREFIX${classId.trim()}_${studentUserId.trim()}"
            p.edit().putString(key, json).apply()
            _updateTick.value = System.currentTimeMillis()
        }

        return cert
    }

    /**
     * Checks if a certificate has been awarded to this student by the teacher.
     */
    fun isCertificateAwarded(context: Context, classId: String, studentUserId: String): Boolean {
        if (classId.isBlank() || studentUserId.isBlank()) return false
        val p = getPrefs(context) ?: return false
        val key = "$KEY_AWARDED_CERT_PREFIX${classId.trim()}_${studentUserId.trim()}"
        return p.contains(key)
    }

    /**
     * Retrieves the teacher-awarded certificate for a student in a class.
     */
    fun getAwardedCertificate(context: Context, classId: String, studentUserId: String): AwardedCertificate? {
        if (classId.isBlank() || studentUserId.isBlank()) return false.let { null }
        val p = getPrefs(context) ?: return null
        val key = "$KEY_AWARDED_CERT_PREFIX${classId.trim()}_${studentUserId.trim()}"
        val jsonStr = p.getString(key, null) ?: return null
        return try {
            val obj = org.json.JSONObject(jsonStr)
            AwardedCertificate(
                classId = obj.optString("classId"),
                studentUserId = obj.optString("studentUserId"),
                studentName = obj.optString("studentName"),
                teacherUserId = obj.optString("teacherUserId"),
                teacherName = obj.optString("teacherName"),
                awardTitle = obj.optString("awardTitle", "Certificate of Academic Excellence"),
                teacherCommendation = obj.optString("teacherCommendation", "Awarded in recognition of dedicated effort and academic achievement."),
                issueDate = obj.optString("issueDate", ""),
                certificateId = obj.optString("certificateId", "")
            )
        } catch (e: Exception) {
            null
        }
    }
}
