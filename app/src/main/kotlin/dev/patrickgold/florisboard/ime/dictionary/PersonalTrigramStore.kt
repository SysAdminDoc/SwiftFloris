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
        private const val MAX_REJECTED_CONTEXTS = 4000
        private const val MAX_REJECTIONS_PER_CONTEXT = 24
        private const val MAX_COUNT = 1000
        private const val MAX_REJECTION_COUNT = 8
        private const val MIN_COUNT_FOR_SUGGEST = 2
        private const val MIN_REJECTION_ADJUSTED_SCORE_FOR_SUGGEST = 1.0
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

        internal fun forTesting(context: Context): PersonalTrigramStore {
            return PersonalTrigramStore(context.applicationContext)
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
            "Personal trigram background work failed; continuing without it: ${error.message}"
        }
    }
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + ioExceptionHandler)
    // ConcurrentHashMap (not HashMap): see PersonalBigramStore — ensureLoaded() reads the
    // top-level map outside any lock, while loads run under loadGuard and reset/stats
    // iterate under synchronized(tablesByLocale), so a plain HashMap could corrupt or
    // throw CME. Inner per-context maps stay guarded by synchronized(table).
    private val tablesByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Int>>> = ConcurrentHashMap()
    private val lastSeenByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Long>>> = ConcurrentHashMap()
    private val rejectionCountsByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Int>>> = ConcurrentHashMap()
    private val rejectionLastSeenByLocale: MutableMap<String, MutableMap<String, MutableMap<String, Long>>> = ConcurrentHashMap()
    private val loadGuard = Mutex()
    private val pendingCommitsByLocale = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    private val loadStates = ConcurrentHashMap<String, PersonalNgramPersistence.LoadState>()

    private fun fileFor(localeTag: String): File =
        File(context.filesDir, "personal_trigrams_${localeTag.ifBlank { "default" }}.tsv")

    private fun rejectionFileFor(localeTag: String): File =
        File(context.filesDir, "personal_trigram_rejections_${localeTag.ifBlank { "default" }}.tsv")

    private data class TrigramSnapshot(
        val prev2: String,
        val prev1: String,
        val next: String,
        val count: Int,
        val lastSeenMs: Long,
    )

    private data class TrigramRejectionSnapshot(
        val prev2: String,
        val prev1: String,
        val next: String,
        val count: Int,
        val lastSeenMs: Long,
    )

    data class LearnedTrigram(
        val localeTag: String,
        val prev2: String,
        val prev1: String,
        val next: String,
        val count: Int,
        val lastSeenMs: Long,
    )

    private fun normalize(word: String): String = PersonalNgramPersistence.normalizeToken(word)

    internal fun loadState(locale: FlorisLocale): PersonalNgramPersistence.LoadState {
        return loadStates[locale.languageTag()] ?: PersonalNgramPersistence.LoadState.NOT_LOADED
    }

    private fun contextKey(prev2: String, prev1: String): String = prev2 + CONTEXT_DELIMITER + prev1

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
                "Personal trigram load for '${loadError.source.name}' failed; in-memory and on-disk state preserved"
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
                    require(parts.size == 4 || parts.size == 5) {
                        "invalid TSV column count at ${file.name}:${index + 1}"
                    }
                    val prev2 = parts[0].takeUnless { it.isBlank() }
                        ?: error("blank first context word at ${file.name}:${index + 1}")
                    val prev1 = parts[1].takeUnless { it.isBlank() }
                        ?: error("blank second context word at ${file.name}:${index + 1}")
                    val next = parts[2].takeUnless { it.isBlank() }
                        ?: error("blank next word at ${file.name}:${index + 1}")
                    val count = parts[3].toIntOrNull()?.takeIf { it > 0 }
                        ?: error("invalid count at ${file.name}:${index + 1}")
                    val lastSeenMs = if (parts.size == 5) {
                        parts[4].toLongOrNull()?.takeIf { it > 0L }
                            ?: error("invalid timestamp at ${file.name}:${index + 1}")
                    } else {
                        System.currentTimeMillis()
                    }
                    val key = contextKey(prev2, prev1)
                    val nextMap = table.getOrPut(key) { HashMap() }
                    nextMap[next] = count.coerceAtMost(maxCount)
                    val recencyNextMap = recencyTable.getOrPut(key) { HashMap() }
                    recencyNextMap[next] = lastSeenMs
                }
            }
        } catch (error: Throwable) {
            if (error is PersonalNgramPersistence.LoadException) throw error
            throw PersonalNgramPersistence.LoadException(file, error)
        }
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
                clearRejectionLocked(tag, a, b, c)
                pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.incrementAndGet() >= FLUSH_EVERY_N_COMMITS
            }
            if (shouldFlush) {
                flushAndAwait(tag)
            }
        }
    }

    suspend fun learnAndAwait(prev2: String, prev1: String, currWord: String, locale: FlorisLocale) {
        val a = normalize(prev2)
        val b = normalize(prev1)
        val c = normalize(currWord)
        if (a.isEmpty() || b.isEmpty() || c.isEmpty()) return
        val tag = locale.languageTag()
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
            clearRejectionLocked(tag, a, b, c)
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

    private fun clearRejectionLocked(localeTag: String, prev2: String, prev1: String, next: String): Boolean {
        val table = rejectionCountsByLocale[localeTag] ?: return false
        val recencyTable = rejectionLastSeenByLocale[localeTag]
        val key = contextKey(prev2, prev1)
        var changed = false
        synchronized(table) {
            val nextMap = table[key]
            if (nextMap?.remove(next) != null) {
                changed = true
                recencyTable?.get(key)?.remove(next)
                if (nextMap.isEmpty()) {
                    table.remove(key)
                    recencyTable?.remove(key)
                }
            }
        }
        return changed
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
        val rejectionCounts = rejectionCountsByLocale[localeTag]?.let { rejections ->
            synchronized(rejections) {
                rejections[key]?.toMap().orEmpty()
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
        val rejectionCount = rejectionCountsByLocale[localeTag]?.let { rejections ->
            synchronized(rejections) {
                rejections[key]?.get(c)
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
            lastSeenMs = snapshot.second[c] ?: now,
            maxScore = maxScore,
            nowMs = now,
        ) * rejectionDiscount(rejectionCount)
    }

    suspend fun rejectionPenalty(prev2: String, prev1: String, currWord: String, locale: FlorisLocale): Double {
        val a = normalize(prev2)
        val b = normalize(prev1)
        val c = normalize(currWord)
        if (a.isEmpty() || b.isEmpty() || c.isEmpty()) return 0.0
        val localeTag = locale.languageTag()
        val key = contextKey(a, b)
        ensureLoaded(localeTag)
        val rejectionCount = rejectionCountsByLocale[localeTag]?.let { rejections ->
            synchronized(rejections) {
                rejections[key]?.get(c)
            }
        } ?: 0
        return (1.0 - rejectionDiscount(rejectionCount)).coerceIn(0.0, 1.0)
    }

    /**
     * Returns the total number of learned trigram continuations across all
     * loaded and persisted locales. Used by the local-only typing stats screen.
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

    suspend fun snapshot(maxEntries: Int = 500): List<LearnedTrigram> {
        if (maxEntries <= 0) return emptyList()
        val localeTags = knownLocaleTags()
        val rows = loadGuard.withLock {
            buildList {
                for (localeTag in localeTags) {
                    val table = loadOrSkipLocked(localeTag) ?: continue
                    val recencyTable = lastSeenByLocale[localeTag].orEmpty()
                    synchronized(table) {
                        for ((ctxKey, nextMap) in table) {
                            val parts = ctxKey.split(CONTEXT_DELIMITER, limit = 2)
                            if (parts.size != 2) continue
                            val prev2 = parts[0]
                            val prev1 = parts[1]
                            val recencyNextMap = recencyTable[ctxKey].orEmpty()
                            for ((next, count) in nextMap) {
                                add(
                                    LearnedTrigram(
                                        localeTag = localeTag,
                                        prev2 = prev2,
                                        prev1 = prev1,
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
            compareByDescending<LearnedTrigram> { it.count }
                .thenByDescending { it.lastSeenMs }
                .thenBy { it.prev2 }
                .thenBy { it.prev1 }
                .thenBy { it.next }
        ).take(maxEntries)
    }

    private fun knownLocaleTags(): Set<String> = buildSet {
        context.filesDir.listFiles { _, name ->
            name.startsWith("personal_trigrams_") && name.endsWith(".tsv") && !name.endsWith(".tsv.tmp")
        }?.forEach { file ->
            add(file.name.removePrefix("personal_trigrams_").removeSuffix(".tsv"))
        }
        context.filesDir.listFiles { _, name ->
            name.startsWith("personal_trigram_rejections_") &&
                name.endsWith(".tsv") &&
                !name.endsWith(".tsv.tmp")
        }?.forEach { file ->
            add(file.name.removePrefix("personal_trigram_rejections_").removeSuffix(".tsv"))
        }
        synchronized(tablesByLocale) {
            addAll(tablesByLocale.keys)
        }
    }

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
            val snapshot: List<TrigramSnapshot>
            val rejectionSnapshot: List<TrigramRejectionSnapshot>
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
                synchronized(rejectionTable) {
                    if (rejectionTable.size > MAX_REJECTED_CONTEXTS) {
                        val keepKeys = rejectionTable.entries
                            .sortedByDescending { e -> e.value.values.sum() }
                            .take(MAX_REJECTED_CONTEXTS)
                            .map { it.key }
                            .toSet()
                        val removeKeys = rejectionTable.keys.filter { it !in keepKeys }
                        for (k in removeKeys) {
                            rejectionTable.remove(k)
                            rejectionRecencyTable.remove(k)
                        }
                    }
                    for ((ctxKey, nextMap) in rejectionTable) {
                        if (nextMap.size > MAX_REJECTIONS_PER_CONTEXT) {
                            val keepKeys = nextMap.entries
                                .sortedByDescending { it.value }
                                .take(MAX_REJECTIONS_PER_CONTEXT)
                                .map { it.key }
                                .toSet()
                            val removeKeys = nextMap.keys.filter { it !in keepKeys }
                            val recencyNextMap = rejectionRecencyTable[ctxKey]
                            for (k in removeKeys) {
                                nextMap.remove(k)
                                recencyNextMap?.remove(k)
                            }
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
                rejectionSnapshot = buildList {
                    val now = System.currentTimeMillis()
                    synchronized(rejectionTable) {
                        for ((ctxKey, nextMap) in rejectionTable) {
                            val parts = ctxKey.split(CONTEXT_DELIMITER, limit = 2)
                            if (parts.size != 2) continue
                            val prev2 = parts[0]
                            val prev1 = parts[1]
                            val recencyNextMap = rejectionRecencyTable[ctxKey].orEmpty()
                            for ((next, count) in nextMap) {
                                add(
                                    TrigramRejectionSnapshot(
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
            val rejectionsPersisted = PersonalNgramPersistence.atomicReplace(rejectionFileFor(localeTag)) { w ->
                for (row in rejectionSnapshot) {
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
            if (!rejectionsPersisted) {
                flogWarning(LogTopic.DICTIONARY) {
                    "Personal trigram rejection flush for '$localeTag' failed; previous on-disk state preserved"
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
     * Forgets every trigram in [locale] that has [rawWord] as its `next` value.
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
        flushAndAwait(tag)
    }

    suspend fun forgetExactAndAwait(prev2: String, prev1: String, currWord: String, locale: FlorisLocale) {
        val a = normalize(prev2)
        val b = normalize(prev1)
        val c = normalize(currWord)
        if (a.isEmpty() || b.isEmpty() || c.isEmpty()) return
        val tag = locale.languageTag()
        val key = contextKey(a, b)
        var changed = false
        loadGuard.withLock {
            val table = ensureLoadedLocked(tag)
            val recencyTable = lastSeenByLocale.getOrPut(tag) { HashMap() }
            synchronized(table) {
                val nextMap = table[key]
                if (nextMap?.remove(c) != null) {
                    changed = true
                    recencyTable[key]?.remove(c)
                    if (nextMap.isEmpty()) {
                        table.remove(key)
                        recencyTable.remove(key)
                    }
                    pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.set(FLUSH_EVERY_N_COMMITS)
                }
            }
        }
        if (changed) {
            flushAndAwait(tag)
        }
    }

    fun rejectContinuation(prev2: String, prev1: String, currWord: String, locale: FlorisLocale) {
        ioScope.launch {
            rejectContinuationAndAwait(prev2, prev1, currWord, locale)
        }
    }

    suspend fun rejectContinuationAndAwait(
        prev2: String,
        prev1: String,
        currWord: String,
        locale: FlorisLocale,
    ): Boolean {
        val a = normalize(prev2)
        val b = normalize(prev1)
        val c = normalize(currWord)
        if (a.isEmpty() || b.isEmpty() || c.isEmpty()) return false
        val tag = locale.languageTag()
        val key = contextKey(a, b)
        var changed = false
        loadGuard.withLock {
            ensureLoadedLocked(tag)
            val table = rejectionCountsByLocale.getOrPut(tag) { HashMap() }
            val recencyTable = rejectionLastSeenByLocale.getOrPut(tag) { HashMap() }
            val now = System.currentTimeMillis()
            synchronized(table) {
                val nextMap = table.getOrPut(key) { HashMap() }
                val newCount = (nextMap[c] ?: 0) + 1
                nextMap[c] = newCount.coerceAtMost(MAX_REJECTION_COUNT)
                val recencyNextMap = recencyTable.getOrPut(key) { HashMap() }
                recencyNextMap[c] = now
                pendingCommitsByLocale.getOrPut(tag) { AtomicInteger(0) }.set(FLUSH_EVERY_N_COMMITS)
                changed = true
            }
        }
        if (changed) {
            flushAndAwait(tag)
        }
        return changed
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
            synchronized(rejectionCountsByLocale) { rejectionCountsByLocale.clear() }
            synchronized(rejectionLastSeenByLocale) { rejectionLastSeenByLocale.clear() }
            pendingCommitsByLocale.clear()
            loadStates.clear()
            runCatching {
                context.filesDir.listFiles { _, name -> name.startsWith("personal_trigrams_") }
                    ?.forEach { it.delete() }
                context.filesDir.listFiles { _, name -> name.startsWith("personal_trigram_rejections_") }
                    ?.forEach { it.delete() }
            }
        }
    }
}
