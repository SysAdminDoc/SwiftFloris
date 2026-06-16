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
