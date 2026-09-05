/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

import dev.patrickgold.florisboard.ime.clipboard.ClipboardSyncBehavior
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistory
import dev.patrickgold.florisboard.ime.smartbar.CandidatesDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionJsonConfig
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.florisboard.ime.theme.extCoreTheme
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.model.PreferenceMigrationEntry
import dev.patrickgold.jetpref.datastore.model.PreferenceType
import dev.patrickgold.jetpref.datastore.runtime.DataStoreReader
import dev.patrickgold.jetpref.datastore.runtime.DataStoreWriter
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import dev.patrickgold.jetpref.datastore.runtime.LoadStrategy
import dev.patrickgold.jetpref.datastore.runtime.PersistStrategy
import dev.patrickgold.jetpref.material.ui.ColorRepresentation
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppPrefsMigrationTest : FunSpec({
    val prefs = FlorisPreferenceModelImpl()

    test("legacy key rename migrations keep old preference backups readable") {
        val renameCases = listOf(
            "media__emoji_recently_used_max_size" to "emoji__history_recent_max_size",
            "advanced__settings_theme" to "other__settings_theme",
            "advanced__accent_color" to "other__accent_color",
            "advanced__settings_language" to "other__settings_language",
            "advanced__show_app_icon" to "other__show_app_icon",
            "advanced__incognito_mode" to "suggestion__incognito_mode",
            "advanced__force_incognito_mode_from_dynamic" to "suggestion__force_incognito_mode_from_dynamic",
            "suggestion__clipboard_content_enabled" to "clipboard__suggestion_enabled",
            "suggestion__clipboard_content_timeout" to "clipboard__suggestion_timeout",
            "clipboard__num_history_grid_columns_portrait" to "clipboard__history_num_grid_columns_portrait",
            "clipboard__num_history_grid_columns_landscape" to "clipboard__history_num_grid_columns_landscape",
            "clipboard__clean_up_old" to "clipboard__history_auto_clean_old_enabled",
            "clipboard__clean_up_after" to "clipboard__history_auto_clean_old_after",
            "clipboard__auto_clean_sensitive" to "clipboard__history_auto_clean_sensitive_enabled",
            "clipboard__auto_clean_sensitive_after" to "clipboard__history_auto_clean_sensitive_after",
            "clipboard__limit_history_size" to "clipboard__history_size_limit_enabled",
            "clipboard__max_history_size" to "clipboard__history_size_limit",
            "clipboard__clear_primary_clip_deletes_last_item" to
                "clipboard__clear_primary_clip_affects_history_if_unpinned",
        )

        renameCases.forEach { (legacyKey, migratedKey) ->
            val migrated = prefs.migrate(migrationEntry(legacyKey, "legacy"))
            migrated.shouldTransformTo(key = migratedKey, rawValue = "legacy")
        }
    }

    test("legacy emoji history migration filters empty separator entries") {
        val migrated = prefs.migrate(migrationEntry(
            key = "media__emoji_recently_used",
            rawValue = "😀;😎;",
        ))

        migrated.actionName() shouldBe "TRANSFORM"
        migrated.key shouldBe "emoji__history_data"
        val history = Json.decodeFromString<EmojiHistory>(migrated.rawValue)
        history.pinned shouldBe emptyList()
        history.recent.map { it.value } shouldBe listOf("😀", "😎")
    }

    test("legacy Smartbar arrangement migration rewrites removed actions and restores required actions") {
        val legacyArrangement = QuickActionArrangement(
            stickyAction = QuickAction.InsertKey(TextKeyData.VOICE_INPUT),
            dynamicActions = listOf(
                QuickAction.InsertKey(TextKeyData.COMPACT_LAYOUT_TO_RIGHT),
            ),
            hiddenActions = emptyList(),
        )

        val migrated = prefs.migrate(migrationEntry(
            key = "smartbar__action_arrangement",
            rawValue = QuickActionJsonConfig.encodeToString(legacyArrangement),
        ))

        migrated.actionName() shouldBe "TRANSFORM"
        val arrangement = QuickActionJsonConfig.decodeFromString<QuickActionArrangement>(migrated.rawValue)
        arrangement.stickyAction shouldBe null
        arrangement.dynamicActions shouldContain QuickAction.InsertKey(TextKeyData.TOGGLE_COMPACT_LAYOUT)
        arrangement.dynamicActions shouldNotContain QuickAction.InsertKey(TextKeyData.COMPACT_LAYOUT_TO_RIGHT)
        arrangement.dynamicActions shouldContain QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH)
        arrangement.dynamicActions shouldContain QuickAction.InsertKey(TextKeyData.FORWARD_DELETE)
        arrangement.dynamicActions shouldContain QuickAction.InsertKey(TextKeyData.IME_HIDE_UI)
        arrangement.dynamicActions shouldContain QuickAction.InsertKey(TextKeyData.TOGGLE_FLOATING_WINDOW)
        arrangement.dynamicActions shouldContain QuickAction.InsertKey(TextKeyData.TOGGLE_RESIZE_MODE)
        arrangement.hiddenActions shouldContain QuickAction.InsertTask
        arrangement.hiddenActions shouldContain QuickAction.InsertCalendarEvent
    }

    test("legacy value rewrite migrations retain user intent under current defaults") {
        val valueRewriteCases = listOf(
            ValueRewriteCase(
                key = "theme__editor_display_colors_as",
                legacyRawValue = "RGBA",
                expectedKey = "theme__editor_color_representation",
                expectedRawValue = ColorRepresentation.RGB.name,
            ),
            ValueRewriteCase(
                key = "theme__editor_display_colors_as",
                legacyRawValue = "HEX",
                expectedKey = "theme__editor_color_representation",
                expectedRawValue = ColorRepresentation.HEX.name,
            ),
            ValueRewriteCase(
                key = "clipboard__sync_to_floris",
                legacyRawValue = "true",
                expectedRawValue = ClipboardSyncBehavior.ALL_EVENTS.name,
            ),
            ValueRewriteCase(
                key = "clipboard__sync_to_system",
                legacyRawValue = "false",
                expectedRawValue = ClipboardSyncBehavior.NO_EVENTS.name,
            ),
        )

        valueRewriteCases.forEach { case ->
            val migrated = prefs.migrate(migrationEntry(case.key, case.legacyRawValue, case.type))
            migrated.shouldTransformTo(
                key = case.expectedKey,
                rawValue = case.expectedRawValue,
                type = case.expectedType,
            )
        }
    }

    test("legacy conditional migrations reset invalid values and keep already-compatible values") {
        prefs.migrate(migrationEntry("keyboard__one_handed_mode", "OFF")).actionName() shouldBe "RESET"
        prefs.migrate(migrationEntry("keyboard__one_handed_mode", "START")).actionName() shouldBe "KEEP_AS_IS"
        prefs.migrate(
            migrationEntry("keyboard__key_spacing_horizontal", "1.0", PreferenceType.float()),
        ).actionName() shouldBe "RESET"
        prefs.migrate(
            migrationEntry("keyboard__key_spacing_vertical", "1.0", PreferenceType.float()),
        ).actionName() shouldBe "RESET"
        prefs.migrate(
            migrationEntry("keyboard__key_spacing_horizontal", "100", PreferenceType.integer()),
        ).actionName() shouldBe "KEEP_AS_IS"
        prefs.migrate(
            migrationEntry("keyboard__key_spacing_vertical", "100", PreferenceType.integer()),
        ).actionName() shouldBe "KEEP_AS_IS"
    }

    // Regression guard for issue #22. These nine keys used to be rewritten
    // unconditionally by migrate(), which jetpref runs on every datastore load
    // and on every import, so a user could not keep any of these settings. The
    // fork's preferred starting point is carried by each PreferenceData default
    // instead, so migration must leave a stored value alone whatever it is.
    test("preferences a user can choose are never rewritten by migration") {
        val everySelectableValue = buildList {
            for (rawValue in listOf("true", "false")) {
                add("keyboard__number_row" to rawValue)
                add("keyboard__hinted_number_row_enabled" to rawValue)
                add("keyboard__hinted_symbols_enabled" to rawValue)
            }
            UtilityKeyAction.entries.forEach { add("keyboard__utility_key_action" to it.name) }
            SpaceBarMode.entries.forEach { add("keyboard__space_bar_display_mode" to it.name) }
            CandidatesDisplayMode.entries.forEach { add("suggestion__display_mode" to it.name) }
            ThemeMode.entries.forEach { add("theme__mode" to it.name) }
            for (themeId in BUNDLED_CORE_THEME_IDS) {
                add("theme__day_theme_id" to "org.florisboard.themes:$themeId")
                add("theme__night_theme_id" to "org.florisboard.themes:$themeId")
            }
        }

        everySelectableValue.forEach { (key, rawValue) ->
            val migrated = prefs.migrate(migrationEntry(key, rawValue))
            withClue("$key=$rawValue must survive migration") {
                migrated.actionName() shouldBe "KEEP_AS_IS"
                migrated.key shouldBe key
                migrated.rawValue shouldBe rawValue
            }
        }
    }

    // Issue #22's second symptom: the settings were not restored from a backup
    // either. jetpref applies migrate() inside DataStore.loadAndUpdate, which
    // runs for Event.Init (every process start) and for Event.Import (restore),
    // so a rule there survives neither. This drives one stored blob through two
    // loads and one import and compares against the values that were chosen,
    // never against the previous trip.
    test("chosen preferences survive two datastore loads and a restore") {
        fun FlorisPreferenceModel.assertChosenValues(stage: String) {
            val model = this@assertChosenValues
            withClue(stage) {
                model.keyboard.numberRow.get() shouldBe false
                model.keyboard.hintedNumberRowEnabled.get() shouldBe true
                model.keyboard.hintedSymbolsEnabled.get() shouldBe true
                model.keyboard.utilityKeyAction.get() shouldBe UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS
                model.keyboard.spaceBarMode.get() shouldBe SpaceBarMode.CURRENT_LANGUAGE
                model.suggestion.displayMode.get() shouldBe CandidatesDisplayMode.DYNAMIC_SCROLLABLE
                model.theme.mode.get() shouldBe ThemeMode.FOLLOW_SYSTEM
                model.theme.dayThemeId.get() shouldBe extCoreTheme("floris_day")
                model.theme.nightThemeId.get() shouldBe extCoreTheme("floris_night")
            }
        }

        val chosenStore = jetprefDataStoreOf(FlorisPreferenceModel::class)
        val chosen by chosenStore
        chosenStore.init(LoadStrategy.Disabled, PersistStrategy.Disabled).getOrThrow()
        chosen.keyboard.numberRow.set(false).getOrThrow()
        chosen.keyboard.hintedNumberRowEnabled.set(true).getOrThrow()
        chosen.keyboard.hintedSymbolsEnabled.set(true).getOrThrow()
        chosen.keyboard.utilityKeyAction.set(UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS).getOrThrow()
        chosen.keyboard.spaceBarMode.set(SpaceBarMode.CURRENT_LANGUAGE).getOrThrow()
        chosen.suggestion.displayMode.set(CandidatesDisplayMode.DYNAMIC_SCROLLABLE).getOrThrow()
        chosen.theme.mode.set(ThemeMode.FOLLOW_SYSTEM).getOrThrow()
        chosen.theme.dayThemeId.set(extCoreTheme("floris_day")).getOrThrow()
        chosen.theme.nightThemeId.set(extCoreTheme("floris_night")).getOrThrow()

        val backup = InMemoryDatastoreBlob()
        chosenStore.export(backup).getOrThrow()

        // First Event.Init: the process the user changed the settings in restarts.
        val firstRestart = jetprefDataStoreOf(FlorisPreferenceModel::class)
        val firstRestartModel by firstRestart
        firstRestart.init(LoadStrategy.UseReader(backup), PersistStrategy.Disabled).getOrThrow()
        firstRestartModel.assertChosenValues("after the first load")

        // Second Event.Init, from what the first generation would have persisted.
        val secondGeneration = InMemoryDatastoreBlob()
        firstRestart.export(secondGeneration).getOrThrow()
        val secondRestart = jetprefDataStoreOf(FlorisPreferenceModel::class)
        val secondRestartModel by secondRestart
        secondRestart.init(LoadStrategy.UseReader(secondGeneration), PersistStrategy.Disabled).getOrThrow()
        secondRestartModel.assertChosenValues("after the second load")

        // Event.Import: restoring the backup archive onto a default install.
        val restored = jetprefDataStoreOf(FlorisPreferenceModel::class)
        val restoredModel by restored
        restored.init(LoadStrategy.Disabled, PersistStrategy.Disabled).getOrThrow()
        restored.import(ImportStrategy.Erase, backup).getOrThrow()
        restoredModel.assertChosenValues("after restoring a backup")
    }

    test("fixture catalog covers every retained legacy migration key") {
        TESTED_LEGACY_MIGRATION_KEYS shouldBe setOf(
            "media__emoji_recently_used",
            "media__emoji_recently_used_max_size",
            "advanced__settings_theme",
            "advanced__accent_color",
            "advanced__settings_language",
            "advanced__show_app_icon",
            "advanced__incognito_mode",
            "advanced__force_incognito_mode_from_dynamic",
            "suggestion__clipboard_content_enabled",
            "suggestion__clipboard_content_timeout",
            "keyboard__one_handed_mode",
            "smartbar__action_arrangement",
            "theme__editor_display_colors_as",
            "clipboard__sync_to_floris",
            "clipboard__sync_to_system",
            "clipboard__num_history_grid_columns_portrait",
            "clipboard__num_history_grid_columns_landscape",
            "clipboard__clean_up_old",
            "clipboard__clean_up_after",
            "clipboard__auto_clean_sensitive",
            "clipboard__auto_clean_sensitive_after",
            "clipboard__limit_history_size",
            "clipboard__max_history_size",
            "clipboard__clear_primary_clip_deletes_last_item",
            "keyboard__key_spacing_horizontal",
            "keyboard__key_spacing_vertical",
        )
    }
})

