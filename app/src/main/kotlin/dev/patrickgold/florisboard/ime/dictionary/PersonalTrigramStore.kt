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
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local trigram store: counts how often the user types `next` after the
 * two-word context `(prev2, prev1)`. Persisted to a single TSV file per
 * locale at `<filesDir>/personal_trigrams_<localeTag>.tsv`. Current format
 * is `prev2\tprev1\tnext\tcount\tlastSeenMs`; legacy four-column files are
 * still accepted and upgraded on the next flush.
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
    // ConcurrentHashMap (not HashMap): see PersonalBigramStore — ensureLoaded() reads the
    // top-level map outside any lock, while loads run under loadGuard and reset/stats
    // iterate under synchronized(tablesByLocale), so a plain HashMap could corrupt or
    // throw CME. Inner per-context maps stay guarded by synchronized(table).
    private val tablesByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Int>>> = ConcurrentHashMap()
    private val lastSeenByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Long>>> = ConcurrentHashMap()
    private val loadGuard = Mutex()
    private val pendingCommitsByLocale = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()

    private fun fileFor(localeTag: String): File =
        File(context.filesDir, "personal_trigrams_${localeTag.ifBlank { "default" }}.tsv")

    private data class TrigramSnapshot(
        val prev2: String,
        val prev1: String,
        val next: String,
        val count: Int,
        val lastSeenMs: Long,
    )

    private fun normalize(word: String): String = PersonalNgramPersistence.normalizeToken(word)

    private fun contextKey(prev2: String, prev1: String): String = prev2 + CONTEXT_DELIMITER + prev1

    private suspend fun ensureLoaded(localeTag: String): MutableMap<String, MutableMap<String, Int>> {
        tablesByLocale[localeTag]?.let { return it }
        loadGuard.withLock {
            return ensureLoadedLocked(localeTag)
        }
    }

    /** Loads (or returns) the table for [localeTag]. Caller must hold [loadGuard]. */
    private fun ensureLoadedLocked(localeTag: String): MutableMap<String, MutableMap<String, Int>> {
        tablesByLocale[localeTag]?.let { return it }
        val table: MutableMap<String, MutableMap<String, Int>> = HashMap()
        val recencyTable: MutableMap<String, MutableMap<String, Long>> = HashMap()
        val f = fileFor(localeTag)
        val loadTimestampMs = System.currentTimeMillis()
        if (f.exists() && f.length() > 0L) {
            runCatching {
                f.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val parts = line.split('\t')
                        if (parts.size != 4 && parts.size != 5) continue
                        val prev2 = parts[0]
                        val prev1 = parts[1]
                        val next = parts[2]
                        val count = parts[3].toIntOrNull() ?: continue
                        val lastSeenMs = parts.getOrNull(4)?.toLongOrNull()
                            ?.takeIf { it > 0L }
                            ?: loadTimestampMs
                        if (prev2.isBlank() || prev1.isBlank() || next.isBlank() || count <= 0) continue
                        val key = contextKey(prev2, prev1)
                        val nextMap = table.getOrPut(key) { HashMap() }
                        nextMap[next] = count.coerceAtMost(MAX_COUNT)
                        val recencyNextMap = recencyTable.getOrPut(key) { HashMap() }
                        recencyNextMap[next] = lastSeenMs
                    }
                }
            }
        }
        tablesByLocale[localeTag] = table
        lastSeenByLocale[localeTag] = recencyTable
        return table
    }

    fun learn(prev2: String, prev1: String, currWord: String, locale: FlorisLocale) {
        val a = normalize(prev2)
        val b = normalize(prev1)
        val c = normalize(currWord)
        if (a.isEmpty() || b.isEmpty() || c.isEmpty()) return
        val tag = locale.languageTag()
        ioScope.launch {
            // Mutate under loadGuard so a concurrent resetAndAwait() cannot detach
            // the table mid-write — otherwise this learn would resurrect entries
            // into a discarded map or write to a file reset is deleting.
            val shouldFlush = loadGuard.withLock {
                val table = ensureLoadedLocked(tag)
                val recencyTable = lastSeenByLocale.getOrPut(tag) { HashMap() }
                val now = System.currentTimeMillis()
                synchronized(table) {
                    val key = contextKey(a, b)
                    val nextMap = table.getOrPut(key) { HashMap() }
                    val newCount = (nextMap[c] ?: 0) + 1
                    nextMap[c] = newCount.coerceAtMost(MAX_COUNT)
                    val recencyNextMap = recencyTable.getOrPut(key) { HashMap() }
                    recencyNextMap[c] = now
                }
                pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.incrementAndGet() >= FLUSH_EVERY_N_COMMITS
            }
            if (shouldFlush) {
                flush(tag)
            }
        }
    }

    suspend fun predict(prev2: String, prev1: String, locale: FlorisLocale, max: Int): List<String> {
        if (max <= 0) return emptyList()
        val a = normalize(prev2)
        val b = normalize(prev1)
        if (a.isEmpty() || b.isEmpty()) return emptyList()
        val localeTag = locale.languageTag()
        val key = contextKey(a, b)
        val table = ensureLoaded(localeTag)
        val snapshot = synchronized(table) {
            val nextMap = table[key]?.toMap() ?: return emptyList()
            val recencyMap = lastSeenByLocale[localeTag]?.get(key)?.toMap().orEmpty()
            nextMap to recencyMap
        }
        val now = System.currentTimeMillis()
        return snapshot.first.entries
            .asSequence()
            .filter { it.value >= MIN_COUNT_FOR_SUGGEST }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { entry ->
                    PersonalNgramRecency.decayedScore(
                        count = entry.value,
                        lastSeenMs = snapshot.second[entry.key] ?: now,
                        nowMs = now,
                    )
                }.thenByDescending { it.value }
            )
            .map { it.key }
            .take(max)
            .toList()
    }

    /**
     * Scores [currWord] as a continuation for the two-word context
     * ([prev2], [prev1]). The result is normalized to the strongest learned
     * continuation for that exact context.
     */
    suspend fun score(prev2: String, prev1: String, currWord: String, locale: FlorisLocale): Double {
        val a = normalize(prev2)
        val b = normalize(prev1)
        val c = normalize(currWord)
        if (a.isEmpty() || b.isEmpty() || c.isEmpty()) return 0.0
        val localeTag = locale.languageTag()
        val key = contextKey(a, b)
        val table = ensureLoaded(localeTag)
        val snapshot = synchronized(table) {
            val nextMap = table[key]?.toMap() ?: return 0.0
            val recencyMap = lastSeenByLocale[localeTag]?.get(key)?.toMap().orEmpty()
            nextMap to recencyMap
        }
        val count = snapshot.first[c] ?: return 0.0
        val now = System.currentTimeMillis()
        val maxScore = snapshot.first.entries.maxOfOrNull { entry ->
            PersonalNgramRecency.decayedScore(
                count = entry.value,
                lastSeenMs = snapshot.second[entry.key] ?: now,
                nowMs = now,
            )
        } ?: return 0.0
        return PersonalNgramRecency.normalizedScore(
            count = count,
            lastSeenMs = snapshot.second[c] ?: now,
            maxScore = maxScore,
            nowMs = now,
        )
    }

    /**
     * Returns the total number of learned trigram continuations across all
     * loaded and persisted locales. Used by the local-only typing stats screen.
     */
    suspend fun totalEntryCount(): Int {
        val localeTags = buildSet {
            context.filesDir.listFiles { _, name ->
                name.startsWith("personal_trigrams_") && name.endsWith(".tsv")
            }?.forEach { file ->
                add(file.name.removePrefix("personal_trigrams_").removeSuffix(".tsv"))
            }
            synchronized(tablesByLocale) {
                addAll(tablesByLocale.keys)
            }
        }
        return loadGuard.withLock {
            localeTags.sumOf { localeTag ->
                val table = ensureLoadedLocked(localeTag)
                synchronized(table) {
                    table.values.sumOf { nextMap -> nextMap.size }
                }
            }
        }
    }

    fun flush(localeTag: String) {
        ioScope.launch {
            loadGuard.withLock {
                val table = tablesByLocale[localeTag] ?: return@withLock
                val recencyTable = lastSeenByLocale[localeTag] ?: HashMap()
                val snapshot: List<TrigramSnapshot>
                synchronized(table) {
                    pendingCommitsByLocale[localeTag]?.set(0)
                    if (table.size > MAX_CONTEXTS) {
                        val keepKeys = table.entries
                            .sortedByDescending { e -> e.value.values.sum() }
                            .take(MAX_CONTEXTS)
                            .map { it.key }
                            .toSet()
                        val removeKeys = table.keys.filter { it !in keepKeys }
                        for (k in removeKeys) {
                            table.remove(k)
                            recencyTable.remove(k)
                        }
                    }
                    for ((ctxKey, nextMap) in table) {
                        if (nextMap.size > MAX_NEXT_PER_CONTEXT) {
                            val keepKeys = nextMap.entries
                                .sortedByDescending { it.value }
                                .take(MAX_NEXT_PER_CONTEXT)
                                .map { it.key }
                                .toSet()
                            val removeKeys = nextMap.keys.filter { it !in keepKeys }
                            val recencyNextMap = recencyTable[ctxKey]
                            for (k in removeKeys) {
                                nextMap.remove(k)
                                recencyNextMap?.remove(k)
                            }
                        }
                    }
                    snapshot = buildList {
                        val now = System.currentTimeMillis()
                        for ((ctxKey, nextMap) in table) {
                            val parts = ctxKey.split(CONTEXT_DELIMITER, limit = 2)
                            if (parts.size != 2) continue
                            val prev2 = parts[0]
                            val prev1 = parts[1]
                            val recencyNextMap = recencyTable[ctxKey].orEmpty()
                            for ((next, count) in nextMap) {
                                add(
                                    TrigramSnapshot(
                                        prev2 = prev2,
                                        prev1 = prev1,
                                        next = next,
                                        count = count,
                                        lastSeenMs = recencyNextMap[next] ?: now,
                                    )
                                )
                            }
                        }
                    }
                }
                val persisted = PersonalNgramPersistence.atomicReplace(fileFor(localeTag)) { w ->
                    for (row in snapshot) {
                        w.write(row.prev2)
                        w.write("\t")
                        w.write(row.prev1)
                        w.write("\t")
                        w.write(row.next)
                        w.write("\t")
                        w.write(row.count.toString())
                        w.write("\t")
                        w.write(row.lastSeenMs.toString())
                        w.newLine()
                    }
                }
                if (!persisted) {
                    flogWarning(LogTopic.DICTIONARY) {
                        "Personal trigram flush for '$localeTag' failed; previous on-disk state preserved"
                    }
                }
            }
        }
    }

    /**
     * Forgets every trigram in [locale] that has [rawWord] as its `next` value.
     */
    fun forget(rawWord: String, locale: FlorisLocale) {
        val target = normalize(rawWord)
        if (target.isEmpty()) return
        val tag = locale.languageTag()
        ioScope.launch {
            loadGuard.withLock {
                val table = ensureLoadedLocked(tag)
                val recencyTable = lastSeenByLocale.getOrPut(tag) { HashMap() }
                synchronized(table) {
                    val emptiedKeys = ArrayList<String>()
                    for ((ctxKey, nextMap) in table) {
                        recencyTable[ctxKey]?.remove(target)
                        if (nextMap.remove(target) != null && nextMap.isEmpty()) {
                            emptiedKeys.add(ctxKey)
                        }
                    }
                    for (k in emptiedKeys) {
                        table.remove(k)
                        recencyTable.remove(k)
                    }
                    pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.set(FLUSH_EVERY_N_COMMITS)
                }
            }
            flush(tag)
        }
    }

    fun reset() {
        ioScope.launch {
            resetAndAwait()
        }
    }

    /**
     * Synchronous-in-coroutine reset for settings UI flows that need the next
     * stats refresh to observe the cleared state immediately.
     */
    suspend fun resetAndAwait() {
        loadGuard.withLock {
            synchronized(tablesByLocale) { tablesByLocale.clear() }
            synchronized(lastSeenByLocale) { lastSeenByLocale.clear() }
            pendingCommitsByLocale.clear()
            runCatching {
                context.filesDir.listFiles { _, name -> name.startsWith("personal_trigrams_") }
                    ?.forEach { it.delete() }
            }
        }
    }
}
