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
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PersonalNgramLearnedEntriesTest {
    private lateinit var context: Context
    private lateinit var locale: FlorisLocale

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        locale = FlorisLocale.fromTag("en")
    }

    @Test
    fun bigramSnapshotExposesPersistedEntriesAndExactForgetRemovesOneRow() = runTest {
        val store = PersonalBigramStore.get(context)
        store.resetAndAwait()
        File(context.filesDir, "personal_bigrams_en.tsv").writeText(
            """
            swift floris 4 1800000000100
            swift keyboard 3 1800000000200
            floris keyboard 2 1800000000300
            """.trimIndent().replace(' ', '\t') + "\n",
        )

        store.snapshot().map { "${it.prev} ${it.next}" }.shouldContainExactly(
            "swift floris",
            "swift keyboard",
            "floris keyboard",
        )

        store.forgetExactAndAwait("swift", "keyboard", locale)

        store.snapshot().map { "${it.prev} ${it.next}" }.shouldContainExactly(
            "swift floris",
            "floris keyboard",
        )
        store.resetAndAwait()
    }

    @Test
    fun trigramSnapshotExposesPersistedEntriesAndExactForgetRemovesOneRow() = runTest {
        val store = PersonalTrigramStore.get(context)
        store.resetAndAwait()
        File(context.filesDir, "personal_trigrams_en.tsv").writeText(
            """
            the quick brown 4 1800000000100
            the quick fox 3 1800000000200
            quick brown fox 2 1800000000300
            """.trimIndent().replace(' ', '\t') + "\n",
        )

        store.snapshot().map { "${it.prev2} ${it.prev1} ${it.next}" }.shouldContainExactly(
            "the quick brown",
            "the quick fox",
            "quick brown fox",
        )

        store.forgetExactAndAwait("the", "quick", "fox", locale)

        store.snapshot().map { "${it.prev2} ${it.prev1} ${it.next}" }.shouldContainExactly(
            "the quick brown",
            "quick brown fox",
        )
        store.resetAndAwait()
    }
}
