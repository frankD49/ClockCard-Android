package com.kosd.log_inattendancesafeguard.ui.theme

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Centralized haptic feedback helpers.
 *
 * Usage:
 *   val haptics = LocalHaptics.current
 *   haptics.success()   // check-in succeeded
 *   haptics.error()     // invalid code
 *   haptics.tap()       // button press
 */
class Haptics(private val feedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {

    /** Light tap for general button presses. */
    fun tap() {
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** Confirm a successful action (check-in, OTP verified, event created). */
    fun success() {
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** Signal an error (invalid code, check-in failed). */
    fun error() {
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** Selection changed (filter chips, mode selector). */
    fun selection() {
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return Haptics(feedback)
}
