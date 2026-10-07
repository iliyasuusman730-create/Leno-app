package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.viewmodel.LenoViewModel

enum class OnboardingStep {
    STEP_1_NAME,
    STEP_2_USERNAME,
    STEP_3_PASSWORD,
    STEP_4_LINO_ID,
    LOGIN,
    FORGOT_PASSWORD
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen(
    viewModel: LenoViewModel,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var currentStep by remember { mutableStateOf(OnboardingStep.STEP_1_NAME) }

    // Step 1: Full Name
    var fullNameInput by remember { mutableStateOf("") }

    // Step 2: Username
    var usernameInput by remember { mutableStateOf("") }

    // Step 3: Password
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Step 4: Generated Leno ID
    var generatedLinoId by remember { mutableStateOf("") }
    var isCopied by remember { mutableStateOf(false) }

    // Login screen inputs
    var loginIdentifierInput by remember { mutableStateOf("") }
    var loginPasswordInput by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }


    // Account recovery inputs
    var recoverIdentifierInput by remember { mutableStateOf("") }
    var recoverHintInput by remember { mutableStateOf("") }
    var recoverNewPasswordInput by remember { mutableStateOf("") }
    var recoverPasswordVisible by remember { mutableStateOf(false) }

    // Google recovery / login dialog
    var showGoogleLoginDialog by remember { mutableStateOf(false) }
    var googleLoginEmailInput by remember { mutableStateOf("") }

    // Common states
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Automatic safety watchdog to guarantee loading spinner never hangs indefinitely
    LaunchedEffect(isLoading) {
        if (isLoading) {
            kotlinx.coroutines.delay(12000)
            if (isLoading) {
                isLoading = false
                if (errorMessage == null) {
                    errorMessage = "Connection is taking longer than expected. Please check your connection and tap again."
                }
            }
        }
    }

    val brandOrange = MaterialTheme.colorScheme.primary
    val brandBg = MaterialTheme.colorScheme.background
    val inputBg = MaterialTheme.colorScheme.surfaceVariant
    val inputBorder = MaterialTheme.colorScheme.outlineVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==========================================
            // TOP BAR: LINO LOGO & NAVIGATION
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Back button if past step 1 or in login
                if (currentStep == OnboardingStep.STEP_2_USERNAME ||
                    currentStep == OnboardingStep.STEP_3_PASSWORD ||
                    currentStep == OnboardingStep.LOGIN ||
                    currentStep == OnboardingStep.FORGOT_PASSWORD
                ) {
                    IconButton(
                        onClick = {
                            errorMessage = null
                            currentStep = when (currentStep) {
                                OnboardingStep.STEP_2_USERNAME -> OnboardingStep.STEP_1_NAME
                                OnboardingStep.STEP_3_PASSWORD -> OnboardingStep.STEP_2_USERNAME
                                OnboardingStep.LOGIN -> OnboardingStep.STEP_1_NAME
                                OnboardingStep.FORGOT_PASSWORD -> OnboardingStep.LOGIN
                                else -> OnboardingStep.STEP_1_NAME
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Leno Logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_official_leno_avatar),
                        contentDescription = "Leno Logo",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "LENO",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimary,
                        letterSpacing = 3.sp
                    )
                }
            }

