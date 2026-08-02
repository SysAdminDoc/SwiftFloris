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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The calendar quick action is the only surface that asks for `READ_CALENDAR`. These assertions
 * pin every branch of the denial recovery: a first denial has to stay retryable, a permanent
 * denial has to route to app settings, and none of the copy may be hard-coded English.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CalendarPermissionPolicyTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun grantedResultsSkipRecoveryEntirely() {
        CalendarPermissionPolicy.outcomeOf(granted = true, canShowRationale = false) shouldBe
            CalendarPermissionOutcome.Granted
        CalendarPermissionPolicy.outcomeOf(granted = true, canShowRationale = true) shouldBe
            CalendarPermissionOutcome.Granted
        CalendarPermissionPolicy.messageResOf(CalendarPermissionOutcome.Granted) shouldBe null
        CalendarPermissionPolicy.recoveryActionResOf(CalendarPermissionOutcome.Granted) shouldBe null
    }

    @Test
    fun firstDenialStaysRetryable() {
        val outcome = CalendarPermissionPolicy.outcomeOf(granted = false, canShowRationale = true)

        outcome shouldBe CalendarPermissionOutcome.Denied
        CalendarPermissionPolicy.recoveryActionResOf(outcome) shouldBe R.string.action__retry
    }

    @Test
    fun permanentDenialRoutesToAppSettings() {
        val outcome = CalendarPermissionPolicy.outcomeOf(granted = false, canShowRationale = false)

        outcome shouldBe CalendarPermissionOutcome.PermanentlyDenied
        CalendarPermissionPolicy.recoveryActionResOf(outcome) shouldBe R.string.action__open_settings
    }

    @Test
    fun everyRecoveryBranchHasDistinctLocalizedCopy() {
        val denied = CalendarPermissionPolicy.messageResOf(CalendarPermissionOutcome.Denied)!!
        val blocked = CalendarPermissionPolicy.messageResOf(CalendarPermissionOutcome.PermanentlyDenied)!!

        denied shouldNotBe blocked
        listOf(
            R.string.calendar__permission__title,
            R.string.calendar__permission__open_settings_failed,
            R.string.calendar__panel__error,
            R.string.action__retry,
            R.string.action__open_settings,
            denied,
            blocked,
        ).forEach { resId ->
            context.getString(resId).shouldNotBeBlank()
        }
    }

    @Test
    fun appSettingsIntentTargetsThisPackageOnly() {
        val intent = CalendarPermissionPolicy.appSettingsIntent(context.packageName)

        intent.action shouldBe Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data?.scheme shouldBe "package"
        intent.data?.schemeSpecificPart shouldBe context.packageName
        (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldNotBe 0
    }

    @Test
    fun anUnresolvableSettingsActivityIsDetectableBeforeLaunching() {
        // Robolectric ships no settings activity, which is exactly the restricted-device shape the
        // activity has to survive: resolution fails, so startActivity would throw and the caller
        // must fall back to a localized toast instead of crashing.
        val intent = CalendarPermissionPolicy.appSettingsIntent(context.packageName)

        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) shouldBe null
    }
}
