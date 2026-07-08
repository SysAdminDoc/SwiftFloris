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

package dev.patrickgold.florisboard.app.settings.privacy

import dev.patrickgold.florisboard.ime.smartcompose.AddonInvocationAudit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun auditRecord(
    sequence: Long,
    surface: AddonInvocationAudit.Surface = AddonInvocationAudit.Surface.MCP,
    outcome: AddonInvocationAudit.Outcome = AddonInvocationAudit.Outcome.SUPPRESSED,
    reason: String = "tool disabled by user",
    subject: String = "com.example.mcp::calendar.next",
): AddonInvocationAudit.Record = AddonInvocationAudit.Record(
    sequence = sequence,
    timestampMillis = 1_778_947_200_000L,
    surface = surface,
    outcome = outcome,
    reason = reason,
    subject = subject,
)

class PrivacyAuditExportPolicyTest : FunSpec({
    test("payload uses application/json and a deterministic UTC filename") {
        val payload = PrivacyAuditExportPolicy.buildPayload(
            records = emptyList(),
            nowMillis = 1_778_947_200_000L,
            totalCount = 0L,
        )

        payload.mimeType shouldBe "application/json"
        payload.fileName shouldBe "swiftfloris-privacy-audit-20260516T160000Z.json"
    }

    test("payload JSON carries only categorical audit fields, not typed content fields") {
        val payload = PrivacyAuditExportPolicy.buildPayload(
            records = listOf(auditRecord(sequence = 42L)),
            nowMillis = 1_778_947_200_000L,
            totalCount = 1L,
        )
        val parsed = Json.parseToJsonElement(payload.json).jsonObject
        val record = parsed["records"]!!.jsonArray.single().jsonObject

        record.keys.toSet() shouldBe setOf(
            "sequence",
            "timestampMillis",
            "timestampIso",
            "surface",
            "outcome",
            "reason",
            "subject",
        )
        record["subject"]!!.jsonPrimitive.content shouldBe "com.example.mcp::calendar.next"
        payload.json shouldNotContain "typedText"
        payload.json shouldNotContain "candidate"
        payload.json shouldNotContain "translatedContent"
        payload.json shouldNotContain "toolParameters"
        payload.json shouldNotContain "toolResult"
        payload.json shouldNotContain "clipboard"
        payload.json shouldNotContain "hello secret"
    }

    test("save payload resolver keeps the pending payload when recreation preserved it") {
        val pending = PrivacyAuditExportPolicy.buildPayload(
            records = listOf(auditRecord(sequence = 1L)),
            nowMillis = 1_778_947_200_000L,
            totalCount = 1L,
        )

        PrivacyAuditExportPolicy.resolveSavePayload(
            pending = pending,
            records = emptyList(),
            nowMillis = 1_778_947_201_000L,
            totalCount = 0L,
        ) shouldBe pending
    }

    test("save payload resolver rebuilds from the audit ring when pending state was lost") {
        val payload = PrivacyAuditExportPolicy.resolveSavePayload(
            pending = null,
            records = listOf(auditRecord(sequence = 7L)),
            nowMillis = 1_778_947_200_000L,
            totalCount = 7L,
        )!!
        val parsed = Json.parseToJsonElement(payload.json).jsonObject

        payload.fileName shouldBe "swiftfloris-privacy-audit-20260516T160000Z.json"
        parsed["totalCount"]!!.jsonPrimitive.content shouldBe "7"
        parsed["records"]!!.jsonArray.single().jsonObject["sequence"]!!.jsonPrimitive.content shouldBe "7"
    }

    test("save payload resolver returns null when there is no pending payload or live audit record") {
        PrivacyAuditExportPolicy.resolveSavePayload(
            pending = null,
            records = emptyList(),
            nowMillis = 1_778_947_200_000L,
            totalCount = 0L,
        ) shouldBe null
    }
})
