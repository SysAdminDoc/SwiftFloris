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

package dev.patrickgold.florisboard.ime.calendar

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import dev.patrickgold.florisboard.R

/** What the keyboard should offer after a `READ_CALENDAR` request came back. */
enum class CalendarPermissionOutcome {
    /** The agenda picker can open right away. */
    Granted,

    /** Denied once; the system will still show the prompt, so retrying is worthwhile. */
    Denied,

    /** Denied for good (or blocked by policy); only app settings can restore access. */
    PermanentlyDenied,
}

/**
 * Pure decisions for the calendar permission trampoline. Kept free of activity state so every
 * grant / deny / permanently-denied branch is testable without driving the system prompt.
 */
object CalendarPermissionPolicy {
    /**
     * @param granted result reported by the permission contract.
     * @param canShowRationale `shouldShowRequestPermissionRationale` **after** the result. Android
     *  reports `false` once the user picked "don't ask again" or a device policy blocks the
     *  permission, which is the only reliable signal that another prompt would be a no-op.
     */
    fun outcomeOf(granted: Boolean, canShowRationale: Boolean): CalendarPermissionOutcome {
        return when {
            granted -> CalendarPermissionOutcome.Granted
            canShowRationale -> CalendarPermissionOutcome.Denied
            else -> CalendarPermissionOutcome.PermanentlyDenied
        }
    }

    /** Localized body copy explaining the outcome, or `null` when there is nothing to recover from. */
    fun messageResOf(outcome: CalendarPermissionOutcome): Int? {
        return when (outcome) {
            CalendarPermissionOutcome.Granted -> null
            CalendarPermissionOutcome.Denied -> R.string.calendar__permission__denied_message
            CalendarPermissionOutcome.PermanentlyDenied -> R.string.calendar__permission__blocked_message
        }
    }

    /** Label of the recovery action offered for [outcome], or `null` when none applies. */
    fun recoveryActionResOf(outcome: CalendarPermissionOutcome): Int? {
        return when (outcome) {
            CalendarPermissionOutcome.Granted -> null
            CalendarPermissionOutcome.Denied -> R.string.action__retry
            CalendarPermissionOutcome.PermanentlyDenied -> R.string.action__open_settings
        }
    }

    /**
     * App-details settings intent for [packageName]. The caller must still guard `startActivity`:
     * the settings activity is absent or restricted on some builds.
     */
    fun appSettingsIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
