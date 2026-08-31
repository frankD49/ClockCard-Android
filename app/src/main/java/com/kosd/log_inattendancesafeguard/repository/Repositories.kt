@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.kosd.log_inattendancesafeguard.repository

import com.kosd.log_inattendancesafeguard.models.*
import com.kosd.log_inattendancesafeguard.models.EventStaffMember
import com.kosd.log_inattendancesafeguard.network.SupabaseClientProvider.client
import com.kosd.log_inattendancesafeguard.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import com.kosd.log_inattendancesafeguard.models.Event
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private fun Throwable.toErrorMessage(): String {
    val raw = message ?: "Unknown error"
    return when {
        raw.contains("Unable to resolve host", ignoreCase = true) ||
        raw.contains("No address associated with hostname", ignoreCase = true) ||
        raw.contains("UnknownHostException", ignoreCase = true) ->
            "No internet connection. Please check your network and try again."

        raw.contains("timeout", ignoreCase = true) ||
        raw.contains("timed out", ignoreCase = true) ||
        raw.contains("SocketTimeoutException", ignoreCase = true) ->
            "Connection timed out. Please try again."

        raw.contains("SSL", ignoreCase = true) ||
        raw.contains("certificate", ignoreCase = true) ||
        raw.contains("CertPathValidatorException", ignoreCase = true) ->
            "Secure connection failed. Please check your network security settings."

        raw.contains("HTTP request to", ignoreCase = true) && raw.contains("failed with message", ignoreCase = true) -> {
            // Extract the actual message after "failed with message:"
            val idx = raw.indexOf("failed with message:", ignoreCase = true)
            if (idx >= 0) {
                val inner = raw.substring(idx + 20).trim().trim('"')
                when {
                    inner.contains("Unable to resolve host", ignoreCase = true) ||
                    inner.contains("No address associated with hostname", ignoreCase = true) ->
                        "No internet connection. Please check your network and try again."
                    else -> "Server error: $inner"
                }
            } else "Server request failed. Please try again."
        }

        else -> raw
    }
}

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>()
}

// ─── Auth Repository ─────────────────────────────────────────────────────────

class AuthRepository {

