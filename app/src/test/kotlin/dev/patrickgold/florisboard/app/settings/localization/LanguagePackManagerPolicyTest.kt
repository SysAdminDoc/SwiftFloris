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

package dev.patrickgold.florisboard.app.settings.localization

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LanguagePackManagerPolicyTest : FunSpec({
    test("language pack import and delete actions are disabled while deletion is busy") {
        LanguagePackManagerPolicy.canTriggerImport(isDeleteInProgress = false) shouldBe true
        LanguagePackManagerPolicy.canTriggerImport(isDeleteInProgress = true) shouldBe false

        LanguagePackManagerPolicy.canDelete(
            extensionCanBeDeleted = true,
            isDeleteInProgress = false,
        ) shouldBe true
        LanguagePackManagerPolicy.canDelete(
            extensionCanBeDeleted = false,
            isDeleteInProgress = false,
        ) shouldBe false
        LanguagePackManagerPolicy.canDelete(
            extensionCanBeDeleted = true,
            isDeleteInProgress = true,
        ) shouldBe false
    }

    test("language pack manager notice prioritizes delete progress over terminal state") {
        LanguagePackManagerPolicy.resolveNotice(
            isDeleteInProgress = true,
            lastTerminalNotice = LanguagePackManagerNotice.DeleteFailure,
        ) shouldBe LanguagePackManagerNotice.DeleteInProgress

        LanguagePackManagerPolicy.resolveNotice(
            isDeleteInProgress = false,
            lastTerminalNotice = LanguagePackManagerNotice.DeleteSuccess,
        ) shouldBe LanguagePackManagerNotice.DeleteSuccess

        LanguagePackManagerPolicy.resolveNotice(
            isDeleteInProgress = false,
            lastTerminalNotice = null,
        ) shouldBe LanguagePackManagerNotice.None
    }
})
