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

/**
 * ROADMAP §10.5 Next-9.4a — "Pin to group…" long-press sheet state.
 *
 * Presenter-style state machine for the bottom-sheet that opens when a
 * user long-presses an emoji in the palette. Holds:
 *
 *  - The emoji being pinned.
 *  - Snapshot of currently-existing groups (so the sheet can render
 *    "Pin to ‹existing-group›" rows immediately).
 *  - The new-group text field state (empty + length-capped).
 *  - Error categorisation for the four reasons a pin can fail
 *    ([PinError]).
 *
 * Kept Compose-agnostic so it can be unit-tested without Robolectric;
 * the composable layer in [PinToGroupSheet] (see sibling file) just
 * binds [pinToExisting] / [createGroupAndPin] / [dismiss] to UI events.
 */
class PinToGroupSheetState internal constructor(
    private val store: EmojiPinGroupStore,
) {

    @Volatile
    private var visible: Boolean = false

    @Volatile
    private var emojiBeingPinned: String = ""

    @Volatile
    private var newGroupName: String = ""

    @Volatile
    private var lastError: PinError? = null

    @Volatile
    private var lastPinnedTo: String? = null

    fun isVisible(): Boolean = visible
    fun emoji(): String = emojiBeingPinned
    fun newGroupNameInput(): String = newGroupName
    fun error(): PinError? = lastError
    fun lastPinnedGroupName(): String? = lastPinnedTo

    /** Snapshot of existing group names in stable creation order. */
    fun existingGroups(): List<String> = store.groupNames()

    /** Snapshot of the emoji currently pinned under [groupName]. */
    fun emojisForExistingGroup(groupName: String): List<String> = store.emojisFor(groupName)

    /** Open the sheet for [emojiValue]. Clears prior error / new-group input. */
    fun open(emojiValue: String) {
        emojiBeingPinned = emojiValue
        newGroupName = ""
        lastError = null
        lastPinnedTo = null
        visible = true
    }

    /** Update the new-group text field. Caps to [EmojiPinGroupStore.MaxGroupNameLength]. */
    fun updateNewGroupName(text: String) {
        newGroupName = text.take(EmojiPinGroupStore.MaxGroupNameLength)
        lastError = null
    }

    /** Pin the current emoji to an already-existing group. */
    fun pinToExisting(groupName: String): Boolean {
        val emoji = emojiBeingPinned
        if (emoji.isBlank()) {
            lastError = PinError.NoEmojiSelected
            return false
        }
        val existing = store.emojisFor(groupName)
        if (existing.size >= EmojiPinGroupStore.MaxEmojisPerGroup) {
            lastError = PinError.GroupFull
            return false
        }
        if (emoji in existing) {
            lastError = PinError.AlreadyPinned
            return false
        }
        store.pinEmojiToGroup(groupName, emoji)
        lastPinnedTo = groupName
        lastError = null
        visible = false
        return true
    }

    /**
     * Create a new group from the current text field input and pin the
     * current emoji to it. Returns false (and sets [error]) when the
     * input is blank, when the group cap is reached, or when the
     * resulting group name collides with an existing one and the
     * emoji is already pinned.
     */
    fun createGroupAndPin(): Boolean {
        val emoji = emojiBeingPinned
        if (emoji.isBlank()) {
            lastError = PinError.NoEmojiSelected
            return false
        }
        val trimmed = newGroupName.trim()
        if (trimmed.isBlank()) {
            lastError = PinError.GroupNameBlank
            return false
        }
        val existing = store.groupNames()
        if (trimmed !in existing && existing.size >= EmojiPinGroupStore.MaxGroups) {
            lastError = PinError.TooManyGroups
            return false
        }
        val current = store.emojisFor(trimmed)
        if (current.size >= EmojiPinGroupStore.MaxEmojisPerGroup) {
            lastError = PinError.GroupFull
            return false
        }
        if (emoji in current) {
            lastError = PinError.AlreadyPinned
            return false
        }
        store.pinEmojiToGroup(trimmed, emoji)
        lastPinnedTo = trimmed
        lastError = null
        visible = false
        return true
    }

    /** Close the sheet without writing anything. */
    fun dismiss() {
        visible = false
        lastError = null
    }

    enum class PinError {
        NoEmojiSelected,
        GroupNameBlank,
        TooManyGroups,
        GroupFull,
        AlreadyPinned,
    }

    companion object {
        /** Public factory — production callers pass the singleton store. */
        fun forStore(store: EmojiPinGroupStore): PinToGroupSheetState =
            PinToGroupSheetState(store)
    }
}
