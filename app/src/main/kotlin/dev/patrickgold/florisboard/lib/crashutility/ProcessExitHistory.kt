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
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last few ways the process ended, as the platform recorded them, so a report can tell a
 * system memory kill from a crash.
 *
 * Android 17 adds a per-app memory limiter that can kill an app over its budget even while it is
 * on screen. A keyboard killed that way vanishes and comes back, which feels like a crash to the
 * person typing but leaves no stack trace. The platform records it as
 * [ApplicationExitInfo.REASON_OTHER] with "MemoryLimiter" in the description.
 *
 * A Java crash never reaches the platform as [ApplicationExitInfo.REASON_CRASH] here: [CrashUtility]
 * saves the stack trace and kills the process itself, which the platform records as a signal or a
 * self exit. An exit like that within [CrashToExitWindowMs] of a saved crash counts as a crash.
 *
 * Only the timestamp, reason code, the [Kind] it maps to, importance and memory figures are kept.
 * The description is matched and then dropped, and the trace and process state blobs, which can
 * hold heap contents, are never opened, so nothing typed can reach the file or a report.
 */
object ProcessExitHistory {
    enum class Kind(val label: String) {
        MemoryLimiter("memory limiter kill"),
        LowMemory("low memory kill"),
        Crash("crash"),
        NativeCrash("native crash"),
        NotResponding("not responding"),
        Other("other"),
    }

    data class Record(
        val timestamp: Long,
        val pid: Int,
        val kind: Kind,
        val reason: Int,
        val importance: Int,
        val pssKb: Long,
        val rssKb: Long,
    )

    /** Covers a run of kills in one sitting while the file stays well under a kilobyte. */
    const val MaxRecords = 10

    /** Logcat tag of the one line written per newly seen exit, which the device check reads. */
    const val LogTag = "SwiftFlorisExit"

    /**
     * How long after [CrashUtility] saves a crash the process can take to die. Writing the trace and
     * posting the notification take milliseconds, so this is generous.
     */
    const val CrashToExitWindowMs = 10_000L

    private const val FileName = "process-exits.txt"

    /** Names for [ApplicationExitInfo] reason codes, indexed by code. */
    private val ReasonNames = listOf(
        "unknown", "exit self", "signaled", "low memory", "crash", "native crash", "anr",
        "initialization failure", "permission change", "excessive resource usage", "user requested",
        "user stopped", "dependency died", "other", "freezer", "package state change",
        "package updated",
    )

    /**
     * @param followsSavedCrash whether [CrashUtility] saved a crash just before this exit, which makes
     *   the signal or self exit it ends in a crash.
     */
    fun classify(reason: Int, description: String?, followsSavedCrash: Boolean = false): Kind = when (reason) {
        ApplicationExitInfo.REASON_OTHER -> {
            if (description?.contains("MemoryLimiter", ignoreCase = true) == true) Kind.MemoryLimiter else Kind.Other
        }
        ApplicationExitInfo.REASON_LOW_MEMORY -> Kind.LowMemory
        ApplicationExitInfo.REASON_CRASH -> Kind.Crash
        ApplicationExitInfo.REASON_CRASH_NATIVE -> Kind.NativeCrash
        ApplicationExitInfo.REASON_ANR -> Kind.NotResponding
        ApplicationExitInfo.REASON_SIGNALED,
        ApplicationExitInfo.REASON_EXIT_SELF,
        ApplicationExitInfo.REASON_UNKNOWN,
        -> if (followsSavedCrash) Kind.Crash else Kind.Other
        else -> Kind.Other
    }

    /**
     * Saves the exits the platform reported since the last call, newest first, and returns just
     * those. Reads and writes small files, so the app calls it off the main thread at startup. The
     * crash dialog calls it again before building its report, because a crash restarts the process
     * and the dialog can open before the startup call has run. Two exits in the same millisecond are
     * told apart by process id.
     */
    @Synchronized
    fun update(context: Context): List<Record> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val file = file(context)
        val known = read(file)
        val since = known.firstOrNull()?.timestamp ?: 0L
        val seen = known.mapTo(HashSet()) { it.timestamp to it.pid }
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        val crashTimes = CrashUtility.savedCrashTimestamps(context)
        val fresh = runCatching {
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, MaxRecords)
        }.getOrDefault(emptyList())
            .filter { it.timestamp >= since && (it.timestamp to it.pid) !in seen }
            .map { info ->
                val followsSavedCrash = crashTimes.any { it in (info.timestamp - CrashToExitWindowMs)..info.timestamp }
                Record(
                    timestamp = info.timestamp,
                    pid = info.pid,
                    kind = classify(info.reason, info.description, followsSavedCrash),
                    reason = info.reason,
                    importance = info.importance,
                    pssKb = info.pss,
                    rssKb = info.rss,
                )
            }
            .sortedByDescending { it.timestamp }
        if (fresh.isEmpty()) return emptyList()
        fresh.forEach { Log.i(LogTag, describe(it)) }
        write(file, (fresh + known).take(MaxRecords))
        return fresh
    }

    /** What [update] has saved, newest first. */
    fun recent(context: Context): List<Record> = read(file(context))

    fun describe(record: Record): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(record.timestamp))
        val reasonName = ReasonNames.getOrNull(record.reason) ?: "reason"
        return "$time ${record.kind.label} ($reasonName ${record.reason}, importance ${record.importance}, " +
            "pss ${record.pssKb} KB, rss ${record.rssKb} KB)"
    }

    private fun file(context: Context) = File(context.noBackupFilesDir, FileName)

    private fun read(file: File): List<Record> {
        val lines = runCatching { if (file.isFile) file.readLines() else emptyList() }.getOrDefault(emptyList())
        return lines.mapNotNull { line ->
            val parts = line.split(' ')
            if (parts.size != 7) return@mapNotNull null
            Record(
                timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null,
                pid = parts[1].toIntOrNull() ?: return@mapNotNull null,
                kind = Kind.entries.find { it.name == parts[2] } ?: return@mapNotNull null,
                reason = parts[3].toIntOrNull() ?: return@mapNotNull null,
                importance = parts[4].toIntOrNull() ?: return@mapNotNull null,
                pssKb = parts[5].toLongOrNull() ?: return@mapNotNull null,
                rssKb = parts[6].toLongOrNull() ?: return@mapNotNull null,
            )
        }.sortedByDescending { it.timestamp }
    }

    private fun write(file: File, records: List<Record>) {
        runCatching {
            val text = records.joinToString("") { r ->
                "${r.timestamp} ${r.pid} ${r.kind.name} ${r.reason} ${r.importance} ${r.pssKb} ${r.rssKb}\n"
            }
            val temp = File(file.parentFile, "$FileName.tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                file.writeText(text)
                temp.delete()
            }
        }.onFailure { error -> Log.w(LogTag, "Could not save exit history: ${error.message}") }
    }
}
