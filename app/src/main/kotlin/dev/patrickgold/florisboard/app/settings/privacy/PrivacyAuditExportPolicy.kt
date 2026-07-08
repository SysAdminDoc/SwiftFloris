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

import dev.patrickgold.florisboard.ime.smartcompose.AddonAuditExport
import dev.patrickgold.florisboard.ime.smartcompose.AddonInvocationAudit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class PrivacyAuditExportPayload(
    val fileName: String,
    val mimeType: String,
    val json: String,
)

internal object PrivacyAuditExportPolicy {
    const val MIME_TYPE: String = "application/json"
    private const val FILE_PREFIX = "swiftfloris-privacy-audit"

    fun buildPayload(
        records: List<AddonInvocationAudit.Record>,
        nowMillis: Long = System.currentTimeMillis(),
        totalCount: Long = AddonInvocationAudit.totalCount(),
    ): PrivacyAuditExportPayload {
        return PrivacyAuditExportPayload(
            fileName = defaultFileName(nowMillis),
            mimeType = MIME_TYPE,
            json = AddonAuditExport.toJsonString(nowMillis = nowMillis, records = records, totalCount = totalCount),
        )
    }

    fun resolveSavePayload(
        pending: PrivacyAuditExportPayload?,
        records: List<AddonInvocationAudit.Record> = AddonInvocationAudit.snapshot(),
        nowMillis: Long = System.currentTimeMillis(),
        totalCount: Long = AddonInvocationAudit.totalCount(),
    ): PrivacyAuditExportPayload? {
        return pending ?: records.takeIf { it.isNotEmpty() }?.let { liveRecords ->
            buildPayload(records = liveRecords, nowMillis = nowMillis, totalCount = totalCount)
        }
    }

    fun defaultFileName(nowMillis: Long): String {
        val formatter = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return "$FILE_PREFIX-${formatter.format(Date(nowMillis))}.json"
    }
}
