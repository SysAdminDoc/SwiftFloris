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

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfile
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfiles
import dev.patrickgold.florisboard.ime.profile.PerAppSuggestionAggressiveness
import dev.patrickgold.florisboard.nlpManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A per-app profile with suggestions "Off for this app" used to turn only
 * composing off. The suggestion engine read the global switch alone, so the
 * strip kept suggesting in that app and a space still corrected the word.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NlpManagerPerAppSuggestionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs by FlorisPreferenceStore

    @Before
    fun setUp(): Unit = runBlocking {
        savePref { prefs.suggestion.enabled.set(true) }
        // Emoji and clipboard suggestions keep the strip on by themselves, so only word
        // suggestions are left to decide it here.
        savePref { prefs.emoji.suggestionEnabled.set(false) }
        savePref { prefs.clipboard.suggestionEnabled.set(false) }
        savePref {
            prefs.privacy.perAppKeyboardProfiles.set(
                PerAppKeyboardProfiles.upsert(
                    PerAppKeyboardProfiles.EmptyJson,
                    PerAppKeyboardProfile(
                        packageName = QuietApp,
                        suggestions = PerAppSuggestionAggressiveness.OFF,
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown(): Unit = runBlocking {
        savePref { prefs.privacy.perAppKeyboardProfiles.set(PerAppKeyboardProfiles.EmptyJson) }
        // All three default to on (EmojiPrefs, ClipboardPrefs, SuggestionPrefs).
        savePref { prefs.emoji.suggestionEnabled.set(true) }
        savePref { prefs.clipboard.suggestionEnabled.set(true) }
        savePref { prefs.suggestion.enabled.set(true) }
    }

    @Test
    fun anAppWhoseProfileTurnsSuggestionsOffGetsNoneAndOtherAppsKeepThem() {
        val editorInstance by context.editorInstance()
        val nlpManager by context.nlpManager()

        editorInstance.handleStartInput(fieldInfo(QuietApp))
        assertFalse(nlpManager.isSuggestionOn())

        editorInstance.handleStartInput(fieldInfo(OtherApp))
        assertTrue(nlpManager.isSuggestionOn())

        editorInstance.handleStartInput(fieldInfo(QuietApp))
        assertFalse(nlpManager.isSuggestionOn())
    }

    @Test
    fun removingTheProfileBringsSuggestionsBackInThatApp(): Unit = runBlocking {
        val editorInstance by context.editorInstance()
        val nlpManager by context.nlpManager()

        editorInstance.handleStartInput(fieldInfo(QuietApp))
        assertFalse(nlpManager.isSuggestionOn())

        savePref {
            prefs.privacy.perAppKeyboardProfiles.set(
                PerAppKeyboardProfiles.remove(prefs.privacy.perAppKeyboardProfiles.get(), QuietApp),
            )
        }
        assertTrue(nlpManager.isSuggestionOn())
    }

    @Test
    fun theGlobalSwitchStillTurnsSuggestionsOffEverywhere(): Unit = runBlocking {
        val editorInstance by context.editorInstance()
        val nlpManager by context.nlpManager()
        savePref { prefs.suggestion.enabled.set(false) }

        editorInstance.handleStartInput(fieldInfo(OtherApp))
        assertFalse(nlpManager.isSuggestionOn())
    }

    private fun fieldInfo(packageName: String): FlorisEditorInfo {
        return FlorisEditorInfo.wrap(
            EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT
                this.packageName = packageName
                initialSelStart = 0
                initialSelEnd = 0
            },
        )
    }

    private suspend fun savePref(write: suspend () -> Result<*>) {
        // The app loads the store in the background and swaps it in when done, which would drop a
        // value set before then.
        withTimeout(10_000) {
            while (!(context as FlorisApplication).preferenceStoreLoaded.value) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(20)
            }
        }
        withTimeout(10_000) { write() }.onFailure { error ->
            // JetPref saves by renaming a temp file over the old one, which a Windows JVM refuses
            // once the file exists. The value is set in memory before that save, and memory is all
            // this test reads, so that one failure is let through and any other is thrown.
            if (error.message?.startsWith("Failed to rename temp file") != true) throw error
        }
    }

    private companion object {
        const val QuietApp = "com.example.quiet"
        const val OtherApp = "com.example.other"
    }
}
