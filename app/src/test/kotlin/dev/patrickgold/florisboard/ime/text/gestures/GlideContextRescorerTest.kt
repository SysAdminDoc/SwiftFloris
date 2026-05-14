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

package dev.patrickgold.florisboard.ime.text.gestures

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GlideContextRescorerTest : FunSpec({
    val fixtureCases = GlideContextReplayFixtureParser.parse("swiftkey/replay/glide_context_cases.jsonl")

    test("following context can rescue an ambiguous short glide word") {
        val replacement = GlideContextRescorer.chooseReplacement(
            committedWord = "in",
            candidateWords = listOf("in", "I'm", "on"),
            nextWord = "going",
            contextScores = mapOf(
                "i'm" to 0.44,
                "in" to 0.0,
            ),
        )

        replacement shouldBe "I'm"
    }

    test("weak context does not override the top glide candidate") {
        val replacement = GlideContextRescorer.chooseReplacement(
            committedWord = "to",
            candidateWords = listOf("to", "go", "too"),
            nextWord = "the",
            contextScores = mapOf(
                "go" to 0.30,
                "to" to 0.22,
            ),
        )

        replacement shouldBe null
    }

    test("long words are not retroactively replaced by short-context rescoring") {
        val replacement = GlideContextRescorer.chooseReplacement(
            committedWord = "through",
            candidateWords = listOf("through", "though"),
            nextWord = "the",
            contextScores = mapOf("though" to 1.0),
        )

        replacement shouldBe null
    }

    test("checked-in glide context fixtures cover SwiftKey-like rescoring gaps") {
        fixtureCases.map { it.name } shouldContainAll listOf(
            "contraction before going",
            "preposition before the",
            "contraction before be",
            "weak context no override",
            "long word no override",
            "punctuated next word no override",
        )
    }

    test("checked-in glide context fixtures replay through the rescorer") {
        for (case in fixtureCases) {
            case.replay().replacement shouldBe case.expectedReplacement
        }
    }

    test("checked-in glide context fixtures expose aggregate parity metrics") {
        val outcomes = fixtureCases.map { it.replay() }
        val metrics = GlideContextReplayMetrics.from(outcomes)

        metrics.caseCount shouldBe fixtureCases.size
        metrics.replacementHitCount shouldBe metrics.caseCount
        metrics.caseCountByTag.getValue(GlideContextRescueTag) shouldBe 3
        metrics.replacementHitCountByTag.getValue(GlideContextRescueTag) shouldBe
            metrics.caseCountByTag.getValue(GlideContextRescueTag)
        metrics.caseCountByTag.getValue(GlideNoOpTag) shouldBe 3
        metrics.replacementHitCountByTag.getValue(GlideNoOpTag) shouldBe
            metrics.caseCountByTag.getValue(GlideNoOpTag)

        val strictMetrics = GlideContextReplayMetrics.from(
            fixtureCases.map {
                it.replay(tuning = GlideContextTuning(minContextScore = 0.90))
            }
        )
        (strictMetrics.replacementHitCount < metrics.replacementHitCount) shouldBe true
    }
})

private data class GlideContextReplayCase(
    val name: String,
    val tags: Set<String>,
    val committedWord: String,
    val candidateWords: List<String>,
    val nextWord: String,
    val contextScores: Map<String, Double>,
    val expectedReplacement: String?,
)

private data class GlideContextReplayOutcome(
    val case: GlideContextReplayCase,
    val replacement: String?,
)

private data class GlideContextReplayMetrics(
    val caseCount: Int,
    val replacementHitCount: Int,
    val caseCountByTag: Map<String, Int>,
    val replacementHitCountByTag: Map<String, Int>,
) {
    companion object {
        fun from(outcomes: List<GlideContextReplayOutcome>): GlideContextReplayMetrics {
            val caseCountsByTag = mutableMapOf<String, Int>()
            val replacementHitsByTag = mutableMapOf<String, Int>()
            for (outcome in outcomes) {
                val hit = outcome.replacement == outcome.case.expectedReplacement
                for (tag in outcome.case.tags) {
                    caseCountsByTag.increment(tag)
                    if (hit) {
                        replacementHitsByTag.increment(tag)
                    }
                }
            }
            return GlideContextReplayMetrics(
                caseCount = outcomes.size,
                replacementHitCount = outcomes.count { it.replacement == it.case.expectedReplacement },
                caseCountByTag = caseCountsByTag,
                replacementHitCountByTag = replacementHitsByTag,
            )
        }
    }
}

private fun GlideContextReplayCase.replay(
    tuning: GlideContextTuning = GlideContextTuning.Default,
): GlideContextReplayOutcome {
    return GlideContextReplayOutcome(
        case = this,
        replacement = GlideContextRescorer.chooseReplacement(
            committedWord = committedWord,
            candidateWords = candidateWords,
            nextWord = nextWord,
            contextScores = contextScores,
            tuning = tuning,
        ),
    )
}

private object GlideContextReplayFixtureParser {
    private val json = Json

    fun parse(resourcePath: String): List<GlideContextReplayCase> {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(resourcePath)) {
            "Missing glide context replay fixture: $resourcePath"
        }
        return stream.bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line -> parseLine(json.parseToJsonElement(line).jsonObject) }
                .toList()
        }
    }

    private fun parseLine(json: JsonObject): GlideContextReplayCase {
        return GlideContextReplayCase(
            name = json.getString("name"),
            tags = json.getArrayOrNull("tags")?.toStringSet().orEmpty(),
            committedWord = json.getString("committedWord"),
            candidateWords = json.getArray("candidateWords").toStringList(),
            nextWord = json.getString("nextWord"),
            contextScores = json.getObject("contextScores").toDoubleMap(),
            expectedReplacement = json.optNullableString("expectedReplacement"),
        )
    }

    private fun JsonObject.toDoubleMap(): Map<String, Double> {
        return entries.associate { (key, value) ->
            key to ((value as? JsonPrimitive)?.doubleOrNull ?: 0.0)
        }
    }

    private fun JsonArray.toStringList(): List<String> {
        return map { it.jsonPrimitive.content }
    }

    private fun JsonArray.toStringSet(): Set<String> {
        return mapTo(linkedSetOf()) { it.jsonPrimitive.content }
    }

    private fun JsonObject.optNullableString(name: String): String? {
        val value = get(name) ?: return null
        if (value is JsonNull) return null
        return value.jsonPrimitive.content
    }

    private fun JsonObject.getString(name: String): String {
        return requireNotNull(get(name)) { "Missing string field: $name" }.jsonPrimitive.content
    }

    private fun JsonObject.getArray(name: String): JsonArray {
        return requireNotNull(get(name)) { "Missing array field: $name" }.jsonArray
    }

    private fun JsonObject.getArrayOrNull(name: String): JsonArray? {
        val value = get(name) ?: return null
        if (value is JsonNull) return null
        return value.jsonArray
    }

    private fun JsonObject.getObject(name: String): JsonObject {
        return requireNotNull(get(name)) { "Missing object field: $name" }.jsonObject
    }
}

private fun MutableMap<String, Int>.increment(key: String) {
    put(key, getOrDefault(key, 0) + 1)
}

private const val GlideContextRescueTag = "glide-context-rescue"
private const val GlideNoOpTag = "glide-no-op"
