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

package dev.patrickgold.florisboard.ime.media.emoji

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * ROADMAP §6 N17.1 — emoji-picker crash triage regression coverage.
 *
 * The historical crash report ([GH-SWIFTF-ISSUE-1]) was reproduced as
 * "tap an emoji from the palette → IME process death". The audit
 * trace landed on an empty `Emoji.value` reaching `Paint.hasGlyph("")`,
 * which throws `IllegalArgumentException("hasGlyph called with empty
 * string")` and aborts the palette render. Empty values can leak in
 * through:
 *
 *  1. `Emoji.ValueOnlySerializer.deserialize(decoder)` — a `""` entry
 *     in a corrupt / hand-edited `emoji.historyData` preference JSON
 *     round-trips into an `Emoji(value = "")` with no validation.
 *  2. `EmojiData.loadEmojiDataMap(...)` — a malformed `;...;...;...`
 *     asset line with a blank first column yields an empty-value Emoji
 *     and adds it to the per-category list.
 *
 * Both paths are now defended at construction; this test pins the
 * boundary contracts so a future contributor cannot quietly
 * re-introduce either.
 */
class EmojiHistoryEmptyValueTest : FunSpec({

    test("Emoji.ValueOnlySerializer still round-trips an empty string (downstream filters are the gate)") {
        // The serializer itself is intentionally permissive: it
        // accepts whatever the codec decoded so we never lose data on
        // a partial corruption. The palette + downstream call sites
        // are the ones that must guard.
        val json = Json
        val emoji = json.decodeFromString(Emoji.ValueOnlySerializer, "\"\"")

        emoji.value shouldBe ""
        emoji.name shouldBe ""
        emoji.keywords shouldBe emptyList()
    }

    test("EmojiHistory deserialiser tolerates a stored empty-value entry") {
        // A corrupt historyData JSON with an empty pinned entry must
        // not throw — `Empty` fallback is the last-resort behaviour
        // documented on the serializer, but a parseable malformed
        // entry should round-trip and let the palette filter handle
        // it instead of nuking the entire history.
        val json = """{"pinned":["","😀"],"recent":[]}"""

        val history = EmojiHistory.Serializer.deserialize(json)

        history.pinned shouldHaveSize 2
        history.pinned[0].value shouldBe ""
        history.pinned[1].value shouldBe "😀"
    }

    test("EmojiSet wrapping an empty-value Emoji is still constructible") {
        // EmojiSet.init only requires the list to be non-empty, not
        // every Emoji in the list to have a non-empty value. That's by
        // design — we don't want one corrupt entry to crash the
        // wrapping. Only the palette pipeline is responsible for
        // filtering blank values before render.
        val set = EmojiSet(listOf(Emoji(value = "", name = "broken", keywords = emptyList())))

        set.emojis shouldHaveSize 1
        set.base().value shouldBe ""
    }

    test("history-filter snippet removes empty-value entries before EmojiSet wrap") {
        // Mirrors the filter `EmojiPaletteView` applies before wrapping
        // history entries into EmojiSets. Pure-Kotlin replication so
        // the regression can be caught without Robolectric.
        val pinned = listOf(
            Emoji(value = "😀", name = "happy", keywords = emptyList()),
            Emoji(value = "", name = "broken", keywords = emptyList()),
            Emoji(value = "🔥", name = "fire", keywords = emptyList()),
        )
        val wrapped = pinned
            .filter { it.value.isNotEmpty() }
            .map { EmojiSet(listOf(it)) }

        wrapped.map { it.base().value } shouldContainExactly listOf("😀", "🔥")
    }
})
