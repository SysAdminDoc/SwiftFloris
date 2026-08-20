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

import android.app.ActivityManager
import android.content.Context
import androidx.core.app.ActivityManagerCompat
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CancellationException
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
        private const val DEFAULT_POINTER_ID = 0
        private const val MAX_SUGGESTION_COUNT = 8
        private const val CONTEXT_RESCORE_WINDOW_MS = 6_000L
    }

    private val prefs by FlorisPreferenceStore
    private val appContext = context
    private val keyboardManager by context.keyboardManager()
    private val nlpManager by context.nlpManager()
    private val subtypeManager by context.subtypeManager()

    // Single-threaded so layout/word-data swaps never interleave with an
    // in-flight classification: the classifier mutates keys/pruner in place.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    private val classifierLock = Any()
    private val classifiers = mutableMapOf<Int, StatisticalGlideTypingClassifier>()
    private var layoutSnapshot: LayoutSnapshot? = null
    private var lastTime = System.currentTimeMillis()
    private val previewJobs = mutableMapOf<Int, Job>()

    // Written from the main thread (gesture callbacks / post-commit remember),
    // read and cleared from the serialized classifier scope (context rescore).
    @Volatile
    private var pendingContextRescore: PendingGlideCommit? = null

    init {
        // Android flags devices that cannot afford large heaps; the glide vocabulary, its pruner
        // and the ideal-gesture cache are exactly the kind of allocation that flag exists for.
        val activityManager = context.applicationContext
            .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = activityManager?.let { ActivityManagerCompat.isLowRamDevice(it) } ?: false
        GlideTypingCapability.setLowRamDevice(isLowRam)
        if (isLowRam) {
            flogWarning { "Glide typing disabled: device is flagged low-RAM" }
        }
    }

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        onGlideComplete(data.pointerId, data)
    }

    override fun onGlideComplete(pointerId: Int, data: GlideTypingGesture.Detector.PointerData) {
        cancelPreviewJob(pointerId)
        commitCurrentGesture(pointerId)
    }

    override fun onGlideWordBoundary(data: GlideTypingGesture.Detector.PointerData) {
        onGlideWordBoundary(data.pointerId, data)
    }

    override fun onGlideWordBoundary(pointerId: Int, data: GlideTypingGesture.Detector.PointerData) {
        // Flow Through Space: commit the current most-confident gesture word, then clear
        // the classifier so the continuing trace starts fresh for the next word. The
        // existing commitGesture path already activates phantom-space, so the next
        // committed word will be auto-prefixed with " ".
        cancelPreviewJob(pointerId)
        commitCurrentGesture(pointerId)
    }

    private fun commitCurrentGesture(pointerId: Int) {
        // Snapshot-and-reset must happen synchronously at the boundary: the
        // finger keeps moving, so deferring the snapshot (or the clear) to the
        // async classification job would append the next word's points to this
        // word's gesture and wipe the head of the next word's trace.
        val classifier = classifierFor(pointerId)
        val snapshot = classifier.snapshotAndClear()
        launchSuggestions(classifier, snapshot, MAX_SUGGESTION_COUNT, commit = true, pointerId = pointerId)
    }

    override fun onGlideCancelled() {
        previewJobs.values.forEach(Job::cancel)
        previewJobs.clear()
        synchronized(classifierLock) {
            classifiers.values.forEach { it.clear() }
        }
        pendingContextRescore = null
    }

    override fun onGlideCancelled(pointerId: Int) {
        cancelPreviewJob(pointerId)
        classifierFor(pointerId).clear()
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        onGlideAddPoint(DEFAULT_POINTER_ID, point)
    }

    override fun onGlideAddPoint(pointerId: Int, point: GlideTypingGesture.Detector.Position) {
        val normalized = GlideTypingGesture.Detector.Position(point.x, point.y)
        val classifier = classifierFor(pointerId)

        classifier.addGesturePoint(normalized)

        val time = System.currentTimeMillis()
        if (prefs.glide.showPreview.get() && time - lastTime > prefs.glide.previewRefreshDelay.get()) {
            // Cancel any stale preview job so they don't pile up.
            cancelPreviewJob(pointerId)
            launchSuggestions(
                classifier = classifier,
                gestureSnapshot = null,
                maxSuggestionsToShow = 1,
                commit = false,
                pointerId = pointerId,
            )?.let { previewJobs[pointerId] = it }
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
        // A low-RAM device, or a session that already ran out of memory building this data, must
        // not attempt the build again — that is the allocation that failed in the first place.
        if (!GlideTypingCapability.isAvailable) return
        // Word-list load + pruner construction are far too heavy for the
        // composition path (the first glide-enabled layout per subtype used to
        // stall the main thread for the whole dictionary normalization pass);
        // run them on the serialized classifier scope instead. Queued commit
        // jobs run after this completes, so they observe the new layout.
        val snapshot = LayoutSnapshot(keys.toList(), subtype)
        val targets = synchronized(classifierLock) {
            layoutSnapshot = snapshot
            classifiers.values.toList()
        }
        targets.forEach { classifier ->
            configureClassifier(classifier, snapshot)
        }
    }

    /**
     * Releases every partial allocation and turns glide off for the rest of this IME session.
     * The next session re-evaluates, so a transient memory spike does not disable the feature
     * permanently. Only the error class is logged — never the gesture or any typed text.
     */
    private fun disableAfterAllocationFailure(error: Throwable) {
        synchronized(classifierLock) {
            classifiers.values.forEach { it.releaseMemory() }
        }
        GlideTypingCapability.disableAfterAllocationFailure()
        flogWarning {
            "Glide typing disabled for this session after ${error::class.java.simpleName} " +
                "while building gesture data"
        }
    }

    /**
     * Asks gesture classifier for suggestions and then passes that on to the smartbar.
     * Also commits the most confident suggestion if [commit] is set. All happens on an async executor.
     *
     * @param gestureSnapshot The gesture to classify, taken at the word boundary;
     * null means "classify the live in-progress gesture" (preview path).
     */
    private fun launchSuggestions(
        classifier: StatisticalGlideTypingClassifier,
        gestureSnapshot: StatisticalGlideTypingClassifier.Gesture?,
        maxSuggestionsToShow: Int,
        commit: Boolean,
        pointerId: Int,
    ): Job? {
        if (!prefs.glide.isEnabledForSubtype(subtypeManager.activeSubtype)) {
            return null
        }
        if (!GlideTypingCapability.isAvailable) {
            return null
        }

        return scope.launch {
            // The ready check runs inside the serialized scope so a commit that
            // raced a subtype/layout swap waits for the queued setLayout job
            // instead of being dropped (or worse, classified on stale data).
            if (!classifier.ready) return@launch
            // For preview, only compute the few we'll display; for commit, compute all.
            val classifierCount = if (commit) MAX_SUGGESTION_COUNT else maxSuggestionsToShow.coerceAtLeast(1)
            val suggestions = try {
                if (gestureSnapshot != null) {
                    classifier.getSuggestionsForSnapshot(gestureSnapshot, classifierCount)
                } else {
                    classifier.getSuggestions(classifierCount, true)
                }
            } catch (error: OutOfMemoryError) {
                // Classification grows the ideal-gesture cache; if that is what tips the heap
                // over, drop it all rather than retry the same allocation on the next gesture.
                disableAfterAllocationFailure(error)
                return@launch
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

                if (commit && suggestions.isNotEmpty()) {
                    rescore?.let { (expectedWord, replacementWord) ->
                        keyboardManager.replaceLastGestureWordForContext(
                            expectedWord = expectedWord,
                            replacementWord = replacementWord,
                        )
                    }
                    keyboardManager.commitGesture(
                        word = suggestions.first(),
                        alternatives = suggestionList.map { it.text.toString() },
                    )
                    rememberPendingGlideCommit(suggestions)
                } else {
                    nlpManager.suggestDirectly(suggestionList)
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

    private fun cancelPreviewJob(pointerId: Int) {
        previewJobs.remove(pointerId)?.cancel()
    }

    private fun classifierFor(pointerId: Int): StatisticalGlideTypingClassifier {
        val result = synchronized(classifierLock) {
            val existing = classifiers[pointerId]
            if (existing != null) {
                existing to null
            } else {
                val created = StatisticalGlideTypingClassifier(appContext)
                classifiers[pointerId] = created
                created to layoutSnapshot
            }
        }
        result.second?.let { configureClassifier(result.first, it) }
        return result.first
    }

    private fun configureClassifier(
        classifier: StatisticalGlideTypingClassifier,
        snapshot: LayoutSnapshot,
    ) {
        scope.launch {
            try {
                classifier.setLayout(snapshot.keys, snapshot.subtype)
            } catch (cancellation: CancellationException) {
                // A cancelled build leaves half a dictionary and a half-filled pruner behind.
                classifier.releaseMemory()
                throw cancellation
            } catch (error: OutOfMemoryError) {
                disableAfterAllocationFailure(error)
            }
        }
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

    private data class LayoutSnapshot(
        val keys: List<TextKey>,
        val subtype: Subtype,
    )
}
