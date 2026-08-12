/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.dictionary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.nlp.NextWordSuggestionContext
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PersonalNgramLearnedEntriesTest {
    companion object {
        private val StoreTestLock = Any()
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun runStoreTest(block: suspend TestScope.() -> Unit) = synchronized(StoreTestLock) {
        runTest(testBody = block)
    }

    @Test
    fun bigramSnapshotExposesPersistedEntriesAndExactForgetRemovesOneRow() = runStoreTest {
        val store = PersonalBigramStore.forTesting(context)
        val testLocale = FlorisLocale.fromTag("en")
        store.resetAndAwait()
        repeat(4) { store.learnAndAwait("swift", "floris", testLocale) }
        repeat(3) { store.learnAndAwait("swift", "keyboard", testLocale) }
        repeat(2) { store.learnAndAwait("floris", "keyboard", testLocale) }

        store.snapshot().map { "${it.prev} ${it.next}" }.shouldContainExactly(
            "swift floris",
            "swift keyboard",
            "floris keyboard",
        )

        store.forgetExactAndAwait("swift", "keyboard", testLocale)

        store.snapshot().map { "${it.prev} ${it.next}" }.shouldContainExactly(
            "swift floris",
            "floris keyboard",
        )
        store.resetAndAwait()
    }

    @Test
    fun bigramContextRejectionDemotesOnlyExactContinuationAndLearningClearsIt() = runStoreTest {
        val store = PersonalBigramStore.get(context)
        val testLocale = FlorisLocale.fromTag("fr")
        store.resetAndAwait()
        repeat(10) { store.learnAndAwait("swift", "keyboard", testLocale) }
        repeat(3) { store.learnAndAwait("swift", "floris", testLocale) }
        repeat(5) { store.learnAndAwait("floris", "keyboard", testLocale) }

        store.predict("swift", testLocale, max = 2).first() shouldBe "keyboard"
        val originalRejectedScore = store.score("swift", "keyboard", testLocale)
        val otherContextScore = store.score("floris", "keyboard", testLocale)

        store.rejectContinuationAndAwait("swift", "keyboard", testLocale) shouldBe true

        store.predict("swift", testLocale, max = 2).first() shouldBe "floris"
        store.score("swift", "keyboard", testLocale) shouldBeLessThan originalRejectedScore
        store.score("floris", "keyboard", testLocale) shouldBe otherContextScore
        store.rejectionPenalty("floris", "keyboard", testLocale) shouldBe 0.0

        store.learnAndAwait("swift", "keyboard", testLocale)
        store.rejectionPenalty("swift", "keyboard", testLocale) shouldBe 0.0
        store.resetAndAwait()
    }

    @Test
    fun trigramSnapshotExposesPersistedEntriesAndExactForgetRemovesOneRow() = runStoreTest {
        val store = PersonalTrigramStore.forTesting(context)
        val testLocale = FlorisLocale.fromTag("de")
        store.resetAndAwait()
        repeat(4) { store.learnAndAwait("the", "quick", "brown", testLocale) }
        repeat(3) { store.learnAndAwait("the", "quick", "fox", testLocale) }
        repeat(2) { store.learnAndAwait("quick", "brown", "fox", testLocale) }

        store.snapshot().map { "${it.prev2} ${it.prev1} ${it.next}" }.shouldContainExactly(
            "the quick brown",
            "the quick fox",
            "quick brown fox",
        )

        store.forgetExactAndAwait("the", "quick", "fox", testLocale)

        store.snapshot().map { "${it.prev2} ${it.prev1} ${it.next}" }.shouldContainExactly(
            "the quick brown",
            "quick brown fox",
        )
        store.resetAndAwait()
    }

    @Test
    fun trigramContextRejectionDemotesOnlyExactContinuationAndLearningClearsIt() = runStoreTest {
        val store = PersonalTrigramStore.get(context)
        val testLocale = FlorisLocale.fromTag("es")
        store.resetAndAwait()
        repeat(10) { store.learnAndAwait("the", "quick", "fox", testLocale) }
        repeat(3) { store.learnAndAwait("the", "quick", "brown", testLocale) }
        repeat(5) { store.learnAndAwait("quick", "fox", "jumps", testLocale) }

        store.predict("the", "quick", testLocale, max = 2).first() shouldBe "fox"
        val originalRejectedScore = store.score("the", "quick", "fox", testLocale)
        val otherContextScore = store.score("quick", "fox", "jumps", testLocale)

        store.rejectContinuationAndAwait("the", "quick", "fox", testLocale) shouldBe true

        store.predict("the", "quick", testLocale, max = 2).first() shouldBe "brown"
        store.score("the", "quick", "fox", testLocale) shouldBeLessThan originalRejectedScore
        store.score("quick", "fox", "jumps", testLocale) shouldBe otherContextScore
        store.rejectionPenalty("quick", "fox", "jumps", testLocale) shouldBe 0.0

        store.learnAndAwait("the", "quick", "fox", testLocale)
        store.rejectionPenalty("the", "quick", "fox", testLocale) shouldBe 0.0
        store.resetAndAwait()
    }

    @Test
    fun latinProviderRemovalRecordsNextWordContextRejectionInsteadOfGlobalForget() = runStoreTest {
        val testLocale = FlorisLocale.fromTag("it")
        val bigramStore = PersonalBigramStore.get(context)
        val trigramStore = PersonalTrigramStore.get(context)
        bigramStore.resetAndAwait()
        trigramStore.resetAndAwait()
        val provider = LatinLanguageProvider(context)
        val candidate = WordSuggestionCandidate(
            text = "Keyboard",
            isEligibleForUserRemoval = true,
            sourceProvider = provider,
            nextWordContext = NextWordSuggestionContext(
                previousWord = "swift",
                secondPreviousWord = "floris",
            ),
        )

        provider.removeSuggestion(
            subtype = Subtype.DEFAULT.copy(primaryLocale = testLocale),
            candidate = candidate,
        ) shouldBe true

        bigramStore.rejectionPenalty("swift", "keyboard", testLocale) shouldBeGreaterThan 0.0
        trigramStore.rejectionPenalty("floris", "swift", "keyboard", testLocale) shouldBeGreaterThan 0.0
        bigramStore.rejectionPenalty("floris", "keyboard", testLocale) shouldBe 0.0
        bigramStore.snapshot().map { it.next } shouldBe emptyList()
        trigramStore.snapshot().map { it.next } shouldBe emptyList()
        bigramStore.resetAndAwait()
        trigramStore.resetAndAwait()
    }

    @Test
    fun malformedBigramFileDoesNotInstallPartialRowsOrReplaceTheSource() = runStoreTest {
        val store = PersonalBigramStore.forTesting(context)
        val locale = FlorisLocale.fromTag("en")
        val file = context.filesDir.resolve("personal_bigrams_${locale.languageTag()}.tsv")
        store.resetAndAwait()
        file.writeText("swift\tfloris\t4\t100\nbroken-row\n")
        val originalBytes = file.readBytes().toList()

        shouldThrow<PersonalNgramPersistence.LoadException> {
            store.predict("swift", locale, max = 1)
        }

        store.loadState(locale) shouldBe PersonalNgramPersistence.LoadState.UNREADABLE
        file.readBytes().toList() shouldBe originalBytes
        store.resetAndAwait()
    }

    @Test
    fun malformedTrigramFileDoesNotInstallPartialRowsOrReplaceTheSource() = runStoreTest {
        val store = PersonalTrigramStore.forTesting(context)
        val locale = FlorisLocale.fromTag("en")
        val file = context.filesDir.resolve("personal_trigrams_${locale.languageTag()}.tsv")
        store.resetAndAwait()
        file.writeText("the\tquick\tbrown\t4\t100\nbroken-row\n")
        val originalBytes = file.readBytes().toList()

        shouldThrow<PersonalNgramPersistence.LoadException> {
            store.predict("the", "quick", locale, max = 1)
        }

        store.loadState(locale) shouldBe PersonalNgramPersistence.LoadState.UNREADABLE
        file.readBytes().toList() shouldBe originalBytes
        store.resetAndAwait()
    }
}
