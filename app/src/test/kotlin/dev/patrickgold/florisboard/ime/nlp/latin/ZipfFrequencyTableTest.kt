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

package dev.patrickgold.florisboard.ime.nlp.latin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File

class ZipfFrequencyTableTest : FunSpec({
    test("empty table is a passthrough for SCOWL frequency") {
        val table = ZipfFrequencyTable.Empty
        table.blendedFrequency("anything", 0.42) shouldBe 0.42
        table.zipfFor("anything").shouldBeNull()
        table.size shouldBe 0
    }

    test("parse skips blank lines, comments, malformed rows, and out-of-range Zipf values") {
        val tsv = """
            # subtitle frequency table
            the	7.5

            and	7.1
            invalid_row_no_tab
            three	cols	5.0
            zerofreq	0.5
            highfreq	8.5
            ok	5.16
        """.trimIndent()

        val table = ZipfFrequencyTable.parse("en", tsv)
        table.size shouldBe 3
        table.zipfFor("the") shouldBe 7.5f
        table.zipfFor("and") shouldBe 7.1f
        table.zipfFor("ok") shouldBe 5.16f
        table.zipfFor("zerofreq").shouldBeNull()
        table.zipfFor("highfreq").shouldBeNull()
        table.zipfFor("missing").shouldBeNull()
    }

    test("blend uses 0.6 SCOWL + 0.4 Zipf when both present") {
        val table = ZipfFrequencyTable.parse("en", "the\t7.5")
        val scowl = 0.8
        val expected = 0.6 * scowl + 0.4 * (7.5 / 8.0)
        table.blendedFrequency("the", scowl) shouldBe (expected plusOrMinus 1e-9)
    }

    test("blend returns Zipf-only when SCOWL is zero") {
        val table = ZipfFrequencyTable.parse("en", "okay\t5.0")
        // Word missing from SCOWL but present in subtitle corpus — common
        // conversational tokens like "okay" / "yeah" land here. The fallback
        // is `zipf / 8.0`, so this becomes 0.625 instead of 0.0.
        table.blendedFrequency("okay", 0.0) shouldBe (0.625 plusOrMinus 1e-9)
    }

    test("blend returns SCOWL unchanged when word missing from Zipf table") {
        val table = ZipfFrequencyTable.parse("en", "the\t7.5")
        table.blendedFrequency("kubernetes", 96 / 255.0) shouldBe (96 / 255.0)
    }

    test("Zipf normalisation clamps the [1, 8] range to [0, 1]") {
        val table = ZipfFrequencyTable.parse("en", "a\t8.0\nb\t1.0")
        table.blendedFrequency("a", 0.0) shouldBe (1.0 plusOrMinus 1e-9)
        table.blendedFrequency("b", 0.0) shouldBe (0.125 plusOrMinus 1e-9)
    }

    test("lookup is case-insensitive on the query side") {
        val table = ZipfFrequencyTable.parse("en", "Hello\t5.16")
        // Table normalises stored words to lowercase, query is also lowercased.
        table.zipfFor("HELLO") shouldBe 5.16f
        table.zipfFor("hello") shouldBe 5.16f
    }

    test("parse of null or blank input returns the Empty singleton") {
        ZipfFrequencyTable.parse("en", null) shouldBe ZipfFrequencyTable.Empty
        ZipfFrequencyTable.parse("en", "") shouldBe ZipfFrequencyTable.Empty
        ZipfFrequencyTable.parse("en", "   \n  \n") shouldBe ZipfFrequencyTable.Empty
    }

    test("bundled multilingual Zipf seed tables parse and expose common words") {
        val expectedWords = mapOf(
            "cs" to "že",
            "de" to "und",
            "es" to "que",
            "fr" to "est",
            "it" to "che",
            "pt" to "que",
        )

        expectedWords.forEach { (language, expectedWord) ->
            val rawTsv = bundledFreqAsset("$language.tsv")?.readText()
            val table = ZipfFrequencyTable.parse(language, rawTsv)

            table.size shouldBe 1000
            (table.zipfFor(expectedWord) != null) shouldBe true
        }
    }
})

private fun bundledFreqAsset(fileName: String): File? {
    return listOf(
        File("src/main/assets/freq/$fileName"),
        File("app/src/main/assets/freq/$fileName"),
    ).firstOrNull { it.isFile }
}
