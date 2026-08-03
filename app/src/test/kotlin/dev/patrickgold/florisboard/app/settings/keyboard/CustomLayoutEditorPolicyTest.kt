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

package dev.patrickgold.florisboard.app.settings.keyboard

import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangement
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangementComponent
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.ime.keyboard.LayoutTypeId
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.AutoTextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.lib.io.loadJsonAsset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class CustomLayoutEditorPolicyTest : FunSpec({
    test("clone creates editable rows from simple printable character keys") {
        val draft = CustomLayoutEditorPolicy.newDraftFromArrangement(
            source = qwertyComponent(),
            arrangement = simpleArrangement(),
            existingComponentIds = setOf("qwerty_custom"),
            defaultLabel = "QWERTY Custom",
        ).getOrThrow()

        draft.layoutId shouldBe "qwerty_custom_2"
        draft.label shouldBe "QWERTY Custom"
        draft.rows.map { row -> row.map { it.label } } shouldBe listOf(
            listOf("q", "w"),
            listOf("a"),
        )
    }

    test("clone rejects non-character layouts instead of silently flattening unsupported keys") {
        CustomLayoutEditorPolicy.newDraftFromArrangement(
            source = qwertyComponent(),
            arrangement = listOf(listOf(TextKeyData.SHIFT)),
            existingComponentIds = emptySet(),
            defaultLabel = "QWERTY Custom",
        ).isFailure shouldBe true
    }

    test("custom layouts retain Page Up and Page Down navigation keys") {
        val draft = CustomLayoutEditorDraft(
            layoutId = "terminal_navigation",
            label = "Terminal navigation",
            sourceLabel = "Terminal",
            rows = listOf(listOf(CustomLayoutEditorKey("Page Up"), CustomLayoutEditorKey("Page Down"))),
        )

        CustomLayoutEditorPolicy.validate(draft, existingComponentIds = emptySet()).isValid shouldBe true
        val decoded = loadJsonAsset<LayoutArrangement>(CustomLayoutEditorPolicy.encodeArrangement(draft)).getOrThrow()
        decoded.single().map { (it as KeyData).code } shouldBe listOf(KeyCode.PAGE_UP, KeyCode.PAGE_DOWN)
    }

    test("cloning a layout with Page Up and Page Down keeps those actions editable") {
        val draft = CustomLayoutEditorPolicy.newDraftFromArrangement(
            source = qwertyComponent(),
            arrangement = listOf(listOf(TextKeyData.PAGE_UP, TextKeyData.PAGE_DOWN)),
            existingComponentIds = emptySet(),
            defaultLabel = "Terminal navigation",
        ).getOrThrow()

        draft.rows.single().map { it.label } shouldBe listOf("Page Up", "Page Down")
    }

    test("editor operations swap add remove and validate keys") {
        val base = CustomLayoutEditorDraft(
            layoutId = "custom",
            label = "Custom",
            sourceLabel = "QWERTY",
            rows = listOf(listOf(CustomLayoutEditorKey("a"), CustomLayoutEditorKey("b"))),
        )

        val swapped = CustomLayoutEditorPolicy.moveKey(base, rowIndex = 0, keyIndex = 1, delta = -1)
        swapped.rows.single().map { it.label } shouldBe listOf("b", "a")

        val added = CustomLayoutEditorPolicy.addKeyAfter(swapped, rowIndex = 0, keyIndex = 0)
        added.rows.single().map { it.label } shouldBe listOf("b", "x", "a")

        val invalid = CustomLayoutEditorPolicy.updateKey(added, rowIndex = 0, keyIndex = 1, label = "xy")
        CustomLayoutEditorPolicy.validate(invalid, existingComponentIds = emptySet()).errors shouldContain
            CustomLayoutEditorValidationError.MultiCodePointKey

        val removed = CustomLayoutEditorPolicy.removeKey(added, rowIndex = 0, keyIndex = 1)
        removed.rows.single().map { it.label } shouldBe listOf("b", "a")
    }

    test("arrangement JSON and keyboard extension metadata round trip through existing models") {
        val draft = CustomLayoutEditorDraft(
            layoutId = "custom",
            label = "Custom",
            sourceLabel = "QWERTY",
            rows = listOf(listOf(CustomLayoutEditorKey("z"))),
        )

        val json = CustomLayoutEditorPolicy.encodeArrangement(draft)
        json.contains("auto_text_key") shouldBe true

        val decoded = loadJsonAsset<LayoutArrangement>(json).getOrThrow()
        val key = decoded.single().single() as KeyData
        key.label shouldBe "z"
        key.code shouldBe 122

        val extension = CustomLayoutEditorPolicy.buildKeyboardExtension(
            draft = draft,
            extensionTitle = "Custom layout: Custom",
            extensionDescription = "Local keyboard layout created with SwiftFloris.",
            localMaintainer = "SwiftFloris user",
        )
        extension.meta.id shouldBe "local.swiftfloris.keyboardlayout.custom"
        extension.meta.title shouldBe "Custom layout: Custom"
        extension.meta.description shouldBe "Local keyboard layout created with SwiftFloris."
        extension.meta.maintainers.single().name shouldBe "SwiftFloris user"
        val layout = extension.layouts.getValue(LayoutTypeId.CHARACTERS).single()
        layout.id shouldBe "custom"
        layout.label shouldBe "Custom"
        layout.arrangementFile(LayoutType.CHARACTERS) shouldBe "layouts/characters/custom.json"
    }
})

private fun qwertyComponent() = LayoutArrangementComponent(
    id = "qwerty",
    label = "QWERTY",
    authors = listOf("SwiftFloris Contributors"),
    direction = "ltr",
)

private fun simpleArrangement(): LayoutArrangement {
    return listOf(
        listOf(
            AutoTextKeyData(code = 113, label = "q"),
            AutoTextKeyData(code = 119, label = "w"),
        ),
        listOf(
            AutoTextKeyData(code = 97, label = "a"),
        ),
    )
}
