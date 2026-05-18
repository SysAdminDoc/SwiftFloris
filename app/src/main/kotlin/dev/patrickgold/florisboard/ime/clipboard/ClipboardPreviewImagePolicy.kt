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

object ClipboardPreviewImagePolicy {
    const val MAX_PREVIEW_BITMAP_SIDE = 1024
    const val MAX_DECODE_BITMAP_SIDE = 8192

    fun requireSupportedBounds(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Image bounds must be known before preview decode." }
        require(width <= MAX_DECODE_BITMAP_SIDE && height <= MAX_DECODE_BITMAP_SIDE) {
            "Image preview dimensions ${width}x$height exceed ${MAX_DECODE_BITMAP_SIDE}px."
        }
    }
}
