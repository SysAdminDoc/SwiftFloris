/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.latin.LatinDictionaryStore
import dev.patrickgold.florisboard.ime.security.EncryptedDatabaseFiles
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import dev.patrickgold.florisboard.lib.util.debugSummarizeTextForLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

private const val FLORIS_USER_DICTIONARY_SOURCE_PRIORITY = 0
private const val SYSTEM_USER_DICTIONARY_SOURCE_PRIORITY = 1
private const val SHORTCUT_MATCH_PRIORITY = 0
private const val PREFIX_MATCH_PRIORITY = 1
private const val CONTAINS_MATCH_PRIORITY = 2
internal const val DICTIONARY_ROOM_SYNC_TIMEOUT_MS = 150L

/**
 * Frequency starting point assigned to a freshly-learned word.
 *
 * SwiftKey-style "instant remember" — set high enough that a single
 * commit makes the word a top suggestion for its prefix and removes
 * the spell-check underline. 245 / 255 ≈ 0.96 weight. Kept in
 * sync with [UserDictionaryOverlay.INITIAL_FREQUENCY] so disk +
 * in-memory agree on the per-word weight.
 */
private const val LEARN_INITIAL_FREQUENCY = 245
/**
 * Frequency increment applied each time an existing learned word is reinforced. One step per use,
 * so each of a word's first 11 uses ranks it above the learned words typed less often. It was +5
 * up to 250: the second use hit the cap and every learned word tied from then on.
 */
private const val LEARN_INCREMENT = 1
/** Cap learned-word frequency at the top of the scale, reached on the 11th use. */
private const val LEARN_MAX_FREQUENCY = 255
/** How long a change to the words waiting to be learned sits before it's written, so a burst of
 *  sightings costs one write. Hiding the keyboard writes it at once. */
private const val PENDING_LEARNED_WORDS_SAVE_DELAY_MS = 3_000L

/** Prefix of the broken pre-v1.9.56 learned-row locale format (`FlorisLocale.toString()`). */
internal const val LEGACY_DEBUG_LOCALE_PREFIX = "FlorisLocale {"
private val LEGACY_DEBUG_LOCALE_REGEX =
    Regex("""^FlorisLocale \{ l=(\S*) c=(\S*) v=(\S*) \}$""")

/**
 * Parses the pre-v1.9.56 debug locale string back into a proper locale tag
 * (`en_US`), or `null` (= all locales) when the string cannot be parsed or
 * carries no language.
 */
internal fun parseLegacyDebugLocaleTag(debugString: String): String? {
    val match = LEGACY_DEBUG_LOCALE_REGEX.matchEntire(debugString.trim()) ?: return null
    val (language, country, variant) = match.destructured
    if (language.isBlank()) return null
    val locale = when {
        variant.isNotBlank() -> FlorisLocale.from(language, country, variant)
        country.isNotBlank() -> FlorisLocale.from(language, country)
        else -> FlorisLocale.from(language)
    }
    return locale.localeTag().takeIf { it.isNotBlank() }
}

internal data class LegacyLearnedLocaleRepairResult(
    val rewritten: Int,
    val merged: Int,
)

internal fun repairLegacyLearnedLocaleRows(dao: UserDictionaryDao): LegacyLearnedLocaleRepairResult {
    val all = dao.queryAll()
    val broken = all.filter { it.locale?.startsWith(LEGACY_DEBUG_LOCALE_PREFIX) == true }
    if (broken.isEmpty()) return LegacyLearnedLocaleRepairResult(rewritten = 0, merged = 0)

    val healthyByKey = HashMap<Pair<String, String?>, UserDictionaryEntry>()
    for (entry in all) {
        if (entry.locale?.startsWith(LEGACY_DEBUG_LOCALE_PREFIX) == true) continue
        healthyByKey.putIfAbsent(entry.word.lowercase() to entry.locale, entry)
    }

    var rewritten = 0
    var merged = 0
    for (entry in broken) {
        val fixedTag = parseLegacyDebugLocaleTag(entry.locale.orEmpty())
        val key = entry.word.lowercase() to fixedTag
        val existing = healthyByKey[key]
        if (existing != null) {
            if (entry.freq > existing.freq) {
                val updated = existing.copy(freq = entry.freq)
                dao.update(updated)
                healthyByKey[key] = updated
            }
            dao.delete(entry)
            merged++
        } else {
            val updated = entry.copy(locale = fixedTag)
            dao.update(updated)
            healthyByKey[key] = updated
            rewritten++
        }
    }
    return LegacyLearnedLocaleRepairResult(rewritten = rewritten, merged = merged)
}

/** File in `filesDir` listing the misspellings already cleaned out of learned words, one per line. */
internal const val LEARNED_MISSPELLING_CLEANUP_FILE = "learned_misspelling_cleanup.txt"

/**
 * File in `filesDir` listing the unvetted words (`ime/dict/en_unvetted.txt`) already cleaned out of
 * learned words, one per line. Same scheme as [LEARNED_MISSPELLING_CLEANUP_FILE].
 */
internal const val LEARNED_SLIP_CLEANUP_FILE = "learned_slip_cleanup.txt"

/**
 * Misspellings from [table] that the learned-word cleanup has not handled yet, given the
 * contents of [LEARNED_MISSPELLING_CLEANUP_FILE] ([processedMarker], `null` when absent).
 * Each word is cleaned once, so a misspelling the user teaches the keyboard afterwards stays.
 */
