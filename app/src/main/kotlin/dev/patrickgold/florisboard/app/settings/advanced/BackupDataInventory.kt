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

package dev.patrickgold.florisboard.app.settings.advanced

/** Android Auto Backup / data-extraction domain a store lives in. */
enum class BackupDomain(val xmlName: String) {
    Root("root"),
    File("file"),
    Database("database"),
    SharedPref("sharedpref"),
}

/** What happens to a persisted store when the user backs up or transfers the app. */
enum class BackupDisposition {
    /** Carried by both the manual archive and Android's own backup/transfer rules. */
    Included,

    /**
     * Deliberately kept out of every archive: learned typing data, clipboard contents, or key
     * material bound to this device's Keystore that would arrive undecryptable elsewhere.
     */
    SensitiveExcluded,

    /** Transient runtime state with no restore value. */
    Ephemeral,

    /**
     * Persisted, restorable in principle, but not yet carried by the manual archive. Named
     * explicitly in the backup UI so an archive is never presented as complete when it is not.
     */
    NotYetCovered,
}

/** Section of the manual archive a store belongs to, when it is carried by one. */
enum class BackupSection {
    JetprefDatastore,
    ImeKeyboard,
    ImeTheme,
    LocalStickerPacks,
    ClipboardTextItems,
    ClipboardImageItems,
    ClipboardVideoItems,
}

/**
 * One persisted store.
 *
 * @param path the Android rules path for [domain]; for [BackupDomain.File] stores that are a
 *  filename prefix (per-locale n-gram tables) this is the prefix.
 */
data class BackupDataEntry(
    val id: String,
    val domain: BackupDomain,
    val path: String,
    val disposition: BackupDisposition,
    val section: BackupSection? = null,
    /** True when Android's rules must carry an explicit `<exclude>` for this path. */
    val requiresAndroidExclude: Boolean = false,
)

/**
 * The single inventory of everything SwiftFloris persists.
 *
 * Backup coverage used to be described in three places that could disagree: the manual-archive
 * selector, `backup_rules.xml`, and `data_extraction_rules.xml`. This list is the one place that
 * says what exists and how each store is treated; the parity tests check the XML rules and the
 * archive sections against it, so a new store cannot be added and silently forgotten.
 *
 * Android's rule files are allowlists — only `jetpref_datastore` (root) and `files/ime` are
 * included — so a store needs an explicit `<exclude>` only when it would otherwise fall inside
 * one of those included paths, or when the exclusion is load-bearing enough to state outright.
 */
object BackupDataInventory {
    val entries: List<BackupDataEntry> = listOf(
        BackupDataEntry(
            id = "jetpref_datastore",
            domain = BackupDomain.Root,
            path = "jetpref_datastore",
            disposition = BackupDisposition.Included,
            section = BackupSection.JetprefDatastore,
        ),
        BackupDataEntry(
            id = "ime_keyboard_extensions",
            domain = BackupDomain.File,
            path = "ime/keyboard",
            disposition = BackupDisposition.Included,
            section = BackupSection.ImeKeyboard,
        ),
        BackupDataEntry(
            id = "ime_theme_extensions",
            domain = BackupDomain.File,
            path = "ime/theme",
            disposition = BackupDisposition.Included,
            section = BackupSection.ImeTheme,
        ),
        BackupDataEntry(
            id = "local_sticker_packs",
            domain = BackupDomain.File,
            path = "sticker_packs",
            disposition = BackupDisposition.Included,
            section = BackupSection.LocalStickerPacks,
        ),
        BackupDataEntry(
            id = "clipboard_text_items",
            domain = BackupDomain.Database,
            path = "clipboard_history",
            disposition = BackupDisposition.SensitiveExcluded,
            section = BackupSection.ClipboardTextItems,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "clipboard_files_metadata",
            domain = BackupDomain.Database,
            path = "clipboard_files",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "clipboard_media_files",
            domain = BackupDomain.File,
            path = "clipboard_history",
            disposition = BackupDisposition.SensitiveExcluded,
            section = BackupSection.ClipboardImageItems,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "clipboard_media_files_video",
            domain = BackupDomain.File,
            path = "clipboard_history",
            disposition = BackupDisposition.SensitiveExcluded,
            section = BackupSection.ClipboardVideoItems,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "clipboard_history_key",
            domain = BackupDomain.SharedPref,
            path = "clipboard_history_key.xml",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "personal_dictionary",
            domain = BackupDomain.Database,
            path = "floris_user_dictionary",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "personal_dictionary_key",
            domain = BackupDomain.SharedPref,
            path = "floris_user_dictionary_key.xml",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "tasker_auth",
            domain = BackupDomain.SharedPref,
            path = "swiftfloris_tasker_auth.xml",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "personal_bigrams",
            domain = BackupDomain.File,
            path = "personal_bigrams",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "personal_trigrams",
            domain = BackupDomain.File,
            path = "personal_trigrams",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "correction_outcome_priors",
            domain = BackupDomain.File,
            path = "correction_outcome_priors",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "typing_traces",
            domain = BackupDomain.File,
            path = "swiftkey_typing_traces.jsonl",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "typing_trace_flag",
            domain = BackupDomain.File,
            path = "swiftkey_trace.enabled",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "sync_identity",
            domain = BackupDomain.File,
            path = "sync",
            disposition = BackupDisposition.SensitiveExcluded,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "diagnostics",
            domain = BackupDomain.File,
            path = "diagnostics",
            disposition = BackupDisposition.Ephemeral,
            requiresAndroidExclude = true,
        ),
        BackupDataEntry(
            id = "snippets",
            domain = BackupDomain.File,
            path = "snippets",
            disposition = BackupDisposition.NotYetCovered,
        ),
        BackupDataEntry(
            id = "hardware_keyboard_layouts",
            domain = BackupDomain.File,
            path = "hardware_keyboard_layouts.json",
            disposition = BackupDisposition.NotYetCovered,
        ),
        BackupDataEntry(
            id = "custom_emoji_tags",
            domain = BackupDomain.File,
            path = "custom_emoji_tags.json",
            disposition = BackupDisposition.NotYetCovered,
        ),
        BackupDataEntry(
            id = "emoji_pin_groups",
            domain = BackupDomain.File,
            path = "emoji_pin_groups.json",
            disposition = BackupDisposition.NotYetCovered,
        ),
    )

    /** Sections the manual archive actually writes. */
    fun coveredSections(): Set<BackupSection> = entries.mapNotNull { it.section }.toSet()

    /** Stores a manual archive does not carry, so the UI can name them instead of implying it does. */
    fun notYetCovered(): List<BackupDataEntry> =
        entries.filter { it.disposition == BackupDisposition.NotYetCovered }

    /** Stores held back on purpose. */
    fun sensitiveExclusions(): List<BackupDataEntry> =
        entries.filter { it.disposition == BackupDisposition.SensitiveExcluded }

    /** Paths Android's cloud-backup and device-transfer rules must exclude explicitly. */
    fun requiredAndroidExcludes(): Set<Pair<String, String>> =
        entries.filter { it.requiresAndroidExclude }
            .map { it.domain.xmlName to it.path }
            .toSet()
}
