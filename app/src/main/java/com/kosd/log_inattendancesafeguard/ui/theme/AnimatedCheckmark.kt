package com.kosd.log_inattendancesafeguard.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An animated checkmark that draws itself in a circle.
 * Use inside a success overlay.
 */
@Composable
fun AnimatedCheckmark(
    color: Color = MaterialTheme.colorScheme.primary,
    size: Int = 100,
    onComplete: (() -> Unit)? = null
) {
    val circleProgress = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Scale in
        scale.animateTo(1f, tween(300))
        // Draw circle
        circleProgress.animateTo(1f, tween(400))
        // Draw checkmark
        checkProgress.animateTo(1f, tween(300))
        onComplete?.invoke()
    }

    Box(
        modifier = Modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size.dp)
                .scale(scale.value)
        ) {
            val strokeWidth = size.dp.toPx() * 0.06f
            val radius = this.size.minDimension / 2 - strokeWidth

            // Circle
            drawCircle(
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                radius = radius,
                center = center,
                alpha = circleProgress.value
            )

            // Filled circle background (fades in after circle is drawn)
            if (circleProgress.value > 0.5f) {
                drawCircle(
                    color = color.copy(alpha = (circleProgress.value - 0.5f) * 0.15f),
                    radius = radius
                )
            }

            // Checkmark path
            if (checkProgress.value > 0f) {
                val checkPath = androidx.compose.ui.graphics.Path()
                val w = this.size.width
                val h = this.size.height
                checkPath.moveTo(w * 0.28f, h * 0.52f)
                checkPath.lineTo(w * 0.42f, h * 0.66f)
                checkPath.lineTo(w * 0.72f, h * 0.36f)

                drawPath(
                    path = checkPath,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * Full-screen animated success overlay for kiosk check-in.
 * Shows animated checkmark, "You're checked in!", name, and attendee number.
 */
@Composable
fun KioskSuccessOverlay(
    fullName: String,
    attendeeNumber: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedCheckmark(
            color = MaterialTheme.colorScheme.primary,
            size = 100
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "You're checked in!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            fullName,
            fontSize = 22.sp
        )
        Spacer(Modifier.height(8.dp))
        AnimatedNumber(
            targetValue = attendeeNumber,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Attendee",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
