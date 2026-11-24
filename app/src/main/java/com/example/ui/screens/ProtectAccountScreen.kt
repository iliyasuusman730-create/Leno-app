package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ChangePasswordDialog
import com.example.ui.components.DisableSecurityPinDialog
import com.example.ui.components.SetupSecurityPinDialog
import com.example.viewmodel.LenoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectAccountScreen(
    viewModel: LenoViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val isPinEnabled by viewModel.isSecurityPinEnabled.collectAsState()
    val securityEvents by viewModel.securityEvents.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val isDarkTheme = com.example.ui.theme.LocalLenoDarkTheme.current
    val brandOrange = if (isDarkTheme) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val brandBg = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val inputBg = MaterialTheme.colorScheme.surfaceVariant
    val inputBorder = MaterialTheme.colorScheme.outline
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val successGreen = com.example.ui.theme.LenoSuccess
    val warningAmber = com.example.ui.theme.LenoWarning

    val isGoogleConnected = currentUser?.googleEmail?.isNotBlank() == true
    val isEmailConnected = currentUser?.recoveryEmail?.isNotBlank() == true
    val isProtected = isGoogleConnected || isEmailConnected
    val recoveryCode = currentUser?.recoveryCode.orEmpty()
    val hasBackupCode = recoveryCode.isNotBlank()
    val hasExistingPassword = (currentUser?.passwordHash?.isNotBlank() == true) ||
        (currentUser?.password?.isNotBlank() == true)

    val completedPillars = listOf(isProtected, hasBackupCode, isPinEnabled).count { it }
    val securityProgress = completedPillars / 3f

    LaunchedEffect(currentUser?.userId) {
        if (currentUser != null) {
            viewModel.ensureRecoveryCode()
            viewModel.refreshSecurityState()
        }
    }

    var showEmailConnectDialog by remember { mutableStateOf(false) }
    var showCannotRemoveDialog by remember { mutableStateOf<String?>(null) }
    var showConfirmDisconnectDialog by remember { mutableStateOf<String?>(null) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showSetupPinDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }
    var showRegenerateCodeDialog by remember { mutableStateOf(false) }
    var revealRecoveryCode by remember { mutableStateOf(false) }

    var emailInput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Protect Your Account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("protect_account_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = brandBg
                )
            )
        },
        containerColor = brandBg,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("protect_account_screen")
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Status Header Banner + Security Score
            if (isProtected) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_protected_banner"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(successGreen.copy(alpha = 0.6f), brandOrange.copy(alpha = 0.4f))
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(successGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Account Protected",
                                    tint = successGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (completedPillars == 3) "Strong Protection" else "Account Protected",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        modifier = Modifier.testTag("account_protected_badge")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "✓",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = successGreen
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Your Leno account has active recovery methods. You can regain access securely at any time.",
                                    fontSize = 13.sp,
                                    color = textSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Security Score",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                            Text(
                                text = "$completedPillars / 3 checks active",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = successGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { securityProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = successGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_unprotected_banner"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(warningAmber.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Security Alert",
                                    tint = warningAmber,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (completedPillars >= 1) "Partial Protection" else "Protect Your Account",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Save your Backup Recovery Code and link Google or an email so you never lose access to your Leno account.",
                                    fontSize = 13.sp,
                                    color = textSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Security Score",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                            Text(
                                text = "$completedPillars / 3 checks active",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = warningAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { securityProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = warningAmber,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subheading
            Text(
                text = "Choose a recovery method",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // METHOD 1: CONNECT GOOGLE
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connect_google_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(
                            if (isGoogleConnected) successGreen.copy(alpha = 0.4f) else inputBorder,
                            inputBorder
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isGoogleConnected) successGreen else com.example.ui.theme.LenoInfo
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Connect Google",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                if (isGoogleConnected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "✓ Connected",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = successGreen
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isGoogleConnected) {
                                    currentUser?.googleEmail ?: ""
                                } else {
                                    "Use your Google account to recover and sign in to Leno."
                                },
                                fontSize = 13.sp,
                                color = if (isGoogleConnected) textPrimary else textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isGoogleConnected) {
                        Button(
                            onClick = {
                                if (isProcessing) return@Button
                                isProcessing = true
                                dialogError = null
                                com.example.util.GoogleAuthHelper.launchGoogleSignIn(
                                    context = context,
                                    scope = coroutineScope,
                                    onAuthSuccess = { firebaseUser ->
                                        viewModel.linkVerifiedGoogleAccount(firebaseUser) { res ->
                                            isProcessing = false
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Verified Google account connected ✓", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    res.exceptionOrNull()?.message ?: "Failed to connect Google account",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },
                                    onAuthError = { errMsg ->
                                        isProcessing = false
                                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                                    },
                                    onAuthCancelled = {
                                        isProcessing = false
                                    }
                                )
                            },
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = brandOrange,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("connect_google_button")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Connect Google",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Connected for sign-in & recovery",
                                fontSize = 12.sp,
                                color = successGreen,
                                fontWeight = FontWeight.SemiBold
                            )

                            OutlinedButton(
                                onClick = {
                                    if (!isEmailConnected) {
                                        showCannotRemoveDialog = "Google"
                                    } else {
                                        showConfirmDisconnectDialog = "GOOGLE"
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = com.example.ui.theme.LenoDangerPrimary
                                ),
                                modifier = Modifier.testTag("disconnect_google_button")
                            ) {
                                Text("Disconnect", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // METHOD 2: ADD EMAIL ADDRESS
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_email_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(
                            if (isEmailConnected) successGreen.copy(alpha = 0.4f) else inputBorder,
                            inputBorder
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email",
                                tint = if (isEmailConnected) successGreen else brandOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Add Email Address",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                if (isEmailConnected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "✓ Connected",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = successGreen
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isEmailConnected) {
                                    currentUser?.recoveryEmail ?: ""
                                } else {
                                    "Add an email address to recover your Leno account."
                                },
                                fontSize = 13.sp,
                                color = if (isEmailConnected) textPrimary else textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isEmailConnected) {
                        Button(
                            onClick = {
                                dialogError = null
                                emailInput = ""
                                showEmailConnectDialog = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = brandOrange,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("add_email_button")
                        ) {
                            Text(
                                text = "Add Email",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Connected for recovery",
                                fontSize = 12.sp,
                                color = successGreen,
                                fontWeight = FontWeight.SemiBold
                            )

                            OutlinedButton(
                                onClick = {
                                    if (!isGoogleConnected) {
                                        showCannotRemoveDialog = "Email"
                                    } else {
                                        showConfirmDisconnectDialog = "EMAIL"
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = com.example.ui.theme.LenoDangerPrimary
                                ),
                                modifier = Modifier.testTag("disconnect_email_button")
                            ) {
                                Text("Disconnect", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ==========================================
            // BACKUP RECOVERY CODE
            // ==========================================
            Text(
                text = "Private Backup Code",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_recovery_code_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, inputBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(brandOrange.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Backup Recovery Code",
                                tint = brandOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Backup Recovery Code",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = "Keep this private code safe to reset your password if you lose access.",
                                fontSize = 12.sp,
                                color = textSecondary,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, inputBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    recoveryCode.isBlank() -> "Generating..."
                                    revealRecoveryCode -> recoveryCode
                                    else -> "REC-••••-••••"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp,
                                color = textPrimary,
                                modifier = Modifier.testTag("backup_recovery_code_value")
                            )
                            IconButton(
                                onClick = { revealRecoveryCode = !revealRecoveryCode },
                                modifier = Modifier.testTag("toggle_recovery_code_button")
                            ) {
                                Icon(
                                    imageVector = if (revealRecoveryCode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (revealRecoveryCode) "Hide recovery code" else "Reveal recovery code",
                                    tint = brandOrange
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (recoveryCode.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(recoveryCode))
                                    Toast.makeText(context, "Recovery code copied to clipboard ✓", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = recoveryCode.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_recovery_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Code", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showRegenerateCodeDialog = true },
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("regenerate_recovery_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Regenerate", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ==========================================
            // PASSWORD & APP SECURITY PIN
            // ==========================================
            Text(
                text = "Password & App Lock",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_and_pin_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, inputBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(brandOrange.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Password,
                                contentDescription = null,
                                tint = brandOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasExistingPassword) "Account Password" else "Set Account Password",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = "Protected with salted PBKDF2-HMAC-SHA256",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = { showChangePasswordDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("protect_change_password_button")
                        ) {
                            Text(
                                text = if (hasExistingPassword) "Change" else "Set",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = inputBorder
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPinEnabled) successGreen.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pin,
                                contentDescription = null,
                                tint = if (isPinEnabled) successGreen else textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Security PIN",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = if (isPinEnabled) "4–6 digit PIN lock is active on launch" else "Lock Leno with a private 4–6 digit PIN",
                                fontSize = 12.sp,
                                color = if (isPinEnabled) successGreen else textSecondary
                            )
                        }
                        Switch(
                            checked = isPinEnabled,
                            onCheckedChange = { shouldEnable ->
                                if (shouldEnable) {
                                    showSetupPinDialog = true
                                } else {
                                    showDisablePinDialog = true
                                }
                            },
                            modifier = Modifier.testTag("protect_security_pin_switch")
                        )
                    }

                    AnimatedVisibility(visible = isPinEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.lockAppNow() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("lock_app_now_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Lock Leno Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ==========================================
            // RECENT SECURITY ACTIVITY LOG
            // ==========================================
            Text(
                text = "Recent Security Activity",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("security_activity_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, inputBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = brandOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Security Audit Log",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (securityEvents.isEmpty()) {
                        Text(
                            text = "No recent security events recorded on this device yet.",
                            fontSize = 13.sp,
                            color = textSecondary
                        )
                    } else {
                        val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                        securityEvents.take(6).forEachIndexed { idx, ev ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = inputBorder.copy(alpha = 0.6f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ev.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    )
                                    if (ev.detail.isNotBlank()) {
                                        Text(
                                            text = ev.detail,
                                            fontSize = 12.sp,
                                            color = textSecondary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatter.format(Date(ev.timestamp)),
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Security & Privacy Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = brandOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "How Account Recovery Works",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Password resets strictly require your private Backup Recovery Code (REC-...) or a verified Google / Recovery Email.\n" +
                               "• Brute-force protection locks login, recovery, and PIN retries for 60 seconds after 5 failed attempts.\n" +
                               "• Passwords and App PINs are never stored in plain text and are protected with salted PBKDF2-HMAC-SHA256 derivation.",
                        fontSize = 13.sp,
                        color = textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ==========================================
    // ADD EMAIL DIALOG
    // ==========================================
    if (showEmailConnectDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) showEmailConnectDialog = false
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = brandOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Add Recovery Email", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter a secure email address to receive recovery instructions if you ever forget your password:",
                        fontSize = 14.sp,
                        color = textSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            dialogError = null
                        },
                        placeholder = { Text("e.g. yourname@domain.com", color = textSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (emailInput.isNotBlank()) {
                                    isProcessing = true
                                    viewModel.connectRecoveryEmail(emailInput.trim()) { res ->
                                        isProcessing = false
                                        if (res.isSuccess) {
                                            showEmailConnectDialog = false
                                            Toast.makeText(context, "Recovery email added ✓", Toast.LENGTH_SHORT).show()
                                        } else {
                                            dialogError = res.exceptionOrNull()?.message ?: "Failed to add recovery email"
                                        }
                                    }
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedBorderColor = brandOrange,
                            unfocusedBorderColor = inputBorder,
                            cursorColor = brandOrange,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_email_input")
                    )

                    if (dialogError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = dialogError ?: "",
                            fontSize = 13.sp,
                            color = com.example.ui.theme.LenoTextError,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = emailInput.trim()
                        if (clean.isBlank()) {
                            dialogError = "Please enter an email address."
                            return@Button
                        }
                        isProcessing = true
                        dialogError = null
                        viewModel.connectRecoveryEmail(clean) { res ->
                            isProcessing = false
                            if (res.isSuccess) {
                                showEmailConnectDialog = false
                                Toast.makeText(context, "Recovery email added ✓", Toast.LENGTH_SHORT).show()
                            } else {
                                dialogError = res.exceptionOrNull()?.message ?: "Failed to add recovery email"
                            }
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_connect_email_button")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Add Email", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEmailConnectDialog = false },
                    enabled = !isProcessing
                ) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    // ==========================================
    // CANNOT REMOVE ONLY RECOVERY METHOD DIALOG
    // ==========================================
    if (showCannotRemoveDialog != null) {
        val methodName = showCannotRemoveDialog
        val otherMethodName = if (methodName == "Google") "an email address" else "Google"

        AlertDialog(
            onDismissRequest = { showCannotRemoveDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = brandOrange,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Cannot Remove $methodName",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "You must have at least one active recovery method to keep your Leno account protected. Please add $otherMethodName first before disconnecting $methodName.",
                    fontSize = 14.sp,
                    color = textSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showCannotRemoveDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("cannot_remove_ok_button")
                ) {
                    Text("Understood", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ==========================================
    // CONFIRM DISCONNECT DIALOG
    // ==========================================
    if (showConfirmDisconnectDialog != null) {
        val methodType = showConfirmDisconnectDialog!!
        val isGoogle = methodType == "GOOGLE"
        val label = if (isGoogle) "Google Account" else "Recovery Email"

        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) showConfirmDisconnectDialog = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Disconnect $label?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            },
            text = {
                Text(
                    text = "Are you sure you want to disconnect this recovery method from your Leno account? Your other recovery method will remain active.",
                    fontSize = 14.sp,
                    color = textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isProcessing = true
                        viewModel.removeRecoveryMethod(methodType) { res ->
                            isProcessing = false
                            showConfirmDisconnectDialog = null
                            if (res.isSuccess) {
                                Toast.makeText(context, "$label disconnected", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed to remove", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.LenoButtonDestructiveBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_disconnect_action_button")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Disconnect", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDisconnectDialog = null },
                    enabled = !isProcessing
                ) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            hasExistingPassword = hasExistingPassword,
            onDismiss = { showChangePasswordDialog = false },
            onSubmit = { currentPass, newPass, callback ->
                viewModel.changePasswordVerified(currentPass, newPass) { res ->
                    if (res.isSuccess) {
                        Toast.makeText(context, "Password updated ✓", Toast.LENGTH_SHORT).show()
                    }
                    callback(res)
                }
            }
        )
    }

    if (showSetupPinDialog) {
        SetupSecurityPinDialog(
            onDismiss = { showSetupPinDialog = false },
            onSubmit = { pin, callback ->
                viewModel.setSecurityPin(pin) { res ->
                    if (res.isSuccess) {
                        Toast.makeText(context, "App Security PIN enabled ✓", Toast.LENGTH_SHORT).show()
                    }
                    callback(res)
                }
            }
        )
    }

    if (showDisablePinDialog) {
        DisableSecurityPinDialog(
            onDismiss = { showDisablePinDialog = false },
            onSubmit = { currentPin, callback ->
                viewModel.disableSecurityPin(currentPin) { res ->
                    if (res.isSuccess) {
                        Toast.makeText(context, "App Security PIN disabled", Toast.LENGTH_SHORT).show()
                    }
                    callback(res)
                }
            }
        )
    }

    if (showRegenerateCodeDialog) {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) showRegenerateCodeDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Regenerate Recovery Code?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            },
            text = {
                Text(
                    text = "Generating a new Backup Recovery Code immediately invalidates your previous code. Make sure to save the new code securely.",
                    fontSize = 14.sp,
                    color = textSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isProcessing = true
                        viewModel.regenerateRecoveryCode { res ->
                            isProcessing = false
                            showRegenerateCodeDialog = false
                            if (res.isSuccess) {
                                revealRecoveryCode = true
                                Toast.makeText(context, "New recovery code generated ✓", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed to regenerate", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_regenerate_code_button")
                ) {
                    Text("Regenerate", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRegenerateCodeDialog = false },
                    enabled = !isProcessing
                ) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }
}
