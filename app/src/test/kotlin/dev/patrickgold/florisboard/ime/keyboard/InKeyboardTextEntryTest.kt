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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Issue #24: the emoji tag, emoji search, sticker search, clipboard search and
 * pin group fields could not be typed into, because Android never connects an
 * input method to its own window. The letter keys now type into those fields
 * through an entry session.
 */
class InKeyboardTextEntryTest : FunSpec({

    fun request(
        fieldKey: String = InKeyboardTextEntry.emojiTag("🦋"),
        initialText: String = "",
        maxLength: Int = 32,
        returnTo: ImeUiMode = ImeUiMode.MEDIA,
    ) = InKeyboardTextEntry.Request(
        fieldKey = fieldKey,
        hint = "Add a tag",
        initialText = initialText,
        maxLength = maxLength,
        returnTo = returnTo,
    )

    test("typed keys build the text and Done hands it to the field that asked") {
        val entry = InKeyboardTextEntry()
        entry.begin(request())

        "freedom".forEach { entry.type(it.toString()) shouldBe true }
        val returnTo = entry.finish(submitted = true)

        returnTo shouldBe ImeUiMode.MEDIA
        entry.isActive shouldBe false
        entry.pendingOutcomeKey() shouldBe InKeyboardTextEntry.emojiTag("🦋")
        val outcome = entry.consumeOutcome { InKeyboardTextEntry.emojiTagValue(it) == "🦋" }
        outcome shouldBe InKeyboardTextEntry.Outcome(
            fieldKey = InKeyboardTextEntry.emojiTag("🦋"),
            text = "freedom",
            submitted = true,
        )
    }

    test("backing out keeps the text the field had before") {
        val entry = InKeyboardTextEntry()
        entry.begin(request(fieldKey = InKeyboardTextEntry.ClipboardSearch, initialText = "inv", returnTo = ImeUiMode.CLIPBOARD))
        entry.type("oice")

        entry.finish(submitted = false) shouldBe ImeUiMode.CLIPBOARD
        val outcome = entry.consumeOutcome { it == InKeyboardTextEntry.ClipboardSearch }

        outcome?.text shouldBe "inv"
        outcome?.submitted shouldBe false
    }

    test("an outcome goes only to its own field and only once") {
        val entry = InKeyboardTextEntry()
        entry.begin(request(fieldKey = InKeyboardTextEntry.StickerSearch))
        entry.type("cat")
        entry.finish(submitted = true)

        entry.consumeOutcome { it == InKeyboardTextEntry.EmojiSearch }.shouldBeNull()
        entry.consumeOutcome { it == InKeyboardTextEntry.StickerSearch }?.text shouldBe "cat"
        entry.consumeOutcome { it == InKeyboardTextEntry.StickerSearch }.shouldBeNull()
    }

    test("closing the keyboard mid-entry drops the session and leaves no outcome") {
        val entry = InKeyboardTextEntry()
        entry.begin(request())
        entry.type("half")

        entry.abandon()

        entry.isActive shouldBe false
        entry.session.value.shouldBeNull()
        entry.pendingOutcomeKey().shouldBeNull()
        entry.type("x") shouldBe false
        entry.finish(submitted = true).shouldBeNull()
    }

    test("a new entry clears an outcome nobody picked up") {
        val entry = InKeyboardTextEntry()
        entry.begin(request(fieldKey = InKeyboardTextEntry.EmojiSearch))
        entry.finish(submitted = true)

        entry.begin(request(fieldKey = InKeyboardTextEntry.StickerSearch))

        entry.pendingOutcomeKey().shouldBeNull()
    }

    test("text stops at the field's limit without splitting an emoji") {
        val entry = InKeyboardTextEntry()
        entry.begin(request(maxLength = 4))

        entry.type("abc")
        entry.type("🦋")
        entry.session.value?.text shouldBe "abc"
        entry.type("d")
        entry.session.value?.text shouldBe "abcd"
        entry.type("e")
        entry.session.value?.text shouldBe "abcd"

        TextEntryEditing.clamp("abc🦋", 4) shouldBe "abc"
        TextEntryEditing.clamp("line\nbreak", 32) shouldBe "linebreak"
    }

    test("Enter never types a line break into the field") {
        TextEntryEditing.append("cat", "\n", 32) shouldBe "cat"
        TextEntryEditing.append("cat", " dog\r\n", 32) shouldBe "cat dog"
    }

    test("delete removes a whole emoji or accented letter in one press") {
        TextEntryEditing.deleteLastCharacter("ok👍🏽") shouldBe "ok"
        TextEntryEditing.deleteLastCharacter("hi👨‍👩‍👧") shouldBe "hi"
        // "e" plus a combining acute accent is one character to the user.
        TextEntryEditing.deleteLastCharacter("cafe" + 0x301.toChar()) shouldBe "caf"
        TextEntryEditing.deleteLastCharacter("ab") shouldBe "a"
        TextEntryEditing.deleteLastCharacter("") shouldBe ""
    }

    test("delete word removes the last word and the spaces after it") {
        TextEntryEditing.deleteLastWord("big cat  ") shouldBe "big "
        TextEntryEditing.deleteLastWord("cat") shouldBe ""
        TextEntryEditing.deleteLastWord("") shouldBe ""
    }

    test("clear empties the entry") {
        val entry = InKeyboardTextEntry()
        entry.begin(request(initialText = "old"))
        entry.clear() shouldBe true
        entry.session.value?.text shouldBe ""
    }

    test("field keys carry the emoji they belong to") {
        InKeyboardTextEntry.emojiTagValue(InKeyboardTextEntry.emojiTag("😔")) shouldBe "😔"
        InKeyboardTextEntry.pinGroupValue(InKeyboardTextEntry.pinGroup("😔")) shouldBe "😔"
        InKeyboardTextEntry.emojiTagValue(InKeyboardTextEntry.pinGroup("😔")).shouldBeNull()
        InKeyboardTextEntry.emojiTagValue(InKeyboardTextEntry.emojiTag("")).shouldBeNull()
        InKeyboardTextEntry.pinGroupValue(InKeyboardTextEntry.EmojiSearch).shouldBeNull()
    }

    test("letter, number, delete, space and enter keys go to the entry") {
        TextEntryKeyPolicy.routeFor('a'.code, KeyType.CHARACTER) shouldBe TextEntryKeyRoute.Text
        TextEntryKeyPolicy.routeFor('7'.code, KeyType.NUMERIC) shouldBe TextEntryKeyRoute.Text
        TextEntryKeyPolicy.routeFor(KeyCode.DELETE, KeyType.ENTER_EDITING) shouldBe TextEntryKeyRoute.DeleteCharacter
        TextEntryKeyPolicy.routeFor(KeyCode.DELETE_WORD, KeyType.ENTER_EDITING) shouldBe TextEntryKeyRoute.DeleteWord
        TextEntryKeyPolicy.routeFor(KeyCode.SPACE, KeyType.CHARACTER) shouldBe TextEntryKeyRoute.Space
        TextEntryKeyPolicy.routeFor(KeyCode.CJK_SPACE, KeyType.CHARACTER) shouldBe TextEntryKeyRoute.Space
        TextEntryKeyPolicy.routeFor(KeyCode.ENTER, KeyType.ENTER_EDITING) shouldBe TextEntryKeyRoute.Submit
        TextEntryKeyPolicy.routeFor(KeyCode.ESCAPE, KeyType.CHARACTER) shouldBe TextEntryKeyRoute.Cancel
    }

    test("keys that would edit the app's text do nothing during an entry") {
        listOf(
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.CLIPBOARD_PASTE,
            KeyCode.CLIPBOARD_SELECT_ALL,
            KeyCode.UNDO,
            KeyCode.FORWARD_DELETE,
            KeyCode.TAB,
        ).forEach { code ->
            TextEntryKeyPolicy.routeFor(code, KeyType.CHARACTER) shouldBe TextEntryKeyRoute.Ignore
        }
    }

    test("panel, voice and hide keys back out of the entry and then do their job") {
        listOf(
            KeyCode.IME_UI_MODE_MEDIA,
            KeyCode.IME_UI_MODE_CLIPBOARD,
            KeyCode.VOICE_INPUT,
            KeyCode.IME_HIDE_UI,
        ).forEach { code ->
            TextEntryKeyPolicy.routeFor(code, KeyType.SYSTEM_GUI) shouldBe TextEntryKeyRoute.CancelThenPassThrough
        }
    }

    test("shift, symbols and language keys keep working on the letter keys") {
        TextEntryKeyPolicy.routeFor(KeyCode.SHIFT, KeyType.MODIFIER) shouldBe TextEntryKeyRoute.PassThrough
        TextEntryKeyPolicy.routeFor(KeyCode.VIEW_SYMBOLS, KeyType.SYSTEM_GUI) shouldBe TextEntryKeyRoute.PassThrough
        TextEntryKeyPolicy.routeFor(KeyCode.LANGUAGE_SWITCH, KeyType.SYSTEM_GUI) shouldBe TextEntryKeyRoute.PassThrough
    }
})
