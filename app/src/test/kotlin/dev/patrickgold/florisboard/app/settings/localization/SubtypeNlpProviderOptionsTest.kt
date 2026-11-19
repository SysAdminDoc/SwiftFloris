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

package dev.patrickgold.florisboard.app.settings.localization

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.nlp.NlpProviderCatalog
import dev.patrickgold.florisboard.ime.nlp.advanced.AdvancedSpellingProvider
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SubtypeNlpProviderOptionsTest : FunSpec({
    val selectableIds = NlpProviderCatalog.selectable.map { it.id }

    test("the list is the placeholder and every selectable provider, from the catalog") {
        val options = SubtypeNlpProviderOptions.forSavedProvider(SelectNlpProviderId)

        options.options.map { it.id } shouldContainExactly listOf(SelectNlpProviderId) + selectableIds
        options.selected shouldBe SubtypeNlpProviderOption.Placeholder
    }

    test("a saved selectable provider is selected and adds nothing") {
        val options = SubtypeNlpProviderOptions.forSavedProvider(HanShapeBasedLanguageProvider.ProviderId)

        options.options.map { it.id } shouldContainExactly listOf(SelectNlpProviderId) + selectableIds
        val selected = options.selected.shouldBeInstanceOf<SubtypeNlpProviderOption.Provider>()
        selected.metadata shouldBe NlpProviderCatalog.metadataFor(HanShapeBasedLanguageProvider.ProviderId)
    }

    test("the default subtype's engine is a selectable provider") {
        SubtypeNlpProviderOptions.forSavedProvider(Subtype.DEFAULT.nlpProviders.suggestion)
            .selected.shouldBeInstanceOf<SubtypeNlpProviderOption.Provider>()
    }

    test("an unknown saved ID shows as unavailable, last and selected, instead of the placeholder") {
        val options = SubtypeNlpProviderOptions.forSavedProvider("com.example.nlp.providers.gone")

        val selected = options.selected.shouldBeInstanceOf<SubtypeNlpProviderOption.Unavailable>()
        selected.id shouldBe "com.example.nlp.providers.gone"
        selected.metadata.shouldBeNull()
        options.selectedIndex shouldBe options.options.lastIndex
    }

    test("a pair whose spelling engine is gone shows that engine as unavailable") {
        val options = SubtypeNlpProviderOptions.forSavedProviders(
            suggestionId = LatinLanguageProvider.ProviderId,
            spellingId = "com.example.nlp.providers.gone",
        )

        options.selected.shouldBeInstanceOf<SubtypeNlpProviderOption.Unavailable>().id shouldBe
            "com.example.nlp.providers.gone"
    }

    test("a pair whose spelling engine can't be picked shows it as unavailable under its own name") {
        val options = SubtypeNlpProviderOptions.forSavedProviders(
            suggestionId = LatinLanguageProvider.ProviderId,
            spellingId = AdvancedSpellingProvider.ProviderId,
        )

        options.selected.shouldBeInstanceOf<SubtypeNlpProviderOption.Unavailable>()
            .metadata.shouldNotBeNull().id shouldBe AdvancedSpellingProvider.ProviderId
    }

    test("a pair whose spelling engine was never picked shows the placeholder, not the suggestion engine") {
        val options = SubtypeNlpProviderOptions.forSavedProviders(
            suggestionId = LatinLanguageProvider.ProviderId,
            spellingId = SelectNlpProviderId,
        )

        options.selected shouldBe SubtypeNlpProviderOption.Placeholder
    }

    test("a matching pair of selectable engines shows that engine") {
        val options = SubtypeNlpProviderOptions.forSavedProviders(
            suggestionId = LatinLanguageProvider.ProviderId,
            spellingId = LatinLanguageProvider.ProviderId,
        )

        options.selected.shouldBeInstanceOf<SubtypeNlpProviderOption.Provider>().id shouldBe
            LatinLanguageProvider.ProviderId
    }

    test("a registered provider no language can pick shows as unavailable under its own name") {
        val options = SubtypeNlpProviderOptions.forSavedProvider(AdvancedSpellingProvider.ProviderId)

        val selected = options.selected.shouldBeInstanceOf<SubtypeNlpProviderOption.Unavailable>()
        selected.metadata.shouldNotBeNull().id shouldBe AdvancedSpellingProvider.ProviderId
        selectableIds shouldNotContainAnyOf listOf(AdvancedSpellingProvider.ProviderId)
    }
})
