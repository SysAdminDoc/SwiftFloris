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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExtensionEditorFilesPolicyTest : FunSpec({
    test("file actions block leaving and duplicate actions while busy") {
        ExtensionEditorFilesPolicy.canLeave(isFileActionInProgress = true) shouldBe false
        ExtensionEditorFilesPolicy.canStartFileAction(isFileActionInProgress = true) shouldBe false

        ExtensionEditorFilesPolicy.canLeave(isFileActionInProgress = false) shouldBe true
        ExtensionEditorFilesPolicy.canStartFileAction(isFileActionInProgress = false) shouldBe true
    }

    test("file notice prioritizes active work over terminal state") {
        ExtensionEditorFilesPolicy.resolveNotice(
            isFileActionInProgress = true,
            lastTerminalNotice = ExtensionEditorFileNotice.ImportFailure,
        ) shouldBe ExtensionEditorFileNotice.FileActionInProgress

        ExtensionEditorFilesPolicy.resolveNotice(
            isFileActionInProgress = false,
            lastTerminalNotice = ExtensionEditorFileNotice.DeleteSuccess,
        ) shouldBe ExtensionEditorFileNotice.DeleteSuccess

        ExtensionEditorFilesPolicy.resolveNotice(
            isFileActionInProgress = false,
            lastTerminalNotice = null,
        ) shouldBe ExtensionEditorFileNotice.None
    }

    test("file action results map to visible terminal notices") {
        ExtensionEditorFilesPolicy.importResult(imported = true) shouldBe ExtensionEditorFileNotice.ImportSuccess
        ExtensionEditorFilesPolicy.importResult(imported = false) shouldBe ExtensionEditorFileNotice.ImportFailure

        ExtensionEditorFilesPolicy.renameResult(renamed = true) shouldBe ExtensionEditorFileNotice.RenameSuccess
        ExtensionEditorFilesPolicy.renameResult(renamed = false) shouldBe ExtensionEditorFileNotice.RenameFailure

        ExtensionEditorFilesPolicy.deleteResult(deleted = true) shouldBe ExtensionEditorFileNotice.DeleteSuccess
        ExtensionEditorFilesPolicy.deleteResult(deleted = false) shouldBe ExtensionEditorFileNotice.DeleteFailure
    }
})
