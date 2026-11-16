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

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The keyboard's country reaches the misspelling fix through the real provider, the way a typing session asks. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LatinLanguageProviderSpellingRegionTest {
    private lateinit var provider: LatinLanguageProvider

    @Before
    fun setUp() {
        provider = LatinLanguageProvider(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun spellCheckUsesTheKeyboardsCountry() {
        runBlocking {
            spellFix("en-UK", "jewelery") shouldBe "jewellery"
            spellFix("en-US", "jewelery") shouldBe "jewelry"
            spellFix("en-CA", "jewelery") shouldBe "jewellery"
            spellFix("en-CA", "relize") shouldBe "realize"
            spellFix("en-UK", "relize") shouldBe "realise"
        }
    }

    @Test
    fun suggestionsUseTheKeyboardsCountry() {
        runBlocking {
            suggestedFix("en-UK", "nieghbor") shouldBe "neighbour"
            suggestedFix("en-US", "nieghbor") shouldBe "neighbor"
            suggestedFix("en-CA", "nieghbor") shouldBe "neighbour"
            suggestedFix("en-CA", "fullfill") shouldBe "fulfill"
            suggestedFix("en-AU", "fullfill") shouldBe "fulfil"
        }
    }

    private fun english(tag: String) = Subtype.DEFAULT.copy(primaryLocale = FlorisLocale.fromTag(tag))

    private suspend fun spellFix(tag: String, word: String): String {
        val result = provider.spell(
            subtype = english(tag),
            word = word,
            precedingWords = emptyList(),
            followingWords = emptyList(),
            maxSuggestionCount = 3,
            allowPossiblyOffensive = false,
            isPrivateSession = true,
        )
        return result.suggestionsInfo.getSuggestionAt(0)
    }

    private suspend fun suggestedFix(tag: String, word: String): String {
        val content = EditorContent(
            text = word,
            offset = 0,
            localSelection = EditorRange.cursor(word.length),
            localComposing = EditorRange.Unspecified,
            localCurrentWord = EditorRange(0, word.length),
        )
        return provider.suggest(
            subtype = english(tag),
            content = content,
            maxCandidateCount = 8,
            allowPossiblyOffensive = false,
            isPrivateSession = true,
        ).first { it.isEligibleForAutoCommit }.text.toString()
    }
}
