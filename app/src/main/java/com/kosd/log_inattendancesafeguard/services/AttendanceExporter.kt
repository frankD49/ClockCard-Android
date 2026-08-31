package com.kosd.log_inattendancesafeguard.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kosd.log_inattendancesafeguard.models.AttendanceRecord
import com.kosd.log_inattendancesafeguard.models.User
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a CSV file of attendance records and shares it via FileProvider.
 *
 * The CSV is UTF-8 with a BOM so Excel opens it with correct encoding by
 * default. Microsoft Excel, Google Sheets, Numbers and most other tools
 * import CSVs natively, so a single .csv file covers the "printable
 * Excel/CSV" requirement without bundling Apache POI.
 */
object AttendanceExporter {

    private val DATE_IN  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val DATE_OUT = SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault())
    private val TIME_IN  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val TIME_OUT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val FILE_TS  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    private fun formatDate(date: String): String =
        runCatching { DATE_OUT.format(DATE_IN.parse(date)!!) }.getOrDefault(date)

    private fun formatTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            val trimmed = iso.substringBefore('.').substringBefore('+').take(19)
            TIME_OUT.format(TIME_IN.parse(trimmed)!!)
        }.getOrDefault(iso)
    }

    /** Escape a single CSV field per RFC 4180. */
    private fun csv(value: String?): String {
        val v = value ?: ""
        return if (v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r')) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else v
    }

    /**
     * Build the CSV text for the given records and member profile lookup.
     * Includes a header row and a trailing summary line.
     */
    fun buildCsv(
        records: List<AttendanceRecord>,
        profiles: Map<String, User>,
        orgName: String,
        filterLabel: String,
        startDate: String?,
        endDate: String?
    ): String {
        val sb = StringBuilder()
        // BOM for Excel UTF-8 detection
        sb.append('\uFEFF')

        // Title block (purely informational — Excel will show it as the first rows)
        sb.append("Attendance Report\n")
        sb.append("Organization,").append(csv(orgName)).append('\n')
        sb.append("Period,").append(csv(filterLabel)).append('\n')
        if (startDate != null && endDate != null) {
            sb.append("Date range,").append(csv("$startDate to $endDate")).append('\n')
        }
        sb.append("Generated,").append(csv(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))).append('\n')
        sb.append("Total records,").append(records.size).append('\n')
        sb.append('\n')

        // Header
        sb.append("Email,Name,Date,Day,Check-In,Check-Out,Status,Late,Remote,Late Minutes,Notes\n")

        records.forEach { r ->
            val profile  = profiles[r.userId]
            val email    = profile?.email ?: r.userId
            val name     = listOfNotNull(profile?.firstName, profile?.lastName)
                .joinToString(" ").ifBlank { "—" }
            val day      = formatDate(r.date)
            val notes    = listOfNotNull(r.checkInNotes, r.checkOutNotes)
                .joinToString(" | ")

            sb.append(csv(email)).append(',')
            sb.append(csv(name)).append(',')
            sb.append(csv(r.date)).append(',')
            sb.append(csv(day)).append(',')
            sb.append(csv(formatTime(r.checkInTime))).append(',')
            sb.append(csv(formatTime(r.checkOutTime))).append(',')
            sb.append(csv(r.status.name)).append(',')
            sb.append(if (r.isLate) "Yes" else "No").append(',')
            sb.append(if (r.isRemote) "Yes" else "No").append(',')
            sb.append(r.lateMinutes?.toString() ?: "").append(',')
            sb.append(csv(notes)).append('\n')
        }
        return sb.toString()
    }

    /**
     * Writes a CSV to the app's cache and returns a content:// Uri suitable
     * for sharing through Intent.ACTION_SEND.
     */
    fun writeCsvToCache(context: Context, csv: String, filename: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, filename)
        file.writeText(csv, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /** Convenience: build CSV and produce a share Intent ready to launch. */
    fun shareIntent(
        context: Context,
        records: List<AttendanceRecord>,
        profiles: Map<String, User>,
        orgName: String,
        filterLabel: String,
        startDate: String? = null,
        endDate: String? = null
    ): Intent {
        val csv = buildCsv(records, profiles, orgName, filterLabel, startDate, endDate)
        val safeOrg = orgName.replace("[^A-Za-z0-9_-]".toRegex(), "_").take(40)
        val safeFilter = filterLabel.replace(" ", "_")
        val filename = "attendance_${safeOrg}_${safeFilter}_${FILE_TS.format(Date())}.csv"
        val uri = writeCsvToCache(context, csv, filename)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Attendance Report — $orgName ($filterLabel)")
            putExtra(Intent.EXTRA_TEXT,
                "Attendance report for $orgName, period: $filterLabel (${records.size} records).")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share attendance report")
    }
}
