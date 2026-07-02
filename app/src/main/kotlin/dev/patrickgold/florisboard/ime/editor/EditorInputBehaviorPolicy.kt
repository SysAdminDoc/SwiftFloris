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

package dev.patrickgold.florisboard.ime.editor

import dev.patrickgold.florisboard.ime.nlp.PunctuationRule

internal object EditorInputBehaviorPolicy {
    private val DoubleSpacePeriodMatcher = """([^.!?\u203D\s]\s)""".toRegex()

    fun shouldInsertAutoSpaceBefore(
        text: String,
        textBeforeCursor: String,
        punctuationRule: PunctuationRule,
        isAutoSpacePunctuationEnabled: Boolean,
        isRawInputEditor: Boolean,
        isNormalKeyVariation: Boolean,
    ): Boolean {
        if (!isAutoSpacePunctuationEnabled || text.isEmpty()) return false
        if (isRawInputEditor || !isNormalKeyVariation) return false
        return textBeforeCursor.isNotEmpty() &&
            !textBeforeCursor.last().isWhitespace() &&
            punctuationRule.symbolsFollowingAutoSpace.contains(text.first())
    }

    fun shouldInsertAutoSpaceAfter(
        text: String,
        textBeforeCursor: String,
        currentWordText: String,
        punctuationRule: PunctuationRule,
        isAutoSpaceActive: Boolean,
        isAutoSpacePunctuationEnabled: Boolean,
        isRawInputEditor: Boolean,
        isNormalKeyVariation: Boolean,
    ): Boolean {
        if (!isAutoSpacePunctuationEnabled || text.isEmpty()) return false
        if (isRawInputEditor || !isNormalKeyVariation) return false
        val normalizedTextBefore = if (
            isAutoSpaceActive &&
            textBeforeCursor.isNotEmpty() &&
            textBeforeCursor.last() == ' '
        ) {
            textBeforeCursor.dropLast(1)
        } else {
            textBeforeCursor
        }
        return normalizedTextBefore.isNotEmpty() &&
            !normalizedTextBefore.last().isWhitespace() &&
            currentWordText.all { !it.isDigit() } &&
            punctuationRule.symbolsPrecedingAutoSpace.contains(text.first())
    }

    fun shouldAutoCapitalizeAfter(
        char: String,
        textBeforeCursor: String,
        isAutoCapitalizationEnabled: Boolean,
        isRawInputEditor: Boolean,
        isNormalKeyVariation: Boolean,
    ): Boolean {
        if (!isAutoCapitalizationEnabled || !isSentenceEndingPunctuation(char)) return false
        if (isRawInputEditor || !isNormalKeyVariation) return false
        val charBeforePunctuation = textBeforeCursor.lastOrNull() ?: return false
        if (!charBeforePunctuation.isLetter()) return false
        val charBeforeWordEnd = textBeforeCursor.dropLast(1).lastOrNull()
        if (charBeforeWordEnd != null &&
            !charBeforeWordEnd.isLetter() &&
            !charBeforeWordEnd.isWhitespace()
        ) {
            return false
        }
        return true
    }

    fun shouldInsertPhantomSpace(
        text: String,
        textBeforeCursor: String,
        punctuationRule: PunctuationRule,
        isActive: Boolean,
        forceActive: Boolean,
        isSelectionValid: Boolean,
        selectionStart: Int,
        supportsAutoSpace: Boolean,
    ): Boolean {
        if (!(isActive || forceActive) || !isSelectionValid || selectionStart <= 0 || text.isEmpty()) {
            return false
        }
        if (!supportsAutoSpace || textBeforeCursor.isEmpty()) return false
        val previous = textBeforeCursor.last()
        val next = text.first()
        return (punctuationRule.symbolsPrecedingPhantomSpace.contains(previous) || previous.isLetterOrDigit()) &&
            (punctuationRule.symbolsFollowingPhantomSpace.contains(next) || next.isLetterOrDigit())
    }

    fun shouldEscalateGlideBackspaceToWordDelete(
        operationUnit: OperationUnit,
        isPhantomSpaceActive: Boolean,
        isCurrentWordValid: Boolean,
        immediateBackspaceDeletesWord: Boolean,
    ): Boolean {
        return operationUnit == OperationUnit.CHARACTERS &&
            immediateBackspaceDeletesWord &&
            isPhantomSpaceActive &&
            isCurrentWordValid
    }

    fun shouldConvertDoubleSpaceToPeriod(textBeforeCursor: String): Boolean {
        return textBeforeCursor.length == 2 && DoubleSpacePeriodMatcher.matches(textBeforeCursor)
    }

    private fun isSentenceEndingPunctuation(char: String): Boolean {
        return char.isNotEmpty() && char.first() in ".!?"
    }
}
