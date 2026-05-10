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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.text.keyboard.AdaptiveTouchModel
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes
import java.io.File

@Composable
fun TypingStatsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__typing_stats__title)
    previewFieldVisible = false

    val context = LocalContext.current

    var personalDictCount by remember { mutableLongStateOf(-1L) }
    var personalDictTopWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var bigramFileBytes by remember { mutableLongStateOf(-1L) }
    var refreshTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(refreshTick) {
        withContext(Dispatchers.IO) {
            // Personal dictionary count + top words. Lifecycle-safe: a null dao means the
            // user hasn't opted into personal-dict yet, in which case we just show 0.
            val dao = DictionaryManager.default().florisUserDictionaryDao()
            personalDictCount = dao?.queryAll()?.size?.toLong() ?: 0L
            personalDictTopWords = dao?.queryAll()
                ?.sortedByDescending { it.freq }
                ?.take(10)
                ?.map { "${it.word}  (×${it.freq})" }
                ?: emptyList()
            // Bigram store size on disk — sum of every personal_bigrams_*.tsv file.
            bigramFileBytes = context.filesDir.listFiles { _, name ->
                name.startsWith("personal_bigrams_") && name.endsWith(".tsv")
            }?.sumOf { f: File -> f.length() } ?: 0L
        }
    }

    val adaptiveSamples = AdaptiveTouchModel.totalSampleCount()

    content {
        PreferenceGroup(title = stringRes(R.string.settings__typing_stats__group_corpus)) {
            Preference(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = stringRes(R.string.settings__typing_stats__words_learned),
                summary = if (personalDictCount < 0L) {
                    stringRes(R.string.settings__typing_stats__loading)
                } else {
                    "$personalDictCount"
                },
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__bigrams_disk),
                summary = if (bigramFileBytes < 0L) {
                    stringRes(R.string.settings__typing_stats__loading)
                } else if (bigramFileBytes < 1024L) {
                    "$bigramFileBytes B"
                } else if (bigramFileBytes < 1024L * 1024L) {
                    "${bigramFileBytes / 1024L} KB"
                } else {
                    "${bigramFileBytes / (1024L * 1024L)} MB"
                },
            )
            Preference(
                icon = Icons.Default.TouchApp,
                title = stringRes(R.string.settings__typing_stats__adaptive_touch_samples),
                summary = "$adaptiveSamples",
            )
        }

        if (personalDictTopWords.isNotEmpty()) {
            PreferenceGroup(title = stringRes(R.string.settings__typing_stats__group_top_words)) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    for (entry in personalDictTopWords) {
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
        }
    }
}
