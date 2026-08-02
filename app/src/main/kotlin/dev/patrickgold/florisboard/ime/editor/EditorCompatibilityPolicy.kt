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

import dev.patrickgold.florisboard.ime.text.key.KeyVariation

/**
 * The editor contract shared by input lifecycle, composing, and suggestion code.
 *
 * Android's [android.view.inputmethod.EditorInfo] is intentionally lossy once it
 * reaches the rest of the IME: callers previously re-derived these decisions in
 * separate places. Keeping the host's input class, variation, and text flags in
 * one snapshot prevents a web password, numeric PIN, or no-suggestions editor
 * from accidentally inheriting the plain-text candidate pipeline.
 */
internal object EditorCompatibilityPolicy {
    data class Snapshot(
        val keyVariation: KeyVariation,
        val allowsImeSuggestions: Boolean,
        val allowsComposing: Boolean,
    )

    fun snapshot(editorInfo: FlorisEditorInfo): Snapshot {
        val attributes = editorInfo.inputAttributes
        val keyVariation = when (attributes.type) {
            InputAttributes.Type.NUMBER -> {
                if (attributes.variation == InputAttributes.Variation.PASSWORD) {
                    KeyVariation.PASSWORD
                } else {
                    KeyVariation.NORMAL
                }
            }
            InputAttributes.Type.TEXT -> when (attributes.variation) {
                InputAttributes.Variation.EMAIL_ADDRESS,
                InputAttributes.Variation.WEB_EMAIL_ADDRESS,
                -> KeyVariation.EMAIL_ADDRESS
                InputAttributes.Variation.PASSWORD,
                InputAttributes.Variation.VISIBLE_PASSWORD,
                InputAttributes.Variation.WEB_PASSWORD,
                -> KeyVariation.PASSWORD
                InputAttributes.Variation.URI -> KeyVariation.URI
                else -> KeyVariation.NORMAL
            }
            else -> KeyVariation.NORMAL
        }

        // Only text editors can provide a composing/suggestion contract. A
        // password field is a hard stop, while AUTO_COMPLETE and NO_SUGGESTIONS
        // explicitly assign completion/suggestion responsibility to the host.
        val allowsImeSuggestions = attributes.type == InputAttributes.Type.TEXT &&
            keyVariation != KeyVariation.PASSWORD &&
            !attributes.flagTextAutoComplete &&
            !attributes.flagTextNoSuggestions

        return Snapshot(
            keyVariation = keyVariation,
            allowsImeSuggestions = allowsImeSuggestions,
            allowsComposing = allowsImeSuggestions,
        )
    }
}
