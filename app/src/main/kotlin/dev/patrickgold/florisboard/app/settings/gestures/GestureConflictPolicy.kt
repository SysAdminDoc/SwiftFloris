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

data class GesturePreferenceSnapshot(
    val glideEnabled: Boolean,
    val symbolFlickEnabled: Boolean,
    val hintedSymbolsEnabled: Boolean,
    val spaceBarSwipeUp: SwipeAction,
    val spaceBarSwipeDown: SwipeAction,
    val spaceBarSwipeLeft: SwipeAction,
    val spaceBarSwipeRight: SwipeAction,
    val deleteKeySwipeLeft: SwipeAction,
    val autoReturnAfterApostrophe: Boolean,
)

data class GestureConflictSummary(
    val glidePausesGeneralKeySwipes: Boolean,
    val symbolFlickReady: Boolean,
    val symbolFlickNeedsHintedSymbols: Boolean,
    val symbolFlickPausedByGlide: Boolean,
    val spaceBarCursorMovementEnabled: Boolean,
    val deleteSwipeEnabled: Boolean,
    val apostropheAutoReturnEnabled: Boolean,
)

object GestureConflictPolicy {
    fun evaluate(snapshot: GesturePreferenceSnapshot): GestureConflictSummary {
        return GestureConflictSummary(
            glidePausesGeneralKeySwipes = snapshot.glideEnabled,
            symbolFlickReady = snapshot.symbolFlickEnabled &&
                snapshot.hintedSymbolsEnabled &&
                !snapshot.glideEnabled,
            symbolFlickNeedsHintedSymbols = snapshot.symbolFlickEnabled &&
                !snapshot.hintedSymbolsEnabled,
            symbolFlickPausedByGlide = snapshot.symbolFlickEnabled &&
                snapshot.hintedSymbolsEnabled &&
                snapshot.glideEnabled,
            spaceBarCursorMovementEnabled = listOf(
                snapshot.spaceBarSwipeUp,
                snapshot.spaceBarSwipeDown,
                snapshot.spaceBarSwipeLeft,
                snapshot.spaceBarSwipeRight,
            ).any { it.isCursorMovementAction },
            deleteSwipeEnabled = snapshot.deleteKeySwipeLeft != SwipeAction.NO_ACTION,
            apostropheAutoReturnEnabled = snapshot.autoReturnAfterApostrophe,
        )
    }
}

private val SwipeAction.isCursorMovementAction: Boolean
    get() = when (this) {
        SwipeAction.MOVE_CURSOR_UP,
        SwipeAction.MOVE_CURSOR_DOWN,
        SwipeAction.MOVE_CURSOR_LEFT,
        SwipeAction.MOVE_CURSOR_RIGHT,
        SwipeAction.MOVE_CURSOR_START_OF_LINE,
        SwipeAction.MOVE_CURSOR_END_OF_LINE,
        SwipeAction.MOVE_CURSOR_START_OF_PAGE,
        SwipeAction.MOVE_CURSOR_END_OF_PAGE -> true
        else -> false
    }
