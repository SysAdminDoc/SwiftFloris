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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class PortableBackupScreenContractTest : FunSpec({
    test("clipboard exports seal and delete plaintext before either external destination") {
        val source = projectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt",
        ).readText()
        val builder = projectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupArchiveBuilder.kt",
        ).readText()

        source shouldContain "BackupRestorePolicy.requiresPortableEncryption"
        source shouldContain "BackupArchiveBuilder.build("
        builder shouldContain "snapshotHistoryForRestore()"
        builder shouldContain "PortableBackupEnvelope.encrypt("
        builder shouldContain "ClipboardFileStorage.copyDecryptedTo("
        builder shouldContain "check(plaintextZip.delete())"
        builder shouldContain "check(inputDir.deleteRecursively())"
        builder shouldContain "containsClipboard = selection.containsClipboard"
        source shouldContain "writeFromFile(uri, workspace.archiveFile)"
        source shouldContain "FileProvider.getUriForFile("
        source shouldContain "workspace.archiveFile"
        source shouldContain "cacheManager.leaseSharedBackupArtifact(workspace, uri)"
        source shouldNotContain "writeFromFile(uri, plaintextZip)"
    }

    test("encrypted restore authenticates in staging before any live restore call") {
        val source = projectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreScreen.kt",
        ).readText()

        val decryptIndex = source.indexOf("PortableBackupEnvelope.decrypt(")
        val restoreIndex = source.indexOf("suspend fun performRestore(")
        decryptIndex shouldBeLessThan restoreIndex
        source shouldContain "PortableBackupEnvelope.inspect(copiedWorkspace.archiveFile)"
        source shouldContain "pendingEncryptedWorkspace?.close()"
        source shouldContain "passphrase.fill('\\u0000')"
        source shouldContain "check(zipFile.delete())"
        source shouldContain "workspace.archiveWasLegacyPlaintext = !encrypted"
        source shouldContain "metadata_warn_legacy_plaintext"
    }

    test("restore captures and reapplies an app-private rollback snapshot on every failure path") {
        val restoreSource = projectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreScreen.kt",
        ).readText()
        val rollbackSource = projectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreRollbackSnapshot.kt",
        ).readText()

        val captureIndex = restoreSource.indexOf("RestoreRollbackSnapshot.capture(")
        val mutationIndex = restoreSource.indexOf("performRestore(selection, strategy)")
        captureIndex shouldBeLessThan mutationIndex
        restoreSource shouldContain "withContext(NonCancellable)"
        restoreSource shouldContain "rollbackIfNeeded()"
        restoreSource shouldContain "clearFullHistoryForRestore()"
        restoreSource shouldContain "restoreHistoryAndAwait("
        restoreSource shouldContain "ClipboardFileStorage.insertFileFromBackup("
        restoreSource shouldContain "ClipboardMediaProvider.IMAGE_CLIPS_URI"
        restoreSource shouldContain "ClipboardMediaProvider.VIDEO_CLIPS_URI"
        restoreSource shouldNotContain "clipboardManager.clearFullHistory()"
        restoreSource shouldNotContain "clipboardManager.restoreHistory(items"

        rollbackSource shouldContain "snapshotHistoryForRestore()"
        rollbackSource shouldContain "replaceHistoryFromRollback(history)"
        rollbackSource shouldContain "replaceAllForRestore(fileInfos)"
        rollbackSource shouldContain "ClipboardFileStorage.resetClipboardFileStorage(context)"
        rollbackSource shouldContain "workspace.close()"
    }

    test("passphrase UI is secure and never saveable") {
        val source = projectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupPassphraseDialog.kt",
        ).readText()

        source shouldContain "WindowManager.LayoutParams.FLAG_SECURE"
        source shouldContain "PasswordVisualTransformation()"
        source shouldContain "var passphrase by remember { mutableStateOf(\"\") }"
        source shouldNotContain "var passphrase by rememberSaveable"
    }

    test("shared export lease bounds both URI access and cache lifetime") {
        val source = projectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/lib/cache/CacheManager.kt",
        ).readText()

        source shouldContain "SharedBackupGrantLeaseMillis = 15L * 60L * 1000L"
        source shouldContain "delay(SharedBackupGrantLeaseMillis)"
        source shouldContain "revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)"
        source shouldContain "workspace.close()"
        source shouldContain "val abandonedBackupWorkspaces"
        source shouldContain "staleDir.deleteRecursively()"
    }
})

private fun projectFile(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.isFile }
        ?: error("File is not reachable from ${File(".").absolutePath}: $path")
}
