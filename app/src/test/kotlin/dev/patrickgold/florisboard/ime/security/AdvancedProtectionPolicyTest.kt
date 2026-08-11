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

import dev.patrickgold.florisboard.ime.profile.PerAppBooleanOverride
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfilePolicy
import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AdvancedProtectionPolicyTest : FunSpec({

    test("an unrestricted device withdraws nothing") {
        val decision = AdvancedProtectionPolicy.Decision.Unrestricted

        decision.forcesIncognito shouldBe false
        decision.suspendsClipboardHistoryPersistence shouldBe false
        decision.blocksNewEnrolment shouldBe false
    }

    test("Advanced Protection withdraws learning, clipboard retention and enrolment together") {
        val decision = AdvancedProtectionPolicy.Decision(advancedProtectionEnabled = true)

        decision.forcesIncognito shouldBe true
        decision.suspendsClipboardHistoryPersistence shouldBe true
        decision.blocksNewEnrolment shouldBe true
    }

    test("the API guard matches the release that introduced AdvancedProtectionManager") {
        AdvancedProtectionPolicy.MinimumApiLevel shouldBe 36
        AdvancedProtectionPolicy.isSupported(sdkInt = 35) shouldBe false
        AdvancedProtectionPolicy.isSupported(sdkInt = 36) shouldBe true
        AdvancedProtectionPolicy.isSupported(sdkInt = 37) shouldBe true
    }

    test("Advanced Protection outranks a per-app FORCE_OFF incognito override") {
        // The user may have turned incognito off for this app. AAPM is the
        // platform saying they are a target; it wins.
        PerAppKeyboardProfilePolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            globalPreference = IncognitoMode.FORCE_OFF,
            isDynamicIncognitoForced = false,
            override = PerAppBooleanOverride.FORCE_OFF,
            advancedProtectionEnabled = true,
        ) shouldBe true
    }

    test("without Advanced Protection the saved preferences still decide") {
        PerAppKeyboardProfilePolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            globalPreference = IncognitoMode.FORCE_OFF,
            isDynamicIncognitoForced = false,
            override = PerAppBooleanOverride.FORCE_OFF,
            advancedProtectionEnabled = false,
        ) shouldBe false
    }

    test("the incognito default is unchanged for every existing caller") {
        // The parameter defaults to false, so no call site that predates
        // Advanced Protection support changes behaviour.
        PerAppKeyboardProfilePolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = true,
            globalPreference = IncognitoMode.FORCE_OFF,
            isDynamicIncognitoForced = false,
            override = PerAppBooleanOverride.FORCE_OFF,
        ) shouldBe true
    }
})
