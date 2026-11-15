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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor

/**
 * Decodes a downsampled preview of a clipboard image. The clipboard provider
 * hands out a fresh decrypted descriptor per open, so the bounds pass and the
 * pixel pass each open their own.
 */
internal object ClipboardPreviewImageDecoder {
    fun decode(source: Any, openDescriptor: () -> ParcelFileDescriptor?): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode fills [bounds] and always returns null, so the
        // block reports whether the descriptor opened, not what the decoder
        // returned. Using the decoder's result here failed every image.
        val opened = openDescriptor()?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
            true
        } ?: false
        check(opened) { "Unable to resolve image at $source" }
        val sampleSize = ClipboardPreviewImagePolicy.sampleSizeForPreview(bounds.outWidth, bounds.outHeight)
        val bitmap = openDescriptor()?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(
                descriptor.fileDescriptor,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }
        return checkNotNull(bitmap) { "Unable to decode image at $source" }
    }
}
