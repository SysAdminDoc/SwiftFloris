/*
 * Copyright (C) 2026 The SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.dictionary

import android.content.Context
import dev.patrickgold.florisboard.lib.FlorisLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Local on-device bigram counter that powers next-word suggestions. Bigrams
 * are accumulated as the user commits words and persisted to a single TSV
 * file per locale under `<filesDir>/personal_bigrams_<localeTag>.tsv`. Format
 * is one tab-separated triple per line: `prevWord\tnextWord\tcount`.
 *
 * Storage cap: at most [MAX_PREV_WORDS] previous words per locale, [MAX_NEXT_PER_PREV]
 * next words per previous word. When the cap is reached, the lowest-count
 * entries are evicted on next save. Counts are clamped at [MAX_COUNT] so a
 * runaway pattern can't dominate the score forever.
 *
 * The store never leaves the device. There is no INTERNET permission and no
 * cloud sync. Incognito-mode commits never reach this class — that gate lives
 * in [KeyboardManager.learnIfAllowed], same as [DictionaryManager.learnWord].
 */
class PersonalBigramStore private constructor(private val context: Context) {
    companion object {
        private const val MAX_PREV_WORDS = 2000
        private const val MAX_NEXT_PER_PREV = 16
        private const val MAX_COUNT = 1000
        private const val MIN_COUNT_FOR_SUGGEST = 2
        private const val FLUSH_EVERY_N_COMMITS = 20

        @Volatile
        private var instance: PersonalBigramStore? = null

        fun get(context: Context): PersonalBigramStore {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                return PersonalBigramStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tablesByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Int>>> = HashMap()
    private val loadGuard = Mutex()
    private var pendingCommits: Int = 0

    private fun fileFor(localeTag: String): File =
        File(context.filesDir, "personal_bigrams_${localeTag.ifBlank { "default" }}.tsv")

    private fun normalize(word: String): String {
        if (word.isBlank()) return ""
        val trimmed = word.trim().trim { ch -> !ch.isLetter() && ch != '\'' && ch != '-' }
        if (trimmed.length < 2 || trimmed.length > 32) return ""
        if (trimmed.any { it.isDigit() }) return ""
        if (trimmed.none { it.isLetter() }) return ""
        return trimmed.lowercase()
    }

    private suspend fun ensureLoaded(localeTag: String): MutableMap<String, MutableMap<String, Int>> {
        tablesByLocale[localeTag]?.let { return it }
        loadGuard.withLock {
            tablesByLocale[localeTag]?.let { return it }
            val table: MutableMap<String, MutableMap<String, Int>> = HashMap()
            val f = fileFor(localeTag)
            if (f.exists() && f.length() > 0L) {
                runCatching {
                    f.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            val parts = line.split('\t')
                            if (parts.size != 3) continue
                            val prev = parts[0]
                            val next = parts[1]
                            val count = parts[2].toIntOrNull() ?: continue
                            if (prev.isBlank() || next.isBlank() || count <= 0) continue
                            val nextMap = table.getOrPut(prev) { HashMap() }
                            nextMap[next] = count.coerceAtMost(MAX_COUNT)
                        }
                    }
                }
            }
            tablesByLocale[localeTag] = table
            return table
        }
    }

    /**
     * Records the bigram (`prevWord`, `currWord`) under [locale]. No-op if either
     * word fails normalization (too short, contains digits, etc.). Triggers a
     * debounced flush every [FLUSH_EVERY_N_COMMITS] commits.
     */
    fun learn(prevWord: String, currWord: String, locale: FlorisLocale) {
        val prev = normalize(prevWord)
        val curr = normalize(currWord)
        if (prev.isEmpty() || curr.isEmpty() || prev == curr) return
        val tag = locale.languageTag()
        ioScope.launch {
            val table = ensureLoaded(tag)
            synchronized(table) {
                val nextMap = table.getOrPut(prev) { HashMap() }
                val newCount = (nextMap[curr] ?: 0) + 1
                nextMap[curr] = newCount.coerceAtMost(MAX_COUNT)
                pendingCommits += 1
            }
            if (pendingCommits >= FLUSH_EVERY_N_COMMITS) {
                flush(tag)
            }
        }
    }

