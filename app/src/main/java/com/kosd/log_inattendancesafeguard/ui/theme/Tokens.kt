package com.kosd.log_inattendancesafeguard.ui.theme

import androidx.compose.ui.unit.dp

// ── Design Tokens ─────────────────────────────────────────────────────────────
// Centralized spacing, elevation, and sizing constants.
// Use these instead of hardcoded dp values for consistency.

/** 8dp grid spacing system. */
object Spacing {
    val xs   = 4.dp    // tight gaps between related items
    val sm   = 8.dp    // small padding inside cards
    val md   = 12.dp   // medium padding, field spacing
    val lg   = 16.dp   // standard content padding
    val xl   = 24.dp   // section spacing
    val xxl  = 32.dp   // large section gaps
}

/** Standard elevation values. */
object Elevation {
    val none   = 0.dp   // flat surfaces, lists
    val low    = 1.dp   // subtle cards
    val medium = 2.dp   // standard cards
    val high   = 4.dp   // prominent cards (mode selector)
    val modal  = 8.dp   // dialogs, modals
}

/** Standard card corner radius — use MaterialTheme.shapes.medium instead. */
object Radius {
    val small  = 12.dp
    val medium = 16.dp
    val large  = 20.dp
}
