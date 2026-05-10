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
 * Local trigram store: counts how often the user types `next` after the
 * two-word context `(prev2, prev1)`. Persisted to a single TSV file per
 * locale at `<filesDir>/personal_trigrams_<localeTag>.tsv` with one
 * tab-separated quadruple per line: `prev2\tprev1\tnext\tcount`.
 *
 * Rationale: a bigram like "the" → most common next word will pick "the"
 * generically. A trigram like ("the", "quick") → "brown" is *vastly* more
 * predictive of the user's actual style. SwiftKey's neural LM gets the
 * same effect via attention; this is the n-gram fallback while we wait for
 * a quantised on-device LLM.
 *
 * Storage cap: at most [MAX_CONTEXTS] (prev2, prev1) keys per locale,
 * [MAX_NEXT_PER_CONTEXT] next words per context. Eviction on next save
 * sorts by total observed count (most-frequent contexts retained).
 *
 * Privacy posture matches PersonalBigramStore: never leaves the device.
 */
class PersonalTrigramStore private constructor(private val context: Context) {
    companion object {
        private const val MAX_CONTEXTS = 4000
        private const val MAX_NEXT_PER_CONTEXT = 12
        private const val MAX_COUNT = 1000
        private const val MIN_COUNT_FOR_SUGGEST = 2
        private const val FLUSH_EVERY_N_COMMITS = 20
        private const val CONTEXT_DELIMITER = "\u0000"

        @Volatile
        private var instance: PersonalTrigramStore? = null

        fun get(context: Context): PersonalTrigramStore {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                return PersonalTrigramStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tablesByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Int>>> = HashMap()
    private val loadGuard = Mutex()
    private var pendingCommits: Int = 0

    private fun fileFor(localeTag: String): File =
        File(context.filesDir, "personal_trigrams_${localeTag.ifBlank { "default" }}.tsv")

    private fun normalize(word: String): String {
        if (word.isBlank()) return ""
        val trimmed = word.trim().trim { ch -> !ch.isLetter() && ch != '\'' && ch != '-' }
        if (trimmed.length < 2 || trimmed.length > 32) return ""
        if (trimmed.any { it.isDigit() }) return ""
        if (trimmed.none { it.isLetter() }) return ""
        return trimmed.lowercase()
    }

    private fun contextKey(prev2: String, prev1: String): String = prev2 + CONTEXT_DELIMITER + prev1

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
                            if (parts.size != 4) continue
                            val prev2 = parts[0]
                            val prev1 = parts[1]
                            val next = parts[2]
                            val count = parts[3].toIntOrNull() ?: continue
                            if (prev2.isBlank() || prev1.isBlank() || next.isBlank() || count <= 0) continue
                            val nextMap = table.getOrPut(contextKey(prev2, prev1)) { HashMap() }
                            nextMap[next] = count.coerceAtMost(MAX_COUNT)
                        }
                    }
                }
            }
            tablesByLocale[localeTag] = table
            return table
        }
    }

    fun learn(prev2: String, prev1: String, currWord: String, locale: FlorisLocale) {
        val a = normalize(prev2)
        val b = normalize(prev1)
        val c = normalize(currWord)
        if (a.isEmpty() || b.isEmpty() || c.isEmpty()) return
        val tag = locale.languageTag()
        ioScope.launch {
            val table = ensureLoaded(tag)
            synchronized(table) {
                val nextMap = table.getOrPut(contextKey(a, b)) { HashMap() }
                val newCount = (nextMap[c] ?: 0) + 1
                nextMap[c] = newCount.coerceAtMost(MAX_COUNT)
                pendingCommits += 1
            }
            if (pendingCommits >= FLUSH_EVERY_N_COMMITS) {
                flush(tag)
            }
        }
    }

    suspend fun predict(prev2: String, prev1: String, locale: FlorisLocale, max: Int): List<String> {
        if (max <= 0) return emptyList()
        val a = normalize(prev2)
        val b = normalize(prev1)
        if (a.isEmpty() || b.isEmpty()) return emptyList()
        val table = ensureLoaded(locale.languageTag())
        val nextMap = synchronized(table) { table[contextKey(a, b)]?.toMap() } ?: return emptyList()
        return nextMap.entries
            .asSequence()
            .filter { it.value >= MIN_COUNT_FOR_SUGGEST }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(max)
            .toList()
    }

    fun flush(localeTag: String) {
        ioScope.launch {
            val table = tablesByLocale[localeTag] ?: return@launch
            val snapshot: List<Triple<String, String, Pair<String, Int>>>
            synchronized(table) {
                pendingCommits = 0
                if (table.size > MAX_CONTEXTS) {
                    val keepKeys = table.entries
                        .sortedByDescending { e -> e.value.values.sum() }
                        .take(MAX_CONTEXTS)
                        .map { it.key }
                        .toSet()
                    val removeKeys = table.keys.filter { it !in keepKeys }
                    for (k in removeKeys) table.remove(k)
                }
                for ((_, nextMap) in table) {
                    if (nextMap.size > MAX_NEXT_PER_CONTEXT) {
                        val keepKeys = nextMap.entries
                            .sortedByDescending { it.value }
                            .take(MAX_NEXT_PER_CONTEXT)
                            .map { it.key }
                            .toSet()
                        val removeKeys = nextMap.keys.filter { it !in keepKeys }
                        for (k in removeKeys) nextMap.remove(k)
                    }
                }
                snapshot = buildList {
                    for ((ctxKey, nextMap) in table) {
                        val parts = ctxKey.split(CONTEXT_DELIMITER, limit = 2)
                        if (parts.size != 2) continue
                        val prev2 = parts[0]
                        val prev1 = parts[1]
                        for ((next, count) in nextMap) {
                            add(Triple(prev2, prev1, next to count))
                        }
                    }
                }
            }
            runCatching {
                val tmp = File(fileFor(localeTag).parentFile, fileFor(localeTag).name + ".tmp")
                tmp.bufferedWriter().use { w ->
                    for ((prev2, prev1, nextCount) in snapshot) {
                        w.write(prev2)
                        w.write("\t")
                        w.write(prev1)
                        w.write("\t")
                        w.write(nextCount.first)
                        w.write("\t")
                        w.write(nextCount.second.toString())
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

    fun reset() {
        ioScope.launch {
            synchronized(tablesByLocale) { tablesByLocale.clear() }
            runCatching {
                context.filesDir.listFiles { _, name -> name.startsWith("personal_trigrams_") }
                    ?.forEach { it.delete() }
            }
        }
    }
}
