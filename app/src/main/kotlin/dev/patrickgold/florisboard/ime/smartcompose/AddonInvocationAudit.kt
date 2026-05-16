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

import java.util.concurrent.atomic.AtomicLong

/**
 * ROADMAP §10.5 N7.6 — opt-in-addon invocation audit log.
 *
 * Settings → Privacy lets the user see "what has the keyboard
 * actually called into?" — every smart-compose / translation /
 * MCP dispatch goes through one of the three Routers (v1.8.21
 * `SmartComposeRouter`, v1.8.22 `TranslationRouter`, v1.8.23
 * `McpDispatchRouter`); this audit log captures **PII-safe
 * metadata only** about each invocation:
 *
 *  - Which surface (`SMART_COMPOSE` / `TRANSLATION` / `MCP`).
 *  - Outcome (`ACCEPTED` / `SUPPRESSED` / `FAILED`).
 *  - Timestamp (host clock).
 *  - Categorical reason for `SUPPRESSED` / `FAILED` — these are
 *    the *router-emitted reason strings* (e.g. `"sensitive field"`,
 *    `"no installed pair"`, `"tool X not registered"`) which by
 *    construction don't contain user text.
 *
 * **Never** captures the user's text, the candidate suggestions,
 * the translated content, the tool parameters, or the tool result.
 * Per §1 (no cloud / no telemetry) the log lives in process memory
 * only — caps at [MAX_RECORDS] entries with FIFO eviction. The
 * Settings UI reads the in-memory snapshot; nothing is persisted
 * to disk.
 *
 * The audit is also strictly **observability**, not enforcement —
 * the routers' suppression logic is the actual privacy gate.
 */
object AddonInvocationAudit {

    enum class Surface { SMART_COMPOSE, TRANSLATION, MCP }
    enum class Outcome { ACCEPTED, SUPPRESSED, FAILED }

    /** One audit record. PII-safe by construction. */
    data class Record(
        val sequence: Long,
        val timestampMillis: Long,
        val surface: Surface,
        val outcome: Outcome,
        val reason: String? = null,
    )

    private val MAX_RECORDS: Int = 256

    private val lock = Any()
    private val ring = ArrayDeque<Record>(MAX_RECORDS)
    private val sequence = AtomicLong(1)

    /**
     * Record an invocation. [reason] is required for SUPPRESSED /
     * FAILED and ignored for ACCEPTED. Caller passes the
     * timestamp so the routers can use the same clock source.
     */
    fun record(
        surface: Surface,
        outcome: Outcome,
        reason: String? = null,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        require(outcome == Outcome.ACCEPTED || !reason.isNullOrBlank()) {
            "reason must be set when outcome is $outcome"
        }
        val record = Record(
            sequence = sequence.getAndIncrement(),
            timestampMillis = timestampMillis,
            surface = surface,
            outcome = outcome,
            reason = if (outcome == Outcome.ACCEPTED) null else reason,
        )
        synchronized(lock) {
            while (ring.size >= MAX_RECORDS) ring.removeFirst()
            ring.addLast(record)
        }
    }

    /** Snapshot of the audit log, most recent last. */
    fun snapshot(): List<Record> = synchronized(lock) { ring.toList() }

    /** Snapshot filtered to one surface. */
    fun snapshotFor(surface: Surface): List<Record> =
        synchronized(lock) { ring.filter { it.surface == surface } }

    /** Total invocations recorded since process start. */
    fun totalCount(): Long = sequence.get() - 1

    /** Drop every record — used by Settings → Privacy "clear log". */
    fun clear() = synchronized(lock) {
        ring.clear()
        sequence.set(1)
    }

    /** Test-only — alias of [clear]. */
    internal fun resetForTest() = clear()
}
