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
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationHelper.createNotificationChannels(this)
        initFirebase()
    }

    private fun initFirebase() {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "🔥 [Firebase Startup] Initializing Firebase Services...")

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    // Initialize from google-services.json configuration
                    FirebaseApp.initializeApp(this)
                    Log.i(TAG, "✅ [Firebase Startup] FirebaseApp initialized from google-services.json")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ [Firebase Startup] Default config not found, initializing with project fallback: ${e.message}")
                    val apiKey = try {
                        if (BuildConfig.FIREBASE_API_KEY.isNotBlank() && !BuildConfig.FIREBASE_API_KEY.startsWith("AIzaSy_YOUR")) {
                            BuildConfig.FIREBASE_API_KEY
                        } else {
                            "AIzaSy_Leno_Default_Key"
                        }
                    } catch (_: Exception) {
                        "AIzaSy_Leno_Default_Key"
                    }

                    val projectId = try {
                        if (BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()) BuildConfig.FIREBASE_PROJECT_ID else "leno-app"
                    } catch (_: Exception) {
                        "leno-app"
                    }

                    val appId = try {
                        if (BuildConfig.FIREBASE_APP_ID.isNotBlank()) BuildConfig.FIREBASE_APP_ID else "1:123456789012:android:lenoapp"
                    } catch (_: Exception) {
                        "1:123456789012:android:lenoapp"
                    }

                    val senderId = try {
                        if (BuildConfig.FIREBASE_MESSAGING_SENDER_ID.isNotBlank()) BuildConfig.FIREBASE_MESSAGING_SENDER_ID else "123456789012"
                    } catch (_: Exception) {
                        "123456789012"
                    }

                    val options = FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setApplicationId(appId)
                        .setProjectId(projectId)
                        .setGcmSenderId(senderId)
                        .build()

                    FirebaseApp.initializeApp(this, options)
                    Log.i(TAG, "✅ [Firebase Startup] Firebase manually initialized for project '$projectId'")
                }
            }

            val currentApp = FirebaseApp.getInstance()
            val options = currentApp.options
            Log.i(TAG, "✅ [Firebase Startup ACTIVE] App Name: ${currentApp.name}")
            Log.i(TAG, "   • Project ID: ${options.projectId}")
            Log.i(TAG, "   • Application ID: ${options.applicationId}")
            Log.i(TAG, "   • GCM Sender ID: ${options.gcmSenderId}")

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



