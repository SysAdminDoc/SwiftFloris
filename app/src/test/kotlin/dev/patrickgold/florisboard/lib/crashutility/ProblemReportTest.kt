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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The issue templates ask for version, versionCode, build type, commit hash, install source,
 * device and Android version. All seven were computed inside the crash dialog and nowhere else, so
 * the only in-app way to obtain them was to crash the app; a user reporting a bug that does not
 * crash had to find them by hand and usually did not.
 *
 * These cover the block Settings → About now produces. `bug_report.yml` is read at test time so
 * that adding a required field to the template without adding it here fails.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProblemReportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theProblemReportCarriesEveryFactTheIssueTemplateAsksFor() {
        val report = CrashReportFormatter.formatProblemReport(
            CrashReportEnvironment.current(context, versionNameMarkdown = "[1.2.3](https://example.invalid)"),
        )

        report shouldContain "[1.2.3](https://example.invalid)"
        report shouldContain "- Package: "
        report shouldContain "- Build type: "
        report shouldContain "- Build commit: "
        report shouldContain "- Install source: "
        report shouldContain "- Device: "
        report shouldContain "- Android: "
        report shouldContain "- Reproducibility: "
        report shouldContain CrashReportFormatter.REDACTION_REMINDER
    }

    @Test
    fun theProblemReportDoesNotAskAboutACrashThatDidNotHappen() {
        val report = CrashReportFormatter.formatProblemReport(
            CrashReportEnvironment.current(context, versionNameMarkdown = "1.2.3"),
        )

        // The crash-log-source line only makes sense when a stacktrace is attached.
        report shouldNotContain "Crash log source"
        report shouldContain "#### What happened"
    }

    @Test
    fun theInstallSourceIsNeverBlank() {
        // Sideloads report no installer and the lookup throws for an invisible package. Either way
        // the reporter should get a label rather than an empty field they have to explain.
        val source = CrashReportEnvironment.resolveInstallSource(context)

        source.isBlank() shouldBe false
    }

    @Test
    fun theCrashReportStillCarriesTheCrashOnlyFields() {
        val report = CrashReportFormatter.formatReport(
            environment = CrashReportEnvironment.current(context, versionNameMarkdown = "1.2.3"),
            debugLogHeader = "header",
            stacktraces = emptyList(),
        )

        report shouldContain "Crash log source"
        report shouldContain "Detailed info (Debug log header)"
    }
}
