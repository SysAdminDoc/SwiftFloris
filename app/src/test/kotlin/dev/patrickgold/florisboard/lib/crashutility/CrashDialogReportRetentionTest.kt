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

import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The crash dialog consumes the stacktrace files it reads, so the report it
 * builds on first launch is the only remaining copy. `configChanges` covers
 * rotation and nothing else, which leaves a theme switch, a font-scale change,
 * a locale change and a process restart all able to recreate the activity. If
 * the report is not carried across that recreation, the user is left looking at
 * an empty report for the crash they opened the dialog to send.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CrashDialogReportRetentionTest {

    private lateinit var ustDir: File

    @Before
    fun seedUnhandledStacktrace() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ustDir = File(context.noBackupFilesDir, "unhandled_stacktraces")
        ustDir.mkdirs()
        ustDir.listFiles()?.forEach { it.delete() }
        File(ustDir, "crash_fixture.stacktrace").writeText(
            "java.lang.IllegalStateException: $MARKER\n\tat fixture.Crash.trigger(Crash.kt:1)\n",
        )
    }

    @Test
    fun theReportSurvivesActivityRecreation() {
        val controller = Robolectric.buildActivity(CrashDialogActivity::class.java).setup()

        val firstReport = controller.get().reportText()
        assertContains(firstReport, MARKER, message = "first launch should render the seeded crash")

        // The dialog deletes what it reads, so a second read can only come back empty.
        assertTrue(
            ustDir.listFiles().orEmpty().isEmpty(),
            "the stacktrace file should have been consumed on first launch",
        )

        controller.recreate()

        assertEquals(
            firstReport,
            controller.get().reportText(),
            "recreation must not discard the only copy of the report",
        )
    }

    private fun CrashDialogActivity.reportText(): String {
        return findViewById<TextView>(R.id.stacktrace).text.toString()
    }

    private companion object {
        const val MARKER = "swiftfloris-crash-retention-fixture"
    }
}
