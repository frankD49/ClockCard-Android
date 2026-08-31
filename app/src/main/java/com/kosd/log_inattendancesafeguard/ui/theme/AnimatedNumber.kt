package com.kosd.log_inattendancesafeguard.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * A number that animates (slide + fade) when its value changes.
 *
 * Usage:
 *   AnimatedNumber(targetValue = liveCount, style = MaterialTheme.typography.displayMedium)
 */
@Composable
fun AnimatedNumber(
    targetValue: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.MaterialTheme.typography.displayMedium,
    fontWeight: FontWeight = FontWeight.Bold,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    textAlign: TextAlign? = null
) {
    AnimatedContent(
        targetState = targetValue,
        transitionSpec = {
            (slideInVertically(
                animationSpec = tween(300),
                initialOffsetY = { it / 3 }
            ) + fadeIn(tween(300)))
                .togetherWith(
                    slideOutVertically(
                        animationSpec = tween(300),
                        targetOffsetY = { -it / 3 }
                    ) + fadeOut(tween(200))
                )
        },
        label = "animated_number"
    ) { value ->
        Text(
            text = value.toString(),
            style = style,
            fontWeight = fontWeight,
            color = color,
            textAlign = textAlign,
            modifier = modifier
        )
    }
}