    /**
     * Returns the top-[max] next-word candidates following [prevWord] in [locale],
     * sorted by count desc. Empty when there is no learned context yet.
     */
    suspend fun predict(prevWord: String, locale: FlorisLocale, max: Int): List<String> {
        if (max <= 0) return emptyList()
        val prev = normalize(prevWord)
        if (prev.isEmpty()) return emptyList()
        val table = ensureLoaded(locale.languageTag())
        val nextMap = synchronized(table) { table[prev]?.toMap() } ?: return emptyList()
        return nextMap.entries
            .asSequence()
            .filter { it.value >= MIN_COUNT_FOR_SUGGEST }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(max)
            .toList()
    }

    /**
     * Forces an immediate flush of [localeTag]'s table to disk. Called by the
     * commit-count threshold and from the IME service shutdown path.
     */
    fun flush(localeTag: String) {
        ioScope.launch {
            val table = tablesByLocale[localeTag] ?: return@launch
            val snapshot: List<Triple<String, String, Int>>
            synchronized(table) {
                pendingCommits = 0
                if (table.size > MAX_PREV_WORDS) {
                    val keepKeys = table.entries
                        .sortedByDescending { e -> e.value.values.sum() }
                        .take(MAX_PREV_WORDS)
                        .map { it.key }
                        .toSet()
                    val removeKeys = table.keys.filter { it !in keepKeys }
                    for (k in removeKeys) table.remove(k)
                }
                for ((_, nextMap) in table) {
                    if (nextMap.size > MAX_NEXT_PER_PREV) {
                        val keepKeys = nextMap.entries
                            .sortedByDescending { it.value }
                            .take(MAX_NEXT_PER_PREV)
                            .map { it.key }
                            .toSet()
                        val removeKeys = nextMap.keys.filter { it !in keepKeys }
                        for (k in removeKeys) nextMap.remove(k)
                    }
                }
                snapshot = buildList {
                    for ((prev, nextMap) in table) {
                        for ((next, count) in nextMap) {
                            add(Triple(prev, next, count))
                        }
                    }
                }
            }
            runCatching {
                val tmp = File(fileFor(localeTag).parentFile, fileFor(localeTag).name + ".tmp")
                tmp.bufferedWriter().use { w ->
                    for ((prev, next, count) in snapshot) {
                        w.write(prev)
                        w.write("\t")
                        w.write(next)
                        w.write("\t")
                        w.write(count.toString())
                        w.newLine()
                    }
                }
                if (!tmp.renameTo(fileFor(localeTag))) {
                    fileFor(localeTag).delete()
                    tmp.renameTo(fileFor(localeTag))
                }
            }
        }
    }

    /**
     * Forgets every bigram in [locale] that has [rawWord] as its `next` value, so the
     * given word never resurfaces in next-word predictions for that locale. Triggers a
     * flush.
     */
    fun forget(rawWord: String, locale: FlorisLocale) {
        val target = normalize(rawWord)
        if (target.isEmpty()) return
        val tag = locale.languageTag()
        ioScope.launch {
            val table = ensureLoaded(tag)
            synchronized(table) {
                val emptiedKeys = ArrayList<String>()
                for ((prev, nextMap) in table) {
                    if (nextMap.remove(target) != null && nextMap.isEmpty()) {
                        emptiedKeys.add(prev)
                    }
                }
                for (k in emptiedKeys) table.remove(k)
                pendingCommits = FLUSH_EVERY_N_COMMITS
            }
            flush(tag)
        }
    }

    /** Clears all bigram data on disk and in memory. Used by the "Reset learned data" action. */
    fun reset() {
        ioScope.launch {
            synchronized(tablesByLocale) { tablesByLocale.clear() }
            runCatching {
                context.filesDir.listFiles { _, name -> name.startsWith("personal_bigrams_") }
                    ?.forEach { it.delete() }
            }
        }
    }
}
