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

import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EditorCompatibilityPolicyTest : FunSpec({
    test("plain and rich text editors keep the IME composing contract") {
        listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
        ).forEach { inputType ->
            EditorCompatibilityPolicy.snapshot(editorInfo(inputType)).shouldBe(
                EditorCompatibilityPolicy.Snapshot(
                    keyVariation = KeyVariation.NORMAL,
                    allowsImeSuggestions = true,
                    allowsComposing = true,
                ),
            )
        }
    }

    test("web text, email, and URI fields remain text-capable but retain their variations") {
        val cases = mapOf(
            InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT to KeyVariation.NORMAL,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS to KeyVariation.EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS to KeyVariation.EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI to KeyVariation.URI,
        )

        cases.forEach { (variation, keyVariation) ->
            EditorCompatibilityPolicy.snapshot(editorInfo(InputType.TYPE_CLASS_TEXT or variation)).shouldBe(
                EditorCompatibilityPolicy.Snapshot(
                    keyVariation = keyVariation,
                    allowsImeSuggestions = true,
                    allowsComposing = true,
                ),
            )
        }
    }

    test("number and phone editors do not enter the text suggestion pipeline") {
        listOf(
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_PHONE,
        ).forEach { inputType ->
            val snapshot = EditorCompatibilityPolicy.snapshot(editorInfo(inputType))
            snapshot.allowsImeSuggestions shouldBe false
            snapshot.allowsComposing shouldBe false
        }

        EditorCompatibilityPolicy.snapshot(
            editorInfo(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
        ).keyVariation shouldBe KeyVariation.PASSWORD
    }

    test("password variations are sensitive and never compose") {
        listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        ).forEach { variation ->
            val snapshot = EditorCompatibilityPolicy.snapshot(
                editorInfo(InputType.TYPE_CLASS_TEXT or variation),
            )
            snapshot.keyVariation shouldBe KeyVariation.PASSWORD
            snapshot.allowsImeSuggestions shouldBe false
            snapshot.allowsComposing shouldBe false
        }
    }

    test("host completion and no-suggestions flags override the global IME preference") {
        listOf(
            InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE,
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        ).forEach { flags ->
            val snapshot = EditorCompatibilityPolicy.snapshot(
                editorInfo(InputType.TYPE_CLASS_TEXT or flags),
            )
            snapshot.allowsImeSuggestions shouldBe false
            snapshot.allowsComposing shouldBe false
        }
    }

    test("raw input editors have no composing or suggestion contract") {
        EditorCompatibilityPolicy.snapshot(editorInfo(InputType.TYPE_NULL)) shouldBe
            EditorCompatibilityPolicy.Snapshot(
                keyVariation = KeyVariation.NORMAL,
                allowsImeSuggestions = false,
                allowsComposing = false,
            )
    }
})

private fun editorInfo(inputType: Int): FlorisEditorInfo {
    return FlorisEditorInfo.wrap(
        EditorInfo().apply {
            packageName = "com.example.editor"
            this.inputType = inputType
        },
    )
}
