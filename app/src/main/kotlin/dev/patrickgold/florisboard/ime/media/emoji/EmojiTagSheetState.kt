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

import java.util.Locale

/** Compose-agnostic state for assigning and managing custom emoji tags. */
class EmojiTagSheetState internal constructor(
    private val store: CustomEmojiTagStore,
) {

    @Volatile
    private var visible: Boolean = false

    @Volatile
    private var emojiBeingTagged: String = ""

    @Volatile
    private var tagInput: String = ""

    @Volatile
    private var lastError: TagError? = null

    @Volatile
    private var lastAddedTag: String? = null

    fun isVisible(): Boolean = visible
    fun emoji(): String = emojiBeingTagged
    fun tagInput(): String = tagInput
    fun error(): TagError? = lastError
    fun lastAddedTag(): String? = lastAddedTag

    fun existingTags(): List<String> = store.tagsFor(emojiBeingTagged)

    fun open(emojiValue: String) {
        emojiBeingTagged = emojiValue
        tagInput = ""
        lastError = null
        lastAddedTag = null
        visible = true
    }

    fun updateTagInput(text: String) {
        tagInput = text.take(CustomEmojiTagStore.MaxTagLength)
        lastError = null
    }

    /** Adds the current input and closes the sheet only when it was accepted. */
    fun addTag(): Boolean {
        if (emojiBeingTagged.isBlank()) {
            lastError = TagError.NoEmojiSelected
            return false
        }
        val normalized = tagInput.trim().lowercase(Locale.ROOT).take(CustomEmojiTagStore.MaxTagLength)
        if (normalized.isBlank()) {
            lastError = TagError.TagBlank
            return false
        }
        val before = store.tagsFor(emojiBeingTagged)
        val after = store.addTag(emojiBeingTagged, tagInput)
        if (after.size == before.size) {
            lastError = when {
                normalized in before -> TagError.Duplicate
                before.size >= CustomEmojiTagStore.MaxTagsPerEmoji -> TagError.TooManyTags
                else -> TagError.TooManyEmoji
            }
            return false
        }
        lastAddedTag = normalized
        lastError = null
        visible = false
        return true
    }

    fun removeTag(tag: String): Boolean {
        val before = store.tagsFor(emojiBeingTagged)
        if (tag !in before) return false
        store.removeTag(emojiBeingTagged, tag)
        return true
    }

    fun dismiss() {
        visible = false
        lastError = null
    }

    enum class TagError {
        NoEmojiSelected,
        TagBlank,
        Duplicate,
        TooManyTags,
        TooManyEmoji,
    }

    companion object {
        fun forStore(store: CustomEmojiTagStore): EmojiTagSheetState =
            EmojiTagSheetState(store)
    }
}
