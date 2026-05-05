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

package dev.patrickgold.florisboard.ime.text.gestures

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.lib.FlorisLocale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultilingualGlideDictionaryProfileTest {
    @Test
    fun bundledGlideDictionariesLoadOnCurrentDevice() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val provider = LatinLanguageProvider(context)
            provider.create()

            val results = TargetLanguages.mapIndexed { index, target ->
                val subtype = Subtype.DEFAULT.copy(
                    id = index.toLong(),
                    primaryLocale = FlorisLocale.from(target.languageCode),
                )
                val startMillis = SystemClock.elapsedRealtime()
                provider.preload(subtype)
                val words = provider.getListOfWords(subtype)
                val loadMillis = SystemClock.elapsedRealtime() - startMillis

                assertTrue(
                    "${target.displayName} dictionary had ${words.size} words; expected at least ${target.minWords}",
                    words.size >= target.minWords,
                )
                for (word in target.sampleWords) {
                    assertTrue("${target.displayName} dictionary missing '$word'", word in words)
                    assertTrue(
                        "${target.displayName} dictionary returned no frequency for '$word'",
                        provider.getFrequencyForWord(subtype, word) > 0.0,
                    )
                }

                ProfileResult(
                    displayName = target.displayName,
                    wordCount = words.size,
                    loadMillis = loadMillis,
                )
            }

            provider.destroy()
            Log.i(
                TAG,
                "Multilingual glide dictionary profile: " +
                    "device=${Build.MANUFACTURER} ${Build.MODEL}, " +
                    "sdk=${Build.VERSION.SDK_INT}, " +
                    results.joinToString { "${it.displayName}=${it.wordCount} words/${it.loadMillis}ms" },
            )
        }
    }

    private data class TargetLanguage(
        val languageCode: String,
        val displayName: String,
        val minWords: Int,
        val sampleWords: List<String>,
    )

    private data class ProfileResult(
        val displayName: String,
        val wordCount: Int,
        val loadMillis: Long,
    )

    private companion object {
        const val TAG = "GlideDictProfile"

        val TargetLanguages = listOf(
            TargetLanguage("en", "English", 10_000, listOf("the", "and", "people")),
            TargetLanguage("de", "German", 100_000, listOf("hallo", "und", "ist")),
            TargetLanguage("es", "Spanish", 100_000, listOf("hola", "que", "para")),
            TargetLanguage("fr", "French", 100_000, listOf("bonjour", "que", "pour")),
            TargetLanguage("it", "Italian", 100_000, listOf("ciao", "che", "per")),
            TargetLanguage("pt", "Portuguese", 100_000, listOf("ola", "que", "para")),
        )
    }
}
