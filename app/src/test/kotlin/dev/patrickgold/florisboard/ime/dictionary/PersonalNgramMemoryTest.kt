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
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * How long the personal next-word stores remember a phrase, and what they keep when a word has
 * more continuations than fit.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PersonalNgramMemoryTest {
    private lateinit var context: Context
    private val english = FlorisLocale.fromTag("en")
    private val dayMs = 86_400_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun bigramFile() = File(context.filesDir, "personal_bigrams_${english.languageTag()}.tsv")

    private fun trigramFile() = File(context.filesDir, "personal_trigrams_${english.languageTag()}.tsv")

    @Test
    fun newPairAfterABusyWordSurvivesTheSaveAndIsSuggestedWhenTypedAgain() = runTest {
        PersonalBigramStore.forTesting(context).resetAndAwait()
        val longAgo = System.currentTimeMillis() - 60 * dayMs
        bigramFile().writeText((1..40).joinToString("\n") { "the\tword$it\t2\t$longAgo" } + "\n")
        val store = PersonalBigramStore.forTesting(context)

        store.learnAndAwait("the", "gym", english)
        store.flushPendingAndAwait()
        store.learnAndAwait("the", "gym", english)
        store.flushPendingAndAwait()

        PersonalBigramStore.forTesting(context).predict("the", english, 3).first() shouldBe "gym"
        store.resetAndAwait()
    }

    @Test
    fun pairTypedTwiceIsStillSuggestedAfterAMonthAway() = runTest {
        PersonalBigramStore.forTesting(context).resetAndAwait()
        val now = System.currentTimeMillis()
        bigramFile().writeText(
            "see\ttomorrow\t2\t${now - 45 * dayMs}\n" +
                "see\tsoon\t2\t${now - 400 * dayMs}\n",
        )

        val predictions = PersonalBigramStore.forTesting(context).predict("see", english, 3)

        predictions shouldContain "tomorrow"
        predictions shouldNotContain "soon"
        PersonalBigramStore.forTesting(context).resetAndAwait()
    }

    @Test
    fun pairTypedOnceIsNotSuggestedYet() = runTest {
        val store = PersonalBigramStore.forTesting(context)
        store.resetAndAwait()

        store.learnAndAwait("see", "tomorrow", english)

        store.predict("see", english, 3) shouldNotContain "tomorrow"
        store.resetAndAwait()
    }

    @Test
    fun phrasesStartingWithIOrAAreLearned() = runTest {
        val bigrams = PersonalBigramStore.forTesting(context)
        val trigrams = PersonalTrigramStore.forTesting(context)
        bigrams.resetAndAwait()
        trigrams.resetAndAwait()

        repeat(2) {
            bigrams.learnAndAwait("I", "think", english)
            bigrams.learnAndAwait("a", "lot", english)
            trigrams.learnAndAwait("I", "think", "so", english)
        }

        bigrams.predict("I", english, 3) shouldContain "think"
        bigrams.predict("a", english, 3) shouldContain "lot"
        trigrams.predict("I", "think", english, 3) shouldContain "so"
        bigrams.resetAndAwait()
        trigrams.resetAndAwait()
    }

    @Test
    fun newPhraseInABusyContextSurvivesTheSave() = runTest {
        PersonalTrigramStore.forTesting(context).resetAndAwait()
        val longAgo = System.currentTimeMillis() - 60 * dayMs
        trigramFile().writeText((1..30).joinToString("\n") { "going\tto\tplace$it\t2\t$longAgo" } + "\n")
        val store = PersonalTrigramStore.forTesting(context)

        store.learnAndAwait("going", "to", "gym", english)
        store.flushPendingAndAwait()
        store.learnAndAwait("going", "to", "gym", english)
        store.flushPendingAndAwait()

        PersonalTrigramStore.forTesting(context).predict("going", "to", english, 3).first() shouldBe "gym"
        store.resetAndAwait()
    }
}
