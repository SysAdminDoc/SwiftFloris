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

import androidx.annotation.StringRes
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.nlp.advanced.AdvancedPredictionProvider
import dev.patrickgold.florisboard.ime.nlp.advanced.AdvancedSpellingProvider
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider

internal enum class NlpProviderCapability {
    Spelling,
    Suggestions,
}

internal enum class NlpProviderAvailability {
    /** Offered as a keyboard language's suggestion engine, which serves its spelling and its suggestions. */
    Selectable,

    /** Registered and resolved by ID, but never offered, because it covers only one of the two. */
    NotSelectable,
}

internal data class NlpProviderMetadata(
    val id: String,
    @StringRes val labelRes: Int,
    val capabilities: Set<NlpProviderCapability>,
    val availability: NlpProviderAvailability,
) {
    init {
        require(capabilities.isNotEmpty()) { "$id has no capabilities" }
    }

    /** Worded from [capabilities], so the summary can't disagree with what the provider does. */
    @get:StringRes
    val capabilitySummaryRes: Int
        get() {
            val spelling = NlpProviderCapability.Spelling in capabilities
            val suggestions = NlpProviderCapability.Suggestions in capabilities
            return when {
                spelling && suggestions -> R.string.settings__localization__nlp_provider_capability__spelling_and_suggestions
                spelling -> R.string.settings__localization__nlp_provider_capability__spelling_only
                else -> R.string.settings__localization__nlp_provider_capability__suggestions_only
            }
        }
}

/**
 * What each provider [NlpProviderFactory] registers looks like in settings. Screens read names and states from
 * here instead of naming providers themselves, and NlpProviderCatalogTest fails when a registered provider has
 * no entry or an entry's capabilities don't match the provider.
 */
internal object NlpProviderCatalog {
    val entries: List<NlpProviderMetadata> = listOf(
        NlpProviderMetadata(
            id = LatinLanguageProvider.ProviderId,
            labelRes = R.string.settings__localization__nlp_provider__latin,
            capabilities = setOf(NlpProviderCapability.Spelling, NlpProviderCapability.Suggestions),
            availability = NlpProviderAvailability.Selectable,
        ),
        NlpProviderMetadata(
            id = HanShapeBasedLanguageProvider.ProviderId,
            labelRes = R.string.settings__localization__nlp_provider__han_shape,
            capabilities = setOf(NlpProviderCapability.Spelling, NlpProviderCapability.Suggestions),
            availability = NlpProviderAvailability.Selectable,
        ),
        NlpProviderMetadata(
            id = AdvancedSpellingProvider.ProviderId,
            labelRes = R.string.settings__localization__nlp_provider__advanced_spelling,
            capabilities = setOf(NlpProviderCapability.Spelling),
            availability = NlpProviderAvailability.NotSelectable,
        ),
        NlpProviderMetadata(
            id = AdvancedPredictionProvider.ProviderId,
            labelRes = R.string.settings__localization__nlp_provider__advanced_prediction,
            capabilities = setOf(NlpProviderCapability.Suggestions),
            availability = NlpProviderAvailability.NotSelectable,
        ),
    )

    private val entriesById = entries.associateBy { it.id }

    val selectable: List<NlpProviderMetadata> = entries.filter { it.availability == NlpProviderAvailability.Selectable }

    fun metadataFor(providerId: String): NlpProviderMetadata? = entriesById[providerId]
}
