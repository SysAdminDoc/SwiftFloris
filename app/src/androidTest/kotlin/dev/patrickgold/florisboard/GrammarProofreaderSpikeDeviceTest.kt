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

package dev.patrickgold.florisboard

import android.os.Build
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GrammarProofreaderSpikeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val spellCheckerId =
        "${BuildConfig.APPLICATION_ID}/dev.patrickgold.florisboard.FlorisSpellCheckerService"
    private var previousSpellChecker: String? = null
    private var previousEnabled: String? = null
    private var session: SpellCheckerSession? = null

    @Before
    fun setUp() {
        previousSpellChecker = shell("settings get secure selected_spell_checker").normalizedSetting()
        previousEnabled = shell("settings get secure spell_checker_enabled").normalizedSetting()
        shell("settings put secure selected_spell_checker $spellCheckerId")
        shell("settings put secure spell_checker_enabled 1")
    }

    @After
    fun tearDown() {
        session?.close()
        restoreSetting("selected_spell_checker", previousSpellChecker)
        restoreSetting("spell_checker_enabled", previousEnabled)
    }

    @Test
    fun englishRuleReturnsGrammarAttributeThroughSystemService() {
        assertTrue("Grammar attributes require Android 12 or newer", Build.VERSION.SDK_INT >= 31)

        val callback = SentenceCallback()
        val manager = instrumentation.targetContext.getSystemService(TextServicesManager::class.java)
        assertNotNull("TextServicesManager must be available", manager)
        waitUntil("SwiftFloris became the selected spell checker") {
            manager.currentSpellCheckerInfo?.id == spellCheckerId
        }

        instrumentation.runOnMainSync {
            session = manager.newSpellCheckerSession(
                null,
                Locale.ENGLISH,
                callback,
                false,
            )
        }
        assertNotNull("SwiftFloris spell-checker session must open", session)

        instrumentation.runOnMainSync {
            session!!.getSentenceSuggestions(
                arrayOf(TextInfo("This are ready.", Cookie, Sequence)),
                5,
            )
        }
        assertTrue("Sentence callback timed out", callback.latch.await(10, TimeUnit.SECONDS))

        val sentence = callback.results.single()
        val grammarIndexes = (0 until sentence.suggestionsCount).filter { index ->
            sentence.getSuggestionsInfoAt(index).suggestionsAttributes and
                SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR != 0
        }
        assertEquals("Exactly one grammar result expected", listOf(0), grammarIndexes)

        val suggestion = sentence.getSuggestionsInfoAt(grammarIndexes.single())
        assertEquals(5, sentence.getOffsetAt(grammarIndexes.single()))
        assertEquals(3, sentence.getLengthAt(grammarIndexes.single()))
        assertEquals("is", suggestion.getSuggestionAt(0))
        assertEquals(Cookie, suggestion.cookie)
        assertEquals(Sequence, suggestion.sequence)
    }

    private fun waitUntil(label: String, timeoutMillis: Long = 7_500L, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (predicate()) return
            Thread.sleep(100L)
        }
        error("Timed out waiting for $label")
    }

    private fun restoreSetting(name: String, value: String?) {
        if (value == null) {
            shell("settings delete secure $name")
        } else {
            shell("settings put secure $name $value")
        }
    }

    private fun String.normalizedSetting(): String? = trim().takeUnless { it.isEmpty() || it == "null" }

    private fun shell(command: String): String = device.executeShellCommand(command)

    private class SentenceCallback : SpellCheckerSession.SpellCheckerSessionListener {
        val latch = CountDownLatch(1)
        lateinit var results: Array<out SentenceSuggestionsInfo>
            private set

        override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>) {
            this.results = results
            latch.countDown()
        }

        override fun onGetSuggestions(results: Array<out SuggestionsInfo>) = Unit
    }

    private companion object {
        const val Cookie = 73
        const val Sequence = 91
    }
}
