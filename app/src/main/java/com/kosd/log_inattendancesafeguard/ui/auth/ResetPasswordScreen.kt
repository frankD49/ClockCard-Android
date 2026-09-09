package com.kosd.log_inattendancesafeguard.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kosd.log_inattendancesafeguard.ui.theme.AuthViolet
import com.kosd.log_inattendancesafeguard.ui.theme.AuthVioletLight
import com.kosd.log_inattendancesafeguard.ui.theme.FloatingShapes
import com.kosd.log_inattendancesafeguard.ui.theme.GlassAuthSheet
import com.kosd.log_inattendancesafeguard.ui.theme.OnAuthViolet
import com.kosd.log_inattendancesafeguard.ui.theme.rememberHaptics
import com.kosd.log_inattendancesafeguard.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val haptics = rememberHaptics()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AuthVioletLight, AuthViolet)))
    ) {
        FloatingShapes(primaryColor = OnAuthViolet, tertiaryColor = MaterialTheme.colorScheme.tertiary)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { haptics.tap(); onBack }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnAuthViolet)
                }
            }

            Spacer(Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Reset",
                modifier = Modifier.size(64.dp),
                tint = OnAuthViolet
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Reset Password",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = OnAuthViolet
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Enter your email and we'll send you a link to reset your password.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnAuthViolet.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            GlassAuthSheet {
                Box(
                    modifier = Modifier
                        .width(40.dp).height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (email.isNotBlank()) { haptics.tap(); viewModel.requestPasswordReset(email) }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { haptics.tap(); viewModel.requestPasswordReset(email) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.small,
                    enabled = email.isNotBlank() && !viewModel.isLoading
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Send Reset Link", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (viewModel.passwordResetRequested) {
        AlertDialog(
            onDismissRequest = { viewModel.passwordResetRequested = false; onBack() },
            title = { Text("Check Your Email") },
            text = { Text(viewModel.successMessage ?: "If an account exists for that email, a reset link has been sent.") },
            confirmButton = {
                TextButton(onClick = { viewModel.passwordResetRequested = false; viewModel.dismissSuccess(); onBack() }) { Text("OK") }
            }
        )
    }

    if (viewModel.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text = { Text(viewModel.errorMessage ?: "An error occurred") },
            confirmButton = { TextButton(onClick = { viewModel.dismissError() }) { Text("OK") } }
        )
    }
}

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
    val haptics = rememberHaptics()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AuthVioletLight, AuthViolet)))
    ) {
        FloatingShapes(primaryColor = OnAuthViolet, tertiaryColor = MaterialTheme.colorScheme.tertiary)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))

            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Logo",
                modifier = Modifier.size(64.dp),
                tint = OnAuthViolet
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "New Password",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = OnAuthViolet
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Enter your new password below.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnAuthViolet.copy(alpha = 0.8f)
            )

            Spacer(Modifier.weight(1f))

            GlassAuthSheet {
                Box(
                    modifier = Modifier
                        .width(40.dp).height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    trailingIcon = {
                        IconButton(onClick = { haptics.tap(); passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (newPassword.isNotBlank() && confirmPassword.isNotBlank()) {
                                haptics.tap(); viewModel.verifyPasswordReset(newPassword, confirmPassword)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { haptics.tap(); viewModel.verifyPasswordReset(newPassword, confirmPassword) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.small,
                    enabled = newPassword.isNotBlank() && confirmPassword.isNotBlank() && !viewModel.isLoading
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Reset Password", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (viewModel.passwordResetComplete) {
        AlertDialog(
            onDismissRequest = { viewModel.passwordResetComplete = false; onBackToLogin() },
            title = { Text("Password Reset") },
            text = { Text(viewModel.successMessage ?: "Password reset successfully! Please sign in.") },
            confirmButton = {
                TextButton(onClick = { viewModel.passwordResetComplete = false; viewModel.dismissSuccess(); onBackToLogin() }) { Text("Sign In") }
            }
        )
    }

    if (viewModel.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text = { Text(viewModel.errorMessage ?: "An error occurred") },
            confirmButton = { TextButton(onClick = { viewModel.dismissError() }) { Text("OK") } }
        )
    }
}
