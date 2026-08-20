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

import android.content.ClipData
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ClipboardTextRetentionPolicyTest {
    @Test
    fun `retention boundary is exactly 64 KiB of UTF-8`() {
        ClipboardTextRetentionPolicy.MAX_RETAINED_UTF8_BYTES shouldBe 65_536

        ClipboardTextRetentionPolicy.shouldRetain("a".repeat(65_536)) shouldBe true
        ClipboardTextRetentionPolicy.shouldRetain("a".repeat(65_537)) shouldBe false
    }

    @Test
    fun `multibyte text is measured by encoded bytes rather than UTF-16 units`() {
        val exactThreeByteBoundary = "€".repeat(21_845) + "a"
        val overThreeByteBoundary = exactThreeByteBoundary + "é"
        val exactFourByteBoundary = "😀".repeat(16_384)

        exactThreeByteBoundary.toByteArray(Charsets.UTF_8).size shouldBe 65_536
        ClipboardTextRetentionPolicy.shouldRetain(exactThreeByteBoundary) shouldBe true
        ClipboardTextRetentionPolicy.shouldRetain(overThreeByteBoundary) shouldBe false

        exactFourByteBoundary.toByteArray(Charsets.UTF_8).size shouldBe 65_536
        ClipboardTextRetentionPolicy.shouldRetain(exactFourByteBoundary) shouldBe true
        ClipboardTextRetentionPolicy.shouldRetain(exactFourByteBoundary + "a") shouldBe false
    }

    @Test
    fun `120 KiB live clip is paste-only and keeps its original text`() {
        val text = "x".repeat(120 * 1024)
        val clipData = ClipData.newPlainText("test", text)

        ClipboardTextRetentionPolicy.shouldRetain(text) shouldBe false
        ClipboardTextRetentionPolicy.pasteOnlyTextOrNull(clipData) shouldBe text
        ClipboardTextRetentionPolicy.pasteOnlyTextOrNull(clipData) shouldNotBe text.take(65_536)
    }

    @Test
    fun `multi-MiB rejection does not inspect payload characters`() {
        val text = LengthOnlyCharSequence(length = 4 * 1024 * 1024)

        ClipboardTextRetentionPolicy.shouldRetain(text) shouldBe false
        text.readCount shouldBe 0
    }

    @Test
    fun `retained clips do not enter direct-only path`() {
        val text = "a".repeat(65_536)
        val clipData = ClipData.newPlainText("test", text)

        ClipboardTextRetentionPolicy.pasteOnlyTextOrNull(clipData) shouldBe null
    }

    @Test
    fun `ClipboardItem factories reject oversized text before entity creation`() {
        val text = "x".repeat(65_537)
        val clipData = ClipData.newPlainText("test", text)
        val context = ApplicationProvider.getApplicationContext<Context>()

        shouldThrow<IllegalArgumentException> {
            ClipboardItem.text(text)
        }
        shouldThrow<IllegalArgumentException> {
            ClipboardItem.fromClipData(context, clipData, cloneUri = false)
        }
    }
}

private class LengthOnlyCharSequence(
    override val length: Int,
) : CharSequence {
    var readCount: Int = 0
        private set

    override fun get(index: Int): Char {
        readCount += 1
        error("A payload above the UTF-8 cap must be rejected from its length")
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        error("A payload above the UTF-8 cap must not be copied")
    }
}
