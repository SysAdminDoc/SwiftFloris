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

import android.icu.text.BreakIterator
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypeNlpProviderMap
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class NlpProviderRegistryTest : FunSpec({
    test("provider lookup is not blocked by in-flight provider preload") {
        val provider = BlockingSuggestionProvider()
        val registry = NlpProviderRegistry(listOf(provider))
        val subtype = Subtype.DEFAULT.copy(
            nlpProviders = SubtypeNlpProviderMap(
                spelling = provider.providerId,
                suggestion = provider.providerId,
            ),
        )

        coroutineScope {
            val preloadJob = launch { registry.preload(subtype) }
            provider.preloadStarted.await()

            withTimeout(250) {
                registry.suggestionProvider(subtype) shouldBe provider
            }

            provider.releasePreload.complete(Unit)
            preloadJob.join()
        }
    }
})

private class BlockingSuggestionProvider : SuggestionProvider {
    override val providerId: String = "dev.patrickgold.florisboard.test.blocking"
    val preloadStarted = CompletableDeferred<Unit>()
    val releasePreload = CompletableDeferred<Unit>()

    override suspend fun create() = Unit

    override suspend fun preload(subtype: Subtype) {
        preloadStarted.complete(Unit)
        releasePreload.await()
    }

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

    override suspend fun determineLocalComposing(
        subtype: Subtype,
        textBeforeSelection: CharSequence,
        breakIterators: BreakIteratorGroup,
        localLastCommitPosition: Int,
    ): EditorRange = EditorRange.Unspecified
}
