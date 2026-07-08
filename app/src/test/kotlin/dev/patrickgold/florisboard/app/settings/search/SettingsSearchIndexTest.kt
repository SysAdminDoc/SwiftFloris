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

package dev.patrickgold.florisboard.app.settings.search

import dev.patrickgold.florisboard.R
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class SettingsSearchIndexTest : FunSpec({
    beforeTest {
        SettingsSearchHighlightStore.clear()
    }

    test("blank query returns no results") {
        SettingsSearchIndex.search("   ", ::resolve).shouldBeEmpty()
    }

    test("catalog has at least one entry for every destination") {
        val missing = SettingsSearchDestination.entries
            .filterNot { destination -> SettingsSearchIndex.entries.any { it.destination == destination } }
            .toSet()

        missing.shouldBeEmpty()
    }

    test("title matches rank ahead of broader screen keyword matches") {
        val results = SettingsSearchIndex.search("autocorrect", ::resolve)

        results.first().entry.id shouldBe "typing.autocorrect"
        results.first().entry.destination shouldBe SettingsSearchDestination.TYPING
    }

    test("multi-term query can combine destination screen and setting label") {
        val results = SettingsSearchIndex.search("keyboard spacing", ::resolve)

        results.first().entry.id shouldBe "keyboard.spacing"
        results.first().entry.destination shouldBe SettingsSearchDestination.KEYBOARD
    }

    test("custom layout editor is searchable from keyboard settings") {
        val results = SettingsSearchIndex.search("custom layout", ::resolve)

        results.first().entry.id shouldBe "keyboard.custom-layout-editor"
        results.first().entry.destination shouldBe SettingsSearchDestination.KEYBOARD
    }

    test("query normalization folds diacritics") {
        SettingsSearchIndex.search("theme", ::resolve).first().entry.id shouldBe "theme"
        SettingsSearchIndex.search("themé", ::resolve).first().entry.id shouldBe "theme"
        SettingsSearchIndex.search("thème", ::resolve).first().entry.id shouldBe "theme"
    }

    test("capability synonyms resolve to expected settings destinations") {
        val cases = mapOf(
            "dark theme" to SettingsSearchDestination.THEME,
            "haptic" to SettingsSearchDestination.INPUT_FEEDBACK,
            "trace" to SettingsSearchDestination.GESTURES,
            "punctuation" to SettingsSearchDestination.TYPING,
            "privacy" to SettingsSearchDestination.PRIVACY_AUDIT,
        )

        cases.forEach { (query, destination) ->
            SettingsSearchIndex.search(query, ::resolve).first().entry.destination shouldBe destination
        }
    }

    test("specific capability synonyms rank the target setting first") {
        SettingsSearchIndex.search("dark mode", ::resolve).first().entry.id shouldBe "theme.mode"
        SettingsSearchIndex.search("punctuation", ::resolve).first().entry.id shouldBe "typing.auto-space-punctuation"
        SettingsSearchIndex.search("privacy", ::resolve).first().entry.id shouldBe "privacy-audit"
        SettingsSearchIndex.search("tasker automation", ::resolve).first().entry.id shouldBe
            "privacy-posture.automation"
    }

    test("search target stores resolved labels for destination highlight") {
        val result = SettingsSearchIndex.search("futo", ::resolve).first()

        SettingsSearchHighlightStore.mark(result.entry, "futo", ::resolve)

        SettingsSearchHighlightStore.activeTarget shouldBe SettingsSearchTarget(
            entryId = "voice",
            screenTitle = "Voice input",
            title = "Voice input",
            summary = "FUTO setup, offline language models, and voice keyboard status",
            query = "futo",
        )
    }

    test("search target is consumed once for its matching destination screen") {
        val result = SettingsSearchIndex.search("futo", ::resolve).first()

        SettingsSearchHighlightStore.mark(result.entry, " futo ", ::resolve)

        SettingsSearchHighlightStore.consumeTargetFor("Typing") shouldBe null
        SettingsSearchHighlightStore.activeTarget?.entryId shouldBe "voice"

        SettingsSearchHighlightStore.consumeTargetFor("Voice input") shouldBe SettingsSearchTarget(
            entryId = "voice",
            screenTitle = "Voice input",
            title = "Voice input",
            summary = "FUTO setup, offline language models, and voice keyboard status",
            query = "futo",
        )
        SettingsSearchHighlightStore.activeTarget shouldBe null
        SettingsSearchHighlightStore.consumeTargetFor("Voice input") shouldBe null
    }
})

private val testStrings = mapOf(
    R.string.settings__keyboard__title to "Keyboard",
    R.string.pref__keyboard__key_spacing__label to "Key spacing",
    R.string.settings__keyboard__custom_layout_editor__title to "Custom layout editor",
    R.string.settings__keyboard__custom_layout_editor__summary to "Clone a character layout, edit rows and keys, then save it as a local layout.",
    R.string.settings__typing__title to "Typing",
    R.string.settings__home__typing_summary to "Word suggestions, autocorrect, spelling, and dictionaries",
    R.string.pref__correction__auto_correct__label to "Autocorrect",
    R.string.pref__correction__auto_correct__summary to "Correct mistyped words automatically",
    R.string.settings__theme__title to "Theme",
    R.string.settings__home__theme_summary to "Keyboard colors, dark mode, and custom themes",
    R.string.pref__theme__mode__label to "Theme mode",
    R.string.settings__input_feedback__title to "Input feedback",
    R.string.settings__home__input_feedback_summary to "Sound, vibration, and haptic keypress feedback",
    R.string.settings__gestures__title to "Gestures",
    R.string.pref__glide__enabled__label to "Glide typing",
    R.string.pref__glide__show_trail__label to "Show glide trail",
    R.string.pref__correction__auto_space_punctuation__label to "Auto-space punctuation",
    R.string.pref__correction__auto_space_punctuation__summary to "Insert spacing around punctuation automatically",
    R.string.settings__privacy_audit__title to "Privacy audit",
    R.string.settings__privacy_audit__home_summary to "Review local privacy and addon activity",
    R.string.settings__privacy_posture__automation_title to "Tasker automation",
    R.string.settings__privacy_posture__automation_summary to
        "Off by default. When enabled, automation apps can send validated SwiftFloris actions.",
    R.string.settings__voice_input__title to "Voice input",
    R.string.settings__home__voice_input_summary to "FUTO setup, offline language models, and voice keyboard status",
)

private fun resolve(resId: Int): String = testStrings[resId] ?: "res-$resId"
