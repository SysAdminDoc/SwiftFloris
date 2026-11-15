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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

private val englishUs = subtype(1L, "en", "US")
private val germanDe = subtype(2L, "de", "DE")
private val englishGb = subtype(3L, "en", "GB")
private val frenchFr = subtype(4L, "fr", "FR")
private val hebrew = subtype(5L, "iw", "IL")

private fun subtype(id: Long, language: String, country: String) = Subtype.DEFAULT.copy(
    id = id,
    primaryLocale = FlorisLocale.from(language, country),
)

private fun resolve(
    subtypes: List<Subtype> = listOf(englishUs, germanDe, frenchFr),
    active: Subtype = englishUs,
    user: Subtype = englishUs,
    activeFromHint: Boolean = false,
    remembered: Subtype? = null,
    pickedByHand: Boolean = false,
    hintsEnabled: Boolean = true,
    editor: List<String> = emptyList(),
    app: List<String>? = emptyList(),
): LocaleHints.Resolution? = LocaleHints.resolve(
    subtypes = subtypes,
    activeSubtypeId = active.id,
    userSubtypeId = user.id,
    activeFromHint = activeFromHint,
    rememberedSubtypeId = remembered?.id,
    pickedByHand = pickedByHand,
    hintsEnabled = hintsEnabled,
    editorLocales = editor.map(Locale::forLanguageTag),
    appLocales = { checkNotNull(app) { "app locales read" }.map(Locale::forLanguageTag) },
)

