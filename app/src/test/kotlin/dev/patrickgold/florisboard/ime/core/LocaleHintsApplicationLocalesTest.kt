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

package dev.patrickgold.florisboard.ime.core

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The app's own language is read only where Android lets the current keyboard read it. Robolectric
 * hands it out on any version, so these show which versions are asked at all.
 *
 * A plain Application, since the real one's start-up work on these older versions keeps the fonts
 * jar open and the next class on a newer version then fails to set up.
 */
@RunWith(AndroidJUnit4::class)
class LocaleHintsApplicationLocalesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun appPicksGerman() {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("de-DE")
    }

    @Test
    @Config(sdk = [34], application = Application::class)
    fun androidFourteenGivesTheKeyboardTheAppsOwnLanguage() {
        appPicksGerman()
        LocaleHints.applicationLocales(context, context.packageName) shouldBe listOf(Locale.GERMANY)
    }

    @Test
    @Config(sdk = [33], application = Application::class)
    fun androidThirteenIsNotAskedSinceItRefusesKeyboards() {
        appPicksGerman()
        LocaleHints.applicationLocales(context, context.packageName) shouldBe emptyList()
    }
}
