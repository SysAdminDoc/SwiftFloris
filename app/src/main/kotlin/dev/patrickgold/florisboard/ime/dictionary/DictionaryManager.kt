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
import androidx.room.Room
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.FlorisLocale
import java.lang.ref.WeakReference

private const val FLORIS_USER_DICTIONARY_SOURCE_PRIORITY = 0
private const val SYSTEM_USER_DICTIONARY_SOURCE_PRIORITY = 1
private const val SHORTCUT_MATCH_PRIORITY = 0
private const val PREFIX_MATCH_PRIORITY = 1
private const val CONTAINS_MATCH_PRIORITY = 2

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
                isEligibleForUserRemoval = false,
            )
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
            florisUserDictionaryDatabase = Room.databaseBuilder(
                context,
                FlorisUserDictionaryDatabase::class.java,
                FlorisUserDictionaryDatabase.DB_FILE_NAME
            ).allowMainThreadQueries().build()
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
