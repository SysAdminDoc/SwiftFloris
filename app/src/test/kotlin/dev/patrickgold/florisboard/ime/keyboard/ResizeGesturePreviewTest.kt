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

package dev.patrickgold.florisboard.ime.keyboard

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.ime.window.ImeInsets
import dev.patrickgold.florisboard.ime.window.ImeWindowConfig
import dev.patrickgold.florisboard.ime.window.ImeWindowController
import dev.patrickgold.florisboard.ime.window.ImeWindowMode
import dev.patrickgold.florisboard.ime.window.ImeWindowResizeHandle
import dev.patrickgold.florisboard.ime.window.ImeWindowSpec
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.engine.coroutines.backgroundScope
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.first

/**
 * Issue #23 ("janky" resize): the keys were laid out again on every frame of a resize drag because the row
 * height followed the window. The rows now hold the height the drag began with and the drawn keyboard is
 * stretched by the difference.
 */
class ResizeGesturePreviewTest : FunSpec({
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

    test("a resize drag holds the spec it began with while the live spec follows the finger") {
        fixedController { windowController ->
            val initialSpec = windowController.editor.beginResizeGesture()
            windowController.editor.gestureStartSpec shouldBeSameInstanceAs initialSpec

            val resized = initialSpec.resizedBy(
                DpOffset(0.dp, (-16).dp),
                ImeWindowResizeHandle.TOP,
                imeRowCount,
                smartbarRowCount,
            ).shouldBeInstanceOf<ImeWindowSpec.Fixed>()
            windowController.editor.onSpecUpdated(resized)

            windowController.activeWindowSpec.value shouldBe resized
            windowController.editor.gestureStartSpec shouldBeSameInstanceAs initialSpec
        }
    }

    test("the stretch a top drag shows is the distance dragged") {
        fixedController { windowController ->
            val initialSpec = windowController.editor.beginResizeGesture()
            for (dragDp in listOf(-16, -8, 8, 16)) {
                val resized = initialSpec.resizedBy(
                    DpOffset(0.dp, dragDp.dp),
                    ImeWindowResizeHandle.TOP,
                    imeRowCount,
                    smartbarRowCount,
                )
                val extra = ResizeGesturePreview.extraHeight(
                    start = ImeRowHeights.of(initialSpec),
                    live = ImeRowHeights.of(resized),
                    keyboardRowCount = imeRowCount,
                    smartbarRowCount = smartbarRowCount,
                )
                extra.value shouldBe ((-dragDp).toFloat() plusOrMinus 0.01f)
            }
        }
    }

    test("no drag means no stretch") {
        fixedController { windowController ->
            val spec = windowController.activeWindowSpec.value
            val heights = ImeRowHeights.of(spec)
            ResizeGesturePreview.extraHeight(heights, heights, imeRowCount, smartbarRowCount).value shouldBe 0f
        }
    }

    test("the stretched height adds the extra height and stays inside the constraints") {
        ResizeGesturePreview.stretchedHeightPx(measuredPx = 900, extraPx = 120, minHeightPx = 0, maxHeightPx = 2000) shouldBe 1020
        ResizeGesturePreview.stretchedHeightPx(measuredPx = 900, extraPx = -120, minHeightPx = 0, maxHeightPx = 2000) shouldBe 780
        ResizeGesturePreview.stretchedHeightPx(measuredPx = 900, extraPx = 1500, minHeightPx = 0, maxHeightPx = 2000) shouldBe 2000
        ResizeGesturePreview.stretchedHeightPx(measuredPx = 900, extraPx = -100, minHeightPx = 850, maxHeightPx = 2000) shouldBe 850
        ResizeGesturePreview.stretchedHeightPx(measuredPx = 900, extraPx = -5000, minHeightPx = 0, maxHeightPx = 2000) shouldBe 1
    }
})
