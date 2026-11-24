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
    private const val KEY_SEEN_LESSONS = "seen_lessons_"
    private const val KEY_BOOKMARKED_LESSONS = "bookmarked_lessons_"
    private const val KEY_READER_FONT_SCALE = "reader_font_scale"
    private const val KEY_READER_THEME_MODE = "reader_theme_mode"
    private const val KEY_ANNOUNCEMENTS = "class_announcements_"
    
    private var prefs: SharedPreferences? = null
    private val _updateTick = MutableStateFlow(0L)
    val updateTick: StateFlow<Long> = _updateTick.asStateFlow()

    var remoteSyncHandler: ((
        userId: String,
        classId: String,
        completedIds: Set<String>,
        quizScores: Map<String, String>,
        streakDays: Int,
        lastStudyDate: String,
        lastOpenedClassId: String?,
        lastOpenedLessonId: String?
    ) -> Unit)? = null

    var remoteCertificateHandler: ((AwardedCertificate) -> Unit)? = null

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
        notifyRemoteClassSync(context, userId, classId)
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

    private fun makeSeenKey(userId: String, classId: String): String =
        "$KEY_SEEN_LESSONS${userId.trim()}_${classId.trim()}"

    fun isLessonSeen(context: Context, userId: String, classId: String, lessonId: String): Boolean {
        if (userId.isBlank() || classId.isBlank() || lessonId.isBlank()) return false
        val p = getPrefs(context) ?: return false
        val set = p.getStringSet(makeSeenKey(userId, classId), emptySet()) ?: emptySet()
        return set.contains(lessonId) || isLessonCompleted(context, userId, classId, lessonId)
    }

    fun markLessonSeen(context: Context, userId: String, classId: String, lessonId: String) {
        if (userId.isBlank() || classId.isBlank() || lessonId.isBlank()) return
        val p = getPrefs(context) ?: return
        val key = makeSeenKey(userId, classId)
        val current = p.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.add(lessonId)) {
            p.edit().putStringSet(key, current).apply()
            _updateTick.value = System.currentTimeMillis()
        }
    }

    fun getSeenLessonIds(context: Context, userId: String, classId: String): Set<String> {
        if (userId.isBlank() || classId.isBlank()) return emptySet()
        val p = getPrefs(context) ?: return emptySet()
        val seen = p.getStringSet(makeSeenKey(userId, classId), emptySet()) ?: emptySet()
        return seen + getCompletedLessonIds(context, userId, classId)
    }

    private fun makeBookmarkKey(userId: String): String =
        "$KEY_BOOKMARKED_LESSONS${userId.trim()}"

    fun isLessonBookmarked(context: Context, userId: String, lessonId: String): Boolean {
        if (userId.isBlank() || lessonId.isBlank()) return false
        val p = getPrefs(context) ?: return false
        val set = p.getStringSet(makeBookmarkKey(userId), emptySet()) ?: emptySet()
        return set.contains(lessonId)
    }

    fun toggleLessonBookmark(context: Context, userId: String, lessonId: String): Boolean {
        if (userId.isBlank() || lessonId.isBlank()) return false
        val p = getPrefs(context) ?: return false
        val key = makeBookmarkKey(userId)
        val set = p.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        val nowBookmarked = if (set.contains(lessonId)) {
            set.remove(lessonId)
            false
        } else {
            set.add(lessonId)
            true
        }
        p.edit().putStringSet(key, set).apply()
        _updateTick.value = System.currentTimeMillis()
        return nowBookmarked
    }

    fun getReaderFontScale(context: Context): Float {
        val p = getPrefs(context) ?: return 1.0f
        return p.getFloat(KEY_READER_FONT_SCALE, 1.0f).coerceIn(0.85f, 1.35f)
    }

    fun setReaderFontScale(context: Context, scale: Float) {
        val p = getPrefs(context) ?: return
        p.edit().putFloat(KEY_READER_FONT_SCALE, scale.coerceIn(0.85f, 1.35f)).apply()
        _updateTick.value = System.currentTimeMillis()
    }

    /** 0 = App Default, 1 = Warm Paper (Sepia), 2 = Dark Slate */
    fun getReaderThemeMode(context: Context): Int {
        val p = getPrefs(context) ?: return 0
        return p.getInt(KEY_READER_THEME_MODE, 0).coerceIn(0, 2)
    }

    fun setReaderThemeMode(context: Context, mode: Int) {
        val p = getPrefs(context) ?: return
        p.edit().putInt(KEY_READER_THEME_MODE, mode.coerceIn(0, 2)).apply()
        _updateTick.value = System.currentTimeMillis()
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
        notifyRemoteClassSync(context, userId, classId)
    }

    /**
     * Returns saved quiz score (e.g. "2/2" or null if not yet taken).
     */
    fun getQuizScore(context: Context, userId: String, classId: String, lessonId: String): String? {
        val p = getPrefs(context) ?: return null
        val key = "$KEY_QUIZ_RESULTS${userId.trim()}_${classId.trim()}_${lessonId.trim()}"
        return p.getString(key, null)
    }

    fun getAllQuizScoresForClass(context: Context, userId: String, classId: String): Map<String, String> {
        val p = getPrefs(context) ?: return emptyMap()
        val prefix = "$KEY_QUIZ_RESULTS${userId.trim()}_${classId.trim()}_"
        val map = mutableMapOf<String, String>()
        p.all.forEach { (k, v) ->
            if (k.startsWith(prefix) && v is String) {
                val lessonId = k.removePrefix(prefix)
                if (lessonId.isNotBlank()) map[lessonId] = v
            }
        }
        return map
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
        notifyRemoteClassSync(context, userId, getLastOpenedClassId(context, userId).orEmpty())
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

    private const val KEY_LAST_OPENED_CLASS = "last_opened_class_"
    private const val KEY_LAST_OPENED_LESSON = "last_opened_lesson_"

    /**
     * Records the most recently opened class (and optionally lesson) for a student so
     * the "Continue Learning" hero card resumes right where they left off.
     */
    fun recordLastOpenedClass(
        context: Context,
        userId: String,
        classId: String,
        lessonId: String = ""
    ) {
        if (userId.isBlank() || classId.isBlank()) return
        val p = getPrefs(context) ?: return
        val editor = p.edit()
            .putString("$KEY_LAST_OPENED_CLASS${userId.trim()}", classId.trim())
        if (lessonId.isNotBlank()) {
            editor.putString("$KEY_LAST_OPENED_LESSON${userId.trim()}_${classId.trim()}", lessonId.trim())
        }
        editor.apply()
        _updateTick.value = System.currentTimeMillis()
        notifyRemoteClassSync(context, userId, classId)
    }

    fun getLastOpenedClassId(context: Context, userId: String): String? {
        if (userId.isBlank()) return null
        val p = getPrefs(context) ?: return null
        return p.getString("$KEY_LAST_OPENED_CLASS${userId.trim()}", null)?.takeIf { it.isNotBlank() }
    }

    fun getLastOpenedLessonId(context: Context, userId: String, classId: String): String? {
        if (userId.isBlank() || classId.isBlank()) return null
        val p = getPrefs(context) ?: return null
        return p.getString("$KEY_LAST_OPENED_LESSON${userId.trim()}_${classId.trim()}", null)?.takeIf { it.isNotBlank() }
    }

    fun getNextUncompletedLesson(
        context: Context,
        userId: String,
        classId: String,
        lessons: List<com.example.data.entity.LessonEntity>
    ): com.example.data.entity.LessonEntity? {
        if (lessons.isEmpty()) return null
        val sorted = lessons.sortedWith(compareBy<com.example.data.entity.LessonEntity> { it.orderIndex }.thenBy { it.createdAt })
        val completedIds = if (userId.isNotBlank()) getCompletedLessonIds(context, userId, classId) else emptySet()
        val lastLessonId = getLastOpenedLessonId(context, userId, classId)
        return sorted.firstOrNull { !completedIds.contains(it.lessonId) }
            ?: sorted.firstOrNull { it.lessonId == lastLessonId }
            ?: sorted.firstOrNull()
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

        remoteCertificateHandler?.invoke(cert)
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

    private fun notifyRemoteClassSync(context: Context, userId: String, classId: String) {
        if (userId.isBlank()) return
        val handler = remoteSyncHandler ?: return
        val p = getPrefs(context)
        val completedIds = if (classId.isNotBlank()) getCompletedLessonIds(context, userId, classId) else emptySet()
        val quizScores = if (classId.isNotBlank()) getAllQuizScoresForClass(context, userId, classId) else emptyMap()
        val streakDays = getStudyStreak(context, userId)
        val lastStudyDate = p?.getString("$KEY_LAST_STUDY_DATE${userId.trim()}", "") ?: ""
        val lastOpenedClassId = getLastOpenedClassId(context, userId)
        val lastOpenedLessonId = if (classId.isNotBlank()) getLastOpenedLessonId(context, userId, classId) else null
        handler.invoke(
            userId,
            classId,
            completedIds,
            quizScores,
            streakDays,
            lastStudyDate,
            lastOpenedClassId,
            lastOpenedLessonId
        )
    }

    fun applyRemoteClassProgress(
        context: Context,
        userId: String,
        classId: String,
        completedIds: Set<String>,
        quizScores: Map<String, String>,
        lastOpenedLessonId: String?
    ) {
        if (userId.isBlank() || classId.isBlank()) return
        val p = getPrefs(context) ?: return
        val key = makeClassKey(userId, classId)
        val localCompleted = p.getStringSet(key, emptySet()) ?: emptySet()
        val mergedCompleted = (localCompleted + completedIds).filter { it.isNotBlank() }.toSet()
        val editor = p.edit()
        var changed = false
        if (mergedCompleted != localCompleted) {
            editor.putStringSet(key, mergedCompleted)
            changed = true
        }
        quizScores.forEach { (lessonId, scoreStr) ->
            if (lessonId.isNotBlank() && scoreStr.isNotBlank()) {
                val qKey = "$KEY_QUIZ_RESULTS${userId.trim()}_${classId.trim()}_${lessonId.trim()}"
                if (p.getString(qKey, null) != scoreStr) {
                    editor.putString(qKey, scoreStr)
                    changed = true
                }
            }
        }
        if (!lastOpenedLessonId.isNullOrBlank()) {
            val lKey = "$KEY_LAST_OPENED_LESSON${userId.trim()}_${classId.trim()}"
            if (p.getString(lKey, null) != lastOpenedLessonId) {
                editor.putString(lKey, lastOpenedLessonId)
                changed = true
            }
        }
        if (changed) {
            editor.apply()
            _updateTick.value = System.currentTimeMillis()
        }
    }

    fun applyRemoteStudyProfile(
        context: Context,
        userId: String,
        streakDays: Int,
        lastStudyDate: String?,
        lastOpenedClassId: String?
    ) {
        if (userId.isBlank()) return
        val p = getPrefs(context) ?: return
        val editor = p.edit()
        var changed = false
        val streakKey = "$KEY_STREAK_DAYS${userId.trim()}"
        val currentStreak = p.getInt(streakKey, 0)
        if (streakDays > currentStreak) {
            editor.putInt(streakKey, streakDays)
            changed = true
        }
        if (!lastStudyDate.isNullOrBlank()) {
            val dateKey = "$KEY_LAST_STUDY_DATE${userId.trim()}"
            val currentDate = p.getString(dateKey, null)
            if (currentDate.isNullOrBlank() || lastStudyDate > currentDate) {
                editor.putString(dateKey, lastStudyDate)
                changed = true
            }
        }
        if (!lastOpenedClassId.isNullOrBlank()) {
            val classKey = "$KEY_LAST_OPENED_CLASS${userId.trim()}"
            if (p.getString(classKey, null).isNullOrBlank()) {
                editor.putString(classKey, lastOpenedClassId)
                changed = true
            }
        }
        if (changed) {
            editor.apply()
            _updateTick.value = System.currentTimeMillis()
        }
    }

    fun applyRemoteCertificate(context: Context, cert: AwardedCertificate) {
        if (cert.classId.isBlank() || cert.studentUserId.isBlank()) return
        val p = getPrefs(context) ?: return
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
        val key = "$KEY_AWARDED_CERT_PREFIX${cert.classId.trim()}_${cert.studentUserId.trim()}"
        if (p.getString(key, null) != json) {
            p.edit().putString(key, json).apply()
            _updateTick.value = System.currentTimeMillis()
        }
    }
}
