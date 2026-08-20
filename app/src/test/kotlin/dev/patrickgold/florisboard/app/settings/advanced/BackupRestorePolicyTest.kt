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

import androidx.compose.ui.state.ToggleableState
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.io.FileRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BackupRestorePolicyTest : FunSpec({
    val validMetadata = Backup.Metadata(
        packageName = "dev.patrickgold.florisboard.swiftfloris",
        versionCode = 1933,
        versionName = "1.8.133",
        timestamp = 42L,
    )

    test("restore archive copy has a hard provider-stream byte budget") {
        Restore.MAX_ARCHIVE_BYTES shouldBe 256L * 1024L * 1024L
        Restore.MAX_PORTABLE_ARCHIVE_BYTES shouldBe
            Restore.MAX_ARCHIVE_BYTES +
            PortableBackupEnvelope.HeaderBytes +
            PortableBackupEnvelope.GcmTagBytes
    }

    test("clipboard selection is the mandatory portable-encryption boundary") {
        BackupRestorePolicy.requiresPortableEncryption(clipboardItemsSelected = false) shouldBe false
        BackupRestorePolicy.requiresPortableEncryption(clipboardItemsSelected = true) shouldBe true

        Backup.defaultFileName(validMetadata, encrypted = false).endsWith(".zip") shouldBe true
        Backup.defaultFileName(validMetadata, encrypted = true).endsWith(".sfbak") shouldBe true
        FileRegistry.EncryptedBackupArchive.fileExt shouldBe "sfbak"
        FileRegistry.EncryptedBackupArchive.mediaType shouldBe "application/octet-stream"
    }

    test("backup document result distinguishes success cancellation and failure") {
        BackupRestorePolicy.classifyBackupDocumentResult(
            uriSelected = true,
            writeSucceeded = true,
        ) shouldBe BackupDocumentResult.Success
        BackupRestorePolicy.classifyBackupDocumentResult(
            uriSelected = false,
            writeSucceeded = false,
        ) shouldBe BackupDocumentResult.Cancelled
        BackupRestorePolicy.classifyBackupDocumentResult(
            uriSelected = true,
            writeSucceeded = false,
        ) shouldBe BackupDocumentResult.Failure
    }

    test("backup can start only with selected files while idle") {
        BackupRestorePolicy.canStartBackup(
            hasSelectedFiles = true,
            isBackupInProgress = false,
        ) shouldBe true
        BackupRestorePolicy.canStartBackup(
            hasSelectedFiles = false,
            isBackupInProgress = false,
        ) shouldBe false
        BackupRestorePolicy.canStartBackup(
            hasSelectedFiles = true,
            isBackupInProgress = true,
        ) shouldBe false
    }

    test("backup document result maps to terminal flow notices") {
        BackupRestorePolicy.noticeForBackupDocumentResult(BackupDocumentResult.Success) shouldBe
            BackupFlowNotice.Success
        BackupRestorePolicy.noticeForBackupDocumentResult(BackupDocumentResult.Cancelled) shouldBe
            BackupFlowNotice.Cancelled
        BackupRestorePolicy.noticeForBackupDocumentResult(BackupDocumentResult.Failure) shouldBe
            BackupFlowNotice.Failure
    }

    test("backup flow notice prioritizes progress terminal state and clipboard warning") {
        BackupRestorePolicy.resolveBackupFlowNotice(
            isBackupInProgress = true,
            clipboardItemsSelected = true,
            lastTerminalNotice = BackupFlowNotice.Failure,
        ) shouldBe BackupFlowNotice.InProgress

        BackupRestorePolicy.resolveBackupFlowNotice(
            isBackupInProgress = false,
            clipboardItemsSelected = true,
            lastTerminalNotice = BackupFlowNotice.Cancelled,
        ) shouldBe BackupFlowNotice.Cancelled

        BackupRestorePolicy.resolveBackupFlowNotice(
            isBackupInProgress = false,
            clipboardItemsSelected = true,
            lastTerminalNotice = null,
        ) shouldBe BackupFlowNotice.ClipboardPrivacyWarning

        BackupRestorePolicy.resolveBackupFlowNotice(
            isBackupInProgress = false,
            clipboardItemsSelected = false,
            lastTerminalNotice = null,
        ) shouldBe BackupFlowNotice.None
    }

    test("restore archive validation accepts compatible archives with restorable content") {
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata,
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = true,
        ) shouldBe RestoreArchiveValidation(warningId = null, errorId = null)
    }

    test("restore archive validation rejects invalid archives") {
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata.copy(packageName = ""),
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = true,
        ).errorId shouldBe R.string.backup_and_restore__restore__metadata_error_invalid_metadata
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata.copy(versionCode = Restore.MIN_VERSION_CODE - 1),
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = true,
        ).errorId shouldBe R.string.backup_and_restore__restore__metadata_error_invalid_metadata
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata,
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = false,
        ).errorId shouldBe R.string.backup_and_restore__restore__metadata_error_nothing_to_restore
    }

    test("archive format accepts legacy and current versions but rejects future versions") {
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata.copy(
                archiveVersion = Backup.LEGACY_ARCHIVE_FORMAT_VERSION,
            ),
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = true,
        ).errorId shouldBe null
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata.copy(
                archiveVersion = Backup.CURRENT_ARCHIVE_FORMAT_VERSION,
            ),
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = true,
        ).errorId shouldBe null
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata.copy(
                archiveVersion = Backup.CURRENT_ARCHIVE_FORMAT_VERSION + 1,
            ),
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = true,
        ).errorId shouldBe R.string.backup_and_restore__restore__metadata_error_invalid_metadata
    }

    test("restore archive validation accepts both current and legacy application IDs") {
        // The app-ID migration data path: a backup created by the
        // pre-migration install (dev.patrickgold.florisboard[.debug]) must
        // restore into the io.github.sysadmindoc.swiftfloris install without
        // a vendor warning, and vice versa.
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata.copy(packageName = "io.github.sysadmindoc.swiftfloris"),
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = true,
        ) shouldBe RestoreArchiveValidation(warningId = null, errorId = null)
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata.copy(packageName = "dev.patrickgold.florisboard.debug"),
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = true,
        ) shouldBe RestoreArchiveValidation(warningId = null, errorId = null)
    }

    test("restore archive validation warns for version or vendor mismatches") {
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata.copy(versionCode = 1932),
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = true,
        ).warningId shouldBe R.string.backup_and_restore__restore__metadata_warn_different_version
        BackupRestorePolicy.validateRestoreArchive(
            metadata = validMetadata.copy(packageName = "com.example.keyboard"),
            currentVersionCode = 1933,
            minimumVersionCode = Restore.MIN_VERSION_CODE,
            expectedPackagePrefixes = Restore.ACCEPTED_PACKAGE_PREFIXES,
            hasRestorableContent = true,
        ).warningId shouldBe R.string.backup_and_restore__restore__metadata_warn_different_vendor
    }

    test("restore can start only with valid workspace selection while idle") {
        BackupRestorePolicy.canStartRestore(
            hasWorkspace = true,
            restoreErrorId = null,
            hasSelectedFiles = true,
            isRestoreInProgress = false,
        ) shouldBe true
        BackupRestorePolicy.canStartRestore(
            hasWorkspace = false,
            restoreErrorId = null,
            hasSelectedFiles = true,
            isRestoreInProgress = false,
        ) shouldBe false
        BackupRestorePolicy.canStartRestore(
            hasWorkspace = true,
            restoreErrorId = R.string.backup_and_restore__restore__metadata_error_invalid_metadata,
            hasSelectedFiles = true,
            isRestoreInProgress = false,
        ) shouldBe false
        BackupRestorePolicy.canStartRestore(
            hasWorkspace = true,
            restoreErrorId = null,
            hasSelectedFiles = false,
            isRestoreInProgress = false,
        ) shouldBe false
        BackupRestorePolicy.canStartRestore(
            hasWorkspace = true,
            restoreErrorId = null,
            hasSelectedFiles = true,
            isRestoreInProgress = true,
        ) shouldBe false
    }

    test("restore operation result maps to terminal flow notices") {
        BackupRestorePolicy.noticeForRestoreOperationResult(RestoreOperationResult.Success) shouldBe
            RestoreFlowNotice.Success
        BackupRestorePolicy.noticeForRestoreOperationResult(RestoreOperationResult.Cancelled) shouldBe
            RestoreFlowNotice.Cancelled
        BackupRestorePolicy.noticeForRestoreOperationResult(RestoreOperationResult.PartialFailure) shouldBe
            RestoreFlowNotice.PartialFailure
        BackupRestorePolicy.noticeForRestoreOperationResult(RestoreOperationResult.Failure) shouldBe
            RestoreFlowNotice.Failure
    }

    test("restore flow notice prioritizes progress terminal state and erase recovery copy") {
        BackupRestorePolicy.resolveRestoreFlowNotice(
            isRestoreInProgress = true,
            hasWorkspace = false,
            eraseMode = true,
            lastTerminalNotice = RestoreFlowNotice.Failure,
        ) shouldBe RestoreFlowNotice.LoadingArchive

        BackupRestorePolicy.resolveRestoreFlowNotice(
            isRestoreInProgress = true,
            hasWorkspace = true,
            eraseMode = true,
            lastTerminalNotice = RestoreFlowNotice.Failure,
        ) shouldBe RestoreFlowNotice.Restoring

        BackupRestorePolicy.resolveRestoreFlowNotice(
            isRestoreInProgress = false,
            hasWorkspace = true,
            eraseMode = true,
            lastTerminalNotice = RestoreFlowNotice.PartialFailure,
        ) shouldBe RestoreFlowNotice.PartialFailure

        BackupRestorePolicy.resolveRestoreFlowNotice(
            isRestoreInProgress = false,
            hasWorkspace = true,
            eraseMode = true,
            lastTerminalNotice = null,
        ) shouldBe RestoreFlowNotice.EraseRecoveryCopy

        BackupRestorePolicy.resolveRestoreFlowNotice(
            isRestoreInProgress = false,
            hasWorkspace = true,
            eraseMode = false,
            lastTerminalNotice = null,
        ) shouldBe RestoreFlowNotice.None
    }

    test("restore operation classification covers success cancellation partial and failure") {
        BackupRestorePolicy.classifyRestoreOperation(
            selectedSections = 2,
            restoredSections = 2,
            missingSections = 0,
            failedSections = 0,
        ) shouldBe RestoreOperationResult.Success
        BackupRestorePolicy.classifyRestoreOperation(
            selectedSections = 0,
            restoredSections = 0,
            missingSections = 0,
            failedSections = 0,
        ) shouldBe RestoreOperationResult.Cancelled
        BackupRestorePolicy.classifyRestoreOperation(
            selectedSections = 3,
            restoredSections = 2,
            missingSections = 1,
            failedSections = 0,
        ) shouldBe RestoreOperationResult.PartialFailure
        BackupRestorePolicy.classifyRestoreOperation(
            selectedSections = 2,
            restoredSections = 0,
            missingSections = 1,
            failedSections = 1,
        ) shouldBe RestoreOperationResult.Failure
    }

    test("restore operation summary exposes classification and problem count") {
        val summary = RestoreOperationSummary(
            selectedSections = 3,
            restoredSections = 2,
            missingSections = 1,
            failedSections = 0,
            firstFailureMessage = "kept for UI",
        )

        summary.problemSections shouldBe 1
        summary.result shouldBe RestoreOperationResult.PartialFailure
        summary.firstFailureMessage shouldBe "kept for UI"
    }

    test("restore error message falls back when throwable copy is absent or blank") {
        BackupRestorePolicy.restoreErrorMessage(
            error = IllegalStateException("invalid archive"),
            fallbackMessage = "Unknown error",
        ) shouldBe "invalid archive"

        BackupRestorePolicy.restoreErrorMessage(
            error = Throwable(),
            fallbackMessage = "Unknown error",
        ) shouldBe "Unknown error"

        BackupRestorePolicy.restoreErrorMessage(
            error = IllegalArgumentException(" "),
            fallbackMessage = "Unknown error",
        ) shouldBe "Unknown error"
    }

    test("fresh files selector ticks core sections, clipboard off") {
        val selector = Backup.FilesSelector()
        selector.jetprefDatastore shouldBe true
        selector.imeKeyboard shouldBe true
        selector.imeTheme shouldBe true
        selector.localStickerPacks shouldBe true
        selector.snippets shouldBe true
        selector.hardwareKeyboardLayouts shouldBe true
        selector.customEmojiTags shouldBe true
        selector.emojiPinGroups shouldBe true
        selector.clipboardTextItems shouldBe false
        selector.clipboardImageItems shouldBe false
        selector.clipboardVideoItems shouldBe false
        selector.provideClipboardItems() shouldBe false
        selector.atLeastOneSelected() shouldBe true
    }

    test("selectAll ticks every section including clipboard") {
        val selector = Backup.FilesSelector()
        selector.selectAll()
        selector.jetprefDatastore shouldBe true
        selector.imeKeyboard shouldBe true
        selector.imeTheme shouldBe true
        selector.localStickerPacks shouldBe true
        selector.snippets shouldBe true
        selector.hardwareKeyboardLayouts shouldBe true
        selector.customEmojiTags shouldBe true
        selector.emojiPinGroups shouldBe true
        selector.clipboardTextItems shouldBe true
        selector.clipboardImageItems shouldBe true
        selector.clipboardVideoItems shouldBe true
        selector.provideClipboardItems() shouldBe true
        selector.atLeastOneSelected() shouldBe true
        selector.clipboardData.value shouldBe ToggleableState.On
    }

    test("selectAll recovers a fully-deselected selector") {
        val selector = Backup.FilesSelector()
        selector.jetprefDatastore = false
        selector.imeKeyboard = false
        selector.keypressSounds = false
        selector.imeTheme = false
        selector.localStickerPacks = false
        selector.snippets = false
        selector.hardwareKeyboardLayouts = false
        selector.customEmojiTags = false
        selector.emojiPinGroups = false
        selector.atLeastOneSelected() shouldBe false
        selector.selectAll()
        selector.atLeastOneSelected() shouldBe true
    }
})
