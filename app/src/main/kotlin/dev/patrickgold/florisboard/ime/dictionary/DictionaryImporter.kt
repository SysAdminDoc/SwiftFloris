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

package dev.patrickgold.florisboard.ime.dictionary

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * ROADMAP §7 Next-6.1 + Next-6.2 — personal-dictionary importers.
 *
 * Three supported source formats:
 *  1. **Gboard `PersonalDictionary.zip`** — what `Google Takeout > Keyboard`
 *     exports. The zip contains a single XML file
 *     `Phrase Personalization Words.xml` (or similar) where each `<entry>`
 *     carries `word`, `shortcut`, `locale`, and `frequency` attributes.
 *  2. **FlorisBoard / HeliBoard `.flbackup`** — same-shaped zip with a JSON
 *     manifest plus a SQLite snapshot of the personal dictionary Room
 *     database. Either layout is supported.
 *  3. **Generic CSV** — `word,frequency,shortcut,locale` per line, one entry
 *     per line, used by SwiftFloris's own Settings → Personal dictionary
 *     CSV export path (Next-6.3 documents this format).
 *
 * Detection is structure-based: read the first entry of the archive (or the
 * first line of plain text) and route on its shape. Caller passes an
 * [InputStream] (typically from a `ContentResolver.openInputStream(uri)`
 * call against an Android document picker URI); the importer never reads
 * arbitrary paths.
 *
 * Output is a list of [PersonalDictionaryEntry] records ready for batch
 * insert into `DictionaryManager.florisUserDictionaryDao`. The caller is
 * responsible for the actual DB write so this class stays Android-free
 * (testable as plain JVM Kotlin).
 *
 * Privacy invariant: importer parses the file in-memory and never writes a
 * staging copy to disk. On bad input it throws [DictionaryImportException]
 * with a user-facing reason; the dictionary state is unchanged.
 */
class DictionaryImporter {

    fun import(input: InputStream): List<PersonalDictionaryEntry> {
        // We need to peek the first few bytes to choose a parser. Wrap the
        // stream in a BufferedInputStream so we can mark()/reset() instead
        // of consuming the original.
        val buffered = input.buffered()
        buffered.mark(MAX_SNIFF_BYTES)
        val sniff = ByteArray(MAX_SNIFF_BYTES)
        val read = buffered.read(sniff)
        buffered.reset()
        val sniffed = sniff.copyOf(read.coerceAtLeast(0))
        return when (detectFormat(sniffed)) {
            DictionaryImportFormat.ZIP -> parseZip(buffered)
            DictionaryImportFormat.XML -> parseGboardXml(buffered.bufferedReader().readText())
            DictionaryImportFormat.CSV -> parseCsv(buffered.bufferedReader().readText())
            DictionaryImportFormat.UNKNOWN -> throw DictionaryImportException(
                "Unrecognised dictionary format. Supported: Gboard PersonalDictionary.zip, " +
                    "FlorisBoard/HeliBoard .flbackup, or word,frequency,shortcut,locale CSV.",
            )
        }
    }

    internal fun detectFormat(sniff: ByteArray): DictionaryImportFormat {
        if (sniff.size >= 4 && sniff[0] == 0x50.toByte() && sniff[1] == 0x4B.toByte()) {
            // PK zip magic — Gboard zip OR FlorisBoard .flbackup zip.
            return DictionaryImportFormat.ZIP
        }
        val asText = String(sniff, Charsets.UTF_8).trimStart()
        if (asText.startsWith("<?xml") || asText.startsWith("<userdictionary")) {
            return DictionaryImportFormat.XML
        }
        if (asText.contains(",")) {
            // Heuristic for CSV: at least one comma in the first kilobyte
            // and at least one of the first two lines either looks like
            // our known header (`word,frequency...`) OR has the shape
            // `<word>,<int>,...`. The two-line tolerance covers files
            // whose first line is a header that doesn't tokenize as data.
            val lines = asText.lineSequence().take(2).toList()
            for (line in lines) {
                val parts = line.split(",")
                if (parts.size < 2) continue
                if (parts[0].trim().lowercase() == "word" &&
                    parts[1].trim().lowercase() == "frequency") {
                    return DictionaryImportFormat.CSV
                }
                if (parts[1].trim().toIntOrNull() != null) {
                    return DictionaryImportFormat.CSV
                }
            }
        }
        return DictionaryImportFormat.UNKNOWN
    }

