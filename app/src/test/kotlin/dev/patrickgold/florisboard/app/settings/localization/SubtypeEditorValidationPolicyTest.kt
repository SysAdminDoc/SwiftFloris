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
import dev.patrickgold.florisboard.ime.core.SubtypeLayoutMap
import dev.patrickgold.florisboard.ime.core.SubtypeNlpProviderMap
import dev.patrickgold.florisboard.ime.keyboard.extCoreComposer
import dev.patrickgold.florisboard.ime.keyboard.extCoreCurrencySet
import dev.patrickgold.florisboard.ime.keyboard.extCoreLayout
import dev.patrickgold.florisboard.ime.keyboard.extCorePopupMapping
import dev.patrickgold.florisboard.ime.keyboard.extCorePunctuationRule
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class SubtypeEditorValidationPolicyTest : FunSpec({
    test("default add draft requires the fields still represented by select placeholders") {
        val validation = SubtypeEditorValidationPolicy.validate(
            SubtypeEditorValidationPolicy.draftFrom(null),
        )

        validation.missingFields shouldContainExactlyInAnyOrder listOf(
            SubtypeEditorField.PrimaryLocale,
            SubtypeEditorField.Composer,
            SubtypeEditorField.CurrencySet,
            SubtypeEditorField.PopupMapping,
            SubtypeEditorField.CharactersLayout,
            SubtypeEditorField.SymbolsLayout,
            SubtypeEditorField.Symbols2Layout,
            SubtypeEditorField.NumericLayout,
            SubtypeEditorField.NumericAdvancedLayout,
            SubtypeEditorField.NumericRowLayout,
            SubtypeEditorField.PhoneLayout,
            SubtypeEditorField.Phone2Layout,
        )
        validation.isValid shouldBe false
    }

    test("complete subtype draft validates and builds without dropping secondary locales") {
        val draft = validDraft().copy(
            secondaryLocales = listOf(FlorisLocale.from("es", "ES"), FlorisLocale.from("fr", "FR")),
        )

        SubtypeEditorValidationPolicy.validate(draft).isValid shouldBe true
        val subtype = SubtypeEditorValidationPolicy.toSubtype(draft).getOrThrow()

        subtype.id shouldBe draft.id
        subtype.primaryLocale shouldBe draft.primaryLocale
        subtype.secondaryLocales shouldBe draft.secondaryLocales
        subtype.nlpProviders shouldBe draft.nlpProviders
        subtype.layoutMap shouldBe draft.layoutMap
    }

    test("select placeholders are rejected for every required component field") {
        val validation = SubtypeEditorValidationPolicy.validate(
            validDraft().copy(
                nlpProviders = SubtypeNlpProviderMap(
                    spelling = SelectNlpProviderId,
                    suggestion = SelectNlpProviderId,
                ),
                composer = SelectComponentName,
                currencySet = SelectComponentName,
                punctuationRule = SelectComponentName,
                popupMapping = SelectComponentName,
                layoutMap = SelectLayoutMap,
            ),
        )

        validation.missingFields shouldContainExactlyInAnyOrder listOf(
            SubtypeEditorField.SpellingProvider,
            SubtypeEditorField.SuggestionProvider,
            SubtypeEditorField.Composer,
            SubtypeEditorField.CurrencySet,
            SubtypeEditorField.PunctuationRule,
            SubtypeEditorField.PopupMapping,
            SubtypeEditorField.CharactersLayout,
            SubtypeEditorField.SymbolsLayout,
            SubtypeEditorField.Symbols2Layout,
            SubtypeEditorField.NumericLayout,
            SubtypeEditorField.NumericAdvancedLayout,
            SubtypeEditorField.NumericRowLayout,
            SubtypeEditorField.PhoneLayout,
            SubtypeEditorField.Phone2Layout,
        )
        shouldThrow<IllegalStateException> {
            SubtypeEditorValidationPolicy.toSubtype(
                validDraft().copy(primaryLocale = SelectLocale),
            ).getOrThrow()
        }
    }

    test("draftFrom existing subtype preserves all editable fields") {
        val subtype = Subtype.DEFAULT.copy(
            id = 42,
            primaryLocale = FlorisLocale.from("de", "DE"),
            secondaryLocales = listOf(FlorisLocale.from("en", "US")),
            nlpProviders = SubtypeNlpProviderMap(
                spelling = HanShapeBasedLanguageProvider.ProviderId,
                suggestion = HanShapeBasedLanguageProvider.ProviderId,
            ),
        )

        val draft = SubtypeEditorValidationPolicy.draftFrom(subtype)

        draft.id shouldBe subtype.id
        draft.primaryLocale shouldBe subtype.primaryLocale
        draft.secondaryLocales shouldBe subtype.secondaryLocales
        draft.nlpProviders shouldBe subtype.nlpProviders
        draft.composer shouldBe subtype.composer
        draft.currencySet shouldBe subtype.currencySet
        draft.punctuationRule shouldBe subtype.punctuationRule
        draft.popupMapping shouldBe subtype.popupMapping
        draft.layoutMap shouldBe subtype.layoutMap
    }
})

private fun validDraft(): SubtypeEditorDraft {
    return SubtypeEditorDraft(
        id = 7,
        primaryLocale = FlorisLocale.from("en", "US"),
        secondaryLocales = emptyList(),
        nlpProviders = SubtypeNlpProviderMap(
            spelling = LatinLanguageProvider.ProviderId,
            suggestion = LatinLanguageProvider.ProviderId,
        ),
        composer = extCoreComposer("appender"),
        currencySet = extCoreCurrencySet("dollar"),
        punctuationRule = extCorePunctuationRule("default"),
        popupMapping = extCorePopupMapping("en"),
        layoutMap = SubtypeLayoutMap(
            characters = extCoreLayout("qwerty"),
            symbols = extCoreLayout("western"),
            symbols2 = extCoreLayout("western"),
            numeric = extCoreLayout("western_arabic"),
            numericAdvanced = extCoreLayout("western_arabic"),
            numericRow = extCoreLayout("western_arabic"),
            phone = extCoreLayout("telpad"),
            phone2 = extCoreLayout("telpad"),
        ),
    )
}
