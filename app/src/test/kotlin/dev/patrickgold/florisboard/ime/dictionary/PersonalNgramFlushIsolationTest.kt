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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class PersonalNgramFlushIsolationTest : FunSpec({
    val bigramSource = locateSource("PersonalBigramStore.kt")
    val trigramSource = locateSource("PersonalTrigramStore.kt")

    test("bigram flush only snapshots and writes the requested locale") {
        assertFlushIsLocaleScoped(
            source = bigramSource.readText(),
            fileName = bigramSource.name,
            filePrefix = "personal_bigrams_",
        )
    }

    test("trigram flush only snapshots and writes the requested locale") {
        assertFlushIsLocaleScoped(
            source = trigramSource.readText(),
            fileName = trigramSource.name,
            filePrefix = "personal_trigrams_",
        )
    }

    test("bigram learn threshold flushes the current locale tag") {
        assertLearnFlushesCurrentLocaleTag(bigramSource.readText(), bigramSource.name)
    }

    test("trigram learn threshold flushes the current locale tag") {
        assertLearnFlushesCurrentLocaleTag(trigramSource.readText(), trigramSource.name)
    }
})

private fun assertFlushIsLocaleScoped(source: String, fileName: String, filePrefix: String) {
    val flushBody = extractFunctionBody(source, "fun flush(localeTag: String)")
    flushBody shouldContain "tablesByLocale[localeTag]"
    flushBody shouldContain "lastSeenByLocale[localeTag]"
    flushBody shouldContain "pendingCommitsByLocale[localeTag]?.set(0)"
    flushBody shouldContain "fileFor(localeTag)"
    flushBody.contains(filePrefix) shouldBe false
    flushBody.contains("tablesByLocale.clear()") shouldBe false
    flushBody.contains("lastSeenByLocale.clear()") shouldBe false
    flushBody.contains("pendingCommitsByLocale.clear()") shouldBe false

    val resetBody = extractFunctionBody(source, "suspend fun resetAndAwait()")
    resetBody shouldContain "tablesByLocale.clear()"
    check(resetBody.contains(filePrefix)) {
        "$fileName resetAndAwait should be the only broad per-locale file cleanup path."
    }
}

private fun assertLearnFlushesCurrentLocaleTag(source: String, fileName: String) {
    val learnBody = extractFunctionBody(source, "fun learn(")
    learnBody shouldContain "val tag = locale.languageTag()"
    learnBody shouldContain "pendingCommitsByLocale.getOrPut(tag)"
    learnBody shouldContain "flush(tag)"
    check(!learnBody.contains("flush(localeTag)")) {
        "$fileName learn() must flush the current locale tag, not an outer or stale localeTag symbol."
    }
}

private fun locateSource(fileName: String): File {
    val source = File("app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/$fileName")
    check(source.exists() && source.canRead()) {
        "$fileName not reachable from working directory ${File(".").absolutePath}"
    }
    return source
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
                if (depth == 0) {
                    return source.substring(openBrace, i + 1)
                }
            }
        }
        i++
    }
    error("Function '$startsWith' is missing its closing brace")
}
