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

package dev.patrickgold.florisboard.app.settings.dictionary

import dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import kotlin.coroutines.cancellation.CancellationException

/** Read state of the learned-entries screen. */
internal sealed interface LearnedEntriesState {
    data object Loading : LearnedEntriesState

    data class Ready(
        val words: List<UserDictionaryEntry>,
        val bigrams: List<PersonalBigramStore.LearnedBigram>,
        val trigrams: List<PersonalTrigramStore.LearnedTrigram>,
    ) : LearnedEntriesState

    /** Reading learned data failed. [errorClass] is a throwable class name, never learned text. */
    data class Failure(val errorClass: String) : LearnedEntriesState
}

/** Outcome of a single learned-entry removal. */
internal sealed interface LearnedEntryRemoval {
    data object InProgress : LearnedEntryRemoval

    data object Success : LearnedEntryRemoval

    /** Removal failed and the entry is still stored. [errorClass] is never learned text. */
    data class Failure(val errorClass: String) : LearnedEntryRemoval
}

/**
 * Pure state transitions for the learned-entries screen. Kept free of Compose and Android
 * types so every load/removal branch is unit-testable.
 *
 * Diagnostics carry throwable class names only: learned words, n-grams and raw error messages
 * (which may embed the offending row) never leave this layer.
 */
internal object LearnedEntriesPolicy {
    const val UNKNOWN_ERROR_CLASS = "UnknownError"

    /** Maps [error] to a stable, privacy-safe identifier. */
    fun errorClassOf(error: Throwable): String {
        val simpleName = error::class.java.simpleName
        if (simpleName.isNotBlank()) return simpleName
        val binaryName = error::class.java.name.substringAfterLast('.')
        return binaryName.ifBlank { UNKNOWN_ERROR_CLASS }
    }

    /** Stable presentation order: locale first (unqualified entries first), then word. */
    fun sortWords(words: List<UserDictionaryEntry>): List<UserDictionaryEntry> {
        return words.sortedWith(compareBy<UserDictionaryEntry> { it.locale.orEmpty() }.thenBy { it.word })
    }

    /**
     * Reads every learned store, returning [LearnedEntriesState.Failure] instead of leaving the
     * screen stuck on [LearnedEntriesState.Loading] when a read throws. Cancellation propagates.
     */
    suspend fun load(
        readWords: suspend () -> List<UserDictionaryEntry>,
        readBigrams: suspend () -> List<PersonalBigramStore.LearnedBigram>,
        readTrigrams: suspend () -> List<PersonalTrigramStore.LearnedTrigram>,
        onError: (String) -> Unit = {},
    ): LearnedEntriesState {
        return try {
            LearnedEntriesState.Ready(
                words = sortWords(readWords()),
                bigrams = readBigrams(),
                trigrams = readTrigrams(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val errorClass = errorClassOf(error)
            onError(errorClass)
            LearnedEntriesState.Failure(errorClass)
        }
    }

    /**
     * Runs [operation] and reports whether the entry actually went away. Failures keep the row
     * on screen instead of silently refreshing it out of view.
     */
    suspend fun remove(
        operation: suspend () -> Unit,
        onError: (String) -> Unit = {},
    ): LearnedEntryRemoval {
        return try {
            operation()
            LearnedEntryRemoval.Success
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val errorClass = errorClassOf(error)
            onError(errorClass)
            LearnedEntryRemoval.Failure(errorClass)
        }
    }
}
