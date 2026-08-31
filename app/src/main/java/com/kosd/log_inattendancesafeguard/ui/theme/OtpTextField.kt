package com.kosd.log_inattendancesafeguard.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * A 6-digit OTP input with individual boxes, auto-advance, and shake on error.
 *
 * Usage:
 *   var otpValue by remember { mutableStateOf("") }
 *   var shakeTrigger by remember { mutableStateOf(0) }
 *
 *   OtpTextField(
 *       otpText = otpValue,
 *       onOtpChange = { otpValue = it },
 *       shakeTrigger = shakeTrigger,
 *       onComplete = { /* all 6 digits entered */ }
 *   )
 *
 *   // To trigger shake: shakeTrigger++
 */
@Composable
fun OtpTextField(
    otpText: String,
    onOtpChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    shakeTrigger: Int = 0,
    isError: Boolean = false,
    onComplete: (() -> Unit)? = null
) {
    val digitCount = 6
    val focusRequester = remember { FocusRequester() }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Shake animation when shakeTrigger changes
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            scope.launch {
                shakeOffset.animateTo(8f, tween(50))
                shakeOffset.animateTo(-8f, tween(50))
                shakeOffset.animateTo(6f, tween(50))
                shakeOffset.animateTo(-6f, tween(50))
                shakeOffset.animateTo(0f, tween(50))
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val borderColor = if (isError) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.outline
    val filledColor = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                      else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset(x = shakeOffset.value.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        // Hidden text field that captures input
        BasicTextField(
            value = otpText,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }.take(digitCount)
                onOtpChange(filtered)
                if (filtered.length == digitCount) {
                    onComplete?.invoke()
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .size(0.dp)
                .focusRequester(focusRequester)
        )

        // Visible digit boxes
        repeat(digitCount) { index ->
            val digit = otpText.getOrNull(index)?.toString() ?: ""
            val isFilled = digit.isNotEmpty()
            val isCurrent = index == otpText.length

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isFilled) filledColor else MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (isCurrent && !isError) 2.dp else 1.dp,
                        color = if (isCurrent && !isError) MaterialTheme.colorScheme.primary else borderColor,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isFilled) {
                    Text(
                        text = digit,
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}
