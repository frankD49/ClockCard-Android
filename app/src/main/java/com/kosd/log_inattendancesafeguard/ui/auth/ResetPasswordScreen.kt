package com.kosd.log_inattendancesafeguard.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosd.log_inattendancesafeguard.ui.theme.AuthViolet
import com.kosd.log_inattendancesafeguard.ui.theme.AuthVioletLight
import com.kosd.log_inattendancesafeguard.ui.theme.OnAuthViolet
import com.kosd.log_inattendancesafeguard.viewmodel.AuthViewModel

/**
 * Screen shown when the user taps "Forgot Password?" on the login screen.
 * Prompts for email, sends a reset link via Resend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AuthVioletLight, AuthViolet)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnAuthViolet)
                }
            }

            Spacer(Modifier.height(24.dp))

            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Reset",
                modifier = Modifier.size(64.dp),
                tint = OnAuthViolet
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Reset Password",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = OnAuthViolet
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Enter your email and we'll send you a link to reset your password.",
                fontSize = 14.sp,
                color = OnAuthViolet.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (email.isNotBlank()) {
                                    viewModel.requestPasswordReset(email)
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )

                    Button(
                        onClick = { viewModel.requestPasswordReset(email) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.small,
                        enabled = email.isNotBlank() && !viewModel.isLoading
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Send Reset Link", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (viewModel.passwordResetRequested) {
        AlertDialog(
            onDismissRequest = {
                viewModel.passwordResetRequested = false
                onBack()
            },
            title = { Text("Check Your Email") },
            text = { Text(viewModel.successMessage ?: "If an account exists for that email, a reset link has been sent.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.passwordResetRequested = false
                    viewModel.dismissSuccess()
                    onBack()
                }) { Text("OK") }
            }
        )
    }

    if (viewModel.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text = { Text(viewModel.errorMessage ?: "An error occurred") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) { Text("OK") }
            }
        )
    }
}

/**
 * Screen shown when the user clicks the password-reset deep link.
 * Prompts for a new password, then calls verify-password-reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    viewModel: AuthViewModel,
    onBackToLogin: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AuthVioletLight, AuthViolet)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Logo",
                modifier = Modifier.size(64.dp),
                tint = OnAuthViolet
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "New Password",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = OnAuthViolet
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Enter your new password below.",
                fontSize = 14.sp,
                color = OnAuthViolet.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(32.dp))

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                                                  else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (newPassword.isNotBlank() && confirmPassword.isNotBlank()) {
                                    viewModel.verifyPasswordReset(newPassword, confirmPassword)
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )

                    Button(
                        onClick = { viewModel.verifyPasswordReset(newPassword, confirmPassword) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.small,
                        enabled = newPassword.isNotBlank() && confirmPassword.isNotBlank() && !viewModel.isLoading
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Reset Password", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (viewModel.passwordResetComplete) {
        AlertDialog(
            onDismissRequest = {
                viewModel.passwordResetComplete = false
                onBackToLogin()
            },
            title = { Text("Password Reset") },
            text = { Text(viewModel.successMessage ?: "Password reset successfully! Please sign in.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.passwordResetComplete = false
                    viewModel.dismissSuccess()
                    onBackToLogin()
                }) { Text("Sign In") }
            }
        )
    }

    if (viewModel.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text = { Text(viewModel.errorMessage ?: "An error occurred") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) { Text("OK") }
            }
        )
    }
}
