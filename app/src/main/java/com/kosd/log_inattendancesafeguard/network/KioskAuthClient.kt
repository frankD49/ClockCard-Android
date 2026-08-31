package com.kosd.log_inattendancesafeguard.network

import com.kosd.log_inattendancesafeguard.BuildConfig
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kiosk OTP verification via custom Edge Function + verify RPC.
 *
 * Bypasses Supabase Auth's built-in email rate limit (2/hour on hosted free tier)
 * by using the send-email Edge Function (type: "kiosk_otp") that sends emails via
 * Resend, and a verify_kiosk_otp RPC that validates the code against the
 * kiosk_otp_codes table.
 *
 * Flow:
 *  1. sendOtp(email) → send-email Edge Function generates 6-digit code, stores in DB, emails via Resend
 *  2. verifyOtp(email, code) → verify_kiosk_otp RPC returns verified email on success
 *  3. Caller passes the verified email to event_check_in() with user_id = null
 *     → the guest path kicks in, which deduplicates by email_hash via guest_profiles
 */
object KioskAuthClient {

    private val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
    }

    private val functionsUrl = "${BuildConfig.SUPABASE_URL}/functions/v1/send-email"

    /** Send a 6-digit OTP code to [email] via the send-email Edge Function (type: kiosk_otp). */
    suspend fun sendOtp(email: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(functionsUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            conn.doOutput = true

            val body = """{"type":"kiosk_otp","email":"${email.replace("\"", "\\\"")}"}"""
            conn.outputStream.use { it.write(body.toByteArray()) }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verify the 6-digit [code] sent to [email].
     * Returns the verified email on success, or null on failure.
     */
    suspend fun verifyOtp(email: String, code: String): String? = withContext(Dispatchers.IO) {
        try {
            val params = buildJsonObject {
                put("p_email", email)
                put("p_code", code)
            }
            val json = client.postgrest.rpc("verify_kiosk_otp", params)
                .decodeSingle<JsonObject>()
            val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                json["email"]?.jsonPrimitive?.contentOrNull ?: email
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** No-op — no session to clean up with custom OTP (unlike Supabase Auth). */
    suspend fun signOut() { /* no-op */ }
}
