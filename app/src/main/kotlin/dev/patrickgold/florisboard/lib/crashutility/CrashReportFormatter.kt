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

package dev.patrickgold.florisboard.lib.crashutility

internal data class CrashReportEnvironment(
    val versionNameMarkdown: String,
    val versionCode: Int,
    val applicationId: String,
    val buildType: String,
    val buildCommitHash: String,
    val installSource: String,
    val deviceName: String,
    val androidVersion: String,
)

internal object CrashReportFormatter {
    const val PRODUCT_NAME = "SwiftFloris"
    const val REDACTION_REMINDER =
        "Before sharing, remove typed text, clipboard content, personal dictionary content, " +
            "private APK paths, and unrelated device logs."

    fun formatReport(
        environment: CrashReportEnvironment,
        debugLogHeader: String,
        stacktraces: List<CrashUtility.Stacktrace>,
    ): String {
        return buildString {
            appendLine("#### Environment information")
            appendLine("- $PRODUCT_NAME ${environment.versionNameMarkdown} (${environment.versionCode})")
            appendLine("- Package: ${environment.applicationId}")
            appendLine("- Build type: ${environment.buildType}")
            appendLine("- Build commit: ${environment.buildCommitHash}")
            appendLine("- Install source: ${environment.installSource}")
            appendLine("- Device: ${environment.deviceName}")
            appendLine("- Android: ${environment.androidVersion}")
            appendLine("- Reproducibility: _fill in: always / sometimes / once_")
            appendLine("- Crash log source: _fill in: in-app crash dialog / logcat / Android crash prompt_")
            appendLine()
            appendLine("#### Privacy redaction")
            appendLine(REDACTION_REMINDER)
            appendLine()
            appendLine("#### Attached logs and stacktrace files")
            appendCollapsibleSection(
                summary = "Detailed info (Debug log header)",
                details = debugLogHeader,
            )
            appendLine()
            for (stacktrace in stacktraces) {
                appendCollapsibleSection(stacktrace.name, stacktrace.details)
                appendLine()
            }
        }
    }

    /**
     * Rules for collapsible markdown on GitHub:
     *  https://gist.github.com/pierrejoubert73/902cc94d79424356a8d20be2b382e1ab
     */
    private fun StringBuilder.appendCollapsibleSection(summary: String, details: String) {
        appendLine("<details>")
        append("<summary>").append(summary).appendLine("</summary>")
        appendLine()
        appendLine("```")
        appendLine(details)
        appendLine("```")
        appendLine("</details>")
    }
}
