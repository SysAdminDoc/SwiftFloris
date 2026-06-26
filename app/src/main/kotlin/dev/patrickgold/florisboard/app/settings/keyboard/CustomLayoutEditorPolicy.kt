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

import dev.patrickgold.florisboard.ime.keyboard.AbstractKeyData
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangement
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangementComponent
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.ime.keyboard.LayoutTypeId
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.AutoTextKeyData
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.lib.ext.ExtensionMaintainer
import dev.patrickgold.florisboard.lib.ext.ExtensionMeta
import dev.patrickgold.florisboard.lib.io.DefaultJsonConfig
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class CustomLayoutEditorDraft(
    val layoutId: String,
    val label: String,
    val sourceLabel: String,
    val rows: List<List<CustomLayoutEditorKey>>,
)

@Serializable
internal data class CustomLayoutEditorKey(
    val label: String,
)

internal enum class CustomLayoutEditorValidationError {
    BlankLabel,
    InvalidLayoutId,
    DuplicateLayoutId,
    EmptyLayout,
    TooManyRows,
    EmptyRow,
    TooManyKeys,
    BlankKey,
    MultiCodePointKey,
    ControlKey,
}

internal data class CustomLayoutEditorValidation(
    val errors: Set<CustomLayoutEditorValidationError>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

internal object CustomLayoutEditorPolicy {
    const val MaxRows = 6
    const val MaxKeysPerRow = 16

    private const val ExtensionIdPrefix = "local.swiftfloris.keyboardlayout"
    private const val LocalMaintainer = "SwiftFloris user"
    private val ComponentIdRegex = """^[a-z][a-z0-9_]*${'$'}""".toRegex()

    val ArrangementJsonConfig = Json(DefaultJsonConfig) {
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun newDraftFromArrangement(
        source: LayoutArrangementComponent,
        arrangement: LayoutArrangement,
        existingComponentIds: Set<String>,
    ): Result<CustomLayoutEditorDraft> = runCatching {
        val label = "${source.label} Custom"
        CustomLayoutEditorDraft(
            layoutId = layoutIdForLabel(label, existingComponentIds),
            label = label,
            sourceLabel = source.label,
            rows = arrangement.mapIndexed { rowIndex, row ->
                require(row.isNotEmpty()) { "Row ${rowIndex + 1} is empty." }
                row.mapIndexed { keyIndex, keyData ->
                    keyData.toEditableKey()
                        ?: error("Row ${rowIndex + 1}, key ${keyIndex + 1} is not a simple printable character key.")
                }
            },
        )
    }

    fun layoutIdForLabel(label: String, existingComponentIds: Set<String>): String {
        if (label.isBlank()) {
            return ""
        }
        val base = slugFor(label)
        if (base !in existingComponentIds) {
            return base
        }
        for (n in 2..999) {
            val candidate = "${base}_$n"
            if (candidate !in existingComponentIds) {
                return candidate
            }
        }
        return "${base}_${System.currentTimeMillis()}"
    }

    fun updateLabel(
        draft: CustomLayoutEditorDraft,
        label: String,
        existingComponentIds: Set<String>,
    ): CustomLayoutEditorDraft {
        return draft.copy(label = label, layoutId = layoutIdForLabel(label, existingComponentIds))
    }

    fun updateKey(
        draft: CustomLayoutEditorDraft,
        rowIndex: Int,
        keyIndex: Int,
        label: String,
    ): CustomLayoutEditorDraft {
        return draft.copy(rows = draft.rows.mapIndexed { r, row ->
            if (r != rowIndex) {
                row
            } else {
                row.mapIndexed { k, key ->
                    if (k == keyIndex) key.copy(label = label) else key
                }
            }
        })
    }

    fun moveKey(
        draft: CustomLayoutEditorDraft,
        rowIndex: Int,
        keyIndex: Int,
        delta: Int,
    ): CustomLayoutEditorDraft {
        val row = draft.rows.getOrNull(rowIndex) ?: return draft
        val targetIndex = keyIndex + delta
        if (keyIndex !in row.indices || targetIndex !in row.indices) {
            return draft
        }
        return draft.copy(rows = draft.rows.mapIndexed { r, currentRow ->
            if (r != rowIndex) {
                currentRow
            } else {
                currentRow.toMutableList().also { keys ->
                    val key = keys[keyIndex]
                    keys[keyIndex] = keys[targetIndex]
                    keys[targetIndex] = key
                }
            }
        })
    }

    fun addKeyAfter(
        draft: CustomLayoutEditorDraft,
        rowIndex: Int,
        keyIndex: Int,
    ): CustomLayoutEditorDraft {
        return draft.copy(rows = draft.rows.mapIndexed { r, row ->
            if (r != rowIndex || row.size >= MaxKeysPerRow) {
                row
            } else {
                val insertAt = (keyIndex + 1).coerceIn(0, row.size)
                row.toMutableList().also { it.add(insertAt, CustomLayoutEditorKey("x")) }
            }
        })
    }

    fun removeKey(
        draft: CustomLayoutEditorDraft,
        rowIndex: Int,
        keyIndex: Int,
    ): CustomLayoutEditorDraft {
        return draft.copy(rows = draft.rows.mapIndexed { r, row ->
            if (r != rowIndex || keyIndex !in row.indices) {
                row
            } else {
                row.toMutableList().also { it.removeAt(keyIndex) }
            }
        })
    }

    fun addRow(draft: CustomLayoutEditorDraft): CustomLayoutEditorDraft {
        if (draft.rows.size >= MaxRows) {
            return draft
        }
        return draft.copy(rows = draft.rows + listOf(listOf(CustomLayoutEditorKey("x"))))
    }

    fun removeRow(draft: CustomLayoutEditorDraft, rowIndex: Int): CustomLayoutEditorDraft {
        if (rowIndex !in draft.rows.indices) {
            return draft
        }
        return draft.copy(rows = draft.rows.toMutableList().also { it.removeAt(rowIndex) })
    }

    fun validate(
        draft: CustomLayoutEditorDraft,
        existingComponentIds: Set<String>,
    ): CustomLayoutEditorValidation {
        val errors = mutableSetOf<CustomLayoutEditorValidationError>()
        if (draft.label.isBlank()) {
            errors.add(CustomLayoutEditorValidationError.BlankLabel)
        }
        if (!ComponentIdRegex.matches(draft.layoutId)) {
            errors.add(CustomLayoutEditorValidationError.InvalidLayoutId)
        }
        if (draft.layoutId in existingComponentIds) {
            errors.add(CustomLayoutEditorValidationError.DuplicateLayoutId)
        }
        if (draft.rows.isEmpty()) {
            errors.add(CustomLayoutEditorValidationError.EmptyLayout)
        }
        if (draft.rows.size > MaxRows) {
            errors.add(CustomLayoutEditorValidationError.TooManyRows)
        }
        for (row in draft.rows) {
            if (row.isEmpty()) {
                errors.add(CustomLayoutEditorValidationError.EmptyRow)
            }
            if (row.size > MaxKeysPerRow) {
                errors.add(CustomLayoutEditorValidationError.TooManyKeys)
            }
            for (key in row) {
                val label = key.label.trim()
                when {
                    label.isBlank() -> errors.add(CustomLayoutEditorValidationError.BlankKey)
                    label.singleCodePointOrNull() == null -> {
                        errors.add(CustomLayoutEditorValidationError.MultiCodePointKey)
                    }
                    Character.isISOControl(label.singleCodePointOrNull()!!) ||
                        Character.isWhitespace(label.singleCodePointOrNull()!!) -> {
                        errors.add(CustomLayoutEditorValidationError.ControlKey)
                    }
                }
            }
        }
        return CustomLayoutEditorValidation(errors)
    }

    fun toArrangement(draft: CustomLayoutEditorDraft): LayoutArrangement {
        return draft.rows.map { row ->
            row.map { key ->
                val label = key.label.trim()
                val codePoint = requireNotNull(label.singleCodePointOrNull()) {
                    "Key label must be exactly one code point."
                }
                AutoTextKeyData(code = codePoint, label = label)
            }
        }
    }

    fun encodeArrangement(draft: CustomLayoutEditorDraft): String {
        return ArrangementJsonConfig.encodeToString<LayoutArrangement>(toArrangement(draft))
    }

    fun extensionIdFor(layoutId: String): String {
        return "$ExtensionIdPrefix.$layoutId"
    }

    fun componentNameFor(layoutId: String): ExtensionComponentName {
        return ExtensionComponentName(extensionIdFor(layoutId), layoutId)
    }

    fun arrangementPath(layoutId: String): String {
        return "layouts/${LayoutType.CHARACTERS.id}/$layoutId.json"
    }

    fun buildKeyboardExtension(draft: CustomLayoutEditorDraft): KeyboardExtension {
        val layout = LayoutArrangementComponent(
            id = draft.layoutId,
            label = draft.label.trim(),
            authors = listOf(LocalMaintainer),
            direction = "ltr",
            arrangementFile = arrangementPath(draft.layoutId),
        )
        return KeyboardExtension(
            meta = ExtensionMeta(
                id = extensionIdFor(draft.layoutId),
                version = "1.0.0",
                title = "Custom layout: ${draft.label.trim()}",
                description = "Local keyboard layout created with SwiftFloris.",
                maintainers = listOf(ExtensionMaintainer(LocalMaintainer)),
                license = "NOASSERTION",
            ),
            layouts = mapOf(LayoutTypeId.CHARACTERS to listOf(layout)),
        )
    }

    private fun AbstractKeyData.toEditableKey(): CustomLayoutEditorKey? {
        val data = this as? KeyData ?: return null
        val label = data.label.trim()
        val codePoint = label.singleCodePointOrNull() ?: return null
        if (data.type != KeyType.CHARACTER || data.code <= 0 || data.groupId != KeyData.GROUP_DEFAULT) {
            return null
        }
        if (data.popup != null || Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)) {
            return null
        }
        return CustomLayoutEditorKey(label)
    }

    private fun slugFor(label: String): String {
        val normalized = label.lowercase(Locale.US)
            .map { ch ->
                when (ch) {
                    in 'a'..'z', in '0'..'9' -> ch
                    else -> '_'
                }
            }
            .joinToString(separator = "")
            .replace("_+".toRegex(), "_")
            .trim('_')
            .take(48)
        val withFallback = normalized.ifBlank { "layout" }
        return if (withFallback.first() in 'a'..'z') {
            withFallback
        } else {
            "layout_$withFallback"
        }
    }

    private fun String.singleCodePointOrNull(): Int? {
        if (isEmpty()) {
            return null
        }
        val codePoint = codePointAt(0)
        return if (Character.charCount(codePoint) == length) {
            codePoint
        } else {
            null
        }
    }
}
