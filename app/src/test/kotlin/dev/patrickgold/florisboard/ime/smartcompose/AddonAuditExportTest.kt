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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun record(
    sequence: Long,
    surface: AddonInvocationAudit.Surface,
    outcome: AddonInvocationAudit.Outcome,
    reason: String? = null,
    subject: String? = null,
    timestampMillis: Long = 1_778_947_200_000L,
): AddonInvocationAudit.Record = AddonInvocationAudit.Record(
    sequence = sequence,
    timestampMillis = timestampMillis,
    surface = surface,
    outcome = outcome,
    reason = if (outcome == AddonInvocationAudit.Outcome.ACCEPTED) null else reason,
    subject = subject?.takeIf { it.isNotBlank() },
)

class AddonAuditExportTest : FunSpec({

    test("buildBundle emits the schema version + exported timestamp + counts") {
        val bundle = AddonAuditExport.buildBundle(
            nowMillis = 1_778_947_200_000L,
            records = emptyList(),
            totalCount = 0L,
        )

        (bundle["exportVersion"] as JsonPrimitive).content shouldBe "1"
        (bundle["exportedAtIso"] as JsonPrimitive).content shouldBe "2026-05-16T16:00:00.000Z"
        (bundle["totalCount"] as JsonPrimitive).content shouldBe "0"
        (bundle["recordCount"] as JsonPrimitive).content shouldBe "0"
        (bundle["records"] as JsonArray).size shouldBe 0
    }

    test("records carry sequence + UTC ISO timestamp + surface + outcome + reason + subject") {
        val bundle = AddonAuditExport.buildBundle(
            nowMillis = 1_778_947_200_000L,
            records = listOf(
                record(
                    sequence = 42L,
                    surface = AddonInvocationAudit.Surface.MCP,
                    outcome = AddonInvocationAudit.Outcome.SUPPRESSED,
                    reason = "tool calendar.next on daemon com.example.mcp disabled by user",
                    subject = "com.example.mcp::calendar.next",
                ),
            ),
            totalCount = 137L,
        )
        val entry = (bundle["records"] as JsonArray).single() as JsonObject

        entry["sequence"]?.jsonPrimitive?.content shouldBe "42"
        entry["timestampIso"]?.jsonPrimitive?.content shouldBe "2026-05-16T16:00:00.000Z"
        entry["surface"]?.jsonPrimitive?.content shouldBe "MCP"
        entry["outcome"]?.jsonPrimitive?.content shouldBe "SUPPRESSED"
        entry["reason"]?.jsonPrimitive?.content shouldBe
            "tool calendar.next on daemon com.example.mcp disabled by user"
        entry["subject"]?.jsonPrimitive?.content shouldBe "com.example.mcp::calendar.next"
        (bundle["totalCount"] as JsonPrimitive).content shouldBe "137"
    }

    test("ACCEPTED records omit the reason field entirely (not just null)") {
        val bundle = AddonAuditExport.buildBundle(
            nowMillis = 1_778_947_200_000L,
            records = listOf(
                record(
                    sequence = 1L,
                    surface = AddonInvocationAudit.Surface.SMART_COMPOSE,
                    outcome = AddonInvocationAudit.Outcome.ACCEPTED,
                    subject = "com.slack",
                ),
            ),
            totalCount = 1L,
        )
        val entry = (bundle["records"] as JsonArray).single() as JsonObject

        entry.containsKey("reason") shouldBe false
        entry["subject"]?.jsonPrimitive?.content shouldBe "com.slack"
    }

    test("subject is omitted entirely when null") {
        val bundle = AddonAuditExport.buildBundle(
            nowMillis = 0L,
            records = listOf(
                record(
                    sequence = 1L,
                    surface = AddonInvocationAudit.Surface.TRANSLATION,
                    outcome = AddonInvocationAudit.Outcome.FAILED,
                    reason = "no installed pair",
                    subject = null,
                ),
            ),
            totalCount = 1L,
        )
        val entry = (bundle["records"] as JsonArray).single() as JsonObject

        entry.containsKey("subject") shouldBe false
        entry["reason"]?.jsonPrimitive?.content shouldBe "no installed pair"
    }

    test("toJsonString is round-trip parseable as a JsonObject") {
        val records = listOf(
            record(
                sequence = 1L,
                surface = AddonInvocationAudit.Surface.MCP,
                outcome = AddonInvocationAudit.Outcome.FAILED,
                reason = "TIMEOUT",
                subject = "com.example.mcp::weather.now",
            ),
            record(
                sequence = 2L,
                surface = AddonInvocationAudit.Surface.SMART_COMPOSE,
                outcome = AddonInvocationAudit.Outcome.ACCEPTED,
                subject = "com.notes",
            ),
        )
        val serialized = AddonAuditExport.toJsonString(
            nowMillis = 1_747_401_600_000L,
            records = records,
            totalCount = 2L,
        )

        val parsed = Json.parseToJsonElement(serialized) as JsonObject
        parsed["records"]!!.jsonArray.size shouldBe 2
        parsed["records"]!!.jsonArray[0].jsonObject["surface"]?.jsonPrimitive?.content shouldBe "MCP"
    }

    test("toJsonString never carries reason text for ACCEPTED outcomes (the model enforces this upstream)") {
        val records = listOf(
            record(
                sequence = 1L,
                surface = AddonInvocationAudit.Surface.SMART_COMPOSE,
                outcome = AddonInvocationAudit.Outcome.ACCEPTED,
            ),
        )
        val serialized = AddonAuditExport.toJsonString(
            nowMillis = 1_747_401_600_000L,
            records = records,
            totalCount = 1L,
        )

        serialized shouldContain "\"outcome\":\"ACCEPTED\""
        serialized shouldNotContain "\"reason\""
    }

    test("summaryLine handles the empty / no-rollover / rollover cases") {
        AddonAuditExport.summaryLine(records = emptyList(), totalCount = 0L) shouldBe
            "Audit log is empty."
        AddonAuditExport.summaryLine(
            records = listOf(
                record(1L, AddonInvocationAudit.Surface.MCP, AddonInvocationAudit.Outcome.ACCEPTED),
            ),
            totalCount = 1L,
        ) shouldBe "1 record(s) in the audit log."
        AddonAuditExport.summaryLine(
            records = List(64) {
                record(it.toLong(), AddonInvocationAudit.Surface.MCP, AddonInvocationAudit.Outcome.ACCEPTED)
            },
            totalCount = 320L,
        ) shouldBe "64 record(s) in the audit log; 256 earlier record(s) have rolled off the ring."
    }

    test("bundle has stable ordering of top-level fields") {
        val serialized = AddonAuditExport.toJsonString(
            nowMillis = 1_747_401_600_000L,
            records = emptyList(),
            totalCount = 0L,
        )
        val versionIdx = serialized.indexOf("\"exportVersion\"")
        val timestampIdx = serialized.indexOf("\"exportedAtIso\"")
        val totalIdx = serialized.indexOf("\"totalCount\"")
        val countIdx = serialized.indexOf("\"recordCount\"")
        val recordsIdx = serialized.indexOf("\"records\"")

        (versionIdx < timestampIdx) shouldBe true
        (timestampIdx < totalIdx) shouldBe true
        (totalIdx < countIdx) shouldBe true
        (countIdx < recordsIdx) shouldBe true
    }
})
