package com.kosd.log_inattendancesafeguard.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kosd.log_inattendancesafeguard.models.AttendanceRecord
import com.kosd.log_inattendancesafeguard.models.AttendanceStatus
import com.kosd.log_inattendancesafeguard.models.AttendanceSummary
import com.kosd.log_inattendancesafeguard.models.MemberAttendanceSummary
import com.kosd.log_inattendancesafeguard.models.OrgMember
import com.kosd.log_inattendancesafeguard.models.User
import com.kosd.log_inattendancesafeguard.models.UserRole
import com.kosd.log_inattendancesafeguard.network.SupabaseClientProvider.client
import com.kosd.log_inattendancesafeguard.repository.AttendanceRepository
import com.kosd.log_inattendancesafeguard.repository.OrganizationRepository
import com.kosd.log_inattendancesafeguard.repository.Result
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminViewModel(
    private val repository: AttendanceRepository,
    private val orgRepository: OrganizationRepository = OrganizationRepository()
) : ViewModel() {

    var orgAttendance    by mutableStateOf<List<AttendanceRecord>>(emptyList())
    var userProfiles     by mutableStateOf<Map<String, User>>(emptyMap())
    var summary          by mutableStateOf<AttendanceSummary?>(null)
    var membersSummary   by mutableStateOf<List<MemberAttendanceSummary>>(emptyList())
    var isLoading        by mutableStateOf(false)
    var errorMessage     by mutableStateOf<String?>(null)
    var showError        by mutableStateOf(false)
    var selectedFilter   by mutableStateOf("This Month")

    val filters = listOf("Today", "This Week", "This Month", "Last Month", "All Time")

    // ── Realtime subscription ────────────────────────────────────────────────
    private var realtimeChannel: RealtimeChannel? = null
    private var realtimeJob: Job? = null
    private var subscribedOrgId: String? = null

    fun loadOrgAttendance(orgId: String, startDate: String? = null, endDate: String? = null, status: String? = null) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getOrgAttendance(orgId, startDate, endDate, status)) {
                is Result.Success -> orgAttendance = result.data
                is Result.Error   -> showError(result.message)
            }
            isLoading = false
        }
    }

    fun loadSummary(orgId: String, startDate: String? = null, endDate: String? = null) {
        viewModelScope.launch {
            isLoading = true
            if (startDate != null && endDate != null) {
                // Use the new RPC that computes absences dynamically
                when (val result = repository.getAttendanceReport(orgId, startDate, endDate)) {
                    is Result.Success -> {
                        val rows = result.data
                        summary = AttendanceSummary(
                            total   = rows.size,
                            present = rows.count { it.status == AttendanceStatus.PRESENT },
                            absent  = rows.count { it.status == AttendanceStatus.ABSENT },
                            late    = rows.count { it.status == AttendanceStatus.LATE },
                            earlyDeparture = rows.count { it.status == AttendanceStatus.EARLY_DEPARTURE },
                            onLeave = rows.count { it.status == AttendanceStatus.ON_LEAVE },
                            remote  = rows.count { it.status == AttendanceStatus.REMOTE },
                            attendanceRate = if (rows.isEmpty()) 0.0
                                             else rows.count { it.status != AttendanceStatus.ABSENT }.toDouble() / rows.size * 100
                        )
                    }
                    is Result.Error -> showError(result.message)
                }
            } else {
                when (val result = repository.getOrgSummary(orgId, startDate, endDate)) {
                    is Result.Success -> summary = result.data
                    is Result.Error   -> showError(result.message)
                }
            }
            isLoading = false
        }
    }

    fun loadMembersSummary(orgId: String, startDate: String? = null, endDate: String? = null) {
        viewModelScope.launch {
            isLoading = true
            if (startDate != null && endDate != null) {
                // Use the new RPC that computes absences dynamically
                when (val result = repository.getAttendanceReport(orgId, startDate, endDate)) {
                    is Result.Success -> {
                        val grouped = result.data.groupBy { it.userId }
                        membersSummary = grouped.entries.map { (userId, rows) ->
                            val firstRow = rows.first()
                            MemberAttendanceSummary(
                                member = OrgMember(
                                    id = firstRow.memberId,
                                    userId = userId,
                                    organizationId = orgId,
                                    role = runCatching { UserRole.valueOf(firstRow.role.uppercase()) }.getOrDefault(UserRole.MEMBER),
                                    isActive = true,
                                    profile = User(
                                        id = userId,
                                        email = firstRow.email,
                                        firstName = firstRow.firstName,
                                        lastName = firstRow.lastName
                                    )
                                ),
                                summary = AttendanceSummary(
                                    total   = rows.size,
                                    present = rows.count { it.status == AttendanceStatus.PRESENT },
                                    absent  = rows.count { it.status == AttendanceStatus.ABSENT },
                                    late    = rows.count { it.status == AttendanceStatus.LATE },
                                    earlyDeparture = rows.count { it.status == AttendanceStatus.EARLY_DEPARTURE },
                                    onLeave = rows.count { it.status == AttendanceStatus.ON_LEAVE },
                                    remote  = rows.count { it.status == AttendanceStatus.REMOTE }
                                ),
                                records = rows.map { row ->
                                    AttendanceRecord(
                                        userId = row.userId,
                                        organizationId = orgId,
                                        date = row.date,
                                        checkInTime = row.checkInTime,
                                        checkOutTime = row.checkOutTime,
                                        status = row.status,
                                        isLate = row.isLate,
                                        isRemote = row.isRemote,
                                        lateMinutes = row.lateMinutes
                                    )
                                }
                            )
                        }
                    }
                    is Result.Error -> showError(result.message)
                }
            } else {
                when (val result = repository.getOrgAttendance(orgId, startDate, endDate)) {
                    is Result.Success -> {
                        val grouped = result.data.groupBy { it.userId }
                        membersSummary = grouped.entries.map { (_, records) ->
                            MemberAttendanceSummary(
                                member = OrgMember(userId = records.first().userId, organizationId = orgId),
                                summary = AttendanceSummary(total = records.size),
                                records = records
                            )
                        }
                    }
                    is Result.Error -> showError(result.message)
                }
            }
            isLoading = false
        }
    }

    fun applyFilter(orgId: String, filter: String) {
        selectedFilter = filter
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val (start, end) = when (filter) {
            "Today" -> {
                val today = fmt.format(cal.time)
                Pair(today, today)
            }
            "This Week" -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val s = fmt.format(cal.time)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek + 6)
                Pair(s, fmt.format(cal.time))
            }
            "This Month" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val s = fmt.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(s, fmt.format(cal.time))
            }
            "Last Month" -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val s = fmt.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(s, fmt.format(cal.time))
            }
            else -> Pair(null, null)
        }
        loadOrgAttendance(orgId, start, end)
        loadSummary(orgId, start, end)
        loadMembersSummary(orgId, start, end)
        loadMemberProfiles(orgId)
    }

    fun loadMemberProfiles(orgId: String) {
        viewModelScope.launch {
            when (val members = orgRepository.getMembers(orgId)) {
                is Result.Success -> {
                    val ids = members.data.map { it.userId }
                    when (val result = orgRepository.getProfilesByIds(ids)) {
                        is Result.Success -> userProfiles = result.data.associateBy { it.id }
                        is Result.Error   -> { /* silent — table will fall back to user_id */ }
                    }
                }
                is Result.Error -> { /* silent */ }
            }
        }
    }

    /**
     * Subscribe to realtime postgres_changes on attendance_records filtered by
     * orgId. The filter ensures the subscription only receives changes for the
     * admin's own organization — not cross-org data leaks.
     * Refreshes [orgAttendance] on every INSERT / UPDATE / DELETE.
     */
    fun subscribeRealtime(orgId: String, onChange: () -> Unit) {
        if (subscribedOrgId == orgId && realtimeChannel != null) return
        unsubscribeRealtime()
        subscribedOrgId = orgId

        viewModelScope.launch {
            val ch = client.realtime.channel("admin-attendance-$orgId")
            realtimeChannel = ch
            val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "attendance_records"
                filter("organization_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, orgId)
            }
            realtimeJob = flow.onEach { onChange() }.launchIn(viewModelScope)
            ch.subscribe()
        }
    }

    fun unsubscribeRealtime() {
        realtimeJob?.cancel(); realtimeJob = null
        val ch = realtimeChannel
        realtimeChannel = null
        subscribedOrgId = null
        if (ch != null) {
            viewModelScope.launch { runCatching { ch.unsubscribe() } }
        }
    }

    override fun onCleared() {
        unsubscribeRealtime()
        super.onCleared()
    }

    fun dismissError() { showError = false; errorMessage = null }
    private fun showError(msg: String) { errorMessage = msg; showError = true }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AdminViewModel(AttendanceRepository()) as T
    }
}
