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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ThemeExtensionTrustStatePolicyTest : FunSpec({
    test("theme editor actions are disabled while save is busy") {
        ThemeExtensionTrustStatePolicy.canLeaveEditor(isSaveInProgress = true) shouldBe false
        ThemeExtensionTrustStatePolicy.canMutateEditor(isSaveInProgress = true) shouldBe false

        ThemeExtensionTrustStatePolicy.canLeaveEditor(isSaveInProgress = false) shouldBe true
        ThemeExtensionTrustStatePolicy.canMutateEditor(isSaveInProgress = false) shouldBe true
    }

    test("theme editor notice prioritizes save progress over terminal states") {
        ThemeExtensionTrustStatePolicy.resolveEditNotice(
            isSaveInProgress = true,
            lastTerminalNotice = ThemeExtensionEditNotice.SaveFailure,
        ) shouldBe ThemeExtensionEditNotice.Saving

        ThemeExtensionTrustStatePolicy.resolveEditNotice(
            isSaveInProgress = false,
            lastTerminalNotice = ThemeExtensionEditNotice.ComponentDeleted,
        ) shouldBe ThemeExtensionEditNotice.ComponentDeleted

        ThemeExtensionTrustStatePolicy.resolveEditNotice(
            isSaveInProgress = false,
            lastTerminalNotice = null,
        ) shouldBe ThemeExtensionEditNotice.None
    }

    test("theme extension delete and export actions are disabled while delete is busy") {
        ThemeExtensionTrustStatePolicy.canDeleteExtension(
            extensionCanBeDeleted = true,
            isDeleteInProgress = true,
        ) shouldBe false
        ThemeExtensionTrustStatePolicy.canDeleteExtension(
            extensionCanBeDeleted = true,
            isDeleteInProgress = false,
        ) shouldBe true
        ThemeExtensionTrustStatePolicy.canDeleteExtension(
            extensionCanBeDeleted = false,
            isDeleteInProgress = false,
        ) shouldBe false

        ThemeExtensionTrustStatePolicy.canExportExtension(isDeleteInProgress = true) shouldBe false
        ThemeExtensionTrustStatePolicy.canExportExtension(isDeleteInProgress = false) shouldBe true
    }

    test("theme extension delete notice prioritizes delete progress over terminal state") {
        ThemeExtensionTrustStatePolicy.resolveDeleteNotice(
            isDeleteInProgress = true,
            lastTerminalNotice = ThemeExtensionDeleteNotice.DeleteFailure,
        ) shouldBe ThemeExtensionDeleteNotice.DeleteInProgress

        ThemeExtensionTrustStatePolicy.resolveDeleteNotice(
            isDeleteInProgress = false,
            lastTerminalNotice = ThemeExtensionDeleteNotice.DeleteFailure,
        ) shouldBe ThemeExtensionDeleteNotice.DeleteFailure

        ThemeExtensionTrustStatePolicy.resolveDeleteNotice(
            isDeleteInProgress = false,
            lastTerminalNotice = null,
        ) shouldBe ThemeExtensionDeleteNotice.None
    }
})
