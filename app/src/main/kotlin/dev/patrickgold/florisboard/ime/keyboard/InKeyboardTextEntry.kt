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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import java.text.BreakIterator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Text typed into a field the keyboard draws itself: emoji, sticker and
 * clipboard search, custom emoji tags and pin group names.
 *
 * Android only connects an input method to the focused app window, never to
 * its own, so a text field inside the keyboard can't be focused or typed into
 * (#24). While a session is open the keyboard shows its letter keys with an
 * entry bar in place of the Smartbar, and [KeyboardManager] sends key presses
 * here instead of to the app. Ending the session leaves an [Outcome] for the
 * panel that asked, which reads it with [consumeOutcome] when it is shown again.
 */
class InKeyboardTextEntry {
    data class Request(
        val fieldKey: String,
        val hint: String,
        val initialText: String,
        val maxLength: Int,
        val returnTo: ImeUiMode,
    )

    data class Session(
        val request: Request,
        val text: String,
    )

    /**
     * [text] is what the field should hold now: the typed text when the user
     * pressed Done or Enter, the text it had before when they backed out.
     */
    data class Outcome(
        val fieldKey: String,
        val text: String,
        val submitted: Boolean,
    )

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private var pendingOutcome: Outcome? = null

    val isActive: Boolean
        get() = _session.value != null

    fun begin(request: Request) {
        val maxLength = request.maxLength.coerceAtLeast(1)
        val clamped = request.copy(maxLength = maxLength)
        pendingOutcome = null
        _session.value = Session(clamped, TextEntryEditing.clamp(clamped.initialText, maxLength))
    }

    /** Appends typed text. Returns false when no session is open. */
    fun type(text: String): Boolean {
        val session = _session.value ?: return false
        _session.value = session.copy(
            text = TextEntryEditing.append(session.text, text, session.request.maxLength),
        )
        return true
    }

    /** Deletes one character, or the last word when [wholeWord] is set. */
    fun deleteBackward(wholeWord: Boolean = false): Boolean {
        val session = _session.value ?: return false
        _session.value = session.copy(
            text = if (wholeWord) {
                TextEntryEditing.deleteLastWord(session.text)
            } else {
                TextEntryEditing.deleteLastCharacter(session.text)
            },
        )
        return true
    }

    fun clear(): Boolean {
        val session = _session.value ?: return false
        _session.value = session.copy(text = "")
        return true
    }

    /**
     * Ends the session and records its outcome. Returns the UI mode to go back
     * to, or null when no session was open.
     */
    fun finish(submitted: Boolean): ImeUiMode? {
        val session = _session.value ?: return null
        _session.value = null
        pendingOutcome = Outcome(
            fieldKey = session.request.fieldKey,
            text = if (submitted) session.text else session.request.initialText,
            submitted = submitted,
        )
        return session.request.returnTo
    }

    /** Drops the session without an outcome, for when the keyboard closes or the app's field changes. */
    fun abandon() {
        _session.value = null
        pendingOutcome = null
    }

    fun pendingOutcomeKey(): String? = pendingOutcome?.fieldKey

    /** Hands the outcome to the panel it belongs to, once. */
    fun consumeOutcome(accepts: (String) -> Boolean): Outcome? {
        val outcome = pendingOutcome ?: return null
        if (!accepts(outcome.fieldKey)) return null
        pendingOutcome = null
        return outcome
    }

    companion object {
        const val EmojiSearch = "emoji_search"
        const val StickerSearch = "sticker_search"
        const val ClipboardSearch = "clipboard_search"
        private const val EmojiTagPrefix = "emoji_tag:"
        private const val PinGroupPrefix = "pin_group:"

        fun emojiTag(emojiValue: String) = EmojiTagPrefix + emojiValue
        fun pinGroup(emojiValue: String) = PinGroupPrefix + emojiValue

        fun emojiTagValue(fieldKey: String): String? = fieldKey.removePrefixOrNull(EmojiTagPrefix)
        fun pinGroupValue(fieldKey: String): String? = fieldKey.removePrefixOrNull(PinGroupPrefix)

        private fun String.removePrefixOrNull(prefix: String): String? {
            return if (startsWith(prefix) && length > prefix.length) substring(prefix.length) else null
        }
    }
}

internal object TextEntryEditing {
    /** Typed text that would go past [maxLength] is dropped whole, so a surrogate pair is never split. */
    fun append(current: String, typed: String, maxLength: Int): String {
        val clean = typed.filterNot { it == '\n' || it == '\r' }
        if (clean.isEmpty()) return current
        val next = current + clean
        return if (next.length <= maxLength) next else current
    }

    fun clamp(text: String, maxLength: Int): String {
        val clean = text.filterNot { it == '\n' || it == '\r' }
        if (clean.length <= maxLength) return clean
        var end = maxLength
        if (end > 0 && Character.isHighSurrogate(clean[end - 1])) end--
        return clean.substring(0, end)
    }

    /** Removes the last user-perceived character, so an emoji or accented letter goes in one press. */
    fun deleteLastCharacter(text: String): String {
        if (text.isEmpty()) return text
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        val end = iterator.last()
        val start = iterator.previous()
        return if (start == BreakIterator.DONE) "" else text.substring(0, start) + text.substring(end)
    }

    /** Removes trailing spaces and the word before them. */
    fun deleteLastWord(text: String): String {
        val trimmed = text.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        return if (lastSpace < 0) "" else trimmed.substring(0, lastSpace + 1)
    }
}

/** What a key press does while an [InKeyboardTextEntry] session is open. */
internal enum class TextEntryKeyRoute {
    /** Type the key's text into the entry. */
    Text,
    Space,
    DeleteCharacter,
    DeleteWord,
    /** Enter applies the text and goes back to the panel that asked. */
    Submit,
    /** Esc backs out of the entry and keeps the text the field had. */
    Cancel,
    /** Back out of the entry first, then let the key do its usual job (emoji or clipboard panel, voice, hide). */
    CancelThenPassThrough,
    /** The key would act on the app's text (cursor, clipboard, undo), so it does nothing here. */
    Ignore,
    /** Shift, caps lock, symbol layers and the like work as usual. */
    PassThrough,
}

internal object TextEntryKeyPolicy {
    fun routeFor(code: Int, type: KeyType): TextEntryKeyRoute = when (code) {
        KeyCode.DELETE -> TextEntryKeyRoute.DeleteCharacter
        KeyCode.DELETE_WORD -> TextEntryKeyRoute.DeleteWord
        KeyCode.ENTER -> TextEntryKeyRoute.Submit
        KeyCode.ESCAPE -> TextEntryKeyRoute.Cancel
        KeyCode.SPACE,
        KeyCode.CJK_SPACE -> TextEntryKeyRoute.Space
        KeyCode.IME_UI_MODE_TEXT,
        KeyCode.IME_UI_MODE_MEDIA,
        KeyCode.IME_UI_MODE_CLIPBOARD,
        KeyCode.IME_HIDE_UI,
        KeyCode.VOICE_INPUT,
        KeyCode.SETTINGS,
        KeyCode.SYSTEM_INPUT_METHOD_PICKER,
        KeyCode.SYSTEM_PREV_INPUT_METHOD,
        KeyCode.SYSTEM_NEXT_INPUT_METHOD,
        KeyCode.TOGGLE_RESIZE_MODE -> TextEntryKeyRoute.CancelThenPassThrough
        KeyCode.TAB,
        KeyCode.FORWARD_DELETE,
        KeyCode.FORWARD_DELETE_WORD,
        KeyCode.ARROW_LEFT,
        KeyCode.ARROW_RIGHT,
        KeyCode.ARROW_UP,
        KeyCode.ARROW_DOWN,
        KeyCode.MOVE_START_OF_PAGE,
        KeyCode.MOVE_END_OF_PAGE,
        KeyCode.MOVE_START_OF_LINE,
        KeyCode.MOVE_END_OF_LINE,
        KeyCode.PAGE_UP,
        KeyCode.PAGE_DOWN,
        KeyCode.CLIPBOARD_COPY,
        KeyCode.CLIPBOARD_CUT,
        KeyCode.CLIPBOARD_PASTE,
        KeyCode.CLIPBOARD_SELECT,
        KeyCode.CLIPBOARD_SELECT_ALL,
        KeyCode.UNDO,
        KeyCode.REDO,
        KeyCode.TOGGLE_ACTIONS_OVERFLOW,
        KeyCode.TOGGLE_ACTIONS_EDITOR -> TextEntryKeyRoute.Ignore
        else -> when (type) {
            KeyType.CHARACTER, KeyType.NUMERIC -> TextEntryKeyRoute.Text
            else -> TextEntryKeyRoute.PassThrough
        }
    }
}
