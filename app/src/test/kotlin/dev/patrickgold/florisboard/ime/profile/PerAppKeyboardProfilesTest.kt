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

package dev.patrickgold.florisboard.ime.profile

import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PerAppKeyboardProfilesTest : FunSpec({
    test("upsert stores normalized profile by package name") {
        val raw = PerAppKeyboardProfiles.upsert(
            rawJson = PerAppKeyboardProfiles.EmptyJson,
            profile = PerAppKeyboardProfile(
                packageName = " com.example.notes ",
                label = "",
                theme = PerAppThemeOverride.ADAPTIVE_ACCENT,
                incognito = PerAppBooleanOverride.FORCE_ON,
                clipboardHistory = PerAppBooleanOverride.FORCE_OFF,
                suggestions = PerAppSuggestionAggressiveness.CONSERVATIVE,
                gestureSet = PerAppGestureSet.CODE,
            ),
        )

        val resolved = PerAppKeyboardProfiles.resolve(raw, "com.example.notes")

        resolved?.packageName shouldBe "com.example.notes"
        resolved?.label shouldBe "com.example.notes"
        resolved?.theme shouldBe PerAppThemeOverride.ADAPTIVE_ACCENT
        resolved?.incognito shouldBe PerAppBooleanOverride.FORCE_ON
        resolved?.clipboardHistory shouldBe PerAppBooleanOverride.FORCE_OFF
        resolved?.suggestions shouldBe PerAppSuggestionAggressiveness.CONSERVATIVE
        resolved?.gestureSet shouldBe PerAppGestureSet.CODE
    }

    test("legacy list shape is accepted and normalized") {
        val raw = """
            [
              {
                "packageName": "com.example.chat",
                "label": "Chat",
                "gestureSet": "CHAT"
              }
            ]
        """.trimIndent()

        PerAppKeyboardProfiles.count(raw) shouldBe 1
        PerAppKeyboardProfiles.resolve(raw, "com.example.chat")?.gestureSet shouldBe PerAppGestureSet.CHAT
    }

    test("malformed json falls back to empty profile map") {
        PerAppKeyboardProfiles.count("not-json") shouldBe 0
        PerAppKeyboardProfiles.resolve("not-json", "com.example.notes") shouldBe null
    }

    test("invalid package names are ignored") {
        val raw = PerAppKeyboardProfiles.upsert(
            rawJson = PerAppKeyboardProfiles.EmptyJson,
            profile = PerAppKeyboardProfile(packageName = "com.example/.bad"),
        )

        raw shouldBe PerAppKeyboardProfiles.EmptyJson
    }

    test("app-declared privacy still wins over forced incognito off") {
        PerAppKeyboardProfilePolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = true,
            globalPreference = IncognitoMode.FORCE_OFF,
            isDynamicIncognitoForced = false,
            override = PerAppBooleanOverride.FORCE_OFF,
        ) shouldBe true
    }

    test("profile incognito override wins over global dynamic preference") {
        PerAppKeyboardProfilePolicy.resolveIncognitoMode(
            appDeclaredNoPersonalizedLearning = false,
            globalPreference = IncognitoMode.DYNAMIC_ON_OFF,
            isDynamicIncognitoForced = false,
            override = PerAppBooleanOverride.FORCE_ON,
        ) shouldBe true
    }

    test("suggestion off profile disables composing from an otherwise enabled base") {
        PerAppKeyboardProfilePolicy.shouldEnableComposing(
            baseEnabled = true,
            suggestions = PerAppSuggestionAggressiveness.OFF,
        ) shouldBe false
    }

    test("clipboard force-off suppresses clipboard history retention") {
        PerAppKeyboardProfilePolicy.shouldSuppressClipboardHistory(PerAppBooleanOverride.FORCE_OFF) shouldBe true
        PerAppKeyboardProfilePolicy.shouldSuppressClipboardHistory(PerAppBooleanOverride.FOLLOW_GLOBAL) shouldBe false
    }
})
