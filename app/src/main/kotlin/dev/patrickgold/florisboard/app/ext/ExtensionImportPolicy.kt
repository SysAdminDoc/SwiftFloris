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

package dev.patrickgold.florisboard.app.ext

import androidx.annotation.StringRes
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.ime.nlp.LanguagePackExtension
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import dev.patrickgold.florisboard.lib.NATIVE_NULLPTR
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.lib.ext.validate

internal enum class ExtensionImportExistingSource {
    None,
    UserInstalled,
    BundledAsset,
}

internal enum class ExtensionImportAction {
    NewInstall,
    Update,
}

internal data class ExtensionImportDecision(
    @param:StringRes val skipReason: Int,
    val action: ExtensionImportAction?,
) {
    val isImportable: Boolean
        get() = skipReason == NATIVE_NULLPTR.toInt()
}

internal object ExtensionImportPolicy {
    fun existingSourceFor(existingExtension: Extension?): ExtensionImportExistingSource {
        return when {
            existingExtension == null -> ExtensionImportExistingSource.None
            existingExtension.sourceRef?.isAssets == true -> ExtensionImportExistingSource.BundledAsset
            else -> ExtensionImportExistingSource.UserInstalled
        }
    }

    fun decideFile(
        fileMatchesFilter: Boolean,
        extension: Extension?,
        requestedType: ExtensionImportScreenType,
        existingSource: ExtensionImportExistingSource,
    ): ExtensionImportDecision {
        return when {
            !fileMatchesFilter -> skipped(R.string.ext__import__file_skip_unsupported)
            extension == null -> skipped(R.string.ext__import__file_skip_ext_corrupted)
            !extension.meta.validate() -> skipped(R.string.ext__import__file_skip_ext_corrupted)
            !extension.matchesRequestedType(requestedType) -> skipped(R.string.ext__import__file_skip_unsupported)
            existingSource == ExtensionImportExistingSource.BundledAsset -> {
                skipped(R.string.ext__import__file_skip_ext_core)
            }
            existingSource == ExtensionImportExistingSource.UserInstalled -> {
                importable(ExtensionImportAction.Update)
            }
            else -> importable(ExtensionImportAction.NewInstall)
        }
    }

    fun canImport(decisions: Iterable<ExtensionImportDecision>): Boolean {
        return decisions.any { it.isImportable }
    }

    private fun Extension.matchesRequestedType(type: ExtensionImportScreenType): Boolean {
        return when (type) {
            ExtensionImportScreenType.EXT_ANY -> true
            ExtensionImportScreenType.EXT_KEYBOARD -> serialType() == KeyboardExtension.SERIAL_TYPE
            ExtensionImportScreenType.EXT_THEME -> serialType() == ThemeExtension.SERIAL_TYPE
            ExtensionImportScreenType.EXT_LANGUAGEPACK -> serialType() == LanguagePackExtension.SERIAL_TYPE
        }
    }

    private fun importable(action: ExtensionImportAction): ExtensionImportDecision {
        return ExtensionImportDecision(
            skipReason = NATIVE_NULLPTR.toInt(),
            action = action,
        )
    }

    private fun skipped(@StringRes reasonId: Int): ExtensionImportDecision {
        return ExtensionImportDecision(
            skipReason = reasonId,
            action = null,
        )
    }
}