private data class ValueRewriteCase(
    val key: String,
    val legacyRawValue: String,
    val expectedRawValue: String,
    val expectedKey: String = key,
    val type: PreferenceType = PreferenceType.string(),
    val expectedType: PreferenceType = type,
)

private val TESTED_LEGACY_MIGRATION_KEYS = setOf(
    "media__emoji_recently_used",
    "media__emoji_recently_used_max_size",
    "advanced__settings_theme",
    "advanced__accent_color",
    "advanced__settings_language",
    "advanced__show_app_icon",
    "advanced__incognito_mode",
    "advanced__force_incognito_mode_from_dynamic",
    "suggestion__clipboard_content_enabled",
    "suggestion__clipboard_content_timeout",
    "keyboard__one_handed_mode",
    "smartbar__action_arrangement",
    "theme__editor_display_colors_as",
    "clipboard__sync_to_floris",
    "clipboard__sync_to_system",
    "clipboard__num_history_grid_columns_portrait",
    "clipboard__num_history_grid_columns_landscape",
    "clipboard__clean_up_old",
    "clipboard__clean_up_after",
    "clipboard__auto_clean_sensitive",
    "clipboard__auto_clean_sensitive_after",
    "clipboard__limit_history_size",
    "clipboard__max_history_size",
    "clipboard__clear_primary_clip_deletes_last_item",
    "keyboard__key_spacing_horizontal",
    "keyboard__key_spacing_vertical",
)

