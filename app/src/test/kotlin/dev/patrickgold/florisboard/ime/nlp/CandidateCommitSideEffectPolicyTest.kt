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

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.media.emoji.Emoji
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class CandidateCommitSideEffectPolicyTest : FunSpec({
    test("provider acceptance notification requires a successful commit and a provider") {
        CandidateCommitSideEffectPolicy.shouldNotifyAcceptedProvider(
            commitSucceeded = true,
            hasSourceProvider = true,
        ) shouldBe true

        CandidateCommitSideEffectPolicy.shouldNotifyAcceptedProvider(
            commitSucceeded = false,
            hasSourceProvider = true,
        ) shouldBe false

        CandidateCommitSideEffectPolicy.shouldNotifyAcceptedProvider(
            commitSucceeded = true,
            hasSourceProvider = false,
        ) shouldBe false
    }

    test("learning follows successful non-clipboard candidate commits only") {
        CandidateCommitSideEffectPolicy.shouldLearnCommittedCandidate(
            commitSucceeded = true,
            isClipboardCandidate = false,
        ) shouldBe true

        CandidateCommitSideEffectPolicy.shouldLearnCommittedCandidate(
            commitSucceeded = true,
            isClipboardCandidate = true,
        ) shouldBe false

        CandidateCommitSideEffectPolicy.shouldLearnCommittedCandidate(
            commitSucceeded = false,
            isClipboardCandidate = false,
        ) shouldBe false
    }

    test("spacebar trailing space preserves legacy Latin auto-space behavior") {
        val candidate = WordSuggestionCandidate("hello", isEligibleForAutoCommit = true)

        CandidateCommitSideEffectPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidate = candidate,
            suppressPlainSpaceForPrediction = false,
            supportsAutoSpace = true,
        ) shouldBe true

        CandidateCommitSideEffectPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidate = candidate,
            suppressPlainSpaceForPrediction = false,
            supportsAutoSpace = false,
        ) shouldBe false
    }

    test("spacebar trailing space follows candidate and provider policy") {
        val providerOwnedNoSpace = WordSuggestionCandidate(
            text = "\u4f60",
            sourceProvider = NoTrailingSpaceProvider,
        )
        val explicitAlways = WordSuggestionCandidate(
            text = "done",
            trailingSpacePolicy = CandidateTrailingSpacePolicy.ALWAYS,
        )
        val emojiCandidate = EmojiSuggestionCandidate(
            emoji = Emoji("\uD83D\uDC4D", "thumbs up", emptyList()),
            showName = false,
        )

        providerOwnedNoSpace.trailingSpacePolicy shouldBe CandidateTrailingSpacePolicy.NEVER
        emojiCandidate.trailingSpacePolicy shouldBe CandidateTrailingSpacePolicy.NEVER

        CandidateCommitSideEffectPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidate = providerOwnedNoSpace,
            suppressPlainSpaceForPrediction = false,
            supportsAutoSpace = true,
        ) shouldBe false

        CandidateCommitSideEffectPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidate = emojiCandidate,
            suppressPlainSpaceForPrediction = false,
            supportsAutoSpace = true,
        ) shouldBe false

        CandidateCommitSideEffectPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidate = explicitAlways,
            suppressPlainSpaceForPrediction = false,
            supportsAutoSpace = false,
        ) shouldBe true
    }

    test("spacebar prediction suppression still blocks plain space without an accepted candidate") {
        CandidateCommitSideEffectPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidate = null,
            suppressPlainSpaceForPrediction = true,
            supportsAutoSpace = true,
        ) shouldBe false

        CandidateCommitSideEffectPolicy.shouldCommitPlainSpaceAfterSpacebar(
            candidate = null,
            suppressPlainSpaceForPrediction = false,
            supportsAutoSpace = false,
        ) shouldBe true
    }

    test("soft and hardware spacebar paths share the candidate trailing-space helper") {
        val source = locateKeyboardManagerSource().readText()
        val hardwareSpaceBody = source.substringAfter("fun handleHardwareKeyboardSpace()")
            .substringBefore("private fun handleSpace(data: KeyData)")
        val softSpaceBody = source.substringAfter("private fun handleSpace(data: KeyData)")
            .substringBefore("private fun shouldCommitPlainSpaceAfterSpacebar(")

        hardwareSpaceBody shouldContain "shouldCommitPlainSpaceAfterSpacebar(candidate, suppressPlainSpace)"
        softSpaceBody shouldContain "shouldCommitPlainSpaceAfterSpacebar(candidate, suppressPlainSpace)"
        source shouldContain "CandidateCommitSideEffectPolicy.shouldCommitPlainSpaceAfterSpacebar"
        source shouldNotContain "EditorInputBehaviorPolicy.shouldCommitPlainSpaceAfterSpacebar"
    }
})

private object NoTrailingSpaceProvider : SuggestionProvider {
    override val providerId = "test.no.trailing.space"
    override val candidateTrailingSpacePolicy = CandidateTrailingSpacePolicy.NEVER

    override suspend fun create() = Unit
    override suspend fun preload(subtype: Subtype) = Unit
    override suspend fun destroy() = Unit

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> = emptyList()

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) = Unit
    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) = Unit
    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean = false
    override suspend fun getListOfWords(subtype: Subtype): List<String> = emptyList()
    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double = 0.0
}

private fun locateKeyboardManagerSource(): File {
    return listOf(
        File("app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt"),
        File("../app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt"),
    ).first { it.isFile }
}
