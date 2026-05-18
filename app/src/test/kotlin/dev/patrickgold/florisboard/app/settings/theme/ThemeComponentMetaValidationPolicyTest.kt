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

package dev.patrickgold.florisboard.app.settings.theme

import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.florisboard.lib.color.MaterialYouFlags

class ThemeComponentMetaValidationPolicyTest : FunSpec({
    test("valid theme metadata can be applied after trimming") {
        val flags = MaterialYouFlags(
            paletteStyle = PaletteStyle.entries.last(),
            contrastLevel = Contrast.entries.last(),
            specVersion = ColorSpec.SpecVersion.entries.last(),
        )
        val draft = validDraft().copy(
            id = "aurora_custom",
            label = "  Aurora Custom  ",
            authors = " Alice \n\n Bob ",
            isNightTheme = false,
            materialYouFlags = flags,
            stylesheetPath = " stylesheets/aurora_custom.json ",
        )

        val validation = ThemeComponentMetaValidationPolicy.validate(
            draft = draft,
            originalId = "aurora_old",
            existingThemeIds = listOf("aurora_old", "swift_dark"),
        )
        val update = ThemeComponentMetaValidationPolicy.toUpdate(draft)

        validation.canApply shouldBe true
        update.id shouldBe "aurora_custom"
        update.label shouldBe "Aurora Custom"
        update.authors shouldBe listOf("Alice", "Bob")
        update.isNightTheme shouldBe false
        update.materialYouFlags shouldBe flags
        update.stylesheetPath shouldBe "stylesheets/aurora_custom.json"
    }

    test("invalid theme metadata reports every invalid field") {
        val validation = ThemeComponentMetaValidationPolicy.validate(
            draft = validDraft().copy(
                id = "../bad",
                label = "",
                authors = " \n ",
                stylesheetPath = "bad:path.json",
            ),
            originalId = "existing",
            existingThemeIds = emptyList(),
        )

        validation.invalidFields shouldContainExactlyInAnyOrder listOf(
            ThemeComponentMetaField.Id,
            ThemeComponentMetaField.Label,
            ThemeComponentMetaField.Authors,
            ThemeComponentMetaField.StylesheetPath,
        )
        validation.fieldsAreValid shouldBe false
        validation.canApply shouldBe false
    }

    test("duplicate theme ids are rejected only when changing to another component id") {
        ThemeComponentMetaValidationPolicy.validate(
            draft = validDraft().copy(id = "swift_dark"),
            originalId = "aurora_custom",
            existingThemeIds = listOf("swift_dark", "aurora_custom"),
        ).duplicateId shouldBe true

        ThemeComponentMetaValidationPolicy.validate(
            draft = validDraft().copy(id = "aurora_custom"),
            originalId = "aurora_custom",
            existingThemeIds = listOf("swift_dark", "aurora_custom"),
        ).duplicateId shouldBe false
    }

    test("blank stylesheet path is valid and normalizes to blank for default path fallback") {
        val draft = validDraft().copy(stylesheetPath = "")
        val validation = ThemeComponentMetaValidationPolicy.validate(
            draft = draft,
            originalId = "theme_id",
            existingThemeIds = emptyList(),
        )

        validation.canApply shouldBe true
        ThemeComponentMetaValidationPolicy.toUpdate(draft).stylesheetPath shouldBe ""
    }
})

private fun validDraft(): ThemeComponentMetaDraft {
    return ThemeComponentMetaDraft(
        id = "theme_id",
        label = "Theme",
        authors = "SwiftFloris",
        isNightTheme = true,
        materialYouFlags = MaterialYouFlags(),
        stylesheetPath = "stylesheets/theme_id.json",
    )
}
