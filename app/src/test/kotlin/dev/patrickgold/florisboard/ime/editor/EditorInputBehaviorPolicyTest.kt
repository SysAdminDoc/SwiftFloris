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

import dev.patrickgold.florisboard.ime.nlp.AutoCommitSuppression
import dev.patrickgold.florisboard.ime.nlp.PunctuationRule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EditorInputBehaviorPolicyTest : FunSpec({
    val pairedPunctuationRule = PunctuationRule(
        id = "test",
        symbolsPrecedingAutoSpace = ".,?!",
        symbolsFollowingAutoSpace = "([{",
        symbolsPrecedingPhantomSpace = ".,?!",
        symbolsFollowingPhantomSpace = "([{",
        symbolsTerminatingSentence = ".?!",
    )

    test("autocorrect acceptance still commits a trailing space for auto-space locales") {
        EditorInputBehaviorPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidateAccepted = true,
            suppressPlainSpaceForPrediction = false,
            supportsAutoSpace = true,
        ) shouldBe true
    }

    test("autocorrect acceptance avoids a duplicate trailing space for no-auto-space locales") {
        EditorInputBehaviorPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidateAccepted = true,
            suppressPlainSpaceForPrediction = false,
            supportsAutoSpace = false,
        ) shouldBe false
    }

    test("prediction suppression can reject plain space without an accepted candidate") {
        EditorInputBehaviorPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidateAccepted = false,
            suppressPlainSpaceForPrediction = true,
            supportsAutoSpace = true,
        ) shouldBe false
    }

    test("backspace rejection protects the original autocorrect slot from reacceptance") {
        val suppression = AutoCommitSuppression()

        suppression.rememberAccepted(originalText = "teh", correctedText = "the", wordStart = 0)
        suppression.rejectAccepted(textBeforeSelection = "the ", cursorPosition = 4) shouldBe true

        suppression.shouldSuppress(currentWord = "teh", candidateText = "the", currentWordStart = 0) shouldBe true
        suppression.shouldKeepTypedLiteral(currentWord = "teh", currentWordStart = 0) shouldBe true
    }

    test("auto-space inserts before paired opening punctuation when text touches it") {
        EditorInputBehaviorPolicy.shouldInsertAutoSpaceBefore(
            text = "(",
            textBeforeCursor = "hello",
            punctuationRule = pairedPunctuationRule,
            isAutoSpacePunctuationEnabled = true,
            isRawInputEditor = false,
            isNormalKeyVariation = true,
        ) shouldBe true
    }

    test("auto-space inserts after terminal punctuation for non-numeric current words") {
        EditorInputBehaviorPolicy.shouldInsertAutoSpaceAfter(
            text = ".",
            textBeforeCursor = "hello",
            currentWordText = "hello",
            punctuationRule = pairedPunctuationRule,
            isAutoSpaceActive = false,
            isAutoSpacePunctuationEnabled = true,
            isRawInputEditor = false,
            isNormalKeyVariation = true,
        ) shouldBe true
    }

    test("auto-space does not split numeric punctuation") {
        EditorInputBehaviorPolicy.shouldInsertAutoSpaceAfter(
            text = ".",
            textBeforeCursor = "3",
            currentWordText = "3",
            punctuationRule = pairedPunctuationRule,
            isAutoSpaceActive = false,
            isAutoSpacePunctuationEnabled = true,
            isRawInputEditor = false,
            isNormalKeyVariation = true,
        ) shouldBe false
    }

    test("phantom space inserts between an accepted word and the next typed word") {
        EditorInputBehaviorPolicy.shouldInsertPhantomSpace(
            text = "world",
            textBeforeCursor = "hello",
            punctuationRule = pairedPunctuationRule,
            isActive = true,
            forceActive = false,
            isSelectionValid = true,
            selectionStart = 5,
            supportsAutoSpace = true,
        ) shouldBe true
    }

    test("phantom space is disabled for no-auto-space locales") {
        EditorInputBehaviorPolicy.shouldInsertPhantomSpace(
            text = "world",
            textBeforeCursor = "hello",
            punctuationRule = pairedPunctuationRule,
            isActive = true,
            forceActive = false,
            isSelectionValid = true,
            selectionStart = 5,
            supportsAutoSpace = false,
        ) shouldBe false
    }

    test("glide backspace escalates character delete to word delete for an active committed glide word") {
        EditorInputBehaviorPolicy.shouldEscalateGlideBackspaceToWordDelete(
            operationUnit = OperationUnit.CHARACTERS,
            isPhantomSpaceActive = true,
            isCurrentWordValid = true,
            immediateBackspaceDeletesWord = true,
        ) shouldBe true
    }

    test("glide backspace does not escalate when the preference is disabled") {
        EditorInputBehaviorPolicy.shouldEscalateGlideBackspaceToWordDelete(
            operationUnit = OperationUnit.CHARACTERS,
            isPhantomSpaceActive = true,
            isCurrentWordValid = true,
            immediateBackspaceDeletesWord = false,
        ) shouldBe false
    }

    test("glide backspace does not escalate without an active phantom-space word") {
        EditorInputBehaviorPolicy.shouldEscalateGlideBackspaceToWordDelete(
            operationUnit = OperationUnit.CHARACTERS,
            isPhantomSpaceActive = false,
            isCurrentWordValid = true,
            immediateBackspaceDeletesWord = true,
        ) shouldBe false
    }

    test("glide backspace does not re-escalate explicit word deletes") {
        EditorInputBehaviorPolicy.shouldEscalateGlideBackspaceToWordDelete(
            operationUnit = OperationUnit.WORDS,
            isPhantomSpaceActive = true,
            isCurrentWordValid = true,
            immediateBackspaceDeletesWord = true,
        ) shouldBe false
    }

    test("double-space period only accepts non-terminal text followed by a space") {
        EditorInputBehaviorPolicy.shouldConvertDoubleSpaceToPeriod("a ") shouldBe true
        EditorInputBehaviorPolicy.shouldConvertDoubleSpaceToPeriod(". ") shouldBe false
        EditorInputBehaviorPolicy.shouldConvertDoubleSpaceToPeriod("! ") shouldBe false
        EditorInputBehaviorPolicy.shouldConvertDoubleSpaceToPeriod("  ") shouldBe false
    }

    test("sentence capitalization arms after word-ending punctuation only") {
        EditorInputBehaviorPolicy.shouldAutoCapitalizeAfter(
            char = ".",
            textBeforeCursor = "hi",
            isAutoCapitalizationEnabled = true,
            isRawInputEditor = false,
            isNormalKeyVariation = true,
        ) shouldBe true

        EditorInputBehaviorPolicy.shouldAutoCapitalizeAfter(
            char = ".",
            textBeforeCursor = "3",
            isAutoCapitalizationEnabled = true,
            isRawInputEditor = false,
            isNormalKeyVariation = true,
        ) shouldBe false

        EditorInputBehaviorPolicy.shouldAutoCapitalizeAfter(
            char = ".",
            textBeforeCursor = "e.g",
            isAutoCapitalizationEnabled = true,
            isRawInputEditor = false,
            isNormalKeyVariation = true,
        ) shouldBe false
    }
})
