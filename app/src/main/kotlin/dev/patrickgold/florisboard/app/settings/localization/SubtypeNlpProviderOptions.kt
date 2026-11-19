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

import dev.patrickgold.florisboard.ime.nlp.NlpProviderCatalog
import dev.patrickgold.florisboard.ime.nlp.NlpProviderMetadata

/** One entry in the subtype editor's suggestion engine list. */
internal sealed interface SubtypeNlpProviderOption {
    val id: String

    data object Placeholder : SubtypeNlpProviderOption {
        override val id: String = SelectNlpProviderId
    }

    data class Provider(val metadata: NlpProviderMetadata) : SubtypeNlpProviderOption {
        override val id: String get() = metadata.id
    }

    /**
     * The saved engine when no keyboard language can pick it here: an ID this version doesn't know, or a
     * registered provider that isn't selectable. [metadata] is null for an unknown ID.
     */
    data class Unavailable(override val id: String, val metadata: NlpProviderMetadata?) : SubtypeNlpProviderOption
}

internal data class SubtypeNlpProviderOptions(
    val options: List<SubtypeNlpProviderOption>,
    val selectedIndex: Int,
) {
    val selected: SubtypeNlpProviderOption get() = options[selectedIndex]

    companion object {
        /** The placeholder, every selectable provider, and the saved engine last when it isn't one of them. */
        fun forSavedProvider(savedProviderId: String): SubtypeNlpProviderOptions {
            val selectable = NlpProviderCatalog.selectable.map { SubtypeNlpProviderOption.Provider(it) }
            val unavailable = if (savedProviderId == SelectNlpProviderId || selectable.any { it.id == savedProviderId }) {
                null
            } else {
                SubtypeNlpProviderOption.Unavailable(savedProviderId, NlpProviderCatalog.metadataFor(savedProviderId))
            }
            val options = listOf(SubtypeNlpProviderOption.Placeholder) + selectable + listOfNotNull(unavailable)
            return SubtypeNlpProviderOptions(
                options = options,
                selectedIndex = options.indexOfFirst { it.id == savedProviderId }.coerceAtLeast(0),
            )
        }
    }
}
