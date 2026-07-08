/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.dictionary.FlorisUserDictionaryDatabase
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryOverlay
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LearnedWordPersistenceRoundTripTest {
    private lateinit var database: FlorisUserDictionaryDatabase

    @Before
    fun setUp() {
        UserDictionaryOverlay.resetForTest()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FlorisUserDictionaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        UserDictionaryOverlay.resetForTest()
    }

    @Test
    fun learnedWordRoundTripsFromRoomHydrationToPrefixSuggestion() {
        val locale = FlorisLocale.fromTag("en")
        val otherLocale = FlorisLocale.fromTag("es")
        val dao = database.userDictionaryDao()

        dao.insert(
            UserDictionaryEntry(
                id = 0,
                word = "zorbulate",
                freq = UserDictionaryOverlay.INITIAL_FREQUENCY,
                locale = locale.localeTag(),
                shortcut = null,
            ),
        )

        dao.queryAll(locale).map { it.word to it.locale }
            .shouldContainExactly("zorbulate" to locale.localeTag())
        dao.queryAll(otherLocale) shouldBe emptyList()

        val overlay = UserDictionaryOverlay.get()
        overlay.hydrateLocale(
            locale = locale,
            entries = dao.queryAll(locale).map { entry -> entry.word.lowercase() to entry.freq },
        )
        overlay.snapshotFor(locale) shouldBe mapOf(
            "zorbulate" to UserDictionaryOverlay.INITIAL_FREQUENCY,
        )

        val suggestions = LatinDictionarySuggester.suggest(
            rawWord = "zor",
            dictionary = latinDictionary(
                "zonal" to 190,
                "zone" to 180,
                "zoo" to 170,
            ),
            maxCandidateCount = 4,
            userOverlay = overlay.viewFor(locale),
        )

        suggestions.first().text shouldBe "zorbulate"
    }
}

private fun latinDictionary(vararg words: Pair<String, Int>): LatinDictionarySnapshot {
    val frequencies = words.toMap()
    return LatinDictionarySnapshot(
        frequencies = frequencies,
        sortedWords = frequencies.keys.sorted(),
    )
}
