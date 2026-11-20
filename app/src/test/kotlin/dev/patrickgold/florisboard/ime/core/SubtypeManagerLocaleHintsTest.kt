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
import dev.patrickgold.florisboard.ime.keyboard.extCoreCurrencySet
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineDispatcher
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * Drives the real [SubtypeManager] through field focus changes, so the parts [LocaleHintsTest]
 * can't see are covered: a hinted language is never saved as the user's own, a language picked
 * by hand holds until another app gets focus, and a field without a hint goes back. It also covers the
 * language first-run setup adds, which has to become the user's own even when others are set up.
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
    /** The job of a manager a test starts on [PumpedWork], stopped afterwards the same way. */
    private val pumpedJob = SupervisorJob()
    private val saves = HeldSaves()
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
        manager = SubtypeManager(context, managerScope, saves)
        useAsTheAppsSubtypeManager(manager)
        waitFor {
            manager.subtypes.size == 2 && manager.activeSubtype.id == english.id &&
                prefs.localization.activeSubtypeId.get() == english.id
        }
    }

    @After
    fun tearDown(): Unit = runBlocking {
        managerScope.cancel()
        pumpedJob.cancel()
        saves.release()
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

    @Test
    fun aLanguageChosenInSetupOnAFreshProfileIsAddedAndBecomesTheUsersOwn(): Unit = runBlocking {
        savePref { prefs.localization.subtypes.set("[]") }
        waitFor { manager.subtypes.isEmpty() && prefs.localization.activeSubtypeId.get() == Subtype.DEFAULT.id }
        settle()

        manager.addAndActivateSubtype(german.copy(id = -1L))
        waitFor { manager.subtypes.size == 1 && manager.activeSubtype.primaryLocale == german.primaryLocale }
        settle()
        val added = manager.subtypes.single()
        added.id shouldNotBe -1L
        manager.activeSubtype.id shouldBe added.id
        prefs.localization.activeSubtypeId.get() shouldBe added.id
    }

    @Test
    fun aLanguageChosenInSetupIsSwitchedToEvenWhenOthersAreAlreadySetUp(): Unit = runBlocking {
        manager.addAndActivateSubtype(frenchToPick)
        // English, the first in the list and the one showing, must not win just for being there first.
        waitFor { manager.subtypes.size == 3 && manager.activeSubtype.primaryLocale == frenchToPick.primaryLocale }
        settle()
        val added = manager.subtypes.single { it.primaryLocale == frenchToPick.primaryLocale }
        manager.activeSubtype.id shouldBe added.id
        prefs.localization.activeSubtypeId.get() shouldBe added.id
    }

    @Test
    fun choosingALanguageAlreadySetUpSwitchesToItWithoutAddingItAgain(): Unit = runBlocking {
        manager.addAndActivateSubtype(german.copy(id = -1L))
        waitFor { manager.activeSubtype.id == german.id && prefs.localization.activeSubtypeId.get() == german.id }
        settle()
        manager.subtypes.map { it.id } shouldBe listOf(english.id, german.id)
        manager.activeSubtype.id shouldBe german.id
    }

    @Test
    fun pickingAnotherLanguageInSetupSwapsTheOneItAddedEvenBeforeTheFirstSaveLands(): Unit = runBlocking {
        saves.hold()
        val frenchId = manager.addAndActivateSubtype(frenchToPick).shouldNotBeNull()
        manager.subtypes.map { it.id } shouldBe listOf(english.id, german.id, frenchId)
        // French's saves are held back, so this only works if the manager already lists French.
        val italianId = manager.addAndActivateSubtype(italianToPick, replacing = frenchId).shouldNotBeNull()
        manager.subtypes.map { it.id } shouldBe listOf(english.id, german.id, italianId)
        saves.release()
        waitFor { savedLocaleTags() == listOf("en-US", "de-DE", "it-IT") }
        settle()
        savedLocaleTags() shouldBe listOf("en-US", "de-DE", "it-IT")
        manager.subtypes.map { it.id } shouldBe listOf(english.id, german.id, italianId)
        manager.activeSubtype.id shouldBe italianId
        prefs.localization.activeSubtypeId.get() shouldBe italianId

        // The same language again keeps it rather than adding it a second time.
        manager.addAndActivateSubtype(italianToPick, replacing = italianId) shouldBe italianId
        settle()
        manager.subtypes.map { it.id } shouldBe listOf(english.id, german.id, italianId)
    }

    @Test
    fun switchingFromTheAddedLanguageToOneSetUpBeforeTakesTheAddedOneOut(): Unit = runBlocking {
        saves.hold()
        val frenchId = manager.addAndActivateSubtype(frenchToPick).shouldNotBeNull()
        // French's save is held back, so this only works if the manager already lists French.
        manager.addAndActivateSubtype(german.copy(id = -1L), replacing = frenchId).shouldBeNull()
        manager.subtypes.map { it.id } shouldBe listOf(english.id, german.id)
        saves.release()
        waitFor { prefs.localization.activeSubtypeId.get() == german.id }
        settle()
        savedLocaleTags() shouldBe listOf("en-US", "de-DE")
        manager.subtypes.map { it.id } shouldBe listOf(english.id, german.id)
        manager.activeSubtype.id shouldBe german.id
    }

    @Test
    fun setupNeverTakesOutALanguageThatWasSetUpBeforeIt(): Unit = runBlocking {
        // Each pick passes on what the one before returned, as setup's language step does.
        // German was there before setup, so picking it leaves nothing for the next pick to replace.
        val afterGerman = manager.addAndActivateSubtype(german.copy(id = -1L))
        afterGerman.shouldBeNull()
        val frenchId = manager.addAndActivateSubtype(frenchToPick, replacing = afterGerman).shouldNotBeNull()
        // Back to German swaps out the French setup added, and again leaves nothing to replace.
        val backToGerman = manager.addAndActivateSubtype(german.copy(id = -1L), replacing = frenchId)
        backToGerman.shouldBeNull()
        val italianId = manager.addAndActivateSubtype(italianToPick, replacing = backToGerman).shouldNotBeNull()
        waitFor { prefs.localization.activeSubtypeId.get() == italianId }
        settle()
        manager.subtypes.map { it.id } shouldBe listOf(english.id, german.id, italianId)
        savedLocaleTags() shouldBe listOf("en-US", "de-DE", "it-IT")
    }

    @Test
    fun aPickMadeBeforeTheStoredListIsTakenKeepsTheLanguagesSetUpBefore(): Unit = runBlocking {
        managerScope.cancel()
        val work = PumpedWork()
        val fresh = SubtypeManager(context, CoroutineScope(work + pumpedJob), saves)
        // None of the new manager's own work has run, so its copy of the list is still empty.
        fresh.subtypes shouldBe emptyList()
        val frenchId = fresh.addAndActivateSubtype(frenchToPick).shouldNotBeNull()
        fresh.subtypes.map { it.id } shouldBe listOf(english.id, german.id, frenchId)

        waitFor {
            work.runAll()
            savedLocaleTags() == listOf("en-US", "de-DE", "fr-FR") &&
                prefs.localization.activeSubtypeId.get() == frenchId
        }
        settle()
        work.runAll()
        fresh.subtypes.map { it.id } shouldBe listOf(english.id, german.id, frenchId)
        fresh.activeSubtype.id shouldBe frenchId
    }

    @Test
    fun aRemovalStartsFromTheStoredListAndIsNotUndoneBeforeItsSaved(): Unit = runBlocking {
        val work = PumpedWork()
        val pumped = startManagerOn(work)
        // Stored by something else, and not taken yet, since the manager's own work hasn't run since.
        savePref {
            prefs.localization.subtypes.set(SubtypeJsonConfig.encodeToString(listOf(english, german, italianSetUp)))
        }
        saves.hold()
        pumped.removeSubtype(german)
        pumped.subtypes.map { it.id } shouldBe listOf(english.id, italianSetUp.id)
        // The stored list has German until the removal lands. Taking it would bring German back meanwhile.
        work.runUntil { pumped.subtypes.any { it.id == german.id } } shouldBe false
        pumped.subtypes.map { it.id } shouldBe listOf(english.id, italianSetUp.id)

        saves.release()
        waitFor {
            work.runAll()
            savedLocaleTags() == listOf("en-US", "it-IT")
        }
        settle()
        work.runAll()
        pumped.subtypes.map { it.id } shouldBe listOf(english.id, italianSetUp.id)
        pumped.activeSubtype.id shouldBe english.id
    }

    @Test
    fun aListStoredWhileSetupsPickWaitsToBeSavedDoesNotReplaceIt(): Unit = runBlocking {
        val work = PumpedWork()
        val pumped = startManagerOn(work)
        saves.hold()
        val frenchId = pumped.addAndActivateSubtype(frenchToPick).shouldNotBeNull()
        // Stored meanwhile by something else, without French. Taken, it would fall back to English.
        savePref { prefs.localization.subtypes.set(SubtypeJsonConfig.encodeToString(listOf(english, italianSetUp))) }
        work.runAll()
        pumped.subtypes.map { it.id } shouldBe listOf(english.id, german.id, frenchId)
        pumped.activeSubtype.id shouldBe frenchId

        saves.release()
        waitFor {
            work.runAll()
            savedLocaleTags() == listOf("en-US", "de-DE", "fr-FR") &&
                prefs.localization.activeSubtypeId.get() == frenchId
        }
        settle()
        work.runAll()
        pumped.subtypes.map { it.id } shouldBe listOf(english.id, german.id, frenchId)
        pumped.activeSubtype.id shouldBe frenchId
        prefs.localization.activeSubtypeId.get() shouldBe frenchId
    }

    @Test
    fun aListTakenJustBeforeSetupsPickIsNotDecidedOnAfterIt(): Unit = runBlocking {
        val work = PumpedWork()
        val pumped = startManagerOn(work)
        savePref {
            prefs.localization.subtypes.set(SubtypeJsonConfig.encodeToString(listOf(english, german, italianSetUp)))
        }
        // Stopped once the list is taken, with the decision on it still to run.
        work.runUntil { pumped.subtypes.map { it.id } == listOf(english.id, german.id, italianSetUp.id) } shouldBe true
        saves.hold()
        val frenchId = pumped.addAndActivateSubtype(frenchToPick).shouldNotBeNull()
        work.runAll()
        // Deciding on the list without French would have fallen back to English.
        pumped.activeSubtype.id shouldBe frenchId

        saves.release()
        waitFor {
            work.runAll()
            savedLocaleTags() == listOf("en-US", "de-DE", "it-IT", "fr-FR") &&
                prefs.localization.activeSubtypeId.get() == frenchId
        }
        settle()
        work.runAll()
        pumped.subtypes.map { it.id } shouldBe listOf(english.id, german.id, italianSetUp.id, frenchId)
        pumped.activeSubtype.id shouldBe frenchId
    }

    @Test
    fun aLayoutSwitchKeepsAChangeToTheSameKeyboardStillBeingSaved(): Unit = runBlocking {
        saves.hold()
        // A currency change from the keyboard's settings, not saved yet, so the active keyboard still has the old one.
        manager.modifySubtypeWithSameId(english.copy(currencySet = extCoreCurrencySet("euro")))
        manager.switchActiveSubtypeCharactersLayout("qwertz") shouldBe true
        saves.release()

        waitFor {
            val saved = SubtypeJsonConfig.decodeFromString<List<Subtype>>(prefs.localization.subtypes.get())
                .firstOrNull { it.id == english.id }
            saved != null && saved.layoutMap.characters.componentId == "qwertz" &&
                saved.currencySet.componentId == "euro"
        }
        settle()
        val active = manager.activeSubtype
        active.id shouldBe english.id
        active.currencySet.componentId shouldBe "euro"
        active.layoutMap.characters.componentId shouldBe "qwertz"
    }

    private val frenchToPick = Subtype.DEFAULT.copy(id = -1L, primaryLocale = FlorisLocale.from("fr", "FR"))
    private val italianToPick = Subtype.DEFAULT.copy(id = -1L, primaryLocale = FlorisLocale.from("it", "IT"))
    private val italianSetUp = italianToPick.copy(id = 103L)

    /**
     * Stops the manager [setUp] started and starts one whose own work runs only when [work] is pumped, so a test
     * can stop between two steps it takes one after the other.
     */
    private fun startManagerOn(work: PumpedWork): SubtypeManager {
        managerScope.cancel()
        val pumped = SubtypeManager(context, CoroutineScope(work + pumpedJob), saves)
        work.runUntil { pumped.subtypes.size == 2 && pumped.activeSubtype.id == english.id } shouldBe true
        return pumped
    }

    private fun savedLocaleTags(): List<String> {
        return SubtypeJsonConfig.decodeFromString<List<Subtype>>(prefs.localization.subtypes.get())
            .map { it.primaryLocale.languageTag() }
    }

    private suspend fun savePref(write: suspend () -> Result<*>) {
        // The app loads the store in the background and swaps it in when done, which would drop a
        // value set before then.
        waitFor { (context as FlorisApplication).preferenceStoreLoaded.value }
        // A save that never answers fails this test instead of hanging the whole run.
        withTimeout(10_000) { write() }.onFailure { error ->
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

    /**
     * The app makes its own SubtypeManager the first time anything asks for one, and addAndActivateSubtype
     * creates the app's KeyboardManager, which asks. Two managers on one store act on each other's held saves
     * (the app's one could put English back after this test's Italian landed), and a real app has only one,
     * so the app gets this test's manager instead.
     */
    private fun useAsTheAppsSubtypeManager(manager: SubtypeManager) {
        val app = context as FlorisApplication
        check(!app.subtypeManager.isInitialized()) { "The app made its own SubtypeManager before the test gave it one" }
        FlorisApplication::class.java.getDeclaredField("subtypeManager").apply { isAccessible = true }
            .set(app, lazyOf(manager))
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

/**
 * Runs the manager's saves one at a time in order, like the app's dispatcher, and can hold them back the way a
 * slow disk would, so a test can act before a save lands without racing it.
 */
private class HeldSaves : CoroutineDispatcher() {
    private val thread = Executors.newSingleThreadExecutor { task ->
        Thread(task, "held-saves").apply { isDaemon = true }
    }
    private val held = ArrayDeque<Runnable>()
    private var holding = false

    override fun dispatch(context: CoroutineContext, block: Runnable): Unit = synchronized(this) {
        if (holding) held.addLast(block) else thread.execute(block)
    }

    fun hold(): Unit = synchronized(this) { holding = true }

    fun release(): Unit = synchronized(this) {
        holding = false
        while (held.isNotEmpty()) thread.execute(held.removeFirst())
    }
}

/**
 * Keeps a manager's own work until a test runs it, on the test's thread, so the test decides what has happened
 * when it checks. Saves still go to [HeldSaves].
 */
private class PumpedWork : CoroutineDispatcher() {
    private val queue = ConcurrentLinkedQueue<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.add(block)
    }

    /** Runs work in the order it came, including work that comes meanwhile, until [done] or none is left. */
    fun runUntil(done: () -> Boolean): Boolean {
        while (!done()) {
            val next = queue.poll() ?: return false
            next.run()
        }
        return true
    }

    fun runAll() {
        runUntil { false }
    }
}
