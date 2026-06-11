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

    // Single-threaded so layout/word-data swaps never interleave with an
    // in-flight classification: the classifier mutates keys/pruner in place.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private var glideTypingClassifier = StatisticalGlideTypingClassifier(context)
    private var lastTime = System.currentTimeMillis()
    private var previewJob: Job? = null

    // Written from the main thread (gesture callbacks / post-commit remember),
    // read and cleared from the serialized classifier scope (context rescore).
    @Volatile
    private var pendingContextRescore: PendingGlideCommit? = null

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        previewJob?.cancel()
        commitCurrentGesture()
    }

    override fun onGlideWordBoundary(data: GlideTypingGesture.Detector.PointerData) {
        // Flow Through Space: commit the current most-confident gesture word, then clear
        // the classifier so the continuing trace starts fresh for the next word. The
        // existing commitGesture path already activates phantom-space, so the next
        // committed word will be auto-prefixed with " ".
        previewJob?.cancel()
        commitCurrentGesture()
    }

    private fun commitCurrentGesture() {
        // Snapshot-and-reset must happen synchronously at the boundary: the
        // finger keeps moving, so deferring the snapshot (or the clear) to the
        // async classification job would append the next word's points to this
        // word's gesture and wipe the head of the next word's trace.
        val snapshot = glideTypingClassifier.snapshotAndClear()
        launchSuggestions(snapshot, MAX_SUGGESTION_COUNT, commit = true)
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
            previewJob = launchSuggestions(gestureSnapshot = null, maxSuggestionsToShow = 1, commit = false)
            lastTime = time
        }
    }

    /**
     * Change the layout of the internal gesture classifier
     */
    fun setLayout(keys: List<TextKey>) {
        if (keys.isEmpty()) return
        val subtype = subtypeManager.activeSubtype
        if (!prefs.glide.isEnabledForSubtype(subtype)) return
        // Word-list load + pruner construction are far too heavy for the
        // composition path (the first glide-enabled layout per subtype used to
        // stall the main thread for the whole dictionary normalization pass);
        // run them on the serialized classifier scope instead. Queued commit
        // jobs run after this completes, so they observe the new layout.
        scope.launch {
            glideTypingClassifier.setLayout(keys, subtype)
        }
    }

    /**
     * Asks gesture classifier for suggestions and then passes that on to the smartbar.
     * Also commits the most confident suggestion if [commit] is set. All happens on an async executor.
     *
     * @param gestureSnapshot The gesture to classify, taken at the word boundary;
     * null means "classify the live in-progress gesture" (preview path).
     */
    private fun launchSuggestions(gestureSnapshot: StatisticalGlideTypingClassifier.Gesture?, maxSuggestionsToShow: Int, commit: Boolean): Job? {
        if (!prefs.glide.isEnabledForSubtype(subtypeManager.activeSubtype)) {
            return null
        }

        return scope.launch {
            // The ready check runs inside the serialized scope so a commit that
            // raced a subtype/layout swap waits for the queued setLayout job
            // instead of being dropped (or worse, classified on stale data).
            if (!glideTypingClassifier.ready) return@launch
            // For preview, only compute the few we'll display; for commit, compute all.
            val classifierCount = if (commit) MAX_SUGGESTION_COUNT else maxSuggestionsToShow.coerceAtLeast(1)
            val suggestions = if (gestureSnapshot != null) {
                glideTypingClassifier.getSuggestionsForSnapshot(gestureSnapshot, classifierCount)
            } else {
                glideTypingClassifier.getSuggestions(classifierCount, true)
            }

            // Score the previous-glide context rescore here, still on the
            // serialized classifier dispatcher: the first
            // nextWordContextScore per locale loads the persisted n-gram
            // tables from disk and must not run inside the Main block below.
            val rescore = if (commit && suggestions.isNotEmpty()) {
                computeGlideRescore(suggestions.first())
            } else {
                null
            }

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
                    rescore?.let { (expectedWord, replacementWord) ->
                        keyboardManager.replaceLastGestureWordForContext(
                            expectedWord = expectedWord,
                            replacementWord = replacementWord,
                        )
                    }
                    keyboardManager.commitGesture(suggestions.first())
                    rememberPendingGlideCommit(suggestions)
                }
            }
        }
    }

    /**
     * Computes the context-driven replacement for the previously committed
     * glide word, given the [nextWord] that is about to be committed. Pure
     * scoring — the editor mutation happens on the main thread afterwards
     * (replaceLastGestureWordForContext re-verifies the expected word against
     * the live editor content, so a stale result degrades to a no-op).
     *
     * @return (expectedWord, replacementWord) or null when no rescore applies.
     */
    private suspend fun computeGlideRescore(nextWord: CharSequence): Pair<String, String>? {
        val pending = pendingContextRescore ?: return null
        if (System.currentTimeMillis() - pending.timestampMs > CONTEXT_RESCORE_WINDOW_MS) {
            pendingContextRescore = null
            return null
        }
        val nextWordStr = nextWord.toString()
        val normalizedNext = GlideContextRescorer.normalizeGlideWordForContext(nextWordStr) ?: return null
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
            nextWord = nextWordStr,
            contextScores = contextScores,
        ) ?: return null
        return pending.committedWord to replacement
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
