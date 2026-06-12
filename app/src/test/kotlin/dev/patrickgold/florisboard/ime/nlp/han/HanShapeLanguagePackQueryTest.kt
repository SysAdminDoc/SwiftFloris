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

package dev.patrickgold.florisboard.ime.nlp.han

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HanShapeLanguagePackQueryTest {
    private lateinit var database: SQLiteDatabase

    @Before
    fun setUp() {
        database = SQLiteDatabase.create(null)
        database.execSQL("CREATE TABLE zhengma (code TEXT, text TEXT, weight DOUBLE)")
        insert(code = "a", text = "alpha", weight = 10.0)
        insert(code = "a", text = "alternate", weight = 5.0)
        insert(code = "ab", text = "alphabet", weight = 4.0)
        insert(code = "b", text = "beta", weight = 8.0)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun suggestionsComeFromInstalledTableRows() {
        val suggestions = HanShapeLanguagePackQuery.suggestions(
            database = database,
            table = "zhengma",
            composingText = "a",
            maxCandidateCount = 3,
            sourceProvider = null,
        )

        suggestions.map { it.text.toString() } shouldBe listOf("alpha", "alternate", "alphabet")
        suggestions.map { it.secondaryText.toString() } shouldBe listOf("a", "a", "ab")
        suggestions.map { it.confidence } shouldBe listOf(1.0, 0.5, 0.4)
        suggestions.map { it.isEligibleForAutoCommit } shouldBe listOf(true, false, false)
    }

    @Test
    fun wordListUsesTableTextByFrequency() {
        HanShapeLanguagePackQuery.words(database, "zhengma") shouldBe
            listOf("alpha", "beta", "alternate", "alphabet")
    }

    @Test
    fun frequencyLookupReturnsTableWeightForCommittedWord() {
        HanShapeLanguagePackQuery.frequencyForWord(database, "zhengma", "beta") shouldBe 8.0
        HanShapeLanguagePackQuery.containsWord(database, "zhengma", "beta") shouldBe true
        HanShapeLanguagePackQuery.frequencyForWord(database, "zhengma", "missing") shouldBe 0.0
        HanShapeLanguagePackQuery.containsWord(database, "zhengma", "missing") shouldBe false
    }

    @Test
    fun missingOrUnsafeTablesFailClosed() {
        HanShapeLanguagePackQuery.suggestions(database, "missing", "a", 3, null).shouldBeEmpty()
        HanShapeLanguagePackQuery.suggestions(database, "bad;table", "a", 3, null).shouldBeEmpty()
        HanShapeLanguagePackQuery.words(database, "missing").shouldBeEmpty()
        HanShapeLanguagePackQuery.frequencyForWord(database, "missing", "alpha") shouldBe 0.0
        HanShapeLanguagePackQuery.frequencyForWord(database, "bad;table", "alpha") shouldBe 0.0
    }

    private fun insert(code: String, text: String, weight: Double) {
        database.execSQL(
            "INSERT INTO zhengma (code, text, weight) VALUES (?, ?, ?)",
            arrayOf<Any>(code, text, weight),
        )
    }
}
