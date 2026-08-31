package com.kosd.log_inattendancesafeguard.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

// ─── Enums ───────────────────────────────────────────────────────────────────

@Serializable
enum class UserRole(val value: String) {
    @SerialName("owner")        OWNER("owner"),
    @SerialName("admin")        ADMIN("admin"),
    @SerialName("event_staff")  EVENT_STAFF("event_staff"),
    @SerialName("member")       MEMBER("member");

    val displayName: String
        get() = when (this) {
            OWNER        -> "Owner"
            ADMIN        -> "Admin"
            EVENT_STAFF  -> "Event Staff"
            MEMBER       -> "Member"
        }

    val isAdmin: Boolean get() = this == OWNER || this == ADMIN
    val isOwner: Boolean get() = this == OWNER
    val isEventStaff: Boolean get() = this == OWNER || this == ADMIN || this == EVENT_STAFF
}

enum class Permission(val value: String, val displayName: String) {
    MANAGE_MEMBERS("manage_members", "Add and remove ordinary members"),
    CREATE_INVITES("create_invites", "Add and invite members"),
    VIEW_REPORTS("view_reports", "View organization reports"),
    MANAGE_ATTENDANCE_RULES("manage_attendance_rules", "Manage attendance settings"),
    MANAGE_EVENTS("manage_events", "Manage events");

    companion object {
        fun fromValue(value: String): Permission? = entries.firstOrNull { it.value == value }
    }
}

@Serializable
data class OrganizationMemberPermission(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("member_id") val memberId: String,
    val permission: String
)

@Serializable
enum class AttendanceStatus(val value: String) {
    @SerialName("present")          PRESENT("present"),
    @SerialName("absent")           ABSENT("absent"),
    @SerialName("late")             LATE("late"),
    @SerialName("early_departure")  EARLY_DEPARTURE("early_departure"),
    @SerialName("on_leave")         ON_LEAVE("on_leave"),
    @SerialName("remote")           REMOTE("remote"),
    @SerialName("half_day")         HALF_DAY("half_day");

    val displayName: String
        get() = when (this) {
            PRESENT         -> "Present"
            ABSENT          -> "Absent"
            LATE            -> "Late"
            EARLY_DEPARTURE -> "Early Departure"
            ON_LEAVE        -> "On Leave"
            REMOTE          -> "Remote"
            HALF_DAY        -> "Half Day"
        }
}

@Serializable
enum class AttendanceFrequency(val value: String) {
    @SerialName("daily")           DAILY("daily"),
    @SerialName("monday_friday")   MONDAY_FRIDAY("monday_friday"),
    @SerialName("sunday_thursday") SUNDAY_THURSDAY("sunday_thursday");

    val displayName: String
        get() = when (this) {
            DAILY           -> "Daily"
            MONDAY_FRIDAY   -> "Monday to Friday"
            SUNDAY_THURSDAY -> "Sunday to Thursday"
        }

    val workingDays: List<Int>
        get() = when (this) {
            DAILY           -> listOf(0, 1, 2, 3, 4, 5, 6)
            MONDAY_FRIDAY   -> listOf(1, 2, 3, 4, 5)
            SUNDAY_THURSDAY -> listOf(0, 1, 2, 3, 4)
        }
}

@Serializable
enum class PopulationTier(val value: String) {
    // Legacy aliases (`under_20`, `20_50`, `over_500`) are accepted via @JsonNames so
    // rows from the previous schema deserialize without crashing the org load.
    @SerialName("under_10")        @JsonNames("under_20")  UNDER_10("under_10"),
    @SerialName("10_50")           @JsonNames("20_50")     T_10_50("10_50"),
    @SerialName("50_100")          T_50_100("50_100"),
    @SerialName("100_500")         T_100_500("100_500"),
    @SerialName("500_1000")        @JsonNames("over_500")  T_500_1000("500_1000"),
    @SerialName("1000_10000")      T_1K_10K("1000_10000"),
    @SerialName("10000_100000")    T_10K_100K("10000_100000"),
    @SerialName("100000_1000000")  T_100K_1M("100000_1000000"),
    @SerialName("over_1000000")    T_OVER_1M("over_1000000");

