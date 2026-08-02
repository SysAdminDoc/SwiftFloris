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

import androidx.annotation.StringRes
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.smartcompose.AddonInvocationAudit
import java.time.Instant
import java.time.chrono.Chronology
import java.time.format.DateTimeFormatter

/** Presentation-only helpers for the local privacy audit surface. */
internal object PrivacyAuditDisplay {
    data class Summary(
        val totalCount: Long,
        val recordCount: Int,
        val rolledOffCount: Long,
    )

    fun summary(
        records: List<AddonInvocationAudit.Record>,
        totalCount: Long,
    ): Summary = Summary(
        totalCount = totalCount,
        recordCount = records.size,
        rolledOffCount = (totalCount - records.size.toLong()).coerceAtLeast(0L),
    )

    @StringRes
    fun surfaceLabelRes(surface: AddonInvocationAudit.Surface): Int = when (surface) {
        AddonInvocationAudit.Surface.SMART_COMPOSE -> R.string.settings__privacy_audit__surface_smart_compose
        AddonInvocationAudit.Surface.TRANSLATION -> R.string.settings__privacy_audit__surface_translation
        AddonInvocationAudit.Surface.MCP -> R.string.settings__privacy_audit__surface_mcp
    }

    @StringRes
    fun outcomeLabelRes(outcome: AddonInvocationAudit.Outcome): Int = when (outcome) {
        AddonInvocationAudit.Outcome.ACCEPTED -> R.string.settings__privacy_audit__outcome_accepted
        AddonInvocationAudit.Outcome.SUPPRESSED -> R.string.settings__privacy_audit__outcome_suppressed
        AddonInvocationAudit.Outcome.FAILED -> R.string.settings__privacy_audit__outcome_failed
    }

    fun formatTimestamp(formatter: DateTimeFormatter, timestampMillis: Long): String {
        // Android locales may carry a calendar extension (for example, Japanese or Buddhist).
        // DateTimeFormatter's localized factory does not always apply that chronology itself.
        val calendarAwareFormatter = runCatching {
            formatter.withChronology(Chronology.ofLocale(formatter.locale))
        }.getOrDefault(formatter)
        return calendarAwareFormatter.format(Instant.ofEpochMilli(timestampMillis))
    }
}