internal fun pendingLearnedMisspellingCleanup(table: Set<String>, processedMarker: String?): Set<String> {
    val processed = parseLearnedMisspellingMarker(processedMarker)
    if (processed.isEmpty()) return table
    return table.filterNotTo(HashSet()) { it in processed }
}

private fun parseLearnedMisspellingMarker(marker: String?): Set<String> {
    if (marker.isNullOrBlank()) return emptySet()
    return marker.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
}

/**
 * Lowest frequency the misspelling cleanup treats as learned: v1.8.26 learned new words at 240,
 * later versions at [LEARN_INITIAL_FREQUENCY]. v1.6.0 to v1.8.25 started at 80 and added 6 per
 * use, and later uses added 5, so those rows can sit anywhere from 80 to 250. Nothing tells one of
 * them from a frequency picked by hand, so they're left alone; the chip that forgets a learned
 * word and the personal dictionary screen still remove them.
 */
internal const val LEARNED_MISSPELLING_MIN_FREQUENCY = 240

/**
 * True for a row that [DictionaryManager.learnWord] wrote for an English misspelling: lowercase,
 * no shortcut, an English locale tag and a frequency in the auto-learn band. Hand-added words
 * default to [FREQUENCY_DEFAULT] and keep their typed case, so they don't match.
 */
internal fun isAutoLearnedMisspelling(entry: UserDictionaryEntry, misspellings: Set<String>): Boolean {
    if (entry.shortcut != null) return false
    if (entry.freq !in LEARNED_MISSPELLING_MIN_FREQUENCY..LEARN_MAX_FREQUENCY) return false
    if (entry.word != entry.word.lowercase() || entry.word !in misspellings) return false
    val language = entry.locale?.substringBefore('_')?.substringBefore('-') ?: return false
    return language == "en"
}

/**
 * Deletes the rows that [isAutoLearnedMisspelling] matches and returns them. Before the bundled
 * misspelling table, words like "teh" and "untill" were in the English word list, so typing one
 * learned it, and a learned word blocks the fix.
 */
internal fun removeAutoLearnedMisspellings(
    dao: UserDictionaryDao,
    misspellings: Set<String>,
): List<UserDictionaryEntry> {
    if (misspellings.isEmpty()) return emptyList()
    val removed = dao.queryAll().filter { isAutoLearnedMisspelling(it, misspellings) }
    for (entry in removed) dao.delete(entry)
    return removed
}

internal class LearnedMisspellingCleanupResult(val removed: List<UserDictionaryEntry>, val markerSaved: Boolean)

/**
 * Cleans the [table] words [markerFile] doesn't list yet out of the learned words: deletes the rows
 * [removeAutoLearnedMisspellings] matches, hands each one to [forget] so the caller can drop it
 * from memory, then adds the whole table to the marker. Words already in the marker stay there, so
 * one that leaves the table and comes back isn't cleaned a second time. Returns `null` when
 * every word was already handled.
 */
internal fun cleanUpLearnedMisspellingsOnce(
    dao: UserDictionaryDao,
    table: Set<String>,
    markerFile: File,
    forget: (UserDictionaryEntry) -> Unit,
): LearnedMisspellingCleanupResult? {
    if (table.isEmpty()) return null
    val marker = if (markerFile.isFile) markerFile.readText() else null
    val pending = pendingLearnedMisspellingCleanup(table, marker)
    if (pending.isEmpty()) return null
    val removed = removeAutoLearnedMisspellings(dao, pending)
    removed.forEach(forget)
    val markerSaved = PersonalNgramPersistence.atomicReplace(markerFile) { writer ->
        for (word in (parseLearnedMisspellingMarker(marker) + table).sorted()) {
            writer.write(word)
            writer.write("\n")
        }
    }
    return LearnedMisspellingCleanupResult(removed, markerSaved)
}

/**
 * Coordinates SwiftFloris' internal user dictionary and the platform user dictionary.
 *
 * NLP providers own bundled and downloadable language dictionaries. This manager overlays user-managed entries on top
 * of those providers: the platform user dictionary is treated as a system-level source, and SwiftFloris' internal user
 * dictionary is the highest-priority source for conflicts. Stores are opened lazily when an accessor first needs them.
 */