class LocaleHintsTest : FunSpec({
    test("a field's language hint switches to the one keyboard set up for it") {
        resolve(editor = listOf("de-DE")) shouldBe LocaleHints.Resolution(germanDe, SubtypeManager.SubtypeSwitchSource.Hint)
        // A hint without a country, or for another country, still fits the only German keyboard.
        resolve(editor = listOf("de"))?.subtype shouldBe germanDe
        resolve(editor = listOf("de-AT"))?.subtype shouldBe germanDe
        // The first hint that fits any keyboard decides.
        resolve(editor = listOf("ja-JP", "fr-CA", "de-DE"))?.subtype shouldBe frenchFr
    }

    test("a hint that fits more than one keyboard, or none, changes nothing") {
        val both = listOf(englishUs, englishGb, germanDe)
        resolve(subtypes = both, active = germanDe, user = germanDe, editor = listOf("en"), app = null) shouldBe null
        resolve(subtypes = both, active = germanDe, user = germanDe, editor = listOf("en-AU"), app = null) shouldBe null
        // Naming the country picks between them.
        resolve(subtypes = both, active = germanDe, user = germanDe, editor = listOf("en-GB"))?.subtype shouldBe englishGb
        // Nothing set up for it: nothing is added or downloaded, the keyboard stays.
        resolve(editor = listOf("ja-JP")) shouldBe null
        resolve(editor = listOf("und")) shouldBe null
    }

    test("a keyboard that already fits the hint stays, even when another one fits too") {
        val twoGerman = listOf(englishUs, germanDe, germanDe.copy(id = 9L))
        resolve(subtypes = twoGerman, active = germanDe, user = germanDe, editor = listOf("de"), app = null) shouldBe null
        resolve(active = germanDe, user = germanDe, editor = listOf("de-DE")) shouldBe null
        // The user's own keyboard fitting too doesn't move them off the one that fits already.
        val secondGerman = germanDe.copy(id = 9L)
        resolve(subtypes = twoGerman, active = secondGerman, user = germanDe, activeFromHint = true, editor = listOf("de"), app = null) shouldBe null
    }

    test("old Java language codes still match") {
        resolve(subtypes = listOf(englishUs, hebrew), editor = listOf("he-IL"))?.subtype shouldBe hebrew
        // Newer JVMs already turn these into the new codes inside Locale, so they're checked as strings.
        LocaleHints.normalizedLanguage("iw") shouldBe "he"
        LocaleHints.normalizedLanguage("IN") shouldBe "id"
        LocaleHints.normalizedLanguage("ji") shouldBe "yi"
        LocaleHints.normalizedLanguage("de") shouldBe "de"
    }

    test("the app's own language counts only when the field's hints fit no keyboard") {
        resolve(editor = emptyList(), app = listOf("fr-FR"))?.subtype shouldBe frenchFr
        resolve(editor = listOf("ja-JP"), app = listOf("fr-FR"))?.subtype shouldBe frenchFr
        // A field hint that fits wins, and the app's language isn't even asked for.
        resolve(editor = listOf("de-DE"), app = null)?.subtype shouldBe germanDe
        // An ambiguous field hint stops there instead of falling through to the app's language.
        resolve(
            subtypes = listOf(englishUs, englishGb, frenchFr), active = frenchFr, user = frenchFr,
            editor = listOf("en"), app = null,
        ) shouldBe null
    }

    test("a language remembered for the app, or picked by hand in it, comes before any hint") {
        resolve(remembered = germanDe, editor = listOf("fr-FR"), app = null) shouldBe
            LocaleHints.Resolution(germanDe, SubtypeManager.SubtypeSwitchSource.Restore)
        resolve(active = germanDe, remembered = germanDe, editor = listOf("fr-FR"), app = null) shouldBe null
        resolve(pickedByHand = true, editor = listOf("fr-FR"), app = null) shouldBe null
        resolve(pickedByHand = true, active = frenchFr, activeFromHint = true, app = null) shouldBe null
        // The pick is newer than what's remembered for the app, which is saved after it.
        resolve(pickedByHand = true, active = frenchFr, remembered = germanDe, app = null) shouldBe null
    }

    test("turning hints off ignores them") {
        resolve(hintsEnabled = false, editor = listOf("de-DE"), app = null) shouldBe null
        resolve(hintsEnabled = false, app = null) shouldBe null
    }

    test("a field with no usable hint goes back to the user's own language after a hinted one") {
        resolve(active = germanDe, user = englishUs, activeFromHint = true, editor = listOf("ja-JP")) shouldBe
            LocaleHints.Resolution(englishUs, SubtypeManager.SubtypeSwitchSource.System)
        resolve(active = germanDe, user = englishUs, activeFromHint = true, hintsEnabled = false, app = null)?.subtype shouldBe englishUs
        // A keyboard the user picked themselves stays.
        resolve(active = germanDe, user = germanDe, activeFromHint = false, editor = listOf("ja-JP")) shouldBe null
        // A new hint that fits several keyboards goes back to the user's own when it's one of them,
        resolve(
            subtypes = listOf(englishUs, englishGb, germanDe), active = germanDe, user = englishUs,
            activeFromHint = true, editor = listOf("en"), app = null,
        ) shouldBe LocaleHints.Resolution(englishUs, SubtypeManager.SubtypeSwitchSource.System)
        // and counts as no hint otherwise, so an English field doesn't keep an earlier app's German.
        resolve(
            subtypes = listOf(englishUs, englishGb, germanDe, frenchFr), active = germanDe, user = frenchFr,
            activeFromHint = true, editor = listOf("en"), app = null,
        ) shouldBe LocaleHints.Resolution(frenchFr, SubtypeManager.SubtypeSwitchSource.System)
    }

    test("British English hints find the bundled keyboard tagged en-UK") {
        val englishUk = subtype(6L, "en", "UK")
        resolve(subtypes = listOf(englishUs, englishUk, germanDe), active = germanDe, user = germanDe, editor = listOf("en-GB"))?.subtype shouldBe
            englishUk
        resolve(subtypes = listOf(englishUs, englishUk, germanDe), active = germanDe, user = germanDe, editor = listOf("en-US"))?.subtype shouldBe
            englishUs
    }

    test("a hint that names a script picks nothing, and nothing after it wins") {
        val serbian = subtype(7L, "sr", "RS")
        // A keyboard's locale never says which script it types, and the app's language isn't asked.
        resolve(subtypes = listOf(englishUs, serbian), editor = listOf("sr-Latn-RS"), app = null) shouldBe null
        // A Serbian field doesn't move to English because its script is unknown.
        resolve(
            subtypes = listOf(englishUs, serbian), active = serbian, user = serbian,
            editor = listOf("sr-Latn-RS", "en-US"), app = null,
        ) shouldBe null
        // After a hinted keyboard it goes back to the user's own, like any hint that picks nothing.
        resolve(
            subtypes = listOf(englishUs, serbian, germanDe), active = germanDe, user = englishUs,
            activeFromHint = true, editor = listOf("sr-Latn-RS"), app = null,
        ) shouldBe LocaleHints.Resolution(englishUs, SubtypeManager.SubtypeSwitchSource.System)
        // A Serbian keyboard an earlier hint brought up stays, whichever script this field names.
        resolve(
            subtypes = listOf(englishUs, serbian), active = serbian, user = englishUs,
            activeFromHint = true, editor = listOf("sr-Latn-RS"), app = null,
        ) shouldBe null
        resolve(
            subtypes = listOf(englishUs, serbian), active = serbian, user = englishUs,
            activeFromHint = true, app = listOf("sr-Latn"),
        ) shouldBe null
        // A script for a language no keyboard has is passed over like any other hint.
        resolve(subtypes = listOf(englishUs, germanDe), editor = listOf("sr-Latn-RS", "de-DE"), app = null)?.subtype shouldBe germanDe
        resolve(subtypes = listOf(englishUs, serbian), editor = listOf("sr-RS"), app = null)?.subtype shouldBe serbian
    }

    test("a remembered language that a hint already brought up counts as chosen") {
        resolve(active = germanDe, activeFromHint = true, remembered = germanDe, app = null) shouldBe
            LocaleHints.Resolution(germanDe, SubtypeManager.SubtypeSwitchSource.Restore)
        // A hint for the keyboard a hint already brought up changes nothing.
        resolve(active = germanDe, activeFromHint = true, editor = listOf("de-DE"), app = null) shouldBe null
    }
})
