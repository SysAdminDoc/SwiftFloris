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
import java.nio.file.StandardCopyOption

class EmojiPinGroupStoreTest : FunSpec({
    fun freshStore(): Pair<EmojiPinGroupStore, File> {
        val tmpDir = Files.createTempDirectory("emoji-pin-test").toFile()
        val file = File(tmpDir, "groups.json")
        return EmojiPinGroupStore.forStorageFile(file) to file
    }

    test("starts empty and reports no groups") {
        val (store, _) = freshStore()
        store.groupNames() shouldBe emptyList()
        store.emojisFor("birthday") shouldBe emptyList()
    }

    test("pin appends an emoji to a new group") {
        val (store, _) = freshStore()
        store.pinEmojiToGroup("birthday", "\uD83C\uDF82") shouldContainExactly listOf("\uD83C\uDF82") // 🎂
        store.pinEmojiToGroup("birthday", "\uD83C\uDF89") shouldContainExactly listOf("\uD83C\uDF82", "\uD83C\uDF89") // 🎉
        store.groupNames() shouldContainExactly listOf("birthday")
    }

    test("pinning a duplicate emoji is a no-op") {
        val (store, _) = freshStore()
        store.pinEmojiToGroup("party", "\uD83C\uDF89")
        store.pinEmojiToGroup("party", "\uD83C\uDF89") shouldContainExactly listOf("\uD83C\uDF89")
    }

    test("unpin removes the emoji; emptied groups are dropped entirely") {
        val (store, _) = freshStore()
        store.pinEmojiToGroup("party", "\uD83C\uDF89")
        store.pinEmojiToGroup("party", "\uD83C\uDF82")
        store.unpinEmojiFromGroup("party", "\uD83C\uDF89") shouldContainExactly listOf("\uD83C\uDF82")
        store.unpinEmojiFromGroup("party", "\uD83C\uDF82") shouldContainExactly emptyList()
        store.groupNames() shouldBe emptyList()
    }

    test("removeGroup drops the named group and reports the removal") {
        val (store, _) = freshStore()
        store.pinEmojiToGroup("a", "\uD83C\uDF82")
        store.pinEmojiToGroup("b", "\uD83C\uDF89")
        store.removeGroup("a") shouldBe true
        store.removeGroup("missing") shouldBe false
        store.groupNames() shouldContainExactly listOf("b")
    }

    test("clearAll empties every group") {
        val (store, _) = freshStore()
        store.pinEmojiToGroup("a", "\uD83C\uDF82")
        store.pinEmojiToGroup("b", "\uD83C\uDF89")
        store.clearAll()
        store.groupNames() shouldBe emptyList()
    }

    test("respects MaxEmojisPerGroup cap") {
        val (store, _) = freshStore()
        repeat(EmojiPinGroupStore.MaxEmojisPerGroup + 4) { i ->
            store.pinEmojiToGroup("over", "x$i")
        }
        store.emojisFor("over").size shouldBe EmojiPinGroupStore.MaxEmojisPerGroup
    }

    test("respects MaxGroups cap by refusing new groups after the limit") {
        val (store, _) = freshStore()
        repeat(EmojiPinGroupStore.MaxGroups) { i ->
            store.pinEmojiToGroup("group$i", "\uD83C\uDF82")
        }
        store.pinEmojiToGroup("overflow", "\uD83C\uDF82") shouldBe emptyList()
        store.groupNames().size shouldBe EmojiPinGroupStore.MaxGroups
    }

    test("survives a flush-and-load round-trip") {
        val (store, file) = freshStore()
        store.pinEmojiToGroup("a", "\uD83C\uDF82")
        store.pinEmojiToGroup("a", "\uD83C\uDF89")
        // Reload from the same file.
        val restored = EmojiPinGroupStore.forStorageFile(file)
        restored.emojisFor("a") shouldContainExactly listOf("\uD83C\uDF82", "\uD83C\uDF89")
    }

    test("replacement failure keeps the previous good file intact") {
        val (directory, file) = run {
            val tmpDir = Files.createTempDirectory("emoji-pin-failure-test").toFile()
            tmpDir to File(tmpDir, "groups.json")
        }
        var moveCount = 0
        val store = EmojiPinGroupStore.forStorageFile(file) { staged, target ->
            moveCount++
            if (moveCount == 2) error("forced replacement failure")
            Files.move(
                staged.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        store.pinEmojiToGroup("party", "🎂") shouldContainExactly listOf("🎂")
        store.pinEmojiToGroup("party", "🎉") shouldContainExactly listOf("🎂", "🎉")

        file.readText().contains("🎂") shouldBe true
        file.readText().contains("🎉") shouldBe false
        EmojiPinGroupStore.forStorageFile(file).emojisFor("party") shouldContainExactly listOf("🎂")
        directory.deleteRecursively()
    }
})
