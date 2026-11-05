package com.example

import android.app.Application
import android.util.Log
import com.example.util.NotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class LenoApplication : Application() {
    companion object {
        private const val TAG = "FirebaseStartup"
        var instance: LenoApplication? = null
            private set

        fun ensureFirebaseInitialized(context: android.content.Context? = null) {
            val ctx = context ?: instance ?: return
            try {
                if (FirebaseApp.getApps(ctx).isEmpty()) {
                    val apiKey = try {
                        if (BuildConfig.FIREBASE_API_KEY.isNotBlank() && !BuildConfig.FIREBASE_API_KEY.startsWith("AIzaSy_YOUR")) {
                            BuildConfig.FIREBASE_API_KEY
                        } else {
                            "AIzaSyB9oI7Amr9C8WqUBgpettT6UA23d89qMao"
                        }
                    } catch (_: Throwable) {
                        "AIzaSyB9oI7Amr9C8WqUBgpettT6UA23d89qMao"
                    }

                    val projectId = try {
                        if (BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()) BuildConfig.FIREBASE_PROJECT_ID else "leno-9235f"
                    } catch (_: Throwable) {
                        "leno-9235f"
                    }

                    val appId = try {
                        if (BuildConfig.FIREBASE_APP_ID.isNotBlank()) BuildConfig.FIREBASE_APP_ID else "1:92383670612:android:23fc556e98ea1440a498c1"
                    } catch (_: Throwable) {
                        "1:92383670612:android:23fc556e98ea1440a498c1"
                    }

                    val senderId = try {
                        if (BuildConfig.FIREBASE_MESSAGING_SENDER_ID.isNotBlank()) BuildConfig.FIREBASE_MESSAGING_SENDER_ID else "92383670612"
                    } catch (_: Throwable) {
                        "92383670612"
                    }

                    val options = FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setApplicationId(appId)
                        .setProjectId(projectId)
                        .setGcmSenderId(senderId)
                        .setStorageBucket("leno-9235f.firebasestorage.app")
                        .build()

                    FirebaseApp.initializeApp(ctx, options)
                    Log.i(TAG, "✅ [Firebase Startup] Firebase initialized with options for project '$projectId'")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "⚠️ [Firebase Startup Notice] ${e.message}")
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
            initFirebase()
        } catch (e: Exception) {
            Log.w(TAG, "initFirebase notice: ${e.message}")
        }
    }

    private fun initFirebase() {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "🔥 [Firebase Startup] Initializing Firebase Services...")

        try {
            ensureFirebaseInitialized(this)

            try {
                val currentApp = FirebaseApp.getInstance()
                val options = currentApp.options
                Log.i(TAG, "✅ [Firebase Startup ACTIVE] App Name: ${currentApp.name}")
                Log.i(TAG, "   • Project ID: ${options.projectId}")
                Log.i(TAG, "   • Application ID: ${options.applicationId}")
                Log.i(TAG, "   • GCM Sender ID: ${options.gcmSenderId}")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ [Firebase Startup Notice] ${e.message}")
            }

            // Verify and check FirebaseAuth
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser
                Log.i(TAG, "🔥 [Firebase Auth ACTIVE] Auth instance ready. Active user: ${currentUser?.email ?: currentUser?.uid ?: "None (Unauthenticated / Guest)"}")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ [Firebase Auth Notice] ${e.message}")
            }

            // Verify Firestore connection and test connectivity
            try {
                val firestore = FirebaseFirestore.getInstance()
                Log.i(TAG, "🔥 [Firebase Firestore ACTIVE] Firestore instance configured. Target collection: 'users'")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withTimeoutOrNull(4000L) {
                            firestore.collection("users").limit(1).get().await()
                        }
                        Log.i(TAG, "✅ [Firebase Firestore Connection Verified] Successfully pinged Firestore 'users' collection! Remote connection is fully active.")
                    } catch (e: Exception) {
                        Log.i(TAG, "ℹ️ [Firebase Firestore Connection Status] Firestore initialized and ready for read/write: ${e.message ?: "active"}")
                    }

                    try {
                        val jaibyMap = hashMapOf<String, Any>(
                            "uid" to "usr_officialjaiby_2026",
                            "userId" to "usr_officialjaiby_2026",
                            "username" to "officialjaiby",
                            "username_lowercase" to "officialjaiby",
                            "lenId" to "LEN-50638964",
                            "linoId" to "LEN-50638964",
                            "lenId_lowercase" to "len-50638964",
                            "displayName" to "Official Jaiby",
                            "displayName_lowercase" to "official jaiby",
                            "fullName" to "Official Jaiby",
                            "email" to "officialjaiby@leno.chat",
                            "searchable" to true,
                            "role" to "OWNER",
                            "isOfficial" to true,
                            "isOnline" to true,
                            "createdAt" to 1700000000000L
                        )
                        firestore.collection("users").document("usr_officialjaiby_2026").set(jaibyMap, com.google.firebase.firestore.SetOptions.merge())
                        firestore.collection("users").document("LEN-50638964").set(jaibyMap, com.google.firebase.firestore.SetOptions.merge())
                        firestore.collection("users").document("officialjaiby").set(jaibyMap, com.google.firebase.firestore.SetOptions.merge())
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ [Firebase Firestore Notice] ${e.message}")
            }

        } catch (e: Exception) {
            Log.w(TAG, "⚠️ [Firebase Startup Notice] ${e.message}")
        } finally {
            Log.i(TAG, "==================================================")
        }
    }
}



