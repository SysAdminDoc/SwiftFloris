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

package dev.patrickgold.florisboard.ime.window

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardSplitLayout
import dev.patrickgold.florisboard.plusOrMinus
import dev.patrickgold.florisboard.shouldBeGreaterThanOrEqualTo
import dev.patrickgold.florisboard.shouldBeLessThanOrEqualTo
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Android 17 (API 37) makes the Android 16 large-screen adaptive behavior
 * mandatory by removing the temporary orientation/resizability opt-out. These
 * cases model the tablet/foldable/windowed roots Google documents for the
 * API 36 compat flag and the API 37 target behavior, then assert SwiftFloris'
 * IME window math keeps fixed, split, compact, and floating windows usable.
 */
class AndroidAdaptiveImeWindowTest : FunSpec({
    val tolerance = 1e-3f.dp
    val characterRows = 4
    val wcagTarget = ImeWindowConstraints.MinInteractiveTouchSize

    test("sw600dp foldable inner displays use tablet sizing instead of phone portrait sizing") {
        rootInsets(widthDp = 673, heightDp = 841).formFactor.typeGuess shouldBe
            ImeFormFactor.Type.TABLET_PORTRAIT
        rootInsets(widthDp = 599, heightDp = 900).formFactor.typeGuess shouldBe
            ImeFormFactor.Type.PHONE_PORTRAIT
    }

    test("API 36/37 adaptive roots keep fixed and floating IME windows inside bounds") {
        AndroidAdaptiveScenarios.forEach { scenario ->
            withClue(scenario.description) {
                val rootInsets = rootInsets(scenario.widthDp, scenario.heightDp)

                ImeWindowMode.Fixed.entries.forEach { fixedMode ->
                    val constraints = ImeWindowConstraints.of(rootInsets, fixedMode)
                    val props = constraints.defaultProps
                    val spec = ImeWindowSpec.Fixed(
                        fixedMode = fixedMode,
                        props = props,
                        constraints = constraints,
                        userPreferredOptions = UserOptions,
                    )

                    assertSoftly {
                        props.shouldBeConstrainedTo(constraints, tolerance)
                        if (scenario.enforceTouchTargets) {
                            spec.calcRowHeight(props.keyboardHeight).shouldBeGreaterThanOrEqualTo(wcagTarget)
                            spec.calcSmartbarRowHeight(props.keyboardHeight).shouldBeGreaterThanOrEqualTo(wcagTarget)
                        }
                        props.keyboardWidth(constraints).shouldBeGreaterThanOrEqualTo(constraints.minKeyboardWidth)
                        (props.keyboardHeight + props.paddingBottom)
                            .shouldBeLessThanOrEqualTo(rootInsets.boundsDp.height)
                    }
                }

                ImeWindowMode.Floating.entries.forEach { floatingMode ->
                    val constraints = ImeWindowConstraints.of(rootInsets, floatingMode)
                    val props = constraints.defaultProps
                    val spec = ImeWindowSpec.Floating(
                        floatingMode = floatingMode,
                        props = props,
                        constraints = constraints,
                        userPreferredOptions = UserOptions,
                    )

                    assertSoftly {
                        props.shouldBeConstrainedTo(constraints, tolerance)
                        if (scenario.enforceTouchTargets) {
                            spec.calcRowHeight(props.keyboardHeight).shouldBeGreaterThanOrEqualTo(wcagTarget)
                            spec.calcSmartbarRowHeight(props.keyboardHeight).shouldBeGreaterThanOrEqualTo(wcagTarget)
                        }
                        props.keyboardWidth.shouldBeGreaterThanOrEqualTo(constraints.minKeyboardWidth)
                        (props.offsetLeft + props.keyboardWidth).shouldBeLessThanOrEqualTo(rootInsets.boundsDp.width)
                        (props.offsetBottom + props.keyboardHeight).shouldBeLessThanOrEqualTo(rootInsets.boundsDp.height)
                    }
                }
            }
        }
    }

    test("split mode only activates on sw600dp adaptive roots and preserves reachable key geometry") {
        AndroidAdaptiveScenarios.forEach { scenario ->
            withClue(scenario.description) {
                val rootInsets = rootInsets(scenario.widthDp, scenario.heightDp)
                val constraints = ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.SPLIT)
                    .shouldBeInstanceOf<ImeWindowConstraints.Fixed.Split>()

                constraints.isViable shouldBe scenario.splitExpected

                if (scenario.splitExpected) {
                    val props = constraints.defaultProps
                    val keyboardWidth = props.keyboardWidth(constraints)
                    val gutter = TextKeyboardSplitLayout.gutterPx(
                        keyboardMode = KeyboardMode.CHARACTERS,
                        fixedMode = ImeWindowMode.Fixed.SPLIT,
                        splitViable = constraints.isViable,
                        defaultGutterPx = constraints.defaultGutter.value,
                        keyboardWidthPx = keyboardWidth.value,
                    ).dp
                    val (leftKeys, rightKeys) = SplitKeyboardLayoutCalculator.qwertyBoundary(
                        rowIndex = 0,
                        keyCount = 10,
                    )
                    val row = SplitKeyboardLayoutCalculator.calculateRow(
                        totalWidth = keyboardWidth,
                        gutter = gutter,
                        leftKeyCount = leftKeys,
                        rightKeyCount = rightKeys,
                    )

                    assertSoftly {
                        gutter.shouldBeGreaterThanOrEqualTo(constraints.defaultGutter)
                        row.totalWidth shouldBe keyboardWidth.plusOrMinus(tolerance)
                        row.leftKeyWidth.shouldBeGreaterThanOrEqualTo(wcagTarget)
                        row.rightKeyWidth.shouldBeGreaterThanOrEqualTo(wcagTarget)
                    }
                }
            }
        }
    }

    test("short adaptive windows retain enough vertical space for four key rows") {
        AndroidAdaptiveScenarios
            .filter { it.heightDp <= 700 }
            .forEach { scenario ->
                withClue(scenario.description) {
                    val rootInsets = rootInsets(scenario.widthDp, scenario.heightDp)
                    val constraints = ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.NORMAL)
                    val props = constraints.defaultProps
                    val perRow = props.keyboardHeight / characterRows
                    val visibleKeyHeight = perRow - constraints.defKeyMarginV * 2

                    assertSoftly {
                        perRow.shouldBeGreaterThanOrEqualTo(wcagTarget)
                        visibleKeyHeight.shouldBeGreaterThanOrEqualTo(wcagTarget)
                    }
                }
            }
    }
})

