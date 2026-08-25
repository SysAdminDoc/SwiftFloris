/*
 * Copyright (C) 2020-2025 The FlorisBoard Contributors
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

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.devtools.Devtools
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import org.florisboard.lib.android.stringRes

private class SafePreferenceInstanceWrapper : ReadOnlyProperty<Any?, FlorisPreferenceModel?> {
    val cachedPreferenceModel = try {
        FlorisPreferenceStore
    } catch (_: Throwable) {
        null
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): FlorisPreferenceModel? {
        return cachedPreferenceModel?.getValue(thisRef, property)
    }
}

class CrashDialogActivity : ComponentActivity() {
    private var stacktraces: List<CrashUtility.Stacktrace> = listOf()
    private var errorReport: String = ""
    private val prefs by SafePreferenceInstanceWrapper()

    private val stacktrace by lazy { findViewById<TextView>(R.id.stacktrace) }
    private val reportInstructions by lazy { findViewById<TextView>(R.id.report_instructions) }
    private val copyToClipboard by lazy { findViewById<Button>(R.id.copy_to_clipboard) }
    private val shareReport by lazy { findViewById<Button>(R.id.share_report) }
    private val openBugReportForm by lazy { findViewById<Button>(R.id.open_bug_report_form) }
    private val close by lazy { findViewById<Button>(R.id.close) }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = layoutInflater.inflate(R.layout.crash_dialog, null)
        setContentView(layout)

        val toolbar = layout.findViewById<Toolbar>(R.id.crash_dialog_toolbar)
        setActionBar(toolbar)

        // getUnhandledStacktraces deletes each file as it reads it, so the report
        // built below is the only surviving copy. configChanges covers rotation
        // but not a theme, font-scale or locale change, and nothing covers the
        // system recreating this activity after killing the process, so reading
        // again on every onCreate handed the user an empty report for the crash
        // they were trying to send. Restore the previous report instead, and
        // only consume the files on the run that has none.
        val restoredReport = savedInstanceState?.getString(STATE_ERROR_REPORT)
        val versionName = buildString {
            append("[")
            append(BuildConfig.VERSION_NAME)
            append("](")
            if (BuildConfig.DEBUG) {
                append(stringRes(R.string.florisboard__commit_by_hash_url, "hash" to BuildConfig.BUILD_COMMIT_HASH))
            } else {
                append(stringRes(R.string.florisboard__changelog_url, "version" to BuildConfig.VERSION_NAME))
            }
            append(")")
        }
        errorReport = if (restoredReport != null) {
            restoredReport
        } else {
            stacktraces = CrashUtility.getUnhandledStacktraces(this)
            if (stacktraces.isEmpty()) {
                flogWarning(LogTopic.CRASH_UTILITY) {
                    "Stacktrace file list is empty."
                }
            }
            CrashReportFormatter.formatReport(
                environment = CrashReportEnvironment.current(this, versionNameMarkdown = versionName),
                debugLogHeader = Devtools.generateDebugLog(
                    this@CrashDialogActivity,
                    prefs,
                    includeLogcat = false,
                ),
                stacktraces = stacktraces,
            )
        }
        stacktrace.text = errorReport

        reportInstructions.text =
            reportInstructions.text.toString().format(
                resources.getString(R.string.crash_dialog__bug_report_template)
            )

        copyToClipboard.setOnClickListener {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE)
            val toastMessage: String = if (clipboardManager != null && clipboardManager is ClipboardManager) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText(CrashReportFormatter.PRODUCT_NAME, errorReport))
                resources.getString(R.string.crash_dialog__copy_to_clipboard_success)
            } else {
                resources.getString(R.string.crash_dialog__copy_to_clipboard_failure)
            }
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
        }

        openBugReportForm.setOnClickListener {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                resources.getString(R.string.florisboard__crash_report_url).toUri()
            )
            startActivity(browserIntent)
        }

        shareReport.setOnClickListener {
            // Copy-to-clipboard was the only route out of this dialog, so a user filing the report
            // from a phone had to paste into the browser by hand. ACTION_SEND lets them hand it to
            // a mail or notes app directly. The text is the same, redaction reminder included.
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${CrashReportFormatter.PRODUCT_NAME} crash report")
                putExtra(Intent.EXTRA_TEXT, errorReport)
            }
            val chooser = Intent.createChooser(
                send,
                resources.getString(R.string.crash_dialog__share_report),
            )
            if (send.resolveActivity(packageManager) != null) {
                startActivity(chooser)
            } else {
                Toast.makeText(
                    this,
                    resources.getString(R.string.crash_dialog__share_report_unavailable),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        close.setOnClickListener {
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Saved state crosses a Binder transaction, and the report is not
        // bounded: it carries every pending stacktrace, up to 50 of them, plus
        // the debug log header. Handing an oversized string to the bundle would
        // trade a recoverable empty report for a TransactionTooLargeException.
        // The head is the part that matters, since the environment block and
        // the first stacktrace lead the report.
        outState.putString(STATE_ERROR_REPORT, errorReport.takeHeadForSavedState())
    }

    private fun String.takeHeadForSavedState(): String {
        if (length <= MAX_SAVED_REPORT_CHARS) return this
        return take(MAX_SAVED_REPORT_CHARS) + SAVED_REPORT_TRUNCATION_NOTICE
    }

    private companion object {
        const val STATE_ERROR_REPORT = "error_report"

        /** Well inside the ~1 MB Binder budget the whole bundle shares. */
        const val MAX_SAVED_REPORT_CHARS = 128 * 1024
        const val SAVED_REPORT_TRUNCATION_NOTICE =
            "\n\n[report truncated: reopen the app after a crash to capture it in full]"
    }
}
