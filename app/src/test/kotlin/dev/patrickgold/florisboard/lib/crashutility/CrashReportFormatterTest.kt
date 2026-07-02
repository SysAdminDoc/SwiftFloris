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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class CrashReportFormatterTest : FunSpec({
    test("generated crash report uses SwiftFloris identity and redaction fields") {
        val report = CrashReportFormatter.formatReport(
            environment = CrashReportEnvironment(
                versionNameMarkdown = "[1.9.53](https://example.invalid/release)",
                versionCode = 2102,
                applicationId = "io.github.sysadmindoc.swiftfloris.debug",
                buildType = "debug",
                buildCommitHash = "abc123",
                installSource = "com.android.packageinstaller",
                deviceName = "Pixel Test",
                androidVersion = "Android 16 (SDK 36)",
            ),
            debugLogHeader = "======= APP INFO =======\nName                : SwiftFloris",
            stacktraces = listOf(CrashUtility.Stacktrace("trace-1.stacktrace", "boom")),
        )

        report shouldContain "- SwiftFloris [1.9.53](https://example.invalid/release) (2102)"
        report shouldContain "- Package: io.github.sysadmindoc.swiftfloris.debug"
        report shouldContain "- Build type: debug"
        report shouldContain "- Build commit: abc123"
        report shouldContain "- Install source: com.android.packageinstaller"
        report shouldContain "- Reproducibility: _fill in: always / sometimes / once_"
        report shouldContain "- Crash log source: _fill in: in-app crash dialog / logcat / Android crash prompt_"
        report shouldContain "typed text, clipboard content, personal dictionary content"
        report shouldContain "private APK paths, and unrelated device logs"
        report shouldContain "<summary>trace-1.stacktrace</summary>"
        report shouldNotContain "- FlorisBoard ["
    }
})
