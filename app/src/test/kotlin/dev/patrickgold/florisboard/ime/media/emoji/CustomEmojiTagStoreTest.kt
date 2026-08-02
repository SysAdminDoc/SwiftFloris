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
import java.util.Locale

class CustomEmojiTagStoreTest : FunSpec({
    fun freshStore(): Pair<CustomEmojiTagStore, File> {
        val tmpDir = Files.createTempDirectory("emoji-tag-test").toFile()
        val file = File(tmpDir, "tags.json")
        return CustomEmojiTagStore.forStorageFile(file) to file
    }

    test("starts empty and ignores blank emoji or tag input") {
        val (store, _) = freshStore()
        store.taggedEmojiValues() shouldBe emptySet()
        store.addTag("", "tag") shouldBe emptyList()
        store.addTag("🦋", "   ") shouldBe emptyList()
        store.snapshot() shouldBe emptyMap()
    }

    test("normalizes tags and survives a reload") {
        val (store, file) = freshStore()
        store.addTag("🦋", "  Freedom  ") shouldContainExactly listOf("freedom")
        store.addTag("🦋", "TRANSFORM") shouldContainExactly listOf("freedom", "transform")

        val restored = CustomEmojiTagStore.forStorageFile(file)
        restored.tagsFor("🦋") shouldContainExactly listOf("freedom", "transform")
        restored.removeTag("🦋", " FREEDOM ") shouldContainExactly listOf("transform")
    }

    test("normalization is locale independent for Turkish") {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr"))
            val (store, _) = freshStore()
            store.addTag("🦋", "I") shouldContainExactly listOf("i")
            store.removeTag("🦋", "I") shouldBe emptyList()
        } finally {
            Locale.setDefault(previous)
        }
    }

    test("search includes a persisted custom tag") {
        val (store, _) = freshStore()
        store.addTag("🦋", "freedom")
        val mappings = EmojiCategory.entries.associateWith { category ->
            if (category == EmojiCategory.SMILEYS_EMOTION) {
                listOf(EmojiSet(listOf(Emoji("🦋", "butterfly", emptyList()))))
            } else {
                emptyList()
            }
        }

        EmojiSearch.results(mappings, "freedom", customTagStore = store)
            .map { it.base().value } shouldContainExactly listOf("🦋")
    }

    test("enforces the per-emoji tag cap") {
        val (store, _) = freshStore()
        repeat(CustomEmojiTagStore.MaxTagsPerEmoji) { index ->
            store.addTag("🦋", "tag-$index")
        }
        store.addTag("🦋", "overflow")

        store.tagsFor("🦋").size shouldBe CustomEmojiTagStore.MaxTagsPerEmoji
        store.tagsFor("🦋").last() shouldBe "tag-${CustomEmojiTagStore.MaxTagsPerEmoji - 1}"
    }

    test("enforces the total tagged emoji cap") {
        val (store, _) = freshStore()
        repeat(CustomEmojiTagStore.MaxTaggedEmoji) { index ->
            store.addTag("emoji-$index", "tag")
        }
        store.addTag("emoji-overflow", "tag") shouldBe emptyList()
        store.taggedEmojiValues().size shouldBe CustomEmojiTagStore.MaxTaggedEmoji
    }

    test("corrupt JSON recovers as empty and can be rewritten") {
        val (_, file) = freshStore()
        file.writeText("not-json")
        val recovered = CustomEmojiTagStore.forStorageFile(file)
        recovered.snapshot() shouldBe emptyMap()
        recovered.addTag("🎯", "focus") shouldContainExactly listOf("focus")
        CustomEmojiTagStore.forStorageFile(file).tagsFor("🎯") shouldContainExactly listOf("focus")
    }

    test("clearAll removes every custom tag") {
        val (store, _) = freshStore()
        store.addTag("🦋", "freedom")
        store.addTag("🎯", "focus")
        store.clearAll()
        store.snapshot() shouldBe emptyMap()
    }
})
