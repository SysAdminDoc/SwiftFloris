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

package dev.patrickgold.florisboard.ime.dictionary

import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.time.measureTime

class DictionaryManagerTest : FunSpec({
    test("rankUserDictionaryCandidates prefers internal entries over system duplicates") {
        val ranked = rankUserDictionaryCandidates(
            query = "swi",
            candidates = listOf(
                candidate("SwiftFloris", freq = 255, sourcePriority = 1, matchPriority = 1),
                candidate("SwiftFloris", freq = 128, sourcePriority = 0, matchPriority = 1),
                candidate("swiftly", freq = 220, sourcePriority = 1, matchPriority = 1),
            ),
        )

        ranked.map { it.word } shouldBe listOf("SwiftFloris", "swiftly")
        ranked.first().freq shouldBe 128
    }

    test("rankUserDictionaryCandidates puts shortcut expansions ahead of word completions") {
        val ranked = rankUserDictionaryCandidates(
            query = "omw",
            candidates = listOf(
                candidate("omelet", freq = 255, sourcePriority = 0, matchPriority = 1),
                candidate("On my way", freq = 128, shortcut = "omw", sourcePriority = 0, matchPriority = 0),
            ),
        )

        ranked.map { it.word } shouldBe listOf("On my way", "omelet")
    }

    test("rankUserDictionaryCandidates removes exact current word from suggestion candidates") {
        val ranked = rankUserDictionaryCandidates(
            query = "SwiftFloris",
            candidates = listOf(
                candidate("SwiftFloris", freq = 255, sourcePriority = 0, matchPriority = 1),
                candidate("SwiftFloris keyboard", freq = 128, sourcePriority = 0, matchPriority = 1),
            ),
        )

        ranked.map { it.word } shouldBe listOf("SwiftFloris keyboard")
    }

    test("parseLegacyDebugLocaleTag restores Room-compatible locale tags") {
        parseLegacyDebugLocaleTag("FlorisLocale { l=en c=US v= }") shouldBe "en_US"
        parseLegacyDebugLocaleTag(" FlorisLocale { l=ja c=JP v=POSIX } ") shouldBe "ja_JP_POSIX"
        parseLegacyDebugLocaleTag("FlorisLocale { l= c=US v= }") shouldBe null
        parseLegacyDebugLocaleTag("not a locale") shouldBe null
    }

    test("repairLegacyLearnedLocaleRows rewrites debug locales and merges duplicates by max frequency") {
        val dao = RepairUserDictionaryDao().apply {
            seed(word = "patrick", freq = 245, locale = "en_US")
            seed(word = "Patrick", freq = 250, locale = "FlorisLocale { l=en c=US v= }")
            seed(word = "globalword", freq = 200, locale = "FlorisLocale { l= c= v= }")
        }

        val result = repairLegacyLearnedLocaleRows(dao)

        result shouldBe LegacyLearnedLocaleRepairResult(rewritten = 1, merged = 1)
        dao.queryAll().map { it.word to it.locale }.shouldContainExactlyInAnyOrder(
            "patrick" to "en_US",
            "globalword" to null,
        )
        dao.queryAll().first { it.word == "patrick" }.freq shouldBe 250
    }

    test("removeAutoLearnedMisspellings drops only auto-learned English rows of listed misspellings") {
        val dao = RepairUserDictionaryDao().apply {
            seed(word = "teh", freq = 245, locale = "en_US")
            seed(word = "untill", freq = 250, locale = "en_GB")
            seed(word = "thier", freq = 250, locale = "en")
            // Hand-added at the default frequency: kept.
            seed(word = "alot", freq = FREQUENCY_DEFAULT, locale = "en_US")
            // Typed case means the user added it by hand: kept.
            seed(word = "Recieve", freq = 250, locale = "en_US")
            // A shortcut entry is a deliberate expansion: kept.
            seed(word = "wich", freq = 250, locale = "en_US", shortcut = "wch")
            // Not English, or for every language: kept.
            seed(word = "nad", freq = 250, locale = "de_DE")
            seed(word = "taht", freq = 250, locale = null)
            // Not a listed misspelling: kept.
            seed(word = "swiftfloris", freq = 250, locale = "en_US")
        }
        val misspellings = setOf("teh", "untill", "thier", "alot", "recieve", "wich", "nad", "taht")

        val removed = removeAutoLearnedMisspellings(dao, misspellings)

        removed.map { it.word }.shouldContainExactlyInAnyOrder("teh", "untill", "thier")
        dao.queryAll().map { it.word }.shouldContainExactlyInAnyOrder(
            "alot", "Recieve", "wich", "nad", "taht", "swiftfloris",
        )
    }

    test("removeAutoLearnedMisspellings leaves the dictionary alone when nothing is pending") {
        val dao = RepairUserDictionaryDao().apply {
            seed(word = "teh", freq = 250, locale = "en_US")
        }

        removeAutoLearnedMisspellings(dao, emptySet()) shouldBe emptyList()
        dao.queryAll().map { it.word } shouldBe listOf("teh")
    }

    test("pendingLearnedMisspellingCleanup skips words the marker already lists") {
        val table = setOf("teh", "untill", "thier")

        pendingLearnedMisspellingCleanup(table, null) shouldBe table
        pendingLearnedMisspellingCleanup(table, "") shouldBe table
        pendingLearnedMisspellingCleanup(table, "teh\nuntill\nthier\n") shouldBe emptySet()
        // A word added to the table after the last cleanup is still pending.
        pendingLearnedMisspellingCleanup(table, "teh\r\n untill \n") shouldBe setOf("thier")
    }

    test("isAutoLearnedMisspelling covers 240 to 255 and hyphenated English tags") {
        val misspellings = setOf("teh")
        fun row(freq: Int, locale: String = "en_US") =
            UserDictionaryEntry(id = 0, word = "teh", freq = freq, locale = locale, shortcut = null)

        // v1.8.26 learned new words at 240.
        isAutoLearnedMisspelling(row(240), misspellings) shouldBe true
        isAutoLearnedMisspelling(row(239), misspellings) shouldBe false
        isAutoLearnedMisspelling(row(250), misspellings) shouldBe true
        // Learning adds one per use up to 255, so the top of the scale is learned too.
        isAutoLearnedMisspelling(row(251), misspellings) shouldBe true
        isAutoLearnedMisspelling(row(255), misspellings) shouldBe true
        isAutoLearnedMisspelling(row(245, locale = "en-GB"), misspellings) shouldBe true
    }

    test("cleanUpLearnedMisspellingsOnce removes, forgets and records each word once") {
        val marker = File(Files.createTempDirectory("missp-cleanup").toFile(), LEARNED_MISSPELLING_CLEANUP_FILE)
        val dao = RepairUserDictionaryDao().apply {
            seed(word = "teh", freq = 245, locale = "en_US")
            seed(word = "alot", freq = FREQUENCY_DEFAULT, locale = "en_US")
        }
        val forgotten = mutableListOf<String>()
        val table = setOf("teh", "alot", "untill")

        val first = cleanUpLearnedMisspellingsOnce(dao, table, marker) { forgotten += it.word }

        first.shouldNotBeNull()
        first.removed.map { it.word } shouldBe listOf("teh")
        first.markerSaved.shouldBeTrue()
        forgotten shouldBe listOf("teh")
        dao.queryAll().map { it.word } shouldBe listOf("alot")
        marker.readLines() shouldBe listOf("alot", "teh", "untill")

        // Typed and learned again after the cleanup: the user meant it, so it stays.
        dao.seed(word = "teh", freq = 245, locale = "en_US")
        cleanUpLearnedMisspellingsOnce(dao, table, marker) { forgotten += it.word }.shouldBeNull()
        dao.queryAll().map { it.word }.shouldContainExactlyInAnyOrder("alot", "teh")
        forgotten shouldBe listOf("teh")
    }

    test("cleanUpLearnedMisspellingsOnce keeps words an earlier table had in the marker") {
        val marker = File(Files.createTempDirectory("missp-cleanup").toFile(), LEARNED_MISSPELLING_CLEANUP_FILE)
        marker.writeText("recieve\nteh\n")
        val dao = RepairUserDictionaryDao().apply {
            seed(word = "untill", freq = 250, locale = "en_GB")
        }

        val result = cleanUpLearnedMisspellingsOnce(dao, setOf("teh", "untill"), marker) {}

        result.shouldNotBeNull()
        result.removed.map { it.word } shouldBe listOf("untill")
        // "recieve" left the table but stays handled, so it isn't cleaned again if it comes back.
        marker.readLines() shouldBe listOf("recieve", "teh", "untill")
    }

    test("cleanUpLearnedMisspellingsOnce does nothing without a table") {
        val marker = File(Files.createTempDirectory("missp-cleanup").toFile(), LEARNED_MISSPELLING_CLEANUP_FILE)
        val dao = RepairUserDictionaryDao().apply {
            seed(word = "teh", freq = 245, locale = "en_US")
        }

        cleanUpLearnedMisspellingsOnce(dao, emptySet(), marker) { error("nothing to forget") }.shouldBeNull()
        marker.exists() shouldBe false
        dao.queryAll().map { it.word } shouldBe listOf("teh")
    }

    test("DictionarySyncBridge returns fallback when a blocking Room query exceeds budget") {
        val executor = Executors.newSingleThreadExecutor()
        var timedOut = false
        var result = "unset"
        try {
            val elapsed = measureTime {
                result = DictionarySyncBridge.runWithTimeout(
                    executor = executor,
                    timeoutMs = 25L,
                    fallback = "fallback",
                    onTimeout = { timedOut = true },
                ) {
                    Thread.sleep(250L)
                    "late"
                }
            }

            result shouldBe "fallback"
            timedOut.shouldBeTrue()
            (elapsed.inWholeMilliseconds < 200L).shouldBeTrue()
        } finally {
            executor.shutdownNow()
        }
    }

})

