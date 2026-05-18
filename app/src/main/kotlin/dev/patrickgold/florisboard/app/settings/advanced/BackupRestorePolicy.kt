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

import dev.patrickgold.florisboard.R

internal object BackupRestorePolicy {
    fun classifyBackupDocumentResult(
        uriSelected: Boolean,
        writeSucceeded: Boolean,
    ): BackupDocumentResult {
        return when {
            !uriSelected -> BackupDocumentResult.Cancelled
            writeSucceeded -> BackupDocumentResult.Success
            else -> BackupDocumentResult.Failure
        }
    }

    fun canStartBackup(
        hasSelectedFiles: Boolean,
        isBackupInProgress: Boolean,
    ): Boolean {
        return hasSelectedFiles && !isBackupInProgress
    }

    fun hasRestorableContent(
        hasJetprefDatastore: Boolean,
        hasImeKeyboard: Boolean,
        hasImeTheme: Boolean,
        hasClipboardTextItems: Boolean,
        hasClipboardImageItems: Boolean,
        hasClipboardVideoItems: Boolean,
    ): Boolean {
        return hasJetprefDatastore ||
            hasImeKeyboard ||
            hasImeTheme ||
            hasClipboardTextItems ||
            hasClipboardImageItems ||
            hasClipboardVideoItems
    }

    fun validateRestoreArchive(
        metadata: Backup.Metadata,
        currentVersionCode: Int,
        minimumVersionCode: Int,
        expectedPackagePrefix: String,
        hasRestorableContent: Boolean,
    ): RestoreArchiveValidation {
        val errorId = when {
            metadata.packageName.isBlank() || metadata.versionCode < minimumVersionCode -> {
                R.string.backup_and_restore__restore__metadata_error_invalid_metadata
            }
            !hasRestorableContent -> {
                R.string.backup_and_restore__restore__metadata_error_nothing_to_restore
            }
            else -> null
        }
        val warningId = if (errorId != null) {
            null
        } else {
            when {
                metadata.versionCode != currentVersionCode -> {
                    R.string.backup_and_restore__restore__metadata_warn_different_version
                }
                !metadata.packageName.startsWith(expectedPackagePrefix) -> {
                    R.string.backup_and_restore__restore__metadata_warn_different_vendor
                }
                else -> null
            }
        }
        return RestoreArchiveValidation(warningId = warningId, errorId = errorId)
    }

    fun canStartRestore(
        hasWorkspace: Boolean,
        restoreErrorId: Int?,
        hasSelectedFiles: Boolean,
        isRestoreInProgress: Boolean,
    ): Boolean {
        return hasWorkspace &&
            restoreErrorId == null &&
            hasSelectedFiles &&
            !isRestoreInProgress
    }

    fun classifyRestoreOperation(
        selectedSections: Int,
        restoredSections: Int,
        missingSections: Int,
        failedSections: Int,
    ): RestoreOperationResult {
        val problemCount = missingSections + failedSections
        return when {
            selectedSections <= 0 -> RestoreOperationResult.Cancelled
            restoredSections > 0 && problemCount == 0 -> RestoreOperationResult.Success
            restoredSections > 0 && problemCount > 0 -> RestoreOperationResult.PartialFailure
            else -> RestoreOperationResult.Failure
        }
    }
}

internal enum class BackupDocumentResult {
    Success,
    Cancelled,
    Failure,
}

internal data class RestoreArchiveValidation(
    val warningId: Int?,
    val errorId: Int?,
)

internal enum class RestoreOperationResult {
    Success,
    Cancelled,
    PartialFailure,
    Failure,
}
