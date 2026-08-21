/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.gestures

import dev.patrickgold.florisboard.ime.editor.EditorRange
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SpaceTouchpadModeTest : FunSpec({

    test("horizontal directions are detected for left/right events") {
        val leftEvent = swipeEvent(SwipeGesture.Direction.LEFT, relX = -3, relY = 0)
        val rightEvent = swipeEvent(SwipeGesture.Direction.RIGHT, relX = 3, relY = 0)

        leftEvent.isHorizontal shouldBe true
        leftEvent.isVertical shouldBe false
        rightEvent.isHorizontal shouldBe true
        rightEvent.isVertical shouldBe false
    }

    test("vertical directions are detected for up/down events") {
        val upEvent = swipeEvent(SwipeGesture.Direction.UP, relX = 0, relY = -3)
        val downEvent = swipeEvent(SwipeGesture.Direction.DOWN, relX = 0, relY = 3)

        upEvent.isVertical shouldBe true
        upEvent.isHorizontal shouldBe false
        downEvent.isVertical shouldBe true
        downEvent.isHorizontal shouldBe false
    }

    test("diagonal directions are neither horizontal nor vertical") {
        val diagEvent = swipeEvent(SwipeGesture.Direction.DOWN_RIGHT, relX = 2, relY = 2)

        diagEvent.isHorizontal shouldBe false
        diagEvent.isVertical shouldBe false
    }

    test("unit count per axis is extracted correctly") {
        val leftEvent = swipeEvent(SwipeGesture.Direction.LEFT, relX = -5, relY = 0)
        val upEvent = swipeEvent(SwipeGesture.Direction.UP, relX = 0, relY = -3)

        leftEvent.touchpadAxisUnitCount shouldBe 5
        upEvent.touchpadAxisUnitCount shouldBe 3
    }

    test("diagonal movement keeps both axes available") {
        val diagonalEvent = swipeEvent(SwipeGesture.Direction.DOWN_RIGHT, relX = 4, relY = 2)

        diagonalEvent.relUnitCountX shouldBe 4
        diagonalEvent.relUnitCountY shouldBe 2
    }

    test("ratio keeps fractional movement between events") {
        val first = SpaceTouchpadPolicy.scaleAxis(1, ratioPercent = 50, remainder = 0.0)
        val second = SpaceTouchpadPolicy.scaleAxis(1, ratioPercent = 50, remainder = first.remainder)

        first.units shouldBe 0
        second.units shouldBe 1
        second.remainder shouldBe 0.0
    }

    test("ratio is bounded and horizontal targets stay inside safe editor bounds") {
        SpaceTouchpadPolicy.scaleAxis(4, ratioPercent = 0, remainder = 0.0).units shouldBe 1
        SpaceTouchpadPolicy.scaleAxis(4, ratioPercent = 500, remainder = 0.0).units shouldBe 8

        SpaceTouchpadPolicy.safeDelta(1, -8, EditorRange(0, 5)) shouldBe -1
        SpaceTouchpadPolicy.safeDelta(4, 8, EditorRange(0, 5)) shouldBe 1
    }
})

private val SwipeGesture.Event.isHorizontal: Boolean
    get() = direction == SwipeGesture.Direction.LEFT || direction == SwipeGesture.Direction.RIGHT

private val SwipeGesture.Event.isVertical: Boolean
    get() = direction == SwipeGesture.Direction.UP || direction == SwipeGesture.Direction.DOWN

private val SwipeGesture.Event.touchpadAxisUnitCount: Int
    get() = if (isHorizontal) kotlin.math.abs(relUnitCountX) else kotlin.math.abs(relUnitCountY)

private fun swipeEvent(
    direction: SwipeGesture.Direction,
    relX: Int = 0,
    relY: Int = 0,
) = SwipeGesture.Event(
    direction = direction,
    type = SwipeGesture.Type.TOUCH_MOVE,
    pointerId = 0,
    absUnitCountX = relX,
    absUnitCountY = relY,
    relUnitCountX = relX,
    relUnitCountY = relY,
)
