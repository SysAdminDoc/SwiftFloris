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

package dev.patrickgold.florisboard.ime.window

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.plusOrMinus
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first

/**
 * Issue #23: with the Keyboard height slider away from 100%, every resize or move made the
 * fixed keyboard bigger (or smaller), because the gesture saved the height the slider had
 * already scaled and the next spec computation scaled it again.
 */
class ImeWindowHeightScaleRoundTripTest : FunSpec({
    val tolerance = 1e-3f.dp
    val imeRowCount = 4
    val smartbarRowCount = 1
    val rootInsets = with(Density(2.625f, 1f)) { ImeInsets.Root.of(IntRect(0, 0, 1080, 2340)) }

    coroutineTestScope = true

    suspend fun TestScope.fixedController(percent: Int, controllerTest: suspend (ImeWindowController) -> Unit) {
        val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
        val previousPercent = prefs.keyboard.keyboardHeightMultiplierPortrait.get()
        prefs.keyboard.keyboardHeightMultiplierPortrait.set(percent)
        try {
            val windowController = ImeWindowController(prefs, backgroundScope)
            windowController.updateRootInsets(rootInsets)
            val freshConfig = ImeWindowConfig(ImeWindowMode.FIXED, fixedMode = ImeWindowMode.Fixed.NORMAL)
            windowController.updateWindowConfig { freshConfig }
            windowController.activeWindowConfig.first { it == freshConfig }
            windowController.activeWindowSpec.first {
                it is ImeWindowSpec.Fixed && it.userPreferredOptions.keyboardHeightScale == percent / 100f
            }
            controllerTest(windowController)
        } finally {
            prefs.keyboard.keyboardHeightMultiplierPortrait.set(previousPercent)
        }
    }

    suspend fun savedNormalProps(windowController: ImeWindowController): ImeWindowProps.Fixed {
        return windowController.activeWindowConfig
            .first { ImeWindowMode.Fixed.NORMAL in it.fixedProps }
            .fixedProps.getValue(ImeWindowMode.Fixed.NORMAL)
    }

    for (percent in listOf(80, 120)) {
        val scale = percent / 100f

        test("a resize with the height slider at $percent% saves the height before the slider scales it") {
            fixedController(percent) { windowController ->
                val initialSpec = windowController.editor.beginResizeGesture()
                val resized = initialSpec.resizedBy(
                    DpOffset(0.dp, (-20).dp),
                    ImeWindowResizeHandle.TOP,
                    imeRowCount,
                    smartbarRowCount,
                ).shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                windowController.editor.onSpecUpdated(resized)
                windowController.editor.endResizeGesture(resized)

                val saved = savedNormalProps(windowController)
                (saved.keyboardHeight * scale) shouldBe resized.props.keyboardHeight.plusOrMinus(tolerance)
                saved.paddingBottom shouldBe resized.props.paddingBottom.plusOrMinus(tolerance)
            }
        }

        test("a move with the height slider at $percent% leaves the keyboard height where it was") {
            fixedController(percent) { windowController ->
                val initialSpec = windowController.editor.beginMoveGesture()
                    .shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                val moved = initialSpec.movedBy(DpOffset(0.dp, (-10).dp), imeRowCount, smartbarRowCount)
                    .shouldBeInstanceOf<ImeWindowSpec.Fixed>()
                windowController.editor.onSpecUpdated(moved)
                windowController.editor.endMoveGesture(moved)

                val saved = savedNormalProps(windowController)
                (saved.keyboardHeight * scale) shouldBe initialSpec.props.keyboardHeight.plusOrMinus(tolerance)
            }
        }
    }
})
