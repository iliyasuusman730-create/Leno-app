package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BlockReportDao
import com.example.data.dao.CallDao
import com.example.data.dao.ClassDao
import com.example.data.dao.ClassQuestionDao
import com.example.data.dao.EnrollmentDao
import com.example.data.dao.FriendshipDao
import com.example.data.dao.LessonDao
import com.example.data.dao.MessageDao
import com.example.data.dao.NotificationDao
import com.example.data.dao.TeacherRoleDao
import com.example.data.dao.UserDao
import com.example.data.dao.UserSettingsDao
import com.example.data.entity.BlockReportEntity
import com.example.data.entity.CallEntity
import com.example.data.entity.ClassEntity
import com.example.data.entity.ClassQuestionEntity
import com.example.data.entity.EnrollmentEntity
import com.example.data.entity.FriendshipEntity
import com.example.data.entity.LessonEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.NotificationEntity
import com.example.data.entity.TeacherRoleEntity
import com.example.data.entity.UserEntity
import com.example.data.entity.UserSettingsEntity
import androidx.room.migration.Migration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
        FriendshipEntity::class,
        BlockReportEntity::class,
        UserSettingsEntity::class,
        NotificationEntity::class,
        CallEntity::class,
        ClassEntity::class,
        EnrollmentEntity::class,
        TeacherRoleEntity::class,
        LessonEntity::class,
        ClassQuestionEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun friendshipDao(): FriendshipDao
    abstract fun blockReportDao(): BlockReportDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun notificationDao(): NotificationDao
    abstract fun callDao(): CallDao
    abstract fun classDao(): ClassDao
    abstract fun enrollmentDao(): EnrollmentDao
    abstract fun teacherRoleDao(): TeacherRoleDao
    abstract fun lessonDao(): LessonDao
    abstract fun classQuestionDao(): ClassQuestionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val database = db
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS classes (
                        classId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        level TEXT NOT NULL,
                        shortDescription TEXT NOT NULL,
                        lessonContent TEXT NOT NULL,
                        imageUrl TEXT NOT NULL,
                        optionalImages TEXT NOT NULL,
                        videoUrl TEXT NOT NULL,
                        hasVideo INTEGER NOT NULL,
                        isPaid INTEGER NOT NULL,
                        price TEXT NOT NULL,
                        schedule TEXT NOT NULL,
                        lessonType TEXT NOT NULL,
                        instructorUserId TEXT NOT NULL,
                        instructorName TEXT NOT NULL,
                        instructorUsername TEXT NOT NULL,
                        instructorLinoId TEXT NOT NULL,
                        instructorIsTeacher INTEGER NOT NULL,
                        instructorIsTrusted INTEGER NOT NULL,
                        enrolledStudentsCount INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PUBLISHED',
                        lessonCount INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS enrollments (
                        enrollmentId TEXT NOT NULL PRIMARY KEY,
                        classId TEXT NOT NULL,
                        studentUserId TEXT NOT NULL,
                        studentLinoId TEXT NOT NULL DEFAULT '',
                        teacherUserId TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        enrolledAt INTEGER NOT NULL,
                        progress INTEGER NOT NULL,
                        lastAccessedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS teacher_roles (
                        userId TEXT NOT NULL PRIMARY KEY,
                        status TEXT NOT NULL,
                        approvedSubject TEXT NOT NULL DEFAULT '',
                        isTrustedTeacher INTEGER NOT NULL DEFAULT 0,
                        isContentCreator INTEGER NOT NULL DEFAULT 0,
                        subjects TEXT NOT NULL,
                        teachingLevel TEXT NOT NULL,
                        educationQualification TEXT NOT NULL,
                        teachingExperience TEXT NOT NULL,
                        teacherIntro TEXT NOT NULL,
                        certificateDocumentName TEXT NOT NULL,
                        sampleTeachingInfo TEXT NOT NULL DEFAULT '',
                        agreedToGuidelines INTEGER NOT NULL,
                        submittedAt INTEGER NOT NULL,
                        reviewedAt INTEGER NOT NULL,
                        rejectionReason TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val database = db
                try {
                    database.execSQL("ALTER TABLE classes ADD COLUMN status TEXT NOT NULL DEFAULT 'PUBLISHED'")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE classes ADD COLUMN lessonCount INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE enrollments ADD COLUMN studentLinoId TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE enrollments ADD COLUMN teacherUserId TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE enrollments ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE teacher_roles ADD COLUMN approvedSubject TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE teacher_roles ADD COLUMN isTrustedTeacher INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE teacher_roles ADD COLUMN sampleTeachingInfo TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lessons (
                        lessonId TEXT NOT NULL PRIMARY KEY,
                        classId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        summary TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val database = db
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS class_questions (
                        questionId TEXT NOT NULL PRIMARY KEY,
                        classId TEXT NOT NULL,
                        senderUserId TEXT NOT NULL,
                        senderName TEXT NOT NULL,
                        senderLinoId TEXT NOT NULL,
                        senderRole TEXT NOT NULL DEFAULT 'STUDENT',
                        questionText TEXT NOT NULL,
                        answerText TEXT NOT NULL DEFAULT '',
                        isAnswered INTEGER NOT NULL DEFAULT 0,
                        answeredAt INTEGER NOT NULL DEFAULT 0,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                try {
                    database.execSQL("UPDATE classes SET isPaid = 1, price = '₦1,500' WHERE price = 'Free' OR isPaid = 0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val database = db
                try {
                    database.execSQL("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'STUDENT'")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE users ADD COLUMN assignedSubject TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    database.execSQL("UPDATE users SET role = 'TEACHER', assignedSubject = 'Mathematics' WHERE userId = 'user_len_50638964'")
                    database.execSQL("UPDATE users SET role = 'TEACHER', assignedSubject = 'Quran' WHERE userId = 'user_len_quran_teacher'")
                    database.execSQL("UPDATE users SET role = 'TEACHER', assignedSubject = 'English' WHERE userId = 'user_len_english_teacher'")
                    database.execSQL("UPDATE users SET role = 'TEACHER', assignedSubject = 'Vocational Skills' WHERE userId = 'user_len_skills_teacher'")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val database = db
                try {
                    database.execSQL("ALTER TABLE teacher_roles ADD COLUMN bankName TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE teacher_roles ADD COLUMN accountNumber TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE teacher_roles ADD COLUMN accountName TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE teacher_roles ADD COLUMN totalEarnings REAL NOT NULL DEFAULT 0.0")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE teacher_roles ADD COLUMN availableBalance REAL NOT NULL DEFAULT 0.0")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE teacher_roles ADD COLUMN totalPaidOut REAL NOT NULL DEFAULT 0.0")
                } catch (_: Exception) {}
                try {
                    // Purge duplicate seeded demo user so real user has only their own single profile
                    database.execSQL("DELETE FROM users WHERE userId = 'user_len_50638964' AND isCurrentUser = 0")
                    database.execSQL("DELETE FROM users WHERE username = 'jibril' AND isCurrentUser = 0")
                    database.execSQL("DELETE FROM teacher_roles WHERE userId = 'user_len_50638964'")
                    database.execSQL("UPDATE users SET role = 'USER' WHERE role = 'STUDENT'")
                    database.execSQL("UPDATE classes SET instructorUserId = 'user_len_maths_teacher', instructorName = 'Mallam Bello Isa', instructorUsername = 'bello_maths', instructorLinoId = 'LEN-33829104' WHERE instructorUserId = 'user_len_50638964'")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val database = db
                try {
                    database.execSQL("ALTER TABLE users ADD COLUMN teacherStatus TEXT NOT NULL DEFAULT 'NOT_APPLIED'")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE users ADD COLUMN teacherSubject TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE users ADD COLUMN creatorStatus TEXT NOT NULL DEFAULT 'INACTIVE'")
                } catch (_: Exception) {}
                try {
                    database.execSQL("ALTER TABLE users ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val database = db
                try {
                    database.execSQL("ALTER TABLE users ADD COLUMN isRestricted INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    database.execSQL("UPDATE users SET avatarUrl = '' WHERE avatarUrl LIKE '%picsum.photos%' OR avatarUrl LIKE '%demo%'")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val database = db
                try {
                    database.execSQL("ALTER TABLE users ADD COLUMN isVerified INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "leno_database.db"
                )
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database)
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database)
                    }
                }
            }
        }

        suspend fun populateDatabase(db: AppDatabase) {
            try {
                // Keep classes, lessons, and teachers intact so user messages and learning history never disappear
                return
            } catch (e: Exception) {
                android.util.Log.e("AppDatabase", "Error populating database: ${e.message}", e)
                return
            }
        }
    }
}