            // ==========================================
            // MIDDLE: ONE QUESTION & ONE INPUT
            // ==========================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "step_content"
                ) { step ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        when (step) {
                            // -------------------------------------------------------------
                            // STEP 1: FULL NAME
                            // -------------------------------------------------------------
                            OnboardingStep.STEP_1_NAME -> {
                                Text(
                                    text = "What's your full name?",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 34.sp
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                OutlinedTextField(
                                    value = fullNameInput,
                                    onValueChange = {
                                        fullNameInput = it
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            "Enter your name",
                                            fontSize = 18.sp,
                                            color = textSecondary
                                        )
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = {
                                            if (fullNameInput.trim().isNotBlank()) {
                                                errorMessage = null
                                                currentStep = OnboardingStep.STEP_2_USERNAME
                                            } else {
                                                errorMessage = "Please enter your full name."
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = inputBg,
                                        unfocusedContainerColor = inputBg,
                                        focusedBorderColor = brandOrange,
                                        unfocusedBorderColor = inputBorder,
                                        cursorColor = brandOrange
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .testTag("step1_fullname_input")
                                )

                                if (errorMessage != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = errorMessage!!,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // -------------------------------------------------------------
                            // STEP 2: USERNAME
                            // -------------------------------------------------------------
                            OnboardingStep.STEP_2_USERNAME -> {
                                Text(
                                    text = "Choose your username",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 34.sp
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                OutlinedTextField(
                                    value = usernameInput,
                                    onValueChange = {
                                        usernameInput = it.removePrefix("@").lowercase().trim()
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            "username",
                                            fontSize = 18.sp,
                                            color = textSecondary
                                        )
                                    },
                                    leadingIcon = {
                                        Text(
                                            text = "@",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = brandOrange,
                                            modifier = Modifier.padding(start = 16.dp, end = 4.dp)
                                        )
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = {
                                            val cleanUser = usernameInput.trim()
                                            if (cleanUser.length < 3) {
                                                errorMessage = "Username must be at least 3 characters."
                                                return@KeyboardActions
                                            }
                                            isLoading = true
                                            errorMessage = null
                                            viewModel.checkUsernameAvailable(cleanUser) { res ->
                                                isLoading = false
                                                if (res.isSuccess) {
                                                    currentStep = OnboardingStep.STEP_3_PASSWORD
                                                } else {
                                                    errorMessage = res.exceptionOrNull()?.message ?: "Username is already taken."
                                                }
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = inputBg,
                                        unfocusedContainerColor = inputBg,
                                        focusedBorderColor = brandOrange,
                                        unfocusedBorderColor = inputBorder,
                                        cursorColor = brandOrange
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .testTag("step2_username_input")
                                )

                                if (errorMessage != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = errorMessage!!,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                    if (errorMessage!!.contains("already", ignoreCase = true) || errorMessage!!.contains("taken", ignoreCase = true) || errorMessage!!.contains("registered", ignoreCase = true)) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        TextButton(
                                            onClick = {
                                                loginIdentifierInput = usernameInput
                                                errorMessage = null
                                                currentStep = OnboardingStep.LOGIN
                                            }
                                        ) {
                                            Text(
                                                text = "Log In to @$usernameInput →",
                                                color = brandOrange,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // -------------------------------------------------------------
                            // STEP 3: PASSWORD
                            // -------------------------------------------------------------
                            OnboardingStep.STEP_3_PASSWORD -> {
                                Text(
                                    text = "Create your password",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 34.sp
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = {
                                        passwordInput = it
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            "Enter password",
                                            fontSize = 18.sp,
                                            color = textSecondary
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { passwordVisible = !passwordVisible },
                                            modifier = Modifier
                                                .padding(end = 6.dp)
                                                .testTag("step3_toggle_password")
                                        ) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                                tint = textSecondary
                                            )
                                        }
                                    },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    textStyle = TextStyle(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (passwordInput.length < 6) {
                                                errorMessage = "Password must be at least 6 characters."
                                                return@KeyboardActions
                                            }
                                            isLoading = true
                                            errorMessage = null
                                            viewModel.registerWithLino(
                                                fullName = fullNameInput.trim(),
                                                username = usernameInput.trim(),
                                                password = passwordInput.trim()
                                            ) { res ->
                                                isLoading = false
                                                if (res.isSuccess) {
                                                    val createdUser = res.getOrNull()
                                                    generatedLinoId = createdUser?.linoId ?: "LEN-${(10000000..99999999).random()}"
                                                    currentStep = OnboardingStep.STEP_4_LINO_ID
                                                } else {
                                                    errorMessage = res.exceptionOrNull()?.message ?: "Registration failed."
                                                }
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = inputBg,
                                        unfocusedContainerColor = inputBg,
                                        focusedBorderColor = brandOrange,
                                        unfocusedBorderColor = inputBorder,
                                        cursorColor = brandOrange
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .testTag("step3_password_input")
                                )

                                if (errorMessage != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = errorMessage!!,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                    if (errorMessage!!.contains("already", ignoreCase = true) || errorMessage!!.contains("log in", ignoreCase = true)) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        TextButton(
                                            onClick = {
                                                loginIdentifierInput = usernameInput
                                                loginPasswordInput = passwordInput
                                                errorMessage = null
                                                currentStep = OnboardingStep.LOGIN
                                            }
                                        ) {
                                            Text(
                                                text = "Log In with @$usernameInput →",
                                                color = brandOrange,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // -------------------------------------------------------------
                            // STEP 4: LENO ID READY
                            // -------------------------------------------------------------
                            OnboardingStep.STEP_4_LINO_ID -> {
                                Text(
                                    text = "Your Leno ID is ready 🎉",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 34.sp
                                )

                                Spacer(modifier = Modifier.height(28.dp))

                                // Large Generated Leno ID Card
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    color = inputBg,
                                    border = BorderStroke(1.dp, com.example.ui.theme.LenoBorderDefault)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp, horizontal = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = generatedLinoId.ifBlank { "LEN-728491" },
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = textPrimary,
                                            letterSpacing = 4.sp,
                                            modifier = Modifier.testTag("generated_lino_id_text")
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // [Copy Leno ID] Button
                                OutlinedButton(
                                    onClick = {
                                        val idToCopy = generatedLinoId.ifBlank { "LEN-728491" }
                                        clipboardManager.setText(AnnotatedString(idToCopy))
                                        isCopied = true
                                        Toast.makeText(context, "Leno ID copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isCopied) com.example.ui.theme.LenoSuccess else com.example.ui.theme.LenoPrimary
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCopied) com.example.ui.theme.LenoSuccess else com.example.ui.theme.LenoBorderDefault
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(50.dp)
                                        .testTag("copy_lino_id_button")
                                ) {
                                    Icon(
                                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copy Leno ID",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isCopied) "Copied ID ✓" else "Copy Leno ID",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "Share this ID with friends so they can find you on Leno.",
                                    fontSize = 15.sp,
                                    color = textSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            // -------------------------------------------------------------
                            // LOGIN (RETURNING USERS)
                            // -------------------------------------------------------------
                            OnboardingStep.LOGIN -> {
                                Text(
                                    text = "Welcome back",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                OutlinedTextField(
                                    value = loginIdentifierInput,
                                    onValueChange = {
                                        loginIdentifierInput = it
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            "Username, Leno ID, or Recovery Email",
                                            fontSize = 15.sp,
                                            color = textSecondary
                                        )
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = inputBg,
                                        unfocusedContainerColor = inputBg,
                                        focusedBorderColor = brandOrange,
                                        unfocusedBorderColor = inputBorder,
                                        cursorColor = brandOrange
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .testTag("login_identifier_input")
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = loginPasswordInput,
                                    onValueChange = {
                                        loginPasswordInput = it
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            "Password",
                                            fontSize = 17.sp,
                                            color = textSecondary
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { loginPasswordVisible = !loginPasswordVisible },
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (loginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (loginPasswordVisible) "Hide password" else "Show password",
                                                tint = textSecondary
                                            )
                                        }
                                    },
                                    visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    textStyle = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (loginIdentifierInput.isBlank() || loginPasswordInput.isBlank()) {
                                                errorMessage = "Please enter both Username/Leno ID and password."
                                                return@KeyboardActions
                                            }
                                            if (loginPasswordInput.length < 4) {
                                                errorMessage = "Password must be at least 4 characters."
                                                return@KeyboardActions
                                            }
                                            isLoading = true
                                            errorMessage = null
                                            viewModel.loginWithLino(
                                                identifier = loginIdentifierInput.trim(),
                                                password = loginPasswordInput.trim()
                                            ) { res ->
                                                isLoading = false
                                                if (res.isSuccess) {
                                                    onAuthSuccess()
                                                } else {
                                                    errorMessage = res.exceptionOrNull()?.message ?: "Login failed. Check your credentials."
                                                }
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = inputBg,
                                        unfocusedContainerColor = inputBg,
                                        focusedBorderColor = brandOrange,
                                        unfocusedBorderColor = inputBorder,
                                        cursorColor = brandOrange
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .testTag("login_password_input")
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "Forgot Password?",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = brandOrange,
                                        modifier = Modifier
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                errorMessage = null
                                                recoverIdentifierInput = loginIdentifierInput
                                                currentStep = OnboardingStep.FORGOT_PASSWORD
                                            }
                                            .testTag("forgot_password_button")
                                    )
                                }

                                if (errorMessage != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = errorMessage!!,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )

                                    val cleanId = loginIdentifierInput.trim().removePrefix("@")
                                    if (errorMessage!!.contains("Account not found", ignoreCase = true) && cleanId.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedButton(
                                            onClick = {
                                                if (loginPasswordInput.length < 4) {
                                                    errorMessage = "Password must be at least 4 characters."
                                                    return@OutlinedButton
                                                }
                                                isLoading = true
                                                errorMessage = null
                                                val name = cleanId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                                viewModel.registerWithLino(
                                                    fullName = name,
                                                    username = cleanId,
                                                    password = loginPasswordInput.trim()
                                                ) { res ->
                                                    isLoading = false
                                                    if (res.isSuccess) {
                                                        onAuthSuccess()
                                                    } else {
                                                        errorMessage = res.exceptionOrNull()?.message ?: "Failed to create account."
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, brandOrange),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = brandOrange),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("quick_create_account_button")
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = brandOrange,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Create @$cleanId account now",
                                                color = brandOrange,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // -------------------------------------------------------------
                            // FORGOT PASSWORD / ACCOUNT RECOVERY
                            // -------------------------------------------------------------
                            OnboardingStep.FORGOT_PASSWORD -> {
                                Text(
                                    text = "Recover Account",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textPrimary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Reset password for your existing account",
                                    fontSize = 14.sp,
                                    color = textSecondary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                OutlinedTextField(
                                    value = recoverIdentifierInput,
                                    onValueChange = {
                                        recoverIdentifierInput = it
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            "Username, Leno ID, or Recovery Email",
                                            fontSize = 15.sp,
                                            color = textSecondary
                                        )
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = inputBg,
                                        unfocusedContainerColor = inputBg,
                                        focusedBorderColor = brandOrange,
                                        unfocusedBorderColor = inputBorder,
                                        cursorColor = brandOrange
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .testTag("recover_identifier_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = recoverHintInput,
                                    onValueChange = {
                                        recoverHintInput = it
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            "Full name or Recovery Code (optional)",
                                            fontSize = 15.sp,
                                            color = textSecondary
                                        )
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = inputBg,
                                        unfocusedContainerColor = inputBg,
                                        focusedBorderColor = brandOrange,
                                        unfocusedBorderColor = inputBorder,
                                        cursorColor = brandOrange
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .testTag("recover_hint_input")
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = recoverNewPasswordInput,
                                    onValueChange = {
                                        recoverNewPasswordInput = it
                                        errorMessage = null
                                    },
                                    placeholder = {
                                        Text(
                                            "New Password (min. 4 chars)",
                                            fontSize = 16.sp,
                                            color = textSecondary
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { recoverPasswordVisible = !recoverPasswordVisible },
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (recoverPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (recoverPasswordVisible) "Hide password" else "Show password",
                                                tint = textSecondary
                                            )
                                        }
                                    },
                                    visualTransformation = if (recoverPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    textStyle = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = inputBg,
                                        unfocusedContainerColor = inputBg,
                                        focusedBorderColor = brandOrange,
                                        unfocusedBorderColor = inputBorder,
                                        cursorColor = brandOrange
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .testTag("recover_new_password_input")
                                )

                                if (errorMessage != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = errorMessage!!,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // BOTTOM: LARGE CLEAR CONTINUE BUTTON
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (currentStep) {
                    OnboardingStep.STEP_1_NAME -> {
                        Button(
                            onClick = {
                                if (fullNameInput.trim().isBlank()) {
                                    errorMessage = "Please enter your full name."
                                    return@Button
                                }
                                errorMessage = null
                                currentStep = OnboardingStep.STEP_2_USERNAME
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("step1_continue_button")
                        ) {
                            Text(
                                text = "Continue",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Already have an account?",
                                fontSize = 14.sp,
                                color = textSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Log In",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = brandOrange,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        errorMessage = null
                                        currentStep = OnboardingStep.LOGIN
                                    }
                                    .testTag("switch_to_login_button")
                            )
                        }
                    }

                    OnboardingStep.STEP_2_USERNAME -> {
                        Button(
                            onClick = {
                                val cleanUser = usernameInput.trim()
                                if (cleanUser.isBlank()) {
                                    errorMessage = "Please enter a username."
                                    return@Button
                                }
                                if (cleanUser.length < 3) {
                                    errorMessage = "Username must be at least 3 characters."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                viewModel.checkUsernameAvailable(cleanUser) { res ->
                                    isLoading = false
                                    if (res.isSuccess) {
                                        currentStep = OnboardingStep.STEP_3_PASSWORD
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Username is already taken."
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("step2_continue_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "Continue",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    OnboardingStep.STEP_3_PASSWORD -> {
                        Button(
                            onClick = {
                                if (passwordInput.length < 6) {
                                    errorMessage = "Password must be at least 6 characters."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                viewModel.registerWithLino(
                                    fullName = fullNameInput.trim(),
                                    username = usernameInput.trim(),
                                    password = passwordInput.trim()
                                ) { res ->
                                    isLoading = false
                                    if (res.isSuccess) {
                                        val createdUser = res.getOrNull()
                                        generatedLinoId = createdUser?.linoId ?: "LEN-${(10000000..99999999).random()}"
                                        currentStep = OnboardingStep.STEP_4_LINO_ID
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Failed to complete registration."
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("step3_continue_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "Continue",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    OnboardingStep.STEP_4_LINO_ID -> {
                        Button(
                            onClick = {
                                onAuthSuccess()
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("enter_lino_button")
                        ) {
                            Text(
                                text = "Enter Leno",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    OnboardingStep.LOGIN -> {
                        Button(
                            onClick = {
                                if (loginIdentifierInput.isBlank() || loginPasswordInput.isBlank()) {
                                    errorMessage = "Please enter both Username/Leno ID and password."
                                    return@Button
                                }
                                if (loginPasswordInput.length < 4) {
                                    errorMessage = "Password must be at least 4 characters."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                viewModel.loginWithLino(
                                    identifier = loginIdentifierInput.trim(),
                                    password = loginPasswordInput.trim()
                                ) { res ->
                                    isLoading = false
                                    if (res.isSuccess) {
                                        onAuthSuccess()
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Login failed. Check your credentials."
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("login_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "Log In",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = inputBorder)
                            Text(
                                text = "OR",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = inputBorder)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                errorMessage = null
                                showGoogleLoginDialog = true
                            },
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, inputBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("google_login_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_leno_sparkle),
                                contentDescription = "Google",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Don't have an account?",
                                fontSize = 14.sp,
                                color = textSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Create Leno Account",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = brandOrange,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        errorMessage = null
                                        val clean = loginIdentifierInput.trim().removePrefix("@")
                                        if (clean.isNotBlank()) {
                                            usernameInput = clean.lowercase()
                                            fullNameInput = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                        }
                                        if (loginPasswordInput.isNotBlank()) {
                                            passwordInput = loginPasswordInput.trim()
                                        }
                                        currentStep = OnboardingStep.STEP_1_NAME
                                    }
                                    .testTag("switch_to_register_button")
                            )
                        }
                    }

                    OnboardingStep.FORGOT_PASSWORD -> {
                        Button(
                            onClick = {
                                if (recoverIdentifierInput.isBlank()) {
                                    errorMessage = "Please enter your Username, Leno ID, or Recovery Email."
                                    return@Button
                                }
                                if (recoverNewPasswordInput.length < 4) {
                                    errorMessage = "New password must be at least 4 characters."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                viewModel.recoverAccount(
                                    identifier = recoverIdentifierInput.trim(),
                                    verificationHint = recoverHintInput.trim(),
                                    newPassword = recoverNewPasswordInput.trim()
                                ) { res ->
                                    isLoading = false
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Password updated successfully! Logging in...", Toast.LENGTH_SHORT).show()
                                        val recoveredUser = res.getOrNull()
                                        val loginId = recoveredUser?.linoId ?: recoverIdentifierInput.trim()
                                        viewModel.loginWithLino(
                                            identifier = loginId,
                                            password = recoverNewPasswordInput.trim()
                                        ) { loginRes ->
                                            if (loginRes.isSuccess) {
                                                onAuthSuccess()
                                            } else {
                                                loginIdentifierInput = loginId
                                                loginPasswordInput = recoverNewPasswordInput.trim()
                                                currentStep = OnboardingStep.LOGIN
                                            }
                                        }
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Recovery failed. Account not found."
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("recover_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "Reset Password & Log In",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = inputBorder)
                            Text(
                                text = "OR",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = inputBorder)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                errorMessage = null
                                showGoogleLoginDialog = true
                            },
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, inputBorder),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("google_recover_button")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_leno_sparkle),
                                contentDescription = "Google",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Recover with Google Account",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Remember your password?",
                                fontSize = 14.sp,
                                color = textSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Back to Log In",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = brandOrange,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        errorMessage = null
                                        currentStep = OnboardingStep.LOGIN
                                    }
                                    .testTag("recover_back_to_login_button")
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGoogleLoginDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleLoginDialog = false },
            title = {
                Text(
                    text = "Sign in with Google",
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Enter your Google account email to sign in or recover your existing Leno account:",
                        fontSize = 14.sp,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = googleLoginEmailInput,
                        onValueChange = { googleLoginEmailInput = it },
                        placeholder = { Text("your.google.account@gmail.com", color = textSecondary, fontSize = 14.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = textPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedBorderColor = brandOrange,
                            unfocusedBorderColor = inputBorder,
                            cursorColor = brandOrange
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_login_email_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = googleLoginEmailInput.trim()
                        if (email.isBlank() || !email.contains("@")) {
                            Toast.makeText(context, "Please enter a valid Google email address.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showGoogleLoginDialog = false
                        isLoading = true
                        errorMessage = null
                        viewModel.loginWithGoogle(email) { result ->
                            isLoading = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "Welcome back! Account recovered successfully.", Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "No Leno account found for this Google email."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                    modifier = Modifier.testTag("confirm_google_login_btn")
                ) {
                    Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGoogleLoginDialog = false }
                ) {
                    Text("Cancel", color = textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
