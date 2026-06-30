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

package dev.patrickgold.florisboard.app.settings.about

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import io.kotest.matchers.string.shouldContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ThirdPartyLicensesMetadataTest {
    @Test
    fun generatedLicenseMetadataIncludesAboutLibraries15() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metadata = context.resources.openRawResource(R.raw.aboutlibraries)
            .bufferedReader()
            .use { it.readText() }

        metadata shouldContain "\"uniqueId\":\"com.mikepenz:aboutlibraries-core-android\""
        metadata shouldContain "\"uniqueId\":\"com.mikepenz:aboutlibraries-compose-m3-android\""
        metadata shouldContain "\"artifactVersion\":\"15.0.3\""
    }
}
