package com.kosd.log_inattendancesafeguard

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.kosd.log_inattendancesafeguard.network.SupabaseClientProvider.client
import com.kosd.log_inattendancesafeguard.ui.navigation.ClockCardNavHost
import com.kosd.log_inattendancesafeguard.ui.theme.LogInAttendanceSafeguardTheme
import com.kosd.log_inattendancesafeguard.viewmodel.AuthViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle deep link callbacks (email confirmation, OAuth).
        // Two formats for clockcard://auth-callback:
        //   1. ?token=xxx → Resend confirmation (our custom flow) → call verify-signup
        //   2. ?code=xxx  → Supabase Auth callback (legacy/fallback) → SDK handles it
        handleAuthDeepLink(intent)

        setContent {
            LogInAttendanceSafeguardTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ClockCardNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Handle deep links when the app is already running (e.g. user
        // clicks the confirmation email link while the app is in background).
        handleAuthDeepLink(intent)
    }

    /**
     * Routes clockcard:// deep links:
     * - auth-callback?token=xxx → Resend signup confirmation (verify-signup Edge Function)
     * - auth-callback?code=xxx  → Legacy Supabase Auth callback (SDK handles session import)
     * - password-reset?token=xxx → Resend password reset (verify-password-reset Edge Function)
     */
    private fun handleAuthDeepLink(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        val safeIntent = intent ?: return

        if (data.scheme == "clockcard" && data.host == "auth-callback") {
            val token = data.getQueryParameter("token")
            if (!token.isNullOrBlank()) {
                // Resend confirmation token — verify via Edge Function.
                // Use the same Factory that the Compose layer uses.
                val authViewModel = ViewModelProvider(
                    this,
                    AuthViewModel.Factory()
                )[AuthViewModel::class.java]
                authViewModel.handleSignupConfirmationToken(token)
                return
            }
            // Fall back to Supabase SDK for legacy ?code= links
            client.handleDeeplinks(safeIntent)
            return
        }

        if (data.scheme == "clockcard" && data.host == "password-reset") {
            val token = data.getQueryParameter("token")
            if (!token.isNullOrBlank()) {
                val authViewModel = ViewModelProvider(
                    this,
                    AuthViewModel.Factory()
                )[AuthViewModel::class.java]
                authViewModel.handlePasswordResetToken(token)
                return
            }
        }

        // Non-auth deep links (e.g. clockcard://e/...) — let SDK handle
        client.handleDeeplinks(safeIntent)
    }
}