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

package dev.patrickgold.florisboard.app.settings.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Fails when a settings screen declares a preference row that settings search cannot find.
 *
 * [SettingsSearchIndexIntegrityTest] checks that the index is internally consistent — unique ids,
 * resolvable strings, routable destinations — by iterating the index itself. That can only
 * validate what is already there, so it never noticed that Input Feedback indexed 0 of its 16
 * preferences, Addons 0 of 8, MCP 0 of 9 and Gestures 7 of 28: searching for "vibration strength"
 * or "utility key action" returned nothing at all. The gap grew with every new preference.
 *
 * This reads the other direction: it enumerates the preference rows the screens declare and
 * requires each one to have an entry. A new preference is therefore searchable or the build
 * fails. [EXCLUDED_TITLES] is the escape hatch, and every entry in it carries its reason.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsSearchCoverageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyPreferenceRowTheSettingsScreensDeclareIsSearchable() {
        val declared = declaredPreferenceTitles()
        // Guard the scanner itself: a regex that stops matching would otherwise make this test
        // pass by finding nothing at all.
        declared.size shouldBeGreaterThan 200

        val indexed = SettingsSearchIndex.entries.map { it.titleResId }.toSet()
        val missing = declared
            .filterNot { it.resourceName in EXCLUDED_TITLES }
            .filterNot { resolveStringId(it.resourceName) in indexed }
            .map { "${it.screenFile}: ${it.resourceName}" }
            .sorted()

        missing.shouldBeEmpty()
    }

    @Test
    fun everyExclusionStillNamesARealPreferenceRow() {
        // An exclusion that no longer matches anything is a stale licence to omit something.
        val declaredNames = declaredPreferenceTitles().map { it.resourceName }.toSet()
        val stale = EXCLUDED_TITLES.filterNot { it in declaredNames }.sorted()

        stale.shouldBeEmpty()
    }

    @Test
    fun theScannerFindsRowsInAScreenItIsPointedAt() {
        // Proves the scan is doing real work on a known file rather than returning an empty set.
        val gestures = declaredPreferenceTitles().filter { it.screenFile.endsWith("GesturesScreen.kt") }

        gestures.size shouldBeGreaterThan 20
        gestures.any { it.resourceName == "pref__gestures__swipe_up__label" } shouldBe true
    }

    private fun resolveStringId(name: String): Int =
        context.resources.getIdentifier(name, "string", context.packageName)

    private fun declaredPreferenceTitles(): List<DeclaredPreference> {
        val settingsDir = sequenceOf(
            File("app/src/main/kotlin/dev/patrickgold/florisboard/app/settings"),
            File("src/main/kotlin/dev/patrickgold/florisboard/app/settings"),
        ).firstOrNull { it.isDirectory }
            ?: error("settings sources are not reachable from ${File(".").absolutePath}")

        return settingsDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.parentFile?.name == "search" }
            .flatMap { file ->
                val source = file.readText()
                PreferenceCall.findAll(source).mapNotNull { call ->
                    val body = source.substring(
                        call.range.last + 1,
                        minOf(source.length, call.range.last + 1 + CALL_BODY_SCAN_LENGTH),
                    )
                    TitleResource.find(body)?.let { title ->
                        DeclaredPreference(file.name, title.groupValues[1])
                    }
                }
            }
            .distinctBy { it.screenFile to it.resourceName }
            .toList()
    }

    private data class DeclaredPreference(val screenFile: String, val resourceName: String)

    private companion object {
        /**
         * jetpref's preference composables. A row rendered by anything else is not a preference
         * and is not expected to be searchable.
         */
        val PreferenceCall = Regex(
            "\\b(Preference|SwitchPreference|ListPreference|DialogSliderPreference|" +
                "ColorPickerPreference|TextFieldPreference|CustomPreference)\\s*\\(",
        )
        val TitleResource = Regex("title\\s*=\\s*stringRes\\(\\s*R\\.string\\.([A-Za-z0-9_]+)")

        /**
         * How far past the opening parenthesis to look for the `title =` argument. Long enough for
         * the widest call in the tree, short enough not to run into the next one.
         */
        const val CALL_BODY_SCAN_LENGTH = 700

        /**
         * Preference rows deliberately left out of search, each with its reason. Keep this short:
         * an entry here is a row a user can see and cannot find.
         */
        val EXCLUDED_TITLES = setOf(
            // Rows inside the theme editor's fine-tune dialog. The dialog is reachable only from
            // an open theme edit session, so a search result could not navigate to it.
            "settings__theme_editor__fine_tune__level",
            "settings__theme_editor__fine_tune__color_representation",
            "settings__theme_editor__fine_tune__display_kbd_after_dialogs",
        )
    }
}
