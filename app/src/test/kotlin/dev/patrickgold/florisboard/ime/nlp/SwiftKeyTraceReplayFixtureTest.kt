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

class SwiftKeyTraceReplayFixtureTest : FunSpec({
    val cases = SwiftKeyTraceReplayFixtureParser.parse("swiftkey/replay/trace_replay_cases.jsonl")

    test("checked-in trace fixtures cover the current SwiftKey parity gaps") {
        cases.map { it.name } shouldContainAll listOf(
            "row-gap adjacent correction",
            "adjacent transposition correction",
            "mixed-language literal protection",
            "empty-field quick prediction insertion",
            "rejected correction demotion",
            "missing-letter correction",
            "extra-letter correction",
            "double-letter correction",
        )
    }

    test("checked-in trace fixtures replay through the ranker") {
        for (case in cases) {
            val ranked = SwiftKeyCandidateRanker.rank(
                context = case.context,
                preferred = case.preferred,
                fallback = case.fallback,
            )
            ranked.map { it.text.toString() } shouldBe case.expectedRankedText
            SwiftKeyCandidateRanker.selectSpacebarCandidate(
                currentWord = case.context.currentWord,
                candidates = ranked,
                quickPredictionInsert = case.quickPredictionInsert,
            )?.text?.toString() shouldBe case.expectedSpacebarText

            val scored = SwiftKeyCandidateRanker.scoreCandidates(
                context = case.context,
                preferred = case.preferred,
                fallback = case.fallback,
            )
            for ((text, expectedRole) in case.expectedRolesByText) {
                scored.first { it.candidate.text.toString() == text }.score.role.name shouldBe expectedRole
            }
        }
    }
})

private data class TraceReplayCase(
    val name: String,
    val context: SwiftKeyDecoderContext,
    val preferred: List<SuggestionCandidate>,
    val fallback: List<SuggestionCandidate>,
    val quickPredictionInsert: Boolean,
    val expectedRankedText: List<String>,
    val expectedSpacebarText: String?,
    val expectedRolesByText: Map<String, String>,
)

private object SwiftKeyTraceReplayFixtureParser {
    private val json = Json

