package com.kosd.log_inattendancesafeguard.ui.theme

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism bottom-sheet container for auth screens.
 *
 * Renders a bottom-anchored sheet with:
 * - Semi-transparent frosted surface (real blur on API 31+, fallback overlay)
 * - Subtle border for the glass edge effect
 * - Drag handle indicator
 * - Rounded top corners (28dp)
 *
 * Usage:
 *   GlassAuthSheet {
 *     // form content
 *   }
 */
@Composable
fun GlassAuthSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassColor = if (isDark) {
        Color(0xFF1E293B).copy(alpha = 0.78f)
    } else {
        Color.White.copy(alpha = 0.82f)
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.5f)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Bottom
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(radiusX = 2.dp, radiusY = 2.dp)
                    } else Modifier
                    // Note: blur on the Surface itself doesn't blur content behind.
                    // The glass effect comes from the semi-transparent background
                    // combined with the gradient backdrop in the parent screen.
                )
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(glassColor)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

/**
 * Decorative floating blurred shapes for auth screen backgrounds.
 * Renders behind the glass sheet to create depth and visual interest.
 */
@Composable
fun FloatingShapes(
    modifier: Modifier = Modifier,
    primaryColor: Color,
    tertiaryColor: Color
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    Box(modifier = modifier.fillMaxSize()) {
        // Shape 1 — top left
        Box(
            modifier = Modifier
                .offset(x = (-40).dp, y = (60).dp)
                .size(160.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(primaryColor.copy(alpha = 0.12f))
                .blur(40.dp)
        )
        // Shape 2 — center right
        Box(
            modifier = Modifier
                .offset(x = 220.dp, y = 180.dp)
                .size(140.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(tertiaryColor.copy(alpha = 0.10f))
                .blur(35.dp)
        )
        // Shape 3 — bottom center
        Box(
            modifier = Modifier
                .offset(x = 80.dp, y = 420.dp)
                .size(120.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(primaryColor.copy(alpha = 0.08f))
                .blur(30.dp)
        )
    }
}

/** Helper: luminance of a Color (0 = black, 1 = white). */
private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
