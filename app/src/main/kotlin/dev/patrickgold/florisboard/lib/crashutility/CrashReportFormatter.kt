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

import android.content.Context
import android.os.Build
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.lib.devtools.Devtools

internal data class CrashReportEnvironment(
    val versionNameMarkdown: String,
    val versionCode: Int,
    val applicationId: String,
    val buildType: String,
    val buildCommitHash: String,
    val installSource: String,
    val deviceName: String,
    val androidVersion: String,
) {
    companion object {
        /**
         * The environment block the issue templates ask for.
         *
         * This used to be assembled inside the crash dialog, so the only way to obtain it was to
         * crash the app: a user reporting a non-crashing bug had to find the build type, commit
         * hash and install source by hand, and usually did not. Settings → About reports through
         * the same function now.
         *
         * @param versionNameMarkdown a link to the changelog or commit for this build, which only
         *   the caller can resolve because it needs string resources.
         */
        fun current(context: Context, versionNameMarkdown: String): CrashReportEnvironment {
            return CrashReportEnvironment(
                versionNameMarkdown = versionNameMarkdown,
                versionCode = BuildConfig.VERSION_CODE,
                applicationId = BuildConfig.APPLICATION_ID,
                buildType = BuildConfig.BUILD_TYPE,
                buildCommitHash = BuildConfig.BUILD_COMMIT_HASH,
                installSource = resolveInstallSource(context),
                deviceName = Devtools.getDeviceName(),
                androidVersion = Devtools.getAndroidVersion(),
            )
        }

        /**
         * Which store or tool installed this build. Sideloads report no installer at all, and the
         * lookup throws for a package the caller cannot see, so both collapse to one honest label
         * rather than an empty field the reporter has to explain.
         */
        fun resolveInstallSource(context: Context): String {
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstallerPackageName(context.packageName)
                }
            }.getOrNull()?.takeUnless { it.isBlank() } ?: "unknown / sideload"
        }
    }
}

internal object CrashReportFormatter {
    const val PRODUCT_NAME = "SwiftFloris"
    const val REDACTION_REMINDER =
        "Before sharing, remove typed text, clipboard content, personal dictionary content, " +
            "private APK paths, and unrelated device logs."

    /**
     * The environment block plus the redaction reminder, with no crash attached.
     *
     * Used by Settings → About so a bug that does not crash the app can still be reported with the
     * facts the issue templates require, instead of the reporter guessing at them.
     */
    fun formatProblemReport(environment: CrashReportEnvironment): String {
        return buildString {
            appendEnvironment(environment, includeCrashLogSource = false)
            appendLine("#### What happened")
            appendLine("_fill in: what you did, what you expected, what happened instead_")
            appendLine()
            appendLine("#### Privacy redaction")
            appendLine(REDACTION_REMINDER)
        }
    }

    fun formatReport(
        environment: CrashReportEnvironment,
        debugLogHeader: String,
        stacktraces: List<CrashUtility.Stacktrace>,
    ): String {
        return buildString {
            appendEnvironment(environment, includeCrashLogSource = true)
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

    private fun StringBuilder.appendEnvironment(
        environment: CrashReportEnvironment,
        includeCrashLogSource: Boolean,
    ) {
        appendLine("#### Environment information")
        appendLine("- $PRODUCT_NAME ${environment.versionNameMarkdown} (${environment.versionCode})")
        appendLine("- Package: ${environment.applicationId}")
        appendLine("- Build type: ${environment.buildType}")
        appendLine("- Build commit: ${environment.buildCommitHash}")
        appendLine("- Install source: ${environment.installSource}")
        appendLine("- Device: ${environment.deviceName}")
        appendLine("- Android: ${environment.androidVersion}")
        appendLine("- Reproducibility: _fill in: always / sometimes / once_")
        if (includeCrashLogSource) {
            appendLine("- Crash log source: _fill in: in-app crash dialog / logcat / Android crash prompt_")
        }
        appendLine()
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
