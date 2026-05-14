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
            "short o-i substitution correction",
            "extra-letter correction",
            "double-letter correction",
            "accepted correction prior promotion",
            "rejected spatial correction demotion",
            "phrase continuation after let me",
            "same-prefix bilingual literal protection",
            "secondary-language auto-commit protection",
        )
    }

    test("checked-in trace fixtures replay through the ranker") {
        for (case in cases) {
            val outcome = case.replay()
            outcome.rankedText shouldBe case.expectedRankedText
            outcome.spacebarText shouldBe case.expectedSpacebarText
            for ((text, expectedRole) in case.expectedRolesByText) {
                outcome.rolesByText.getValue(text) shouldBe expectedRole
            }
        }
    }

    test("checked-in trace fixtures expose aggregate parity outcome metrics") {
        val outcomes = cases.map { it.replay() }
        val metrics = ReplayOutcomeMetrics.from(outcomes)

        metrics.caseCount shouldBe cases.size
        metrics.fullRankingHitCount shouldBe metrics.caseCount
        metrics.spacebarHitCount shouldBe metrics.spacebarAssertionCount
        metrics.roleHitCount shouldBe metrics.roleAssertionCount
        metrics.typedLiteralProtectionMissCount shouldBe 0
        metrics.caseCountByTag.getValue(BilingualTokenProtectionTag) shouldBe 2
        metrics.fullRankingHitCountByTag.getValue(BilingualTokenProtectionTag) shouldBe
            metrics.caseCountByTag.getValue(BilingualTokenProtectionTag)
        metrics.typedLiteralProtectionMissCountByTag[BilingualTokenProtectionTag] shouldBe 0

        val conservativeSpatialMetrics = ReplayOutcomeMetrics.from(
            cases.map {
                it.replay(
                    tuning = SwiftKeyCandidateTuning(spatialCorrectionScoreThreshold = 0.99),
                )
            }
        )
        (conservativeSpatialMetrics.roleHitCount < metrics.roleHitCount) shouldBe true
    }
})

private data class TraceReplayCase(
    val name: String,
    val tags: Set<String>,
    val context: SwiftKeyDecoderContext,
    val preferred: List<SuggestionCandidate>,
    val fallback: List<SuggestionCandidate>,
    val quickPredictionInsert: Boolean,
    val expectedRankedText: List<String>,
    val expectedSpacebarText: String?,
    val expectedRolesByText: Map<String, String>,
)

private data class TraceReplayOutcome(
    val case: TraceReplayCase,
    val rankedText: List<String>,
    val spacebarText: String?,
    val rolesByText: Map<String, String>,
)

private data class ReplayOutcomeMetrics(
    val caseCount: Int,
    val fullRankingHitCount: Int,
    val spacebarAssertionCount: Int,
    val spacebarHitCount: Int,
    val roleAssertionCount: Int,
    val roleHitCount: Int,
    val typedLiteralProtectionMissCount: Int,
    val caseCountByTag: Map<String, Int>,
    val fullRankingHitCountByTag: Map<String, Int>,
    val typedLiteralProtectionMissCountByTag: Map<String, Int>,
) {
    companion object {
        fun from(outcomes: List<TraceReplayOutcome>): ReplayOutcomeMetrics {
            var roleAssertions = 0
            var roleHits = 0
            var typedLiteralProtectionMisses = 0
            val caseCountsByTag = mutableMapOf<String, Int>()
            val fullRankingHitsByTag = mutableMapOf<String, Int>()
            val typedLiteralProtectionMissesByTag = mutableMapOf<String, Int>()
            for (outcome in outcomes) {
                val fullRankingHit = outcome.rankedText == outcome.case.expectedRankedText
                val typedLiteralProtectionMiss =
                    outcome.case.typedLiteralProtectionExpected() && outcome.spacebarText != null
                for ((text, expectedRole) in outcome.case.expectedRolesByText) {
                    roleAssertions += 1
                    if (outcome.rolesByText[text] == expectedRole) {
                        roleHits += 1
                    }
                }
                if (typedLiteralProtectionMiss) {
                    typedLiteralProtectionMisses += 1
                }
                for (tag in outcome.case.tags) {
                    caseCountsByTag.increment(tag)
                    if (fullRankingHit) {
                        fullRankingHitsByTag.increment(tag)
                    }
                    if (typedLiteralProtectionMiss) {
                        typedLiteralProtectionMissesByTag.increment(tag)
                    } else {
                        typedLiteralProtectionMissesByTag.putIfAbsent(tag, 0)
                    }
                }
            }
            return ReplayOutcomeMetrics(
                caseCount = outcomes.size,
                fullRankingHitCount = outcomes.count { it.rankedText == it.case.expectedRankedText },
                spacebarAssertionCount = outcomes.count { it.case.expectedSpacebarText != null },
                spacebarHitCount = outcomes.count {
                    it.case.expectedSpacebarText != null && it.spacebarText == it.case.expectedSpacebarText
                },
                roleAssertionCount = roleAssertions,
                roleHitCount = roleHits,
                typedLiteralProtectionMissCount = typedLiteralProtectionMisses,
                caseCountByTag = caseCountsByTag,
                fullRankingHitCountByTag = fullRankingHitsByTag,
                typedLiteralProtectionMissCountByTag = typedLiteralProtectionMissesByTag,
            )
        }
    }
}

private fun TraceReplayCase.replay(
    tuning: SwiftKeyCandidateTuning = SwiftKeyCandidateTuning.Default,
): TraceReplayOutcome {
    val ranked = SwiftKeyCandidateRanker.rank(
        context = context,
        preferred = preferred,
        fallback = fallback,
        tuning = tuning,
    )
    val scored = SwiftKeyCandidateRanker.scoreCandidates(
        context = context,
        preferred = preferred,
        fallback = fallback,
        tuning = tuning,
    )
    return TraceReplayOutcome(
        case = this,
        rankedText = ranked.map { it.text.toString() },
        spacebarText = SwiftKeyCandidateRanker.selectSpacebarCandidate(
            currentWord = context.currentWord,
            candidates = ranked,
            quickPredictionInsert = quickPredictionInsert,
        )?.text?.toString(),
        rolesByText = scored.associate { it.candidate.text.toString() to it.score.role.name },
    )
}

private fun TraceReplayCase.typedLiteralProtectionExpected(): Boolean {
    return context.currentWord.isNotBlank() &&
        context.typedWordKnown &&
        expectedRankedText.contains(context.currentWord) &&
        expectedSpacebarText == null
}

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
            tags = json.getArrayOrNull("tags")?.toStringSet().orEmpty(),
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
                        acceptedCorrectionConfidence = item.getDouble("acceptedCorrectionConfidence", 0.0),
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

    private fun JsonArray.toStringSet(): Set<String> {
        return mapTo(linkedSetOf()) { it.jsonPrimitive.content }
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

private fun MutableMap<String, Int>.increment(key: String) {
    put(key, getOrDefault(key, 0) + 1)
}

private const val BilingualTokenProtectionTag = "bilingual-token-protection"
