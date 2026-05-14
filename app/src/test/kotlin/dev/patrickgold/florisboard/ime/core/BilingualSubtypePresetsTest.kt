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

import dev.patrickgold.florisboard.ime.keyboard.extCoreComposer
import dev.patrickgold.florisboard.ime.keyboard.extCoreCurrencySet
import dev.patrickgold.florisboard.ime.keyboard.extCoreLayout
import dev.patrickgold.florisboard.ime.keyboard.extCorePopupMapping
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class BilingualSubtypePresetsTest : FunSpec({
    test("canonical bilingual presets cover the SwiftKey-style English pairs") {
        val presets = listOf(
            preset(FlorisLocale.from("en", "US")),
            preset(FlorisLocale.from("es", "ES")),
            preset(FlorisLocale.from("fr", "FR")),
            preset(FlorisLocale.from("de", "DE")),
        )

        val bilingualPresets = BilingualSubtypePresets.canonicalFrom(presets)

        bilingualPresets.map { it.locale.languageTag() to it.secondaryLocales.single().languageTag() }
            .shouldContainExactly(
                "en-US" to "es-ES",
                "en-US" to "fr-FR",
                "en-US" to "de-DE",
            )
    }

    test("bilingual presets preserve the English keyboard layout while adding a secondary locale") {
        val englishPreset = preset(
            locale = FlorisLocale.from("en", "US"),
            charactersLayout = "qwerty",
        )
        val spanishPreset = preset(
            locale = FlorisLocale.from("es", "ES"),
            charactersLayout = "spanish",
        )

        val bilingualPreset = BilingualSubtypePresets.canonicalFrom(listOf(englishPreset, spanishPreset)).first()

        bilingualPreset.preferred.characters shouldBe extCoreLayout("qwerty")
        bilingualPreset.secondaryLocales shouldBe listOf(FlorisLocale.from("es", "ES"))
    }

    test("subtype presets persist secondary locales into the subtype") {
        val secondaryLocales = listOf(FlorisLocale.from("es", "ES"))
        val preset = preset(FlorisLocale.from("en", "US")).copy(secondaryLocales = secondaryLocales)

        val subtype = preset.toSubtype()

        subtype.primaryLocale shouldBe FlorisLocale.from("en", "US")
        subtype.secondaryLocales shouldBe secondaryLocales
        Subtype.DEFAULT.equalsExcludingId(subtype) shouldBe false
    }

    test("canonical bilingual presets require an English primary preset") {
        val presets = listOf(
            preset(FlorisLocale.from("es", "ES")),
            preset(FlorisLocale.from("fr", "FR")),
        )

        BilingualSubtypePresets.canonicalFrom(presets) shouldBe emptyList()
    }
})

private fun preset(
    locale: FlorisLocale,
    charactersLayout: String = "qwerty",
): SubtypePreset {
    return SubtypePreset(
        locale = locale,
        composer = extCoreComposer("appender"),
        currencySet = extCoreCurrencySet("dollar"),
        popupMapping = extCorePopupMapping(locale.language),
        preferred = SubtypeLayoutMap(characters = extCoreLayout(charactersLayout)),
    )
}
