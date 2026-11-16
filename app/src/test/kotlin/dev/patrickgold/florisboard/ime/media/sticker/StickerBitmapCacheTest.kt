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

package dev.patrickgold.florisboard.ime.media.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Sticker tiles used to keep a longest edge anywhere up to 1,023 px, so 64 cached tiles could hold
 * about 268 MB, far past what the memory limiter allows a keyboard. These decode real PNGs.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StickerBitmapCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        evictStickerBitmapCache()
    }

    @After
    fun cleanUp() {
        evictStickerBitmapCache()
        files.forEach { it.delete() }
    }

    private fun png(width: Int, height: Int): String {
        val file = File.createTempFile("sticker-", ".png", context.cacheDir).also { files += it }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(137, 180, 250))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file).toString()
    }

    @Test
    fun aStickerJustUnderTwiceTheTileSizeIsShrunkToFit() {
        val tile = assertNotNull(loadStickerBitmap(context, png(1023, 600)))

        assertTrue(tile.width <= TARGET_STICKER_EDGE_PX, "width ${tile.width}")
        assertTrue(tile.height <= TARGET_STICKER_EDGE_PX, "height ${tile.height}")
    }

    @Test
    fun aStickerAtTheTileSizeKeepsItsSize() {
        val tile = assertNotNull(loadStickerBitmap(context, png(512, 512)))

        assertEquals(512, tile.width)
        assertEquals(512, tile.height)
    }

    @Test
    fun aCachedStickerIsNotDecodedAgain() {
        val uri = png(256, 256)
        val first = assertNotNull(loadStickerBitmap(context, uri))

        assertSame(first, loadStickerBitmap(context, uri))
        assertEquals(256 * 256 * 4, stickerBitmapCacheBytes())
    }

    @Test
    fun theCacheStaysUnderItsByteBudget() {
        repeat(40) { loadStickerBitmap(context, png(512, 512)) }

        assertTrue(stickerBitmapCacheBytes() <= STICKER_BITMAP_CACHE_BYTES, "${stickerBitmapCacheBytes()} bytes")
        assertEquals(STICKER_BITMAP_CACHE_BYTES / (512 * 512 * 4) * (512 * 512 * 4), stickerBitmapCacheBytes())
    }
}
