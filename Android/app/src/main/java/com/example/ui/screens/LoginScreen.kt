package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.crypto.BiometricHelper
import com.example.ui.theme.*

@Composable
fun LoginScreen(
    isEncrypted: Boolean,
    onUnlock: (String, (Boolean, String?) -> Unit) -> Unit,
    onDirectOpen: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val canUseBiometric = remember(isEncrypted) {
        isEncrypted && BiometricHelper.canAuthenticate(context)
    }
    var hasAutoPrompted by rememberSaveable { mutableStateOf(false) }

    fun triggerBiometric() {
        if (activity != null) {
            errorMessage = null
            BiometricHelper.promptBiometric(
                activity = activity,
                onSuccess = { savedPassword ->
                    isSubmitting = true
                    onUnlock(savedPassword) { success, err ->
                        isSubmitting = false
                        if (!success) errorMessage = err
                    }
                },
                onError = { err ->
                    errorMessage = err
                }
            )
        }
    }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric && !hasAutoPrompted && activity != null) {
            hasAutoPrompted = true
            triggerBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (isEncrypted) PrimaryBlue.copy(alpha = 0.12f) else GreenIncome.copy(alpha = 0.12f),
                            RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Lock State",
                        tint = if (isEncrypted) PrimaryBlue else GreenIncome,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Personal Finance System",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NavySidebar,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isEncrypted) {
                        "This database is protected by AES-256 encryption.\nEnter your master password to unlock."
                    } else {
                        "Database encryption is not enabled.\nClick Open to continue."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = GrayTextSecondary,
                    textAlign = TextAlign.Center
                )

                if (isEncrypted) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Master Password") },
                        placeholder = { Text("Enter password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (password.isNotBlank() && !isSubmitting) {
                                    isSubmitting = true
                                    onUnlock(password) { success, err ->
                                        isSubmitting = false
                                        if (!success) errorMessage = err
                                    }
                                }
                            }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = RedExpense,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = {
                        if (isEncrypted) {
                            if (password.isBlank()) {
                                errorMessage = "Please enter your password."
                                return@Button
                            }
                            isSubmitting = true
                            onUnlock(password) { success, err ->
                                isSubmitting = false
                                if (!success) errorMessage = err
                            }
                        } else {
                            onDirectOpen()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("unlock_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEncrypted) PrimaryBlue else GreenIncome
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isEncrypted) "Unlock Database" else "Open Finance Manager",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }

                if (isEncrypted && canUseBiometric) {
                    OutlinedButton(
                        onClick = { triggerBiometric() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("biometric_unlock_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryBlue
                        ),
                        enabled = !isSubmitting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Fingerprint Unlock",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unlock with Fingerprint",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }

                Text(
                    text = "Your financial data stays local on your device.\nProtected with PBKDF2 (600,000 iters) & AES-256-GCM.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GrayTextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
