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

package dev.patrickgold.florisboard.app.settings.advanced

import dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardLayoutImportStatus

internal data class HardwareKeyboardDeviceOption(
    val id: Int,
    val displayName: String,
)

internal enum class PhysicalKeyboardOperation {
    Importing,
    Deleting,
}

internal enum class PhysicalKeyboardNotice {
    None,
    Importing,
    DeleteInProgress,
    ImportSuccess,
    ImportUnsupported,
    ImportNoLayout,
    ImportTooLarge,
    ImportFailure,
    ApplySuccess,
    ApplyFailure,
    DeleteSuccess,
    DeleteFailure,
}

internal object PhysicalKeyboardPolicy {
    fun canStartImport(activeOperation: PhysicalKeyboardOperation?): Boolean {
        return activeOperation == null
    }

    fun canApply(
        selectedLayoutId: String?,
        selectedDeviceId: Int?,
        activeOperation: PhysicalKeyboardOperation?,
    ): Boolean {
        return selectedLayoutId != null && selectedDeviceId != null && activeOperation == null
    }

    fun canDelete(
        selectedLayoutId: String?,
        activeOperation: PhysicalKeyboardOperation?,
    ): Boolean {
        return selectedLayoutId != null && activeOperation == null
    }

    fun importNotice(status: HardwareKeyboardLayoutImportStatus): PhysicalKeyboardNotice {
        return when (status) {
            HardwareKeyboardLayoutImportStatus.Imported -> PhysicalKeyboardNotice.ImportSuccess
            HardwareKeyboardLayoutImportStatus.UnsupportedFileType -> PhysicalKeyboardNotice.ImportUnsupported
            HardwareKeyboardLayoutImportStatus.NoImportableLayout -> PhysicalKeyboardNotice.ImportNoLayout
            HardwareKeyboardLayoutImportStatus.TooLarge -> PhysicalKeyboardNotice.ImportTooLarge
            HardwareKeyboardLayoutImportStatus.ReadFailure -> PhysicalKeyboardNotice.ImportFailure
        }
    }

    fun resolveNotice(
        activeOperation: PhysicalKeyboardOperation?,
        lastTerminalNotice: PhysicalKeyboardNotice?,
    ): PhysicalKeyboardNotice {
        return when (activeOperation) {
            PhysicalKeyboardOperation.Importing -> PhysicalKeyboardNotice.Importing
            PhysicalKeyboardOperation.Deleting -> PhysicalKeyboardNotice.DeleteInProgress
            null -> lastTerminalNotice ?: PhysicalKeyboardNotice.None
        }
    }

    fun defaultSelectedDeviceId(
        devices: List<HardwareKeyboardDeviceOption>,
        currentSelectedDeviceId: Int?,
    ): Int? {
        if (currentSelectedDeviceId != null && devices.any { it.id == currentSelectedDeviceId }) {
            return currentSelectedDeviceId
        }
        return devices.firstOrNull()?.id
    }

    fun defaultSelectedLayoutId(
        layoutIds: List<String>,
        currentSelectedLayoutId: String?,
    ): String? {
        if (currentSelectedLayoutId != null && currentSelectedLayoutId in layoutIds) {
            return currentSelectedLayoutId
        }
        return layoutIds.firstOrNull()
    }
}