private val BUNDLED_CORE_THEME_IDS = listOf(
    "floris_day",
    "floris_night",
    "floris_pure_night",
    "swiftkey_pure_light",
    "swiftkey_pure_dark",
    "swiftkey_high_contrast",
    "swift_glacier",
    "swift_slate",
)

/** Captures what a datastore exports so it can be read back verbatim. */
private class InMemoryDatastoreBlob : DataStoreReader, DataStoreWriter {
    private var blob: String = ""

    override suspend fun read(): String = blob

    override suspend fun write(content: String) {
        blob = content
    }
}

private fun migrationEntry(
    key: String,
    rawValue: String,
    type: PreferenceType = PreferenceType.string(),
): PreferenceMigrationEntry {
    val actionClass = Class.forName(
        "dev.patrickgold.jetpref.datastore.model.PreferenceMigrationEntry\$Action",
    )
    val keepAction = actionClass.enumConstants.orEmpty().first { it.toString() == "KEEP_AS_IS" }
    val constructor = PreferenceMigrationEntry::class.java.getDeclaredConstructor(
        actionClass,
        PreferenceType::class.java,
        String::class.java,
        String::class.java,
    )
    constructor.isAccessible = true
    return constructor.newInstance(
        keepAction,
        type,
        key,
        rawValue,
    ) as PreferenceMigrationEntry
}

private fun PreferenceMigrationEntry.shouldTransformTo(
    key: String,
    rawValue: String,
    type: PreferenceType = this.type,
) {
    actionName() shouldBe "TRANSFORM"
    this.key shouldBe key
    this.rawValue shouldBe rawValue
    this.type shouldBe type
}

private fun PreferenceMigrationEntry.actionName(): String {
    val action = PreferenceMigrationEntry::class.java
        .getDeclaredMethod("getAction\$datastore_model")
        .invoke(this)
    return requireNotNull(action)
        .toString()
}
