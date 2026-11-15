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

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Drives the real [SubtypeManager] through field focus changes, so the parts [LocaleHintsTest]
 * can't see are covered: a hinted language is never saved as the user's own, a language picked
 * by hand holds until another app gets focus, and a field without a hint goes back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SubtypeManagerLocaleHintsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs by FlorisPreferenceStore
    private val english = Subtype.DEFAULT.copy(id = 101L, primaryLocale = FlorisLocale.from("en", "US"))
    private val german = Subtype.DEFAULT.copy(id = 102L, primaryLocale = FlorisLocale.from("de", "DE"))
    // Each test's manager is stopped afterwards, so one left from an earlier test can't save its own
    // choice over the store this one reads.
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var manager: SubtypeManager

    @Before
    fun setUp(): Unit = runBlocking {
        savePref { prefs.localization.followAppLanguageHints.set(true) }
        savePref { prefs.localization.rememberSubtypePerAppEnabled.set(false) }
        savePref { prefs.localization.perAppSubtypeMemory.set(PerAppSubtypeMemory.EmptyJson) }
        // The list goes in first: the app's own manager may pick the first subtype for an id it
        // doesn't know, and that's English either way.
        savePref { prefs.localization.subtypes.set(SubtypeJsonConfig.encodeToString(listOf(english, german))) }
        savePref { prefs.localization.activeSubtypeId.set(english.id) }
        manager = SubtypeManager(context, managerScope)
        waitFor {
            manager.subtypes.size == 2 && manager.activeSubtype.id == english.id &&
                prefs.localization.activeSubtypeId.get() == english.id
        }
    }

    @After
    fun tearDown(): Unit = runBlocking {
        managerScope.cancel()
        savePref { prefs.localization.followAppLanguageHints.set(true) }
        savePref { prefs.localization.rememberSubtypePerAppEnabled.set(false) }
        savePref { prefs.localization.perAppSubtypeMemory.set(PerAppSubtypeMemory.EmptyJson) }
        savePref { prefs.localization.activeSubtypeId.set(Subtype.DEFAULT.id) }
        savePref { prefs.localization.subtypes.set("[]") }
    }

    @Test
    fun aHintedLanguageShowsWithoutBecomingTheUsersOwnAndAFieldWithoutOneGoesBack(): Unit = runBlocking {
        manager.onEditorPackageFocus("com.example.chat", hintLocales = listOf(Locale.GERMANY))
        manager.activeSubtype.id shouldBe german.id
        settle()
        prefs.localization.activeSubtypeId.get() shouldBe english.id

        manager.onEditorPackageFocus("com.example.notes")
        manager.activeSubtype.id shouldBe english.id
    }

    @Test
    fun aLanguagePickedByHandHoldsInThatAppUntilAnotherAppGetsFocus(): Unit = runBlocking {
        manager.onEditorPackageFocus("com.example.chat")
        manager.switchToSubtypeById(german.id).join()
        manager.activeSubtype.id shouldBe german.id
        // Unlike a hinted one, a language picked by hand becomes the user's own.
        waitFor { prefs.localization.activeSubtypeId.get() == german.id }

        // Another English field in the same app keeps the hand-picked German.
        manager.onEditorPackageFocus("com.example.chat", hintLocales = listOf(Locale.US))
        manager.activeSubtype.id shouldBe german.id

        // A different app asking for English gets it.
        manager.onEditorPackageFocus("com.example.mail", hintLocales = listOf(Locale.US))
        manager.activeSubtype.id shouldBe english.id
    }

    @Test
    fun aLanguagePickedByHandStopsHoldingOnceItsRemoved(): Unit = runBlocking {
        val french = Subtype.DEFAULT.copy(id = 103L, primaryLocale = FlorisLocale.from("fr", "FR"))
        savePref { prefs.localization.subtypes.set(SubtypeJsonConfig.encodeToString(listOf(english, german, french))) }
        waitFor { manager.subtypes.size == 3 }
        manager.onEditorPackageFocus("com.example.chat")
        manager.switchToSubtypeById(german.id).join()
        waitFor { prefs.localization.activeSubtypeId.get() == german.id }

        // Removed while the app keeps focus, as from the keyboard's own settings.
        manager.removeSubtype(manager.subtypes.first { it.id == german.id })
        waitFor { manager.activeSubtype.id == english.id && prefs.localization.activeSubtypeId.get() == english.id }

        // The pick is gone, so it no longer keeps the same app's fields from following their hint.
        manager.onEditorPackageFocus("com.example.chat", hintLocales = listOf(Locale.FRANCE))
        manager.activeSubtype.id shouldBe french.id
    }

    @Test
    fun clearingRememberedAppsForgetsThem(): Unit = runBlocking {
        val memory = PerAppSubtypeMemory.remember(
            rawJson = PerAppSubtypeMemory.EmptyJson,
            packageName = "com.example.chat",
            subtypeId = german.id,
            availableSubtypeIds = setOf(english.id, german.id),
        )
        savePref { prefs.localization.rememberSubtypePerAppEnabled.set(true) }
        savePref { prefs.localization.perAppSubtypeMemory.set(memory) }
        manager.clearPerAppSubtypeMemory().join()
        prefs.localization.perAppSubtypeMemory.get() shouldBe PerAppSubtypeMemory.EmptyJson
    }

    @Test
    fun theAppsOwnLanguageCountsWhenTheFieldGivesNoHint(): Unit = runBlocking {
        manager.onEditorPackageFocus("com.example.shop", appLocales = { listOf(Locale.GERMAN) })
        manager.activeSubtype.id shouldBe german.id
    }

    @Test
    fun aHintedLanguageStaysWhenTheKeyboardListChanges(): Unit = runBlocking {
        manager.onEditorPackageFocus("com.example.chat", hintLocales = listOf(Locale.GERMANY))
        manager.activeSubtype.id shouldBe german.id
        // A Tasker layout switch saves the list again, which used to put the saved English back.
        manager.switchActiveSubtypeCharactersLayout("qwertz") shouldBe true
        waitFor { prefs.localization.subtypes.get().contains("qwertz") }
        settle()
        manager.activeSubtype.id shouldBe german.id
        manager.activeSubtype.layoutMap.characters.componentId shouldBe "qwertz"
        prefs.localization.activeSubtypeId.get() shouldBe english.id

        // The hinted language still counts as one, so a field without a hint goes back.
        manager.onEditorPackageFocus("com.example.notes")
        manager.activeSubtype.id shouldBe english.id
    }

    @Test
    fun removingTheUsersOwnLanguageWhileAHintedOneShowsLeavesThemAnother(): Unit = runBlocking {
        manager.onEditorPackageFocus("com.example.chat", hintLocales = listOf(Locale.GERMANY))
        manager.activeSubtype.id shouldBe german.id
        manager.removeSubtype(manager.subtypes.first { it.id == english.id })
        // German is all that's left, so it becomes the user's own instead of a deleted id staying saved.
        waitFor { prefs.localization.activeSubtypeId.get() == german.id }
        manager.activeSubtype.id shouldBe german.id
    }

    @Test
    fun aFieldFocusedBeforeTheKeyboardsLoadGetsItsLanguageOnceTheyDo(): Unit = runBlocking {
        emptyTheListKeepingEnglishSaved()
        manager.onEditorPackageFocus("com.example.chat", hintLocales = listOf(Locale.GERMANY))

        // German goes first, so saving the hinted German can't pass for falling back to the first one.
        savePref { prefs.localization.subtypes.set(SubtypeJsonConfig.encodeToString(listOf(german, english))) }
        waitFor { manager.subtypes.size == 2 && manager.activeSubtype.id == german.id }
        settle()
        manager.activeSubtype.id shouldBe german.id
        prefs.localization.activeSubtypeId.get() shouldBe english.id
    }

    @Test
    fun aLanguageRememberedForTheAppBeatsTheFieldsHintEvenBeforeTheKeyboardsLoad(): Unit = runBlocking {
        val memory = PerAppSubtypeMemory.remember(
            rawJson = PerAppSubtypeMemory.EmptyJson,
            packageName = "com.example.chat",
            subtypeId = german.id,
            availableSubtypeIds = setOf(english.id, german.id),
        )
        savePref { prefs.localization.rememberSubtypePerAppEnabled.set(true) }
        savePref { prefs.localization.perAppSubtypeMemory.set(memory) }
        emptyTheListKeepingEnglishSaved()
        // The field asks for English, but German is remembered for this app.
        manager.onEditorPackageFocus("com.example.chat", hintLocales = listOf(Locale.US))

        savePref { prefs.localization.subtypes.set(SubtypeJsonConfig.encodeToString(listOf(english, german))) }
        waitFor { manager.subtypes.size == 2 && manager.activeSubtype.id == german.id }
        settle()
        // A remembered language is saved as the user's own, as it was before hints. The memory
        // itself survived the moment the list was empty instead of being dropped as gone.
        prefs.localization.activeSubtypeId.get() shouldBe german.id
        prefs.localization.perAppSubtypeMemory.get() shouldBe memory
    }

    @Test
    fun turningHintsOffKeepsTheUsersLanguage(): Unit = runBlocking {
        savePref { prefs.localization.followAppLanguageHints.set(false) }
        manager.onEditorPackageFocus("com.example.chat", hintLocales = listOf(Locale.GERMANY))
        manager.activeSubtype.id shouldBe english.id
    }

    private suspend fun savePref(write: suspend () -> Result<*>) {
        // The app loads the store in the background and swaps it in when done, which would drop a
        // value set before then.
        waitFor { (context as FlorisApplication).preferenceStoreLoaded.value }
        write().onFailure { error ->
            // JetPref saves by renaming a temp file over the old one, which a Windows JVM refuses
            // once the file exists (other saves in the app can land at any time, so deleting it
            // first isn't enough). The value is set in memory before that save, and memory is all
            // these tests read, so that one failure is let through and any other is thrown.
            if (error.message?.startsWith("Failed to rename temp file") != true) throw error
        }
    }

    /**
     * Leaves the manager with no keyboards, as right after a cold start. An empty list saves the
     * placeholder as the user's own, so English is put back once that has landed.
     */
    private suspend fun emptyTheListKeepingEnglishSaved() {
        savePref { prefs.localization.subtypes.set("[]") }
        waitFor { manager.subtypes.isEmpty() && prefs.localization.activeSubtypeId.get() == Subtype.DEFAULT.id }
        settle()
        savePref { prefs.localization.activeSubtypeId.set(english.id) }
    }

    /** Lets a save the manager may have launched in the background land before reading it. */
    private suspend fun settle() {
        delay(300)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun waitFor(condition: () -> Boolean) {
        withTimeout(10_000) {
            while (!condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(20)
            }
        }
    }
}
