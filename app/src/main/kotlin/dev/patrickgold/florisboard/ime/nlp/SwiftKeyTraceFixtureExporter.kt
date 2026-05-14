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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object SwiftKeyTraceFixtureExporter {
    private val json = Json

    fun exportSuggestionFixtures(traceJsonl: String): List<String> {
        val fixtures = mutableListOf<String>()
        var pendingSuggestion: JsonObject? = null
        var caseIndex = 1

        for (event in traceJsonl.lineSequence().mapNotNull { line -> line.toTraceEventOrNull() }) {
            when (event.string("type")) {
                "suggestion" -> {
                    pendingSuggestion?.let { suggestion ->
                        fixtures.add(suggestion.toFixture(caseIndex++, expectedSpacebarText = null, outcomeTag = null))
                    }
                    pendingSuggestion = event
                }
                "autoCommitAccepted" -> {
                    val suggestion = pendingSuggestion
                    if (suggestion != null && event.matchesSuggestion(suggestion)) {
                        fixtures.add(
                            suggestion.toFixture(
                                caseIndex = caseIndex++,
                                expectedSpacebarText = event.string("candidate").takeIf { it.isNotBlank() },
                                outcomeTag = AutoCommitAcceptedTag,
                            )
                        )
                        pendingSuggestion = null
                    }
                }
                "autoCommitRejected" -> {
                    val suggestion = pendingSuggestion
                    if (suggestion != null && event.matchesSuggestion(suggestion)) {
                        fixtures.add(
                            suggestion.toFixture(
                                caseIndex = caseIndex++,
                                expectedSpacebarText = null,
                                outcomeTag = AutoCommitRejectedTag,
                            )
                        )
                        pendingSuggestion = null
                    }
                }
            }
        }

        pendingSuggestion?.let { suggestion ->
            fixtures.add(suggestion.toFixture(caseIndex, expectedSpacebarText = null, outcomeTag = null))
        }
        return fixtures
    }

    private fun String.toTraceEventOrNull(): JsonObject? {
        val trimmed = trim()
        if (trimmed.isBlank() || trimmed.startsWith("#")) return null
        return runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
    }

    private fun JsonObject.toFixture(
        caseIndex: Int,
        expectedSpacebarText: String?,
        outcomeTag: String?,
    ): String {
        val currentWord = string("currentWord")
        val tags = buildJsonArray {
            add(JsonPrimitive(LocalTraceTag))
            if (outcomeTag != null) {
                add(JsonPrimitive(outcomeTag))
            }
        }
        val expectedRoles = expectedRolesByText()
        return buildJsonObject {
            put("name", JsonPrimitive("local trace suggestion $caseIndex"))
            put("type", JsonPrimitive("suggestion"))
            put("tags", tags)
            put("currentWord", JsonPrimitive(currentWord))
            put("typedWordKnown", JsonPrimitive(boolean("typedWordKnown")))
            put("quickPredictionInsert", JsonPrimitive(currentWord.isBlank() && expectedSpacebarText != null))
            put("touchEvidence", array("touchEvidence"))
            put("scored", sanitizedScoredCandidates())
            put("expectedRanked", array("ranked"))
            put("expectedSpacebarText", expectedSpacebarText?.let { JsonPrimitive(it) } ?: JsonNull)
            if (expectedRoles.isNotEmpty()) {
                put("expectedRoles", JsonObject(expectedRoles))
            }
        }.toString()
    }

    private fun JsonObject.sanitizedScoredCandidates(): JsonArray {
        return JsonArray(
            array("scored").mapNotNull { element ->
                val scored = element as? JsonObject ?: return@mapNotNull null
                buildJsonObject {
                    put("text", JsonPrimitive(scored.string("text")))
                    put("source", JsonPrimitive(scored.string("source", SwiftKeyCandidateSource.Fallback.name)))
                    put("providerConfidence", JsonPrimitive(scored.double("providerConfidence", 0.5)))
                    put("autoCommitEligible", JsonPrimitive(scored.boolean("autoCommitEligible")))
                    put("dictionaryFrequency", JsonPrimitive(scored.double("dictionaryFrequency", 0.0)))
                    put("contextProbability", JsonPrimitive(scored.double("contextProbability", 0.0)))
                    put("languageConfidence", JsonPrimitive(scored.double("languageConfidence", 1.0)))
                    put(
                        "acceptedCorrectionConfidence",
                        JsonPrimitive(scored.double("acceptedCorrectionConfidence", 0.0)),
                    )
                    put("rejectionPenalty", JsonPrimitive(scored.double("rejectionPenalty", 0.0)))
                }
            }
        )
    }

    private fun JsonObject.expectedRolesByText(): Map<String, JsonElement> {
        return buildMap {
            for (element in array("scored")) {
                val scored = element as? JsonObject ?: continue
                val text = scored.string("text")
                val role = scored.string("role")
                if (text.isNotBlank() && role.isNotBlank()) {
                    put(text, JsonPrimitive(role))
                }
            }
        }
    }

    private fun JsonObject.matchesSuggestion(suggestion: JsonObject): Boolean {
        val outcomeWord = string("original")
            .ifBlank { string("currentWord") }
            .trim()
        val suggestionWord = suggestion.string("currentWord").trim()
        return outcomeWord.isBlank() ||
            suggestionWord.isBlank() ||
            outcomeWord.equals(suggestionWord, ignoreCase = true)
    }

    private fun JsonObject.string(name: String, defaultValue: String = ""): String {
        return (get(name) as? JsonPrimitive)?.content ?: defaultValue
    }

    private fun JsonObject.boolean(name: String, defaultValue: Boolean = false): Boolean {
        return (get(name) as? JsonPrimitive)?.booleanOrNull ?: defaultValue
    }

    private fun JsonObject.double(name: String, defaultValue: Double): Double {
        return (get(name) as? JsonPrimitive)?.doubleOrNull ?: defaultValue
    }

    private fun JsonObject.array(name: String): JsonArray {
        return get(name) as? JsonArray ?: JsonArray(emptyList())
    }

    private const val LocalTraceTag = "local-trace"
    private const val AutoCommitAcceptedTag = "auto-commit-accepted"
    private const val AutoCommitRejectedTag = "auto-commit-rejected"
}
