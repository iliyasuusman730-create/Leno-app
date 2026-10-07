package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.CallOverlay
import com.example.ui.navigation.LenoNavGraph
import com.example.ui.theme.LenoTheme
import com.example.util.ThemeMode
import com.example.viewmodel.LenoViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: LenoViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Notification permission handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestNotificationPermission()
        handleIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDarkSystem = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isDarkSystem
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            val callState by viewModel.callState.collectAsState()

            LenoTheme(darkTheme = isDark) {
                val navController = rememberNavController()

                val activePartnerId by viewModel.activeChatPartnerId.collectAsState()
                LaunchedEffect(activePartnerId) {
                    val partnerId = intent?.getStringExtra("chat_partner_id")
                    if (!partnerId.isNullOrEmpty()) {
                        viewModel.openChat(partnerId)
                        intent.removeExtra("chat_partner_id")
                    }
                }

                LenoNavGraph(
                    navController = navController,
                    viewModel = viewModel
                )

                CallOverlay(
                    callState = callState,
                    onAcceptCall = { viewModel.acceptIncomingCall() },
                    onDeclineCall = { viewModel.declineIncomingCall() },
                    onEndCall = { viewModel.endCall() },
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleSpeaker = { viewModel.toggleSpeaker() },
                    onSimulateAnswer = { viewModel.answerCallForTesting() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val partnerId = intent?.getStringExtra("chat_partner_id")
        if (!partnerId.isNullOrEmpty()) {
            viewModel.openChat(partnerId)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setUserPresence(true)
    }

    override fun onPause() {
        super.onPause()
        viewModel.setUserPresence(false)
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
