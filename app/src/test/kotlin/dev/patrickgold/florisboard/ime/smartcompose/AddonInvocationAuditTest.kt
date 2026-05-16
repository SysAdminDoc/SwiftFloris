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

class AddonInvocationAuditTest : FunSpec({

    afterEach {
        AddonInvocationAudit.resetForTest()
    }

    test("empty audit log returns empty snapshot and zero count") {
        AddonInvocationAudit.snapshot() shouldBe emptyList()
        AddonInvocationAudit.totalCount() shouldBe 0L
    }

    test("ACCEPTED record stores surface + outcome without reason") {
        AddonInvocationAudit.record(
            surface = AddonInvocationAudit.Surface.SMART_COMPOSE,
            outcome = AddonInvocationAudit.Outcome.ACCEPTED,
            timestampMillis = 1000L,
        )
        val snap = AddonInvocationAudit.snapshot()
        snap.size shouldBe 1
        snap[0].surface shouldBe AddonInvocationAudit.Surface.SMART_COMPOSE
        snap[0].outcome shouldBe AddonInvocationAudit.Outcome.ACCEPTED
        snap[0].reason shouldBe null
        snap[0].timestampMillis shouldBe 1000L
    }

    test("SUPPRESSED record requires reason; blank reason is rejected") {
        var caught = false
        try {
            AddonInvocationAudit.record(
                surface = AddonInvocationAudit.Surface.TRANSLATION,
                outcome = AddonInvocationAudit.Outcome.SUPPRESSED,
                reason = null,
            )
        } catch (_: IllegalArgumentException) {
            caught = true
        }
        caught shouldBe true
    }

    test("FAILED record preserves the reason string") {
        AddonInvocationAudit.record(
            surface = AddonInvocationAudit.Surface.MCP,
            outcome = AddonInvocationAudit.Outcome.FAILED,
            reason = "TOOL_INTERNAL_ERROR",
            timestampMillis = 2000L,
        )
        AddonInvocationAudit.snapshot().single().reason shouldBe "TOOL_INTERNAL_ERROR"
    }

    test("snapshotFor returns only records of the requested surface") {
        AddonInvocationAudit.record(AddonInvocationAudit.Surface.SMART_COMPOSE, AddonInvocationAudit.Outcome.ACCEPTED)
        AddonInvocationAudit.record(AddonInvocationAudit.Surface.TRANSLATION, AddonInvocationAudit.Outcome.SUPPRESSED, "no pair")
        AddonInvocationAudit.record(AddonInvocationAudit.Surface.MCP, AddonInvocationAudit.Outcome.FAILED, "TIMEOUT")
        AddonInvocationAudit.record(AddonInvocationAudit.Surface.SMART_COMPOSE, AddonInvocationAudit.Outcome.SUPPRESSED, "sensitive field")

        val smart = AddonInvocationAudit.snapshotFor(AddonInvocationAudit.Surface.SMART_COMPOSE)
        smart.size shouldBe 2
        smart.all { it.surface == AddonInvocationAudit.Surface.SMART_COMPOSE } shouldBe true
    }

    test("sequence numbers are strictly increasing") {
        AddonInvocationAudit.record(AddonInvocationAudit.Surface.SMART_COMPOSE, AddonInvocationAudit.Outcome.ACCEPTED)
        AddonInvocationAudit.record(AddonInvocationAudit.Surface.SMART_COMPOSE, AddonInvocationAudit.Outcome.ACCEPTED)
        AddonInvocationAudit.record(AddonInvocationAudit.Surface.SMART_COMPOSE, AddonInvocationAudit.Outcome.ACCEPTED)
        val seqs = AddonInvocationAudit.snapshot().map { it.sequence }
        seqs shouldBe listOf(1L, 2L, 3L)
        AddonInvocationAudit.totalCount() shouldBe 3L
    }

    test("clear drops every record and resets the sequence") {
        AddonInvocationAudit.record(AddonInvocationAudit.Surface.SMART_COMPOSE, AddonInvocationAudit.Outcome.ACCEPTED)
        AddonInvocationAudit.clear()
        AddonInvocationAudit.snapshot() shouldBe emptyList()
        AddonInvocationAudit.totalCount() shouldBe 0L
    }

    test("subject captures the categorical identifier on MCP records (matrix #38 follow-up)") {
        AddonInvocationAudit.record(
            surface = AddonInvocationAudit.Surface.MCP,
            outcome = AddonInvocationAudit.Outcome.SUPPRESSED,
            reason = "tool calendar.next on daemon com.example.mcp disabled by user",
            subject = "com.example.mcp::calendar.next",
        )
        val record = AddonInvocationAudit.snapshot().single()
        record.subject shouldBe "com.example.mcp::calendar.next"
    }

    test("subject captures the language-pair on TRANSLATION records") {
        AddonInvocationAudit.record(
            surface = AddonInvocationAudit.Surface.TRANSLATION,
            outcome = AddonInvocationAudit.Outcome.ACCEPTED,
            subject = "en->de",
        )
        AddonInvocationAudit.snapshot().single().subject shouldBe "en->de"
    }

    test("subject defaults to null on unannotated records and blank subject is normalised to null") {
        AddonInvocationAudit.record(
            surface = AddonInvocationAudit.Surface.SMART_COMPOSE,
            outcome = AddonInvocationAudit.Outcome.ACCEPTED,
        )
        AddonInvocationAudit.record(
            surface = AddonInvocationAudit.Surface.SMART_COMPOSE,
            outcome = AddonInvocationAudit.Outcome.ACCEPTED,
            subject = "   ",
        )
        AddonInvocationAudit.snapshot().all { it.subject == null } shouldBe true
    }
})
