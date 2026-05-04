package dev.patrickgold.florisboard.ime.clipboard

import android.content.Context
import android.content.ClipboardManager
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.text.SimpleDateFormat
import java.util.*

data class ClipboardHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String = "Unknown",
    val frequency: Int = 1
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getPreview(): String {
        val maxLength = 40
        return if (text.length > maxLength) "${text.substring(0, maxLength)}..." else text
    }
}

class ClipboardHistoryManager(private val context: Context) {
    private val MAX_HISTORY_SIZE = 50
    private val CLIPBOARD_HISTORY_KEY = "clipboard_history"
    private val CLIPBOARD_METADATA_KEY = "clipboard_metadata_"

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "floris_clipboard_history",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun addToHistory(text: String, appName: String = "Unknown"): ClipboardHistoryItem {
        if (text.isEmpty()) return ClipboardHistoryItem(text = "")

        val historyList = getHistory().toMutableList()

        // Check if text already exists (deduplicate)
        val existingItem = historyList.find { it.text == text }
        if (existingItem != null) {
            historyList.remove(existingItem)
            val updated = existingItem.copy(
                frequency = existingItem.frequency + 1,
                timestamp = System.currentTimeMillis()
            )
            historyList.add(0, updated)
        } else {
            val newItem = ClipboardHistoryItem(text = text, appName = appName)
            historyList.add(0, newItem)
        }

        // Keep only the most recent MAX_HISTORY_SIZE items
        while (historyList.size > MAX_HISTORY_SIZE) {
            historyList.removeAt(historyList.size - 1)
        }

        saveHistory(historyList)
        return historyList.first()
    }

    fun getHistory(): List<ClipboardHistoryItem> {
        val json = encryptedPrefs.getString(CLIPBOARD_HISTORY_KEY, "[]") ?: "[]"
        return try {
            parseJsonArray(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeFromHistory(itemId: String) {
        val history = getHistory().toMutableList()
        history.removeAll { it.id == itemId }
        saveHistory(history)
    }

    fun clearHistory() {
        encryptedPrefs.edit().remove(CLIPBOARD_HISTORY_KEY).apply()
    }

    fun getRecentItems(limit: Int = 10): List<ClipboardHistoryItem> {
        return getHistory().take(limit)
    }

    private fun saveHistory(items: List<ClipboardHistoryItem>) {
        val json = items.joinToString(",", "[", "]") { item ->
            """{"id":"${item.id}","text":"${escapeJson(item.text)}","timestamp":${item.timestamp},"appName":"${item.appName}","frequency":${item.frequency}}"""
        }
        encryptedPrefs.edit().putString(CLIPBOARD_HISTORY_KEY, json).apply()
    }

    private fun parseJsonArray(json: String): List<ClipboardHistoryItem> {
        val items = mutableListOf<ClipboardHistoryItem>()
        val trimmed = json.trim().removePrefix("[").removeSuffix("]")
        if (trimmed.isEmpty()) return items

        var depth = 0
        var current = StringBuilder()

        for (char in trimmed) {
            when {
                char == '{' -> {
                    depth++
                    current.append(char)
                }
                char == '}' -> {
                    current.append(char)
                    depth--
                    if (depth == 0) {
                        val item = parseJsonObject(current.toString())
                        if (item != null) items.add(item)
                        current = StringBuilder()
                    }
                }
                char == ',' && depth == 0 -> {
                    // Skip
                }
                else -> {
                    current.append(char)
                }
            }
        }

        return items
    }

    private fun parseJsonObject(json: String): ClipboardHistoryItem? {
        return try {
            val map = mutableMapOf<String, String>()
            val content = json.removePrefix("{").removeSuffix("}")

            var inString = false
            var escaped = false
            var key = StringBuilder()
            var value = StringBuilder()
            var parsingKey = true

            var i = 0
            while (i < content.length) {
                val char = content[i]

                when {
                    escaped -> {
                        if (parsingKey) key.append(char) else value.append(char)
                        escaped = false
                    }
                    char == '\\' -> {
                        escaped = true
                    }
                    char == '"' -> {
                        inString = !inString
                    }
                    char == ':' && !inString -> {
                        parsingKey = false
                    }
                    char == ',' && !inString -> {
                        val k = key.toString().trim().trim('"')
                        val v = value.toString().trim().trim('"')
                        if (k.isNotEmpty() && v.isNotEmpty()) {
                            map[k] = v
                        }
                        key = StringBuilder()
                        value = StringBuilder()
                        parsingKey = true
                    }
                    !parsingKey -> {
                        value.append(char)
                    }
                    parsingKey -> {
                        key.append(char)
                    }
                }
                i++
            }

            // Last item
            if (key.isNotEmpty() && value.isNotEmpty()) {
                val k = key.toString().trim().trim('"')
                val v = value.toString().trim().trim('"')
                if (k.isNotEmpty() && v.isNotEmpty()) {
                    map[k] = v
                }
            }

            ClipboardHistoryItem(
                id = map["id"] ?: UUID.randomUUID().toString(),
                text = map["text"] ?: "",
                timestamp = map["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
                appName = map["appName"] ?: "Unknown",
                frequency = map["frequency"]?.toIntOrNull() ?: 1
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
