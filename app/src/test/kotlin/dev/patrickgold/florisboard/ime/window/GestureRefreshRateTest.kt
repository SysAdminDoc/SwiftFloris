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

package dev.patrickgold.florisboard.ime.window

import android.view.Display
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GestureRefreshRateTest {
    // Display.Mode has no public constructor, so build one the way the framework does.
    private fun mode(id: Int, width: Int, height: Int, rate: Float): Display.Mode {
        val int = Int::class.javaPrimitiveType
        val constructor = Display.Mode::class.java
            .getDeclaredConstructor(int, int, int, Float::class.javaPrimitiveType)
        constructor.isAccessible = true
        return constructor.newInstance(id, width, height, rate)
    }

    @Test
    fun holdsFastestAtSameSize() {
        val at60 = mode(1, 1080, 2340, 60f)
        val at120 = mode(2, 1080, 2340, 120f)
        val larger = mode(3, 1440, 3120, 144f)

        GestureRefreshRate.fastestAtSameSize(at60, arrayOf(at60, larger, at120)) shouldBe at120
        GestureRefreshRate.fastestAtSameSize(at120, arrayOf(at60, larger, at120)) shouldBe at120
    }

    @Test
    fun tieAtFastestKeepsCurrent() {
        val first120 = mode(2, 1080, 2340, 120f)
        val current120 = mode(4, 1080, 2340, 120f)
        val at60 = mode(1, 1080, 2340, 60f)

        GestureRefreshRate.fastestAtSameSize(current120, arrayOf(at60, first120, current120)) shouldBe current120
    }

    @Test
    fun singleModeHoldsItself() {
        val only = mode(1, 1080, 2340, 60f)

        GestureRefreshRate.fastestAtSameSize(only, arrayOf(only)) shouldBe only
        GestureRefreshRate.fastestAtSameSize(only, emptyArray()) shouldBe only
    }

    @Test
    fun applyHoldsThenClears() {
        val attrs = WindowManager.LayoutParams()
        val at120 = mode(2, 1080, 2340, 120f)

        GestureRefreshRate.applyTo(attrs, at120) shouldBe true
        attrs.preferredDisplayModeId shouldBe 2
        attrs.preferredRefreshRate shouldBe 120f
        GestureRefreshRate.applyTo(attrs, at120) shouldBe false

        GestureRefreshRate.applyTo(attrs, null) shouldBe true
        attrs.preferredDisplayModeId shouldBe 0
        attrs.preferredRefreshRate shouldBe 0f
        GestureRefreshRate.applyTo(attrs, null) shouldBe false
    }
}
