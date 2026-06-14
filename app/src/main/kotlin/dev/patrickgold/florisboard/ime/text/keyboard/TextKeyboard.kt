/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.Key
import dev.patrickgold.florisboard.ime.keyboard.Keyboard
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.popup.PopupMapping
import dev.patrickgold.florisboard.ime.window.SplitKeyboardLayoutCalculator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class NearbyTextKey(
    val key: TextKey,
    val confidence: Double,
)

class TextKeyboard(
    val arrangement: Array<Array<TextKey>>,
    override val mode: KeyboardMode,
    val extendedPopupMapping: PopupMapping?,
    val extendedPopupMappingDefault: PopupMapping?,
    val layoutStyle: TextKeyboardLayoutStyle = TextKeyboardLayoutStyle.Standard,
) : Keyboard() {
    private var honeycombCells: List<HoneycombKeyCell> = emptyList()

    val rowCount: Int
        get() = arrangement.size

    val keyCount: Int
        get() = arrangement.sumOf { it.size }

    override fun getKeyForPos(pointerX: Float, pointerY: Float): TextKey? {
        honeycombKeyForPos(pointerX, pointerY)?.let { return it }
        if (layoutStyle == TextKeyboardLayoutStyle.Honeycomb) return null
        for (key in keys()) {
            if (key.touchBounds.contains(pointerX, pointerY)) {
                return key
            }
        }
        return null
    }

    fun getNearestKeyForPos(
        pointerX: Float,
        pointerY: Float,
        gapRescueDistanceFactor: Float = GapRescueDistanceFactor,
    ): TextKey? {
        if (layoutStyle == TextKeyboardLayoutStyle.Honeycomb) {
            return honeycombKeyForPos(pointerX, pointerY)
        }
        if (isPointInSplitGutter(pointerX, pointerY)) return null
        var bestKey: TextKey? = null
        var bestDistanceSq = Float.POSITIVE_INFINITY
        for (key in keys()) {
            if (!key.isEnabled || !key.isVisible || key.visibleBounds.isEmpty()) {
                continue
            }
            val bounds = key.visibleBounds
            val maxRescueDistance = min(bounds.width, bounds.height) * gapRescueDistanceFactor
            val dx = when {
                pointerX < bounds.left -> bounds.left - pointerX
                pointerX >= bounds.right -> pointerX - bounds.right
                else -> 0.0f
            }
            val dy = when {
                pointerY < bounds.top -> bounds.top - pointerY
                pointerY >= bounds.bottom -> pointerY - bounds.bottom
                else -> 0.0f
            }
            if (max(dx, dy) > maxRescueDistance) {
                continue
            }
            val distanceSq = dx * dx + dy * dy
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                bestKey = key
            }
        }
        return bestKey
    }

    fun isPointInSplitGutter(pointerX: Float, pointerY: Float): Boolean {
        for ((rowIndex, row) in rows().withIndex()) {
            if (row.size < 2) continue
            val (leftKeyCount, rightKeyCount) =
                SplitKeyboardLayoutCalculator.qwertyBoundary(rowIndex, row.size)
            if (leftKeyCount <= 0 || rightKeyCount <= 0 || leftKeyCount >= row.size) continue
            val lastLeft = row[leftKeyCount - 1].touchBounds
            val firstRight = row[leftKeyCount].touchBounds
            if (firstRight.left <= lastLeft.right) continue
            val rowTop = min(lastLeft.top, firstRight.top)
            val rowBottom = max(lastLeft.bottom, firstRight.bottom)
            if (pointerY >= rowTop && pointerY < rowBottom &&
                pointerX >= lastLeft.right && pointerX < firstRight.left
            ) {
                return true
            }
        }
        return false
    }

    internal fun getNearbyKeysForPos(
        pointerX: Float,
        pointerY: Float,
        maxCandidateCount: Int = 5,
    ): List<NearbyTextKey> {
        if (maxCandidateCount <= 0) return emptyList()
        return keys().asSequence()
            .mapNotNull { key ->
                if (!key.isEnabled || !key.isVisible || key.visibleBounds.isEmpty()) {
                    return@mapNotNull null
                }
                val bounds = key.visibleBounds
                val halfWidth = bounds.width * 0.5f
                val halfHeight = bounds.height * 0.5f
                if (halfWidth <= 0.0f || halfHeight <= 0.0f) {
                    return@mapNotNull null
                }
                val centerX = bounds.left + halfWidth
                val centerY = bounds.top + halfHeight
                val normalizedX = (pointerX - centerX) / halfWidth
                val normalizedY = (pointerY - centerY) / halfHeight
                val distance = sqrt((normalizedX * normalizedX + normalizedY * normalizedY).toDouble()).toFloat()
                if (distance > NearbyKeyDistanceLimit) {
                    return@mapNotNull null
                }
                NearbyTextKey(
                    key = key,
                    confidence = (1.0 - (distance / NearbyKeyDistanceLimit).toDouble()).coerceIn(0.0, 1.0),
                )
            }
            .sortedByDescending { it.confidence }
            .take(maxCandidateCount)
            .toList()
    }

    override fun layout(
        keyboardWidth: Float,
        keyboardHeight: Float,
        desiredKey: Key,
        extendTouchBoundariesDownwards: Boolean,
    ) {
        if (arrangement.isEmpty()) return
        honeycombCells = emptyList()

        val desiredTouchBounds = desiredKey.touchBounds
        val desiredVisibleBounds = desiredKey.visibleBounds
        if (desiredTouchBounds.isEmpty() || desiredVisibleBounds.isEmpty()) return
        if (keyboardWidth.isNaN() || keyboardHeight.isNaN()) return
        if (layoutStyle == TextKeyboardLayoutStyle.Honeycomb && mode == KeyboardMode.CHARACTERS) {
            layoutHoneycomb(
                keyboardWidth = keyboardWidth,
                keyboardHeight = keyboardHeight,
                marginH = abs(desiredTouchBounds.width - desiredVisibleBounds.width) / 2.0f,
                marginV = abs(desiredTouchBounds.height - desiredVisibleBounds.height) / 2.0f,
            )
            return
        }
        val rowMarginH = abs(desiredTouchBounds.width - desiredVisibleBounds.width)
        val rowMarginV = (keyboardHeight - desiredTouchBounds.height * rowCount.toFloat()) / (rowCount - 1).coerceAtLeast(1).toFloat()

        for ((r, row) in rows().withIndex()) {
            val posY = (desiredTouchBounds.height + rowMarginV) * r
            val availableWidth = (keyboardWidth - rowMarginH) / desiredTouchBounds.width
            var requestedWidth = 0.0f
            var shrinkSum = 0.0f
            var growSum = 0.0f
            for (key in row) {
                requestedWidth += key.flayWidthFactor
                shrinkSum += key.flayShrink
                growSum += key.flayGrow
            }
            if (requestedWidth <= availableWidth) {
                // Requested with is smaller or equal to the available with, so we can grow
                val additionalWidth = availableWidth - requestedWidth
                var posX = rowMarginH / 2.0f
                for ((k, key) in row.withIndex()) {
                    val keyWidth = desiredTouchBounds.width * when (growSum) {
                        0.0f -> when (k) {
                            0, row.size - 1 -> key.flayWidthFactor + additionalWidth / 2.0f
                            else -> key.flayWidthFactor
                        }
                        else -> key.flayWidthFactor + additionalWidth * (key.flayGrow / growSum)
                    }
                    key.touchBounds.apply {
                        left = posX
                        top = posY
                        right = posX + keyWidth
                        bottom = posY + desiredTouchBounds.height
                    }
                    key.visibleBounds.apply {
                        left = key.touchBounds.left + abs(desiredTouchBounds.left - desiredVisibleBounds.left) + when {
                            growSum == 0.0f && k == 0 -> ((additionalWidth / 2.0f) * desiredTouchBounds.width)
                            else -> 0.0f
                        }
                        top = key.touchBounds.top + abs(desiredTouchBounds.top - desiredVisibleBounds.top)
                        right = key.touchBounds.right - abs(desiredTouchBounds.right - desiredVisibleBounds.right) - when {
                            growSum == 0.0f && k == row.size - 1 -> ((additionalWidth / 2.0f) * desiredTouchBounds.width)
                            else -> 0.0f
                        }
                        bottom = key.touchBounds.bottom - abs(desiredTouchBounds.bottom - desiredVisibleBounds.bottom)
                    }
                    posX += keyWidth
                    // After-adjust touch bounds for the row margin
                    key.touchBounds.apply {
                        if (k == 0) {
                            left = 0.0f
                        } else if (k == row.size - 1) {
                            right = keyboardWidth
                        }
                        if (extendTouchBoundariesDownwards && r + 1 == arrangement.size) {
                            bottom += height
                        }
                    }
                }
            } else {
                // Requested size too big, must shrink.
                val clippingWidth = requestedWidth - availableWidth
                var posX = rowMarginH / 2.0f
                for ((k, key) in row.withIndex()) {
                    val keyWidth = desiredTouchBounds.width * if (key.flayShrink == 0.0f) {
                        key.flayWidthFactor
                    } else {
                        key.flayWidthFactor - clippingWidth * (key.flayShrink / shrinkSum)
                    }
                    key.touchBounds.apply {
                        left = posX
                        top = posY
                        right = posX + keyWidth
                        bottom = posY + desiredTouchBounds.height
                    }
                    key.visibleBounds.apply {
                        left = key.touchBounds.left + abs(desiredTouchBounds.left - desiredVisibleBounds.left)
                        top = key.touchBounds.top + abs(desiredTouchBounds.top - desiredVisibleBounds.top)
                        right = key.touchBounds.right - abs(desiredTouchBounds.right - desiredVisibleBounds.right)
                        bottom = key.touchBounds.bottom - abs(desiredTouchBounds.bottom - desiredVisibleBounds.bottom)
                    }
                    posX += keyWidth
                    // After-adjust touch bounds for the row margin
                    key.touchBounds.apply {
                        if (k == 0) {
                            left = 0.0f
                        } else if (k == row.size - 1) {
                            right = keyboardWidth
                        }
                        if (extendTouchBoundariesDownwards && r + 1 == arrangement.size) {
                            bottom += height
                        }
                    }
                }
            }
        }
    }

    private fun layoutHoneycomb(
        keyboardWidth: Float,
        keyboardHeight: Float,
        marginH: Float,
        marginV: Float,
    ) {
        val rows = rows().asSequence().toList()
        val rowCount = rows.size
        if (rowCount == 0) return
        val columnCounts = rows.map { it.size }
        if (columnCounts.any { it <= 0 }) return

        val maxColumnUnits = columnCounts.mapIndexed { rowIndex, count ->
            count.toFloat() + if (rowIndex % 2 == 1) 0.5f else 0.0f
        }.maxOrNull() ?: return
        val heightUnits = 2.0f + 1.5f * (rowCount - 1).toFloat()
        val radiusByWidth = keyboardWidth / (HoneycombTessellation.SQRT_3 * maxColumnUnits)
        val radiusByHeight = keyboardHeight / heightUnits
        val radius = min(radiusByWidth, radiusByHeight)
            .takeIf { it.isFinite() && it > 0.0f }
            ?: return
        val columnStride = radius * HoneycombTessellation.SQRT_3
        val rowStride = radius * 1.5f
        val layoutWidth = columnStride * maxColumnUnits
        val layoutHeight = radius * 2.0f + rowStride * (rowCount - 1).toFloat()
        val originX = ((keyboardWidth - layoutWidth) / 2.0f).coerceAtLeast(0.0f)
        val originY = ((keyboardHeight - layoutHeight) / 2.0f).coerceAtLeast(0.0f)
        val cells = mutableListOf<HoneycombKeyCell>()

        for ((rowIndex, row) in rows.withIndex()) {
            val rowOffsetX = if (rowIndex % 2 == 1) columnStride / 2.0f else 0.0f
            for ((colIndex, key) in row.withIndex()) {
                val left = originX + rowOffsetX + colIndex * columnStride
                val top = originY + rowIndex * rowStride
                key.touchBounds.apply {
                    this.left = left
                    this.top = top
                    this.right = left + columnStride
                    this.bottom = top + radius * 2.0f
                }
                key.visibleBounds.applyFrom(key.touchBounds)
                if (marginH > 0.0f || marginV > 0.0f) {
                    key.visibleBounds.deflateBy(
                        marginH.coerceAtMost(key.visibleBounds.width / 3.0f),
                        marginV.coerceAtMost(key.visibleBounds.height / 3.0f),
                    )
                }
                cells += HoneycombKeyCell(
                    key = key,
                )
            }
        }
        honeycombCells = cells
    }

    private fun honeycombKeyForPos(pointerX: Float, pointerY: Float): TextKey? {
        if (honeycombCells.isEmpty()) return null
        return honeycombCells.firstOrNull { cell ->
            cell.key.isEnabled && cell.key.isVisible && cell.contains(pointerX, pointerY)
        }?.key
    }

    override fun keys(): Iterator<TextKey> {
        return TextKeyboardIterator(arrangement)
    }

    fun rows(): Iterator<Array<TextKey>> {
        return arrangement.iterator()
    }

    class TextKeyboardIterator internal constructor(
        private val arrangement: Array<Array<TextKey>>
    ) : Iterator<TextKey> {
        private var rowIndex: Int = 0
        private var keyIndex: Int = 0

        override fun hasNext(): Boolean {
            // Skip over empty rows (a layout may legitimately produce a
            // zero-key row). The previous `keyIndex < arrangement[rowIndex].size`
            // check evaluated to `0 < 0` on the first empty row and terminated
            // iteration permanently, silently dropping every subsequent row
            // from hit-testing and glide layout.
            while (rowIndex < arrangement.size && keyIndex >= arrangement[rowIndex].size) {
                rowIndex++
                keyIndex = 0
            }
            return rowIndex < arrangement.size
        }

        override fun next(): TextKey {
            if (!hasNext()) throw NoSuchElementException()
            val next = arrangement[rowIndex][keyIndex]
            keyIndex++
            return next
        }
    }

    companion object {
        private const val GapRescueDistanceFactor = 0.32f
        private const val NearbyKeyDistanceLimit = 1.65f
    }
}

enum class TextKeyboardLayoutStyle {
    Standard,
    Honeycomb,
}

private data class HoneycombKeyCell(
    val key: TextKey,
) {
    fun contains(pointerX: Float, pointerY: Float): Boolean {
        val bounds = key.touchBounds
        if (!bounds.contains(pointerX, pointerY)) return false
        val radius = min(bounds.width / 2.0f, bounds.height / HoneycombTessellation.SQRT_3)
        if (radius <= 0.0f) return false
        val halfHeight = radius * HoneycombTessellation.SQRT_3 / 2.0f
        val dx = abs(pointerX - (bounds.left + bounds.width / 2.0f))
        val dy = abs(pointerY - (bounds.top + bounds.height / 2.0f))
        if (dy > halfHeight || dx > radius) return false
        if (dx <= radius / 2.0f) return true
        val edgeY = halfHeight * (2.0f - 2.0f * dx / radius)
        return dy <= edgeY
    }
}
