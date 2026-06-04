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

        SettingsSearchHighlightStore.clear()
    }
})

private val testStrings = mapOf(
    R.string.settings__keyboard__title to "Keyboard",
    R.string.pref__keyboard__key_spacing__label to "Key spacing",
    R.string.settings__typing__title to "Typing",
    R.string.settings__home__typing_summary to "Word suggestions, autocorrect, spelling, and dictionaries",
    R.string.pref__correction__auto_correct__label to "Autocorrect",
    R.string.pref__correction__auto_correct__summary to "Correct mistyped words automatically",
    R.string.settings__voice_input__title to "Voice input",
    R.string.settings__home__voice_input_summary to "FUTO setup, offline language models, and voice keyboard status",
)

private fun resolve(resId: Int): String = testStrings[resId] ?: "res-$resId"
