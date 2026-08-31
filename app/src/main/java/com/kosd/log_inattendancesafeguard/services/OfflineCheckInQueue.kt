package com.kosd.log_inattendancesafeguard.services

import android.content.Context
import com.kosd.log_inattendancesafeguard.repository.EventRepository
import com.kosd.log_inattendancesafeguard.repository.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists queued event check-ins to a JSON file so they survive process death
 * and can be replayed once connectivity is restored. Designed for the kiosk /
 * walk-in scenario where the device may briefly lose network at a large event.
 */
class OfflineCheckInQueue(private val context: Context) {

    @Serializable
    data class QueuedCheckIn(
        val eventId: String,
        val token: String? = null,
        val fullName: String,
        val email: String? = null,
        val userId: String? = null,
        val guestId: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val checkInMethod: String = "kiosk",
        val queuedAt: Long = System.currentTimeMillis()
    )

    @Serializable
    private data class QueueFile(val items: List<QueuedCheckIn> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val queueFile: File
        get() = File(context.filesDir, "offline_check_in_queue.json")

    /** Number of items currently waiting in the queue. */
    val queueCount: Int
        get() = readQueue().items.size

    /** Add a check-in to the persistent queue. */
    fun enqueue(
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
        val current = readQueue()
        val updated = current.items + QueuedCheckIn(
            eventId = eventId,
            token = token,
            fullName = fullName,
            email = email,
            userId = userId,
            guestId = guestId,
            latitude = latitude,
            longitude = longitude,
            checkInMethod = checkInMethod
        )
        writeQueue(QueueFile(updated))
    }

    /**
     * Attempt to submit every queued check-in. Items are removed on success;
     * failures remain in the queue for the next attempt.
     *
     * Returns the number of items successfully processed.
     */
    suspend fun processQueue(repository: EventRepository): Int = withContext(Dispatchers.IO) {
        val current = readQueue()
        if (current.items.isEmpty()) return@withContext 0

        val remaining = mutableListOf<QueuedCheckIn>()
        var processed = 0
        for (item in current.items) {
            val result = repository.eventCheckIn(
                eventId = item.eventId,
                token = item.token,
                fullName = item.fullName,
                email = item.email,
                userId = item.userId,
                guestId = item.guestId,
                latitude = item.latitude,
                longitude = item.longitude,
                checkInMethod = item.checkInMethod
            )
            if (result is Result.Success && result.data.success) {
                processed++
            } else {
                // Keep failed items for retry; preserve original queue order.
                remaining.add(item)
            }
        }
        writeQueue(QueueFile(remaining))
        processed
    }

    /** Remove all queued items. */
    fun clear() {
        writeQueue(QueueFile(emptyList()))
    }

    private fun readQueue(): QueueFile {
        return try {
            val file = queueFile
            if (file.exists()) {
                json.decodeFromString(QueueFile.serializer(), file.readText())
            } else {
                QueueFile()
            }
        } catch (_: Exception) {
            QueueFile()
        }
    }

    private fun writeQueue(queue: QueueFile) {
        try {
            queueFile.writeText(json.encodeToString(QueueFile.serializer(), queue))
        } catch (_: Exception) {
            // Best-effort persistence; failures are non-fatal.
        }
    }
}
