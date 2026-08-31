package com.kosd.log_inattendancesafeguard.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Event Mode Models (Phase 3 MMP — up to 100,000 attendees) ───────────────

@Serializable
data class Event(
    val id: String = "",
    @SerialName("organization_id")    val organizationId: String = "",
    val name: String = "",
    val slug: String = "",
    val description: String? = null,
    @SerialName("event_date")         val eventDate: String? = null,
    @SerialName("event_start_time")   val eventStartTime: String? = null,
    @SerialName("event_end_time")     val eventEndTime: String? = null,
    @SerialName("check_in_open_at")   val checkInOpenAt: String? = null,
    @SerialName("check_in_close_at")  val checkInCloseAt: String? = null,
    @SerialName("venue_name")         val venueName: String? = null,
    @SerialName("venue_address")      val venueAddress: String? = null,
    @SerialName("venue_latitude")     val venueLatitude: Double? = null,
    @SerialName("venue_longitude")    val venueLongitude: Double? = null,
    @SerialName("venue_radius_m")     val venueRadiusM: Double? = null,
    @SerialName("require_location")   val requireLocation: Boolean = false,
    @SerialName("expected_count")     val expectedCount: Int = 0,
    @SerialName("is_active")          val isActive: Boolean = true,
    @SerialName("is_published")       val isPublished: Boolean = false,
    @SerialName("retention_days")     val retentionDays: Int = 90,
    @SerialName("created_by")         val createdBy: String? = null,
    @SerialName("created_at")         val createdAt: String? = null,
    @SerialName("updated_at")         val updatedAt: String? = null
)

@Serializable
data class EventAttendee(
    val id: String = "",
    @SerialName("event_id")               val eventId: String = "",
    @SerialName("user_id")                val userId: String? = null,
    @SerialName("guest_id")               val guestId: String? = null,
    @SerialName("full_name")              val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    @SerialName("registration_type")      val registrationType: String = "pre_registered",
    @SerialName("registered_at")          val registeredAt: String? = null,
    @SerialName("checked_in")             val checkedIn: Boolean = false,
    @SerialName("checked_in_at")          val checkedInAt: String? = null,
    @SerialName("check_in_method")        val checkInMethod: String? = null,
    @SerialName("check_in_latitude")      val checkInLatitude: Double? = null,
    @SerialName("check_in_longitude")     val checkInLongitude: Double? = null,
    @SerialName("check_in_location_status") val checkInLocationStatus: String? = null,
    @SerialName("checked_out")            val checkedOut: Boolean = false,
    @SerialName("checked_out_at")         val checkedOutAt: String? = null
)

@Serializable
data class EventLiveCount(
    @SerialName("registered")     val registered: Int = 0,
    @SerialName("checked_in")     val checkedIn: Int = 0,
    @SerialName("checked_out")    val checkedOut: Int = 0,
    @SerialName("walk_ins")       val walkIns: Int = 0,
    @SerialName("pre_registered") val preRegistered: Int = 0,
    @SerialName("last_check_in_at") val lastCheckInAt: String? = null
)

@Serializable
data class EventReportSummary(
    @SerialName("total_registered") val totalRegistered: Int = 0,
    @SerialName("checked_in")       val checkedIn: Int = 0,
    @SerialName("no_shows")         val noShows: Int = 0,
    @SerialName("walk_ins")         val walkIns: Int = 0,
    @SerialName("pre_registered")   val preRegistered: Int = 0,
    @SerialName("checked_out")      val checkedOut: Int = 0,
    @SerialName("check_in_rate")    val checkInRate: Double = 0.0
)

@Serializable
data class EventReport(
    val event: Event? = null,
    val summary: EventReportSummary? = null,
    @SerialName("method_breakdown")  val methodBreakdown: List<Map<String, kotlinx.serialization.json.JsonElement>> = emptyList(),
    @SerialName("time_distribution") val timeDistribution: List<Map<String, kotlinx.serialization.json.JsonElement>> = emptyList(),
    val attendees: List<EventAttendee> = emptyList()
)

@Serializable
data class CreateEventResponse(
    val success: Boolean = false,
    val event: Event? = null,
    val token: String? = null,
    @SerialName("token_id") val tokenId: String? = null,
    @SerialName("check_in_url") val checkInUrl: String? = null
)

@Serializable
data class EventCheckInResponse(
    val success: Boolean = false,
    val message: String? = null,
    val attendee: EventAttendee? = null,
    @SerialName("guest_id") val guestId: String? = null
)

@Serializable
data class BulkCheckInResponse(
    val success: Boolean = false,
    @SerialName("checked_in") val checkedIn: Int = 0,
    val errors: List<String> = emptyList()
)

@Serializable
data class EventAttendeesResponse(
    val success: Boolean = false,
    val attendees: List<EventAttendee> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 50,
    @SerialName("total_pages") val totalPages: Int = 1
)

@Serializable
data class EventStaffMember(
    @SerialName("member_id") val memberId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("full_name") val fullName: String = "",
    val email: String = "",
    val role: String = ""
)
