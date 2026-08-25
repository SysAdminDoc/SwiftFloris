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

package dev.patrickgold.florisboard.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Android 16 asks an IME where its language settings live, and routes the user
 * there from the system keyboard settings. Discussion #21 is somebody hunting
 * for Portuguese in the wrong screen while the data was already bundled, so the
 * shortest path from "the system offered me a link" to "I can add a language"
 * is worth pinning.
 *
 * Resolved against the merged manifest rather than grepped out of the source,
 * because what matters is that the platform can find the activity, not that a
 * string appears in a file.
 */
@RunWith(AndroidJUnit4::class)
class ImeLanguageSettingsEntryPointTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theActionWeListenForIsTheOneThePlatformSends() {
        // The activity supports API 26, so the constant is spelled out rather
        // than referenced. This is what stops the two drifting apart.
        assertEquals(
            InputMethodInfo.ACTION_IME_LANGUAGE_SETTINGS,
            "android.view.inputmethod.action.IME_LANGUAGE_SETTINGS",
        )
    }

    @Test
    fun exactlyOneActivityAnswersTheLanguageSettingsAction() {
        val intent = Intent(InputMethodInfo.ACTION_IME_LANGUAGE_SETTINGS)
            .setPackage(context.packageName)

        val matches = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )

        assertEquals(
            1,
            matches.size,
            "expected exactly one entry point; the system picks arbitrarily among several " +
                "and none at all leaves the keyboard settings with no way through",
        )
    }

    @Test
    fun theEntryPointLandsOnTheSettingsActivity() {
        val intent = Intent(InputMethodInfo.ACTION_IME_LANGUAGE_SETTINGS)
            .setPackage(context.packageName)

        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )

        assertEquals(
            FlorisAppActivity::class.java.name,
            resolved?.activityInfo?.targetActivity ?: resolved?.activityInfo?.name,
            "the alias must target the activity that knows how to navigate",
        )
    }

    @Test
    @Config(sdk = [36])
    fun theEntryPointIsPresentOnTheReleaseThatIntroducedTheAction() {
        val intent = Intent(InputMethodInfo.ACTION_IME_LANGUAGE_SETTINGS)
            .setPackage(context.packageName)

        assertEquals(
            1,
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size,
        )
    }
}
