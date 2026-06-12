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

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo

/**
 * ROADMAP §6 N8.1 — 48dp touch-target audit (WCAG 2.5.5 AAA, Material 3 default).
 *
 * The keyboard's per-key height is `defKeyboardHeight / numberOfRows`, where the
 * standard text keyboard has 4 character rows. This regression test pins a
 * conservative target: on a typical 360 × 800 dp phone in portrait, the default
 * keyboard height divided by 4 must remain at or above 48dp.
 *
 * Failure mode this guards against: a future contributor lowers
 * `ImeWindowConstraints.Fixed.Normal.defKeyboardHeight`'s phone-portrait factor
 * (currently 0.26) and silently regresses below the WCAG threshold.
 *
 * Smaller form factors (e.g. 540dp tall in portrait) are intentionally not in
 * scope — those devices already trigger the ROADMAP §6 N5.3 user-facing
 * keyboard-height multiplier slider when a user notices keys are too small.
 */
class TouchTargetWcagTest : FunSpec({
    val typicalPhoneHeightDp = 800
    val typicalPhoneWidthDp = 360
    val keyboardCharacterRows = 4
    val wcagAaaTargetDp = 48f

    test("PHONE_PORTRAIT default keyboard at 360x800dp meets 48dp/key WCAG 2.5.5 AAA") {
        // 1f density / 1f fontScale → 1px == 1dp, so we can pass dp values directly.
        val density = Density(density = 1f, fontScale = 1f)
        val rootInsets = with(density) {
            ImeInsets.Root.of(IntRect(0, 0, typicalPhoneWidthDp, typicalPhoneHeightDp))
        }

        val constraints = ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.NORMAL)
        val perKeyHeight = constraints.defKeyboardHeight.value / keyboardCharacterRows

        perKeyHeight shouldBeGreaterThanOrEqualTo wcagAaaTargetDp
    }

    test("PHONE_PORTRAIT max keyboard at 360x800dp comfortably exceeds 48dp/key") {
        val density = Density(density = 1f, fontScale = 1f)
        val rootInsets = with(density) {
            ImeInsets.Root.of(IntRect(0, 0, typicalPhoneWidthDp, typicalPhoneHeightDp))
        }

        val constraints = ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.NORMAL)
        val perKeyAtMax = constraints.maxKeyboardHeight.value / keyboardCharacterRows

        // At max, keys are well above 48dp — this is a sanity floor on the upper bound.
        perKeyAtMax shouldBeGreaterThanOrEqualTo wcagAaaTargetDp
    }

    test("PHONE_PORTRAIT default visible key surface keeps row gaps compact") {
        val density = Density(density = 1f, fontScale = 1f)
        val rootInsets = with(density) {
            ImeInsets.Root.of(IntRect(0, 0, typicalPhoneWidthDp, typicalPhoneHeightDp))
        }

        val constraints = ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.NORMAL)
        val visibleKeyHeight = constraints.defKeyboardHeight.value / keyboardCharacterRows -
            constraints.defKeyMarginV.value * 2f
        val visibleRowGap = constraints.defKeyMarginV.value * 2f

        visibleKeyHeight shouldBeGreaterThanOrEqualTo wcagAaaTargetDp
        visibleRowGap shouldBeLessThanOrEqualTo 6f
    }

    test("PHONE_LANDSCAPE default keyboard on 800x360dp meets 24dp/key WCAG 2.5.8 AA floor") {
        // Landscape phone is intentionally vertically constrained: WCAG 2.5.5 AAA's
        // 48dp target is impractical when total available height is ~360dp. We hold
        // the line at WCAG 2.5.8 AA's 24dp target, which industry-standard landscape
        // keyboards (SwiftKey, Gboard) also respect. Per-row reality on this size
        // typically lands ~42dp at the default factor (0.47) — comfortable enough.
        val density = Density(density = 1f, fontScale = 1f)
        val rootInsets = with(density) {
            // landscape: width > height
            ImeInsets.Root.of(IntRect(0, 0, typicalPhoneHeightDp, typicalPhoneWidthDp))
        }

        val constraints = ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.NORMAL)
        val perKeyHeight = constraints.defKeyboardHeight.value / keyboardCharacterRows
        val wcagAaTargetDp = 24f

        perKeyHeight shouldBeGreaterThanOrEqualTo wcagAaTargetDp
    }

    test("ResizeHandleTouchSize is at least 48dp (M3 minimum touch target)") {
        val density = Density(density = 1f, fontScale = 1f)
        val rootInsets = with(density) {
            ImeInsets.Root.of(IntRect(0, 0, typicalPhoneWidthDp, typicalPhoneHeightDp))
        }
        val constraints = ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.NORMAL)
        constraints.resizeHandleTouchSize shouldBeGreaterThanOrEqualTo 48.dp
    }

    test("PHONE_PORTRAIT smartbar chrome rows meet the 48dp touch target") {
        val density = Density(density = 1f, fontScale = 1f)
        val rootInsets = with(density) {
            ImeInsets.Root.of(IntRect(0, 0, typicalPhoneWidthDp, typicalPhoneHeightDp))
        }
        val constraints = ImeWindowConstraints.of(rootInsets, ImeWindowMode.Fixed.NORMAL)
        val spec = ImeWindowSpec.Fixed(
            fixedMode = ImeWindowMode.Fixed.NORMAL,
            props = constraints.defaultProps,
            constraints = constraints,
            userPreferredOptions = ImeWindowSpec.UserPreferredOptions(
                keySpacingFactorH = 1f,
                keySpacingFactorV = 1f,
                fontScale = 1f,
            ),
        )

        spec.calcSmartbarRowHeight(spec.props.keyboardHeight) shouldBeGreaterThanOrEqualTo 48.dp
    }
})
