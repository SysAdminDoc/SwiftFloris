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

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyType
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TextKeyDataCodePointTest : FunSpec({

    test("builds a labelled key for an ordinary character") {
        val key = TextKeyData.getCodeInfoAsTextKeyData('a'.code)

        key shouldNotBe null
        key!!.label shouldBe "a"
        key.type shouldBe KeyType.CHARACTER
    }

    test("builds a labelled key for a supplementary-plane character") {
        // U+1F600 grinning face — two UTF-16 units, one code point.
        val key = TextKeyData.getCodeInfoAsTextKeyData(0x1F600)

        key shouldNotBe null
        key!!.label shouldBe "😀"
        key.label.length shouldBe 2
    }

    test("rejects code points that cannot render instead of producing a blank key") {
        // Each of these used to yield a key with an empty label: invisible,
        // unlabelled, still pressable, and silent to the layout importer.
        val unrenderable = mapOf(
            "above Unicode range" to (Character.MAX_CODE_POINT + 1),
            "far above Unicode range" to 0x7FFFFFFF,
            "high surrogate" to 0xD800,
            "low surrogate" to 0xDFFF,
        )

        for ((label, code) in unrenderable) {
            withClue("$label (0x${code.toString(16).uppercase()})") {
                TextKeyData.isRenderableCodePoint(code) shouldBe false
                TextKeyData.getCodeInfoAsTextKeyData(code) shouldBe null
            }
        }
    }

    test("accepts the boundary code points either side of the surrogate block") {
        TextKeyData.isRenderableCodePoint(0xD7FF) shouldBe true
        TextKeyData.isRenderableCodePoint(0xE000) shouldBe true
        TextKeyData.isRenderableCodePoint(Character.MAX_CODE_POINT) shouldBe true
    }

    test("still resolves internal keys for non-positive codes") {
        // Internal key codes are zero or negative and must keep resolving
        // through the InternalKeys table rather than the character path.
        // UNSPECIFIED is code 0, so 0 resolves rather than returning null.
        TextKeyData.getCodeInfoAsTextKeyData(0) shouldBe TextKeyData.UNSPECIFIED
        TextKeyData.getCodeInfoAsTextKeyData(
            TextKeyData.UNSPECIFIED.code,
        ) shouldNotBe null
    }
})