    val displayName: String
        get() = when (this) {
            UNDER_10    -> "< 10 (Free)"
            T_10_50     -> "10 – 50"
            T_50_100    -> "50 – 100"
            T_100_500   -> "100 – 500"
            T_500_1000  -> "500 – 1,000"
            T_1K_10K    -> "1,000 – 10,000"
            T_10K_100K  -> "10,000 – 100,000"
            T_100K_1M   -> "100,000 – 1,000,000"
            T_OVER_1M   -> "> 1,000,000"
        }

    /** True for the free tier; paid tiers require an in-app purchase to activate. */
    val isFree: Boolean get() = this == UNDER_10

    /**
     * Google Play product/subscription ID associated with this tier.
     * Configure these in the Play Console as subscriptions.
     * Free tier returns null since no purchase is required.
     */
    val productId: String?
        get() = when (this) {
            UNDER_10    -> null
            T_10_50     -> "tier_10_50"
            T_50_100    -> "tier_50_100"
            T_100_500   -> "tier_100_500"
            T_500_1000  -> "tier_500_1000"
            T_1K_10K    -> "tier_1k_10k"
            T_10K_100K  -> "tier_10k_100k"
            T_100K_1M   -> "tier_100k_1m"
            T_OVER_1M   -> "tier_over_1m"
        }

    companion object {
        val FREE: PopulationTier = UNDER_10
    }
}

@Serializable
enum class LocationStatus(val value: String) {
    @SerialName("valid")        VALID("valid"),
    @SerialName("invalid")      INVALID("invalid"),
    @SerialName("unknown")      UNKNOWN("unknown"),
    @SerialName("not_required") NOT_REQUIRED("not_required")
}

// ─── Invite Signup Response (from Edge Function) ─────────────────────────────

@Serializable
data class InviteSignupResponse(
    val success: Boolean = false,
    val requiresSignIn: Boolean = false,
    val accessToken: String? = null,
    val refreshToken: String? = null
)

// ─── User ────────────────────────────────────────────────────────────────────

@Serializable
data class User(
    val id: String = "",
    val email: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name")  val lastName: String = "",
    @SerialName("is_active")  val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
) {
    val fullName: String get() = "$firstName $lastName"
    val initials: String
        get() = buildString {
            if (firstName.isNotEmpty()) append(firstName.first().uppercaseChar())
            if (lastName.isNotEmpty())  append(lastName.first().uppercaseChar())
        }
}

@Serializable
data class OrgMember(
    val id: String = "",
    @SerialName("user_id")         val userId: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    val role: UserRole = UserRole.MEMBER,
    @SerialName("is_active")       val isActive: Boolean = true,
    @SerialName("joined_at")       val joinedAt: String? = null,
    val profile: User? = null
) {
    val isAdmin: Boolean get() = role.isAdmin
}

@Serializable
data class Organization(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val description: String? = null,
    val timezone: String = "UTC",
    @SerialName("is_active")            val isActive: Boolean = true,
    @SerialName("max_members")          val maxMembers: Int = 100,
    @SerialName("subscription_tier")    val subscriptionTier: String = "basic",
    @SerialName("population_tier")      val populationTier: PopulationTier = PopulationTier.UNDER_10,
    @SerialName("member_count")         val memberCount: Int? = null,
    @SerialName("created_by")           val createdBy: String? = null,
    @SerialName("data_retention_days")  val dataRetentionDays: Int? = null,
    @SerialName("created_at")           val createdAt: String? = null,
    @SerialName("updated_at")           val updatedAt: String? = null
)

