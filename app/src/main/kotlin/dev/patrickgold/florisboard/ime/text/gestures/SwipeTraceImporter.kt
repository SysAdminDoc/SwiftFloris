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

import kotlinx.serialization.json.Json

/**
 * ROADMAP §6 N1.4 — importer for FUTO-style swipe trace datasets.
 *
 * The FUTO Hugging Face data card publishes traces as JSON Lines (one [SwipeTraceRecord] per line), with
 * the optional convenience of a single JSON array file. Both shapes parse through here. Malformed lines
 * are skipped with a logged rejection so a partially-corrupted dataset never blocks the entire benchmark
 * run.
 *
 * The importer is intentionally pure — no Android types, no file IO. Callers pass already-read text and
 * receive a `List<SwipeTraceRecord>`. This keeps the importer unit-testable without Robolectric or
 * temporary-file plumbing.
 */
object SwipeTraceImporter {

    private val JsonConfig = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Parse a string in either:
     * - JSON Lines: one [SwipeTraceRecord] per line.
     * - JSON Array: a single `[ { ... }, { ... }, ... ]` blob.
     *
     * @return the list of successfully-parsed records. Malformed records / lines are silently dropped;
     *  callers that need a rejection count should iterate themselves via [parseJsonLine].
     */
    fun parse(input: String): List<SwipeTraceRecord> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.startsWith("[")) parseJsonArray(trimmed) else parseJsonLines(trimmed)
    }

    /**
     * Parse a JSON-Lines blob, returning the successfully-parsed records and dropping malformed lines.
     */
    fun parseJsonLines(input: String): List<SwipeTraceRecord> {
        val out = ArrayList<SwipeTraceRecord>()
        for (rawLine in input.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            parseJsonLine(line)?.let { out.add(it) }
        }
        return out
    }

    /**
     * Parse a single JSON Lines record. Returns null on any parse / validation failure.
     */
    fun parseJsonLine(line: String): SwipeTraceRecord? {
        return runCatching { JsonConfig.decodeFromString(SwipeTraceRecord.serializer(), line) }.getOrNull()
    }

    private fun parseJsonArray(input: String): List<SwipeTraceRecord> {
        val outer = runCatching {
            JsonConfig.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SwipeTraceRecord.serializer()), input)
        }.getOrNull()
        return outer ?: emptyList()
    }
}
