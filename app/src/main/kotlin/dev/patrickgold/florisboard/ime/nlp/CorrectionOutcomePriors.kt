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

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import dev.patrickgold.florisboard.ime.dictionary.PersonalNgramPersistence
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import dev.patrickgold.florisboard.lib.devtools.flogError

internal data class CorrectionOutcomeSignal(
    val acceptedConfidence: Double = 0.0,
    val rejectedConfidence: Double = 0.0,
)

internal enum class CorrectionAccuracyTrend {
    NO_BASELINE,
    FEWER,
    MORE,
    UNCHANGED,
}

internal data class CorrectionAccuracyDelta(
    val currentWeekAccepted: Int,
    val previousWeekAccepted: Int,
) {
    val changePercent: Int?
        get() {
            if (previousWeekAccepted <= 0) return null
            val delta = kotlin.math.abs(currentWeekAccepted - previousWeekAccepted)
            return ((delta * 100.0) / previousWeekAccepted).toInt()
        }

    val trend: CorrectionAccuracyTrend
        get() = when {
            previousWeekAccepted <= 0 -> CorrectionAccuracyTrend.NO_BASELINE
            currentWeekAccepted < previousWeekAccepted -> CorrectionAccuracyTrend.FEWER
            currentWeekAccepted > previousWeekAccepted -> CorrectionAccuracyTrend.MORE
            else -> CorrectionAccuracyTrend.UNCHANGED
        }
}

/**
 * Local, bounded evidence of correction outcomes. This stores only normalized
 * typed/corrected pairs so the scorer can distinguish corrections the user
 * keeps accepting from corrections they repeatedly undo.
 */