@Serializable
data class OrganizationCreateRequest(
    val name: String,
    val slug: String,
    val description: String? = null,
    val timezone: String = "UTC",
    @SerialName("max_members") val maxMembers: Int = 100,
    @SerialName("population_tier") val populationTier: PopulationTier = PopulationTier.UNDER_10
)

@Serializable
data class OrganizationUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val timezone: String? = null,
    @SerialName("is_active")            val isActive: Boolean? = null,
    @SerialName("max_members")          val maxMembers: Int? = null,
    @SerialName("population_tier")      val populationTier: PopulationTier? = null,
    @SerialName("data_retention_days")  val dataRetentionDays: Int? = null
)

// ─── Attendance Rules ────────────────────────────────────────────────────────

@Serializable
data class AttendanceRule(
    val id: String = "",
    @SerialName("organization_id")    val organizationId: String = "",
    val name: String = "",
    @SerialName("work_start_time")    val workStartTime: String = "09:00",
    @SerialName("work_end_time")      val workEndTime: String = "17:00",
    @SerialName("check_in_start")     val checkInStart: String = "08:30",
    @SerialName("check_in_end")       val checkInEnd: String = "09:15",
    @SerialName("check_out_start")    val checkOutStart: String = "16:30",
    @SerialName("check_out_end")      val checkOutEnd: String = "18:00",
    @SerialName("grace_period_mins")  val gracePeriodMins: Int = 0,
    @SerialName("working_days")       val workingDays: List<Int> = listOf(1, 2, 3, 4, 5),
    @SerialName("require_location")   val requireLocation: Boolean = false,
    @SerialName("location_latitude")  val locationLatitude: Double? = null,
    @SerialName("location_longitude") val locationLongitude: Double? = null,
    @SerialName("location_radius_m")  val locationRadiusM: Double? = null,
    val frequency: AttendanceFrequency = AttendanceFrequency.DAILY,
    @SerialName("is_active")          val isActive: Boolean = true,
    @SerialName("created_at")         val createdAt: String? = null
)

@Serializable
data class AttendanceRuleCreateRequest(
    @SerialName("organization_id")    val organizationId: String,
    val name: String,
    @SerialName("work_start_time")    val workStartTime: String,
    @SerialName("work_end_time")      val workEndTime: String,
    @SerialName("check_in_start")     val checkInStart: String,
    @SerialName("check_in_end")       val checkInEnd: String,
    @SerialName("check_out_start")    val checkOutStart: String,
    @SerialName("check_out_end")      val checkOutEnd: String,
    @SerialName("grace_period_mins")  val gracePeriodMins: Int = 0,
    @SerialName("working_days")       val workingDays: List<Int> = listOf(1, 2, 3, 4, 5),
    @SerialName("require_location")   val requireLocation: Boolean = false,
    @SerialName("location_latitude")  val locationLatitude: Double? = null,
    @SerialName("location_longitude") val locationLongitude: Double? = null,
    @SerialName("location_radius_m")  val locationRadiusM: Double? = null,
    val frequency: AttendanceFrequency = AttendanceFrequency.DAILY
)

// ─── Member (alias kept for UI compatibility) ────────────────────────────────
typealias Member = OrgMember

@Serializable
data class OrgMemberCreateRequest(
    @SerialName("user_id")         val userId: String,
    @SerialName("organization_id") val organizationId: String,
    val role: String = "member"
)

// ─── Attendance ──────────────────────────────────────────────────────────────

