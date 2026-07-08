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

package dev.patrickgold.florisboard.ime.clipboard

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object ClipboardPreviewImagePolicy {
    const val MAX_PREVIEW_BITMAP_SIDE = 1024
    const val MAX_DECODE_BITMAP_SIDE = 8192

    data class PreviewBounds(val width: Int, val height: Int)

    fun requireSupportedBounds(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Image bounds must be known before preview decode." }
        require(width <= MAX_DECODE_BITMAP_SIDE && height <= MAX_DECODE_BITMAP_SIDE) {
            "Image preview dimensions ${width}x$height exceed ${MAX_DECODE_BITMAP_SIDE}px."
        }
    }

    fun sampleSizeForPreview(width: Int, height: Int): Int {
        val scale = previewScale(width, height)
        return if (scale > 1f) ceil(scale).toInt() else 1
    }

    fun scaledPreviewBounds(width: Int, height: Int): PreviewBounds {
        val scale = previewScale(width, height)
        return if (scale > 1f) {
            PreviewBounds(
                width = (width / scale).roundToInt().coerceAtLeast(1),
                height = (height / scale).roundToInt().coerceAtLeast(1),
            )
        } else {
            PreviewBounds(width = width, height = height)
        }
    }

    private fun previewScale(width: Int, height: Int): Float {
        requireSupportedBounds(width, height)
        return max(
            width.toFloat() / MAX_PREVIEW_BITMAP_SIDE,
            height.toFloat() / MAX_PREVIEW_BITMAP_SIDE,
        )
    }
}
