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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first

/**
 * Issue #23 ("janky" resize): every style query in the keyboard depends on the theme font scale, and the
 * font scale follows the keyboard height. Following it during a resize drag restyled every key on every
 * frame. The theme font scale now holds still while the finger is down and catches up when it lifts.
 */
class ImeWindowThemeFontScaleTest : FunSpec({
    val density = Density(2.625f, 1f)
    val imeRowCount = 4
    val smartbarRowCount = 1
    val rootInsets = with(density) { ImeInsets.Root.of(IntRect(0, 0, 1080, 2340)) }

    coroutineTestScope = true

    suspend fun TestScope.fixedController(controllerTest: suspend (ImeWindowController) -> Unit) {
        val prefs by jetprefDataStoreOf(FlorisPreferenceModel::class)
        val windowController = ImeWindowController(prefs, backgroundScope)
        windowController.updateRootInsets(rootInsets)
        val freshConfig = ImeWindowConfig(ImeWindowMode.FIXED, fixedMode = ImeWindowMode.Fixed.NORMAL)
        windowController.updateWindowConfig { freshConfig }
        windowController.activeWindowConfig.first { it == freshConfig }
        windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback }
        controllerTest(windowController)
    }

    fun ImeWindowController.currentThemeFontScale(): Float {
        return themeFontScale(activeWindowSpec.value, editor.state.value)
    }

    test("a resize drag holds the theme font scale until the finger lifts") {
        fixedController { windowController ->
            val before = windowController.currentThemeFontScale()

            val initialSpec = windowController.editor.beginResizeGesture()
            val resized = initialSpec.resizedBy(
                DpOffset(0.dp, (-40).dp),
                ImeWindowResizeHandle.TOP,
                imeRowCount,
                smartbarRowCount,
            ).shouldBeInstanceOf<ImeWindowSpec.Fixed>()
            resized.fontScale shouldNotBe before
            windowController.editor.onSpecUpdated(resized)
            windowController.currentThemeFontScale() shouldBe before

            windowController.editor.endResizeGesture(resized)
            windowController.editor.state.first { !it.isAnyGesture }
            windowController.currentThemeFontScale() shouldBe resized.fontScale
        }
    }

    test("a cancelled resize drag goes back to the font scale it started with") {
        fixedController { windowController ->
            val before = windowController.currentThemeFontScale()

            val initialSpec = windowController.editor.beginResizeGesture()
            val resized = initialSpec.resizedBy(
                DpOffset(0.dp, (-40).dp),
                ImeWindowResizeHandle.TOP,
                imeRowCount,
                smartbarRowCount,
            )
            windowController.editor.onSpecUpdated(resized)
            windowController.currentThemeFontScale() shouldBe before

            windowController.editor.cancelGesture()
            windowController.activeWindowSpec.first { it.fontScale == before }
            windowController.currentThemeFontScale() shouldBe before
        }
    }

    test("a move drag keeps following the window") {
        fixedController { windowController ->
            val initialSpec = windowController.editor.beginMoveGesture()
            windowController.currentThemeFontScale() shouldBe initialSpec.fontScale
            windowController.editor.endMoveGesture(initialSpec)
            windowController.editor.state.first { !it.isAnyGesture }
            windowController.currentThemeFontScale() shouldBe windowController.activeWindowSpec.value.fontScale
        }
    }
})
