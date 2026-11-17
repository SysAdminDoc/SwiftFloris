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

package dev.patrickgold.florisboard.ime.editor

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.keyboardManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A physical keyboard can type into a field whose input view never starts: a
 * Chrome page focused with the on-screen keyboard hidden gets onStartInput
 * and nothing else. The field's own composing, key-variation and incognito
 * decisions have to come from there, or the previous field's stay in force.
 * A stale "composing off" left a hardware-typed "thier " in Chrome reading
 * "thiertheir ".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EditorInstanceFieldContractTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun startInputWithoutAnInputViewTurnsComposingOnForAPlainTextField() {
        val editor = EditorInstance(context)
        val keyboardManager by context.keyboardManager()
        // Left over from a field that refused composing, like Chrome's address bar.
        keyboardManager.activeState.isComposingEnabled = false
        keyboardManager.activeState.keyVariation = KeyVariation.URI

        editor.handleStartInput(fieldInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT))

        assertTrue(keyboardManager.activeState.isComposingEnabled)
        assertEquals(KeyVariation.NORMAL, keyboardManager.activeState.keyVariation)
    }

    @Test
    fun startInputWithoutAnInputViewTurnsComposingOffForANoSuggestionsField() {
        val editor = EditorInstance(context)
        val keyboardManager by context.keyboardManager()
        keyboardManager.activeState.isComposingEnabled = true

        editor.handleStartInput(
            fieldInfo(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            ),
        )

        assertFalse(keyboardManager.activeState.isComposingEnabled)
        assertEquals(KeyVariation.URI, keyboardManager.activeState.keyVariation)
    }

    @Test
    fun startInputWithoutAnInputViewMarksAPasswordField() {
        val editor = EditorInstance(context)
        val keyboardManager by context.keyboardManager()
        keyboardManager.activeState.keyVariation = KeyVariation.NORMAL
        keyboardManager.activeState.isComposingEnabled = true

        editor.handleStartInput(fieldInfo(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))

        assertEquals(KeyVariation.PASSWORD, keyboardManager.activeState.keyVariation)
        assertFalse(keyboardManager.activeState.isComposingEnabled)
    }

    @Test
    fun startInputWithoutAnInputViewFollowsTheFieldsIncognitoContract() {
        val editor = EditorInstance(context)
        val keyboardManager by context.keyboardManager()
        keyboardManager.activeState.isIncognitoMode = false

        editor.handleStartInput(
            fieldInfo(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING),
        )
        assertTrue(keyboardManager.activeState.isIncognitoMode)

        editor.handleStartInput(fieldInfo(InputType.TYPE_CLASS_TEXT))
        assertFalse(keyboardManager.activeState.isIncognitoMode)
    }

    private fun fieldInfo(inputType: Int, imeOptions: Int = 0): FlorisEditorInfo {
        return FlorisEditorInfo.wrap(
            EditorInfo().apply {
                this.inputType = inputType
                this.imeOptions = imeOptions
                packageName = "com.example.fieldcontract"
                initialSelStart = 0
                initialSelEnd = 0
            },
        )
    }
}
