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

import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.smartcompose.AddonInvocationAudit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.chrono.JapaneseChronology
import java.util.Locale

class PrivacyAuditDisplayFormattingTest : FunSpec({
    test("surface and outcome labels use explicit localized resources") {
        PrivacyAuditDisplay.surfaceLabelRes(AddonInvocationAudit.Surface.SMART_COMPOSE) shouldBe
            R.string.settings__privacy_audit__surface_smart_compose
        PrivacyAuditDisplay.surfaceLabelRes(AddonInvocationAudit.Surface.TRANSLATION) shouldBe
            R.string.settings__privacy_audit__surface_translation
        PrivacyAuditDisplay.surfaceLabelRes(AddonInvocationAudit.Surface.MCP) shouldBe
            R.string.settings__privacy_audit__surface_mcp
        PrivacyAuditDisplay.outcomeLabelRes(AddonInvocationAudit.Outcome.ACCEPTED) shouldBe
            R.string.settings__privacy_audit__outcome_accepted
        PrivacyAuditDisplay.outcomeLabelRes(AddonInvocationAudit.Outcome.SUPPRESSED) shouldBe
            R.string.settings__privacy_audit__outcome_suppressed
        PrivacyAuditDisplay.outcomeLabelRes(AddonInvocationAudit.Outcome.FAILED) shouldBe
            R.string.settings__privacy_audit__outcome_failed
    }

    test("summary keeps retained and rolled-off counts separate") {
        val records = List(64) {
            AddonInvocationAudit.Record(
                sequence = it.toLong(),
                timestampMillis = 1_778_947_200_000L,
                surface = AddonInvocationAudit.Surface.MCP,
                outcome = AddonInvocationAudit.Outcome.ACCEPTED,
            )
        }

        PrivacyAuditDisplay.summary(records, totalCount = 320L) shouldBe PrivacyAuditDisplay.Summary(
            totalCount = 320L,
            recordCount = 64,
            rolledOffCount = 256L,
        )
    }

    test("timestamp display accepts the app's localized formatter") {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
            .withZone(ZoneId.of("UTC"))

        PrivacyAuditDisplay.formatTimestamp(formatter, 1_778_947_200_000L) shouldBe
            "2026-05-16 16:00"
    }

    test("Arabic locale changes the displayed timestamp instead of forcing US formatting") {
        val timestamp = 1_778_947_200_000L
        val englishFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.US)
            .withZone(ZoneId.of("UTC"))
        val arabicLocale = Locale.forLanguageTag("ar")
        val arabicFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(arabicLocale)
            .withZone(ZoneId.of("UTC"))

        PrivacyAuditDisplay.formatTimestamp(arabicFormatter, timestamp) shouldNotBe
            PrivacyAuditDisplay.formatTimestamp(englishFormatter, timestamp)
        arabicFormatter.locale.language shouldBe "ar"
    }

    test("Japanese calendar locale remains non-Gregorian in the formatter contract") {
        val japaneseLocale = Locale.forLanguageTag("ja-JP-u-ca-japanese")
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(japaneseLocale)
            .withZone(ZoneId.of("UTC"))

        PrivacyAuditDisplay.formatTimestamp(formatter, 1_778_947_200_000L) shouldNotBe
            PrivacyAuditDisplay.formatTimestamp(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(Locale.US)
                    .withZone(ZoneId.of("UTC")),
                1_778_947_200_000L,
            )
        PrivacyAuditDisplay.formatTimestamp(formatter, 1_778_947_200_000L) shouldBe
            formatter.withChronology(JapaneseChronology.INSTANCE)
                .format(java.time.Instant.ofEpochMilli(1_778_947_200_000L))
    }
})
