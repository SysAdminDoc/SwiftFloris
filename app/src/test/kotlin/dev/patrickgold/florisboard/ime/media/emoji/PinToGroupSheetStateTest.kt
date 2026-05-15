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
import java.io.File
import java.nio.file.Files

class PinToGroupSheetStateTest : FunSpec({

    fun freshStore(): Pair<EmojiPinGroupStore, File> {
        val tmp = Files.createTempDirectory("pinsheet").toFile()
        val storageFile = File(tmp, "emoji_pin_groups.json")
        return EmojiPinGroupStore.forStorageFile(storageFile) to tmp
    }

    test("open sets visible + clears prior input + reads existing groups") {
        val (store, _) = freshStore()
        store.pinEmojiToGroup("birthday", "🎂")
        val state = PinToGroupSheetState.forStore(store)
        state.updateNewGroupName("stale")
        state.open("🎉")
        state.isVisible() shouldBe true
        state.emoji() shouldBe "🎉"
        state.newGroupNameInput() shouldBe ""
        state.existingGroups() shouldBe listOf("birthday")
        state.error() shouldBe null
    }

    test("pinToExisting writes through the store and closes the sheet") {
        val (store, _) = freshStore()
        store.pinEmojiToGroup("celebration", "🎂")
        val state = PinToGroupSheetState.forStore(store)
        state.open("🎁")
        val ok = state.pinToExisting("celebration")
        ok shouldBe true
        state.isVisible() shouldBe false
        state.lastPinnedGroupName() shouldBe "celebration"
        store.emojisFor("celebration") shouldBe listOf("🎂", "🎁")
    }

    test("createGroupAndPin trims input and persists") {
        val (store, _) = freshStore()
        val state = PinToGroupSheetState.forStore(store)
        state.open("😭")
        state.updateNewGroupName("   sadness   ")
        val ok = state.createGroupAndPin()
        ok shouldBe true
        state.lastPinnedGroupName() shouldBe "sadness"
        store.emojisFor("sadness") shouldBe listOf("😭")
    }

    test("blank new-group name surfaces GroupNameBlank") {
        val (store, _) = freshStore()
        val state = PinToGroupSheetState.forStore(store)
        state.open("😭")
        state.updateNewGroupName("   ")
        state.createGroupAndPin() shouldBe false
        state.error() shouldBe PinToGroupSheetState.PinError.GroupNameBlank
        state.isVisible() shouldBe true
    }

    test("pinning to an already-containing group surfaces AlreadyPinned and keeps sheet open") {
        val (store, _) = freshStore()
        store.pinEmojiToGroup("celebration", "🎁")
        val state = PinToGroupSheetState.forStore(store)
        state.open("🎁")
        state.pinToExisting("celebration") shouldBe false
        state.error() shouldBe PinToGroupSheetState.PinError.AlreadyPinned
        state.isVisible() shouldBe true
        store.emojisFor("celebration") shouldBe listOf("🎁")
    }

    test("group-full surfaces GroupFull and refuses the write") {
        val (store, _) = freshStore()
        repeat(EmojiPinGroupStore.MaxEmojisPerGroup) { idx ->
            store.pinEmojiToGroup("celebration", "emoji-$idx")
        }
        store.emojisFor("celebration").size shouldBe EmojiPinGroupStore.MaxEmojisPerGroup
        val state = PinToGroupSheetState.forStore(store)
        state.open("🚀")
        state.pinToExisting("celebration") shouldBe false
        state.error() shouldBe PinToGroupSheetState.PinError.GroupFull
    }

    test("too-many-groups surfaces TooManyGroups when creating a brand-new group at the cap") {
        val (store, _) = freshStore()
        repeat(EmojiPinGroupStore.MaxGroups) { idx ->
            store.pinEmojiToGroup("group-$idx", "🎈")
        }
        store.groupNames().size shouldBe EmojiPinGroupStore.MaxGroups
        val state = PinToGroupSheetState.forStore(store)
        state.open("✨")
        state.updateNewGroupName("brand-new")
        state.createGroupAndPin() shouldBe false
        state.error() shouldBe PinToGroupSheetState.PinError.TooManyGroups
    }

    test("dismiss clears the sheet without touching the store") {
        val (store, _) = freshStore()
        val state = PinToGroupSheetState.forStore(store)
        state.open("🎉")
        state.dismiss()
        state.isVisible() shouldBe false
        store.groupNames() shouldBe emptyList()
    }
})
