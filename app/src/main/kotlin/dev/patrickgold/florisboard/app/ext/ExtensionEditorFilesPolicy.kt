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

internal enum class ExtensionEditorFileNotice {
    None,
    FileActionInProgress,
    ImportSuccess,
    ImportFailure,
    RenameSuccess,
    RenameFailure,
    DeleteSuccess,
    DeleteFailure,
}

internal object ExtensionEditorFilesPolicy {
    fun canLeave(isFileActionInProgress: Boolean): Boolean {
        return !isFileActionInProgress
    }

    fun canStartFileAction(isFileActionInProgress: Boolean): Boolean {
        return !isFileActionInProgress
    }

    fun resolveNotice(
        isFileActionInProgress: Boolean,
        lastTerminalNotice: ExtensionEditorFileNotice?,
    ): ExtensionEditorFileNotice {
        return when {
            isFileActionInProgress -> ExtensionEditorFileNotice.FileActionInProgress
            lastTerminalNotice != null -> lastTerminalNotice
            else -> ExtensionEditorFileNotice.None
        }
    }

    fun importResult(imported: Boolean): ExtensionEditorFileNotice {
        return if (imported) {
            ExtensionEditorFileNotice.ImportSuccess
        } else {
            ExtensionEditorFileNotice.ImportFailure
        }
    }

    fun renameResult(renamed: Boolean): ExtensionEditorFileNotice {
        return if (renamed) {
            ExtensionEditorFileNotice.RenameSuccess
        } else {
            ExtensionEditorFileNotice.RenameFailure
        }
    }

    fun deleteResult(deleted: Boolean): ExtensionEditorFileNotice {
        return if (deleted) {
            ExtensionEditorFileNotice.DeleteSuccess
        } else {
            ExtensionEditorFileNotice.DeleteFailure
        }
    }
}
