package com.kosd.log_inattendancesafeguard.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kosd.log_inattendancesafeguard.models.AttendanceRecord
import com.kosd.log_inattendancesafeguard.models.LocationStatus
import com.kosd.log_inattendancesafeguard.repository.AttendanceRepository
import com.kosd.log_inattendancesafeguard.repository.Result
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AttendanceViewModel(private val repository: AttendanceRepository) : ViewModel() {

    var todayRecord       by mutableStateOf<AttendanceRecord?>(null)
    var historyRecords    by mutableStateOf<List<AttendanceRecord>>(emptyList())
    var isCheckedIn       by mutableStateOf(false)
    var isCheckedOut      by mutableStateOf(false)
    var canCheckIn        by mutableStateOf(true)
    var canCheckOut       by mutableStateOf(false)
    var isLoading         by mutableStateOf(false)
    var isHistoryLoading  by mutableStateOf(false)
    var errorMessage      by mutableStateOf<String?>(null)
    var showError         by mutableStateOf(false)
    var successMessage    by mutableStateOf<String?>(null)
    var showSuccess       by mutableStateOf(false)
    var needsOrganization by mutableStateOf(false)

    val statusText: String
        get() = when {
            isCheckedOut -> "Checked Out"
            isCheckedIn  -> "Checked In"
            else         -> "Not Checked In"
        }

    val checkInTimeText: String?  get() = todayRecord?.checkInTime?.let { formatTimeFromIso(it) }
    val checkOutTimeText: String? get() = todayRecord?.checkOutTime?.let { formatTimeFromIso(it) }

    var currentOrgId: String? = null

    fun resetForOrgSwitch() {
        todayRecord   = null
        isCheckedIn   = false
        isCheckedOut  = false
        canCheckIn    = true
        canCheckOut   = false
        needsOrganization = false
    }

    fun loadTodayStatus() {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getTodayStatus(currentOrgId)) {
                is Result.Success -> {
                    val record: AttendanceRecord? = result.data
                    todayRecord = record
                    if (record != null) {
                        updateStatus(record)
                        needsOrganization = false
                    } else {
                        needsOrganization = currentOrgId == null
                    }
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun checkIn(
        orgId: String, latitude: Double?, longitude: Double?,
        notes: String?, isRemote: Boolean = false,
        locationStatus: LocationStatus = LocationStatus.UNKNOWN
    ) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.checkIn(orgId, latitude, longitude, notes?.ifBlank { null }, isRemote, locationStatus)) {
                is Result.Success -> {
                    todayRecord = result.data
                    updateStatus(result.data)
                    successMessage = "Checked in successfully!"
                    showSuccess = true
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun checkOut(
        latitude: Double?, longitude: Double?, notes: String?,
        locationStatus: LocationStatus = LocationStatus.UNKNOWN
    ) {
        val orgId = currentOrgId ?: return
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.checkOut(orgId, latitude, longitude, notes?.ifBlank { null }, locationStatus)) {
                is Result.Success -> {
                    todayRecord = result.data
                    updateStatus(result.data)
                    successMessage = "Checked out successfully!"
                    showSuccess = true
                }
                is Result.Error -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun loadHistory(startDate: String? = null, endDate: String? = null, status: String? = null) {
        viewModelScope.launch {
            isHistoryLoading = true
            when (val result = repository.getHistory(currentOrgId, startDate, endDate, status)) {
                is Result.Success -> historyRecords = result.data
                is Result.Error   -> showError(result.message)
            }
            isHistoryLoading = false
        }
    }

    fun loadThisMonthHistory() {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = fmt.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = fmt.format(cal.time)
        loadHistory(start, end)
    }

    fun loadThisWeekHistory() {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val start = fmt.format(cal.time)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek + 6)
        val end = fmt.format(cal.time)
        loadHistory(start, end)
    }

    fun loadAllHistory() = loadHistory()

    private fun updateStatus(record: AttendanceRecord) {
        isCheckedIn  = record.checkInTime != null
        isCheckedOut = record.checkOutTime != null
        canCheckIn   = !isCheckedIn
        canCheckOut  = isCheckedIn && !isCheckedOut
    }

    private fun formatTimeFromIso(isoString: String): String {
        return try {
            val inputFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = inputFmt.parse(isoString) ?: return isoString
            outputFmt.format(date)
        } catch (_: Exception) { isoString }
    }

    fun dismissError()   { showError = false; errorMessage = null }
    fun dismissSuccess() { showSuccess = false; successMessage = null }

    private fun showError(message: String) { errorMessage = message; showError = true }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AttendanceViewModel(AttendanceRepository()) as T
    }
}
