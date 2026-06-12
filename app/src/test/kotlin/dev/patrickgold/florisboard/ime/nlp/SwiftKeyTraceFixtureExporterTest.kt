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

package dev.patrickgold.florisboard.ime.nlp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SwiftKeyTraceFixtureExporterTest : FunSpec({
    test("exports suggestion traces as replay fixtures without cursor context") {
        val fixtures = SwiftKeyTraceFixtureExporter.exportSuggestionFixtures(
            """
            {"type":"suggestion","timestampMs":123,"currentWord":"gello","textBeforeCursorLength":42,"previousWords":["please"],"typedWordKnown":false,"touchEvidence":[{"primaryText":"g","alternatives":[{"text":"h","confidence":0.78}]}],"ranked":["gello","hello","fello"],"scored":[{"text":"hello","source":"Fallback","role":"SpatialCorrection","providerConfidence":0.42,"autoCommitEligible":false,"dictionaryFrequency":0.90,"contextProbability":0.0,"languageConfidence":1.0,"acceptedCorrectionConfidence":0.0,"rejectionPenalty":0.0},{"text":"fello","source":"Fallback","role":"Other","providerConfidence":0.99,"autoCommitEligible":false,"dictionaryFrequency":0.20,"contextProbability":0.0,"languageConfidence":1.0,"acceptedCorrectionConfidence":0.0,"rejectionPenalty":0.0}]}
            """.trimIndent()
        )

        fixtures.size shouldBe 1
        val fixture = parseFixture(fixtures.single())
        fixture.string("name") shouldBe "local trace suggestion 1"
        fixture.string("currentWord") shouldBe "gello"
        fixture.containsKey("timestampMs") shouldBe false
        fixture.containsKey("textBeforeCursorLength") shouldBe false
        fixture.containsKey("previousWords") shouldBe false
        fixture.containsKey("touchEvidence") shouldBe false
        fixture.array("tags").toStringList() shouldContain "local-trace"
        fixture.array("expectedRanked").toStringList() shouldBe listOf("gello", "hello", "fello")
        fixture.getObject("expectedRoles").string("hello") shouldBe "SpatialCorrection"
    }

    test("pairs accepted autocorrect outcomes with the previous suggestion") {
        val fixtures = SwiftKeyTraceFixtureExporter.exportSuggestionFixtures(
            """
            {"type":"suggestion","currentWord":"teh","typedWordKnown":false,"touchEvidence":[],"ranked":["teh","the"],"scored":[{"text":"the","source":"Fallback","role":"AutoCorrection","providerConfidence":0.98,"autoCommitEligible":true}]}
            {"type":"autoCommitAccepted","original":"teh","candidate":"the","textBeforeCursorLength":12}
            """.trimIndent()
        )

        val fixture = parseFixture(fixtures.single())
        fixture.string("expectedSpacebarText") shouldBe "the"
        fixture.array("tags").toStringList() shouldContain "auto-commit-accepted"
    }

    test("pairs rejected autocorrect outcomes as no-spacebar fixtures") {
        val fixtures = SwiftKeyTraceFixtureExporter.exportSuggestionFixtures(
            """
            {"type":"suggestion","currentWord":"teh","typedWordKnown":false,"touchEvidence":[],"ranked":["teh","the"],"scored":[{"text":"the","source":"Fallback","role":"AutoCorrection","providerConfidence":0.98,"autoCommitEligible":true}]}
            {"type":"autoCommitRejected","currentWord":"teh","textBeforeCursorLength":12}
            """.trimIndent()
        )

        val fixture = parseFixture(fixtures.single())
        (fixture["expectedSpacebarText"] is JsonNull) shouldBe true
        fixture.array("tags").toStringList() shouldContain "auto-commit-rejected"
    }

    test("marks blank accepted predictions as quick prediction insertion fixtures") {
        val fixtures = SwiftKeyTraceFixtureExporter.exportSuggestionFixtures(
            """
            {"type":"suggestion","currentWord":"","typedWordKnown":false,"touchEvidence":[],"ranked":["I'm","I","it's"],"scored":[{"text":"I","source":"Fallback","role":"Other","providerConfidence":0.88,"autoCommitEligible":false}]}
            {"type":"autoCommitAccepted","original":"","candidate":"I","textBeforeCursorLength":0}
            """.trimIndent()
        )

        val fixture = parseFixture(fixtures.single())
        fixture.boolean("quickPredictionInsert") shouldBe true
        fixture.string("expectedSpacebarText") shouldBe "I"
    }
})

private val FixtureJson = Json

private fun parseFixture(source: String): JsonObject {
    return FixtureJson.parseToJsonElement(source).jsonObject
}

private fun JsonObject.string(name: String): String {
    return getValue(name).jsonPrimitive.content
}

private fun JsonObject.boolean(name: String): Boolean {
    return getValue(name).jsonPrimitive.boolean
}

private fun JsonObject.array(name: String): JsonArray {
    return getValue(name).jsonArray
}

private fun JsonObject.getObject(name: String): JsonObject {
    return getValue(name).jsonObject
}

private fun JsonArray.toStringList(): List<String> {
    return map { it.jsonPrimitive.content }
}
