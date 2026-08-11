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

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * The floating window can be dragged and resized by the user. HeliBoard has
 * three open reports (#2725, #2709, #2708) where that ends in a window the user
 * cannot recover from inside the keyboard: dragged mostly off-screen, or shrunk
 * until the move and resize handles are unreachable.
 *
 * SwiftFloris clamps in `ImeWindowProps.Floating.constrained`, and both
 * [ImeWindowSpec.Floating.movedBy] and [ImeWindowSpec.Floating.resizedBy] route
 * through it. These are the property tests that say so for *any* sequence of
 * gestures rather than for the handful someone thought to write down — a single
 * unclamped path is all it takes to strand the user.
 */
class FloatingWindowReachabilityTest : FunSpec({

    fun rootInsets(widthDp: Int, heightDp: Int): ImeInsets.Root {
        val boundsDp = DpRect(0.dp, 0.dp, widthDp.dp, heightDp.dp)
        return ImeInsets.Root(
            boundsDp = boundsDp,
            boundsPx = IntRect(0, 0, widthDp, heightDp),
            formFactor = ImeFormFactor.of(boundsDp),
        )
    }

    fun floatingSpec(widthDp: Int, heightDp: Int): ImeWindowSpec.Floating {
        val constraints = ImeWindowConstraints.of(
            rootInsets = rootInsets(widthDp, heightDp),
            floatingMode = ImeWindowMode.Floating.NORMAL,
        )
        return ImeWindowSpec.Floating(
            floatingMode = ImeWindowMode.Floating.NORMAL,
            props = constraints.defaultProps,
            constraints = constraints,
            userPreferredOptions = ImeWindowSpec.UserPreferredOptions(
                keySpacingFactorH = 1f,
                keySpacingFactorV = 1f,
                fontScale = 1f,
            ),
        )
    }

    /** Every invariant that has to hold after any gesture, checked together. */
    fun ImeWindowSpec.assertOnScreen(clue: String) {
        val spec = this as ImeWindowSpec.Floating
        val props = spec.props
        val bounds = spec.constraints.rootBounds

        withClue("$clue -> $props (root ${bounds.width} x ${bounds.height})") {
            // Never off the left or bottom edge...
            (props.offsetLeft >= 0.dp) shouldBe true
            (props.offsetBottom >= 0.dp) shouldBe true
            // ...nor past the right or top edge, which is what strands the
            // move handle and the resize corner beyond the user's reach.
            (props.offsetLeft + props.keyboardWidth <= bounds.width + Tolerance) shouldBe true
            (props.offsetBottom + props.keyboardHeight <= bounds.height + Tolerance) shouldBe true
            // Never shrunk below what the constraints allow, so the handles
            // keep a touchable footprint.
            (props.keyboardWidth >= spec.constraints.minKeyboardWidth - Tolerance) shouldBe true
            (props.keyboardHeight >= spec.constraints.minKeyboardHeight - Tolerance) shouldBe true
            (props.keyboardWidth <= spec.constraints.maxKeyboardWidth + Tolerance) shouldBe true
            (props.keyboardHeight <= spec.constraints.maxKeyboardHeight + Tolerance) shouldBe true
        }
    }

    test("no sequence of drags can move the window off screen") {
        checkAll(
            Arb.int(320..1400),
            Arb.int(480..1600),
            Arb.list(Arb.double(-4000.0, 4000.0), 1..12),
            Arb.list(Arb.double(-4000.0, 4000.0), 1..12),
        ) { width, height, dxs, dys ->
            var spec: ImeWindowSpec = floatingSpec(width, height)
            for ((index, dx) in dxs.withIndex()) {
                val dy = dys[index % dys.size]
                spec = spec.movedBy(
                    offset = DpOffset(dx.dp, dy.dp),
                    rowCount = RowCount,
                    smartbarRowCount = SmartbarRowCount,
                )
                spec.assertOnScreen("after drag #$index by ($dx, $dy)")
            }
        }
    }

    test("no sequence of resizes can shrink the handles out of reach or push them off screen") {
        checkAll(
            Arb.int(320..1400),
            Arb.int(480..1600),
            Arb.list(Arb.enum<ImeWindowResizeHandle>(), 1..10),
            Arb.list(Arb.double(-4000.0, 4000.0), 1..10),
        ) { width, height, handles, deltas ->
            var spec: ImeWindowSpec = floatingSpec(width, height)
            for ((index, handle) in handles.withIndex()) {
                val delta = deltas[index % deltas.size]
                spec = spec.resizedBy(
                    offset = DpOffset(delta.dp, delta.dp),
                    handle = handle,
                    rowCount = RowCount,
                    smartbarRowCount = SmartbarRowCount,
                )
                spec.assertOnScreen("after resize #$index on $handle by $delta")
            }
        }
    }

    test("interleaved drags and resizes stay recoverable") {
        // The reported cases are not a single clean gesture — they are a drag
        // that lands badly followed by a resize that makes it worse.
        checkAll(
            Arb.int(320..1400),
            Arb.int(480..1600),
            Arb.list(Arb.double(-2000.0, 2000.0), 2..10),
            Arb.list(Arb.enum<ImeWindowResizeHandle>(), 2..10),
        ) { width, height, deltas, handles ->
            var spec: ImeWindowSpec = floatingSpec(width, height)
            for ((index, delta) in deltas.withIndex()) {
                spec = spec.movedBy(
                    offset = DpOffset(delta.dp, (-delta).dp),
                    rowCount = RowCount,
                    smartbarRowCount = SmartbarRowCount,
                )
                spec.assertOnScreen("after interleaved drag #$index")
                spec = spec.resizedBy(
                    offset = DpOffset((-delta).dp, delta.dp),
                    handle = handles[index % handles.size],
                    rowCount = RowCount,
                    smartbarRowCount = SmartbarRowCount,
                )
                spec.assertOnScreen("after interleaved resize #$index")
            }
        }
    }

    test("a window dragged hard into a corner is still reset-able to the default") {
        // The user's escape hatch: whatever state they reached, resetting must
        // return a spec that satisfies every invariant.
        val spec = floatingSpec(widthDp = 411, heightDp = 891)
        val stranded = spec.movedBy(
            offset = DpOffset(9999.dp, (-9999).dp),
            rowCount = RowCount,
            smartbarRowCount = SmartbarRowCount,
        )
        stranded.assertOnScreen("after an extreme drag")

        val reset = (stranded as ImeWindowSpec.Floating).copy(
            props = stranded.constraints.defaultProps,
        )
        reset.assertOnScreen("after reset to defaults")
    }
}) {
    private companion object {
        val Tolerance = 1e-3f.dp
        const val RowCount = 4
        const val SmartbarRowCount = 1
    }
}
