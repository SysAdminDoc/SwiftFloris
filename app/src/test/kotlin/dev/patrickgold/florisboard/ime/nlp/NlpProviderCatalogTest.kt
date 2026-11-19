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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Every provider the keyboard registers needs a name and a state in settings, and they have to be true. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NlpProviderCatalogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val providers = NlpProviderFactory(context).createProviders()

    @Test
    fun everyRegisteredProviderHasExactlyOneEntry() {
        NlpProviderCatalog.entries.map { it.id }.shouldBeUnique()
        NlpProviderCatalog.entries.map { it.id } shouldContainExactlyInAnyOrder providers.map { it.providerId }
    }

    @Test
    fun capabilitiesMatchWhatEachProviderImplements() {
        for (provider in providers) {
            val metadata = NlpProviderCatalog.metadataFor(provider.providerId).shouldNotBeNull()
            val implemented = buildSet {
                if (provider is SpellingProvider) add(NlpProviderCapability.Spelling)
                if (provider is SuggestionProvider) add(NlpProviderCapability.Suggestions)
            }
            withClue(provider.providerId) { metadata.capabilities shouldBe implemented }
        }
    }

    @Test
    fun aSelectableProviderServesSpellingAndSuggestions() {
        // The subtype editor puts the one engine it picks in both slots.
        NlpProviderCatalog.selectable.shouldNotBeEmpty()
        for (metadata in NlpProviderCatalog.selectable) {
            withClue(metadata.id) {
                metadata.capabilities shouldBe setOf(NlpProviderCapability.Spelling, NlpProviderCapability.Suggestions)
            }
        }
    }

    @Test
    fun everyLabelIsItsOwnLocalizedString() {
        val labels = NlpProviderCatalog.entries.map { context.getString(it.labelRes) }
        labels.forEach { it.shouldNotBeBlank() }
        labels.shouldBeUnique()
        NlpProviderCatalog.entries.forEach { context.getString(it.capabilitySummaryRes).shouldNotBeBlank() }
    }

    @Test
    fun everyEngineABundledLanguageNamesIsSelectable() {
        val presets = listOf(
            File("src/main/assets/ime/keyboard/org.florisboard.localization/extension.json"),
            File("app/src/main/assets/ime/keyboard/org.florisboard.localization/extension.json"),
        ).first { it.exists() }.readText()
        val named = Regex("\"(org\\.[a-z]+\\.nlp\\.providers\\.[a-z_.]+)\"").findAll(presets).map { it.groupValues[1] }.toSet()

        named.shouldNotBeEmpty()
        val selectableIds = NlpProviderCatalog.selectable.map { it.id }
        for (id in named) {
            withClue(id) { (id in selectableIds) shouldBe true }
        }
    }
}
