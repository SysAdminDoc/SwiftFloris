/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictionary

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes

@Composable
fun LearnedEntriesScreen() = FlorisScreen {
    title = stringRes(R.string.settings__learned_entries__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictionaryManager = DictionaryManager.default()
    val bigramStore = remember(context) { PersonalBigramStore.get(context) }
    val trigramStore = remember(context) { PersonalTrigramStore.get(context) }

    var refreshKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LearnedEntriesState>(LearnedEntriesState.Loading) }
    var pendingRemoval by remember { mutableStateOf<LearnedEntryRow?>(null) }

    LaunchedEffect(refreshKey) {
        state = LearnedEntriesState.Loading
        state = withContext(Dispatchers.IO) {
            val words = dictionaryManager.florisUserDictionaryDao()
                ?.queryAll()
                .orEmpty()
                .sortedWith(compareBy<UserDictionaryEntry> { it.locale.orEmpty() }.thenBy { it.word })
            LearnedEntriesState.Ready(
                words = words,
                bigrams = bigramStore.snapshot(),
                trigrams = trigramStore.snapshot(),
            )
        }
    }

    content {
        when (val current = state) {
            LearnedEntriesState.Loading -> FlorisProgressCard(
                modifier = Modifier
                    .padding(16.dp)
                    .defaultFlorisOutlinedBox(),
                text = stringRes(R.string.settings__learned_entries__loading),
            )
            is LearnedEntriesState.Ready -> {
                val isEmpty = current.words.isEmpty() &&
                    current.bigrams.isEmpty() &&
                    current.trigrams.isEmpty()
                if (isEmpty) {
                    FlorisEmptyState(
                        modifier = Modifier.padding(16.dp),
                        icon = Icons.AutoMirrored.Filled.LibraryBooks,
                        title = stringRes(R.string.settings__learned_entries__empty_title),
                        message = stringRes(R.string.settings__learned_entries__empty_message),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                        item {
                            PreferenceGroup(title = stringRes(R.string.settings__learned_entries__words_title)) {
                                current.words.forEach { entry ->
                                    JetPrefListItem(
                                        modifier = Modifier.rippleClickable {
                                            pendingRemoval = LearnedEntryRow.Word(entry)
                                        },
                                        text = entry.word,
                                        secondaryText = stringRes(
                                            R.string.settings__learned_entries__word_summary,
                                            "language" to entry.locale.orEmpty().ifBlank {
                                                stringRes(R.string.settings__udm__all_languages)
                                            },
                                            "count" to entry.freq,
                                        ),
                                    )
                                }
                            }
                        }
                        item {
                            PreferenceGroup(title = stringRes(R.string.settings__learned_entries__bigrams_title)) {
                                current.bigrams.forEach { entry ->
                                    JetPrefListItem(
                                        modifier = Modifier.rippleClickable {
                                            pendingRemoval = LearnedEntryRow.Bigram(entry)
                                        },
                                        text = "${entry.prev} ${entry.next}",
                                        secondaryText = stringRes(
                                            R.string.settings__learned_entries__ngram_summary,
                                            "language" to entry.localeTag,
                                            "count" to entry.count,
                                        ),
                                    )
                                }
                            }
                        }
                        item {
                            PreferenceGroup(title = stringRes(R.string.settings__learned_entries__trigrams_title)) {
                                current.trigrams.forEach { entry ->
                                    JetPrefListItem(
                                        modifier = Modifier.rippleClickable {
                                            pendingRemoval = LearnedEntryRow.Trigram(entry)
                                        },
                                        text = "${entry.prev2} ${entry.prev1} ${entry.next}",
                                        secondaryText = stringRes(
                                            R.string.settings__learned_entries__ngram_summary,
                                            "language" to entry.localeTag,
                                            "count" to entry.count,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        pendingRemoval?.let { row ->
            AlertDialog(
                onDismissRequest = { pendingRemoval = null },
                title = { Text(text = stringRes(R.string.settings__learned_entries__remove_title)) },
                text = {
                    Text(
                        text = stringRes(
                            R.string.settings__learned_entries__remove_message,
                            "entry" to row.displayText,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingRemoval = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    removeLearnedEntry(
                                        row = row,
                                        dictionaryManager = dictionaryManager,
                                        bigramStore = bigramStore,
                                        trigramStore = trigramStore,
                                    )
                                }
                                refreshKey++
                            }
                        },
                    ) {
                        Text(text = stringRes(R.string.action__remove))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRemoval = null }) {
                        Text(text = stringRes(R.string.action__cancel))
                    }
                },
            )
        }
    }
}

private sealed interface LearnedEntriesState {
    data object Loading : LearnedEntriesState
    data class Ready(
        val words: List<UserDictionaryEntry>,
        val bigrams: List<PersonalBigramStore.LearnedBigram>,
        val trigrams: List<PersonalTrigramStore.LearnedTrigram>,
    ) : LearnedEntriesState
}

private sealed interface LearnedEntryRow {
    val displayText: String

    data class Word(val entry: UserDictionaryEntry) : LearnedEntryRow {
        override val displayText: String = entry.word
    }

    data class Bigram(val entry: PersonalBigramStore.LearnedBigram) : LearnedEntryRow {
        override val displayText: String = "${entry.prev} ${entry.next}"
    }

    data class Trigram(val entry: PersonalTrigramStore.LearnedTrigram) : LearnedEntryRow {
        override val displayText: String = "${entry.prev2} ${entry.prev1} ${entry.next}"
    }
}

private suspend fun removeLearnedEntry(
    row: LearnedEntryRow,
    dictionaryManager: DictionaryManager,
    bigramStore: PersonalBigramStore,
    trigramStore: PersonalTrigramStore,
) {
    when (row) {
        is LearnedEntryRow.Word -> {
            dictionaryManager.florisUserDictionaryDao()?.delete(row.entry)
            row.entry.locale?.let { localeTag ->
                val locale = FlorisLocale.fromTag(localeTag)
                dictionaryManager.rebuildOverlay(locale)
                bigramStore.forgetAndAwait(row.entry.word, locale)
                trigramStore.forgetAndAwait(row.entry.word, locale)
            }
        }
        is LearnedEntryRow.Bigram -> {
            bigramStore.forgetExactAndAwait(
                prevWord = row.entry.prev,
                currWord = row.entry.next,
                locale = FlorisLocale.fromTag(row.entry.localeTag),
            )
        }
        is LearnedEntryRow.Trigram -> {
            trigramStore.forgetExactAndAwait(
                prev2 = row.entry.prev2,
                prev1 = row.entry.prev1,
                currWord = row.entry.next,
                locale = FlorisLocale.fromTag(row.entry.localeTag),
            )
        }
    }
}
