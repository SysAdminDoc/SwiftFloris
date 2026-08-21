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

/** Pure movement math for the space-bar cursor trackpad. */
object SpaceTouchpadPolicy {
    const val MIN_RATIO_PERCENT = 25
    const val MAX_RATIO_PERCENT = 200
    const val DEFAULT_RATIO_PERCENT = 100

    data class AxisMovement(
        val units: Int,
        val remainder: Double,
    )

    /** Converts gesture units to cursor steps without losing fractional movement. */
    fun scaleAxis(
        relativeUnitCount: Int,
        ratioPercent: Int,
        remainder: Double,
    ): AxisMovement {
        val ratio = ratioPercent.coerceIn(MIN_RATIO_PERCENT, MAX_RATIO_PERCENT)
        val previousRemainder = remainder.takeIf { it.isFinite() } ?: 0.0
        val accumulated = previousRemainder + relativeUnitCount * ratio / 100.0
        val units = accumulated.toInt()
        return AxisMovement(units = units, remainder = accumulated - units)
    }

    /** Keeps a horizontal cursor target inside the editor's reported safe bounds. */
    fun safeDelta(position: Int, requestedDelta: Int, bounds: EditorRange): Int {
        if (!bounds.isValid) return 0
        val safePosition = position.coerceIn(bounds.start, bounds.end)
        val target = (safePosition.toLong() + requestedDelta.toLong())
            .coerceIn(bounds.start.toLong(), bounds.end.toLong())
        return (target - safePosition.toLong()).toInt()
    }
}
