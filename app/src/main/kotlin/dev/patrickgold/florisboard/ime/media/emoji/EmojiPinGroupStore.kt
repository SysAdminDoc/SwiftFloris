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

package dev.patrickgold.florisboard.ime.media.emoji

import android.content.Context
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.devtools.flogError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference

/**
 * ROADMAP §7 Next-9.4 — pin emoji together for quick recall.
 *
 * Users frequently send the same *combination* of emoji together
 * (🎂🎉🎁 for a birthday, 🥲😭❤️ for a tearful goodbye). The pin-together
 * feature lets the user name a group and pin a list of emoji to it,
 * surfaced in a dedicated "Pinned groups" row in the emoji palette.
 *
 * Storage mirrors [CustomEmojiTagStore] — single JSON file under
 * `filesDir/emoji_pin_groups.json`, atomic-rename writes, in-memory
 * cache read once on first access. Backups: included in
 * device-transfer (carries the user's groups phone-to-phone), excluded
 * from cloud backup (§1 no-network — group data is local-only).
 *
 * Caps: 32 groups total, 12 emoji per group, 32-char group-name
 * length. Keeps the file under ~64 KB worst-case.
 */
class EmojiPinGroupStore private constructor(
    private val storageFile: File,
    private val moveFile: (File, File) -> Unit = ::moveReplacing,
) {

    private val cache = AtomicReference<Map<String, List<String>>>(emptyMap())
    private val writeLock = Any()

    /** Lookup the emoji list pinned under [groupName]. */
    fun emojisFor(groupName: String): List<String> =
        cache.get()[groupName.trim()] ?: emptyList()

    /** All current group names in stable creation order. */
    fun groupNames(): List<String> = cache.get().keys.toList()

    /** All groups + their emoji as one read-only snapshot. */
    fun snapshot(): Map<String, List<String>> = cache.get()

    /**
     * Create [groupName] (if not already present) and append [emojiValue]
     * to its list. No-op when the group cap (32) is reached, when the
     * group's emoji cap (12) is reached, when [emojiValue] is already
     * in the group, or when [groupName] is blank.
     */
    fun pinEmojiToGroup(groupName: String, emojiValue: String): List<String> =
        synchronized(writeLock) {
            val normalised = groupName.trim().take(MaxGroupNameLength)
            if (normalised.isBlank() || emojiValue.isBlank()) return emojisFor(groupName)
            val current = cache.get().toMutableMap()
            val existing = current[normalised] ?: emptyList()
            if (existing.isEmpty() && current.size >= MaxGroups) return emptyList()
            if (existing.size >= MaxEmojisPerGroup) return existing
            if (emojiValue in existing) return existing
            val updated = existing + emojiValue
            current[normalised] = updated
            cache.set(current.toMap())
            flush(current)
            return updated
        }

    /**
     * Remove [emojiValue] from [groupName]. Empties the group entirely
     * (removes the group) when its emoji list becomes empty.
     */
    fun unpinEmojiFromGroup(groupName: String, emojiValue: String): List<String> =
        synchronized(writeLock) {
            val normalised = groupName.trim()
            val current = cache.get().toMutableMap()
            val existing = current[normalised] ?: return emptyList()
            val updated = existing - emojiValue
            if (updated.isEmpty()) current.remove(normalised) else current[normalised] = updated
            cache.set(current.toMap())
            flush(current)
            return updated
        }

    /** Drop one group entirely. */
    fun removeGroup(groupName: String): Boolean = synchronized(writeLock) {
        val current = cache.get().toMutableMap()
        val removed = current.remove(groupName.trim()) != null
        if (removed) {
            cache.set(current.toMap())
            flush(current)
        }
        return removed
    }

    /** Drop every pin group. Used by Settings → Reset typing learning. */
    fun clearAll() = synchronized(writeLock) {
        cache.set(emptyMap())
        flush(emptyMap())
    }

    /** Reloads the on-disk store after a portable backup restore. */
    fun reload() = synchronized(writeLock) {
        load()
    }

    private fun flush(map: Map<String, List<String>>) {
        try {
            storageFile.parentFile?.mkdirs()
            val tmp = File(storageFile.parentFile, storageFile.name + ".tmp")
            tmp.writeText(JsonConfig.encodeToString(StoreFile(map)))
            moveFile(tmp, storageFile)
        } catch (e: Throwable) {
            File(storageFile.parentFile, storageFile.name + ".tmp").delete()
            flogError { "EmojiPinGroupStore.flush failed: $e" }
        }
    }

    private fun load() {
        try {
            if (!storageFile.exists()) {
                cache.set(emptyMap())
                return
            }
            val decoded = JsonConfig.decodeFromString<StoreFile>(storageFile.readText())
            cache.set(decoded.groups)
            flogDebug { "EmojiPinGroupStore.load loaded ${decoded.groups.size} groups" }
        } catch (e: Throwable) {
            flogError { "EmojiPinGroupStore.load failed: $e — starting fresh" }
            cache.set(emptyMap())
        }
    }

    @Serializable
    private data class StoreFile(val groups: Map<String, List<String>>)

    companion object {
        const val MaxGroups: Int = 32
        const val MaxEmojisPerGroup: Int = 12
        const val MaxGroupNameLength: Int = 32

        @Volatile
        private var instance: EmojiPinGroupStore? = null

        private val initLock = Any()

        fun get(context: Context): EmojiPinGroupStore {
            instance?.let { return it }
            return synchronized(initLock) {
                instance ?: build(context).also { instance = it }
            }
        }

        private fun build(context: Context): EmojiPinGroupStore {
            val file = File(context.applicationContext.filesDir, "emoji_pin_groups.json")
            return EmojiPinGroupStore(file).also { it.load() }
        }

        private val JsonConfig = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Test-only constructor for in-memory + custom-file use. */
        internal fun forStorageFile(
            file: File,
            moveFile: (File, File) -> Unit = ::moveReplacing,
        ): EmojiPinGroupStore = EmojiPinGroupStore(file, moveFile).also { it.load() }
    }
}

private fun moveReplacing(stagedFile: File, targetFile: File) {
    try {
        Files.move(
            stagedFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            stagedFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
