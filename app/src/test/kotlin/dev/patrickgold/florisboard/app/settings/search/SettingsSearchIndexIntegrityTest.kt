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
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.devtools.devtoolsSearchRoute
import dev.patrickgold.florisboard.app.ext.ExtensionListScreenType
import dev.patrickgold.florisboard.app.settings.dictionary.UserDictionaryType
import dev.patrickgold.florisboard.app.settings.localization.LanguagePackManagerScreenAction
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsSearchIndexIntegrityTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun entryIdsAreUnique() {
        val duplicateIds = SettingsSearchIndex.entries
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys

        duplicateIds.shouldBeEmpty()
    }

    @Test
    fun allEntryResourcesResolveToRealNonBlankStrings() {
        val failures = buildList {
            SettingsSearchIndex.entries.forEach { entry ->
                checkStringResource("${entry.id}.screenTitleResId", entry.screenTitleResId)
                checkStringResource("${entry.id}.titleResId", entry.titleResId)
                entry.summaryResId?.let { resId ->
                    checkStringResource("${entry.id}.summaryResId", resId)
                }
            }
        }

        failures.shouldBeEmpty()
    }

    @Test
    fun everyDestinationBuildsTheExpectedTypedRoute() {
        val actualRoutes = SettingsSearchDestination.entries.associateWith { it.toSearchRoute() }

        actualRoutes shouldBe expectedRoutes
    }

    private fun MutableList<String>.checkStringResource(label: String, resId: Int) {
        val value = try {
            context.resources.getString(resId)
        } catch (_: Resources.NotFoundException) {
            add("$label is missing string resource $resId")
            return
        }

        if (value.isBlank()) {
            add("$label resolves to blank string resource $resId")
        }
        if (value == "res-$resId") {
            add("$label still resolves through the fake test fallback for $resId")
        }
    }
}

private val expectedRoutes = mapOf<SettingsSearchDestination, Any?>(
    SettingsSearchDestination.HOME to Routes.Settings.Home,
    SettingsSearchDestination.LOCALIZATION to Routes.Settings.Localization,
    SettingsSearchDestination.SELECT_LOCALE to Routes.Settings.SelectLocale,
    SettingsSearchDestination.PER_APP_LANGUAGE to Routes.Settings.PerAppLanguage,
    SettingsSearchDestination.LANGUAGE_PACK_MANAGER to
        Routes.Settings.LanguagePackManager(LanguagePackManagerScreenAction.MANAGE),
    SettingsSearchDestination.SUBTYPE_ADD to Routes.Settings.SubtypeAdd,
    SettingsSearchDestination.THEME to Routes.Settings.Theme,
    SettingsSearchDestination.THEME_MANAGER to
        Routes.Ext.List(ExtensionListScreenType.EXT_THEME, showUpdate = true),
    SettingsSearchDestination.KEYBOARD to Routes.Settings.Keyboard,
    SettingsSearchDestination.INPUT_FEEDBACK to Routes.Settings.InputFeedback,
    SettingsSearchDestination.SMARTBAR to Routes.Settings.Smartbar,
    SettingsSearchDestination.TYPING to Routes.Settings.Typing,
    SettingsSearchDestination.TYPING_STATS to Routes.Settings.TypingStats,
    SettingsSearchDestination.VOICE_INPUT to Routes.Settings.VoiceInput,
    SettingsSearchDestination.DICTIONARY to Routes.Settings.Dictionary,
    SettingsSearchDestination.MIGRATION_ASSISTANT to Routes.Settings.MigrationAssistant,
    SettingsSearchDestination.USER_DICTIONARY_SYSTEM to
        Routes.Settings.UserDictionary(UserDictionaryType.SYSTEM),
    SettingsSearchDestination.USER_DICTIONARY_FLORIS to
        Routes.Settings.UserDictionary(UserDictionaryType.FLORIS),
    SettingsSearchDestination.LEARNED_ENTRIES to Routes.Settings.LearnedEntries,
    SettingsSearchDestination.SYNC to Routes.Settings.Sync,
    SettingsSearchDestination.MCP to Routes.Settings.Mcp,
    SettingsSearchDestination.ADDONS to Routes.Settings.Addons,
    SettingsSearchDestination.EXTENSIONS to Routes.Ext.Home,
    SettingsSearchDestination.GESTURES to Routes.Settings.Gestures,
    SettingsSearchDestination.CLIPBOARD to Routes.Settings.Clipboard,
    SettingsSearchDestination.MEDIA to Routes.Settings.Media,
    SettingsSearchDestination.OTHER to Routes.Settings.Other,
    SettingsSearchDestination.PHYSICAL_KEYBOARD to Routes.Settings.PhysicalKeyboard,
    SettingsSearchDestination.BACKUP to Routes.Settings.Backup,
    SettingsSearchDestination.RESTORE to Routes.Settings.Restore,
    SettingsSearchDestination.PRIVACY_POSTURE to Routes.Settings.PrivacyPosture,
    SettingsSearchDestination.PER_APP_KEYBOARD_PROFILES to Routes.Settings.PerAppKeyboardProfiles,
    SettingsSearchDestination.PRIVACY_AUDIT to Routes.Settings.PrivacyAuditLog,
    SettingsSearchDestination.ABOUT to Routes.Settings.About,
    SettingsSearchDestination.AI_FEATURES to Routes.Settings.AiFeatures,
    SettingsSearchDestination.PROJECT_LICENSE to Routes.Settings.ProjectLicense,
    SettingsSearchDestination.THIRD_PARTY_LICENSES to Routes.Settings.ThirdPartyLicenses,
    SettingsSearchDestination.DEVTOOLS to devtoolsSearchRoute(),
    SettingsSearchDestination.SNIPPET_SETTINGS to Routes.Settings.SnippetSettings,
)
