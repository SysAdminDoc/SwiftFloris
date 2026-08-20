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

package dev.patrickgold.florisboard.ime.window

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * The context chain walk must terminate for every shape of context the IME can
 * be composed into. Recursing on a [ContextWrapper] without unwrapping its base
 * context hangs the main thread instead of returning null, which is what an IME
 * composed inside a non-activity window (a Compose Dialog, a themed wrapper)
 * used to do.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FindWindowTest {
    @Test
    fun `resolves the window of an activity rooted context`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        activity.findWindow() shouldBe activity.window
    }

    @Test
    fun `resolves the window through a wrapper chain rooted in an activity`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapped: Context = ContextThemeWrapper(
            ContextWrapper(activity),
            android.R.style.Theme_DeviceDefault,
        )

        wrapped.findWindow() shouldBe activity.window
    }

    @Test
    fun `returns null for an application context instead of looping`() {
        val application = ApplicationProvider.getApplicationContext<Context>()

        application.findWindow() shouldBe null
    }

    @Test
    fun `returns null for a wrapper chain that is not rooted in a window`() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val wrapped: Context = ContextThemeWrapper(
            ContextWrapper(ContextWrapper(application)),
            android.R.style.Theme_DeviceDefault,
        )

        wrapped.findWindow() shouldBe null
    }

    @Test
    fun `an activity still resolves once wrapped many times`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var context: Context = activity
        repeat(64) { context = ContextWrapper(context) }

        context.findWindow() shouldNotBe null
    }
}
