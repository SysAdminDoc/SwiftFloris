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
import android.util.Log
import androidx.room.Room
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.FlorisLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference

private const val TAG = "DictionaryManager"
private const val FLORIS_USER_DICTIONARY_SOURCE_PRIORITY = 0
private const val SYSTEM_USER_DICTIONARY_SOURCE_PRIORITY = 1
private const val SHORTCUT_MATCH_PRIORITY = 0
private const val PREFIX_MATCH_PRIORITY = 1
private const val CONTAINS_MATCH_PRIORITY = 2

/**
 * Frequency starting point assigned to a freshly-learned word.
 *
 * SwiftKey-style "instant remember" — set high enough that a single
 * commit makes the word the top suggestion for its prefix and removes
 * the spell-check underline. 245 / 255 ≈ 0.96 weight; the second
 * commit hits the cap and the word is fully remembered. Kept in
 * sync with [UserDictionaryOverlay.INITIAL_FREQUENCY] so disk +
 * in-memory agree on the per-word weight.
 */
private const val LEARN_INITIAL_FREQUENCY = 245
/** Frequency increment applied each time an existing learned word is reinforced. */
private const val LEARN_INCREMENT = 5
/** Cap learned-word frequency at the top tier so a much-typed word ties with
 *  SCOWL's most common words. */
private const val LEARN_MAX_FREQUENCY = 250
/** Minimum word length for auto-learning. Single chars and digrams are noise. */
private const val LEARN_MIN_LENGTH = 3
/** Maximum word length for auto-learning. Anything longer is almost certainly a URL,
 *  email, or pasted token and shouldn't be added to the personal dictionary. */
private const val LEARN_MAX_LENGTH = 32

/**
 * Coordinates SwiftFloris' internal user dictionary and the platform user dictionary.
 *
 * NLP providers own bundled and downloadable language dictionaries. This manager overlays user-managed entries on top
 * of those providers: the platform user dictionary is treated as a system-level source, and SwiftFloris' internal user
 * dictionary is the highest-priority source for conflicts. Stores are opened lazily when an accessor first needs them.
 */
