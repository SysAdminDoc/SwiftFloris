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

package dev.patrickgold.florisboard.ime.core

import dev.patrickgold.florisboard.lib.FlorisLocale

internal object BilingualSubtypePresets {
    private val PrimaryLocale = FlorisLocale.from("en", "US")
    private val SecondaryLocales = listOf(
        FlorisLocale.from("es", "ES"),
        FlorisLocale.from("fr", "FR"),
        FlorisLocale.from("de", "DE"),
    )

    fun canonicalFrom(subtypePresets: List<SubtypePreset>): List<SubtypePreset> {
        val primaryPreset = subtypePresets.findBestPresetFor(PrimaryLocale) ?: return emptyList()
        return SecondaryLocales.map { secondaryLocale ->
            primaryPreset.copy(
                secondaryLocales = listOf(
                    subtypePresets.findBestPresetFor(secondaryLocale)?.locale ?: secondaryLocale,
                ),
            )
        }
    }

    private fun List<SubtypePreset>.findBestPresetFor(locale: FlorisLocale): SubtypePreset? {
        return find { it.locale == locale } ?: find { it.locale.language == locale.language }
    }
}