    fun parse(resourcePath: String): List<TraceReplayCase> {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(resourcePath)) {
            "Missing SwiftKey trace replay fixture: $resourcePath"
        }
        return stream.bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line -> parseLine(json.parseToJsonElement(line).jsonObject) }
                .toList()
        }
    }

    private fun parseLine(json: JsonObject): TraceReplayCase {
        val currentWord = json.getString("currentWord")
        val scored = json.getArray("scored")
        val candidates = parseCandidates(scored)
        val signals = parseSignals(scored)
        return TraceReplayCase(
            name = json.getString("name"),
            context = SwiftKeyDecoderContext(
                currentWord = currentWord,
                maxCandidateCount = json.getInt("maxCandidateCount", 8),
                typedWordKnown = json.getBoolean("typedWordKnown", false),
                touchEvidence = parseTouchEvidence(json.getArrayOrNull("touchEvidence")),
                candidateSignals = signals,
            ),
            preferred = candidates.getValue(SwiftKeyCandidateSource.Preferred),
            fallback = candidates.getValue(SwiftKeyCandidateSource.Fallback),
            quickPredictionInsert = json.getBoolean("quickPredictionInsert", false),
            expectedRankedText = json.getArray("expectedRanked").toStringList(),
            expectedSpacebarText = json.optNullableString("expectedSpacebarText"),
            expectedRolesByText = json.getObjectOrNull("expectedRoles")?.toStringMap().orEmpty(),
        )
    }

    private fun parseCandidates(scored: JsonArray): Map<SwiftKeyCandidateSource, List<SuggestionCandidate>> {
        val candidates = mutableMapOf(
            SwiftKeyCandidateSource.Preferred to mutableListOf<SuggestionCandidate>(),
            SwiftKeyCandidateSource.Fallback to mutableListOf<SuggestionCandidate>(),
        )
        for (item in scored.map { it.jsonObject }) {
            val source = SwiftKeyCandidateSource.valueOf(item.getString("source"))
            candidates.getValue(source).add(
                WordSuggestionCandidate(
                    text = item.getString("text"),
                    confidence = item.getDouble("providerConfidence", 0.5).coerceIn(0.0, 1.0),
                    isEligibleForAutoCommit = item.getBoolean("autoCommitEligible", false),
                )
            )
        }
        return candidates
    }

    private fun parseSignals(scored: JsonArray): Map<String, SwiftKeyCandidateSignals> {
        return buildMap {
            for (item in scored.map { it.jsonObject }) {
                put(
                    item.getString("text").lowercase(),
                    SwiftKeyCandidateSignals(
                        dictionaryFrequency = item.getDouble("dictionaryFrequency", 0.0),
                        contextProbability = item.getDouble("contextProbability", 0.0),
                        languageConfidence = item.getDouble("languageConfidence", 1.0),
                        rejectionPenalty = item.getDouble("rejectionPenalty", 0.0),
                    ),
                )
            }
        }
    }

    private fun parseTouchEvidence(samples: JsonArray?): TouchDecoderEvidence? {
        if (samples == null || samples.isEmpty()) return null
        return TouchDecoderEvidence(
            samples = buildList {
                for (sample in samples.map { it.jsonObject }) {
                    add(
                        TouchDecoderSample(
                            primaryText = sample.getString("primaryText"),
                            alternatives = sample.getArray("alternatives").toTouchCandidates(),
                        )
                    )
                }
            }
        )
    }

    private fun JsonArray.toTouchCandidates(): List<TouchDecoderCandidate> {
        return buildList {
            for (candidate in this@toTouchCandidates.map { it.jsonObject }) {
                add(
                    TouchDecoderCandidate(
                        text = candidate.getString("text"),
                        confidence = candidate.getDouble("confidence", 0.0),
                    )
                )
            }
        }
    }

    private fun JsonArray.toStringList(): List<String> {
        return map { it.jsonPrimitive.content }
    }

    private fun JsonObject.toStringMap(): Map<String, String> {
        return entries.associate { (key, value) -> key to value.jsonPrimitive.content }
    }

    private fun JsonObject.optNullableString(name: String): String? {
        val value = get(name) ?: return null
        if (value is JsonNull) return null
        return value.jsonPrimitive.content
    }

    private fun JsonObject.getString(name: String): String {
        return requireNotNull(get(name)) { "Missing string field: $name" }.jsonPrimitive.content
    }

    private fun JsonObject.getBoolean(name: String, defaultValue: Boolean): Boolean {
        val primitive = get(name) as? JsonPrimitive ?: return defaultValue
        return primitive.content.toBooleanStrictOrNull() ?: defaultValue
    }

    private fun JsonObject.getInt(name: String, defaultValue: Int): Int {
        val primitive = get(name) as? JsonPrimitive ?: return defaultValue
        return primitive.content.toIntOrNull() ?: defaultValue
    }

    private fun JsonObject.getDouble(name: String, defaultValue: Double): Double {
        val primitive = get(name) as? JsonPrimitive ?: return defaultValue
        return primitive.doubleOrNull ?: defaultValue
    }

    private fun JsonObject.getArray(name: String): JsonArray {
        return requireNotNull(get(name)) { "Missing array field: $name" }.jsonArray
    }

    private fun JsonObject.getArrayOrNull(name: String): JsonArray? {
        val value = get(name) ?: return null
        if (value is JsonNull) return null
        return value.jsonArray
    }

    private fun JsonObject.getObjectOrNull(name: String): JsonObject? {
        val value = get(name) ?: return null
        if (value is JsonNull) return null
        return value.jsonObject
    }
}