private data class AndroidAdaptiveScenario(
    val label: String,
    val targetApi: Int,
    val widthDp: Int,
    val heightDp: Int,
    val splitExpected: Boolean,
    val enforceTouchTargets: Boolean,
) {
    val description = "$label targetApi=$targetApi ${widthDp}x${heightDp}dp"
}

private val AndroidAdaptiveScenarios = listOf(
    AndroidAdaptiveScenario(
        label = "phone fallback",
        targetApi = 36,
        widthDp = 393,
        heightDp = 852,
        splitExpected = false,
        enforceTouchTargets = false,
    ),
    AndroidAdaptiveScenario(
        label = "tablet portrait universal-resizable",
        targetApi = 36,
        widthDp = 800,
        heightDp = 1280,
        splitExpected = true,
        enforceTouchTargets = true,
    ),
    AndroidAdaptiveScenario(
        label = "tablet landscape universal-resizable",
        targetApi = 36,
        widthDp = 1280,
        heightDp = 800,
        splitExpected = true,
        enforceTouchTargets = true,
    ),
    AndroidAdaptiveScenario(
        label = "foldable inner mandatory adaptive",
        targetApi = 37,
        widthDp = 673,
        heightDp = 841,
        splitExpected = true,
        enforceTouchTargets = true,
    ),
    AndroidAdaptiveScenario(
        label = "desktop window mandatory adaptive",
        targetApi = 37,
        widthDp = 1200,
        heightDp = 700,
        splitExpected = true,
        enforceTouchTargets = true,
    ),
    AndroidAdaptiveScenario(
        label = "narrow multi-window mandatory adaptive",
        targetApi = 37,
        widthDp = 599,
        heightDp = 900,
        splitExpected = false,
        enforceTouchTargets = false,
    ),
)

private val UserOptions = ImeWindowSpec.UserPreferredOptions(
    keySpacingFactorH = 1f,
    keySpacingFactorV = 1f,
    fontScale = 1f,
)

private fun rootInsets(widthDp: Int, heightDp: Int): ImeInsets.Root {
    val boundsDp = DpRect(0.dp, 0.dp, widthDp.dp, heightDp.dp)
    return ImeInsets.Root(
        boundsDp = boundsDp,
        boundsPx = IntRect(0, 0, widthDp, heightDp),
        formFactor = ImeFormFactor.of(boundsDp),
    )
}
