package com.kosd.log_inattendancesafeguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kosd.log_inattendancesafeguard.models.AttendanceStatus

// === Brand (Pale Light Violet) ===
val Blue40        = Color(0xFF9F8AD0)
val Blue80        = Color(0xFFD4C5F9)

val BlueGrey40    = Color(0xFF475569)
val BlueGrey80    = Color(0xFF94A3B8)

val Teal40        = Color(0xFF0D9488)
val Teal80        = Color(0xFF2DD4BF)

// === Authentication ===
val AuthVioletLight = Color(0xFFF5F0FF)
val AuthViolet      = Color(0xFFE4D8FF)
val OnAuthViolet    = Color(0xFF4C1D95)

// === Surfaces ===
val BackgroundLight       = Color(0xFFF8FAFC)
val BackgroundDark        = Color(0xFF0F172A)
val SurfaceLight          = Color(0xFFFFFFFF)
val SurfaceDark           = Color(0xFF1E293B)
val SurfaceVariantLight   = Color(0xFFF1F5F9)
val SurfaceVariantDark    = Color(0xFF334155)
val OnSurfaceDark         = Color(0xFF1A1A2E)

// === Outline ===
val OutlineLight          = Color(0xFFCBD5E1)
val OutlineDark           = Color(0xFF475569)
val OutlineVariantLight   = Color(0xFFE2E8F0)
val OutlineVariantDark    = Color(0xFF334155)

// === Containers (Light) ===
val PrimaryContainerLight       = Color(0xFFEDE9F6)
val OnPrimaryContainerLight     = Color(0xFF4C1D95)
val SecondaryContainerLight     = Color(0xFFE2E8F0)
val OnSecondaryContainerLight   = Color(0xFF334155)
val TertiaryContainerLight      = Color(0xFFCCFBF1)
val OnTertiaryContainerLight    = Color(0xFF0F766E)

// === Containers (Dark) ===
val PrimaryContainerDark        = Color(0xFF2D1B4E)
val OnPrimaryContainerDark      = Color(0xFFD4C5F9)
val SecondaryContainerDark      = Color(0xFF334155)
val OnSecondaryContainerDark    = Color(0xFFE2E8F0)
val TertiaryContainerDark       = Color(0xFF115E59)
val OnTertiaryContainerDark     = Color(0xFF99F6E4)

// === Semantic Status Content (Light) ===
val StatusPresent = Color(0xFF166534)
val StatusAbsent  = Color(0xFF991B1B)
val StatusLate    = Color(0xFF9A3412)
val StatusEarly   = Color(0xFF92400E)
val StatusRemote  = Color(0xFF6D28D9)
val StatusLeave   = Color(0xFF6B21A8)

// === Semantic Status Content (Dark) ===
val StatusPresentDark = Color(0xFF86EFAC)
val StatusAbsentDark  = Color(0xFFFCA5A5)
val StatusLateDark    = Color(0xFFFDBA74)
val StatusEarlyDark   = Color(0xFFFCD34D)
val StatusRemoteDark  = Color(0xFFC4B5FD)
val StatusLeaveDark   = Color(0xFFD8B4FE)

// === Semantic Status Containers (Light) ===
val StatusPresentContainer = Color(0xFFDCFCE7)
val StatusAbsentContainer  = Color(0xFFFEE2E2)
val StatusLateContainer    = Color(0xFFFFEDD5)
val StatusEarlyContainer   = Color(0xFFFEF3C7)
val StatusRemoteContainer  = Color(0xFFEDE9F6)
val StatusLeaveContainer   = Color(0xFFF3E8FF)

// === Semantic Status Containers (Dark) ===
val StatusPresentContainerDark = Color(0xFF14532D)
val StatusAbsentContainerDark  = Color(0xFF7F1D1D)
val StatusLateContainerDark    = Color(0xFF7C2D12)
val StatusEarlyContainerDark   = Color(0xFF78350F)
val StatusRemoteContainerDark  = Color(0xFF2D1B4E)
val StatusLeaveContainerDark   = Color(0xFF581C87)

@Composable
fun statusContainerColor(status: AttendanceStatus): Color {
    return when (status) {
        AttendanceStatus.PRESENT         -> if (isSystemInDarkTheme()) StatusPresentContainerDark else StatusPresentContainer
        AttendanceStatus.ABSENT          -> if (isSystemInDarkTheme()) StatusAbsentContainerDark  else StatusAbsentContainer
        AttendanceStatus.LATE            -> if (isSystemInDarkTheme()) StatusLateContainerDark    else StatusLateContainer
        AttendanceStatus.EARLY_DEPARTURE -> if (isSystemInDarkTheme()) StatusEarlyContainerDark   else StatusEarlyContainer
        AttendanceStatus.REMOTE          -> if (isSystemInDarkTheme()) StatusRemoteContainerDark  else StatusRemoteContainer
        AttendanceStatus.ON_LEAVE        -> if (isSystemInDarkTheme()) StatusLeaveContainerDark   else StatusLeaveContainer
        AttendanceStatus.HALF_DAY        -> if (isSystemInDarkTheme()) StatusLateContainerDark    else StatusLateContainer
    }
}

@Composable
fun statusContentColor(status: AttendanceStatus): Color {
    return when (status) {
        AttendanceStatus.PRESENT         -> if (isSystemInDarkTheme()) StatusPresentDark else StatusPresent
        AttendanceStatus.ABSENT          -> if (isSystemInDarkTheme()) StatusAbsentDark  else StatusAbsent
        AttendanceStatus.LATE            -> if (isSystemInDarkTheme()) StatusLateDark    else StatusLate
        AttendanceStatus.EARLY_DEPARTURE -> if (isSystemInDarkTheme()) StatusEarlyDark   else StatusEarly
        AttendanceStatus.REMOTE          -> if (isSystemInDarkTheme()) StatusRemoteDark  else StatusRemote
        AttendanceStatus.ON_LEAVE        -> if (isSystemInDarkTheme()) StatusLeaveDark   else StatusLeave
        AttendanceStatus.HALF_DAY        -> if (isSystemInDarkTheme()) StatusLateDark    else StatusLate
    }
}
