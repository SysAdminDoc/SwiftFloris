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

package dev.patrickgold.florisboard.ime.security

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FlagSecurePolicyTest : FunSpec({
    test("plain fields stay screenshotable outside incognito") {
        FlagSecurePolicy.shouldSecureImeWindow(
            isPasswordField = false,
            isAppPrivateField = false,
            isIncognitoMode = false,
        ) shouldBe false
    }

    test("plain fields are secured while incognito is active") {
        FlagSecurePolicy.shouldSecureImeWindow(
            isPasswordField = false,
            isAppPrivateField = false,
            isIncognitoMode = true,
        ) shouldBe true
    }

    test("password fields stay secured even when incognito is inactive") {
        FlagSecurePolicy.shouldSecureImeWindow(
            isPasswordField = true,
            isAppPrivateField = false,
            isIncognitoMode = false,
        ) shouldBe true
    }

    test("password fields stay secured when incognito is active") {
        FlagSecurePolicy.shouldSecureImeWindow(
            isPasswordField = true,
            isAppPrivateField = false,
            isIncognitoMode = true,
        ) shouldBe true
    }

    test("app-private fields stay secured even if incognito is inactive") {
        FlagSecurePolicy.shouldSecureImeWindow(
            isPasswordField = false,
            isAppPrivateField = true,
            isIncognitoMode = false,
        ) shouldBe true
    }
})