internal class CorrectionOutcomePriors private constructor(
    private val storageFile: File?,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
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
            "Personal correction-outcome background work failed; continuing without it: ${error.message}"
        }
    }
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + ioExceptionHandler)
    private val entries = LinkedHashMap<String, OutcomeEntry>(MaxEntries, 0.75f, true)
    private val weeklyStats = LinkedHashMap<Long, WeeklyOutcomeEntry>(MaxWeeklyBuckets, 0.75f, true)
    private var loaded = storageFile == null
    @Volatile
    var storageState: PersonalNgramPersistence.LoadState = if (storageFile == null) {
        PersonalNgramPersistence.LoadState.READY
    } else {
        PersonalNgramPersistence.LoadState.NOT_LOADED
    }
        private set
    private val persistMutex = Mutex()
    private val persistSequence = AtomicLong(0)
    private val lastPersistedSequence = AtomicLong(0)

    @Synchronized
    fun recordAccepted(originalText: CharSequence, correctedText: CharSequence) {
        val key = pairKey(originalText, correctedText) ?: return
        ensureLoadedLocked()
        val entry = entries.getOrPut(key) { OutcomeEntry() }
        entry.acceptedCount = (entry.acceptedCount + 1).coerceAtMost(MaxCount)
        if (entry.rejectedCount > 0) {
            entry.rejectedCount -= 1
        }
        val now = nowProvider()
        entry.lastSeenMs = now
        val weeklyEntry = weeklyEntryLocked(now)
        weeklyEntry.acceptedCount = (weeklyEntry.acceptedCount + 1).coerceAtMost(MaxWeeklyCount)
        trimLocked()
        persistLocked()
    }

    @Synchronized
    fun recordRejected(originalText: CharSequence, correctedText: CharSequence) {
        val key = pairKey(originalText, correctedText) ?: return
        ensureLoadedLocked()
        val entry = entries.getOrPut(key) { OutcomeEntry() }
        if (entry.acceptedCount > 0) {
            entry.acceptedCount -= 1
        }
        entry.rejectedCount = (entry.rejectedCount + 1).coerceAtMost(MaxCount)
        val now = nowProvider()
        entry.lastSeenMs = now
        val weeklyEntry = weeklyEntryLocked(now)
        weeklyEntry.rejectedCount = (weeklyEntry.rejectedCount + 1).coerceAtMost(MaxWeeklyCount)
        trimLocked()
        persistLocked()
    }

    @Synchronized
    fun signal(originalText: CharSequence, correctedText: CharSequence): CorrectionOutcomeSignal {
        val key = pairKey(originalText, correctedText) ?: return CorrectionOutcomeSignal()
        ensureLoadedLocked()
        val entry = entries[key] ?: return CorrectionOutcomeSignal()
        return CorrectionOutcomeSignal(
            acceptedConfidence = (entry.acceptedCount.toDouble() / AcceptedCountForFullConfidence)
                .coerceIn(0.0, 1.0),
            rejectedConfidence = (entry.rejectedCount.toDouble() / RejectedCountForFullConfidence)
                .coerceIn(0.0, 1.0),
        )
    }

    @Synchronized
    fun entryCount(): Int {
        ensureLoadedLocked()
        return entries.size
    }

    @Synchronized
    fun accuracyDelta(): CorrectionAccuracyDelta {
        ensureLoadedLocked()
        val currentWeek = weekIndex(nowProvider())
        return CorrectionAccuracyDelta(
            currentWeekAccepted = weeklyStats[currentWeek]?.acceptedCount ?: 0,
            previousWeekAccepted = weeklyStats[currentWeek - 1]?.acceptedCount ?: 0,
        )
    }

    @Synchronized
    fun reset() {
        loaded = true
        storageState = PersonalNgramPersistence.LoadState.READY
        entries.clear()
        weeklyStats.clear()
        val file = storageFile
        if (file != null) {
            ioScope.launch {
                file.delete()
            }
        }
    }

    suspend fun resetAndAwait() {
        val file = synchronized(this) {
            loaded = true
            storageState = PersonalNgramPersistence.LoadState.READY
            entries.clear()
            weeklyStats.clear()
            storageFile
        }
        withContext(Dispatchers.IO) {
            runCatching {
                file?.delete()
            }
        }
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        val file = storageFile ?: return
        if (!file.exists() || file.length() <= 0L) {
            loaded = true
            storageState = PersonalNgramPersistence.LoadState.READY
            return
        }
        val loadedEntries = LinkedHashMap<String, OutcomeEntry>(MaxEntries, 0.75f, true)
        val loadedWeeklyStats = LinkedHashMap<Long, WeeklyOutcomeEntry>(MaxWeeklyBuckets, 0.75f, true)
        try {
            file.bufferedReader().useLines { lines ->
                for ((index, line) in lines.withIndex()) {
                    if (line.isBlank()) continue
                    val parts = line.split('\t')
                    if (parts.firstOrNull() == WeeklyMetaPrefix) {
                        require(parts.size == 4) { "invalid weekly row at ${file.name}:${index + 1}" }
                        val week = parts[1].toLongOrNull()
                            ?: error("invalid week at ${file.name}:${index + 1}")
                        val accepted = parts[2].toIntOrNull()?.takeIf { it >= 0 }
                            ?: error("invalid accepted count at ${file.name}:${index + 1}")
                        val rejected = parts[3].toIntOrNull()?.takeIf { it >= 0 }
                            ?: error("invalid rejected count at ${file.name}:${index + 1}")
                        if (accepted == 0 && rejected == 0) continue
                        loadedWeeklyStats[week] = WeeklyOutcomeEntry(
                            acceptedCount = accepted.coerceAtMost(MaxWeeklyCount),
                            rejectedCount = rejected.coerceAtMost(MaxWeeklyCount),
                        )
                        continue
                    }
                    require(parts.size == 5) { "invalid outcome row at ${file.name}:${index + 1}" }
                    val key = keyFromNormalized(parts[0], parts[1])
                        ?: error("invalid outcome pair at ${file.name}:${index + 1}")
                    val accepted = parts[2].toIntOrNull()?.takeIf { it >= 0 }
                        ?: error("invalid accepted count at ${file.name}:${index + 1}")
                    val rejected = parts[3].toIntOrNull()?.takeIf { it >= 0 }
                        ?: error("invalid rejected count at ${file.name}:${index + 1}")
                    val lastSeen = parts[4].toLongOrNull()?.takeIf { it > 0L }
                        ?: error("invalid timestamp at ${file.name}:${index + 1}")
                    if (accepted == 0 && rejected == 0) continue
                    loadedEntries[key] = OutcomeEntry(
                        acceptedCount = accepted.coerceAtMost(MaxCount),
                        rejectedCount = rejected.coerceAtMost(MaxCount),
                        lastSeenMs = lastSeen,
                    )
                }
            }
        } catch (error: Throwable) {
            storageState = PersonalNgramPersistence.LoadState.UNREADABLE
            val loadError = if (error is PersonalNgramPersistence.LoadException) {
                error
            } else {
                PersonalNgramPersistence.LoadException(file, error)
            }
            flogWarning(LogTopic.DICTIONARY) {
                "Correction outcome load for '${file.name}' failed; in-memory and on-disk state preserved"
            }
            throw loadError
        }
        trimMaps(loadedEntries, loadedWeeklyStats)
        entries.putAll(loadedEntries)
        weeklyStats.putAll(loadedWeeklyStats)
        loaded = true
        storageState = PersonalNgramPersistence.LoadState.READY
    }

    private fun trimMaps(
        entries: MutableMap<String, OutcomeEntry>,
        weeklyStats: MutableMap<Long, WeeklyOutcomeEntry>,
    ) {
        if (entries.size > MaxEntries) {
            val keep = entries.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, OutcomeEntry>> { it.value.lastSeenMs }
                        .thenByDescending { it.value.acceptedCount + it.value.rejectedCount }
                )
                .take(MaxEntries)
                .map { it.key }
                .toHashSet()
            entries.keys.removeAll { it !in keep }
        }
        if (weeklyStats.size > MaxWeeklyBuckets) {
            val keepWeeks = weeklyStats.keys
                .sortedDescending()
                .take(MaxWeeklyBuckets)
                .toHashSet()
            weeklyStats.keys.removeAll { it !in keepWeeks }
        }
    }

    private fun trimLocked() {
        trimMaps(entries, weeklyStats)
    }

    private fun weeklyEntryLocked(nowMs: Long): WeeklyOutcomeEntry {
        return weeklyStats.getOrPut(weekIndex(nowMs)) { WeeklyOutcomeEntry() }
    }

    private fun persistLocked() {
        val file = storageFile ?: return
        val snapshot = entries.mapNotNull { (key, entry) ->
            val separatorIndex = key.indexOf(PairSeparator)
            if (separatorIndex <= 0 || separatorIndex >= key.lastIndex) {
                null
            } else {
                OutcomeSnapshot(
                    original = key.substring(0, separatorIndex),
                    corrected = key.substring(separatorIndex + 1),
                    acceptedCount = entry.acceptedCount,
                    rejectedCount = entry.rejectedCount,
                    lastSeenMs = entry.lastSeenMs,
                )
            }
        }
        val weeklySnapshot = weeklyStats.entries
            .sortedByDescending { it.key }
            .take(MaxWeeklyBuckets)
            .map { (week, entry) ->
                WeeklyOutcomeSnapshot(
                    weekIndex = week,
                    acceptedCount = entry.acceptedCount,
                    rejectedCount = entry.rejectedCount,
                )
            }
        // Serialize writers and drop stale snapshots: rapid accept/undo pairs
        // used to launch concurrent coroutines sharing one .tmp path (torn or
        // interleaved file), and the old rename fallback deleted the
        // destination before the replacement rename. atomicReplace never
        // deletes the destination and fsyncs the temp file first.
        val sequence = persistSequence.incrementAndGet()
        ioScope.launch {
            persistMutex.withLock {
                if (sequence <= lastPersistedSequence.get()) return@withLock
                lastPersistedSequence.set(sequence)
                file.parentFile?.mkdirs()
                val persisted = PersonalNgramPersistence.atomicReplace(file) { writer ->
                    for (row in weeklySnapshot) {
                        if (row.acceptedCount == 0 && row.rejectedCount == 0) continue
                        writer.write(WeeklyMetaPrefix)
                        writer.write('\t'.code)
                        writer.write(row.weekIndex.toString())
                        writer.write('\t'.code)
                        writer.write(row.acceptedCount.toString())
                        writer.write('\t'.code)
                        writer.write(row.rejectedCount.toString())
                        writer.newLine()
                    }
                    for (row in snapshot) {
                        if (row.acceptedCount == 0 && row.rejectedCount == 0) continue
                        writer.write(row.original)
                        writer.write('\t'.code)
                        writer.write(row.corrected)
                        writer.write('\t'.code)
                        writer.write(row.acceptedCount.toString())
                        writer.write('\t'.code)
                        writer.write(row.rejectedCount.toString())
                        writer.write('\t'.code)
                        writer.write(row.lastSeenMs.toString())
                        writer.newLine()
                    }
                }
                if (!persisted) {
                    storageState = PersonalNgramPersistence.LoadState.WRITE_FAILED
                    flogWarning(LogTopic.DICTIONARY) {
                        "Correction outcome flush for '${file.name}' failed; previous on-disk state preserved"
                    }
                } else if (storageState != PersonalNgramPersistence.LoadState.UNREADABLE) {
                    storageState = PersonalNgramPersistence.LoadState.READY
                }
            }
        }
    }

    private data class OutcomeEntry(
        var acceptedCount: Int = 0,
        var rejectedCount: Int = 0,
        var lastSeenMs: Long = System.currentTimeMillis(),
    )

    private data class WeeklyOutcomeEntry(
        var acceptedCount: Int = 0,
        var rejectedCount: Int = 0,
    )

    private data class OutcomeSnapshot(
        val original: String,
        val corrected: String,
        val acceptedCount: Int,
        val rejectedCount: Int,
        val lastSeenMs: Long,
    )

    private data class WeeklyOutcomeSnapshot(
        val weekIndex: Long,
        val acceptedCount: Int,
        val rejectedCount: Int,
    )

    companion object {
        private const val FileName = "correction_outcome_priors.tsv"
        private const val WeeklyMetaPrefix = "#week"
        private const val PairSeparator = '\u001f'
        private const val MaxEntries = 1024
        private const val MaxCount = 8
        private const val MaxWeeklyBuckets = 8
        private const val MaxWeeklyCount = 100_000
        private const val WeekDurationMs = 7L * 24L * 60L * 60L * 1000L
        private const val AcceptedCountForFullConfidence = 3.0
        private const val RejectedCountForFullConfidence = 2.0

        @Volatile
        private var instance: CorrectionOutcomePriors? = null

        fun get(context: Context): CorrectionOutcomePriors {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val file = File(context.applicationContext.filesDir, FileName)
                return CorrectionOutcomePriors(file).also { instance = it }
            }
        }

        fun inMemory(): CorrectionOutcomePriors {
            return CorrectionOutcomePriors(storageFile = null)
        }

        fun inMemory(nowProvider: () -> Long): CorrectionOutcomePriors {
            return CorrectionOutcomePriors(storageFile = null, nowProvider = nowProvider)
        }

        internal fun fromFile(file: File, nowProvider: () -> Long = { System.currentTimeMillis() }): CorrectionOutcomePriors {
            return CorrectionOutcomePriors(storageFile = file, nowProvider = nowProvider)
        }

        private fun pairKey(originalText: CharSequence, correctedText: CharSequence): String? {
            val original = normalizeWord(originalText) ?: return null
            val corrected = normalizeWord(correctedText) ?: return null
            return keyFromNormalized(original, corrected)
        }

        private fun keyFromNormalized(original: String, corrected: String): String? {
            if (original == corrected) return null
            if (original.isBlank() || corrected.isBlank()) return null
            return "$original$PairSeparator$corrected"
        }

        private fun normalizeWord(text: CharSequence): String? {
            val normalized = text
                .toString()
                .trim()
                .trim { char: Char -> !char.isLetterOrDigit() && char != '\'' && char != '\u2019' }
                .lowercase()
            if (normalized.isBlank() || normalized.none { it.isLetterOrDigit() }) return null
            if (normalized.any { char -> !char.isLetterOrDigit() && char != '\'' && char != '\u2019' }) {
                return null
            }
            return normalized
        }

        private fun weekIndex(nowMs: Long): Long {
            return Math.floorDiv(nowMs, WeekDurationMs)
        }
    }
}
