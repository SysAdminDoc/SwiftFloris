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

package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange

internal data class AutoCommitCorrection(
    val original: String,
    val corrected: String,
    val range: EditorRange,
    val sourceProvider: SuggestionProvider?,
) {
    fun toUndoCandidate(): AutoCommitUndoSuggestionCandidate {
        return AutoCommitUndoSuggestionCandidate(
            original = original,
            corrected = corrected,
            range = range,
            sourceProvider = sourceProvider,
        )
    }
}

internal class AutoCommitUndoSession(
    private val maxCorrections: Int = MaxCorrections,
) {
    private val corrections = ArrayDeque<AutoCommitCorrection>()
    private var activeCorrection: AutoCommitCorrection? = null

    val activeUndoCandidate: AutoCommitUndoSuggestionCandidate?
        get() = activeCorrection?.toUndoCandidate()

    fun remember(
        originalText: CharSequence,
        correctedText: CharSequence,
        wordStart: Int?,
        sourceProvider: SuggestionProvider?,
    ) {
        val original = originalText.toString()
        val corrected = correctedText.toString()
        if (wordStart == null || original.isBlank() || corrected.isBlank() || original == corrected) {
            return
        }
        val correction = AutoCommitCorrection(
            original = original,
            corrected = corrected,
            range = EditorRange(wordStart, wordStart + corrected.length),
            sourceProvider = sourceProvider,
        )
        corrections.removeAll { it.range == correction.range }
        corrections.addFirst(correction)
        while (corrections.size > maxCorrections) {
            corrections.removeLast()
        }
        activeCorrection = correction
    }

    fun onContentChanged(content: EditorContent) {
        if (content.selection.isNotValid) {
            activeCorrection = null
            return
        }
        corrections.removeAll { correction ->
            content.textAt(correction.range) != correction.corrected
        }
        activeCorrection = corrections.firstOrNull { correction ->
            correction.selectionTouches(content.selection)
        }
    }

    fun consume(candidate: AutoCommitUndoSuggestionCandidate): AutoCommitCorrection? {
        val correction = corrections.firstOrNull {
            it.original == candidate.original &&
                it.corrected == candidate.corrected &&
                it.range == candidate.range
        } ?: return null
        corrections.remove(correction)
        if (activeCorrection == correction) {
            activeCorrection = null
        }
        return correction
    }

    fun clear() {
        corrections.clear()
        activeCorrection = null
    }

    private fun AutoCommitCorrection.selectionTouches(selection: EditorRange): Boolean {
        return if (selection.isCursorMode) {
            selection.start in range.start..(range.end + 1)
        } else {
            selection.start < range.end && selection.end > range.start
        }
    }

    private fun EditorContent.textAt(range: EditorRange): String? {
        val localStart = range.start - offset
        val localEnd = range.end - offset
        if (offset < 0 || localStart < 0 || localEnd > text.length || localStart >= localEnd) {
            return null
        }
        return text.substring(localStart, localEnd)
    }

    private companion object {
        const val MaxCorrections = 5
    }
}