class DictionaryManager private constructor(context: Context) {
    private val applicationContext: Context = context.applicationContext ?: context
    private val prefs by FlorisPreferenceStore
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val legacyLocaleRepairStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val roomSyncThreadIndex = AtomicInteger(0)
    private val roomSyncExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "SwiftFloris-DictionaryRoomSync-${roomSyncThreadIndex.incrementAndGet()}").apply {
            isDaemon = true
        }
    }

    private var florisUserDictionaryDatabase: FlorisUserDictionaryDatabase? = null
    private val learnedWordWriter = LearnedWordWriter(
        dao = { florisUserDictionaryDao() },
        initialFrequency = LEARN_INITIAL_FREQUENCY,
        increment = LEARN_INCREMENT,
        maxFrequency = LEARN_MAX_FREQUENCY,
    )
    private var systemUserDictionaryDatabase: SystemUserDictionaryDatabase? = null
    private val florisUserDictionaryOpenExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SwiftFloris-UserDictionaryOpen").apply { isDaemon = true }
    }
    private val florisUserDictionaryLoader = UserDictionaryStoreLoader<FlorisUserDictionaryDatabase>(
        now = SystemClock::elapsedRealtime,
        resetNotice = FileResetNotice(
            lazy { applicationContext.getDatabasePath("${FlorisUserDictionaryDatabase.DB_FILE_NAME}.reset-notice") },
        ),
        onUnavailable = { failures, retryInMs, error ->
            flogWarning(LogTopic.DICTIONARY) {
                "Encrypted user dictionary unavailable, files kept; attempt $failures, next in $retryInMs ms: " +
                    (error?.let { "${it::class.java.simpleName}: ${it.message}" } ?: "no store")
            }
        },
    )

    /** Whether the personal dictionary opened, is waiting to retry, or was reset; shown on its screen. */
    val florisUserDictionaryStorageState: StateFlow<UserDictionaryStorageState> = florisUserDictionaryLoader.state

    /** New words seen once, learned on their second sighting (see [learnWord]). */
    private val pendingLearnedWords = PendingLearnedWords(
        File(applicationContext.filesDir, PendingLearnedWords.FILE_NAME),
    )
    private val pendingLearnedWordsSaveQueued = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        // Read the list off the main thread before the first space press needs it.
        ioScope.launch { pendingLearnedWords.preload() }
    }

    /**
     * Saves the pending list off the main thread after [PENDING_LEARNED_WORDS_SAVE_DELAY_MS], so the
     * sightings made meanwhile share one write.
     */
    private fun savePendingLearnedWordsAsync() {
        if (!pendingLearnedWordsSaveQueued.compareAndSet(false, true)) return
        ioScope.launch {
            delay(PENDING_LEARNED_WORDS_SAVE_DELAY_MS)
            savePendingLearnedWordsIfQueued()
        }
    }

    /**
     * Writes a waiting change to the pending list now. Called when the keyboard hides, since the
     * process can be killed without warning after that. Returns at once and writes on IO.
     */
    fun flushPendingLearnedWords() {
        if (learnedWordWriter.waitingSize > 0) {
            ioScope.launch {
                val failed = runCatching { learnedWordWriter.retry() }.getOrElse { 1 }
                if (failed > 0) {
                    flogWarning(LogTopic.DICTIONARY) { "Couldn't write $failed learned word uses that waited for the store" }
                }
            }
        }
        if (!pendingLearnedWordsSaveQueued.get()) return
        ioScope.launch { savePendingLearnedWordsIfQueued() }
    }

    private fun savePendingLearnedWordsIfQueued() {
        if (!pendingLearnedWordsSaveQueued.getAndSet(false)) return
        if (!pendingLearnedWords.save()) {
            flogWarning(LogTopic.DICTIONARY) { "Couldn't save the words waiting to be learned" }
        }
    }

    companion object {
        private var defaultInstance: DictionaryManager? = null

        fun init(applicationContext: Context): DictionaryManager {
            val instance = DictionaryManager(applicationContext)
            defaultInstance = instance
            return instance
        }

        fun default(): DictionaryManager {
            val instance = defaultInstance
            if (instance != null) {
                return instance
            } else {
                throw UninitializedPropertyAccessException(
                    "${DictionaryManager::class.simpleName} has not been initialized previously. Make sure to call init(applicationContext) before using default()."
                )
            }
        }
    }

    /**
     * ROADMAP §6 N5.4 — Exact personal-dictionary shortcut match for SwiftKey-style
     * auto-replacement on space/punctuation. Returns the expansion word *only when*
     * [word] (case-insensitive, trimmed) is a complete shortcut entry — never a prefix.
     *
     * Returns null when:
     * - No personal dictionary is enabled
     * - The query doesn't exactly match any shortcut (case-insensitive)
     * - The shortcut and its expansion are identical (no replacement needed)
     */
    fun queryUserDictionaryShortcutExact(word: String, locale: FlorisLocale): String? {
        val query = word.trim()
        if (query.isBlank()) return null

        return runRoomQueryBlocking<String?>(
            operation = "queryUserDictionaryShortcutExact",
            fallback = null,
        ) {
            val florisDao = florisUserDictionaryDao()
            val systemDao = systemUserDictionaryDao()
            if (florisDao == null && systemDao == null) return@runRoomQueryBlocking null

            val matches = buildList {
                if (prefs.dictionary.enableFlorisUserDictionary.get() && florisDao != null) {
                    addAll(florisDao.queryShortcut(query, locale))
                }
                if (prefs.dictionary.enableSystemUserDictionary.get() && systemDao != null) {
                    addAll(systemDao.queryShortcut(query, locale))
                }
            }

            matches.asSequence()
                .filter { it.shortcut.equals(query, ignoreCase = true) }
                .filter { it.word.isNotBlank() && !it.word.equals(query, ignoreCase = true) }
                .sortedByDescending { it.freq }
                .firstOrNull()
                ?.word
        }
    }

    fun queryUserDictionary(word: String, locale: FlorisLocale): List<SuggestionCandidate> {
        val query = word.trim()
        if (query.isBlank()) {
            return emptyList()
        }

        return runRoomQueryBlocking(
            operation = "queryUserDictionary",
            fallback = emptyList(),
        ) {
            val florisDao = florisUserDictionaryDao()
            val systemDao = systemUserDictionaryDao()
            if (florisDao == null && systemDao == null) {
                return@runRoomQueryBlocking emptyList()
            }

            val candidates = buildList {
                if (prefs.dictionary.enableFlorisUserDictionary.get() && florisDao != null) {
                    addAll(florisDao.queryCandidates(query, locale, sourcePriority = FLORIS_USER_DICTIONARY_SOURCE_PRIORITY))
                }
                if (prefs.dictionary.enableSystemUserDictionary.get() && systemDao != null) {
                    addAll(systemDao.queryCandidates(query, locale, sourcePriority = SYSTEM_USER_DICTIONARY_SOURCE_PRIORITY))
                }
            }

            rankUserDictionaryCandidates(query, candidates).map { entry ->
                WordSuggestionCandidate(
                    text = entry.word,
                    confidence = (entry.freq.coerceIn(0, 255) / 255.0).coerceIn(0.0, 1.0),
                    isEligibleForUserRemoval = true,
                )
            }
        }
    }

    /**
     * Removes [rawWord] from the personal Floris user dictionary for [locale]. Returns
     * `true` if at least one row was deleted. Off-thread.
     */
    fun forgetWord(rawWord: String, locale: FlorisLocale): Boolean {
        if (!prefs.dictionary.enableFlorisUserDictionary.get()) return false
        val cleaned = rawWord.trim()
            .trim { ch -> !ch.isLetter() && ch != '\'' && ch != '-' }
        if (cleaned.isBlank()) return false
        val normalized = cleaned.lowercase()
        // Drop the in-memory overlay entry immediately so the next suggest
        // can't surface the forgotten word from the ranker.
        UserDictionaryOverlay.get().forget(normalized, locale)
        if (pendingLearnedWords.remove(normalized, locale.languageTag())) savePendingLearnedWordsAsync()
        return runRoomQueryBlocking(
            operation = "forgetWord(word=${normalized.debugSummarizeTextForLog()})",
            fallback = false,
        ) {
            val dao = florisUserDictionaryDao() ?: return@runRoomQueryBlocking false
            // queryExactFuzzyLocale's `WORD = :word` is case-sensitive in SQLite,
            // so it misses manually-added cased entries ("Patrick" vs "patrick").
            // The LIKE-based query() is ASCII case-insensitive; the equals-filter
            // below then narrows it back to exact (case-insensitive) matches.
            val matches = dao.query(normalized, locale)
                .filter { it.word.equals(normalized, ignoreCase = true) }
            for (entry in matches) dao.delete(entry)
            matches.isNotEmpty()
        }
    }

    /**
     * Auto-learn a word that the user just committed (via space, punctuation, or
     * accepted suggestion). Increments the frequency of an existing entry, or inserts
     * a new entry at [LEARN_INITIAL_FREQUENCY]. Off-thread; safe to call from input
     * event handlers. No-op when the user has disabled the personal dictionary or is
     * in incognito mode (caller is responsible for the incognito gate).
     *
     * With [requireRepeat], a word the learned words don't have yet is only noted the first
     * time and learned the second, so a typo that slips through once isn't offered as a
     * completion from then on. Callers pass it for a word that was typed and kept, not one
     * picked from the strip.
     */
    fun learnWord(rawWord: String, locale: FlorisLocale, requireRepeat: Boolean = false) {
        if (!prefs.dictionary.enableFlorisUserDictionary.get()) return
        // Anything that doesn't look like a real word is skipped (see LearnedWordText).
        val normalized = LearnedWordText.normalize(rawWord) ?: return

        val admission = pendingLearnedWords.admit(
            word = normalized,
            languageTag = locale.languageTag(),
            requireRepeat = requireRepeat,
            alreadyLearned = UserDictionaryOverlay.get().contains(normalized, locale),
        )
        if (admission.listChanged) savePendingLearnedWordsAsync()
        if (!admission.learnNow) return

        // Bump the in-memory overlay first so the next keystroke's
        // suggest() already ranks this word higher — the IO write below is
        // just for durability across process restarts. ROADMAP §7 Next-3
        // ranker tie-in.
        UserDictionaryOverlay.get().learn(normalized, locale)

        ioScope.launch {
            runCatching {
                // A closed store keeps the word for later instead of dropping it (see LearnedWordWriter).
                learnedWordWriter.write(normalized, locale)
            }.onFailure { e ->
                flogError(LogTopic.DICTIONARY) {
                    "learnWord(word=${normalized.debugSummarizeTextForLog()}) DAO write failed: ${e.message}"
                }
                UserDictionaryOverlay.get().forget(normalized, locale)
            }
        }
    }

    /**
     * Notes a sighting of [rawWord] without learning it: puts it on the pending list unless it's
     * learned or already there. For a word whose correction was taken back with backspace, which
     * isn't kept yet (the user may be about to type something else). The space that keeps it
     * when it's typed again learns it.
     */
    fun noteTypedWordSighting(rawWord: String, locale: FlorisLocale) {
        if (!prefs.dictionary.enableFlorisUserDictionary.get()) return
        val normalized = LearnedWordText.normalize(rawWord) ?: return
        if (UserDictionaryOverlay.get().contains(normalized, locale)) return
        if (pendingLearnedWords.addSighting(normalized, locale.languageTag())) savePendingLearnedWordsAsync()
    }

    /**
     * Populate the in-memory [UserDictionaryOverlay] for [locale] from the
     * disk-backed Floris user-dictionary DAO. Idempotent — the overlay
     * tracks which locales have been hydrated and skips the second call.
     * Safe to call from any thread; the heavy DAO scan is dispatched onto
     * the IO scope and the function returns immediately.
     */
    /**
     * Drop the in-memory overlay for [locale] and re-hydrate from the
     * current DAO snapshot. Called by Settings → User Dictionary after
     * a manual insert / update / delete so the IME's suggest path picks
     * up the change without waiting for an organic learn-cycle to
     * refresh the overlay. Idempotent + async.
     */
    fun rebuildOverlay(locale: FlorisLocale) {
        UserDictionaryOverlay.get().clearLocale(locale)
        hydrateOverlay(locale)
    }

    fun hydrateOverlay(locale: FlorisLocale) {
        if (!prefs.dictionary.enableFlorisUserDictionary.get()) return
        val overlay = UserDictionaryOverlay.get()
        if (overlay.isHydrated(locale)) return
        ioScope.launch {
            val dao = florisUserDictionaryDao() ?: return@launch
            val pairs = runCatching {
                dao.queryAll(locale).map { entry -> entry.word.lowercase() to entry.freq }
            }.getOrElse { e ->
                // Do NOT mark the locale hydrated on a failed read: hydrateLocale
                // pins its locale for the whole process lifetime, so a transient
                // Room/SQLCipher error here would silently drop the user's saved
                // vocabulary from ranking and spell-check for the session.
                // Leaving it un-hydrated lets the next suggest() retry.
                flogError(LogTopic.DICTIONARY) { "Overlay hydration read failed for $locale: ${e.message}" }
                return@launch
            }
            overlay.hydrateLocale(locale, pairs)
        }
    }

    fun isKnownUserDictionaryWord(word: String, locale: FlorisLocale): Boolean {
        val query = word.trim()
        if (query.isBlank()) {
            return false
        }

        return runRoomQueryBlocking(
            operation = "isKnownUserDictionaryWord",
            fallback = false,
        ) {
            val florisDao = florisUserDictionaryDao()
            val systemDao = systemUserDictionaryDao()
            if (florisDao == null && systemDao == null) {
                return@runRoomQueryBlocking false
            }

            if (prefs.dictionary.enableFlorisUserDictionary.get()) {
                if (florisDao?.containsWordOrShortcut(query, locale) == true) {
                    return@runRoomQueryBlocking true
                }
            }
            if (prefs.dictionary.enableSystemUserDictionary.get()) {
                if (systemDao?.containsWordOrShortcut(query, locale) == true) {
                    return@runRoomQueryBlocking true
                }
            }

            false
        }
    }

    @Synchronized
    fun florisUserDictionaryDao(): UserDictionaryDao? {
        return florisUserDictionaryDatabase()?.userDictionaryDao()
    }

    @Synchronized
    fun florisUserDictionaryDatabase(): FlorisUserDictionaryDatabase? {
        if (!prefs.dictionary.enableFlorisUserDictionary.get()) {
            return null
        }
        loadFlorisUserDictionaryIfNecessary()
        return florisUserDictionaryDatabase
    }

    @Synchronized
    fun systemUserDictionaryDao(): UserDictionaryReadDao? {
        return systemUserDictionaryDatabase()?.userDictionaryDao()
    }

    @Synchronized
    fun systemUserDictionaryDatabase(): SystemUserDictionaryDatabase? {
        if (!prefs.dictionary.enableSystemUserDictionary.get()) {
            return null
        }
        loadSystemUserDictionaryIfNecessary()
        return systemUserDictionaryDatabase
    }

    @Synchronized
    fun loadUserDictionariesIfNecessary() {
        loadFlorisUserDictionaryIfNecessary()
        loadSystemUserDictionaryIfNecessary()
    }

    @Synchronized
    fun syncUserDictionaryStoresWithPreferences() {
        if (prefs.dictionary.enableFlorisUserDictionary.get()) {
            loadFlorisUserDictionaryIfNecessary()
        } else {
            closeFlorisUserDictionary()
        }
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            loadSystemUserDictionaryIfNecessary()
        } else {
            closeSystemUserDictionary()
        }
    }

    @Synchronized
    fun unloadUserDictionariesIfNecessary() {
        closeFlorisUserDictionary()
        closeSystemUserDictionary()
    }

    /**
     * Skips the backoff wait after a failed open, for an explicit "Try again". Returns true when the
     * store is open afterwards.
     */
    @Synchronized
    fun retryFlorisUserDictionaryNow(): Boolean {
        florisUserDictionaryLoader.skipBackoff()
        loadFlorisUserDictionaryIfNecessary()
        return florisUserDictionaryDatabase != null
    }

    /** Clears the reset notice once the user has seen it. */
    fun acknowledgeFlorisUserDictionaryReset() {
        florisUserDictionaryLoader.acknowledgeReset()
    }

    /**
     * Deletes the personal dictionary whether or not it's turned on: the open store, its sidecars,
     * copies kept aside after a reset or migration, the reset notice, the in-memory overlay and
     * the words waiting for a second sighting.
     * Going through [florisUserDictionaryDatabase] would skip a turned-off dictionary, whose words
     * come back the moment it's turned on again. Returns the files that couldn't be deleted.
     */
    @Synchronized
    fun eraseFlorisUserDictionary(): List<File> {
        closeFlorisUserDictionary()
        val remaining = EncryptedDatabaseFiles.deleteEveryCopy(
            applicationContext.getDatabasePath(FlorisUserDictionaryDatabase.DB_FILE_NAME),
        ).toMutableList()
        UserDictionaryOverlay.get().clearAll()
        florisUserDictionaryLoader.acknowledgeReset()
        // A save still waiting would write the emptied list back as a new file.
        pendingLearnedWordsSaveQueued.set(false)
        if (!pendingLearnedWords.deleteFile()) {
            remaining += File(applicationContext.filesDir, PendingLearnedWords.FILE_NAME)
        }
        if (remaining.isNotEmpty()) {
            flogError(LogTopic.DICTIONARY) {
                "Erase left ${remaining.size} user dictionary file(s): ${remaining.joinToString { it.name }}"
            }
        }
        return remaining
    }

    private fun loadFlorisUserDictionaryIfNecessary() {
        val context = applicationContext
        if (florisUserDictionaryDatabase != null || !prefs.dictionary.enableFlorisUserDictionary.get()) {
            return
        }
        val database = florisUserDictionaryLoader.load {
            runOpenToCompletion { openEncryptedFlorisUserDictionary(context) }
        } ?: return
        florisUserDictionaryDatabase = database
        repairLegacyLearnedLocaleRowsAsync()
    }

    /**
     * One-time (per process) repair for rows written by the pre-v1.9.56
     * [learnWord], which persisted `FlorisLocale.toString()` (a debug format,
     * `FlorisLocale { l=en c=US v= }`) instead of the Room TypeConverter's
     * `localeTag()` format (`en_US`). Such rows can never match a
     * locale-scoped query: learned words did not survive process restart,
     * could not be forgotten, and every re-commit of the same word inserted
     * a fresh duplicate row. This pass rewrites each broken row to its
     * intended locale tag and merges duplicates (keeping the highest
     * frequency). Rows whose debug string cannot be parsed fall back to a
     * NULL locale, which every locale-scoped query treats as "all locales".
     */
    private fun repairLegacyLearnedLocaleRowsAsync() {
        if (!legacyLocaleRepairStarted.compareAndSet(false, true)) return
        ioScope.launch {
            val repaired = runCatching {
                val dao = florisUserDictionaryDao() ?: return@launch
                val result = repairLegacyLearnedLocaleRows(dao)
                if (result.rewritten > 0 || result.merged > 0) {
                    flogInfo(LogTopic.DICTIONARY) {
                        "Repaired learned-word locale tags: ${result.rewritten} rewritten, ${result.merged} duplicates merged"
                    }
                }
            }.onFailure { e ->
                flogError(LogTopic.DICTIONARY) { "Legacy learned-locale repair failed: ${e.message}" }
            }.isSuccess
            // Only after a finished locale repair: a row it didn't rewrite to en_* yet wouldn't
            // count as English, and the marker would then skip it for good.
            if (!repaired) return@launch
            runCatching {
                val dao = florisUserDictionaryDao() ?: return@launch
                removeAutoLearnedMisspellingsOnce(dao)
            }.onFailure { e ->
                flogError(LogTopic.DICTIONARY) { "Learned misspelling cleanup failed: ${e.message}" }
            }
            runCatching {
                val dao = florisUserDictionaryDao() ?: return@launch
                removeAutoLearnedSlipsOnce(dao)
            }.onFailure { e ->
                flogError(LogTopic.DICTIONARY) { "Learned slip cleanup failed: ${e.message}" }
            }
        }
    }

    /**
     * Drops English misspellings learned before the bundled misspelling table existed (see
     * [removeAutoLearnedMisspellings]) from the user dictionary, the overlay and the n-gram
     * stores. The words handled are recorded in [LEARNED_MISSPELLING_CLEANUP_FILE], so this runs
     * once per table word and later additions to the table are cleaned on the next start.
     */
    private fun removeAutoLearnedMisspellingsOnce(dao: UserDictionaryDao) {
        val raw = applicationContext.assets.open(LatinDictionaryStore.EnglishMisspellingsPath)
            .bufferedReader().use { it.readText() }
        val bigrams by lazy { PersonalBigramStore.get(applicationContext) }
        val trigrams by lazy { PersonalTrigramStore.get(applicationContext) }
        val result = cleanUpLearnedMisspellingsOnce(
            dao = dao,
            table = LatinDictionaryStore.parseMisspellings(raw).keys,
            markerFile = File(applicationContext.filesDir, LEARNED_MISSPELLING_CLEANUP_FILE),
        ) { entry ->
            val locale = FlorisLocale.fromTag(entry.locale ?: return@cleanUpLearnedMisspellingsOnce)
            UserDictionaryOverlay.get().forget(entry.word, locale)
            bigrams.forget(entry.word, locale)
            trigrams.forget(entry.word, locale)
        } ?: return
        if (result.removed.isNotEmpty()) {
            flogInfo(LogTopic.DICTIONARY) { "Removed ${result.removed.size} learned misspellings" }
        }
        if (!result.markerSaved) {
            flogWarning(LogTopic.DICTIONARY) { "Learned misspelling cleanup marker not saved; retrying next start" }
        }
    }

    /**
     * Drops auto-learned English words that no vetted word list backs (`ime/dict/en_unvetted.txt`,
     * "thos", "snd") the same way [removeAutoLearnedMisspellingsOnce] drops misspellings. Any
     * dictionary word used to be learned on its first use, so one slip that space left alone was
     * learned, and a learned word is never corrected as a likely slip. Each listed word is cleaned
     * once ([LEARNED_SLIP_CLEANUP_FILE]); undoing a correction afterwards teaches it again for good.
     */
    private fun removeAutoLearnedSlipsOnce(dao: UserDictionaryDao) {
        val raw = applicationContext.assets.open(LatinDictionaryStore.EnglishUnvettedWordsPath)
            .bufferedReader().use { it.readText() }
        val bigrams by lazy { PersonalBigramStore.get(applicationContext) }
        val trigrams by lazy { PersonalTrigramStore.get(applicationContext) }
        val result = cleanUpLearnedMisspellingsOnce(
            dao = dao,
            table = LatinDictionaryStore.parseWordList(raw),
            markerFile = File(applicationContext.filesDir, LEARNED_SLIP_CLEANUP_FILE),
        ) { entry ->
            val locale = FlorisLocale.fromTag(entry.locale ?: return@cleanUpLearnedMisspellingsOnce)
            UserDictionaryOverlay.get().forget(entry.word, locale)
            bigrams.forget(entry.word, locale)
            trigrams.forget(entry.word, locale)
        } ?: return
        if (result.removed.isNotEmpty()) {
            flogInfo(LogTopic.DICTIONARY) { "Removed ${result.removed.size} learned unvetted words" }
        }
        if (!result.markerSaved) {
            flogWarning(LogTopic.DICTIONARY) { "Learned slip cleanup marker not saved; retrying next start" }
        }
    }

    private fun openEncryptedFlorisUserDictionary(
        context: Context,
    ): UserDictionaryOpenOutcome<FlorisUserDictionaryDatabase> {
        if (!migratePlaintextFlorisUserDictionaryIfNecessary(context)) {
            return UserDictionaryOpenOutcome.Unavailable(null)
        }
        return UserDictionaryStoreRecovery.open(
            build = { buildEncryptedFlorisUserDictionary(context) },
            verify = { database -> database.userDictionaryDao().queryLanguageList() },
            close = { database -> database.close() },
            quarantine = {
                flogWarning(LogTopic.DICTIONARY) { "Encrypted user dictionary is corrupt; preserving it before starting an empty one" }
                quarantineFlorisUserDictionaryDatabaseFiles(context, "unreadable").also { preserved ->
                    if (!preserved) {
                        flogError(LogTopic.DICTIONARY) { "Could not preserve the corrupt user dictionary; leaving it in place" }
                    }
                }
            },
            recordReset = florisUserDictionaryLoader::markReset,
        )
    }

    private fun buildEncryptedFlorisUserDictionary(context: Context): FlorisUserDictionaryDatabase? {
        val factory = FlorisUserDictionaryEncryption.openHelperFactory(context) ?: return null
        return Room.databaseBuilder(
            context,
            FlorisUserDictionaryDatabase::class.java,
            FlorisUserDictionaryDatabase.DB_FILE_NAME,
        ).openHelperFactory(factory).build()
    }

    private fun migratePlaintextFlorisUserDictionaryIfNecessary(context: Context): Boolean {
        val databaseFile = context.getDatabasePath(FlorisUserDictionaryDatabase.DB_FILE_NAME)
        if (!FlorisUserDictionaryEncryption.isPlaintextSqliteDatabase(databaseFile)) {
            return true
        }
        val entries = runCatching {
            val plaintextDatabase = Room.databaseBuilder(
                context,
                FlorisUserDictionaryDatabase::class.java,
                FlorisUserDictionaryDatabase.DB_FILE_NAME,
            ).build()
            try {
                plaintextDatabase.userDictionaryDao().queryAll()
            } finally {
                plaintextDatabase.close()
            }
        }.getOrElse { error ->
            flogWarning(LogTopic.DICTIONARY) { "Unable to read plaintext user dictionary for encryption migration: ${error.message}" }
            return false
        }

        val backups = runCatching {
            moveFlorisUserDictionaryDatabaseFilesAside(context)
        }.getOrElse { error ->
            flogWarning(LogTopic.DICTIONARY) { "Unable to stage plaintext user dictionary for encryption migration: ${error.message}" }
            return false
        }

        val encryptedDatabase = buildEncryptedFlorisUserDictionary(context)
        if (encryptedDatabase == null) {
            restoreFlorisUserDictionaryDatabaseFiles(backups)
            return false
        }

        return runCatching {
            val dao = encryptedDatabase.userDictionaryDao()
            for (entry in entries) {
                dao.insert(entry)
            }
            encryptedDatabase.close()
            deleteBackedUpFlorisUserDictionaryDatabaseFiles(backups)
            flogInfo(LogTopic.DICTIONARY) { "Migrated ${entries.size} user dictionary entries to encrypted SQLCipher storage" }
            true
        }.getOrElse { error ->
            flogError(LogTopic.DICTIONARY) { "Encrypted user dictionary migration failed; restoring plaintext store: ${error.message}" }
            encryptedDatabase.close()
            if (!quarantineFlorisUserDictionaryDatabaseFiles(context, "failed-migration")) {
                flogError(LogTopic.DICTIONARY) { "Could not preserve failed encrypted migration store before plaintext restore" }
                return false
            }
            restoreFlorisUserDictionaryDatabaseFiles(backups)
            false
        }
    }

    private fun loadSystemUserDictionaryIfNecessary() {
        val context = applicationContext
        if (systemUserDictionaryDatabase == null && prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase = SystemUserDictionaryDatabase(context)
        }
    }

    private fun closeFlorisUserDictionary() {
        if (florisUserDictionaryDatabase != null) {
            florisUserDictionaryDatabase?.close()
            florisUserDictionaryDatabase = null
        }
        florisUserDictionaryLoader.closed()
    }

    private fun closeSystemUserDictionary() {
        if (systemUserDictionaryDatabase != null) {
            systemUserDictionaryDatabase = null
        }
    }

    private fun moveFlorisUserDictionaryDatabaseFilesAside(
        context: Context,
    ): List<EncryptedDatabaseFiles.Backup> = EncryptedDatabaseFiles.moveAside(
        databaseFile = context.getDatabasePath(FlorisUserDictionaryDatabase.DB_FILE_NAME),
        suffix = "plaintext",
        timestampMs = System.currentTimeMillis(),
    )

    private fun restoreFlorisUserDictionaryDatabaseFiles(
        backups: List<EncryptedDatabaseFiles.Backup>,
    ): Boolean = EncryptedDatabaseFiles.restore(backups)

    private fun deleteBackedUpFlorisUserDictionaryDatabaseFiles(
        backups: List<EncryptedDatabaseFiles.Backup>,
    ) {
        if (!EncryptedDatabaseFiles.deleteBackups(backups)) {
            flogWarning(LogTopic.DICTIONARY) {
                "Could not delete migrated plaintext user dictionary backups"
            }
        }
    }

    private fun quarantineFlorisUserDictionaryDatabaseFiles(
        context: Context,
        reason: String,
    ): Boolean = EncryptedDatabaseFiles.quarantine(
        databaseFile = context.getDatabasePath(FlorisUserDictionaryDatabase.DB_FILE_NAME),
        reason = reason,
        timestampMs = System.currentTimeMillis(),
    )

    /** Room refuses the verify query on the main thread, so the open runs on a thread of its own. */
    private fun <T> runOpenToCompletion(block: () -> T): T =
        UserDictionaryStoreRecovery.runToCompletion(florisUserDictionaryOpenExecutor, block)

    /** The reset notice as a marker file beside the database, so erasing the store clears it too. */
    private class FileResetNotice(markerFile: Lazy<File>) : UserDictionaryResetNotice {
        private val marker by markerFile

        override fun isPending(): Boolean = marker.exists()

        override fun record() {
            val written = runCatching { marker.parentFile?.mkdirs(); marker.exists() || marker.createNewFile() }
            if (!written.getOrDefault(false)) {
                flogError(LogTopic.DICTIONARY) {
                    "Could not save the user dictionary reset notice; it shows until the keyboard restarts"
                }
            }
        }

        override fun clear() {
            if (marker.exists() && !marker.delete()) {
                flogWarning(LogTopic.DICTIONARY) { "Could not clear the user dictionary reset notice" }
            }
        }
    }

    private fun <T> runRoomQueryBlocking(
        operation: String,
        fallback: T,
        block: () -> T,
    ): T {
        return DictionarySyncBridge.runWithTimeout(
            executor = roomSyncExecutor,
            timeoutMs = DICTIONARY_ROOM_SYNC_TIMEOUT_MS,
            fallback = fallback,
            onTimeout = {
                flogWarning(LogTopic.DICTIONARY) {
                    "$operation timed out after ${DICTIONARY_ROOM_SYNC_TIMEOUT_MS}ms; returning fallback"
                }
            },
            onFailure = { error ->
                flogError(LogTopic.DICTIONARY) {
                    "$operation failed: ${error.message}"
                }
            },
            block = block,
        )
    }

    private fun UserDictionaryReadDao.queryCandidates(
        query: String,
        locale: FlorisLocale,
        sourcePriority: Int,
    ): List<UserDictionaryCandidate> {
        val shortcutCandidates = queryShortcut(query, locale).map { entry ->
            UserDictionaryCandidate(
                entry = entry,
                sourcePriority = sourcePriority,
                matchPriority = SHORTCUT_MATCH_PRIORITY,
            )
        }
        val wordCandidates = query(query, locale).map { entry ->
            UserDictionaryCandidate(
                entry = entry,
                sourcePriority = sourcePriority,
                matchPriority = if (entry.word.startsWith(query, ignoreCase = true)) {
                    PREFIX_MATCH_PRIORITY
                } else {
                    CONTAINS_MATCH_PRIORITY
                },
            )
        }
        return shortcutCandidates + wordCandidates
    }

    private fun UserDictionaryReadDao.containsWordOrShortcut(word: String, locale: FlorisLocale): Boolean {
        if (queryExactFuzzyLocale(word, locale).any { it.word.equals(word, ignoreCase = true) }) {
            return true
        }
        val lowercaseWord = word.lowercase()
        if (lowercaseWord != word && queryExactFuzzyLocale(lowercaseWord, locale).any {
                it.word.equals(word, ignoreCase = true)
            }
        ) {
            return true
        }
        if (queryShortcut(word, locale).any { it.shortcut.equals(word, ignoreCase = true) }) {
            return true
        }
        return query(word, locale).any { entry ->
            entry.word.equals(word, ignoreCase = true) ||
                entry.shortcut.equals(word, ignoreCase = true)
        }
    }
}