class DictionaryManager private constructor(context: Context) {
    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext ?: context)
    private val prefs by FlorisPreferenceStore
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var florisUserDictionaryDatabase: FlorisUserDictionaryDatabase? = null
    private var systemUserDictionaryDatabase: SystemUserDictionaryDatabase? = null

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

        val florisDao = florisUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        if (florisDao == null && systemDao == null) return null

        val matches = buildList {
            if (prefs.dictionary.enableFlorisUserDictionary.get() && florisDao != null) {
                addAll(florisDao.queryShortcut(query, locale))
            }
            if (prefs.dictionary.enableSystemUserDictionary.get() && systemDao != null) {
                addAll(systemDao.queryShortcut(query, locale))
            }
        }

        return matches.asSequence()
            .filter { it.shortcut.equals(query, ignoreCase = true) }
            .filter { it.word.isNotBlank() && !it.word.equals(query, ignoreCase = true) }
            .sortedByDescending { it.freq }
            .firstOrNull()
            ?.word
    }

    fun queryUserDictionary(word: String, locale: FlorisLocale): List<SuggestionCandidate> {
        val query = word.trim()
        if (query.isBlank()) {
            return emptyList()
        }

        val florisDao = florisUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        if (florisDao == null && systemDao == null) {
            return emptyList()
        }

        val candidates = buildList {
            if (prefs.dictionary.enableFlorisUserDictionary.get() && florisDao != null) {
                addAll(florisDao.queryCandidates(query, locale, sourcePriority = FLORIS_USER_DICTIONARY_SOURCE_PRIORITY))
            }
            if (prefs.dictionary.enableSystemUserDictionary.get() && systemDao != null) {
                addAll(systemDao.queryCandidates(query, locale, sourcePriority = SYSTEM_USER_DICTIONARY_SOURCE_PRIORITY))
            }
        }

        return rankUserDictionaryCandidates(query, candidates).map { entry ->
            WordSuggestionCandidate(
                text = entry.word,
                confidence = (entry.freq.coerceIn(0, 255) / 255.0).coerceIn(0.0, 1.0),
                isEligibleForUserRemoval = true,
            )
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
        val dao = florisUserDictionaryDao() ?: return false
        // Synchronous-on-IO inside a runBlocking is acceptable here because the long-press
        // removal path expects an immediate boolean acknowledgement before re-running suggest.
        return runCatching {
            val matches = dao.queryExactFuzzyLocale(normalized, locale)
                .filter { it.word.equals(normalized, ignoreCase = true) }
            for (entry in matches) dao.delete(entry)
            matches.isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * Auto-learn a word that the user just committed (via space, punctuation, or
     * accepted suggestion). Increments the frequency of an existing entry, or inserts
     * a new entry at [LEARN_INITIAL_FREQUENCY]. Off-thread; safe to call from input
     * event handlers. No-op when the user has disabled the personal dictionary or is
     * in incognito mode (caller is responsible for the incognito gate).
     */
    fun learnWord(rawWord: String, locale: FlorisLocale) {
        if (!prefs.dictionary.enableFlorisUserDictionary.get()) return
        val cleaned = rawWord.trim()
            .trim { ch -> !ch.isLetter() && ch != '\'' && ch != '-' }
        if (cleaned.length < LEARN_MIN_LENGTH || cleaned.length > LEARN_MAX_LENGTH) return
        // Reject anything that doesn't look like a real word: must contain at least
        // one letter, no digits, no internal punctuation other than ' or -.
        if (cleaned.any { it.isDigit() }) return
        if (cleaned.none { it.isLetter() }) return
        if (cleaned.any { ch -> !ch.isLetter() && ch != '\'' && ch != '-' }) return
        val normalized = cleaned.lowercase()

        // Bump the in-memory overlay first so the next keystroke's
        // suggest() already ranks this word higher — the IO write below is
        // just for durability across process restarts. ROADMAP §7 Next-3
        // ranker tie-in.
        UserDictionaryOverlay.get().learn(normalized, locale)

        ioScope.launch {
            val dao = florisUserDictionaryDao() ?: return@launch
            val existingMatches = dao.queryExactFuzzyLocale(normalized, locale)
                .filter { it.word.equals(normalized, ignoreCase = true) }
            if (existingMatches.isNotEmpty()) {
                val entry = existingMatches.first()
                val newFreq = (entry.freq + LEARN_INCREMENT).coerceAtMost(LEARN_MAX_FREQUENCY)
                if (newFreq != entry.freq) {
                    dao.update(entry.copy(freq = newFreq))
                }
            } else {
                dao.insert(
                    UserDictionaryEntry(
                        id = 0,
                        word = normalized,
                        freq = LEARN_INITIAL_FREQUENCY,
                        locale = locale.toString(),
                        shortcut = null,
                    ),
                )
            }
        }
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
            }.getOrDefault(emptyList())
            overlay.hydrateLocale(locale, pairs)
        }
    }

    fun isKnownUserDictionaryWord(word: String, locale: FlorisLocale): Boolean {
        val query = word.trim()
        if (query.isBlank()) {
            return false
        }

        val florisDao = florisUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        if (florisDao == null && systemDao == null) {
            return false
        }

        if (prefs.dictionary.enableFlorisUserDictionary.get()) {
            if (florisDao?.containsWordOrShortcut(query, locale) == true) {
                return true
            }
        }
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            if (systemDao?.containsWordOrShortcut(query, locale) == true) {
                return true
            }
        }

        return false
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
    fun systemUserDictionaryDao(): UserDictionaryDao? {
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

    private fun loadFlorisUserDictionaryIfNecessary() {
        val context = applicationContext.get() ?: return
        if (florisUserDictionaryDatabase == null && prefs.dictionary.enableFlorisUserDictionary.get()) {
            florisUserDictionaryDatabase = openEncryptedFlorisUserDictionary(context)
        }
    }

    private fun openEncryptedFlorisUserDictionary(context: Context): FlorisUserDictionaryDatabase? {
        if (!migratePlaintextFlorisUserDictionaryIfNecessary(context)) {
            return null
        }
        return openVerifiedEncryptedFlorisUserDictionary(context)
    }

    private fun openVerifiedEncryptedFlorisUserDictionary(context: Context): FlorisUserDictionaryDatabase? {
        val database = buildEncryptedFlorisUserDictionary(context) ?: return null
        return runCatching {
            database.userDictionaryDao().queryLanguageList()
            database
        }.getOrElse { error ->
            Log.w(TAG, "Encrypted user dictionary could not be opened; recreating empty store: ${error.message}")
            database.close()
            deleteFlorisUserDictionaryDatabaseFiles(context)
            val replacement = buildEncryptedFlorisUserDictionary(context) ?: return null
            runCatching {
                replacement.userDictionaryDao().queryLanguageList()
                replacement
            }.getOrElse { replacementError ->
                Log.w(TAG, "Encrypted user dictionary unavailable after recreation: ${replacementError.message}")
                replacement.close()
                null
            }
        }
    }

    private fun buildEncryptedFlorisUserDictionary(context: Context): FlorisUserDictionaryDatabase? {
        val factory = FlorisUserDictionaryEncryption.openHelperFactory(context) ?: return null
        return Room.databaseBuilder(
            context,
            FlorisUserDictionaryDatabase::class.java,
            FlorisUserDictionaryDatabase.DB_FILE_NAME,
        ).openHelperFactory(factory).allowMainThreadQueries().build()
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
            ).allowMainThreadQueries().build()
            try {
                plaintextDatabase.userDictionaryDao().queryAll()
            } finally {
                plaintextDatabase.close()
            }
        }.getOrElse { error ->
            Log.w(TAG, "Unable to read plaintext user dictionary for encryption migration: ${error.message}")
            return false
        }

        val backups = runCatching {
            moveFlorisUserDictionaryDatabaseFilesAside(context)
        }.getOrElse { error ->
            Log.w(TAG, "Unable to stage plaintext user dictionary for encryption migration: ${error.message}")
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
            Log.i(TAG, "Migrated ${entries.size} user dictionary entries to encrypted SQLCipher storage")
            true
        }.getOrElse { error ->
            Log.w(TAG, "Encrypted user dictionary migration failed; restoring plaintext store: ${error.message}")
            encryptedDatabase.close()
            deleteFlorisUserDictionaryDatabaseFiles(context)
            restoreFlorisUserDictionaryDatabaseFiles(backups)
            false
        }
    }

    private fun loadSystemUserDictionaryIfNecessary() {
        val context = applicationContext.get() ?: return
        if (systemUserDictionaryDatabase == null && prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase = SystemUserDictionaryDatabase(context)
        }
    }

    private fun closeFlorisUserDictionary() {
        if (florisUserDictionaryDatabase != null) {
            florisUserDictionaryDatabase?.close()
            florisUserDictionaryDatabase = null
        }
    }

    private fun closeSystemUserDictionary() {
        if (systemUserDictionaryDatabase != null) {
            systemUserDictionaryDatabase = null
        }
    }

    private data class DatabaseFileBackup(val original: File, val backup: File)

    private fun moveFlorisUserDictionaryDatabaseFilesAside(context: Context): List<DatabaseFileBackup> {
        val databaseFile = context.getDatabasePath(FlorisUserDictionaryDatabase.DB_FILE_NAME)
        val timestamp = System.currentTimeMillis()
        val backups = mutableListOf<DatabaseFileBackup>()
        for (file in florisUserDictionaryDatabaseFiles(databaseFile)) {
            if (file.exists()) {
                val backup = File(file.parentFile, "${file.name}.plaintext-$timestamp")
                if (file.renameTo(backup)) {
                    backups.add(DatabaseFileBackup(file, backup))
                } else {
                    restoreFlorisUserDictionaryDatabaseFiles(backups)
                    error("Could not move ${file.name} to ${backup.name}")
                }
            }
        }
        return backups
    }

    private fun restoreFlorisUserDictionaryDatabaseFiles(backups: List<DatabaseFileBackup>): Boolean {
        var restored = true
        for ((original, backup) in backups) {
            if (original.exists() && !original.delete()) {
                Log.w(TAG, "Could not delete ${original.name} before restoring ${backup.name}")
                restored = false
            }
            if (backup.exists()) {
                if (!backup.renameTo(original)) {
                    Log.w(TAG, "Could not restore ${backup.name} to ${original.name}")
                    restored = false
                }
            }
        }
        return restored
    }

    private fun deleteBackedUpFlorisUserDictionaryDatabaseFiles(backups: List<DatabaseFileBackup>) {
        for ((_, backup) in backups) {
            if (backup.exists() && !backup.delete()) {
                Log.w(TAG, "Could not delete migrated plaintext user dictionary backup ${backup.name}")
            }
        }
    }

    private fun deleteFlorisUserDictionaryDatabaseFiles(context: Context) {
        context.deleteDatabase(FlorisUserDictionaryDatabase.DB_FILE_NAME)
        val databaseFile = context.getDatabasePath(FlorisUserDictionaryDatabase.DB_FILE_NAME)
        for (file in florisUserDictionaryDatabaseFiles(databaseFile)) {
            file.delete()
        }
    }

    private fun florisUserDictionaryDatabaseFiles(databaseFile: File): List<File> {
        return listOf(
            databaseFile,
            File("${databaseFile.path}-wal"),
            File("${databaseFile.path}-shm"),
            File("${databaseFile.path}-journal"),
        )
    }

    private fun UserDictionaryDao.queryCandidates(
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

    private fun UserDictionaryDao.containsWordOrShortcut(word: String, locale: FlorisLocale): Boolean {
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
