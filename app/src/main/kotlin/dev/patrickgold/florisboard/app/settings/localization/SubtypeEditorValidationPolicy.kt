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
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import kotlinx.serialization.Serializable

internal val SelectComponentName = ExtensionComponentName("00", "00")
internal val SelectNlpProviderId = SelectComponentName.toString()
internal val SelectLayoutMap = SubtypeLayoutMap(
    characters = SelectComponentName,
    symbols = SelectComponentName,
    symbols2 = SelectComponentName,
    numeric = SelectComponentName,
    numericAdvanced = SelectComponentName,
    numericRow = SelectComponentName,
    phone = SelectComponentName,
    phone2 = SelectComponentName,
)
internal val SelectLocale = FlorisLocale.from("00", "00")
internal val SelectListKeys = listOf(SelectComponentName)

@Serializable
internal data class SubtypeEditorDraft(
    val id: Long,
    val primaryLocale: FlorisLocale,
    val secondaryLocales: List<FlorisLocale>,
    val nlpProviders: SubtypeNlpProviderMap,
    val composer: ExtensionComponentName,
    val currencySet: ExtensionComponentName,
    val punctuationRule: ExtensionComponentName,
    val popupMapping: ExtensionComponentName,
    val layoutMap: SubtypeLayoutMap,
)

internal enum class SubtypeEditorField {
    PrimaryLocale,
    SpellingProvider,
    SuggestionProvider,
    Composer,
    CurrencySet,
    PunctuationRule,
    PopupMapping,
    CharactersLayout,
    SymbolsLayout,
    Symbols2Layout,
    NumericLayout,
    NumericAdvancedLayout,
    NumericRowLayout,
    PhoneLayout,
    Phone2Layout,
}

internal data class SubtypeEditorValidation(
    val missingFields: Set<SubtypeEditorField>,
) {
    val isValid: Boolean
        get() = missingFields.isEmpty()
}

internal object SubtypeEditorValidationPolicy {
    fun draftFrom(subtype: Subtype?): SubtypeEditorDraft {
        return SubtypeEditorDraft(
            id = subtype?.id ?: -1,
            primaryLocale = subtype?.primaryLocale ?: SelectLocale,
            secondaryLocales = subtype?.secondaryLocales ?: emptyList(),
            nlpProviders = subtype?.nlpProviders ?: Subtype.DEFAULT.nlpProviders,
            composer = subtype?.composer ?: SelectComponentName,
            currencySet = subtype?.currencySet ?: SelectComponentName,
            punctuationRule = subtype?.punctuationRule ?: Subtype.DEFAULT.punctuationRule,
            popupMapping = subtype?.popupMapping ?: SelectComponentName,
            layoutMap = subtype?.layoutMap ?: SelectLayoutMap,
        )
    }

    fun validate(draft: SubtypeEditorDraft): SubtypeEditorValidation {
        val missingFields = buildSet {
            if (draft.primaryLocale == SelectLocale) add(SubtypeEditorField.PrimaryLocale)
            if (draft.nlpProviders.spelling == SelectNlpProviderId) add(SubtypeEditorField.SpellingProvider)
            if (draft.nlpProviders.suggestion == SelectNlpProviderId) add(SubtypeEditorField.SuggestionProvider)
            if (draft.composer == SelectComponentName) add(SubtypeEditorField.Composer)
            if (draft.currencySet == SelectComponentName) add(SubtypeEditorField.CurrencySet)
            if (draft.punctuationRule == SelectComponentName) add(SubtypeEditorField.PunctuationRule)
            if (draft.popupMapping == SelectComponentName) add(SubtypeEditorField.PopupMapping)
            if (draft.layoutMap.characters == SelectComponentName) add(SubtypeEditorField.CharactersLayout)
            if (draft.layoutMap.symbols == SelectComponentName) add(SubtypeEditorField.SymbolsLayout)
            if (draft.layoutMap.symbols2 == SelectComponentName) add(SubtypeEditorField.Symbols2Layout)
            if (draft.layoutMap.numeric == SelectComponentName) add(SubtypeEditorField.NumericLayout)
            if (draft.layoutMap.numericAdvanced == SelectComponentName) add(SubtypeEditorField.NumericAdvancedLayout)
            if (draft.layoutMap.numericRow == SelectComponentName) add(SubtypeEditorField.NumericRowLayout)
            if (draft.layoutMap.phone == SelectComponentName) add(SubtypeEditorField.PhoneLayout)
            if (draft.layoutMap.phone2 == SelectComponentName) add(SubtypeEditorField.Phone2Layout)
        }
        return SubtypeEditorValidation(missingFields = missingFields)
    }

    fun toSubtype(draft: SubtypeEditorDraft): Result<Subtype> = runCatching {
        check(validate(draft).isValid)
        Subtype(
            draft.id,
            draft.primaryLocale,
            draft.secondaryLocales,
            draft.nlpProviders,
            draft.composer,
            draft.currencySet,
            draft.punctuationRule,
            draft.popupMapping,
            draft.layoutMap,
        )
    }
}
