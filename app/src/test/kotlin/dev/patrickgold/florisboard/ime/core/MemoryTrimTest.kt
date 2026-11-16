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

package dev.patrickgold.florisboard.ime.core

import android.content.ComponentCallbacks2
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.ime.text.gestures.StatisticalGlideTypingClassifier
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MemoryTrimTest {
    private val app = ApplicationProvider.getApplicationContext<FlorisApplication>()

    @Test
    fun onlyTheLevelsAndroidStillSendsTrimAnything() {
        @Suppress("DEPRECATION")
        MemoryTrim.tierFor(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) shouldBe MemoryTrim.Tier.None
        MemoryTrim.tierFor(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) shouldBe MemoryTrim.Tier.OffScreen
        MemoryTrim.tierFor(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) shouldBe MemoryTrim.Tier.Background
        @Suppress("DEPRECATION")
        MemoryTrim.tierFor(ComponentCallbacks2.TRIM_MEMORY_COMPLETE) shouldBe MemoryTrim.Tier.Background
    }

    @Test
    fun wordLookupsSurviveTheKeyboardHidingAndEmptyInTheBackground() {
        val nlp = app.nlpManager.value
        nlp.getFrequencyForWord(Subtype.DEFAULT, "hello")
        nlp.getListOfWords(Subtype.DEFAULT)
        nlp.cacheSizes() shouldBe (1 to 1)

        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        nlp.cacheSizes() shouldBe (1 to 1)

        val overlayVersion = nlp.debugOverlayVersion.value
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        nlp.cacheSizes() shouldBe (0 to 0)
        // The debug overlay redraws, so it doesn't keep showing the spelling results just dropped.
        nlp.debugOverlayVersion.value shouldBe overlayVersion + 1
    }

    @Test
    fun glideKeepsItsActivePrunerThroughATrim() {
        val classifier = StatisticalGlideTypingClassifier(app)
        val keys = "qwertyuiopasdfghjklzxcvbnm".mapIndexed { i, c ->
            TextKey(TextKeyData(code = c.code, label = c.toString())).also { key ->
                key.visibleBounds.apply {
                    left = (i % 10) * 10f
                    top = (i / 10) * 10f
                    right = left + 10f
                    bottom = top + 10f
                }
            }
        }
        classifier.setLayout(keys, Subtype.DEFAULT)
        classifier.ready shouldBe true
        classifier.cacheSizes().third shouldBe 1

        classifier.trimMemory()

        classifier.cacheSizes() shouldBe Triple(0, 0, 1)
        classifier.ready shouldBe true
    }
}
