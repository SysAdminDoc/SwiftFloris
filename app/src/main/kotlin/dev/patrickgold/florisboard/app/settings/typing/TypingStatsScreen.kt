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
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.DeleteForever
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
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay
import dev.patrickgold.florisboard.ime.nlp.CorrectionAccuracyDelta
import dev.patrickgold.florisboard.ime.nlp.CorrectionAccuracyTrend
import dev.patrickgold.florisboard.ime.nlp.CorrectionOutcomePriors
import dev.patrickgold.florisboard.ime.nlp.SwiftKeyTypingTraceRecorder
import dev.patrickgold.florisboard.ime.text.keyboard.AdaptiveTouchModel
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
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

    fun shareTraceFile(asReplayFixtures: Boolean = false) {
        scope.launch {
            val exportFile = withContext(Dispatchers.IO) {
                val recorder = SwiftKeyTypingTraceRecorder(context)
                if (asReplayFixtures) {
                    recorder.copyReplayFixtureFileToShareCache()
                } else {
                    recorder.copyTraceFileToShareCache()
                }
            }
            if (exportFile == null) {
                context.showLongToast(
                    if (asReplayFixtures) {
                        R.string.settings__typing_stats__trace_fixture_share_empty__toast
                    } else {
                        R.string.settings__typing_stats__trace_share_empty__toast
                    }
                )
                return@launch
            }
            runCatching {
                val uri = FileProvider.getUriForFile(context, TraceFileProviderAuthority, exportFile)
                val shareIntent = ShareCompat.IntentBuilder(context)
                    .setStream(uri)
                    .setType(TraceExportMimeType)
                    .createChooserIntent()
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(shareIntent)
            }.onFailure {
                context.showLongToast(
                    if (asReplayFixtures) {
                        R.string.settings__typing_stats__trace_fixture_share_failed__toast
                    } else {
                        R.string.settings__typing_stats__trace_share_failed__toast
                    }
                )
            }
        }
    }

    content {
        var showEraseEverythingConfirm by remember { mutableStateOf(false) }
        var showResetAllConfirm by remember { mutableStateOf(false) }

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
                title = stringRes(R.string.settings__typing_stats__accuracy_delta),
                summary = stats?.let { formatCorrectionAccuracyDelta(it.correctionAccuracyDelta) }
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
                // Confirm-gated: this single tap wipes the entire accumulated typing
                // model (phrases, correction memory, adaptive touch) with no recovery —
                // the same destructive-without-recovery property that gates the
                // "Erase everything" row below. The per-category resets above stay
                // unguarded because each only drops one regenerable model.
                onClick = { showResetAllConfirm = true },
            )
            // RESEARCH_FEATURE_PLAN.md EI12 — single confirmed action that also wipes
            // the personal dictionary (the "Reset all learning" row above deliberately
            // keeps it). Destructive + no recovery, so it is gated behind a confirm
            // dialog, unlike the per-category resets.
            Preference(
                icon = Icons.Default.DeleteForever,
                title = stringRes(R.string.settings__typing_stats__erase_everything),
                summary = stringRes(R.string.settings__typing_stats__erase_everything__summary),
                onClick = { showEraseEverythingConfirm = true },
            )
        }

        if (showResetAllConfirm) {
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__typing_stats__reset_all_learning),
                confirmLabel = stringRes(R.string.action__reset),
                onConfirm = {
                    showResetAllConfirm = false
                    resetAndRefresh(R.string.settings__typing_stats__reset_all_learning__toast) {
                        PersonalBigramStore.get(context).resetAndAwait()
                        PersonalTrigramStore.get(context).resetAndAwait()
                        CorrectionOutcomePriors.get(context).resetAndAwait()
                        AdaptiveTouchModel.reset()
                    }
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { showResetAllConfirm = false },
            ) {
                Text(stringRes(R.string.settings__typing_stats__reset_all_learning__summary))
            }
        }

        if (showEraseEverythingConfirm) {
            JetPrefAlertDialog(
                title = stringRes(R.string.settings__typing_stats__erase_everything__confirm_title),
                confirmLabel = stringRes(R.string.settings__typing_stats__erase_everything__confirm_button),
                onConfirm = {
                    showEraseEverythingConfirm = false
                    resetAndRefresh(R.string.settings__typing_stats__erase_everything__toast) {
                        PersonalBigramStore.get(context).resetAndAwait()
                        PersonalTrigramStore.get(context).resetAndAwait()
                        CorrectionOutcomePriors.get(context).resetAndAwait()
                        AdaptiveTouchModel.reset()
                        DictionaryManager.default().florisUserDictionaryDatabase()?.reset()
                        UserDictionaryOverlay.get().clearAll()
                    }
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { showEraseEverythingConfirm = false },
            ) {
                Text(stringRes(R.string.settings__typing_stats__erase_everything__confirm_message))
            }
        }

        PreferenceGroup(title = stringRes(R.string.settings__typing_stats__group_diagnostics)) {
            Preference(
                title = stringRes(R.string.settings__typing_stats__trace_capture),
                summary = stats?.let { current ->
                    if (current.traceCaptureEnabled) {
                        stringRes(
                            R.string.settings__typing_stats__trace_capture__on,
                            "size" to formatBytes(current.traceFileBytes),
                        )
                    } else {
                        stringRes(
                            R.string.settings__typing_stats__trace_capture__off,
                            "size" to formatBytes(current.traceFileBytes),
                        )
                    }
                } ?: stringRes(R.string.settings__typing_stats__loading),
                onClick = {
                    val shouldEnable = stats?.traceCaptureEnabled != true
                    resetAndRefresh(
                        if (shouldEnable) {
                            R.string.settings__typing_stats__trace_capture_enabled__toast
                        } else {
                            R.string.settings__typing_stats__trace_capture_disabled__toast
                        },
                    ) {
                        SwiftKeyTypingTraceRecorder(context).setEnabled(shouldEnable)
                    }
                },
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__trace_share),
                summary = stringRes(R.string.settings__typing_stats__trace_share__summary),
                enabledIf = { (stats?.traceFileBytes ?: 0L) > 0L },
                onClick = { shareTraceFile() },
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__trace_fixture_share),
                summary = stringRes(R.string.settings__typing_stats__trace_fixture_share__summary),
                enabledIf = { (stats?.traceFileBytes ?: 0L) > 0L },
                onClick = { shareTraceFile(asReplayFixtures = true) },
            )
            Preference(
                title = stringRes(R.string.settings__typing_stats__trace_clear),
                summary = stringRes(R.string.settings__typing_stats__trace_clear__summary),
                enabledIf = { (stats?.traceFileBytes ?: 0L) > 0L },
                onClick = {
                    resetAndRefresh(R.string.settings__typing_stats__trace_clear__toast) {
                        SwiftKeyTypingTraceRecorder(context).clearTraceFile()
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
    val correctionAccuracyDelta: CorrectionAccuracyDelta,
    val adaptiveTouchSamples: Int,
    val traceCaptureEnabled: Boolean,
    val traceFileBytes: Long,
)

private suspend fun loadTypingLearningStats(context: Context): TypingLearningStats {
    // Personal dictionary count + top words. Lifecycle-safe: a null dao means
    // the user hasn't opted into personal-dict yet, in which case we show 0.
    val dao = DictionaryManager.default().florisUserDictionaryDao()
    val allEntries = dao?.queryAll().orEmpty()
    val correctionOutcomePriors = CorrectionOutcomePriors.get(context)
    val traceRecorder = SwiftKeyTypingTraceRecorder(context)
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
        correctionOutcomeCount = correctionOutcomePriors.entryCount(),
        correctionAccuracyDelta = correctionOutcomePriors.accuracyDelta(),
        adaptiveTouchSamples = AdaptiveTouchModel.totalSampleCount(),
        traceCaptureEnabled = traceRecorder.isEnabled(),
        traceFileBytes = traceRecorder.traceFileSizeBytes(),
    )
}

@Composable
private fun formatCorrectionAccuracyDelta(delta: CorrectionAccuracyDelta): String {
    return when (delta.trend) {
        CorrectionAccuracyTrend.NO_BASELINE -> {
            if (delta.currentWeekAccepted == 0) {
                stringRes(R.string.settings__typing_stats__accuracy_delta__none)
            } else {
                stringRes(
                    R.string.settings__typing_stats__accuracy_delta__no_baseline,
                    "current" to delta.currentWeekAccepted,
                )
            }
        }
        CorrectionAccuracyTrend.FEWER -> stringRes(
            R.string.settings__typing_stats__accuracy_delta__fewer,
            "percent" to (delta.changePercent ?: 0),
            "current" to delta.currentWeekAccepted,
            "previous" to delta.previousWeekAccepted,
        )
        CorrectionAccuracyTrend.MORE -> stringRes(
            R.string.settings__typing_stats__accuracy_delta__more,
            "percent" to (delta.changePercent ?: 0),
            "current" to delta.currentWeekAccepted,
            "previous" to delta.previousWeekAccepted,
        )
        CorrectionAccuracyTrend.UNCHANGED -> stringRes(
            R.string.settings__typing_stats__accuracy_delta__unchanged,
            "current" to delta.currentWeekAccepted,
        )
    }
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

private const val TraceExportMimeType = "application/json"
private const val TraceFileProviderAuthority = "${BuildConfig.APPLICATION_ID}.provider.file"
