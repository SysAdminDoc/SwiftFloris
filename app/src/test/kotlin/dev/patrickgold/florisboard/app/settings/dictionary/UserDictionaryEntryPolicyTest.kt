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

package dev.patrickgold.florisboard.app.settings.dictionary

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UserDictionaryEntryPolicyTest : FunSpec({
    test("entry mutations block leaving and duplicate entry actions while busy") {
        UserDictionaryEntryPolicy.canLeave(isOperationInProgress = true) shouldBe false
        UserDictionaryEntryPolicy.canMutateEntry(isOperationInProgress = true) shouldBe false
        UserDictionaryEntryPolicy.canStartTransfer(
            isOperationInProgress = true,
            isTransferInProgress = false,
        ) shouldBe false

        UserDictionaryEntryPolicy.canLeave(isOperationInProgress = false) shouldBe true
        UserDictionaryEntryPolicy.canMutateEntry(isOperationInProgress = false) shouldBe true
        UserDictionaryEntryPolicy.canStartTransfer(
            isOperationInProgress = false,
            isTransferInProgress = false,
        ) shouldBe true
    }

    test("dictionary transfers block leaving entry mutations and duplicate transfers") {
        UserDictionaryEntryPolicy.canLeave(
            isOperationInProgress = false,
            isTransferInProgress = true,
        ) shouldBe false
        UserDictionaryEntryPolicy.canMutateEntry(
            isOperationInProgress = false,
            isTransferInProgress = true,
        ) shouldBe false
        UserDictionaryEntryPolicy.canStartTransfer(
            isOperationInProgress = false,
            isTransferInProgress = true,
        ) shouldBe false
    }

    test("entry notice prioritizes active save and delete work") {
        UserDictionaryEntryPolicy.resolveNotice(
            activeOperation = UserDictionaryEntryOperation.Saving,
            lastTerminalNotice = UserDictionaryEntryNotice.DeleteFailure,
        ) shouldBe UserDictionaryEntryNotice.Saving

        UserDictionaryEntryPolicy.resolveNotice(
            activeOperation = UserDictionaryEntryOperation.Deleting,
            lastTerminalNotice = UserDictionaryEntryNotice.SaveSuccess,
        ) shouldBe UserDictionaryEntryNotice.Deleting

        UserDictionaryEntryPolicy.resolveNotice(
            activeOperation = null,
            lastTerminalNotice = UserDictionaryEntryNotice.DeleteSuccess,
        ) shouldBe UserDictionaryEntryNotice.DeleteSuccess

        UserDictionaryEntryPolicy.resolveNotice(
            activeOperation = null,
            lastTerminalNotice = null,
        ) shouldBe UserDictionaryEntryNotice.None
    }

    test("dictionary transfer notice follows active import and export work") {
        UserDictionaryEntryPolicy.resolveTransferNotice(
            activeOperation = UserDictionaryTransferOperation.Importing,
        ) shouldBe UserDictionaryTransferNotice.Importing

        UserDictionaryEntryPolicy.resolveTransferNotice(
            activeOperation = UserDictionaryTransferOperation.Exporting,
        ) shouldBe UserDictionaryTransferNotice.Exporting

        UserDictionaryEntryPolicy.resolveTransferNotice(
            activeOperation = null,
        ) shouldBe UserDictionaryTransferNotice.None
    }

    test("entry operation results map to terminal notices") {
        UserDictionaryEntryPolicy.saveResult(saved = true) shouldBe UserDictionaryEntryNotice.SaveSuccess
        UserDictionaryEntryPolicy.saveResult(saved = false) shouldBe UserDictionaryEntryNotice.SaveFailure

        UserDictionaryEntryPolicy.deleteResult(deleted = true) shouldBe UserDictionaryEntryNotice.DeleteSuccess
        UserDictionaryEntryPolicy.deleteResult(deleted = false) shouldBe UserDictionaryEntryNotice.DeleteFailure
    }
})
