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

package dev.patrickgold.florisboard.ime.dictionary

import java.io.File

/**
 * Words typed once that the keyboard didn't know yet, waiting for a second sighting before
 * [DictionaryManager.learnWord] learns them. A typo that slips through once would otherwise be
 * learned at the top of the frequency scale and offered as a completion from then on.
 *
 * Keys are `languageTag<TAB>word` (lowercase word), kept in insertion order and capped at
 * [maxEntries], dropping the oldest. The list is plain text in [file], like the personal n-gram
 * stores, and loads on first use. Callers persist changes with [save], off the main thread.
 */
internal class PendingLearnedWords(
    private val file: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val lock = Any()
    private val writeLock = Any()
    private var entries: LinkedHashSet<String>? = null

    /**
     * Notes a sighting of [word] in [languageTag]. Returns `true` when it was already pending,
     * which takes it off the list (the caller learns it now), and `false` when this is the first
     * sighting, which adds it.
     */
    fun recordSighting(word: String, languageTag: String): Boolean {
        val key = key(word, languageTag) ?: return false
        synchronized(lock) {
            val set = loadedLocked()
            if (set.remove(key)) return true
            set.add(key)
            trimLocked(set)
            return false
        }
    }

    /**
     * Puts [word] on the list for [languageTag] unless it's there already, without ever taking
     * it off. Returns whether it was added.
     */
    fun addSighting(word: String, languageTag: String): Boolean {
        val key = key(word, languageTag) ?: return false
        synchronized(lock) {
            val set = loadedLocked()
            if (!set.add(key)) return false
            trimLocked(set)
            return true
        }
    }

    /**
     * Decides whether [DictionaryManager.learnWord] learns [word] now. With [requireRepeat], a word
     * that isn't [alreadyLearned] waits for its second sighting. Every other learn takes the word
     * off the list, so a picked word doesn't leave a stale sighting behind.
     */
    fun admit(word: String, languageTag: String, requireRepeat: Boolean, alreadyLearned: Boolean): Admission {
        if (requireRepeat && !alreadyLearned) {
            val seenBefore = recordSighting(word, languageTag)
            return Admission(learnNow = seenBefore, listChanged = true)
        }
        return Admission(learnNow = true, listChanged = remove(word, languageTag))
    }

    /** What [admit] decided, and whether the list needs saving. */
    data class Admission(val learnNow: Boolean, val listChanged: Boolean)

    /** Takes [word] off the list for [languageTag]. Returns whether it was there. */
    fun remove(word: String, languageTag: String): Boolean {
        val key = key(word, languageTag) ?: return false
        synchronized(lock) {
            return loadedLocked().remove(key)
        }
    }

    fun contains(word: String, languageTag: String): Boolean {
        val key = key(word, languageTag) ?: return false
        synchronized(lock) {
            return key in loadedLocked()
        }
    }

    fun size(): Int = synchronized(lock) { loadedLocked().size }

    /** Empties the list in memory. Call [save] or [deleteFile] to make it stick. */
    fun clear() {
        synchronized(lock) {
            entries = LinkedHashSet()
        }
    }

    /** Loads the list now so the first sighting on the main thread doesn't read the file. */
    fun preload() {
        synchronized(lock) { loadedLocked() }
    }

    /**
     * Writes the current list to [file]. Saves are serialized and each takes its snapshot
     * inside the write lock, so the last save to finish always writes the newest list.
     */
    fun save(): Boolean {
        synchronized(writeLock) {
            val snapshot = synchronized(lock) { loadedLocked().toList() }
            return PersonalNgramPersistence.atomicReplace(file) { writer ->
                for (key in snapshot) {
                    writer.write(key)
                    writer.write("\n")
                }
            }
        }
    }

    /**
     * Empties the list and deletes [file], plus the temporary copy a save killed mid-write leaves
     * behind. Returns whether both are gone.
     */
    fun deleteFile(): Boolean {
        synchronized(writeLock) {
            clear()
            val tmp = File(file.parentFile, file.name + ".tmp")
            val tmpGone = !tmp.exists() || tmp.delete()
            return (!file.exists() || file.delete()) && tmpGone
        }
    }

    private fun loadedLocked(): LinkedHashSet<String> {
        entries?.let { return it }
        val loaded = LinkedHashSet<String>()
        val lines = runCatching { if (file.isFile) file.readLines(Charsets.UTF_8) else emptyList() }
            .getOrDefault(emptyList())
        for (line in lines) {
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.length - 1) continue
            key(line.substring(tab + 1), line.substring(0, tab))?.let { loaded.add(it) }
        }
        trimLocked(loaded)
        entries = loaded
        return loaded
    }

    private fun trimLocked(set: LinkedHashSet<String>) {
        while (set.size > maxEntries) {
            val oldest = set.iterator()
            oldest.next()
            oldest.remove()
        }
    }

    private fun key(word: String, languageTag: String): String? {
        val normalizedWord = word.trim().lowercase()
        val tag = languageTag.trim()
        if (normalizedWord.isEmpty() || tag.isEmpty()) return null
        if (normalizedWord.any { it == '\t' || it == '\n' || it == '\r' }) return null
        if (tag.any { it == '\t' || it == '\n' || it == '\r' }) return null
        return "$tag\t$normalizedWord"
    }

    companion object {
        const val FILE_NAME = "learn_pending_words.tsv"
        const val DEFAULT_MAX_ENTRIES = 1000
    }
}
