/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.settings.advanced

import android.content.Context
import dev.patrickgold.florisboard.ime.security.TinkStringPreferenceCrypto
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import dev.patrickgold.florisboard.lib.util.summarizeForUser

/** User-selectable cadence supported by Android's persisted periodic work. */
internal enum class ScheduledBackupCadence(
    val id: String,
    val repeatHours: Long,
) {
    DAILY("daily", 24L),
    WEEKLY("weekly", 7L * 24L),
    ;

    companion object {
        fun fromId(id: String?): ScheduledBackupCadence =
            entries.firstOrNull { it.id == id } ?: DAILY
    }
}

/** Pure naming and retention rules for scheduled archives. */
internal object ScheduledBackupPolicy {
    const val ArchivePrefix = "swiftfloris_scheduled_backup_"
    const val ArchiveExtension = ".sfbak"
    const val DefaultRetentionCount = 3
    val RetentionOptions = listOf(1, 3, 5, 10)

    private val archiveNamePattern = Regex(
        "^${Regex.escape(ArchivePrefix)}(\\d+)_(\\d+)_([0-9a-f-]{36})${Regex.escape(ArchiveExtension)}\\z",
    )

    fun archiveName(versionCode: Int, timestamp: Long, id: UUID = UUID.randomUUID()): String {
        require(versionCode >= 0) { "Backup version code must not be negative." }
        require(timestamp >= 0L) { "Backup timestamp must not be negative." }
        return "$ArchivePrefix${versionCode}_${timestamp}_${id}$ArchiveExtension"
    }

    fun timestampFromArchiveName(name: String): Long? {
        val match = archiveNamePattern.matchEntire(name) ?: return null
        return match.groupValues[2].toLongOrNull()
    }

    fun isManagedArchive(name: String): Boolean = timestampFromArchiveName(name) != null

    fun normalizeRetention(value: Int): Int =
        RetentionOptions.minByOrNull { kotlin.math.abs(it - value) } ?: DefaultRetentionCount
}

/**
 * Private scheduled-backup configuration. The SAF tree URI and status are
 * ordinary local settings; the passphrase is kept in the same Tink plus
 * Android Keystore construction used by the encrypted clipboard stores.
 */
internal object ScheduledBackupStore {
    const val PREFS_FILE_NAME = "swiftfloris_scheduled_backup"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_CADENCE = "cadence"
    private const val KEY_RETENTION = "retention"
    private const val KEY_LAST_SUCCESS_AT = "last_success_at"
    private const val KEY_LAST_FAILURE_AT = "last_failure_at"
    private const val KEY_LAST_FAILURE_MESSAGE = "last_failure_message"
    private const val KEY_LAST_ARCHIVE_NAME = "last_archive_name"
    private const val KEY_PASSPHRASE = "passphrase_tink_v1"
    private const val KEYSTORE_ALIAS = "swiftfloris_scheduled_backup_passphrase_v1"

    data class Settings(
        val enabled: Boolean,
        val treeUri: String,
        val cadence: ScheduledBackupCadence,
        val retentionCount: Int,
        val hasPassphrase: Boolean,
        val lastSuccessAt: Long,
        val lastFailureAt: Long,
        val lastFailureMessage: String,
        val lastArchiveName: String,
    )

    fun load(context: Context): Settings {
        val prefs = prefs(context)
        return Settings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            treeUri = prefs.getString(KEY_TREE_URI, "").orEmpty(),
            cadence = ScheduledBackupCadence.fromId(prefs.getString(KEY_CADENCE, null)),
            retentionCount = ScheduledBackupPolicy.normalizeRetention(
                prefs.getInt(KEY_RETENTION, ScheduledBackupPolicy.DefaultRetentionCount),
            ),
            hasPassphrase = prefs.contains(KEY_PASSPHRASE),
            lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS_AT, 0L),
            lastFailureAt = prefs.getLong(KEY_LAST_FAILURE_AT, 0L),
            lastFailureMessage = prefs.getString(KEY_LAST_FAILURE_MESSAGE, "").orEmpty(),
            lastArchiveName = prefs.getString(KEY_LAST_ARCHIVE_NAME, "").orEmpty(),
        )
    }

    fun setTreeUri(context: Context, uri: String): Boolean = prefs(context).edit()
        .putString(KEY_TREE_URI, uri)
        .commit()

    fun setCadence(context: Context, cadence: ScheduledBackupCadence): Boolean = prefs(context).edit()
        .putString(KEY_CADENCE, cadence.id)
        .commit()

    fun setRetention(context: Context, retentionCount: Int): Boolean = prefs(context).edit()
        .putInt(KEY_RETENTION, ScheduledBackupPolicy.normalizeRetention(retentionCount))
        .commit()

    fun setEnabled(context: Context, enabled: Boolean): Boolean = prefs(context).edit()
        .putBoolean(KEY_ENABLED, enabled)
        .commit()

    fun savePassphrase(context: Context, passphrase: CharArray): Boolean {
        require(passphrase.isNotEmpty()) { "Scheduled backup passphrase must not be empty." }
        val bytes = String(passphrase).toByteArray(UTF_8)
        return try {
            TinkStringPreferenceCrypto.writeBytes(
                prefs = prefs(context),
                prefsFileName = PREFS_FILE_NAME,
                key = KEY_PASSPHRASE,
                keystoreAlias = KEYSTORE_ALIAS,
                value = bytes,
            )
        } finally {
            bytes.fill(0)
        }
    }

    fun readPassphrase(context: Context): CharArray? {
        val bytes = TinkStringPreferenceCrypto.readBytes(
            prefs = prefs(context),
            prefsFileName = PREFS_FILE_NAME,
            key = KEY_PASSPHRASE,
            keystoreAlias = KEYSTORE_ALIAS,
        ) ?: return null
        return try {
            String(bytes, UTF_8).toCharArray()
        } finally {
            bytes.fill(0)
        }
    }

    fun clearPassphrase(context: Context): Boolean = prefs(context).edit()
        .remove(KEY_PASSPHRASE)
        .commit()

    fun recordSuccess(context: Context, archiveName: String, timestamp: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(KEY_LAST_SUCCESS_AT, timestamp)
            .putString(KEY_LAST_ARCHIVE_NAME, archiveName)
            .remove(KEY_LAST_FAILURE_MESSAGE)
            .commit()
    }

    fun recordFailure(context: Context, error: Throwable, timestamp: Long = System.currentTimeMillis()) {
        val message = error.summarizeForUser(error::class.java.simpleName)
        prefs(context).edit()
            .putLong(KEY_LAST_FAILURE_AT, timestamp)
            .putString(KEY_LAST_FAILURE_MESSAGE, message)
            .commit()
    }

    private fun prefs(context: Context) = TinkStringPreferenceCrypto.sharedPreferences(
        context.applicationContext ?: context,
        PREFS_FILE_NAME,
    )
}
