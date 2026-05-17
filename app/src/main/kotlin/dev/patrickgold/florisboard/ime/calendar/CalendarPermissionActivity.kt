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
import androidx.activity.result.contract.ActivityResultContracts
import dev.patrickgold.florisboard.calendarQuickInsertManager

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

    private val requestCalendarPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val manager by calendarQuickInsertManager()
        if (granted) {
            manager.openPicker()
        } else {
            Toast.makeText(
                this,
                "Calendar permission is required to insert agenda events.",
                Toast.LENGTH_SHORT,
            ).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager by calendarQuickInsertManager()
        if (manager.hasReadCalendarPermission()) {
            manager.openPicker()
            finish()
        } else {
            requestCalendarPermission.launch(Manifest.permission.READ_CALENDAR)
        }
    }
}
