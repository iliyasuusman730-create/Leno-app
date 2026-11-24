package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.util.NotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LenoApplication : Application() {
    companion object {
        private const val TAG = "FirebaseStartup"
        var instance: LenoApplication? = null
            private set

        @Volatile
        var configuredFirestoreDatabaseId: String? = null
            private set

        fun resolveFirestoreDatabaseId(context: Context? = instance): String? {
            configuredFirestoreDatabaseId?.let { if (it.isNotBlank()) return it }
            val ctx = context?.applicationContext ?: instance ?: return null
            return try {
                val id = ctx.getString(R.string.firestore_database_id).trim()
                if (id.isNotBlank()) {
                    configuredFirestoreDatabaseId = id
                    id
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
        }

        fun getFirestoreInstance(context: Context? = instance): FirebaseFirestore? {
            return try {
                ensureFirebaseInitialized(context)
                val dbId = resolveFirestoreDatabaseId(context)
                if (!dbId.isNullOrBlank()) {
                    FirebaseFirestore.getInstance(dbId)
                } else {
                    null
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Firestore instance unavailable: ${e.message}")
                null
            }
        }

        fun ensureFirebaseInitialized(context: Context? = null) {
            val ctx = context ?: instance ?: return
            try {
                if (FirebaseApp.getApps(ctx).isEmpty()) {
                    FirebaseApp.initializeApp(ctx)
                }
                resolveFirestoreDatabaseId(ctx)
            } catch (e: Throwable) {
                Log.w(TAG, "Firebase startup notice: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            NotificationHelper.createNotificationChannels(this)
        } catch (e: Exception) {
            Log.w(TAG, "Notification channels notice: ${e.message}")
        }
        try {
            com.example.util.LessonProgressManager.init(this)
        } catch (_: Exception) {}
        try {
            ensureFirebaseInitialized(this)
            val app = FirebaseApp.getApps(this).firstOrNull()
            val dbId = resolveFirestoreDatabaseId(this)
            val currentUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Throwable) { null }
            Log.i(TAG, "Firebase initialized: app=${app?.name}, databaseId=$dbId, authUid=${currentUser?.uid ?: "none"}")
        } catch (e: Exception) {
            Log.w(TAG, "initFirebase notice: ${e.message}")
        }
    }
}
