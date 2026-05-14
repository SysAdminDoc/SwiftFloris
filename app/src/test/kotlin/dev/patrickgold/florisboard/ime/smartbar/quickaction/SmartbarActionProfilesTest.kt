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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SmartbarActionProfilesTest : FunSpec({
    test("disabled profiles keep the saved arrangement unchanged") {
        val base = arrangement(
            dynamicActions = listOf(action(TextKeyData.SETTINGS)),
            hiddenActions = listOf(action(TextKeyData.CLIPBOARD_PASTE)),
        )
        val editorInfo = editorInfo(
            packageName = "com.slack",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
        )

        SmartbarActionProfiles.apply(base, editorInfo, enabled = false) shouldBe base
    }

    test("password fields prioritize credential-safe actions") {
        val base = arrangement(
            dynamicActions = listOf(action(TextKeyData.UNDO), action(TextKeyData.REDO)),
            hiddenActions = listOf(action(TextKeyData.CLIPBOARD_PASTE), action(TextKeyData.SETTINGS)),
        )
        val editorInfo = editorInfo(
            packageName = "com.example.login",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )

        val result = SmartbarActionProfiles.apply(base, editorInfo, enabled = true)

        SmartbarActionProfiles.detect(editorInfo) shouldBe SmartbarActionProfile.PASSWORD
        result.dynamicActions.take(4) shouldBe listOf(
            action(TextKeyData.CLIPBOARD_PASTE),
            action(TextKeyData.TOGGLE_INCOGNITO_MODE),
            action(TextKeyData.IME_HIDE_UI),
            action(TextKeyData.SETTINGS),
        )
        result.hiddenActions.contains(action(TextKeyData.CLIPBOARD_PASTE)) shouldBe false
    }

    test("chat apps and short-message fields prioritize media and voice actions") {
        val base = arrangement(
            dynamicActions = listOf(action(TextKeyData.SETTINGS), action(TextKeyData.CLIPBOARD_PASTE)),
            hiddenActions = listOf(action(TextKeyData.IME_UI_MODE_MEDIA)),
        )
        val editorInfo = editorInfo(
            packageName = "com.Slack",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
        )

        val result = SmartbarActionProfiles.apply(base, editorInfo, enabled = true)

        SmartbarActionProfiles.detect(editorInfo) shouldBe SmartbarActionProfile.CHAT
        result.dynamicActions.take(5) shouldBe listOf(
            action(TextKeyData.IME_UI_MODE_MEDIA),
            action(TextKeyData.VOICE_INPUT),
            action(TextKeyData.CLIPBOARD_PASTE),
            action(TextKeyData.LANGUAGE_SWITCH),
            action(TextKeyData.TOGGLE_AUTOCORRECT),
        )
        result.hiddenActions.contains(action(TextKeyData.IME_UI_MODE_MEDIA)) shouldBe false
    }

    test("email apps and email fields prioritize editing and paste actions") {
        val base = arrangement(
            dynamicActions = listOf(action(TextKeyData.SETTINGS), action(TextKeyData.UNDO)),
            hiddenActions = listOf(action(TextKeyData.ARROW_LEFT), action(TextKeyData.ARROW_RIGHT)),
        )
        val editorInfo = editorInfo(
            packageName = "com.microsoft.office.outlook",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        )

        val result = SmartbarActionProfiles.apply(base, editorInfo, enabled = true)

        SmartbarActionProfiles.detect(editorInfo) shouldBe SmartbarActionProfile.EMAIL
        result.dynamicActions.take(6) shouldBe listOf(
            action(TextKeyData.CLIPBOARD_PASTE),
            action(TextKeyData.VOICE_INPUT),
            action(TextKeyData.LANGUAGE_SWITCH),
            action(TextKeyData.ARROW_LEFT),
            action(TextKeyData.ARROW_RIGHT),
            action(TextKeyData.SETTINGS),
        )
        result.hiddenActions.contains(action(TextKeyData.ARROW_LEFT)) shouldBe false
        result.hiddenActions.contains(action(TextKeyData.ARROW_RIGHT)) shouldBe false
    }

    test("code apps prioritize tab, esc, arrow, and line-jump actions") {
        // ROADMAP §7 Next-8.1 + Next-8.2 — Termux / JuiceSSH / Acode / etc.
        // get a code-mode smartbar that surfaces Tab + Esc + nav keys so
        // power users don't have to switch to the symbol layer for every
        // keypress that matters in a terminal.
        val base = arrangement(
            dynamicActions = listOf(action(TextKeyData.SETTINGS), action(TextKeyData.CLIPBOARD_PASTE)),
            hiddenActions = listOf(action(TextKeyData.TAB), action(TextKeyData.ESCAPE)),
        )
        val editorInfo = editorInfo(
            packageName = "com.termux",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
        )

        val result = SmartbarActionProfiles.apply(base, editorInfo, enabled = true)

        SmartbarActionProfiles.detect(editorInfo) shouldBe SmartbarActionProfile.CODE
        result.dynamicActions.take(8) shouldBe listOf(
            action(TextKeyData.TAB),
            action(TextKeyData.ESCAPE),
            action(TextKeyData.ARROW_LEFT),
            action(TextKeyData.ARROW_RIGHT),
            action(TextKeyData.ARROW_UP),
            action(TextKeyData.ARROW_DOWN),
            action(TextKeyData.MOVE_START_OF_LINE),
            action(TextKeyData.MOVE_END_OF_LINE),
        )
        result.hiddenActions.contains(action(TextKeyData.TAB)) shouldBe false
        result.hiddenActions.contains(action(TextKeyData.ESCAPE)) shouldBe false
    }

    test("code-mode wins over chat-mode when both could match") {
        // JuiceSSH and Acode have generic SHORT_MESSAGE-like input types
        // but the CODE matcher must fire first so terminal users don't get
        // a chat profile.
        val codeEditor = editorInfo(
            packageName = "com.sonelli.juicessh",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
        )
        SmartbarActionProfiles.detect(codeEditor) shouldBe SmartbarActionProfile.CODE
    }

    test("unmatched apps keep the saved arrangement unchanged") {
        val base = arrangement(
            dynamicActions = listOf(action(TextKeyData.SETTINGS), action(TextKeyData.UNDO)),
            hiddenActions = listOf(action(TextKeyData.REDO)),
        )
        val editorInfo = editorInfo(
            packageName = "com.example.notes",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
        )

        SmartbarActionProfiles.detect(editorInfo) shouldBe null
        SmartbarActionProfiles.apply(base, editorInfo, enabled = true) shouldBe base
    }
})

private fun editorInfo(packageName: String?, inputType: Int): FlorisEditorInfo {
    return FlorisEditorInfo.wrap(
        EditorInfo().apply {
            this.packageName = packageName
            this.inputType = inputType
        }
    )
}

private fun arrangement(
    stickyAction: QuickAction? = null,
    dynamicActions: List<QuickAction>,
    hiddenActions: List<QuickAction>,
): QuickActionArrangement {
    return QuickActionArrangement(
        stickyAction = stickyAction,
        dynamicActions = dynamicActions,
        hiddenActions = hiddenActions,
    )
}

private fun action(data: TextKeyData): QuickAction {
    return QuickAction.InsertKey(data)
}
