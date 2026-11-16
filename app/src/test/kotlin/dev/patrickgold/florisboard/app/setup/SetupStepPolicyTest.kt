/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.setup

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SetupStepPolicyTest : FunSpec({
    test("AI explainer stays first and import hint follows before IME setup") {
        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = false,
            isFlorisBoardSelected = false,
            notificationPermissionState = NotificationPermissionState.NOT_SET,
            aiFeaturesExplainerSeen = false,
            hasKeyboardLanguage = true,
            firstRunImportHintSeen = false,
            supportsNotificationPermission = true,
        ) shouldBe SetupStep.AiFeatures

        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = false,
            isFlorisBoardSelected = false,
            notificationPermissionState = NotificationPermissionState.NOT_SET,
            aiFeaturesExplainerSeen = true,
            hasKeyboardLanguage = true,
            firstRunImportHintSeen = false,
            supportsNotificationPermission = true,
        ) shouldBe SetupStep.ImportDictionary
    }

    test("completed import hint proceeds through the existing setup gates") {
        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = false,
            isFlorisBoardSelected = false,
            notificationPermissionState = NotificationPermissionState.NOT_SET,
            aiFeaturesExplainerSeen = true,
            hasKeyboardLanguage = true,
            firstRunImportHintSeen = true,
            supportsNotificationPermission = true,
        ) shouldBe SetupStep.EnableIme

        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = true,
            isFlorisBoardSelected = false,
            notificationPermissionState = NotificationPermissionState.NOT_SET,
            aiFeaturesExplainerSeen = true,
            hasKeyboardLanguage = true,
            firstRunImportHintSeen = true,
            supportsNotificationPermission = true,
        ) shouldBe SetupStep.SelectIme

        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = true,
            isFlorisBoardSelected = true,
            notificationPermissionState = NotificationPermissionState.NOT_SET,
            aiFeaturesExplainerSeen = true,
            hasKeyboardLanguage = true,
            firstRunImportHintSeen = true,
            supportsNotificationPermission = true,
        ) shouldBe SetupStep.SelectNotification

        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = true,
            isFlorisBoardSelected = true,
            notificationPermissionState = NotificationPermissionState.DENIED,
            aiFeaturesExplainerSeen = true,
            hasKeyboardLanguage = true,
            firstRunImportHintSeen = true,
            supportsNotificationPermission = true,
        ) shouldBe SetupStep.FinishUp
    }

    test("a profile without a keyboard language picks one right after the AI explainer") {
        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = false,
            isFlorisBoardSelected = false,
            notificationPermissionState = NotificationPermissionState.NOT_SET,
            aiFeaturesExplainerSeen = false,
            hasKeyboardLanguage = false,
            firstRunImportHintSeen = false,
            supportsNotificationPermission = true,
        ) shouldBe SetupStep.AiFeatures

        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = false,
            isFlorisBoardSelected = false,
            notificationPermissionState = NotificationPermissionState.NOT_SET,
            aiFeaturesExplainerSeen = true,
            hasKeyboardLanguage = false,
            firstRunImportHintSeen = false,
            supportsNotificationPermission = true,
        ) shouldBe SetupStep.ChooseLanguage

        // Even with the keyboard already enabled and selected, it still needs a language first.
        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = true,
            isFlorisBoardSelected = true,
            notificationPermissionState = NotificationPermissionState.GRANTED,
            aiFeaturesExplainerSeen = true,
            hasKeyboardLanguage = false,
            firstRunImportHintSeen = true,
            supportsNotificationPermission = true,
        ) shouldBe SetupStep.ChooseLanguage

        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = false,
            isFlorisBoardSelected = false,
            notificationPermissionState = NotificationPermissionState.NOT_SET,
            aiFeaturesExplainerSeen = true,
            hasKeyboardLanguage = true,
            firstRunImportHintSeen = false,
            supportsNotificationPermission = true,
        ) shouldBe SetupStep.ImportDictionary
    }

    test("each finished step moves setup to a higher step id, so the layout marks the earlier ones done") {
        val walk = (0..6).map { finished ->
            SetupStepPolicy.nextStep(
                aiFeaturesExplainerSeen = finished > 0,
                hasKeyboardLanguage = finished > 1,
                firstRunImportHintSeen = finished > 2,
                isFlorisBoardEnabled = finished > 3,
                isFlorisBoardSelected = finished > 4,
                notificationPermissionState = if (finished > 5) {
                    NotificationPermissionState.GRANTED
                } else {
                    NotificationPermissionState.NOT_SET
                },
                supportsNotificationPermission = true,
            )
        }
        walk shouldBe SetupStep.entries
        walk.map { it.id } shouldBe walk.map { it.id }.sorted().distinct()
    }

    test("notification step is skipped on platform versions without runtime notification permission") {
        SetupStepPolicy.nextStep(
            isFlorisBoardEnabled = true,
            isFlorisBoardSelected = true,
            notificationPermissionState = NotificationPermissionState.NOT_SET,
            aiFeaturesExplainerSeen = true,
            hasKeyboardLanguage = true,
            firstRunImportHintSeen = true,
            supportsNotificationPermission = false,
        ) shouldBe SetupStep.FinishUp
    }
})
