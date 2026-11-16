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

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.patrickgold.florisboard.lib.devtools.Devtools
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProcessExitHistoryTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    /**
     * The description Android 17's limiter writes for a kill, as `dumpsys activity exit-info`
     * showed it for a real limiter kill of the keyboard: reason 13 (OTHER KILLS BY SYSTEM),
     * subreason 12 (KILL PID), `description=MemoryLimiter:AnonSwap`.
     */
    private val limiterDescription = "MemoryLimiter:AnonSwap"

    private val crashDir = File(context.noBackupFilesDir, "unhandled_stacktraces")

    @Before
    fun setUp() {
        File(context.noBackupFilesDir, "process-exits.txt").delete()
        crashDir.deleteRecursively()
        context.getSharedPreferences("crash_utility", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun addExit(timestamp: Long, reason: Int, description: String? = null, pid: Int = 4242) {
        val info = ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
            .setProcessName(context.packageName)
            .setPid(pid)
            .setReason(reason)
            .setDescription(description)
            .setTimestamp(timestamp)
            .setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
            .setPss(0)
            .setRss(0)
            .build()
        shadowOf(activityManager).addApplicationExitInfo(info)
    }

    @Test
    fun limiterKillIsToldApartFromOtherReasons() {
        ProcessExitHistory.classify(ApplicationExitInfo.REASON_OTHER, limiterDescription) shouldBe
            ProcessExitHistory.Kind.MemoryLimiter
        ProcessExitHistory.classify(ApplicationExitInfo.REASON_OTHER, "killed for something else") shouldBe
            ProcessExitHistory.Kind.Other
        ProcessExitHistory.classify(ApplicationExitInfo.REASON_OTHER, null) shouldBe ProcessExitHistory.Kind.Other
        ProcessExitHistory.classify(ApplicationExitInfo.REASON_CRASH, null) shouldBe ProcessExitHistory.Kind.Crash
        ProcessExitHistory.classify(ApplicationExitInfo.REASON_CRASH_NATIVE, null) shouldBe
            ProcessExitHistory.Kind.NativeCrash
        ProcessExitHistory.classify(ApplicationExitInfo.REASON_LOW_MEMORY, null) shouldBe
            ProcessExitHistory.Kind.LowMemory
        ProcessExitHistory.classify(ApplicationExitInfo.REASON_ANR, null) shouldBe
            ProcessExitHistory.Kind.NotResponding
    }

    /**
     * CrashUtility saves the trace and kills the process itself, so the platform records a Java
     * crash as a signal. Without the saved crash next to it, a signal is just "other".
     */
    @Test
    fun aSignalRightAfterASavedCrashIsACrash() {
        crashDir.mkdirs()
        File(crashDir, "4000.stacktrace").writeText("java.lang.IllegalStateException")
        addExit(timestamp = 4_050, reason = ApplicationExitInfo.REASON_SIGNALED, pid = 1)
        addExit(timestamp = 60_000, reason = ApplicationExitInfo.REASON_SIGNALED, pid = 2)

        ProcessExitHistory.update(context).map { it.kind }.shouldContainExactly(
            ProcessExitHistory.Kind.Other,
            ProcessExitHistory.Kind.Crash,
        )
        ProcessExitHistory.describe(ProcessExitHistory.recent(context).last()) shouldContain " crash (signaled 2,"
    }

    /** The crash dialog deletes the trace files as it reads them; the last crash time stays. */
    @Test
    fun aSelfExitAfterTheLastSavedCrashIsACrashOnceTheTraceIsGone() {
        context.getSharedPreferences("crash_utility", Context.MODE_PRIVATE).edit()
            .putLong("last_crash_timestamp", 100_000L).commit()
        addExit(timestamp = 100_400, reason = ApplicationExitInfo.REASON_EXIT_SELF)

        ProcessExitHistory.update(context).single().kind shouldBe ProcessExitHistory.Kind.Crash
    }

    @Test
    fun aCrashSavedLongBeforeASignalDoesNotMakeItACrash() {
        crashDir.mkdirs()
        File(crashDir, "1000.stacktrace").writeText("java.lang.IllegalStateException")
        addExit(timestamp = 1_000 + ProcessExitHistory.CrashToExitWindowMs + 1, reason = ApplicationExitInfo.REASON_SIGNALED)

        ProcessExitHistory.update(context).single().kind shouldBe ProcessExitHistory.Kind.Other
    }

    @Test
    fun twoExitsInTheSameMillisecondAreBothSaved() {
        addExit(timestamp = 1_000, reason = ApplicationExitInfo.REASON_LOW_MEMORY, pid = 1)
        ProcessExitHistory.update(context)
        addExit(timestamp = 1_000, reason = ApplicationExitInfo.REASON_OTHER, description = limiterDescription, pid = 2)

        ProcessExitHistory.update(context).map { it.pid }.shouldContainExactly(2)
        ProcessExitHistory.recent(context) shouldHaveSize 2
        ProcessExitHistory.update(context).shouldBeEmpty()
    }

    @Test
    fun updateSavesNewExitsNewestFirstAndOnlyOnce() {
        addExit(timestamp = 1_000, reason = ApplicationExitInfo.REASON_CRASH)
        addExit(timestamp = 2_000, reason = ApplicationExitInfo.REASON_OTHER, description = limiterDescription)

        val first = ProcessExitHistory.update(context)
        first.map { it.kind }.shouldContainExactly(
            ProcessExitHistory.Kind.MemoryLimiter,
            ProcessExitHistory.Kind.Crash,
        )
        ProcessExitHistory.update(context).shouldBeEmpty()

        addExit(timestamp = 3_000, reason = ApplicationExitInfo.REASON_LOW_MEMORY)
        ProcessExitHistory.update(context).map { it.timestamp }.shouldContainExactly(3_000L)
        ProcessExitHistory.recent(context).map { it.timestamp }.shouldContainExactly(3_000L, 2_000L, 1_000L)
    }

    @Test
    fun historyKeepsOnlyTheNewestRecords() {
        repeat(ProcessExitHistory.MaxRecords) { i ->
            addExit(timestamp = 1_000L + i, reason = ApplicationExitInfo.REASON_CRASH)
        }
        ProcessExitHistory.update(context)
        addExit(timestamp = 5_000, reason = ApplicationExitInfo.REASON_OTHER, description = limiterDescription)
        ProcessExitHistory.update(context)

        val recent = ProcessExitHistory.recent(context)
        recent shouldHaveSize ProcessExitHistory.MaxRecords
        recent.first().kind shouldBe ProcessExitHistory.Kind.MemoryLimiter
        recent.last().timestamp shouldBe 1_001L
    }

    @Test
    fun descriptionNeverReachesTheFileOrAReport() {
        val typed = "my secret pin is 1234"
        addExit(timestamp = 1_000, reason = ApplicationExitInfo.REASON_OTHER, description = "MemoryLimiter $typed")
        ProcessExitHistory.update(context)

        File(context.noBackupFilesDir, "process-exits.txt").readText() shouldNotContain typed
        val log = Devtools.generateRecentExitsLog(context)
        log shouldContain "memory limiter kill"
        log shouldNotContain typed
    }

    @Test
    fun reportsSayWhetherTheLastExitWasALimiterKillOrACrash() {
        addExit(timestamp = 1_000, reason = ApplicationExitInfo.REASON_CRASH)
        ProcessExitHistory.update(context)
        val crashReport = CrashReportFormatter.formatProblemReport(CrashReportEnvironment.current(context, "1.0"))
        crashReport shouldContain "- Last app exit: "
        crashReport shouldContain " crash (crash 4,"
        Devtools.generateRecentExitsLog(context) shouldContain " crash (crash 4,"

        addExit(timestamp = 2_000, reason = ApplicationExitInfo.REASON_OTHER, description = limiterDescription)
        ProcessExitHistory.update(context)
        val report = CrashReportFormatter.formatProblemReport(CrashReportEnvironment.current(context, "1.0"))
        report shouldContain "memory limiter kill (other 13, importance 100"
        report shouldNotContain " crash (crash 4,"
    }

    @Test
    fun reportLeavesTheLineOutWhenNothingWasRecorded() {
        val report = CrashReportFormatter.formatProblemReport(CrashReportEnvironment.current(context, "1.0"))
        report shouldNotContain "Last app exit"
        Devtools.generateRecentExitsLog(context) shouldContain "None recorded"
    }
}
