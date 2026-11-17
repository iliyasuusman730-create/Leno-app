package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.UserEntity
import com.example.ui.theme.LenoDangerPrimary
import com.example.ui.theme.LenoPrimary
import com.example.ui.theme.LenoSuccess
import com.example.ui.theme.LenoWarning
import com.example.util.AuthSecurity
import com.example.util.PasswordStrength

@Composable
fun PasswordStrengthIndicator(
    password: String,
    modifier: Modifier = Modifier
) {
    if (password.isEmpty()) return
    val eval = remember(password) { AuthSecurity.evaluatePasswordStrength(password) }
    val targetColor = when (eval.strength) {
        PasswordStrength.WEAK -> LenoDangerPrimary
        PasswordStrength.FAIR -> LenoWarning
        PasswordStrength.STRONG -> LenoSuccess
    }
    val barColor by animateColorAsState(targetValue = targetColor, label = "strengthColor")
    val barProgress by animateFloatAsState(targetValue = eval.strength.score, label = "strengthProgress")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("password_strength_indicator")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Strength: ${eval.strength.label}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
            Text(
                text = eval.hint,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { barProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun ChangePasswordDialog(
    hasExistingPassword: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (currentPass: String, newPass: String, callback: (Result<Unit>) -> Unit) -> Unit
) {
    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val submitAction = {
        val cleanCur = currentPass.trim()
        val cleanNew = newPass.trim()
        val cleanConfirm = confirmPass.trim()
        when {
            hasExistingPassword && cleanCur.isBlank() -> {
                errorMsg = "Please enter your current password."
            }
            cleanNew.length < 6 -> {
                errorMsg = "New password must be at least 6 characters."
            }
            cleanNew != cleanConfirm -> {
                errorMsg = "New passwords do not match."
            }
            hasExistingPassword && cleanNew == cleanCur -> {
                errorMsg = "New password must be different from your current password."
            }
            else -> {
                isProcessing = true
                errorMsg = null
                onSubmit(cleanCur, cleanNew) { res ->
                    isProcessing = false
                    if (res.isSuccess) {
                        onDismiss()
                    } else {
                        errorMsg = res.exceptionOrNull()?.message ?: "Failed to update password."
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("change_password_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = LenoPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (hasExistingPassword) "Change Password" else "Set Account Password",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (hasExistingPassword) {
                        "Verify your current password to set a new password."
                    } else {
                        "Create a strong password for signing in with your Leno username or ID."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (hasExistingPassword) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = {
                            currentPass = it
                            errorMsg = null
                        },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showCurrent = !showCurrent }) {
                                Icon(
                                    imageVector = if (showCurrent) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showCurrent) "Hide password" else "Show password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("current_password_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPass,
                    onValueChange = {
                        newPass = it
                        errorMsg = null
                    },
                    label = { Text("New Password (min. 6 chars)") },
                    singleLine = true,
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                imageVector = if (showNew) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showNew) "Hide password" else "Show password"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_password_input")
                )

                if (newPass.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordStrengthIndicator(password = newPass)
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = {
                        confirmPass = it
                        errorMsg = null
                    },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitAction() }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_new_password_input")
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMsg ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LenoDangerPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = submitAction,
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_change_password_button")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Update Password", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SetupSecurityPinDialog(
    onDismiss: () -> Unit,
    onSubmit: (pin: String, callback: (Result<Unit>) -> Unit) -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val submitPin = {
        val cleanPin = pinInput.trim()
        val cleanConfirm = confirmPinInput.trim()
        when {
            cleanPin.length !in 4..6 || !cleanPin.all { it.isDigit() } -> {
                errorMsg = "PIN must be 4 to 6 numeric digits."
            }
            cleanPin != cleanConfirm -> {
                errorMsg = "PIN entries do not match."
            }
            else -> {
                onSubmit(cleanPin) { res ->
                    if (res.isSuccess) {
                        onDismiss()
                    } else {
                        errorMsg = res.exceptionOrNull()?.message ?: "Failed to save PIN."
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("setup_pin_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Pin,
                    contentDescription = null,
                    tint = LenoPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Set App Security PIN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Require a 4–6 digit PIN whenever you reopen Leno on this device.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() }.take(6)
                        pinInput = filtered
                        errorMsg = null
                    },
                    label = { Text("Enter 4–6 Digit PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_pin_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPinInput,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() }.take(6)
                        confirmPinInput = filtered
                        errorMsg = null
                    },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitPin() }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_pin_input")
                )
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMsg ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LenoDangerPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = submitPin,
                colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_pin_button")
            ) {
                Text("Enable PIN Lock", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DisableSecurityPinDialog(
    onDismiss: () -> Unit,
    onSubmit: (currentPin: String, callback: (Result<Unit>) -> Unit) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val confirmDisable = {
        val clean = currentPin.trim()
        if (clean.length !in 4..6) {
            errorMsg = "Enter your current 4–6 digit PIN."
        } else {
            onSubmit(clean) { res ->
                if (res.isSuccess) {
                    onDismiss()
                } else {
                    errorMsg = res.exceptionOrNull()?.message ?: "Incorrect PIN."
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("disable_pin_dialog"),
        title = {
            Text("Disable Security PIN?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter your current PIN to turn off app lock protection.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { raw ->
                        currentPin = raw.filter { it.isDigit() }.take(6)
                        errorMsg = null
                    },
                    label = { Text("Current PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirmDisable() }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("disable_pin_input")
                )
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMsg ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LenoDangerPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = confirmDisable,
                colors = ButtonDefaults.buttonColors(containerColor = LenoDangerPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_disable_pin_button")
            ) {
                Text("Disable PIN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AppSecurityLockOverlay(
    user: UserEntity,
    onVerifyPin: (String) -> Result<Unit>,
    onForgotPinLogout: () -> Unit
) {
    BackHandler(enabled = true) {
        // Consume back press while app is locked
    }

    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val tryUnlock = {
        val clean = pinInput.trim()
        if (clean.length !in 4..6) {
            errorMsg = "Enter your 4–6 digit PIN."
        } else {
            val res = onVerifyPin(clean)
            if (res.isFailure) {
                errorMsg = res.exceptionOrNull()?.message ?: "Incorrect PIN."
                pinInput = ""
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_lock_overlay"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(LenoPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Leno App Lock",
                        tint = LenoPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Leno is Locked",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (user.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = user.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(LenoPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (user.displayName.ifBlank { user.username }.firstOrNull() ?: 'L').uppercaseChar().toString(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LenoPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "@${user.username}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { raw ->
                        pinInput = raw.filter { it.isDigit() }.take(6)
                        errorMsg = null
                    },
                    label = { Text("Enter Security PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { tryUnlock() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LenoPrimary,
                        cursorColor = LenoPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lock_screen_pin_input")
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMsg ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LenoDangerPrimary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = tryUnlock,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LenoPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("lock_screen_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unlock Leno",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onForgotPinLogout,
                    modifier = Modifier.testTag("lock_screen_forgot_pin_button")
                ) {
                    Text(
                        text = "Forgot PIN? Sign out & recover",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LenoPrimary
                    )
                }
            }
        }
    }
}
