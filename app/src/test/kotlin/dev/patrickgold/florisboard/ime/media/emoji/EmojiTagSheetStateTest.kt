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
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class EmojiTagSheetStateTest : FunSpec({
    fun freshState(): Pair<EmojiTagSheetState, CustomEmojiTagStore> {
        val directory = Files.createTempDirectory("emoji-tag-sheet").toFile()
        val store = CustomEmojiTagStore.forStorageFile(File(directory, "tags.json"))
        return EmojiTagSheetState.forStore(store) to store
    }

    test("open clears stale input and exposes existing tags") {
        val (state, store) = freshState()
        store.addTag("🦋", "freedom")
        state.updateTagInput("stale")
        state.open("🦋")

        state.isVisible() shouldBe true
        state.tagInput() shouldBe ""
        state.existingTags() shouldContainExactly listOf("freedom")
    }

    test("addTag writes through the store and closes") {
        val (state, store) = freshState()
        state.open("🦋")
        state.updateTagInput("  Freedom ")

        state.addTag() shouldBe true
        state.isVisible() shouldBe false
        state.lastAddedTag() shouldBe "freedom"
        store.tagsFor("🦋") shouldContainExactly listOf("freedom")
    }

    test("blank and duplicate tags keep the sheet open with a useful error") {
        val (state, store) = freshState()
        store.addTag("🦋", "freedom")
        state.open("🦋")
        state.updateTagInput(" ")
        state.addTag() shouldBe false
        state.error() shouldBe EmojiTagSheetState.TagError.TagBlank
        state.updateTagInput("FREEDOM")
        state.addTag() shouldBe false
        state.error() shouldBe EmojiTagSheetState.TagError.Duplicate
        state.isVisible() shouldBe true
    }

    test("removeTag updates the current emoji") {
        val (state, store) = freshState()
        store.addTag("🦋", "freedom")
        store.addTag("🦋", "transform")
        state.open("🦋")

        state.removeTag("freedom") shouldBe true
        state.existingTags() shouldContainExactly listOf("transform")
        state.removeTag("missing") shouldBe false
    }
})
