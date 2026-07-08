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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

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

    test("learnWord persists Room locale tags instead of legacy debug strings") {
        val body = extractFunctionBody(locateDictionaryManagerSource().readText(), "fun learnWord(")

        body shouldContain "locale = locale.localeTag()"
        body shouldNotContain "locale = locale.toString()"
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

private fun locateDictionaryManagerSource(): File {
    val candidates = listOf(
        File("app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt"),
        File("src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt"),
    )
    return candidates.firstOrNull { it.exists() }
        ?: error("DictionaryManager.kt not reachable from working directory ${File(".").absolutePath}")
}

private fun extractFunctionBody(source: String, startsWith: String): String {
    val declStart = source.indexOf(startsWith)
    require(declStart >= 0) { "Function declaration '$startsWith' not found in source" }
    val openBrace = source.indexOf('{', declStart)
    require(openBrace >= 0) { "Function '$startsWith' has no opening brace" }

    var depth = 0
    var i = openBrace
    while (i < source.length) {
        when (source[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return source.substring(openBrace, i + 1)
            }
        }
        i++
    }
    error("Function '$startsWith' is missing its closing brace")
}

private class RepairUserDictionaryDao : UserDictionaryDao {
    private val rows = mutableListOf<UserDictionaryEntry>()
    private var nextId = 1L

    fun seed(word: String, freq: Int, locale: String?) {
        rows += UserDictionaryEntry(
            id = nextId++,
            word = word,
            freq = freq,
            locale = locale,
            shortcut = null,
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
        throw AssertionError("RepairUserDictionaryDao.$name should not be called by legacy locale repair")
}