    suspend fun login(email: String, password: String): Result<User> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        Result.Success(getCurrentUserProfile())
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    /**
     * Owner signup via Resend (bypasses Supabase Auth's 2/hour email rate limit).
     * Calls the send-email Edge Function which:
     *   1. Creates the auth user with email_confirm: false (no Supabase email sent)
     *   2. Generates a confirmation token, stores in signup_confirmations
     *   3. Sends a confirmation link via Resend to the user's email
     *
     * After the user clicks the link, the app calls verifySignupConfirmation(token)
     * which confirms the email. The user then signs in with their password.
     */
    suspend fun signupWithResend(
        email: String, password: String,
        firstName: String, lastName: String,
        orgName: String? = null,
        inviteCode: String? = null
    ): Result<Unit> = runCatching {
        val url = URL("${BuildConfig.SUPABASE_URL}/functions/v1/send-email")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        conn.doOutput = true

        val context = buildJsonObject {
            put("password", password)
            put("firstName", firstName)
            put("lastName", lastName)
            if (!orgName.isNullOrBlank()) put("orgName", orgName)
            if (!inviteCode.isNullOrBlank()) put("inviteCode", inviteCode)
        }

        val body = buildJsonObject {
            put("type", "signup_confirmation")
            put("email", email)
            put("context", context)
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }

        val responseCode = conn.responseCode
        val responseBody = if (responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        conn.disconnect()

        val json = kotlinx.serialization.json.Json.parseToJsonElement(responseBody).jsonObject
        val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
        val message = json["message"]?.jsonPrimitive?.contentOrNull

        if (!success) {
            return@runCatching Result.Error(message ?: "Signup failed")
        }

        Result.Success(Unit)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    /**
     * Verifies a signup confirmation token by calling the verify-signup Edge Function.
     * Called when the app receives a deep link with a `token` query parameter.
     * Returns the confirmed email on success, null on failure.
     */
    suspend fun verifySignupConfirmation(token: String): String? = runCatching {
        val url = URL("${BuildConfig.SUPABASE_URL}/functions/v1/verify-signup")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        conn.doOutput = true

        val body = """{"token":"${token.replace("\"", "\\\"")}"}"""
        conn.outputStream.use { it.write(body.toByteArray()) }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            conn.errorStream?.bufferedReader()?.use { it.readText() }
            conn.disconnect()
            return@runCatching null
        }

        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val json = kotlinx.serialization.json.Json.parseToJsonElement(responseBody).jsonObject
        val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (success) {
            json["email"]?.jsonPrimitive?.contentOrNull
        } else {
            null
        }
    }.getOrNull()

    // ── Invite-code signup (bypasses email confirmation) ──────────────────────
    // Calls the invite-signup Edge Function which creates the user with
    // email_confirm: true, joins the org, and returns session tokens.
    suspend fun inviteSignup(
        email: String, password: String,
        firstName: String, lastName: String,
        inviteCode: String
    ): Result<InviteSignupResponse> = runCatching {
        val url = URL("${BuildConfig.SUPABASE_URL}/functions/v1/invite-signup")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        conn.doOutput = true

        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("firstName", firstName)
            put("lastName", lastName)
            put("inviteCode", inviteCode)
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }

        val responseCode = conn.responseCode
        val responseBody = if (responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        conn.disconnect()

        val json = kotlinx.serialization.json.Json.parseToJsonElement(responseBody).jsonObject
        val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
        val message = json["message"]?.jsonPrimitive?.contentOrNull

        if (!success) {
            return@runCatching Result.Error(message ?: "Invite signup failed")
        }

        val accessToken = json["accessToken"]?.jsonPrimitive?.contentOrNull
        val refreshToken = json["refreshToken"]?.jsonPrimitive?.contentOrNull
        val expiresIn = json["expiresIn"]?.jsonPrimitive?.longOrNull ?: 3600L
        val requiresSignIn = json["requiresSignIn"]?.jsonPrimitive?.booleanOrNull ?: false

        if (accessToken != null && refreshToken != null) {
            // Import the session into the Supabase client
            client.auth.importSession(
                UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn,
                    tokenType = "bearer",
                    user = null
                )
            )
        }

        Result.Success(InviteSignupResponse(
            success = true,
            requiresSignIn = requiresSignIn,
            accessToken = accessToken,
            refreshToken = refreshToken
        ))
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getCurrentUser(): Result<User> = runCatching {
        Result.Success(getCurrentUserProfile())
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        client.auth.updateUser { password = newPassword }
        Result.Success(Unit)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun logout(): Result<Unit> = runCatching {
        client.auth.signOut()
        Result.Success(Unit)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    /**
     * Returns the raw user metadata from the current authenticated session.
     * Used to retrieve pending_org_name / pending_invite_code after email
     * confirmation.
     */
    fun getAuthUserMetadata(): JsonObject? {
        return client.auth.currentUserOrNull()?.userMetadata
    }

    suspend fun getMyMemberships(): Result<List<OrgMember>> = runCatching {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val memberships = client.postgrest["org_members"]
            .select { filter { eq("user_id", uid); eq("is_active", true) } }
            .decodeList<OrgMember>()
        Result.Success(memberships)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    private suspend fun getCurrentUserProfile(): User {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val authEmail = client.auth.currentUserOrNull()?.email ?: ""
        return client.postgrest["profiles"]
            .select { filter { eq("id", uid) } }
            .decodeSingle<User>()
            .copy(email = authEmail)
    }
}

// ─── Organization Repository ─────────────────────────────────────────────────

class OrganizationRepository {

    suspend fun getOrganizations(): Result<List<Organization>> = runCatching {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val memberships = client.postgrest["org_members"]
            .select { filter { eq("user_id", uid); eq("is_active", true) } }
            .decodeList<OrgMember>()
        if (memberships.isEmpty()) return@runCatching Result.Success(emptyList())
        val orgIds = memberships.map { it.organizationId }
        val orgs = client.postgrest["organizations"]
            .select { filter { isIn("id", orgIds) } }
            .decodeList<Organization>()
        Result.Success(orgs)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun createOrganization(request: OrganizationCreateRequest): Result<Organization> = runCatching {
        // Use SECURITY DEFINER RPC to bypass RLS — the direct insert + trigger
        // approach fails because the RETURNING clause is subject to the SELECT
        // policy (user_is_org_member), which can't see the row until the trigger
        // commits the membership insert.
        val org = client.postgrest.rpc(
            function = "create_organization_with_owner",
            parameters = buildJsonObject {
                put("p_name", request.name)
                put("p_slug", request.slug)
                if (request.description != null) put("p_description", request.description)
                else put("p_description", kotlinx.serialization.json.JsonNull)
                put("p_timezone", request.timezone)
                put("p_max_members", request.maxMembers)
                put("p_population_tier", request.populationTier.value)
            }
        ).decodeSingle<Organization>()
        Result.Success(org)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun updateOrganization(id: String, request: OrganizationUpdateRequest): Result<Organization> = runCatching {
        val org = client.postgrest["organizations"]
            .update(request) { filter { eq("id", id) }; select() }
            .decodeSingle<Organization>()
        Result.Success(org)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getMyRoleInOrg(orgId: String): Result<OrgMember?> = runCatching {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val memberships = client.postgrest["org_members"]
            .select { filter { eq("user_id", uid); eq("organization_id", orgId); eq("is_active", true) } }
            .decodeList<OrgMember>()
        Result.Success(memberships.firstOrNull())
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getMyPermissions(orgId: String, memberId: String): Result<Set<Permission>> = runCatching {
        val rows = client.postgrest["organization_member_permissions"]
            .select { filter { eq("organization_id", orgId); eq("member_id", memberId) } }
            .decodeList<OrganizationMemberPermission>()
        Result.Success(rows.mapNotNull { Permission.fromValue(it.permission) }.toSet())
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getOrganizationPermissions(orgId: String): Result<Map<String, Set<Permission>>> = runCatching {
        val rows = client.postgrest["organization_member_permissions"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<OrganizationMemberPermission>()
        Result.Success(rows.groupBy { it.memberId }.mapValues { (_, values) -> values.mapNotNull { Permission.fromValue(it.permission) }.toSet() })
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getMembers(orgId: String): Result<List<Member>> = runCatching {
        val memberships = client.postgrest["org_members"]
            .select { filter { eq("organization_id", orgId); eq("is_active", true) } }
            .decodeList<OrgMember>()
        Result.Success(memberships)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getProfilesByIds(userIds: List<String>): Result<List<User>> = runCatching {
        if (userIds.isEmpty()) return@runCatching Result.Success(emptyList())
        val profiles = client.postgrest["profiles"]
            .select { filter { isIn("id", userIds.distinct()) } }
            .decodeList<User>()
        Result.Success(profiles)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun promoteAdmin(orgId: String, memberId: String, permissions: Set<Permission>): Result<Unit> = runCatching {
        client.postgrest.rpc("promote_organization_admin", buildJsonObject {
            put("p_organization_id", orgId); put("p_member_id", memberId)
            putJsonArray("p_permissions") { permissions.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.value)) } }
        })
        Result.Success(Unit)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun changeAdminPermissions(orgId: String, memberId: String, permissions: Set<Permission>): Result<Unit> = runCatching {
        client.postgrest.rpc("change_organization_admin_permissions", buildJsonObject {
            put("p_organization_id", orgId); put("p_member_id", memberId)
            putJsonArray("p_permissions") { permissions.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.value)) } }
        })
        Result.Success(Unit)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun demoteAdmin(orgId: String, memberId: String): Result<Unit> = runCatching {
        client.postgrest.rpc("demote_organization_admin", buildJsonObject {
            put("p_organization_id", orgId); put("p_member_id", memberId)
        })
        Result.Success(Unit)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun removeMember(orgId: String, memberId: String): Result<Unit> = runCatching {
        client.postgrest.rpc("remove_organization_member", buildJsonObject {
            put("p_organization_id", orgId); put("p_member_id", memberId)
        })
        Result.Success(Unit)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun joinByInviteCode(code: String): Result<Organization> = runCatching {
        val result = client.postgrest.rpc("join_organization_by_invite", buildJsonObject {
            put("p_invite_code", code.trim())
        }).decodeAs<JsonObject>()
        val orgId = result["organization_id"]?.jsonPrimitive?.contentOrNull ?: error("Invite join failed")
        val org = client.postgrest["organizations"]
            .select { filter { eq("id", orgId) } }
            .decodeSingle<Organization>()
        Result.Success(org)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getInviteCodes(orgId: String): Result<List<InviteCode>> = runCatching {
        val codes = client.postgrest["invite_codes"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<InviteCode>()
        Result.Success(codes)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun createInviteCode(orgId: String, request: InviteCodeCreateRequest): Result<InviteCode> = runCatching {
        require(request.role == "member") { "Delegated administrators may only create member invitations" }
        val code = client.postgrest.rpc("create_member_invite", buildJsonObject {
            put("p_organization_id", orgId)
            if (request.maxUses != null) put("p_max_uses", request.maxUses) else put("p_max_uses", kotlinx.serialization.json.JsonNull)
            if (request.expiresAt != null) put("p_expires_at", request.expiresAt) else put("p_expires_at", kotlinx.serialization.json.JsonNull)
        })
            .decodeSingle<InviteCode>()
        Result.Success(code)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getRules(orgId: String): Result<List<AttendanceRule>> = runCatching {
        val rules = client.postgrest["attendance_rules"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<AttendanceRule>()
        Result.Success(rules)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun createRule(orgId: String, request: AttendanceRuleCreateRequest): Result<AttendanceRule> = runCatching {
        // Deactivate any existing active rules for this org so the new rule overrides them.
        runCatching {
            client.postgrest["attendance_rules"].update({ set("is_active", false) }) {
                filter { eq("organization_id", orgId); eq("is_active", true) }
            }
        }
        val rule = client.postgrest["attendance_rules"]
            .insert(request.copy(organizationId = orgId)) { select() }
            .decodeSingle<AttendanceRule>()
        Result.Success(rule)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun deleteRule(ruleId: String): Result<Unit> = runCatching {
        client.postgrest["attendance_rules"].delete { filter { eq("id", ruleId) } }
        Result.Success(Unit)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    // ── Event Staff management (owner only) ───────────────────────────────────

    suspend fun grantEventStaff(memberId: String, orgId: String): Result<Boolean> = runCatching {
        val params = buildJsonObject {
            put("p_member_id", memberId)
            put("p_organization_id", orgId)
        }
        val json = client.postgrest.rpc("grant_event_staff", params).decodeAs<JsonObject>()
        val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!success) {
            return@runCatching Result.Error(json["message"]?.jsonPrimitive?.contentOrNull ?: "Failed to grant privilege")
        }
        Result.Success(true)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun revokeEventStaff(memberId: String, orgId: String): Result<Boolean> = runCatching {
        val params = buildJsonObject {
            put("p_member_id", memberId)
            put("p_organization_id", orgId)
        }
        val json = client.postgrest.rpc("revoke_event_staff", params).decodeAs<JsonObject>()
        val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!success) {
            return@runCatching Result.Error(json["message"]?.jsonPrimitive?.contentOrNull ?: "Failed to revoke privilege")
        }
        Result.Success(true)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getEventStaff(orgId: String): Result<List<EventStaffMember>> = runCatching {
        val params = buildJsonObject { put("p_organization_id", orgId) }
        // JSON/JSONB RPCs return the object itself. decodeSingle() expects a
        // PostgREST row array and therefore fails when the root token is '{'.
        val json = client.postgrest.rpc("get_event_staff", params).decodeAs<JsonObject>()
        val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!success) {
            return@runCatching Result.Error(json["message"]?.jsonPrimitive?.contentOrNull ?: "Not authorized")
        }
        val staffArray = json["staff"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
        val staff = staffArray.map { element ->
            kotlinx.serialization.json.Json.decodeFromJsonElement<EventStaffMember>(element)
        }
        Result.Success(staff)
    }.getOrElse { Result.Error(it.toErrorMessage()) }
}

// ─── Attendance Repository ───────────────────────────────────────────────────

class AttendanceRepository {

    private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private fun nowIso(): String = Instant.now().toString()

    suspend fun getTodayStatus(orgId: String? = null): Result<AttendanceRecord?> = runCatching {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val records = client.postgrest["attendance_records"]
            .select { filter {
                eq("user_id", uid)
                eq("date", today())
                if (orgId != null) eq("organization_id", orgId)
            }}
            .decodeList<AttendanceRecord>()
        Result.Success(records.firstOrNull())
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun checkIn(
        orgId: String, latitude: Double?, longitude: Double?,
        notes: String?, isRemote: Boolean,
        locationStatus: LocationStatus = LocationStatus.UNKNOWN
    ): Result<AttendanceRecord> = runCatching {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val params = buildJsonObject {
            put("p_user_id", uid)
            put("p_organization_id", orgId)
            put("p_date", today())
            put("p_check_in_time", nowIso())
            if (latitude != null) put("p_check_in_latitude", latitude) else put("p_check_in_latitude", kotlinx.serialization.json.JsonNull)
            if (longitude != null) put("p_check_in_longitude", longitude) else put("p_check_in_longitude", kotlinx.serialization.json.JsonNull)
            put("p_check_in_location_status", locationStatus.value)
            if (notes != null) put("p_check_in_notes", notes) else put("p_check_in_notes", kotlinx.serialization.json.JsonNull)
            put("p_is_remote", isRemote)
            put("p_status", if (isRemote) AttendanceStatus.REMOTE.value else AttendanceStatus.PRESENT.value)
        }
        val record = client.postgrest.rpc("check_in", params).decodeSingle<AttendanceRecord>()
        Result.Success(record)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun checkOut(
        orgId: String, latitude: Double?, longitude: Double?, notes: String?,
        locationStatus: LocationStatus = LocationStatus.UNKNOWN
    ): Result<AttendanceRecord> = runCatching {
        val params = buildJsonObject {
            put("p_organization_id", orgId)
            put("p_check_out_time", nowIso())
            if (latitude != null) put("p_check_out_latitude", latitude) else put("p_check_out_latitude", kotlinx.serialization.json.JsonNull)
            if (longitude != null) put("p_check_out_longitude", longitude) else put("p_check_out_longitude", kotlinx.serialization.json.JsonNull)
            put("p_check_out_location_status", locationStatus.value)
            if (notes != null) put("p_check_out_notes", notes) else put("p_check_out_notes", kotlinx.serialization.json.JsonNull)
        }
        val record = client.postgrest.rpc("check_out", params).decodeSingle<AttendanceRecord>()
        Result.Success(record)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getHistory(
        orgId: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        status: String? = null
    ): Result<List<AttendanceRecord>> = runCatching {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        val records = client.postgrest["attendance_records"]
            .select { filter {
                eq("user_id", uid)
                if (orgId != null) eq("organization_id", orgId)
                if (startDate != null) gte("date", startDate)
                if (endDate != null) lte("date", endDate)
                if (status != null) eq("status", status)
            }}
            .decodeList<AttendanceRecord>()
        Result.Success(records)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getOrgAttendance(
        orgId: String,
        startDate: String? = null,
        endDate: String? = null,
        status: String? = null
    ): Result<List<AttendanceRecord>> = runCatching {
        val records = client.postgrest["attendance_records"]
            .select { filter {
                eq("organization_id", orgId)
                if (startDate != null) gte("date", startDate)
                if (endDate != null) lte("date", endDate)
                if (status != null) eq("status", status)
            }}
            .decodeList<AttendanceRecord>()
        Result.Success(records)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getOrgSummary(orgId: String, startDate: String?, endDate: String?): Result<AttendanceSummary> = runCatching {
        val result = getOrgAttendance(orgId, startDate, endDate)
        if (result is Result.Error) return@runCatching Result.Error(result.message)
        val records = (result as Result.Success).data
        val summary = AttendanceSummary(
            total   = records.size,
            present = records.count { it.status == AttendanceStatus.PRESENT },
            absent  = records.count { it.status == AttendanceStatus.ABSENT },
            late    = records.count { it.status == AttendanceStatus.LATE },
            earlyDeparture = records.count { it.status == AttendanceStatus.EARLY_DEPARTURE },
            onLeave = records.count { it.status == AttendanceStatus.ON_LEAVE },
            remote  = records.count { it.status == AttendanceStatus.REMOTE },
            attendanceRate = if (records.isEmpty()) 0.0
                             else records.count { it.status != AttendanceStatus.ABSENT }.toDouble() / records.size * 100
        )
        Result.Success(summary)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    /**
     * Calls the get_attendance_report RPC which cross-joins active members with
     * expected working days and LEFT JOINs actual records, filling in 'absent'
     * for missing days. This ensures absences are counted even when no check-in
     * records exist.
     */
    suspend fun getAttendanceReport(orgId: String, startDate: String, endDate: String): Result<List<AttendanceReportRow>> = runCatching {
        val rows = client.postgrest.rpc("get_attendance_report", buildJsonObject {
            put("p_organization_id", orgId)
            put("p_start_date", startDate)
            put("p_end_date", endDate)
        }).decodeList<AttendanceReportRow>()
        Result.Success(rows)
    }.getOrElse { Result.Error(it.toErrorMessage()) }
}

// ─── Event Repository (Phase 3 MMP — Event Mode) ─────────────────────────────

class EventRepository {

    suspend fun createEvent(
        organizationId: String,
        name: String,
        eventDate: String,
        checkInOpenAt: String,
        checkInCloseAt: String,
        venueName: String?,
        venueLatitude: Double?,
        venueLongitude: Double?,
        venueRadiusM: Double?,
        requireLocation: Boolean,
        expectedCount: Int,
        retentionDays: Int
    ): Result<CreateEventResponse> = runCatching {
        val params = buildJsonObject {
            put("p_organization_id", organizationId)
            put("p_name", name)
            put("p_event_date", eventDate)
            put("p_check_in_open_at", checkInOpenAt)
            put("p_check_in_close_at", checkInCloseAt)
            if (venueName != null) put("p_venue_name", venueName) else put("p_venue_name", kotlinx.serialization.json.JsonNull)
            if (venueLatitude != null) put("p_venue_latitude", venueLatitude) else put("p_venue_latitude", kotlinx.serialization.json.JsonNull)
            if (venueLongitude != null) put("p_venue_longitude", venueLongitude) else put("p_venue_longitude", kotlinx.serialization.json.JsonNull)
            if (venueRadiusM != null) put("p_venue_radius_m", venueRadiusM) else put("p_venue_radius_m", kotlinx.serialization.json.JsonNull)
            put("p_require_location", requireLocation)
            put("p_expected_count", expectedCount)
            put("p_retention_days", retentionDays)
        }
        // RPC returns a bare JSONB object (not an array), so decode as JsonObject
        // and map fields manually — decodeSingle expects an array wrapper.
        val json = client.postgrest.rpc("create_event", params).decodeSingle<JsonObject>()
        val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
        val message = json["message"]?.jsonPrimitive?.contentOrNull
        if (!success) {
            return@runCatching Result.Error(message ?: "Failed to create event")
        }
        val eventJson = json["event"]?.let { kotlinx.serialization.json.Json.decodeFromJsonElement<Event>(it) }
        val token = json["token"]?.jsonPrimitive?.contentOrNull
        val tokenId = json["token_id"]?.jsonPrimitive?.contentOrNull
        val checkInUrl = json["check_in_url"]?.jsonPrimitive?.contentOrNull
        Result.Success(CreateEventResponse(
            success = true,
            event = eventJson,
            token = token,
            tokenId = tokenId,
            checkInUrl = checkInUrl
        ))
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getEvents(organizationId: String): Result<List<Event>> = runCatching {
        val events = client.postgrest["events"]
            .select { filter { eq("organization_id", organizationId) } }
            .decodeList<Event>()
        Result.Success(events)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getEvent(eventId: String): Result<Event> = runCatching {
        val event = client.postgrest["events"]
            .select { filter { eq("id", eventId) } }
            .decodeSingle<Event>()
        Result.Success(event)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun eventCheckIn(
        eventId: String,
        token: String?,
        fullName: String,
        email: String?,
        userId: String?,
        guestId: String?,
        latitude: Double?,
        longitude: Double?,
        checkInMethod: String
    ): Result<EventCheckInResponse> = runCatching {
        val params = buildJsonObject {
            put("p_event_id", eventId)
            if (token != null) put("p_token", token) else put("p_token", kotlinx.serialization.json.JsonNull)
            put("p_full_name", fullName)
            if (email != null) put("p_email", email) else put("p_email", kotlinx.serialization.json.JsonNull)
            if (userId != null) put("p_user_id", userId) else put("p_user_id", kotlinx.serialization.json.JsonNull)
            if (guestId != null) put("p_guest_id", guestId) else put("p_guest_id", kotlinx.serialization.json.JsonNull)
            if (latitude != null) put("p_latitude", latitude) else put("p_latitude", kotlinx.serialization.json.JsonNull)
            if (longitude != null) put("p_longitude", longitude) else put("p_longitude", kotlinx.serialization.json.JsonNull)
            put("p_check_in_method", checkInMethod)
        }
        val response = client.postgrest.rpc("event_check_in", params).decodeSingle<EventCheckInResponse>()
        Result.Success(response)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun bulkCheckIn(
        eventId: String,
        attendees: kotlinx.serialization.json.JsonElement,
        checkInMethod: String
    ): Result<BulkCheckInResponse> = runCatching {
        val params = buildJsonObject {
            put("p_event_id", eventId)
            put("p_attendees", attendees)
            put("p_check_in_method", checkInMethod)
        }
        val response = client.postgrest.rpc("bulk_event_check_in", params).decodeSingle<BulkCheckInResponse>()
        Result.Success(response)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getLiveCount(eventId: String): Result<EventLiveCount> = runCatching {
        val params = buildJsonObject { put("p_event_id", eventId) }
        val response = client.postgrest.rpc("get_event_live_count", params).decodeSingle<EventLiveCount>()
        Result.Success(response)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getAttendees(eventId: String, page: Int, pageSize: Int): Result<EventAttendeesResponse> = runCatching {
        val params = buildJsonObject {
            put("p_event_id", eventId)
            put("p_page", page)
            put("p_page_size", pageSize)
        }
        val response = client.postgrest.rpc("get_event_attendees", params).decodeSingle<EventAttendeesResponse>()
        Result.Success(response)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun getReport(eventId: String): Result<EventReport> = runCatching {
        val params = buildJsonObject { put("p_event_id", eventId) }
        val response = client.postgrest.rpc("get_event_report", params).decodeSingle<EventReport>()
        Result.Success(response)
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun generateQRToken(
        eventId: String,
        tokenType: String,
        expiresSecs: Int,
        maxUses: Int
    ): Result<CreateEventResponse> = runCatching {
        val params = buildJsonObject {
            put("p_event_id", eventId)
            put("p_token_type", tokenType)
            put("p_expires_secs", expiresSecs)
            put("p_max_uses", maxUses)
        }
        val json = client.postgrest.rpc("generate_event_qr_token", params).decodeSingle<JsonObject>()
        val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
        val message = json["message"]?.jsonPrimitive?.contentOrNull
        if (!success) {
            return@runCatching Result.Error(message ?: "Failed to generate token")
        }
        Result.Success(CreateEventResponse(
            success = true,
            token = json["token"]?.jsonPrimitive?.contentOrNull,
            tokenId = json["token_id"]?.jsonPrimitive?.contentOrNull
        ))
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    suspend fun cleanupExpiredEvents(): Result<CreateEventResponse> = runCatching {
        val json = client.postgrest.rpc("cleanup_expired_events").decodeSingle<JsonObject>()
        val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
        val message = json["message"]?.jsonPrimitive?.contentOrNull
        if (!success) {
            return@runCatching Result.Error(message ?: "Cleanup failed")
        }
        Result.Success(CreateEventResponse(success = true))
    }.getOrElse { Result.Error(it.toErrorMessage()) }

    // ── Kiosk token fetch (for event staff) ──────────────────────────────────
    suspend fun getKioskToken(eventId: String): Result<String> = runCatching {
        val params = buildJsonObject { put("p_event_id", eventId) }
        val json = client.postgrest.rpc("get_kiosk_token", params).decodeSingle<JsonObject>()
        val success = json["success"]?.jsonPrimitive?.booleanOrNull ?: false
        val message = json["message"]?.jsonPrimitive?.contentOrNull
        if (!success) {
            return@runCatching Result.Error(message ?: "Failed to get kiosk token")
        }
        val token = json["token"]?.jsonPrimitive?.contentOrNull
        if (token != null) Result.Success(token)
        else Result.Error("No token returned")
    }.getOrElse { Result.Error(it.toErrorMessage()) }
}
