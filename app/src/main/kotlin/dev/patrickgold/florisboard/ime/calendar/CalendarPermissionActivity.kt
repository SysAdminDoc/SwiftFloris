/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.calendar

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.ActivityCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.calendarQuickInsertManager
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes

class CalendarPermissionActivity : ComponentActivity() {
    companion object {
        fun launch(context: Context): Boolean {
            val intent = Intent(context, CalendarPermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }

    private var recovery by mutableStateOf<CalendarPermissionOutcome?>(null)

    private val requestCalendarPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager by calendarQuickInsertManager()
        if (manager.hasReadCalendarPermission()) {
            manager.openPicker()
            finish()
            return
        }
        setFinishOnTouchOutside(true)
        setContent {
            Content()
        }
        if (savedInstanceState == null) {
            requestCalendarPermission.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    private fun onPermissionResult(granted: Boolean) {
        val outcome = CalendarPermissionPolicy.outcomeOf(
            granted = granted,
            canShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_CALENDAR,
            ),
        )
        if (outcome == CalendarPermissionOutcome.Granted) {
            val manager by calendarQuickInsertManager()
            manager.openPicker()
            finish()
            return
        }
        recovery = outcome
    }

    @Composable
    private fun Content() {
        val prefs by FlorisPreferenceStore
        val outcome = recovery ?: return

        ProvideLocalizedResources(
            resourcesContext = this,
            appName = R.string.app_name,
            forceLayoutDirection = LayoutDirection.Ltr,
        ) {
            val theme by prefs.other.settingsTheme.collectAsState()
            FlorisAppTheme(theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                ) {
                    RecoveryDialog(outcome)
                }
            }
        }
    }

    @Composable
    private fun RecoveryDialog(outcome: CalendarPermissionOutcome) {
        val messageRes = CalendarPermissionPolicy.messageResOf(outcome) ?: return
        val actionRes = CalendarPermissionPolicy.recoveryActionResOf(outcome) ?: return
        AlertDialog(
            onDismissRequest = ::finish,
            shape = MaterialTheme.shapes.large,
            title = { Text(text = stringRes(R.string.calendar__permission__title)) },
            text = {
                Text(
                    text = stringRes(messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    shape = MaterialTheme.shapes.small,
                    onClick = {
                        when (outcome) {
                            CalendarPermissionOutcome.Denied -> {
                                recovery = null
                                requestCalendarPermission.launch(Manifest.permission.READ_CALENDAR)
                            }
                            CalendarPermissionOutcome.PermanentlyDenied -> openAppSettings()
                            CalendarPermissionOutcome.Granted -> finish()
                        }
                    },
                ) {
                    Text(text = stringRes(actionRes))
                }
            },
            dismissButton = {
                TextButton(
                    shape = MaterialTheme.shapes.small,
                    onClick = ::finish,
                ) {
                    Text(text = stringRes(R.string.action__cancel))
                }
            },
        )
    }

    /**
     * Opens this app's details page so the user can re-grant calendar access. Devices without a
     * reachable settings activity keep the dialog open and report the failure instead of crashing.
     */
    private fun openAppSettings() {
        try {
            startActivity(CalendarPermissionPolicy.appSettingsIntent(packageName))
            finish()
        } catch (error: ActivityNotFoundException) {
            reportAppSettingsFailure(error)
        } catch (error: SecurityException) {
            reportAppSettingsFailure(error)
        }
    }

    private fun reportAppSettingsFailure(error: Throwable) {
        flogError { "Could not open app settings for calendar permission recovery: $error" }
        Toast.makeText(
            this,
            R.string.calendar__permission__open_settings_failed,
            Toast.LENGTH_LONG,
        ).show()
    }
}
