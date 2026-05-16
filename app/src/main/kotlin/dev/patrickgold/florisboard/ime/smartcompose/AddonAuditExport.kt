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

package dev.patrickgold.florisboard.ime.smartcompose

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ROADMAP matrix #39 — local-only audit export bundle.
 *
 * Turns the in-memory [AddonInvocationAudit] ring into a serialisable bundle a user can copy out of Settings →
 * Privacy → "Export audit log" for offline review, bug reports, or hand-off to a security reviewer. The bundle
 * is **not** an automated outbound channel — every export path goes through an explicit user action (copy to
 * clipboard, share intent, save to file) inside Settings, never as a background upload. The privacy posture in
 * §1 prohibits auto-shipped telemetry; this export is the inverse: the user holds the export and decides where
 * (if anywhere) it goes.
 *
 * ## What is exported
 *
 * Only the fields already in [AddonInvocationAudit.Record]:
 *
 * - `sequence` — monotonic counter, useful for cross-referencing if the bundle is paginated by a future export
 *   chunker.
 * - `timestampMillis` — UTC milliseconds since epoch from `System.currentTimeMillis()`.
 * - `timestampIso` — derived from [timestampMillis], `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`, UTC. Convenience field so
 *   the bundle is human-readable without running it through a parser.
 * - `surface` — `SMART_COMPOSE` / `TRANSLATION` / `MCP`.
 * - `outcome` — `ACCEPTED` / `SUPPRESSED` / `FAILED`.
 * - `reason` — the router-emitted categorical reason for SUPPRESSED / FAILED records.
 * - `subject` — the per-surface categorical identifier (editor package / language pair / `daemon::tool`).
 *
 * ## What is **not** exported
 *
 * - User-typed text. Never enters the ring; never enters this bundle.
 * - Candidate suggestions, translated content, tool parameters, tool results. Never enter the ring.
 * - Device identifiers, install id, build version, locale, screen orientation. The bundle is intentionally
 *   minimal — diagnostic context (build version, device model) belongs in the user's accompanying bug-report
 *   text, not in the audit bundle, so a user can paste only what they're willing to share.
 *
 * ## Format
 *
 * The bundle is JSON — both because every Settings surface that consumes it already speaks JSON, and because
 * it stays diffable across runs.
 *
 * ```
 * {
 *   "exportVersion": 1,
 *   "exportedAtIso": "2026-05-16T13:42:00.000Z",
 *   "totalCount": 137,
 *   "recordCount": 64,
 *   "records": [
 *     { "sequence": 74, "timestampMillis": 1747401600000, "timestampIso": "...", "surface": "MCP",
 *       "outcome": "SUPPRESSED", "reason": "tool calendar.next on daemon com.example.mcp disabled by user",
 *       "subject": "com.example.mcp::calendar.next" },
 *     ...
 *   ]
 * }
 * ```
 *
 * `recordCount` is the size of the snapshot at export time (capped at [AddonInvocationAudit.MAX_RECORDS]).
 * `totalCount` is the cumulative count since process start; if `totalCount > recordCount` the user knows
 * earlier records rolled off the FIFO ring.
 */
object AddonAuditExport {

    /** Bundle schema version. Bump on any load-bearing layout change. */
    const val EXPORT_VERSION: Int = 1

    /**
     * Build the bundle from a snapshot of the current audit ring. Always uses the live
     * [AddonInvocationAudit.snapshot] / [AddonInvocationAudit.totalCount] state at call time.
     */
    fun buildBundle(
        nowMillis: Long = System.currentTimeMillis(),
        records: List<AddonInvocationAudit.Record> = AddonInvocationAudit.snapshot(),
        totalCount: Long = AddonInvocationAudit.totalCount(),
    ): JsonObject {
        return JsonObject(linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "exportVersion" to JsonPrimitive(EXPORT_VERSION),
            "exportedAtIso" to JsonPrimitive(toIso(nowMillis)),
            "totalCount" to JsonPrimitive(totalCount),
            "recordCount" to JsonPrimitive(records.size),
            "records" to JsonArray(records.map(::recordToJson)),
        ))
    }

    /** Serialise the bundle to a stable JSON string. */
    fun toJsonString(
        nowMillis: Long = System.currentTimeMillis(),
        records: List<AddonInvocationAudit.Record> = AddonInvocationAudit.snapshot(),
        totalCount: Long = AddonInvocationAudit.totalCount(),
    ): String {
        val bundle = buildBundle(nowMillis = nowMillis, records = records, totalCount = totalCount)
        return Json.encodeToString(JsonObject.serializer(), bundle)
    }

    /** A short human-readable line summarising the bundle. Used by the Settings preview tile. */
    fun summaryLine(
        records: List<AddonInvocationAudit.Record> = AddonInvocationAudit.snapshot(),
        totalCount: Long = AddonInvocationAudit.totalCount(),
    ): String {
        val ringSize = records.size
        val rolled = (totalCount - ringSize).coerceAtLeast(0)
        return when {
            totalCount == 0L -> "Audit log is empty."
            rolled == 0L -> "$ringSize record(s) in the audit log."
            else -> "$ringSize record(s) in the audit log; $rolled earlier record(s) have rolled off the ring."
        }
    }

    private fun recordToJson(record: AddonInvocationAudit.Record): JsonObject {
        return JsonObject(linkedMapOf<String, kotlinx.serialization.json.JsonElement>().apply {
            put("sequence", JsonPrimitive(record.sequence))
            put("timestampMillis", JsonPrimitive(record.timestampMillis))
            put("timestampIso", JsonPrimitive(toIso(record.timestampMillis)))
            put("surface", JsonPrimitive(record.surface.name))
            put("outcome", JsonPrimitive(record.outcome.name))
            record.reason?.let { put("reason", JsonPrimitive(it)) }
            record.subject?.let { put("subject", JsonPrimitive(it)) }
        })
    }

    private fun toIso(millis: Long): String {
        // Thread-local formatter to keep this stateless across concurrent callers (the audit may be exported
        // from a Settings coroutine while the IME records new entries from a different scope).
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(millis))
    }
}
