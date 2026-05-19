/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.gestures

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Handles the [GlideTypingClassifier]. Basically responsible for linking [GlideTypingGesture.Detector]
 * with [GlideTypingClassifier].
 */
class GlideTypingManager(context: Context) : GlideTypingGesture.Listener {
    companion object {
        private const val MAX_SUGGESTION_COUNT = 8
        private const val CONTEXT_RESCORE_WINDOW_MS = 6_000L
    }

    private val prefs by FlorisPreferenceStore
    private val keyboardManager by context.keyboardManager()
    private val nlpManager by context.nlpManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var glideTypingClassifier = StatisticalGlideTypingClassifier(context)
    private var lastTime = System.currentTimeMillis()
    private var previewJob: Job? = null
    private var pendingContextRescore: PendingGlideCommit? = null

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        previewJob?.cancel()
        launchSuggestions(MAX_SUGGESTION_COUNT, true) {
            glideTypingClassifier.clear()
        }
    }

    override fun onGlideWordBoundary(data: GlideTypingGesture.Detector.PointerData) {
        // Flow Through Space: commit the current most-confident gesture word, then clear
        // the classifier so the continuing trace starts fresh for the next word. The
        // existing commitGesture path already activates phantom-space, so the next
        // committed word will be auto-prefixed with " ".
        previewJob?.cancel()
        launchSuggestions(MAX_SUGGESTION_COUNT, true) {
            glideTypingClassifier.clear()
        }
    }

    override fun onGlideCancelled() {
        glideTypingClassifier.clear()
        pendingContextRescore = null
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        val normalized = GlideTypingGesture.Detector.Position(point.x, point.y)

        this.glideTypingClassifier.addGesturePoint(normalized)

        val time = System.currentTimeMillis()
        if (prefs.glide.showPreview.get() && time - lastTime > prefs.glide.previewRefreshDelay.get()) {
            // Cancel any stale preview job so they don't pile up.
            previewJob?.cancel()
            previewJob = launchSuggestions(1, false) {}
            lastTime = time
        }
    }

    /**
     * Change the layout of the internal gesture classifier
     */
    fun setLayout(keys: List<TextKey>) {
        if (keys.isNotEmpty() && prefs.glide.isEnabledForSubtype(subtypeManager.activeSubtype)) {
            glideTypingClassifier.setLayout(keys, subtypeManager.activeSubtype)
        }
    }

    /**
     * Asks gesture classifier for suggestions and then passes that on to the smartbar.
     * Also commits the most confident suggestion if [commit] is set. All happens on an async executor.
     *
     * @param callback Called when this function completes. Takes a boolean, which indicates if suggestions
     * were successfully set.
     */
    private fun launchSuggestions(maxSuggestionsToShow: Int, commit: Boolean, callback: (Boolean) -> Unit): Job? {
        if (!prefs.glide.isEnabledForSubtype(subtypeManager.activeSubtype) || !glideTypingClassifier.ready) {
            callback.invoke(false)
            return null
        }

        return scope.launch(Dispatchers.Default) {
            // For preview, only compute the few we'll display; for commit, compute all.
            val classifierCount = if (commit) MAX_SUGGESTION_COUNT else maxSuggestionsToShow.coerceAtLeast(1)
            val suggestions = glideTypingClassifier.getSuggestions(classifierCount, true)

            withContext(Dispatchers.Main) {
                val suggestionList = buildList {
                    suggestions.subList(
                        1.coerceAtMost(min(commit.compareTo(false), suggestions.size)),
                        maxSuggestionsToShow.coerceAtMost(suggestions.size)
                    ).map { keyboardManager.fixCase(it) }.forEach {
                        add(WordSuggestionCandidate(it, confidence = 1.0))
                    }
                }

                nlpManager.suggestDirectly(suggestionList)
                if (commit && suggestions.isNotEmpty()) {
                    maybeRescorePreviousGlide(suggestions.first())
                    keyboardManager.commitGesture(suggestions.first())
                    rememberPendingGlideCommit(suggestions)
                }
                callback.invoke(true)
            }
        }
    }

    private suspend fun maybeRescorePreviousGlide(nextWord: String) {
        val pending = pendingContextRescore ?: return
        if (System.currentTimeMillis() - pending.timestampMs > CONTEXT_RESCORE_WINDOW_MS) {
            pendingContextRescore = null
            return
        }
        val normalizedNext = GlideContextRescorer.normalizeGlideWordForContext(nextWord) ?: return
        val contextScores = pending.candidates
            .mapNotNull { candidate ->
                val normalizedCandidate = GlideContextRescorer.normalizeGlideWordForContext(candidate)
                    ?: return@mapNotNull null
                normalizedCandidate to nlpManager.nextWordContextScore(
                    previousWord = normalizedCandidate,
                    nextWord = normalizedNext,
                )
            }
            .toMap()
        val replacement = GlideContextRescorer.chooseReplacement(
            committedWord = pending.committedWord,
            candidateWords = pending.candidates,
            nextWord = nextWord,
            contextScores = contextScores,
        ) ?: return
        keyboardManager.replaceLastGestureWordForContext(
            expectedWord = pending.committedWord,
            replacementWord = replacement,
        )
    }

    private fun rememberPendingGlideCommit(suggestions: List<CharSequence>) {
        val committed = suggestions.firstOrNull()?.toString()?.takeIf { it.isNotBlank() } ?: return
        pendingContextRescore = PendingGlideCommit(
            committedWord = keyboardManager.fixCase(committed),
            candidates = suggestions.map { it.toString() },
            timestampMs = System.currentTimeMillis(),
        )
    }

    private data class PendingGlideCommit(
        val committedWord: String,
        val candidates: List<String>,
        val timestampMs: Long,
    )
}
