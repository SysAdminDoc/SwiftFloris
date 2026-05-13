package dev.patrickgold.florisboard.ime.clipboard

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
data class ClipboardHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String = "Unknown",
    val frequency: Int = 1,
) {
    fun getFormattedTime(): String {
        // SimpleDateFormat is not thread-safe; create a fresh instance per call.
        return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    fun getPreview(): String {
        val maxLength = 40
        return if (text.length > maxLength) "${text.substring(0, maxLength)}…" else text
    }
}

/**
 * Encrypted, in-process clipboard history store.
 *
 * Storage strategy:
 * - `EncryptedSharedPreferences` (AES-256 GCM values, AES-256 SIV keys) backed by a
 *   Keystore-bound master key.
 * - History is serialized via `kotlinx.serialization` to guarantee round-trip safety
 *   for control characters, quotes, backslashes, and non-ASCII text. The previous
 *   hand-rolled JSON parser silently corrupted newlines, tabs, and carriage returns
 *   (they became literal `n`/`t`/`r` characters on read-back).
 * - A `Json { ignoreUnknownKeys = true; encodeDefaults = true }` configuration keeps
 *   the format forward- and backward-compatible across schema additions.
 *
 * Concurrency:
 * - All mutating operations (`addToHistory`, `removeFromHistory`, `clearHistory`)
 *   hold a coarse-grained intrinsic lock so concurrent producers cannot lose entries.
 *   The previous read-modify-write sequence had no synchronization, which meant
 *   two near-simultaneous copies could each clobber the other's update.
 *
 * Resilience:
 * - Master-key construction and the encrypted-prefs handle are built lazily and
 *   wrapped in a fallback that returns an in-memory store when the Android Keystore
 *   is in a corrupted state (commonly seen after factory-resets on some OEM ROMs).
 *   This prevents the entire IME from refusing to instantiate.
 */
class ClipboardHistoryManager(private val context: Context) {
    companion object {
        private const val TAG = "ClipHistoryManager"
        private const val MAX_HISTORY_SIZE = 50
        private const val PREFS_FILE_NAME = "floris_clipboard_history"
        private const val CLIPBOARD_HISTORY_KEY = "clipboard_history"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = false
        }
        private val listSerializer = ListSerializer(ClipboardHistoryItem.serializer())
    }

    private val writeLock = Any()

    // In-memory fallback used when EncryptedSharedPreferences cannot be initialised.
    private var inMemoryHistoryJson: String = "[]"

    private val encryptedPrefs: SharedPreferences? = createEncryptedPrefs()

    private fun createEncryptedPrefs(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            // Keystore corruption or unavailable hardware-backed keystore. Fall back to
            // a non-persistent in-memory store rather than taking the whole IME down.
            Log.w(TAG, "Falling back to in-memory clipboard history (encrypted prefs unavailable): ${t.message}")
            null
        }
    }

    fun addToHistory(text: String, appName: String = "Unknown"): ClipboardHistoryItem? {
        if (text.isEmpty()) return null
        synchronized(writeLock) {
            val historyList = readHistoryLocked().toMutableList()
            val existingItem = historyList.find { it.text == text }
            val result: ClipboardHistoryItem
            if (existingItem != null) {
                historyList.remove(existingItem)
                result = existingItem.copy(
                    frequency = existingItem.frequency + 1,
                    timestamp = System.currentTimeMillis(),
                )
                historyList.add(0, result)
            } else {
                result = ClipboardHistoryItem(text = text, appName = appName)
                historyList.add(0, result)
            }
            val trimmed = if (historyList.size > MAX_HISTORY_SIZE) {
                historyList.subList(0, MAX_HISTORY_SIZE).toList()
            } else {
                historyList.toList()
            }
            writeHistoryLocked(trimmed)
            return result
        }
    }

    fun getHistory(): List<ClipboardHistoryItem> {
        return synchronized(writeLock) { readHistoryLocked() }
    }

    fun removeFromHistory(itemId: String) {
        synchronized(writeLock) {
            val history = readHistoryLocked().filterNot { it.id == itemId }
            writeHistoryLocked(history)
        }
    }

    fun clearHistory() {
        synchronized(writeLock) {
            writeHistoryLocked(emptyList())
        }
    }

    fun getRecentItems(limit: Int = 10): List<ClipboardHistoryItem> {
        if (limit <= 0) return emptyList()
        return getHistory().take(limit)
    }

    private fun readHistoryLocked(): List<ClipboardHistoryItem> {
        val raw = encryptedPrefs?.getString(CLIPBOARD_HISTORY_KEY, null) ?: inMemoryHistoryJson
        if (raw.isBlank() || raw == "[]") return emptyList()
        return try {
            json.decodeFromString(listSerializer, raw)
        } catch (e: SerializationException) {
            // Stored payload is corrupt or pre-dates the new format. Discard it rather
            // than crashing on every read; the next mutation will overwrite cleanly.
            Log.w(TAG, "Discarding unparseable clipboard history payload: ${e.message}")
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Discarding malformed clipboard history payload: ${e.message}")
            emptyList()
        }
    }

    private fun writeHistoryLocked(items: List<ClipboardHistoryItem>) {
        val payload = json.encodeToString(listSerializer, items)
        val prefs = encryptedPrefs
        if (prefs != null) {
            prefs.edit { putString(CLIPBOARD_HISTORY_KEY, payload) }
        } else {
            inMemoryHistoryJson = payload
        }
    }
}
