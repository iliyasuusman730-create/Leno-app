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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.LenoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectAccountScreen(
    viewModel: LenoViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val brandOrange = com.example.ui.theme.LenoPrimary
    val brandBg = com.example.ui.theme.LenoMainBackground
    val cardBg = com.example.ui.theme.LenoSurface
    val inputBg = com.example.ui.theme.LenoInputBackground
    val inputBorder = com.example.ui.theme.LenoBorderDefault
    val textPrimary = com.example.ui.theme.LenoTextPrimary
    val textSecondary = com.example.ui.theme.LenoTextSecondary
    val successGreen = com.example.ui.theme.LenoSuccess

    val isGoogleConnected = currentUser?.googleEmail?.isNotBlank() == true
    val isEmailConnected = currentUser?.recoveryEmail?.isNotBlank() == true
    val isProtected = isGoogleConnected || isEmailConnected

    var showGoogleConnectDialog by remember { mutableStateOf(false) }
    var showEmailConnectDialog by remember { mutableStateOf(false) }
    var showCannotRemoveDialog by remember { mutableStateOf<String?>(null) }
    var showConfirmDisconnectDialog by remember { mutableStateOf<String?>(null) }

    var googleInput by remember { mutableStateOf("") }
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
            // Status Header Banner
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
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
                                    text = "Account Protected",
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
                                text = "Your Leno account has active recovery methods. You can regain access at any time.",
                                fontSize = 13.sp,
                                color = textSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_unprotected_banner"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.LenoBorderDefault)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(brandOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Security Alert",
                                tint = brandOrange,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Protect Your Account",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add a recovery method so you can regain access to your Leno account if you forget your password.",
                                fontSize = 13.sp,
                                color = textSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subheading
            Text(
                text = "Choose a recovery method",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = brandOrange,
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
                                dialogError = null
                                googleInput = ""
                                showGoogleConnectDialog = true
                            },
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
                            Text(
                                text = "Connect Google",
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

            Spacer(modifier = Modifier.height(24.dp))

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
                        text = "• If you forget your password, you can sign in directly with your connected Google account or reset your password using your recovery email.\n" +
                               "• Account recovery restores your EXISTING Leno account with your permanent Leno ID, username, contacts, and chat histories.\n" +
                               "• Passwords are never stored in plain text and are protected with salted cryptographic key derivation.",
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
    // CONNECT GOOGLE DIALOG
    // ==========================================
    if (showGoogleConnectDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) showGoogleConnectDialog = false
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.LenoInfo)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Connect Google Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter your Google account email to link it as a recovery and sign-in method for Leno:",
                        fontSize = 14.sp,
                        color = textSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = googleInput,
                        onValueChange = {
                            googleInput = it
                            dialogError = null
                        },
                        placeholder = { Text("e.g. user@gmail.com", color = textSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (googleInput.isNotBlank()) {
                                    isProcessing = true
                                    viewModel.connectGoogleAccount(googleInput.trim()) { res ->
                                        isProcessing = false
                                        if (res.isSuccess) {
                                            showGoogleConnectDialog = false
                                            Toast.makeText(context, "Google account connected ✓", Toast.LENGTH_SHORT).show()
                                        } else {
                                            dialogError = res.exceptionOrNull()?.message ?: "Failed to connect Google account"
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
                            .testTag("google_email_input")
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
                        val clean = googleInput.trim()
                        if (clean.isBlank()) {
                            dialogError = "Please enter your Google email address."
                            return@Button
                        }
                        isProcessing = true
                        dialogError = null
                        viewModel.connectGoogleAccount(clean) { res ->
                            isProcessing = false
                            if (res.isSuccess) {
                                showGoogleConnectDialog = false
                                Toast.makeText(context, "Google account connected ✓", Toast.LENGTH_SHORT).show()
                            } else {
                                dialogError = res.exceptionOrNull()?.message ?: "Failed to connect Google account"
                            }
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_connect_google_button")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Connect", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGoogleConnectDialog = false },
                    enabled = !isProcessing
                ) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
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
}
