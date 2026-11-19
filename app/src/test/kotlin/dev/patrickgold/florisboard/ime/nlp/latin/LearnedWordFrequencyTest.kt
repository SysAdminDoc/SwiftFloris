/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The frequency the candidate ranker and glide typing read for a word the user taught the keyboard. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LearnedWordFrequencyTest {
    private lateinit var provider: LatinLanguageProvider
    private val subtype = Subtype.DEFAULT.copy(primaryLocale = FlorisLocale.fromTag("en-US"))

    @Before
    fun setUp() {
        UserDictionaryOverlay.resetForTest()
        provider = LatinLanguageProvider(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        UserDictionaryOverlay.resetForTest()
    }

    @Test
    fun aWordOnlyTheUserTaughtGetsFrequencyCredit() {
        runBlocking {
            provider.getFrequencyForWord(subtype, "wegovy") shouldBe 0.0

            UserDictionaryOverlay.get().learn("wegovy", subtype.primaryLocale)

            provider.getFrequencyForWord(subtype, "Wegovy") shouldBeGreaterThan 0.9
        }
    }

    @Test
    fun learningKeepsADictionaryWordAtLeastAsStrongAsBefore() {
        runBlocking {
            val before = provider.getFrequencyForWord(subtype, "the")

            UserDictionaryOverlay.get().learn("the", subtype.primaryLocale)

            (provider.getFrequencyForWord(subtype, "the") >= before) shouldBe true
        }
    }
}
