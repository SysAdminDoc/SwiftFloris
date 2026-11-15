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
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first

/**
 * Issue #23 ("janky" resize): in fixed mode the content inset the app behind the keyboard lays itself out
 * against followed the window on every frame of a drag, so the app re-laid itself out once per frame.
 * The inset now holds the value from before the gesture and moves once, when the finger lifts.
 */
class ImeWindowGestureInsetsTest : FunSpec({
    val density = Density(2.625f, 1f)
    val imeRowCount = 4
    val smartbarRowCount = 1
    val rootInsets = with(density) { ImeInsets.Root.of(IntRect(0, 0, 1080, 2340)) }

    coroutineTestScope = true

    fun windowInsetsWithTop(top: Int): ImeInsets.Window {
        return with(density) { ImeInsets.Window.of(IntRect(0, top, 1080, 2340)) }
    }

    suspend fun TestScope.fixedController(controllerTest: suspend (ImeWindowController) -> Unit) {
        val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
        val windowController = ImeWindowController(prefs, backgroundScope)
        windowController.updateRootInsets(rootInsets)
        val freshConfig = ImeWindowConfig(ImeWindowMode.FIXED, fixedMode = ImeWindowMode.Fixed.NORMAL)
        windowController.updateWindowConfig { freshConfig }
        windowController.activeWindowConfig.first { it == freshConfig }
        windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
        windowController.updateWindowInsets(windowInsetsWithTop(1500))
        controllerTest(windowController)
    }

    test("a fixed resize drag keeps the reported inset until the finger lifts") {
        fixedController { windowController ->
            windowController.reportedInsetsTop() shouldBe 1500

            val initialSpec = windowController.editor.beginResizeGesture()
            val resized = initialSpec.resizedBy(
                DpOffset(0.dp, (-40).dp),
                ImeWindowResizeHandle.TOP,
                imeRowCount,
                smartbarRowCount,
            ).shouldBeInstanceOf<ImeWindowSpec.Fixed>()
            windowController.editor.onSpecUpdated(resized)
            windowController.updateWindowInsets(windowInsetsWithTop(1395))
            windowController.reportedInsetsTop() shouldBe 1500

            windowController.editor.endResizeGesture(resized)
            windowController.editor.state.first { !it.isAnyGesture }
            windowController.reportedInsetsTop() shouldBe 1395
        }
    }

    test("a fixed move drag keeps the reported inset until the finger lifts") {
        fixedController { windowController ->
            val initialSpec = windowController.editor.beginMoveGesture()
                .shouldBeInstanceOf<ImeWindowSpec.Fixed>()
            val moved = initialSpec.movedBy(DpOffset(0.dp, (-20).dp), imeRowCount, smartbarRowCount)
                .shouldBeInstanceOf<ImeWindowSpec.Fixed>()
            windowController.editor.onSpecUpdated(moved)
            windowController.updateWindowInsets(windowInsetsWithTop(1448))
            windowController.reportedInsetsTop() shouldBe 1500

            windowController.editor.endMoveGesture(moved)
            windowController.editor.state.first { !it.isAnyGesture }
            windowController.reportedInsetsTop() shouldBe 1448
        }
    }

    test("a cancelled drag goes back to following the window") {
        fixedController { windowController ->
            windowController.editor.beginResizeGesture()
            windowController.updateWindowInsets(windowInsetsWithTop(1420))
            windowController.reportedInsetsTop() shouldBe 1500

            windowController.editor.cancelGesture()
            windowController.reportedInsetsTop() shouldBe 1420
        }
    }
})
