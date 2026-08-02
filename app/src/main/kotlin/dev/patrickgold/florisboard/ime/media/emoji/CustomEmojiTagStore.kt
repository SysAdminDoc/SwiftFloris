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
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * ROADMAP §7 Next-9.4 — custom user emoji tags.
 *
 * Users can attach their own keywords to any emoji via Settings → Media → Emoji
 * → Tag emoji (long-press an emoji in the palette and pick "Add tag"). The
 * tags participate in both [EmojiSearch] (search-by-tag) and the
 * [EmojiSuggestionProvider] in-strip predict-by-tag flow, so e.g. tagging
 * 🦋 with "freedom" lets typing `freedom` surface 🦋 in the suggestion
 * strip even though the bundled Emojibase keyword set doesn't include it.
 *
 * Storage: a single JSON file at `<filesDir>/custom_emoji_tags.json` of the
 * form `{ "🦋": ["freedom", "transform"], "🎯": ["focus", "goal"] }`. The
 * file is read once on first access (cached in-memory) and rewritten in
 * full on any tag change. Backups: included in device-transfer (so a
 * phone-to-phone migration carries custom tags) but excluded from cloud
 * backup (per the §1 no-network philosophy — tag data is local-only).
 *
 * Caps: 16 tags per emoji, 5,000 tagged emoji total, 32-char per-tag length
 * — keeps the file under ~256 KB in pathological cases.
 */
class CustomEmojiTagStore private constructor(
    private val storageFile: File,
) {

    private val cache = AtomicReference<Map<String, List<String>>>(emptyMap())
    private val writeLock = Any()

    /**
     * Return the user-defined tags for [emojiValue]. Empty list when none.
     * Cheap — hash lookup in the in-memory cache. Tags are returned
     * lowercased and trimmed (the store normalises on write).
     */
    fun tagsFor(emojiValue: String): List<String> {
        return cache.get()[emojiValue] ?: emptyList()
    }

    /** Return every emoji value that carries at least one user tag. */
    fun taggedEmojiValues(): Set<String> = cache.get().keys

    /** Return all user tags as a stable read-only snapshot for management UI. */
    fun snapshot(): Map<String, List<String>> = cache.get()

    /**
     * Add [tag] to [emojiValue]. No-op if the emoji is already at the
     * per-emoji cap (16) or the total tagged-emoji cap (5,000), or if the
     * tag is empty / too long after normalisation. Returns the new tag
     * list for that emoji (including a possibly-rejected addition).
     */
    fun addTag(emojiValue: String, tag: String): List<String> = synchronized(writeLock) {
        if (emojiValue.isBlank()) return tagsFor(emojiValue)
        val normalised = tag.trim().lowercase(Locale.ROOT).take(MaxTagLength)
        if (normalised.isBlank()) return tagsFor(emojiValue)
        val current = cache.get().toMutableMap()
        val existing = current[emojiValue] ?: emptyList()
        if (normalised in existing) return existing
        if (existing.size >= MaxTagsPerEmoji) return existing
        if (existing.isEmpty() && current.size >= MaxTaggedEmoji) return existing
        val updated = existing + normalised
        current[emojiValue] = updated
        cache.set(current.toMap())
        flush(current)
        return updated
    }

    /**
     * Remove [tag] from [emojiValue]. Removes the entire emoji entry from
     * the store when its tag list becomes empty. Returns the new list.
     */
    fun removeTag(emojiValue: String, tag: String): List<String> = synchronized(writeLock) {
        val normalised = tag.trim().lowercase(Locale.ROOT)
        val current = cache.get().toMutableMap()
        val existing = current[emojiValue] ?: return emptyList()
        val updated = existing - normalised
        if (updated.isEmpty()) current.remove(emojiValue) else current[emojiValue] = updated
        cache.set(current.toMap())
        flush(current)
        return updated
    }

    /** Drop every user tag. Used by explicit data reset and restore flows. */
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
            if (!tmp.renameTo(storageFile)) {
                // JVM hosts on Windows refuse rename-over-existing. NIO keeps the
                // replacement staged and never hand-copies over the previous file.
                try {
                    Files.move(
                        tmp.toPath(),
                        storageFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        tmp.toPath(),
                        storageFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            }
        } catch (e: Throwable) {
            flogError { "CustomEmojiTagStore.flush failed: $e" }
        }
    }

    private fun load() {
        try {
            if (!storageFile.exists()) {
                cache.set(emptyMap())
                return
            }
            val decoded = JsonConfig.decodeFromString<StoreFile>(storageFile.readText())
            cache.set(decoded.tags)
            flogDebug { "CustomEmojiTagStore.load loaded ${decoded.tags.size} entries" }
        } catch (e: Throwable) {
            flogError { "CustomEmojiTagStore.load failed: $e — starting fresh" }
            cache.set(emptyMap())
        }
    }

    /** JSON container — keeps the on-disk layout extensible (adding a
     *  "version" field later doesn't break older readers). */
    @Serializable
    private data class StoreFile(val tags: Map<String, List<String>>)

    companion object {
        const val MaxTagsPerEmoji: Int = 16
        const val MaxTaggedEmoji: Int = 5_000
        const val MaxTagLength: Int = 32

        @Volatile
        private var instance: CustomEmojiTagStore? = null

        private val initLock = Any()

        fun get(context: Context): CustomEmojiTagStore {
            instance?.let { return it }
            return synchronized(initLock) {
                instance ?: build(context).also { instance = it }
            }
        }

        private fun build(context: Context): CustomEmojiTagStore {
            val file = File(context.applicationContext.filesDir, "custom_emoji_tags.json")
            return CustomEmojiTagStore(file).also { it.load() }
        }

        /** Test-only constructor for in-memory + custom-file use. */
        internal fun forStorageFile(file: File): CustomEmojiTagStore =
            CustomEmojiTagStore(file).also { it.load() }

        private val JsonConfig = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
