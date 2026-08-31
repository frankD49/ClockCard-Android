package com.kosd.log_inattendancesafeguard.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kosd.log_inattendancesafeguard.models.BulkCheckInResponse
import com.kosd.log_inattendancesafeguard.models.CreateEventResponse
import com.kosd.log_inattendancesafeguard.models.Event
import com.kosd.log_inattendancesafeguard.models.EventAttendeesResponse
import com.kosd.log_inattendancesafeguard.models.EventAttendee
import com.kosd.log_inattendancesafeguard.models.EventCheckInResponse
import com.kosd.log_inattendancesafeguard.models.EventLiveCount
import com.kosd.log_inattendancesafeguard.models.EventReport
import com.kosd.log_inattendancesafeguard.repository.EventRepository
import com.kosd.log_inattendancesafeguard.repository.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EventViewModel(private val repository: EventRepository) : ViewModel() {

    var events          by mutableStateOf<List<Event>>(emptyList())
    var selectedEvent   by mutableStateOf<Event?>(null)
    var liveCount       by mutableStateOf<EventLiveCount?>(null)
    var attendees       by mutableStateOf<List<EventAttendee>>(emptyList())
    var attendeesTotal  by mutableStateOf(0)
    var report          by mutableStateOf<EventReport?>(null)
    var createResult    by mutableStateOf<CreateEventResponse?>(null)
    var checkInResult   by mutableStateOf<EventCheckInResponse?>(null)
    var bulkResult      by mutableStateOf<BulkCheckInResponse?>(null)
    var qrToken         by mutableStateOf<String?>(null)
    var qrTokenId       by mutableStateOf<String?>(null)
    var qrCheckInUrl    by mutableStateOf<String?>(null)
    var isLoading       by mutableStateOf(false)
    var isPolling       by mutableStateOf(false)
    var errorMessage    by mutableStateOf<String?>(null)
    var showError       by mutableStateOf(false)
    var successMessage  by mutableStateOf<String?>(null)
    var showSuccess     by mutableStateOf(false)

    // ── Live count polling ────────────────────────────────────────────────────
    private var pollingJob: Job? = null
    private var pollingEventId: String? = null

    fun loadEvents(organizationId: String) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getEvents(organizationId)) {
                is Result.Success -> events = result.data
                is Result.Error   -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun createEvent(
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
    ) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.createEvent(
                organizationId, name, eventDate, checkInOpenAt, checkInCloseAt,
                venueName, venueLatitude, venueLongitude, venueRadiusM,
                requireLocation, expectedCount, retentionDays
            )) {
                is Result.Success -> {
                    createResult = result.data
                    if (result.data.success && result.data.event != null) {
                        events = events + result.data.event
                        qrToken = result.data.token
                        qrTokenId = result.data.tokenId
                        qrCheckInUrl = result.data.checkInUrl
                        successMessage = "Event created!"
                        showSuccess = true
                    }
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun selectEvent(event: Event) {
        selectedEvent = event
        liveCount = null
        attendees = emptyList()
        attendeesTotal = 0
        qrToken = null
        qrTokenId = null
        qrCheckInUrl = null
    }

    fun loadLiveCount(eventId: String) {
        viewModelScope.launch {
            when (val result = repository.getLiveCount(eventId)) {
                is Result.Success -> liveCount = result.data
                is Result.Error   -> { /* silent — polling shouldn't spam errors */ }
            }
        }
    }

    /**
     * Start polling the live count for an event every 5 seconds.
     * Cancels any previous polling job first.
     */
    fun startLiveCountPolling(eventId: String) {
        if (pollingEventId == eventId && pollingJob?.isActive == true) return
        stopLiveCountPolling()
        pollingEventId = eventId
        isPolling = true
        pollingJob = viewModelScope.launch {
            while (true) {
                loadLiveCount(eventId)
                delay(5000)
            }
        }
    }

    fun stopLiveCountPolling() {
        pollingJob?.cancel()
        pollingJob = null
        pollingEventId = null
        isPolling = false
    }

    fun loadAttendees(eventId: String, page: Int = 1, pageSize: Int = 50) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getAttendees(eventId, page, pageSize)) {
                is Result.Success -> {
                    attendees = result.data.attendees
                    attendeesTotal = result.data.total
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun checkIn(
        eventId: String,
        token: String?,
        fullName: String,
        email: String?,
        userId: String?,
        guestId: String?,
        latitude: Double?,
        longitude: Double?,
        checkInMethod: String
    ) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.eventCheckIn(
                eventId, token, fullName, email, userId, guestId,
                latitude, longitude, checkInMethod
            )) {
                is Result.Success -> {
                    checkInResult = result.data
                    if (result.data.success) {
                        successMessage = "Checked in: ${result.data.attendee?.fullName ?: fullName}"
                        showSuccess = true
                    } else {
                        showError(result.data.message ?: "Check-in failed")
                    }
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun bulkCheckIn(
        eventId: String,
        attendees: kotlinx.serialization.json.JsonElement,
        checkInMethod: String
    ) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.bulkCheckIn(eventId, attendees, checkInMethod)) {
                is Result.Success -> {
                    bulkResult = result.data
                    if (result.data.success) {
                        successMessage = "Bulk check-in: ${result.data.checkedIn} attendees"
                        showSuccess = true
                    }
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun generateQRToken(eventId: String, tokenType: String = "single", expiresSecs: Int = 86400, maxUses: Int = 0) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.generateQRToken(eventId, tokenType, expiresSecs, maxUses)) {
                is Result.Success -> {
                    qrToken = result.data.token
                    qrTokenId = result.data.tokenId
                    val slug = selectedEvent?.slug ?: ""
                    qrCheckInUrl = result.data.token?.let {
                        com.kosd.log_inattendancesafeguard.services.QRService.buildCheckInUrl(slug, it)
                    }
                    successMessage = "QR token generated"
                    showSuccess = true
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun getReport(eventId: String) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getReport(eventId)) {
                is Result.Success -> report = result.data
                is Result.Error   -> showError(result.message)
            }
            isLoading = false
        }
    }

    override fun onCleared() {
        stopLiveCountPolling()
        super.onCleared()
    }

    fun dismissError()   { showError = false; errorMessage = null }
    fun dismissSuccess() { showSuccess = false; successMessage = null }

    // ── Kiosk token fetch (for event staff who can't generate QR tokens) ──────
    fun fetchKioskToken(eventId: String) {
        viewModelScope.launch {
            when (val result = repository.getKioskToken(eventId)) {
                is Result.Success -> {
                    qrToken = result.data
                    val slug = selectedEvent?.slug ?: ""
                    qrCheckInUrl = result.data.let { token ->
                        com.kosd.log_inattendancesafeguard.services.QRService.buildCheckInUrl(slug, token)
                    }
                }
                is Result.Error -> showError(result.message)
            }
        }
    }

    private fun showError(msg: String) { errorMessage = msg; showError = true }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EventViewModel(EventRepository()) as T
    }
}
