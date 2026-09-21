package com.rombamukiyam.vault.core.security

import com.rombamukiyam.vault.core.common.SafeLog
import com.rombamukiyam.vault.domain.model.ReminderType
import com.rombamukiyam.vault.domain.model.ScheduledReminder
import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ScheduleFile(val version: Int = 1, val writtenAt: Long, val entries: List<Entry>) {
    @Serializable
    data class Entry(val dueAt: Long, val type: String, val label: String? = null)
}

/**
 * Publishes the upcoming-reminder schedule to a small plaintext JSON file in private storage so the periodic
 * worker can raise notifications without the Vault Key. By design it contains ONLY due dates and types; item
 * names are added only when the user explicitly chooses less-private notifications.
 */
class ReminderSchedulePublisher(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file get() = File(dir, FILE_NAME)

    fun publish(entries: List<ScheduledReminder>, includeLabels: Boolean, now: Long) {
        try {
            if (!dir.exists()) dir.mkdirs()
            val payload = ScheduleFile(
                writtenAt = now,
                entries = entries.sortedBy { it.dueAt }.take(MAX_ENTRIES).map { ScheduleFile.Entry(it.dueAt, it.type.name, if (includeLabels) it.label else null) },
            )
            val tmp = File(dir, "$FILE_NAME.tmp")
            FileOutputStream(tmp).use { out -> out.write(json.encodeToString(ScheduleFile.serializer(), payload).toByteArray()); out.fd.sync() }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        } catch (e: Exception) {
            SafeLog.w(TAG, "schedule publish failed", e)
        }
    }

    fun read(): List<ScheduledReminder> = try {
        if (!file.exists()) emptyList() else json.decodeFromString(ScheduleFile.serializer(), file.readText()).entries.map {
            ScheduledReminder(it.dueAt, runCatching { ReminderType.valueOf(it.type) }.getOrDefault(ReminderType.CUSTOM), it.label)
        }
    } catch (e: Exception) {
        SafeLog.w(TAG, "schedule read failed", e)
        emptyList()
    }

    fun clear() {
        file.delete()
    }

    companion object {
        private const val TAG = "Schedule"
        const val FILE_NAME = "reminder_schedule.json"
        const val MAX_ENTRIES = 500
    }
}
