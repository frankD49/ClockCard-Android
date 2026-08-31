package com.kosd.log_inattendancesafeguard.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Standardized shape system — all cards use 16dp, small components 12dp,
// buttons/inputs 12dp, modals/sheets 24dp.
val Shapes = Shapes(
    extraSmall  = RoundedCornerShape(8.dp),   // chips, badges
    small       = RoundedCornerShape(12.dp),  // text fields, buttons
    medium      = RoundedCornerShape(16.dp),  // cards
    large       = RoundedCornerShape(20.dp),  // large cards, mode selector
    extraLarge  = RoundedCornerShape(28.dp)   // bottom sheets, dialogs
)
