package com.kosd.log_inattendancesafeguard.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A shimmering placeholder block used in skeleton loading screens.
 * Fades in and out to simulate content loading.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 8
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .alpha(alpha)
    )
}

// ── Skeleton Layouts ──────────────────────────────────────────────────────────

/** Skeleton for an event card — title line, date line, status dot. */
@Composable
fun EventCardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(modifier = Modifier.size(10.dp, 10.dp), cornerRadius = 5)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp))
            }
        }
    }
}

/** Skeleton for a list of event cards. */
@Composable
fun EventListSkeleton(itemCount: Int = 4) {
    Column {
        repeat(itemCount) {
            EventCardSkeleton()
        }
    }
}

/** Skeleton for the event detail live count card. */
@Composable
fun LiveCountSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShimmerBox(modifier = Modifier.width(80.dp).height(16.dp))
        ShimmerBox(modifier = Modifier.width(120.dp).height(48.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(4.dp), cornerRadius = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ShimmerBox(modifier = Modifier.width(80.dp).height(12.dp))
            ShimmerBox(modifier = Modifier.width(80.dp).height(12.dp))
        }
    }
}

/** Skeleton for an attendee row. */
@Composable
fun AttendeeRowSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(modifier = Modifier.size(20.dp), cornerRadius = 10)
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
            ShimmerBox(modifier = Modifier.width(60.dp).height(10.dp))
        }
    }
}

/** Skeleton for a history record card. */
@Composable
fun HistoryRecordSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(modifier = Modifier.size(40.dp), cornerRadius = 12)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp))
            ShimmerBox(modifier = Modifier.width(80.dp).height(10.dp))
        }
        ShimmerBox(modifier = Modifier.width(60.dp).height(24.dp), cornerRadius = 12)
    }
}

/** Skeleton for a stat card. */
@Composable
fun StatCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ShimmerBox(modifier = Modifier.size(24.dp), cornerRadius = 12)
        ShimmerBox(modifier = Modifier.width(40.dp).height(18.dp))
        ShimmerBox(modifier = Modifier.width(50.dp).height(10.dp))
    }
}

/** Skeleton for the home screen status card. */
@Composable
fun HomeStatusSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShimmerBox(modifier = Modifier.weight(1f).height(80.dp), cornerRadius = 12)
            ShimmerBox(modifier = Modifier.weight(1f).height(80.dp), cornerRadius = 12)
        }
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(48.dp), cornerRadius = 12)
    }
}
