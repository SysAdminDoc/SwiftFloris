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
import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Clipboard history showed "Something went wrong" for every copied image:
 * the bounds pass treated the bounds-only decoder's null result as "could not
 * open the image". These decode a real PNG through a real descriptor, the way
 * the clipboard provider serves it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClipboardPreviewImageDecoderTest {
    private val files = mutableListOf<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun png(width: Int, height: Int): File {
        val file = File.createTempFile("clip-preview-", ".png").also { files += it }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(137, 180, 250))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private fun opener(file: File): () -> ParcelFileDescriptor? = {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    @Test
    fun `a copied image decodes to a preview`() {
        val bitmap = ClipboardPreviewImageDecoder.decode("clip", opener(png(120, 90)))

        assertEquals(120, bitmap.width)
        assertEquals(90, bitmap.height)
    }

    @Test
    fun `a large image is downsampled for the preview`() {
        val bitmap = ClipboardPreviewImageDecoder.decode("clip", opener(png(2048, 1024)))

        assertEquals(1024, bitmap.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    fun `an image that cannot be opened still fails`() {
        assertFailsWith<IllegalStateException> {
            ClipboardPreviewImageDecoder.decode("clip") { null }
        }
    }
}
