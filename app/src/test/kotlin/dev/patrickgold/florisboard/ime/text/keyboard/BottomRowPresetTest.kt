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

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class BottomRowPresetTest : FunSpec({
    test("automatic keeps the asset-defined legacy bottom row") {
        BottomRowPreset.fromJsonOverride(BottomRowPreset.AutomaticPreferenceValue) shouldBe null
        BottomRowPreset.fromJsonOverride("") shouldBe null
    }

    test("preset json round-trips as the persisted override") {
        val encoded = BottomRowPreset.Language.toJson()

        BottomRowPreset.fromJsonOverride(encoded) shouldBe BottomRowPreset.Language
    }

    test("invalid override falls back to SwiftKey-style row") {
        BottomRowPreset.fromJsonOverride("{broken") shouldBe BottomRowPreset.SwiftKey
    }

    test("normalization keeps a single spacebar and preserves surrounding keys") {
        val preset = BottomRowPreset(
            keys = listOf(
                BottomRowKey.EMOJI,
                BottomRowKey.SPACE,
                BottomRowKey.SPACE,
                BottomRowKey.PERIOD,
            ),
        )

        preset.normalized().keys shouldBe listOf(
            BottomRowKey.EMOJI,
            BottomRowKey.SPACE,
            BottomRowKey.PERIOD,
        )
    }

    test("SwiftKey preset creates the expected bottom-row key codes") {
        BottomRowPreset.SwiftKey.toTextKeyDataRow().map { it.code }.shouldContainExactly(
            KeyCode.VIEW_SYMBOLS,
            KeyCode.IME_UI_MODE_MEDIA,
            44,
            KeyCode.SPACE,
            46,
            KeyCode.ENTER,
        )
    }

    test("voice preset makes voice a dedicated key instead of comma popup") {
        val row = BottomRowPreset.Voice.toTextKeyDataRow()

        row.map { it.code }.shouldContainExactly(
            KeyCode.VIEW_SYMBOLS,
            KeyCode.IME_UI_MODE_MEDIA,
            44,
            KeyCode.VOICE_INPUT,
            KeyCode.SPACE,
            46,
            KeyCode.ENTER,
        )
        row.first { it.code == 44 }.popup shouldBe null
    }
})
