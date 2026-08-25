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
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Android 16 routes a user from the system keyboard settings to the IME's own
 * language setup. Discussion #21 is somebody hunting for Portuguese in the wrong
 * screen while the data was already bundled, so this path is worth pinning.
 *
 * The platform does not discover it by broadcasting an action. It reads
 * `android:languageSettingsActivity` off the `<input-method>` element and builds
 * an explicit component intent from it, which is why an intent-filter here would
 * do nothing at all. These assertions therefore check the three places that have
 * to agree: the attribute in `method.xml`, the alias in the manifest, and the
 * constant the activity compares against.
 */
@RunWith(AndroidJUnit4::class)
class ImeLanguageSettingsEntryPointTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun projectFile(path: String): File {
        return sequenceOf(File(path), File("../$path"))
            .firstOrNull { it.exists() && it.canRead() }
            ?: error("File is not reachable from ${File(".").absolutePath}: $path")
    }

    @Test
    fun methodXmlNamesTheLanguageSettingsActivity() {
        // Without this attribute createImeLanguageSettingsActivityIntent returns
        // null and the system offers the user no way through, whatever the
        // manifest declares.
        val methodXml = projectFile("app/src/main/res/xml/method.xml").readText()

        assertTrue(
            "android:languageSettingsActivity=\"${FlorisAppActivity.ImeLanguageSettingsAliasName}\"" in methodXml,
            "method.xml must point at the alias the manifest declares",
        )
    }

    @Test
    fun theNamedComponentExistsAndTargetsTheActivityThatCanNavigate() {
        val info = context.packageManager.getActivityInfo(
            android.content.ComponentName(
                context.packageName,
                FlorisAppActivity.ImeLanguageSettingsAliasName,
            ),
            0,
        )

        assertEquals(
            FlorisAppActivity::class.java.name,
            info.targetActivity ?: info.name,
            "the alias must resolve to the activity that knows how to navigate",
        )
    }

    @Test
    fun theEntryPointIsNotExported() {
        // The platform starts it from inside our own package by explicit
        // component, so nothing outside needs to reach it.
        val info = context.packageManager.getActivityInfo(
            android.content.ComponentName(
                context.packageName,
                FlorisAppActivity.ImeLanguageSettingsAliasName,
            ),
            0,
        )

        assertFalse(info.exported, "an internal entry point should not be exported")
    }

    @Test
    fun noStrayIntentFilterAdvertisesAnActionThePlatformNeverSends() {
        // A previous attempt matched on an ACTION_IME_LANGUAGE_SETTINGS
        // intent-filter. The platform never sends one, so that was dead code
        // dressed up as an integration.
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertFalse(
            "IME_LANGUAGE_SETTINGS" in manifest,
            "the manifest should not advertise an action the platform does not use",
        )
    }

    @Test
    fun theResolvedComponentIsQueryableThroughPackageManager() {
        val activities = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            .activities
            .orEmpty()

        assertTrue(
            activities.any { it.name == FlorisAppActivity.ImeLanguageSettingsAliasName },
            "the alias must be present in the merged manifest",
        )
    }
}
