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

    fun noticeForBackupDocumentResult(result: BackupDocumentResult): BackupFlowNotice? {
        return when (result) {
            BackupDocumentResult.Success -> BackupFlowNotice.Success
            BackupDocumentResult.Cancelled -> BackupFlowNotice.Cancelled
            BackupDocumentResult.Failure -> BackupFlowNotice.Failure
        }
    }

    fun resolveBackupFlowNotice(
        isBackupInProgress: Boolean,
        clipboardItemsSelected: Boolean,
        lastTerminalNotice: BackupFlowNotice?,
    ): BackupFlowNotice {
        return when {
            isBackupInProgress -> BackupFlowNotice.InProgress
            lastTerminalNotice != null -> lastTerminalNotice
            clipboardItemsSelected -> BackupFlowNotice.ClipboardPrivacyWarning
            else -> BackupFlowNotice.None
        }
    }

    fun hasRestorableContent(
        hasJetprefDatastore: Boolean,
        hasImeKeyboard: Boolean,
        hasImeTheme: Boolean,
        hasLocalStickerPacks: Boolean,
        hasClipboardTextItems: Boolean,
        hasClipboardImageItems: Boolean,
        hasClipboardVideoItems: Boolean,
    ): Boolean {
        return hasJetprefDatastore ||
            hasImeKeyboard ||
            hasImeTheme ||
            hasLocalStickerPacks ||
            hasClipboardTextItems ||
            hasClipboardImageItems ||
            hasClipboardVideoItems
    }

    fun validateRestoreArchive(
        metadata: Backup.Metadata,
        currentVersionCode: Int,
        minimumVersionCode: Int,
        expectedPackagePrefixes: List<String>,
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
                // Any accepted prefix counts as "same vendor" — the legacy
                // application ID stays accepted so pre-migration backups
                // (and upstream FlorisBoard backups) restore without a
                // misleading vendor warning. This IS the documented
                // old-ID -> new-ID data migration path.
                expectedPackagePrefixes.none { metadata.packageName.startsWith(it) } -> {
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

    fun noticeForRestoreOperationResult(result: RestoreOperationResult): RestoreFlowNotice {
        return when (result) {
            RestoreOperationResult.Success -> RestoreFlowNotice.Success
            RestoreOperationResult.Cancelled -> RestoreFlowNotice.Cancelled
            RestoreOperationResult.PartialFailure -> RestoreFlowNotice.PartialFailure
            RestoreOperationResult.Failure -> RestoreFlowNotice.Failure
        }
    }

    fun resolveRestoreFlowNotice(
        isRestoreInProgress: Boolean,
        hasWorkspace: Boolean,
        eraseMode: Boolean,
        lastTerminalNotice: RestoreFlowNotice?,
    ): RestoreFlowNotice {
        return when {
            isRestoreInProgress && !hasWorkspace -> RestoreFlowNotice.LoadingArchive
            isRestoreInProgress -> RestoreFlowNotice.Restoring
            lastTerminalNotice != null -> lastTerminalNotice
            eraseMode -> RestoreFlowNotice.EraseRecoveryCopy
            else -> RestoreFlowNotice.None
        }
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

    fun restoreErrorMessage(error: Throwable, fallbackMessage: String): String {
        return error.localizedMessage
            ?.takeIf { it.isNotBlank() }
            ?: fallbackMessage
    }
}

internal enum class BackupDocumentResult {
    Success,
    Cancelled,
    Failure,
}

internal enum class BackupFlowNotice {
    None,
    InProgress,
    ClipboardPrivacyWarning,
    Cancelled,
    Failure,
    ShareSheetOpened,
    Success,
}

internal enum class RestoreFlowNotice {
    None,
    LoadingArchive,
    Restoring,
    EraseRecoveryCopy,
    Cancelled,
    Failure,
    PartialFailure,
    Success,
}

internal data class RestoreArchiveValidation(
    val warningId: Int?,
    val errorId: Int?,
)

internal data class RestoreOperationSummary(
    val selectedSections: Int = 0,
    val restoredSections: Int = 0,
    val missingSections: Int = 0,
    val failedSections: Int = 0,
    val firstFailureMessage: String? = null,
) {
    val problemSections: Int
        get() = missingSections + failedSections

    val result: RestoreOperationResult
        get() = BackupRestorePolicy.classifyRestoreOperation(
            selectedSections = selectedSections,
            restoredSections = restoredSections,
            missingSections = missingSections,
            failedSections = failedSections,
        )
}

internal enum class RestoreOperationResult {
    Success,
    Cancelled,
    PartialFailure,
    Failure,
}
