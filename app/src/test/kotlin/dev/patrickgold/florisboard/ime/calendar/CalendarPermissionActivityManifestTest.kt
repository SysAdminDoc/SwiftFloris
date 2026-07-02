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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Calendar permission privacy invariant O6 — manifest coverage for the permission flow.
 *
 * `CalendarPermissionActivity` is the only surface that requests `READ_CALENDAR`,
 * and it requests it on launch — by design, because it is launched *only* after
 * the user taps the Calendar quick action (`CalendarPermissionActivity.launch`).
 * The load-bearing privacy guarantees are therefore (a) the permission is
 * declared so the request can resolve, and (b) the requesting activity is **not
 * exported**, so a third-party app cannot start it and trigger a `READ_CALENDAR`
 * prompt without the user's in-keyboard tap. This mirrors
 * `VoiceInputSetupActivityManifestTest`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CalendarPermissionActivityManifestTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun calendarPermissionActivityIsNotExported() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, CalendarPermissionActivity::class.java),
            PackageManager.GET_META_DATA,
        )

        info.exported shouldBe false
    }

    @Test
    fun readCalendarPermissionIsDeclared() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        info.requestedPermissions?.toList().orEmpty() shouldContain "android.permission.READ_CALENDAR"
    }
}