@Serializable
data class AttendanceRecord(
    val id: String = "",
    @SerialName("user_id")                     val userId: String = "",
    @SerialName("organization_id")             val organizationId: String = "",
    val date: String = "",
    @SerialName("check_in_time")               val checkInTime: String? = null,
    @SerialName("check_out_time")              val checkOutTime: String? = null,
    @SerialName("check_in_latitude")           val checkInLatitude: Double? = null,
    @SerialName("check_in_longitude")          val checkInLongitude: Double? = null,
    @SerialName("check_out_latitude")          val checkOutLatitude: Double? = null,
    @SerialName("check_out_longitude")         val checkOutLongitude: Double? = null,
    @SerialName("check_in_location_status")    val checkInLocationStatus: LocationStatus = LocationStatus.UNKNOWN,
    @SerialName("check_out_location_status")   val checkOutLocationStatus: LocationStatus = LocationStatus.UNKNOWN,
    val status: AttendanceStatus = AttendanceStatus.ABSENT,
    @SerialName("check_in_notes")              val checkInNotes: String? = null,
    @SerialName("check_out_notes")             val checkOutNotes: String? = null,
    @SerialName("is_late")                     val isLate: Boolean = false,
    @SerialName("is_early_departure")          val isEarlyDeparture: Boolean = false,
    @SerialName("late_minutes")                val lateMinutes: Int? = null,
    @SerialName("early_departure_minutes")     val earlyDepartureMinutes: Int? = null,
    @SerialName("is_remote")                   val isRemote: Boolean = false,
    @SerialName("created_at")                  val createdAt: String? = null
)

@Serializable
data class CheckInRequest(
    @SerialName("user_id")                  val userId: String,
    @SerialName("organization_id")          val organizationId: String,
    val date: String,
    @SerialName("check_in_time")            val checkInTime: String,
    @SerialName("check_in_latitude")        val latitude: Double? = null,
    @SerialName("check_in_longitude")       val longitude: Double? = null,
    @SerialName("check_in_location_status") val checkInLocationStatus: LocationStatus = LocationStatus.UNKNOWN,
    @SerialName("check_in_notes")           val checkInNotes: String? = null,
    @SerialName("is_remote")                val isRemote: Boolean = false,
    val status: AttendanceStatus = AttendanceStatus.PRESENT
)

@Serializable
data class CheckOutRequest(
    @SerialName("check_out_time")            val checkOutTime: String,
    @SerialName("check_out_latitude")        val latitude: Double? = null,
    @SerialName("check_out_longitude")       val longitude: Double? = null,
    @SerialName("check_out_location_status") val checkOutLocationStatus: LocationStatus = LocationStatus.UNKNOWN,
    @SerialName("check_out_notes")           val checkOutNotes: String? = null
)

// ─── Invite Code ─────────────────────────────────────────────────────────────

@Serializable
data class InviteCode(
    val id: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    val code: String = "",
    val role: String = "member",
    @SerialName("max_uses")   val maxUses: Int? = null,
    @SerialName("use_count")  val useCount: Int = 0,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("is_active")  val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class InviteCodeCreateRequest(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("max_uses")        val maxUses: Int? = null,
    @SerialName("expires_at")      val expiresAt: String? = null,
    val role: String = "member"
)

// ─── Report ──────────────────────────────────────────────────────────────────

@Serializable
data class AttendanceReportRow(
    @SerialName("user_id")        val userId: String = "",
    @SerialName("member_id")      val memberId: String = "",
    @SerialName("first_name")     val firstName: String = "",
    @SerialName("last_name")      val lastName: String = "",
    val email: String = "",
    val role: String = "member",
    val date: String = "",
    val status: AttendanceStatus = AttendanceStatus.ABSENT,
    @SerialName("check_in_time")  val checkInTime: String? = null,
    @SerialName("check_out_time") val checkOutTime: String? = null,
    @SerialName("is_late")        val isLate: Boolean = false,
    @SerialName("is_remote")      val isRemote: Boolean = false,
    @SerialName("late_minutes")   val lateMinutes: Int? = null
)

data class AttendanceSummary(
    val total: Int = 0,
    val present: Int = 0,
    val absent: Int = 0,
    val late: Int = 0,
    val earlyDeparture: Int = 0,
    val onLeave: Int = 0,
    val remote: Int = 0,
    val attendanceRate: Double = 0.0
)

data class MemberAttendanceSummary(
    val member: Member,
    val summary: AttendanceSummary,
    val records: List<AttendanceRecord> = emptyList()
)

data class MessageResponse(val message: String)
