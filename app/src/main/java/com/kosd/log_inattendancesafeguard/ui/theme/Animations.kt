package com.kosd.log_inattendancesafeguard.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Applies a press-scale animation: scales down to [scaleDown] while pressed,
 * springs back to 1f on release.
 *
 * Usage:
 *   Button(onClick = { ... }, modifier = Modifier.pressScale()) { ... }
 *   Card(onClick = { ... }, modifier = Modifier.pressScale()) { ... }
 */
@Composable
fun Modifier.pressScale(
    scaleDown: Float = 0.96f,
    interactionSource: MutableInteractionSource? = null
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "press_scale"
    )
    return this.scale(scale)
}

/**
 * Staggered fade-in + slide-up animation for list items.
 * Each item appears with a delay based on its [index].
 *
 * Usage:
 *   itemsIndexed(events) { index, event ->
 *       EventCard(...)
 *           .animateStaggeredItem(index)
 *   }
 */
@Composable
fun Modifier.animateStaggeredItem(
    index: Int,
    itemDelayMs: Int = 50,
    animationDurationMs: Int = 300
): Modifier {
    val alpha = animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = animationDurationMs,
            delayMillis = index * itemDelayMs
        ),
        label = "stagger_alpha"
    )
    val offsetY = animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(
            durationMillis = animationDurationMs,
            delayMillis = index * itemDelayMs
        ),
        label = "stagger_offset"
    )
    return this
        .graphicsLayer {
            this.alpha = alpha.value
            this.translationY = (1f - alpha.value) * 24f
        }
}
