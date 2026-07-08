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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class RestoreImportRotationStateContractTest : FunSpec({
    test("restore flow preserves selected archive state across rotation") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreScreen.kt",
        ).readText()

        source shouldContain "rememberSaveable(saver = Backup.FilesSelector.Saver)"
        source shouldContain "var importStrategy by rememberSaveable"
        source shouldContain "var restoreWorkspaceUuid by rememberSaveable"
        source shouldContain "cacheManager.backupAndRestore.getWorkspaceByUuid(uuid)"
        source shouldContain "var lastRestoreNotice by rememberSaveable"
        source shouldContain "var lastRestoreSummary by rememberSaveable(stateSaver = RestoreOperationSummarySaver)"
        source shouldContain "fun closeRestoreWorkspace()"
        source shouldContain "currentActivity?.isChangingConfigurations == true"
        source shouldContain "if (!isConfigurationChange && !currentIsRestoreInProgress)"

        source shouldNotContain "val restoreFilesSelector = remember { Backup.FilesSelector() }"
        source shouldNotContain "var importStrategy by remember { mutableStateOf(ImportStrategy.Merge) }"
        source shouldNotContain "var lastRestoreNotice by remember { mutableStateOf<RestoreFlowNotice?>(null) }"
        source shouldNotContain "var lastRestoreSummary by remember { mutableStateOf<RestoreOperationSummary?>(null) }"
        source shouldNotContain "DisposableEffect(Unit)"
    }

    test("extension import flow preserves picked workspace across rotation") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/ext/ExtensionImportScreen.kt",
        ).readText()

        source shouldContain "var lastImportNotice by rememberSaveable"
        source shouldContain "var lastImportErrorMessage by rememberSaveable"
        source shouldContain "var importWorkspaceUuid by rememberSaveable(initUuid)"
        source shouldContain "cacheManager.importer.getWorkspaceByUuid(it)"
        source shouldContain "fun setImportResult(result: Result<CacheManager.ImporterWorkspace>?)"
        source shouldContain "fun closeImportResult()"
        source shouldContain "DisposableEffect(activity)"
        source shouldContain "currentActivity?.isChangingConfigurations == true"
        source shouldContain "if (!isConfigurationChange && !currentIsPreparingFiles && !currentIsImportInProgress)"

        source shouldNotContain "var lastImportNotice by remember { mutableStateOf<ExtensionImportFlowNotice?>(null) }"
        source shouldNotContain "var lastImportErrorMessage by remember { mutableStateOf<String?>(null) }"
        source shouldNotContain "val workspace = initUuid?.let { cacheManager.importer.getWorkspaceByUuid(it) }"
    }

    test("restore file selector has a saveable state contract") {
        val source = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt",
        ).readText()

        source shouldContain "val Saver = Saver<FilesSelector, ArrayList<Boolean>>"
        source shouldContain "selector.jetprefDatastore"
        source shouldContain "selector.clipboardVideoItems"
        source shouldContain "updateCheckboxState()"
    }
})

private fun locateProjectFile(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.exists() && it.canRead() }
        ?: error("File is not reachable from ${File(".").absolutePath}: $path")
}
