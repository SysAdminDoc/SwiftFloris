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
import io.kotest.matchers.shouldBe

class PinnedGroupChipTest : FunSpec({
    test("fromStoreSnapshot truncates preview to PREVIEW_LIMIT but keeps total count accurate") {
        val snapshot = mapOf(
            "birthday" to listOf("🎂", "🎉", "🎁", "🎈", "✨"),
            "love" to listOf("❤️"),
        )
        val chips = PinnedGroupChip.fromStoreSnapshot(snapshot)
        chips.size shouldBe 2
        val birthday = chips.single { it.name == "birthday" }
        birthday.previewEmojis shouldBe listOf("🎂", "🎉", "🎁")
        birthday.totalEmojiCount shouldBe 5
        val love = chips.single { it.name == "love" }
        love.previewEmojis shouldBe listOf("❤️")
        love.totalEmojiCount shouldBe 1
    }

    test("fromStoreSnapshot preserves group order for stable rendering") {
        val snapshot = linkedMapOf(
            "a" to listOf("🅰️"),
            "b" to listOf("🅱️"),
            "c" to listOf("🆎"),
        )
        val chips = PinnedGroupChip.fromStoreSnapshot(snapshot)
        chips.map { it.name } shouldBe listOf("a", "b", "c")
    }

    test("empty snapshot yields empty chip list") {
        PinnedGroupChip.fromStoreSnapshot(emptyMap()) shouldBe emptyList()
    }
})
