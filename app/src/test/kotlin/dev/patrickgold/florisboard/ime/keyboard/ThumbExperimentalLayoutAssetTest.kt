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

import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.lib.ext.ExtensionJsonConfig
import dev.patrickgold.florisboard.lib.io.loadJsonAsset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

class ThumbExperimentalLayoutAssetTest : FunSpec({
    val assetRoot = keyboardAssetRoot()

    test("default keyboard manifest exposes thumb experimental as an opt-in characters layout") {
        val extension = loadJsonAsset(
            assetRoot.resolve("extension.json").readText(),
            KeyboardExtension.serializer(),
            ExtensionJsonConfig,
        ).getOrThrow()

        val thumbLayout = extension.layouts.getValue(LayoutTypeId.CHARACTERS)
            .first { it.id == ThumbLayoutId }

        thumbLayout.label shouldBe "Thumb Experimental"
        thumbLayout.direction shouldBe "ltr"
        thumbLayout.authors shouldContain "SwiftFloris Contributors"
        thumbLayout.arrangementFile(LayoutType.CHARACTERS) shouldBe
            "layouts/characters/thumb_experimental.json"
    }

    test("thumb experimental layout is a 3-wide large-key character grid with editing controls") {
        val layout = loadJsonAsset<LayoutArrangement>(
            assetRoot.resolve("layouts/characters/thumb_experimental.json").readText(),
        ).getOrThrow()

        layout.map { it.size } shouldContainExactly listOf(3, 3, 3, 3)

        val keys = layout.flatten().map { it as TextKeyData }
        keys.map { it.label } shouldContainExactly listOf(
            "e", "t", "o",
            "a", "h", "n",
            "shift", "c", "delete",
            "view_symbols", "space", "enter",
        )
        keys.map { it.code } shouldContainExactly listOf(
            101, 116, 111,
            97, 104, 110,
            KeyCode.SHIFT, 99, KeyCode.DELETE,
            KeyCode.VIEW_SYMBOLS, KeyCode.SPACE, KeyCode.ENTER,
        )
    }
})

private const val ThumbLayoutId = "thumb_experimental"

private fun keyboardAssetRoot(): File {
    return listOf(
        File("app/src/main/assets/ime/keyboard/org.florisboard.layouts"),
        File("src/main/assets/ime/keyboard/org.florisboard.layouts"),
    ).first { it.exists() }
}
