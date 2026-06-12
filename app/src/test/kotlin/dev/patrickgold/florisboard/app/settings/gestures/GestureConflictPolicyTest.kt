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

package dev.patrickgold.florisboard.app.settings.gestures

import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GestureConflictPolicyTest : FunSpec({
    test("glide pauses general key swipes and symbol flicks") {
        val summary = GestureConflictPolicy.evaluate(
            baseSnapshot(
                glideEnabled = true,
                symbolFlickEnabled = true,
                hintedSymbolsEnabled = true,
            ),
        )

        summary.glidePausesGeneralKeySwipes shouldBe true
        summary.symbolFlickReady shouldBe false
        summary.symbolFlickPausedByGlide shouldBe true
    }

    test("symbol flick requires hinted symbols") {
        val summary = GestureConflictPolicy.evaluate(
            baseSnapshot(
                symbolFlickEnabled = true,
                hintedSymbolsEnabled = false,
            ),
        )

        summary.symbolFlickReady shouldBe false
        summary.symbolFlickNeedsHintedSymbols shouldBe true
    }

    test("spacebar and delete gestures can be disabled independently") {
        val enabledSummary = GestureConflictPolicy.evaluate(baseSnapshot())
        val disabledSummary = GestureConflictPolicy.evaluate(
            baseSnapshot(
                spaceBarSwipeLeft = SwipeAction.NO_ACTION,
                spaceBarSwipeRight = SwipeAction.NO_ACTION,
                deleteKeySwipeLeft = SwipeAction.NO_ACTION,
                autoReturnAfterApostrophe = false,
            ),
        )

        enabledSummary.spaceBarCursorMovementEnabled shouldBe true
        enabledSummary.deleteSwipeEnabled shouldBe true
        enabledSummary.apostropheAutoReturnEnabled shouldBe true
        disabledSummary.spaceBarCursorMovementEnabled shouldBe false
        disabledSummary.deleteSwipeEnabled shouldBe false
        disabledSummary.apostropheAutoReturnEnabled shouldBe false
    }
})

private fun baseSnapshot(
    glideEnabled: Boolean = false,
    symbolFlickEnabled: Boolean = false,
    hintedSymbolsEnabled: Boolean = true,
    spaceBarSwipeUp: SwipeAction = SwipeAction.NO_ACTION,
    spaceBarSwipeDown: SwipeAction = SwipeAction.NO_ACTION,
    spaceBarSwipeLeft: SwipeAction = SwipeAction.MOVE_CURSOR_LEFT,
    spaceBarSwipeRight: SwipeAction = SwipeAction.MOVE_CURSOR_RIGHT,
    deleteKeySwipeLeft: SwipeAction = SwipeAction.DELETE_WORD,
    autoReturnAfterApostrophe: Boolean = true,
) = GesturePreferenceSnapshot(
    glideEnabled = glideEnabled,
    symbolFlickEnabled = symbolFlickEnabled,
    hintedSymbolsEnabled = hintedSymbolsEnabled,
    spaceBarSwipeUp = spaceBarSwipeUp,
    spaceBarSwipeDown = spaceBarSwipeDown,
    spaceBarSwipeLeft = spaceBarSwipeLeft,
    spaceBarSwipeRight = spaceBarSwipeRight,
    deleteKeySwipeLeft = deleteKeySwipeLeft,
    autoReturnAfterApostrophe = autoReturnAfterApostrophe,
)
