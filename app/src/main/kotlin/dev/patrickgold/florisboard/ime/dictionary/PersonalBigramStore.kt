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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import dev.patrickgold.florisboard.lib.devtools.flogError

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
        private const val MAX_REJECTED_PREV_WORDS = 2000
        private const val MAX_REJECTIONS_PER_PREV = 24
        private const val MAX_COUNT = 1000
        private const val MAX_REJECTION_COUNT = 8
        private const val MIN_COUNT_FOR_SUGGEST = 2
        private const val MIN_REJECTION_ADJUSTED_SCORE_FOR_SUGGEST = 1.0
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

        internal fun forTesting(context: Context): PersonalBigramStore {
            return PersonalBigramStore(context.applicationContext)
        }
    }

    // SupervisorJob stops one failed child cancelling its siblings, but it does
    // nothing about an exception nobody caught: that still reaches the thread's
    // default handler and takes the process down. The fire-and-forget entry
    // points here launch and return, so there is no caller to catch anything,
    // and ensureLoadedLocked throws when the backing file will not parse. A
    // half-written TSV therefore crashed the keyboard on the first word it
    // tried to learn, and again on the next one, because a failed load leaves
    // nothing cached to short-circuit the retry.
    //
    // A personal-dictionary cache that cannot read itself must degrade, not
    // take typing with it. The suspending *AndAwait variants are unaffected:
    // they still propagate to whoever called them.
    private val ioExceptionHandler = CoroutineExceptionHandler { _, error ->
        flogError(LogTopic.DICTIONARY) {
            "Personal bigram background work failed; continuing without it: ${error.message}"
        }
    }
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + ioExceptionHandler)
    // ConcurrentHashMap (not HashMap): ensureLoaded() reads the top-level map outside
    // any lock for the fast path, while loads happen under loadGuard and reset/stats
    // iterate under synchronized(tablesByLocale) — two different lock regimes that do
    // not exclude each other, so a plain HashMap could corrupt or throw CME. The inner
    // per-prev maps remain guarded by synchronized(table).
    private val tablesByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Int>>> = ConcurrentHashMap()
    private val lastSeenByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Long>>> = ConcurrentHashMap()
    private val rejectionCountsByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Int>>> = ConcurrentHashMap()
    private val rejectionLastSeenByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Long>>> = ConcurrentHashMap()
    private val loadGuard = Mutex()
    private val pendingCommitsByLocale = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    private val loadStates = ConcurrentHashMap<String, PersonalNgramPersistence.LoadState>()

    private fun fileFor(localeTag: String): File =
        File(context.filesDir, "personal_bigrams_${localeTag.ifBlank { "default" }}.tsv")

    private fun rejectionFileFor(localeTag: String): File =
        File(context.filesDir, "personal_bigram_rejections_${localeTag.ifBlank { "default" }}.tsv")

    private data class BigramSnapshot(
        val prev: String,
        val next: String,
        val count: Int,
        val lastSeenMs: Long,
    )

    private data class BigramRejectionSnapshot(
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

    internal fun loadState(locale: FlorisLocale): PersonalNgramPersistence.LoadState {
        return loadStates[locale.languageTag()] ?: PersonalNgramPersistence.LoadState.NOT_LOADED
    }

    private suspend fun ensureLoaded(localeTag: String): MutableMap<String, MutableMap<String, Int>> {
        tablesByLocale[localeTag]?.let { return it }
        loadGuard.withLock {
            return ensureLoadedLocked(localeTag)
        }
    }

    /**
     * Loads [localeTag]'s table, or returns null when it will not parse.
     *
     * For the aggregate read paths only. A single unreadable file used to
     * propagate out of these, so one torn write took down the whole Learned
     * entries screen and the typing-stats count rather than hiding the one
     * locale it actually affected. Callers that act on a specific locale still
     * see the failure.
     */
    private fun loadOrSkipLocked(localeTag: String): MutableMap<String, MutableMap<String, Int>>? {
        return runCatching { ensureLoadedLocked(localeTag) }.getOrNull()
    }

    /** Loads (or returns) the table for [localeTag]. Caller must hold [loadGuard]. */
    private fun ensureLoadedLocked(localeTag: String): MutableMap<String, MutableMap<String, Int>> {
        tablesByLocale[localeTag]?.let { return it }
        // A load that already failed will fail the same way again: nothing has
        // rewritten the file in between. Re-reading it on every learned word
        // turned one unparseable file into disk traffic on the typing path.
        if (loadStates[localeTag] == PersonalNgramPersistence.LoadState.UNREADABLE) {
            throw PersonalNgramPersistence.LoadException(
                fileFor(localeTag),
                IllegalStateException("load previously failed for '$localeTag'"),
            )
        }
        val loadedTable: MutableMap<String, MutableMap<String, Int>> = HashMap()
        val recencyTable: MutableMap<String, MutableMap<String, Long>> = HashMap()
        val loadedRejectionTable: MutableMap<String, MutableMap<String, Int>> = HashMap()
        val rejectionRecencyTable: MutableMap<String, MutableMap<String, Long>> = HashMap()
        val primaryFile = fileFor(localeTag)
        val rejectionFile = rejectionFileFor(localeTag)
        try {
            readTable(primaryFile, loadedTable, recencyTable, MAX_COUNT)
            readTable(rejectionFile, loadedRejectionTable, rejectionRecencyTable, MAX_REJECTION_COUNT)
        } catch (error: Throwable) {
            val loadError = if (error is PersonalNgramPersistence.LoadException) {
                error
            } else {
                PersonalNgramPersistence.LoadException(primaryFile, error)
            }
            loadStates[localeTag] = PersonalNgramPersistence.LoadState.UNREADABLE
            flogWarning(LogTopic.DICTIONARY) {
                "Personal bigram load for '${loadError.source.name}' failed; in-memory and on-disk state preserved"
            }
            throw loadError
        }
        tablesByLocale[localeTag] = loadedTable
        lastSeenByLocale[localeTag] = recencyTable
        rejectionCountsByLocale[localeTag] = loadedRejectionTable
        rejectionLastSeenByLocale[localeTag] = rejectionRecencyTable
        loadStates[localeTag] = PersonalNgramPersistence.LoadState.READY
        return loadedTable
    }

    private fun readTable(
        file: File,
        table: MutableMap<String, MutableMap<String, Int>>,
        recencyTable: MutableMap<String, MutableMap<String, Long>>,
        maxCount: Int,
    ) {
        if (!file.exists() || file.length() <= 0L) return
        try {
            file.bufferedReader().useLines { lines ->
                for ((index, line) in lines.withIndex()) {
                    if (line.isBlank()) continue
                    val parts = line.split('\t')
                    require(parts.size == 3 || parts.size == 4) {
                        "invalid TSV column count at ${file.name}:${index + 1}"
                    }
                    val prev = parts[0].takeUnless { it.isBlank() }
                        ?: error("blank previous word at ${file.name}:${index + 1}")
                    val next = parts[1].takeUnless { it.isBlank() }
                        ?: error("blank next word at ${file.name}:${index + 1}")
                    val count = parts[2].toIntOrNull()?.takeIf { it > 0 }
                        ?: error("invalid count at ${file.name}:${index + 1}")
                    val lastSeenMs = if (parts.size == 4) {
                        parts[3].toLongOrNull()?.takeIf { it > 0L }
                            ?: error("invalid timestamp at ${file.name}:${index + 1}")
                    } else {
                        System.currentTimeMillis()
                    }
                    val nextMap = table.getOrPut(prev) { HashMap() }
                    nextMap[next] = count.coerceAtMost(maxCount)
                    val recencyNextMap = recencyTable.getOrPut(prev) { HashMap() }
                    recencyNextMap[next] = lastSeenMs
                }
            }
        } catch (error: Throwable) {
            if (error is PersonalNgramPersistence.LoadException) throw error
            throw PersonalNgramPersistence.LoadException(file, error)
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
                clearRejectionLocked(tag, prev, curr)
                pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.incrementAndGet() >= FLUSH_EVERY_N_COMMITS
            }
            if (shouldFlush) {
                flushAndAwait(tag)
            }
        }
    }

    suspend fun learnAndAwait(prevWord: String, currWord: String, locale: FlorisLocale) {
        val prev = normalize(prevWord)
        val curr = normalize(currWord)
        if (prev.isEmpty() || curr.isEmpty() || prev == curr) return
        val tag = locale.languageTag()
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
            clearRejectionLocked(tag, prev, curr)
            pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.incrementAndGet() >= FLUSH_EVERY_N_COMMITS
        }
        if (shouldFlush) {
            flushAndAwait(tag)
        }
    }

    private fun rejectionDiscount(rejectionCount: Int): Double {
        if (rejectionCount <= 0) return 1.0
        return (1.0 / (1.0 + rejectionCount.coerceAtMost(MAX_REJECTION_COUNT) * 4.0))
            .coerceIn(0.05, 1.0)
    }

    private fun clearRejectionLocked(localeTag: String, prev: String, next: String): Boolean {
        val table = rejectionCountsByLocale[localeTag] ?: return false
        val recencyTable = rejectionLastSeenByLocale[localeTag]
        var changed = false
        synchronized(table) {
            val nextMap = table[prev]
            if (nextMap?.remove(next) != null) {
                changed = true
                recencyTable?.get(prev)?.remove(next)
                if (nextMap.isEmpty()) {
                    table.remove(prev)
                    recencyTable?.remove(prev)
                }
            }
        }
        return changed
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
        val rejectionCounts = rejectionCountsByLocale[localeTag]?.let { rejections ->
            synchronized(rejections) {
                rejections[prev]?.toMap().orEmpty()
            }
        }.orEmpty()
        val now = System.currentTimeMillis()
        return snapshot.first.entries
            .asSequence()
            .filter { it.value >= MIN_COUNT_FOR_SUGGEST }
            .map { entry ->
                val decayedScore = PersonalNgramRecency.decayedScore(
                    count = entry.value,
                    lastSeenMs = snapshot.second[entry.key] ?: now,
                    nowMs = now,
                )
                entry to decayedScore * rejectionDiscount(rejectionCounts[entry.key] ?: 0)
            }
            .filter { (_, adjustedScore) -> adjustedScore >= MIN_REJECTION_ADJUSTED_SCORE_FOR_SUGGEST }
            .sortedWith(
                compareByDescending<Pair<Map.Entry<String, Int>, Double>> { it.second }
                    .thenByDescending { it.first.value }
            )
            .map { it.first.key }
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
        val rejectionCount = rejectionCountsByLocale[localeTag]?.let { rejections ->
            synchronized(rejections) {
                rejections[prev]?.get(curr)
            }
        } ?: 0
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
        ) * rejectionDiscount(rejectionCount)
    }

    suspend fun rejectionPenalty(prevWord: String, currWord: String, locale: FlorisLocale): Double {
        val prev = normalize(prevWord)
        val curr = normalize(currWord)
        if (prev.isEmpty() || curr.isEmpty()) return 0.0
        val localeTag = locale.languageTag()
        ensureLoaded(localeTag)
        val rejectionCount = rejectionCountsByLocale[localeTag]?.let { rejections ->
            synchronized(rejections) {
                rejections[prev]?.get(curr)
            }
        } ?: 0
        return (1.0 - rejectionDiscount(rejectionCount)).coerceIn(0.0, 1.0)
    }

    /**
     * Returns the total number of learned bigram continuations across all loaded
     * and persisted locales. Used by the local-only typing stats screen.
     */
    suspend fun totalEntryCount(): Int {
        val localeTags = knownLocaleTags()
        return loadGuard.withLock {
            localeTags.sumOf { localeTag ->
                val table = loadOrSkipLocked(localeTag) ?: return@sumOf 0
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
                    val table = loadOrSkipLocked(localeTag) ?: continue
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
        context.filesDir.listFiles { _, name ->
            name.startsWith("personal_bigram_rejections_") &&
                name.endsWith(".tsv") &&
                !name.endsWith(".tsv.tmp")
        }?.forEach { file ->
            add(file.name.removePrefix("personal_bigram_rejections_").removeSuffix(".tsv"))
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

    suspend fun flushAndAwait(localeTag: String): Boolean {
        return loadGuard.withLock {
            val table = tablesByLocale[localeTag] ?: return@withLock true
            val recencyTable = lastSeenByLocale[localeTag] ?: HashMap()
            val rejectionTable = rejectionCountsByLocale[localeTag] ?: HashMap()
            val rejectionRecencyTable = rejectionLastSeenByLocale[localeTag] ?: HashMap()
            val snapshot: List<BigramSnapshot>
            val rejectionSnapshot: List<BigramRejectionSnapshot>
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
                synchronized(rejectionTable) {
                    if (rejectionTable.size > MAX_REJECTED_PREV_WORDS) {
                        val keepKeys = rejectionTable.entries
                            .sortedByDescending { e -> e.value.values.sum() }
                            .take(MAX_REJECTED_PREV_WORDS)
                            .map { it.key }
                            .toSet()
                        val removeKeys = rejectionTable.keys.filter { it !in keepKeys }
                        for (k in removeKeys) {
                            rejectionTable.remove(k)
                            rejectionRecencyTable.remove(k)
                        }
                    }
                    for ((prev, nextMap) in rejectionTable) {
                        if (nextMap.size > MAX_REJECTIONS_PER_PREV) {
                            val keepKeys = nextMap.entries
                                .sortedByDescending { it.value }
                                .take(MAX_REJECTIONS_PER_PREV)
                                .map { it.key }
                                .toSet()
                            val removeKeys = nextMap.keys.filter { it !in keepKeys }
                            val recencyNextMap = rejectionRecencyTable[prev]
                            for (k in removeKeys) {
                                nextMap.remove(k)
                                recencyNextMap?.remove(k)
                            }
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
                rejectionSnapshot = buildList {
                    val now = System.currentTimeMillis()
                    synchronized(rejectionTable) {
                        for ((prev, nextMap) in rejectionTable) {
                            val recencyNextMap = rejectionRecencyTable[prev].orEmpty()
                            for ((next, count) in nextMap) {
                                add(
                                    BigramRejectionSnapshot(
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
            val rejectionsPersisted = PersonalNgramPersistence.atomicReplace(rejectionFileFor(localeTag)) { w ->
                for (row in rejectionSnapshot) {
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
            if (!rejectionsPersisted) {
                flogWarning(LogTopic.DICTIONARY) {
                    "Personal bigram rejection flush for '$localeTag' failed; previous on-disk state preserved"
                }
            }
            val persistedAll = persisted && rejectionsPersisted
            loadStates[localeTag] = if (persistedAll) {
                PersonalNgramPersistence.LoadState.READY
            } else {
                PersonalNgramPersistence.LoadState.WRITE_FAILED
            }
            persistedAll
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

    fun rejectContinuation(prevWord: String, currWord: String, locale: FlorisLocale) {
        ioScope.launch {
            rejectContinuationAndAwait(prevWord, currWord, locale)
        }
    }

    suspend fun rejectContinuationAndAwait(prevWord: String, currWord: String, locale: FlorisLocale): Boolean {
        val prev = normalize(prevWord)
        val curr = normalize(currWord)
        if (prev.isEmpty() || curr.isEmpty() || prev == curr) return false
        val tag = locale.languageTag()
        var changed = false
        loadGuard.withLock {
            ensureLoadedLocked(tag)
            val table = rejectionCountsByLocale.getOrPut(tag) { HashMap() }
            val recencyTable = rejectionLastSeenByLocale.getOrPut(tag) { HashMap() }
            val now = System.currentTimeMillis()
            synchronized(table) {
                val nextMap = table.getOrPut(prev) { HashMap() }
                val newCount = (nextMap[curr] ?: 0) + 1
                nextMap[curr] = newCount.coerceAtMost(MAX_REJECTION_COUNT)
                val recencyNextMap = recencyTable.getOrPut(prev) { HashMap() }
                recencyNextMap[curr] = now
                pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.set(FLUSH_EVERY_N_COMMITS)
                changed = true
            }
        }
        if (changed) {
            flushAndAwait(tag)
        }
        return changed
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
            synchronized(rejectionCountsByLocale) { rejectionCountsByLocale.clear() }
            synchronized(rejectionLastSeenByLocale) { rejectionLastSeenByLocale.clear() }
            pendingCommitsByLocale.clear()
            loadStates.clear()
            runCatching {
                // Match the loose prefix so leftover `.tmp` flushes from a
                // prior crashed save are cleaned up by reset too.
                context.filesDir.listFiles { _, name -> name.startsWith("personal_bigrams_") }
                    ?.forEach { it.delete() }
                context.filesDir.listFiles { _, name -> name.startsWith("personal_bigram_rejections_") }
                    ?.forEach { it.delete() }
            }
        }
    }
}
