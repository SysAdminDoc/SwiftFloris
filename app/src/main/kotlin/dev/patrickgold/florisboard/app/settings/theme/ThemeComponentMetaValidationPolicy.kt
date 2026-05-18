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

import dev.patrickgold.florisboard.lib.ext.ExtensionValidation
import dev.patrickgold.florisboard.lib.validate as validateRule
import org.florisboard.lib.color.MaterialYouFlags

internal data class ThemeComponentMetaDraft(
    val id: String,
    val label: String,
    val authors: String,
    val isNightTheme: Boolean,
    val materialYouFlags: MaterialYouFlags,
    val stylesheetPath: String,
)

internal data class ThemeComponentMetaUpdate(
    val id: String,
    val label: String,
    val authors: List<String>,
    val isNightTheme: Boolean,
    val materialYouFlags: MaterialYouFlags,
    val stylesheetPath: String,
)

internal enum class ThemeComponentMetaField {
    Id,
    Label,
    Authors,
    StylesheetPath,
}

internal data class ThemeComponentMetaValidation(
    val invalidFields: Set<ThemeComponentMetaField>,
    val duplicateId: Boolean,
) {
    val fieldsAreValid: Boolean
        get() = invalidFields.isEmpty()

    val canApply: Boolean
        get() = fieldsAreValid && !duplicateId
}

internal object ThemeComponentMetaValidationPolicy {
    fun validate(
        draft: ThemeComponentMetaDraft,
        originalId: String,
        existingThemeIds: Collection<String>,
    ): ThemeComponentMetaValidation {
        val invalidFields = buildSet {
            if (validateRule(ExtensionValidation.ComponentId, draft.id).isInvalid()) add(ThemeComponentMetaField.Id)
            if (validateRule(ExtensionValidation.ComponentLabel, draft.label).isInvalid()) add(ThemeComponentMetaField.Label)
            if (validateRule(ExtensionValidation.ComponentAuthors, draft.authors).isInvalid()) add(ThemeComponentMetaField.Authors)
            if (validateRule(ExtensionValidation.ThemeComponentStylesheetPath, draft.stylesheetPath).isInvalid()) {
                add(ThemeComponentMetaField.StylesheetPath)
            }
        }
        val normalizedId = draft.id.trim()
        val duplicateId = normalizedId != originalId && normalizedId in existingThemeIds
        return ThemeComponentMetaValidation(
            invalidFields = invalidFields,
            duplicateId = duplicateId,
        )
    }

    fun toUpdate(draft: ThemeComponentMetaDraft): ThemeComponentMetaUpdate {
        return ThemeComponentMetaUpdate(
            id = draft.id.trim(),
            label = draft.label.trim(),
            authors = draft.authors.lines().map { it.trim() }.filter { it.isNotBlank() },
            isNightTheme = draft.isNightTheme,
            materialYouFlags = draft.materialYouFlags,
            stylesheetPath = draft.stylesheetPath.trim(),
        )
    }
}
