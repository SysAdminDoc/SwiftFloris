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

internal enum class ThemeExtensionEditNotice {
    None,
    Saving,
    SaveFailure,
    ComponentDeleted,
}

internal enum class ThemeExtensionDeleteNotice {
    None,
    DeleteInProgress,
    DeleteFailure,
}

internal object ThemeExtensionTrustStatePolicy {
    fun canLeaveEditor(isSaveInProgress: Boolean): Boolean {
        return !isSaveInProgress
    }

    fun canMutateEditor(isSaveInProgress: Boolean): Boolean {
        return !isSaveInProgress
    }

    fun resolveEditNotice(
        isSaveInProgress: Boolean,
        lastTerminalNotice: ThemeExtensionEditNotice?,
    ): ThemeExtensionEditNotice {
        return when {
            isSaveInProgress -> ThemeExtensionEditNotice.Saving
            lastTerminalNotice != null -> lastTerminalNotice
            else -> ThemeExtensionEditNotice.None
        }
    }

    fun canDeleteExtension(
        extensionCanBeDeleted: Boolean,
        isDeleteInProgress: Boolean,
    ): Boolean {
        return extensionCanBeDeleted && !isDeleteInProgress
    }

    fun canExportExtension(isDeleteInProgress: Boolean): Boolean {
        return !isDeleteInProgress
    }

    fun resolveDeleteNotice(
        isDeleteInProgress: Boolean,
        lastTerminalNotice: ThemeExtensionDeleteNotice?,
    ): ThemeExtensionDeleteNotice {
        return when {
            isDeleteInProgress -> ThemeExtensionDeleteNotice.DeleteInProgress
            lastTerminalNotice != null -> lastTerminalNotice
            else -> ThemeExtensionDeleteNotice.None
        }
    }
}
