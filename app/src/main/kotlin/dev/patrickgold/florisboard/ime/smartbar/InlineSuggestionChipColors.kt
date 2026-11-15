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

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.snygg.SnyggAnnotationRule
import org.florisboard.lib.snygg.SnyggElementRule
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.SnyggSinglePropertySet
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.value.SnyggDefinedVarValue
import org.florisboard.lib.snygg.value.SnyggStaticColorValue
import org.florisboard.lib.snygg.value.SnyggValue

internal data class InlineSuggestionChipColors(val background: Color, val foreground: Color)

/**
 * Picks the colors the autofill service draws inline suggestion chips with.
 *
 * Android asks the keyboard for its inline request before the keyboard UI has
 * drawn for the first time, so the Compose theme query that fills
 * [CachedInlineSuggestionsChipStyleSet] hasn't run yet in a fresh process. The
 * colors then come straight from the active stylesheet, so the request is
 * still made and the service shows chips instead of its own popup.
 */
internal object InlineSuggestionChipColorPolicy {
    // The chip colors of floris_night and floris_day, for a theme that leaves
    // the chip unstyled or styles it with dynamic colors.
    private val NightFallback = InlineSuggestionChipColors(Color(0xFF424242), Color(0xFFFFFFFF))
    private val DayFallback = InlineSuggestionChipColors(Color(0xFFFFFFFF), Color(0xFF000000))

    fun resolve(
        cached: SnyggSinglePropertySet?,
        stylesheet: SnyggStylesheet,
        isNightTheme: Boolean,
    ): InlineSuggestionChipColors {
        val fallback = if (isNightTheme) NightFallback else DayFallback
        if (cached != null) {
            return InlineSuggestionChipColors(
                background = cached.background(default = fallback.background),
                foreground = cached.foreground(default = fallback.foreground),
            )
        }
        val defines = stylesheet.rules[SnyggAnnotationRule.Defines] as? SnyggSinglePropertySet
        val chip = stylesheet.rules.entries.firstOrNull { (rule, _) ->
            rule is SnyggElementRule &&
                rule.elementName == FlorisImeUi.InlineAutofillChip.elementName &&
                rule.attributes.isEmpty() &&
                rule.selector == SnyggSelector.NONE
        }?.value as? SnyggSinglePropertySet

        fun staticColor(value: SnyggValue?): Color? = when (value) {
            is SnyggStaticColorValue -> value.color
            is SnyggDefinedVarValue -> (defines?.properties?.get(value.key) as? SnyggStaticColorValue)?.color
            else -> null
        }

        return InlineSuggestionChipColors(
            background = staticColor(chip?.background) ?: fallback.background,
            foreground = staticColor(chip?.foreground) ?: fallback.foreground,
        )
    }
}
