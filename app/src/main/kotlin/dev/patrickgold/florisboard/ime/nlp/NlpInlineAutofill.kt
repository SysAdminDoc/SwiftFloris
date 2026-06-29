/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.os.Build
import android.util.Size
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionInfo
import android.widget.inline.InlineContentView
import androidx.annotation.RequiresApi
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NlpInlineAutofillSuggestion(
    val info: InlineSuggestionInfo,
    val view: InlineContentView?,
)

data class InlineSuggestionDimensions(
    val widthPx: Int,
    val heightPx: Int,
) {
    fun toAndroidSize(): Size {
        return Size(widthPx, heightPx)
    }
}

object NlpInlineAutofill {
    private val currentSequenceId = AtomicInteger(0)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val setterGuard = Mutex()

    val suggestions: StateFlow<List<NlpInlineAutofillSuggestion>>
        field = MutableStateFlow(emptyList())

    var suggestionsChipHeightPx: Int = 0

    @RequiresApi(Build.VERSION_CODES.R)
    fun showInlineSuggestions(context: Context, rawSuggestions: List<InlineSuggestion>): Boolean {
        val sequenceId = generateSequenceId()

        if (rawSuggestions.isEmpty()) {
            clearInlineSuggestions(sequenceId)
            return false
        }

        scope.launch {
            val size = InlineSuggestionSizePolicy.inflateSize(
                displayWidthPx = context.resources.displayMetrics.widthPixels,
                chipHeightPx = suggestionsChipHeightPx,
            ).toAndroidSize()
            val latch = CountDownLatch(rawSuggestions.size)
            val suggestionsArray = Array<NlpInlineAutofillSuggestion?>(rawSuggestions.size) { null }

            flogInfo { "showInlineSuggestions: [${sequenceId}] start inflating suggestions" }
            for ((index, rawSuggestion) in rawSuggestions.withIndex()) {
                try {
                    rawSuggestion.inflate(context, size, context.mainExecutor) { view ->
                        suggestionsArray[index] = NlpInlineAutofillSuggestion(rawSuggestion.info, view)
                        latch.countDown()
                    }
                } catch (e: RuntimeException) {
                    flogWarning { "showInlineSuggestions: [${sequenceId}] dropping invalid inline suggestion " +
                        "at index=$index size=$size: ${e.javaClass.simpleName}" }
                    latch.countDown()
                }
            }

            if (!latch.await(2_000, TimeUnit.MILLISECONDS)) {
                flogWarning { "showInlineSuggestions: [${sequenceId}] timed out while waiting for all " +
                    "suggestions to inflate" }
                return@launch
            }

            val inflatedSuggestions = suggestionsArray.filterNotNull().sortedByDescending { it.info.isPinned }
            // withLock releases the mutex in a finally, so an exception or
            // coroutine cancellation between acquire and release can no longer
            // leave the guard permanently locked (which would silently freeze
            // every later inline-suggestion update for the process lifetime).
            setterGuard.withLock {
                flogInfo { "showInlineSuggestions: [${sequenceId}] successfully inflated " +
                    "${inflatedSuggestions.count { it.view != null }} out of ${inflatedSuggestions.size} suggestions" }
                if (currentSequenceId.get() == sequenceId) {
                    flogInfo { "showInlineSuggestions: [${sequenceId}] setting suggestions" }
                    suggestions.value = inflatedSuggestions
                } else {
                    flogWarning { "showInlineSuggestions: [${sequenceId}] seqId != current, skip setting suggestions" }
                }
            }
        }

        return true
    }

    fun clearInlineSuggestions() {
        // Increment sequence id to invalidate eventual pending suggestions
        clearInlineSuggestions(generateSequenceId())
    }

    private fun clearInlineSuggestions(sequenceId: Int) {
        scope.launch {
            setterGuard.withLock {
                flogInfo { "clearInlineSuggestions: [${sequenceId}] clearing suggestions" }
                suggestions.value = emptyList()
            }
        }
    }

    private fun generateSequenceId(): Int {
        return currentSequenceId.incrementAndGet()
    }
}

object InlineSuggestionSizePolicy {
    private const val MinDimensionPx = 1
    private const val FallbackWidthPx = 320
    private const val FallbackHeightPx = 48
    private const val MaxWidthPx = 4096
    private const val MaxHeightPx = 512

    val presentationMinDimensions = InlineSuggestionDimensions(MinDimensionPx, MinDimensionPx)
    val presentationMinSize: Size
        get() = presentationMinDimensions.toAndroidSize()

    fun presentationMaxDimensions(displayWidthPx: Int, chipHeightPx: Int): InlineSuggestionDimensions {
        return InlineSuggestionDimensions(
            sanitizeDimension(displayWidthPx, fallback = FallbackWidthPx, max = MaxWidthPx),
            sanitizeDimension(chipHeightPx, fallback = FallbackHeightPx, max = MaxHeightPx),
        )
    }

    fun presentationMaxSize(displayWidthPx: Int, chipHeightPx: Int): Size {
        return presentationMaxDimensions(displayWidthPx, chipHeightPx).toAndroidSize()
    }

    fun inflateSize(displayWidthPx: Int, chipHeightPx: Int): InlineSuggestionDimensions {
        return presentationMaxDimensions(displayWidthPx, chipHeightPx)
    }

    internal fun isValidInlineDimensions(size: InlineSuggestionDimensions): Boolean {
        return size.widthPx in MinDimensionPx..MaxWidthPx &&
            size.heightPx in MinDimensionPx..MaxHeightPx
    }

    private fun sanitizeDimension(value: Int, fallback: Int, max: Int): Int {
        return when {
            value in MinDimensionPx..max -> value
            value < MinDimensionPx -> fallback
            else -> max
        }
    }
}
