/*
 * Copyright (C) 2026 The SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.typing

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import dev.patrickgold.florisboard.ime.nlp.CorrectionOutcomePriors
import dev.patrickgold.florisboard.ime.text.keyboard.AdaptiveTouchModel
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.stringRes
import java.io.File

@Composable
fun TypingStatsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__typing_stats__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stats by remember { mutableStateOf<TypingLearningStats?>(null) }
    var refreshTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(refreshTick) {
        stats = withContext(Dispatchers.IO) {
            loadTypingLearningStats(context)
        }
    }

    fun resetAndRefresh(messageId: Int, block: suspend () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                block()
            }
            refreshTick = System.currentTimeMillis()
            context.showLongToast(messageId)
        }
    }

    content {
        PreferenceGroup(title = stringRes(R.string.settings__typing_stats__group_corpus)) {
            Preference(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = stringRes(R.string.settings__typing_stats__words_learned),
                summary = stats?.personalDictCount?.toString()
                    ?: stringRes(R.string.settings__typing_stats__loading),
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__phrase_pairs),
                summary = stats?.let { "${it.bigramCount} (${formatBytes(it.bigramFileBytes)})" }
                    ?: stringRes(R.string.settings__typing_stats__loading),
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__phrase_triples),
                summary = stats?.let { "${it.trigramCount} (${formatBytes(it.trigramFileBytes)})" }
                    ?: stringRes(R.string.settings__typing_stats__loading),
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__correction_decisions),
                summary = stats?.correctionOutcomeCount?.toString()
                    ?: stringRes(R.string.settings__typing_stats__loading),
            )
            Preference(
                icon = Icons.Default.TouchApp,
                title = stringRes(R.string.settings__typing_stats__adaptive_touch_samples),
                summary = stats?.adaptiveTouchSamples?.toString()
                    ?: stringRes(R.string.settings__typing_stats__loading),
            )
        }

        val topWords = stats?.personalDictTopWords.orEmpty()
        if (topWords.isNotEmpty()) {
            PreferenceGroup(title = stringRes(R.string.settings__typing_stats__group_top_words)) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    for (entry in topWords) {
                        Text(text = entry, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }

        PreferenceGroup(title = stringRes(R.string.settings__typing_stats__group_actions)) {
            Preference(
                icon = Icons.Default.Refresh,
                title = stringRes(R.string.settings__typing_stats__refresh),
                onClick = { refreshTick = System.currentTimeMillis() },
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__reset_phrase_history),
                summary = stringRes(R.string.settings__typing_stats__reset_phrase_history__summary),
                onClick = {
                    resetAndRefresh(R.string.settings__typing_stats__reset_phrase_history__toast) {
                        PersonalBigramStore.get(context).resetAndAwait()
                        PersonalTrigramStore.get(context).resetAndAwait()
                    }
                },
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__reset_correction_memory),
                summary = stringRes(R.string.settings__typing_stats__reset_correction_memory__summary),
                onClick = {
                    resetAndRefresh(R.string.settings__typing_stats__reset_correction_memory__toast) {
                        CorrectionOutcomePriors.get(context).resetAndAwait()
                    }
                },
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__reset_adaptive_touch),
                summary = stringRes(R.string.settings__typing_stats__reset_adaptive_touch__summary),
                onClick = {
                    resetAndRefresh(R.string.settings__typing_stats__reset_adaptive_touch__toast) {
                        AdaptiveTouchModel.reset()
                    }
                },
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__reset_all_learning),
                summary = stringRes(R.string.settings__typing_stats__reset_all_learning__summary),
                onClick = {
                    resetAndRefresh(R.string.settings__typing_stats__reset_all_learning__toast) {
                        PersonalBigramStore.get(context).resetAndAwait()
                        PersonalTrigramStore.get(context).resetAndAwait()
                        CorrectionOutcomePriors.get(context).resetAndAwait()
                        AdaptiveTouchModel.reset()
                    }
                },
            )
        }
    }
}

private data class TypingLearningStats(
    val personalDictCount: Long,
    val personalDictTopWords: List<String>,
    val bigramCount: Int,
    val bigramFileBytes: Long,
    val trigramCount: Int,
    val trigramFileBytes: Long,
    val correctionOutcomeCount: Int,
    val adaptiveTouchSamples: Int,
)

private suspend fun loadTypingLearningStats(context: Context): TypingLearningStats {
    // Personal dictionary count + top words. Lifecycle-safe: a null dao means
    // the user hasn't opted into personal-dict yet, in which case we show 0.
    val dao = DictionaryManager.default().florisUserDictionaryDao()
    val allEntries = dao?.queryAll().orEmpty()
    return TypingLearningStats(
        personalDictCount = allEntries.size.toLong(),
        personalDictTopWords = allEntries
            .sortedByDescending { it.freq }
            .take(10)
            .map { "${it.word}  (x${it.freq})" },
        bigramCount = PersonalBigramStore.get(context).totalEntryCount(),
        bigramFileBytes = sumFilesWithPrefix(context, "personal_bigrams_"),
        trigramCount = PersonalTrigramStore.get(context).totalEntryCount(),
        trigramFileBytes = sumFilesWithPrefix(context, "personal_trigrams_"),
        correctionOutcomeCount = CorrectionOutcomePriors.get(context).entryCount(),
        adaptiveTouchSamples = AdaptiveTouchModel.totalSampleCount(),
    )
}

private fun sumFilesWithPrefix(context: Context, prefix: String): Long {
    return context.filesDir.listFiles { _, name ->
        name.startsWith(prefix) && name.endsWith(".tsv")
    }?.sumOf { f: File -> f.length() } ?: 0L
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> "${bytes / (1024L * 1024L)} MB"
    }
}