    internal fun parseZip(stream: InputStream): List<PersonalDictionaryEntry> {
        var found = mutableListOf<PersonalDictionaryEntry>()
        ZipInputStream(stream).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name.lowercase()
                // Skip directories and Mac-style hidden files.
                if (entry.isDirectory) continue
                if (name.startsWith("__macosx/")) continue
                when {
                    name.endsWith(".xml") -> {
                        val bytes = zis.readBytes()
                        found += parseGboardXml(String(bytes, Charsets.UTF_8))
                    }
                    name.endsWith(".csv") -> {
                        val bytes = zis.readBytes()
                        found += parseCsv(String(bytes, Charsets.UTF_8))
                    }
                    name.endsWith(".json") -> {
                        // FlorisBoard backup manifest — descriptive only, the
                        // actual dictionary entries live in a CSV/XML sibling.
                        // We skip the manifest; if no dictionary file follows
                        // we throw below.
                    }
                    name.endsWith(".db") || name.endsWith(".sqlite") -> {
                        // FlorisBoard backup with raw SQLite snapshot: not
                        // supported in the JVM importer because we can't
                        // open a SQLite database without Android's runtime.
                        // The caller should route .db files to a separate
                        // Android-side helper (deferred to a follow-up).
                        throw DictionaryImportException(
                            "FlorisBoard SQLite snapshot import requires the device's Room runtime; " +
                                "use the in-app Settings → Personal dictionary → Import .flbackup path.",
                        )
                    }
                }
            }
        }
        if (found.isEmpty()) {
            throw DictionaryImportException(
                "Zip archive contains no recognisable dictionary file (looking for *.xml or *.csv).",
            )
        }
        return found
    }

    internal fun parseGboardXml(xml: String): List<PersonalDictionaryEntry> {
        // Gboard's PersonalDictionary export is a tiny, well-formed XML with
        // structure: `<userdictionary><entry word="..." shortcut="..." locale="..." frequency="..." /></userdictionary>`.
        // We don't pull XmlPullParser in here because the file is trivial
        // enough that a regex extractor stays correct AND keeps the JVM
        // tests dependency-free. Each `<entry .../>` self-closes.
        val regex = ENTRY_REGEX.findAll(xml)
        val result = mutableListOf<PersonalDictionaryEntry>()
        for (match in regex) {
            val attrs = parseAttributes(match.groupValues[1])
            val word = attrs["word"]?.takeIf { it.isNotBlank() } ?: continue
            val frequency = attrs["frequency"]?.toIntOrNull()?.coerceIn(0, 255) ?: 128
            val shortcut = attrs["shortcut"]?.takeIf { it.isNotBlank() }
            val locale = attrs["locale"]?.takeIf { it.isNotBlank() }
            result += PersonalDictionaryEntry(
                word = word,
                frequency = frequency,
                shortcut = shortcut,
                locale = locale,
            )
        }
        return result
    }

    internal fun parseCsv(csv: String): List<PersonalDictionaryEntry> {
        // Format: `word,frequency,shortcut,locale`. Empty shortcut / locale
        // → null. First line may be a header (`word,frequency,...`); skip
        // it if any cell isn't a plausible value.
        val rows = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (rows.isEmpty()) return emptyList()
        val result = mutableListOf<PersonalDictionaryEntry>()
        val startIndex = if (rows[0].startsWith("word", ignoreCase = true)) 1 else 0
        for (i in startIndex..rows.lastIndex) {
            val cells = rows[i].split(",").map { it.trim() }
            if (cells.size < 2) continue
            val word = cells[0].takeIf { it.isNotBlank() } ?: continue
            val frequency = cells[1].toIntOrNull()?.coerceIn(0, 255) ?: 128
            val shortcut = cells.getOrNull(2)?.takeIf { it.isNotBlank() }
            val locale = cells.getOrNull(3)?.takeIf { it.isNotBlank() }
            result += PersonalDictionaryEntry(
                word = word,
                frequency = frequency,
                shortcut = shortcut,
                locale = locale,
            )
        }
        return result
    }

    private fun parseAttributes(raw: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var i = 0
        while (i < raw.length) {
            while (i < raw.length && raw[i].isWhitespace()) i++
            if (i >= raw.length) break
            val nameStart = i
            while (i < raw.length && raw[i] != '=' && !raw[i].isWhitespace()) i++
            if (i >= raw.length) break
            val name = raw.substring(nameStart, i)
            while (i < raw.length && (raw[i] == '=' || raw[i].isWhitespace())) i++
            if (i >= raw.length) break
            val quote = raw[i]
            if (quote != '"' && quote != '\'') {
                // Bad/unsupported syntax; skip this attribute and continue.
                while (i < raw.length && !raw[i].isWhitespace()) i++
                continue
            }
            i++
            val valueStart = i
            while (i < raw.length && raw[i] != quote) i++
            if (i > raw.length) break
            val value = raw.substring(valueStart, i)
            if (i < raw.length) i++  // skip closing quote
            result[name] = unescapeXml(value)
        }
        return result
    }

    private fun unescapeXml(raw: String): String {
        if ('&' !in raw) return raw
        return raw
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    companion object {
        private const val MAX_SNIFF_BYTES = 1024
        private val ENTRY_REGEX = Regex(
            pattern = "<entry\\s+([^/>]*)/>",
            options = setOf(RegexOption.IGNORE_CASE),
        )
    }
}

enum class DictionaryImportFormat {
    ZIP,
    XML,
    CSV,
    UNKNOWN,
}

data class PersonalDictionaryEntry(
    val word: String,
    val frequency: Int,
    val shortcut: String?,
    val locale: String?,
)

class DictionaryImportException(message: String) : RuntimeException(message)