private fun candidate(
    word: String,
    freq: Int,
    shortcut: String? = null,
    sourcePriority: Int,
    matchPriority: Int,
): UserDictionaryCandidate {
    return UserDictionaryCandidate(
        entry = UserDictionaryEntry(
            id = 0,
            word = word,
            freq = freq,
            locale = null,
            shortcut = shortcut,
        ),
        sourcePriority = sourcePriority,
        matchPriority = matchPriority,
    )
}

private class RepairUserDictionaryDao : UserDictionaryDao {
    private val rows = mutableListOf<UserDictionaryEntry>()
    private var nextId = 1L

    fun seed(word: String, freq: Int, locale: String?, shortcut: String? = null) {
        rows += UserDictionaryEntry(
            id = nextId++,
            word = word,
            freq = freq,
            locale = locale,
            shortcut = shortcut,
        )
    }

    override fun queryAll(): List<UserDictionaryEntry> = rows.toList()

    override fun update(entry: UserDictionaryEntry) {
        val index = rows.indexOfFirst { it.id == entry.id }
        if (index >= 0) rows[index] = entry
    }

    override fun delete(entry: UserDictionaryEntry) {
        rows.removeAll { it.id == entry.id }
    }

    override fun query(word: String): List<UserDictionaryEntry> = unused("query(word)")
    override fun query(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> = unused("query(word, locale)")
    override fun queryShortcut(shortcut: String): List<UserDictionaryEntry> = unused("queryShortcut")
    override fun queryShortcut(shortcut: String, locale: FlorisLocale?): List<UserDictionaryEntry> = unused("queryShortcut")
    override fun queryAllReadOnlyTransaction(): List<UserDictionaryEntry> = unused("queryAllReadOnlyTransaction")
    override fun queryAll(locale: FlorisLocale?): List<UserDictionaryEntry> = unused("queryAll(locale)")
    override fun queryExact(word: String): List<UserDictionaryEntry> = unused("queryExact(word)")
    override fun queryExact(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> = unused("queryExact(word, locale)")
    override fun queryExactFuzzyLocale(word: String, locale: FlorisLocale?): List<UserDictionaryEntry> =
        unused("queryExactFuzzyLocale")
    override fun queryLanguageTagList(): List<String> = unused("queryLanguageTagList")
    override fun insert(entry: UserDictionaryEntry) = unused<Unit>("insert")
    override fun deleteAll() = unused<Unit>("deleteAll")

    private fun <T> unused(name: String): T =
        throw AssertionError("RepairUserDictionaryDao.$name should not be called by the learned-word repairs")
}