internal data class UserDictionaryCandidate(
    val entry: UserDictionaryEntry,
    val sourcePriority: Int,
    val matchPriority: Int,
)

internal fun rankUserDictionaryCandidates(
    query: String,
    candidates: List<UserDictionaryCandidate>,
): List<UserDictionaryEntry> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        return emptyList()
    }

    return candidates
        .asSequence()
        .filter { it.entry.word.isNotBlank() }
        .filterNot { candidate ->
            candidate.entry.word.equals(normalizedQuery, ignoreCase = true) &&
                !candidate.entry.shortcut.equals(normalizedQuery, ignoreCase = true)
        }
        .sortedWith(
            compareBy<UserDictionaryCandidate> { it.sourcePriority }
                .thenBy { it.matchPriority }
                .thenByDescending { it.entry.freq }
                .thenBy { it.entry.word.length }
                .thenBy { it.entry.word.lowercase() }
        )
        .distinctBy { it.entry.word.lowercase() }
        .map { it.entry }
        .toList()
}

internal object DictionarySyncBridge {
    fun <T> runWithTimeout(
        executor: ExecutorService,
        timeoutMs: Long,
        fallback: T,
        onTimeout: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
        block: () -> T,
    ): T {
        val future = executor.submit(Callable { block() })
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            onTimeout()
            fallback
        } catch (e: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            onFailure(e)
            fallback
        } catch (e: ExecutionException) {
            onFailure(e.cause ?: e)
            fallback
        }
    }
}
