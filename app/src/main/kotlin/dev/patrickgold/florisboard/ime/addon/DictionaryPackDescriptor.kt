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

package dev.patrickgold.florisboard.ime.addon

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * ROADMAP §7 Next-10.3 — first-class dictionary-pack addon descriptor.
 *
 * Every `DICTIONARY_PACK` addon (an external APK declaring
 * `<action android:name="io.github.sysadmindoc.swiftfloris.action.REGISTER_DICTIONARY_PACK"/>`)
 * ships a JSON resource pointed to by the addon's
 * `dev.patrickgold.florisboard.addon.descriptor` meta-data value. That JSON
 * carries this exact shape:
 *
 * ```json
 * {
 *   "schema": 1,
 *   "language": "pl",
 *   "displayName": "Polish (2025 baseline)",
 *   "wordCount": 320000,
 *   "fldicAssetPath": "ime/dict/pl.fldic",
 *   "zipfAssetPath": "freq/pl.tsv",
 *   "source": "OpenSubtitles 2024 + Wiktionary",
 *   "license": "CC-BY-SA-4.0",
 *   "minSchemaCompat": 1
 * }
 * ```
 *
 * The IME loader (`DictionaryPackLoader`) reads + validates this descriptor
 * before mounting the addon's `assets/` directory into the
 * `LatinDictionaryStore.assetPathsForLanguage(language)` lookup, so the
 * addon-supplied `.fldic` and Zipf TSV take precedence over any bundled
 * asset for the same language code.
 *
 * The schema version is monotonic. Older IMEs encountering a descriptor
 * with `schema > SUPPORTED_SCHEMA` decline to enrol the pack, which keeps
 * forward-incompatible addon revisions from corrupting the lookup tables.
 */
@Serializable
data class DictionaryPackDescriptor(
    /** Schema version of this descriptor file. Current supported: [SUPPORTED_SCHEMA]. */
    val schema: Int,
    /** ISO 639-1 language code (e.g. `"pl"`, `"de"`, `"fr"`). Lowercased. */
    val language: String,
    /** Human-readable display name for Settings → Addons. */
    val displayName: String,
    /** Reported total word count (for the Settings UI; not load-bearing for routing). */
    val wordCount: Long,
    /** Path inside the addon APK's `assets/` to the `.fldic` dictionary. */
    val fldicAssetPath: String,
    /** Optional Zipf overlay path inside the addon APK's `assets/`. */
    val zipfAssetPath: String? = null,
    /** Origin / provenance line. */
    val source: String,
    /** SPDX license identifier (the addon enrolment also reads the
     *  `addon.license` AndroidManifest meta-data; this is the dataset
     *  license, which may differ from the APK's code license). */
    val license: String,
    /** Minimum supported descriptor schema. Older descriptors must keep
     *  `minSchemaCompat <= currently-supported schema` to enrol. */
    val minSchemaCompat: Int = 1,
) {
    init {
        require(schema >= 1) { "schema must be >= 1" }
        // Locale.ROOT: DictionaryPackCatalog normalizes lookups with lowercase(Locale.ROOT),
        // so this invariant must use the same locale. The default-locale lowercase() would,
        // on a Turkish device, fold an ASCII 'I' to the dotless 'ı' and wrongly reject a
        // descriptor that was validly ROOT-lowercased.
        require(language.isNotBlank() && language == language.lowercase(Locale.ROOT)) {
            "language must be a non-blank lowercase ISO 639-1 code"
        }
        require(wordCount >= 0) { "wordCount must be non-negative" }
        require(fldicAssetPath.isNotBlank()) { "fldicAssetPath must not be blank" }
        require(!fldicAssetPath.startsWith("/")) {
            "fldicAssetPath must be a relative path inside assets/"
        }
        require(!fldicAssetPath.hasParentTraversalSegment()) {
            "fldicAssetPath must not contain a '..' path segment"
        }
        // The loader picks its decoder off this extension: anything else is
        // handed to the JSON parser, which throws on a binary dictionary. The
        // field is named for the format it is supposed to carry, so requiring it
        // here keeps a mistyped descriptor a rejected enrolment rather than a
        // failure on the typing path.
        require(fldicAssetPath.endsWith(FLDIC_EXTENSION, ignoreCase = true)) {
            "fldicAssetPath must name a $FLDIC_EXTENSION file"
        }
        zipfAssetPath?.let {
            require(it.isNotBlank()) { "zipfAssetPath must not be blank when present" }
            require(!it.startsWith("/")) {
                "zipfAssetPath must be a relative path inside assets/"
            }
            require(!it.hasParentTraversalSegment()) {
                "zipfAssetPath must not contain a '..' path segment"
            }
        }
        require(minSchemaCompat >= 1) { "minSchemaCompat must be >= 1" }
    }

    /** True when this addon's schema is loadable by the current IME. */
    fun isCompatibleWithIme(): Boolean =
        minSchemaCompat <= SUPPORTED_SCHEMA && schema <= SUPPORTED_SCHEMA

    companion object {
        /** Highest descriptor schema version this IME understands. Bump when
         *  the on-disk layout grows a load-bearing field. */
        const val SUPPORTED_SCHEMA: Int = 1

        /**
         * True if any `/`- or `\`-delimited segment of this path is exactly `..`,
         * i.e. the path tries to escape its asset root. A literal `..foo` filename
         * is allowed; only a standalone parent-directory segment is rejected.
         */
    private const val FLDIC_EXTENSION = ".fldic"

    private fun String.hasParentTraversalSegment(): Boolean =
            split('/', '\\').any { it == ".." }

        private val JsonConfig = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        /**
         * Parse a descriptor blob into a [DictionaryPackDescriptor]. Returns
         * `null` on invalid JSON or any validation failure — the addon
         * enumerator logs a rejection reason but never crashes the IME.
         */
        fun parse(rawJson: String): DictionaryPackDescriptor? {
            if (rawJson.isBlank()) return null
            return runCatching { JsonConfig.decodeFromString(serializer(), rawJson) }
                .getOrNull()
        }
    }
}
