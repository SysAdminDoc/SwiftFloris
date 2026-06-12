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
 * Local on-device bigram counter that powers next-word suggestions. Bigrams
 * are accumulated as the user commits words and persisted to a single TSV
 * file per locale under `<filesDir>/personal_bigrams_<localeTag>.tsv`. Current
 * format is `prevWord\tnextWord\tcount\tlastSeenMs`; legacy three-column files
 * are still accepted and upgraded on the next flush.
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
    // ConcurrentHashMap (not HashMap): ensureLoaded() reads the top-level map outside
    // any lock for the fast path, while loads happen under loadGuard and reset/stats
    // iterate under synchronized(tablesByLocale) — two different lock regimes that do
    // not exclude each other, so a plain HashMap could corrupt or throw CME. The inner
    // per-prev maps remain guarded by synchronized(table).
    private val tablesByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Int>>> = ConcurrentHashMap()
    private val lastSeenByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Long>>> = ConcurrentHashMap()
    private val loadGuard = Mutex()
    private val pendingCommitsByLocale = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()

    private fun fileFor(localeTag: String): File =
        File(context.filesDir, "personal_bigrams_${localeTag.ifBlank { "default" }}.tsv")

    private data class BigramSnapshot(
        val prev: String,
        val next: String,
        val count: Int,
        val lastSeenMs: Long,
    )

    data class LearnedBigram(
        val localeTag: String,
        val prev: String,
        val next: String,
        val count: Int,
        val lastSeenMs: Long,
    )

    private fun normalize(word: String): String = PersonalNgramPersistence.normalizeToken(word)

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
                        if (parts.size != 3 && parts.size != 4) continue
                        val prev = parts[0]
                        val next = parts[1]
                        val count = parts[2].toIntOrNull() ?: continue
                        val lastSeenMs = parts.getOrNull(3)?.toLongOrNull()
                            ?.takeIf { it > 0L }
                            ?: loadTimestampMs
                        if (prev.isBlank() || next.isBlank() || count <= 0) continue
                        val nextMap = table.getOrPut(prev) { HashMap() }
                        nextMap[next] = count.coerceAtMost(MAX_COUNT)
                        val recencyNextMap = recencyTable.getOrPut(prev) { HashMap() }
                        recencyNextMap[next] = lastSeenMs
                    }
                }
            }
        }
        tablesByLocale[localeTag] = table
        lastSeenByLocale[localeTag] = recencyTable
        return table
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
            // Mutate under loadGuard so a concurrent resetAndAwait() cannot detach
            // the table mid-write — otherwise this learn would resurrect entries
            // into a discarded map or write to a file reset is deleting.
            val shouldFlush = loadGuard.withLock {
                val table = ensureLoadedLocked(tag)
                val recencyTable = lastSeenByLocale.getOrPut(tag) { HashMap() }
                val now = System.currentTimeMillis()
                synchronized(table) {
                    val nextMap = table.getOrPut(prev) { HashMap() }
                    val newCount = (nextMap[curr] ?: 0) + 1
                    nextMap[curr] = newCount.coerceAtMost(MAX_COUNT)
                    val recencyNextMap = recencyTable.getOrPut(prev) { HashMap() }
                    recencyNextMap[curr] = now
                }
                pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.incrementAndGet() >= FLUSH_EVERY_N_COMMITS
            }
            if (shouldFlush) {
                flushAndAwait(tag)
            }
        }
    }

    /**
     * Returns the top-[max] next-word candidates following [prevWord] in [locale],
     * sorted by decayed personal score. Empty when there is no learned context yet.
     */
    suspend fun predict(prevWord: String, locale: FlorisLocale, max: Int): List<String> {
        if (max <= 0) return emptyList()
        val prev = normalize(prevWord)
        if (prev.isEmpty()) return emptyList()
        val table = ensureLoaded(locale.languageTag())
        val localeTag = locale.languageTag()
        val snapshot = synchronized(table) {
            val nextMap = table[prev]?.toMap() ?: return emptyList()
            val recencyMap = lastSeenByLocale[localeTag]?.get(prev)?.toMap().orEmpty()
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
     * Scores [currWord] as a next-word continuation after [prevWord]. The result is
     * normalized against the most common learned continuation for the same previous
     * word, so it can be blended with dictionary and touch evidence by the decoder.
     */
    suspend fun score(prevWord: String, currWord: String, locale: FlorisLocale): Double {
        val prev = normalize(prevWord)
        val curr = normalize(currWord)
        if (prev.isEmpty() || curr.isEmpty()) return 0.0
        val localeTag = locale.languageTag()
        val table = ensureLoaded(localeTag)
        val snapshot = synchronized(table) {
            val nextMap = table[prev]?.toMap() ?: return 0.0
            val recencyMap = lastSeenByLocale[localeTag]?.get(prev)?.toMap().orEmpty()
            nextMap to recencyMap
        }
        val count = snapshot.first[curr] ?: return 0.0
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
            lastSeenMs = snapshot.second[curr] ?: now,
            maxScore = maxScore,
            nowMs = now,
        )
    }

    /**
     * Returns the total number of learned bigram continuations across all loaded
     * and persisted locales. Used by the local-only typing stats screen.
     */
    suspend fun totalEntryCount(): Int {
        val localeTags = knownLocaleTags()
        return loadGuard.withLock {
            localeTags.sumOf { localeTag ->
                val table = ensureLoadedLocked(localeTag)
                synchronized(table) {
                    table.values.sumOf { nextMap -> nextMap.size }
                }
            }
        }
    }

    suspend fun snapshot(maxEntries: Int = 500): List<LearnedBigram> {
        if (maxEntries <= 0) return emptyList()
        val localeTags = knownLocaleTags()
        val rows = loadGuard.withLock {
            buildList {
                for (localeTag in localeTags) {
                    val table = ensureLoadedLocked(localeTag)
                    val recencyTable = lastSeenByLocale[localeTag].orEmpty()
                    synchronized(table) {
                        for ((prev, nextMap) in table) {
                            val recencyNextMap = recencyTable[prev].orEmpty()
                            for ((next, count) in nextMap) {
                                add(
                                    LearnedBigram(
                                        localeTag = localeTag,
                                        prev = prev,
                                        next = next,
                                        count = count,
                                        lastSeenMs = recencyNextMap[next] ?: 0L,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return rows.sortedWith(
            compareByDescending<LearnedBigram> { it.count }
                .thenByDescending { it.lastSeenMs }
                .thenBy { it.prev }
                .thenBy { it.next }
        ).take(maxEntries)
    }

    private fun knownLocaleTags(): Set<String> = buildSet {
        context.filesDir.listFiles { _, name ->
            // Strict `.tsv` suffix so we don't pick up a leftover
            // `personal_bigrams_<tag>.tsv.tmp` from a crashed flush and
            // then later try to ensureLoaded("<tag>.tsv") with a
            // mismatched tag.
            name.startsWith("personal_bigrams_") && name.endsWith(".tsv") && !name.endsWith(".tsv.tmp")
        }?.forEach { file ->
            add(file.name.removePrefix("personal_bigrams_").removeSuffix(".tsv"))
        }
        synchronized(tablesByLocale) {
            addAll(tablesByLocale.keys)
        }
    }

    /**
     * Forces an immediate flush of [localeTag]'s table to disk. Called by the
     * commit-count threshold and from the IME service shutdown path.
     */
    fun flush(localeTag: String) {
        ioScope.launch {
            flushAndAwait(localeTag)
        }
    }

    suspend fun flushAndAwait(localeTag: String) {
        loadGuard.withLock {
            val table = tablesByLocale[localeTag] ?: return@withLock
            val recencyTable = lastSeenByLocale[localeTag] ?: HashMap()
            val snapshot: List<BigramSnapshot>
            synchronized(table) {
                pendingCommitsByLocale[localeTag]?.set(0)
                if (table.size > MAX_PREV_WORDS) {
                    val keepKeys = table.entries
                        .sortedByDescending { e -> e.value.values.sum() }
                        .take(MAX_PREV_WORDS)
                        .map { it.key }
                        .toSet()
                    val removeKeys = table.keys.filter { it !in keepKeys }
                    for (k in removeKeys) {
                        table.remove(k)
                        recencyTable.remove(k)
                    }
                }
                for ((prev, nextMap) in table) {
                    if (nextMap.size > MAX_NEXT_PER_PREV) {
                        val keepKeys = nextMap.entries
                            .sortedByDescending { it.value }
                            .take(MAX_NEXT_PER_PREV)
                            .map { it.key }
                            .toSet()
                        val removeKeys = nextMap.keys.filter { it !in keepKeys }
                        val recencyNextMap = recencyTable[prev]
                        for (k in removeKeys) {
                            nextMap.remove(k)
                            recencyNextMap?.remove(k)
                        }
                    }
                }
                snapshot = buildList {
                    val now = System.currentTimeMillis()
                    for ((prev, nextMap) in table) {
                        val recencyNextMap = recencyTable[prev].orEmpty()
                        for ((next, count) in nextMap) {
                            add(
                                BigramSnapshot(
                                    prev = prev,
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
                    w.write(row.prev)
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
                    "Personal bigram flush for '$localeTag' failed; previous on-disk state preserved"
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
        ioScope.launch {
            forgetAndAwait(rawWord, locale)
        }
    }

    suspend fun forgetAndAwait(rawWord: String, locale: FlorisLocale) {
        val target = normalize(rawWord)
        if (target.isEmpty()) return
        val tag = locale.languageTag()
        loadGuard.withLock {
            val table = ensureLoadedLocked(tag)
            val recencyTable = lastSeenByLocale.getOrPut(tag) { HashMap() }
            synchronized(table) {
                val emptiedKeys = ArrayList<String>()
                for ((prev, nextMap) in table) {
                    recencyTable[prev]?.remove(target)
                    if (nextMap.remove(target) != null && nextMap.isEmpty()) {
                        emptiedKeys.add(prev)
                    }
                }
                for (k in emptiedKeys) {
                    table.remove(k)
                    recencyTable.remove(k)
                }
                pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.set(FLUSH_EVERY_N_COMMITS)
            }
        }
        flushAndAwait(tag)
    }

    suspend fun forgetExactAndAwait(prevWord: String, currWord: String, locale: FlorisLocale) {
        val prev = normalize(prevWord)
        val curr = normalize(currWord)
        if (prev.isEmpty() || curr.isEmpty()) return
        val tag = locale.languageTag()
        var changed = false
        loadGuard.withLock {
            val table = ensureLoadedLocked(tag)
            val recencyTable = lastSeenByLocale.getOrPut(tag) { HashMap() }
            synchronized(table) {
                val nextMap = table[prev]
                if (nextMap?.remove(curr) != null) {
                    changed = true
                    recencyTable[prev]?.remove(curr)
                    if (nextMap.isEmpty()) {
                        table.remove(prev)
                        recencyTable.remove(prev)
                    }
                    pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.set(FLUSH_EVERY_N_COMMITS)
                }
            }
        }
        if (changed) {
            flushAndAwait(tag)
        }
    }

    /** Clears all bigram data on disk and in memory. Used by reset actions. */
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
                // Match the loose prefix so leftover `.tmp` flushes from a
                // prior crashed save are cleaned up by reset too.
                context.filesDir.listFiles { _, name -> name.startsWith("personal_bigrams_") }
                    ?.forEach { it.delete() }
            }
        }
    }
}
