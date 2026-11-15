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
import android.view.ViewGroup
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

    /**
     * Width the keyboard can actually show a chip in, published by
     * `FlorisImeSizing` from the live window spec. The display width is not a
     * substitute: a floating, one-handed, split or resized keyboard is narrower
     * than the display, and a chip inflated at display width runs off its edge.
     */
    var suggestionsChipMaxWidthPx: Int = 0

    /**
     * Width of the Smartbar slot the chips are laid out in, minus the chip
     * margins, published by the Smartbar when it is measured. A full-width
     * keyboard still puts the actions toggle and sticky action beside the chips,
     * so the keyboard width alone lets a chip run past the right edge (#23).
     * 0 until the slot has been measured.
     */
    var suggestionsSlotChipMaxWidthPx: Int = 0

    fun presentationMaxWidthPx(): Int = InlineSuggestionSizePolicy.availableChipWidthPx(
        keyboardChipMaxWidthPx = suggestionsChipMaxWidthPx,
        slotChipMaxWidthPx = suggestionsSlotChipMaxWidthPx,
    )

    @RequiresApi(Build.VERSION_CODES.R)
    fun showInlineSuggestions(context: Context, rawSuggestions: List<InlineSuggestion>): Boolean {
        val sequenceId = generateSequenceId()

        if (rawSuggestions.isEmpty()) {
            clearInlineSuggestions(sequenceId)
            return false
        }

        scope.launch {
            val chipHeightPx = suggestionsChipHeightPx
            val latch = CountDownLatch(rawSuggestions.size)
            val suggestionsArray = Array<NlpInlineAutofillSuggestion?>(rawSuggestions.size) { null }

            flogInfo { "showInlineSuggestions: [${sequenceId}] start inflating suggestions" }
            for ((index, rawSuggestion) in rawSuggestions.withIndex()) {
                val spec = rawSuggestion.info.inlinePresentationSpec
                val size = InlineSuggestionSizePolicy.inflateSize(
                    chipHeightPx = chipHeightPx,
                    specMinHeightPx = spec.minSize.height,
                    specMaxHeightPx = spec.maxSize.height,
                ).toAndroidSize()
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
    const val WrapContentPx = ViewGroup.LayoutParams.WRAP_CONTENT

    val presentationMinDimensions = InlineSuggestionDimensions(MinDimensionPx, MinDimensionPx)
    val presentationMinSize: Size
        get() = presentationMinDimensions.toAndroidSize()

    /**
     * The chip height is published from composition, and Android asks for the
     * request before a freshly started keyboard has composed. The response
     * arrives after it has, so an unknown height leaves room for whatever the
     * Smartbar settles on. A small guess here made [InlineSuggestion.inflate]
     * reject the real height and drop every chip on the first login field.
     */
    fun presentationMaxDimensions(keyboardWidthPx: Int, chipHeightPx: Int): InlineSuggestionDimensions {
        return InlineSuggestionDimensions(
            sanitizeDimension(keyboardWidthPx, fallback = FallbackWidthPx, max = MaxWidthPx),
            sanitizeDimension(chipHeightPx, fallback = MaxHeightPx, max = MaxHeightPx),
        )
    }

    fun presentationMaxSize(keyboardWidthPx: Int, chipHeightPx: Int): Size {
        return presentationMaxDimensions(keyboardWidthPx, chipHeightPx).toAndroidSize()
    }

    /**
     * The chip width the presentation spec may grow to. The measured Smartbar
     * slot wins when it is known because the chips never get more room than it;
     * before the first layout pass the keyboard width is the best bound there is.
     */
    fun availableChipWidthPx(keyboardChipMaxWidthPx: Int, slotChipMaxWidthPx: Int): Int {
        return when {
            slotChipMaxWidthPx > 0 && keyboardChipMaxWidthPx > 0 -> minOf(slotChipMaxWidthPx, keyboardChipMaxWidthPx)
            slotChipMaxWidthPx > 0 -> slotChipMaxWidthPx
            else -> keyboardChipMaxWidthPx
        }
    }

    /**
     * Chip width for a keyboard that hasn't been laid out yet, taken from the window spec it will
     * settle on. The Smartbar puts its actions toggle and the sticky action, each a square as tall
     * as the Smartbar row, beside the chip slot, so the estimate leaves room for both whatever the
     * layout. Before this a cold request fell back to 320 px, and the password manager kept that
     * width for every field in the form, which cut "Bitwarden My vault" to "Bitwar…" (#23).
     */
    fun estimatedChipWidthPx(
        keyboardWidthPx: Int,
        smartbarRowHeightPx: Int,
        stickyActionPaddingPx: Int,
        chipMarginPx: Int,
    ): Int {
        val besideSlotPx = 2 * smartbarRowHeightPx.coerceAtLeast(0) + stickyActionPaddingPx.coerceAtLeast(0)
        return (keyboardWidthPx - besideSlotPx - chipMarginPx.coerceAtLeast(0)).coerceAtLeast(0)
    }

    /**
     * Chips are inflated at their content width, bounded by the spec maximum.
     * A fixed width makes every chip as wide as the keyboard, so even a short
     * "Vault is locked" chip pushes its label past the screen edge (#23).
     * [InlineSuggestion.inflate] accepts WRAP_CONTENT for either dimension.
     *
     * The height is kept inside the presentation spec the suggestion was built
     * against, because inflate throws for anything outside it. The keyboard can
     * change height between sending the request and getting the response.
     */
    fun inflateSize(
        chipHeightPx: Int,
        specMinHeightPx: Int = MinDimensionPx,
        specMaxHeightPx: Int = MaxHeightPx,
    ): InlineSuggestionDimensions {
        val heightPx = sanitizeDimension(chipHeightPx, fallback = FallbackHeightPx, max = MaxHeightPx)
        val lowPx = specMinHeightPx.coerceAtLeast(MinDimensionPx)
        val highPx = specMaxHeightPx.coerceAtMost(MaxHeightPx)
        return InlineSuggestionDimensions(
            WrapContentPx,
            if (lowPx <= highPx) heightPx.coerceIn(lowPx, highPx) else heightPx,
        )
    }

    internal fun isValidInlineDimensions(size: InlineSuggestionDimensions): Boolean {
        return (size.widthPx == WrapContentPx || size.widthPx in MinDimensionPx..MaxWidthPx) &&
            (size.heightPx == WrapContentPx || size.heightPx in MinDimensionPx..MaxHeightPx)
    }

    private fun sanitizeDimension(value: Int, fallback: Int, max: Int): Int {
        return when {
            value in MinDimensionPx..max -> value
            value < MinDimensionPx -> fallback
            else -> max
        }
    }
}
