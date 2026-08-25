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

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
            DictionaryImportFormat.XML -> parseGboardXml(buffered.readUtf8TextLimited("XML dictionary import"))
            DictionaryImportFormat.CSV -> parseCsv(buffered.readUtf8TextLimited("CSV dictionary import"))
            DictionaryImportFormat.JSON -> parseSwiftKeyJson(buffered.readUtf8TextLimited("SwiftKey JSON dictionary import"))
            DictionaryImportFormat.FLORIS -> parseFlorisCombinedList(
                buffered.readUtf8TextLimited("SwiftFloris personal dictionary export"),
            )
            DictionaryImportFormat.UNKNOWN -> throw DictionaryImportException(
                "Unrecognised dictionary format. Supported: Gboard PersonalDictionary.zip, " +
                    "FlorisBoard/HeliBoard .flbackup, SwiftKey swiftkey-cloud.json, " +
                    "SwiftFloris combined-list export, or word,frequency,shortcut,locale CSV.",
            )
        }
    }

    internal fun detectFormat(sniff: ByteArray): DictionaryImportFormat {
        if (sniff.size >= 4 && sniff[0] == 0x50.toByte() && sniff[1] == 0x4B.toByte()) {
            // PK zip magic — Gboard zip OR FlorisBoard .flbackup zip.
            return DictionaryImportFormat.ZIP
        }
        // Some exporters (Notepad, Excel) emit a UTF-8 BOM ahead of the
        // payload. `trimStart()` does not consume the BOM character, so
        // strip it explicitly before pattern-matching so BOMed files
        // route to the correct parser.
        val asText = String(sniff, Charsets.UTF_8).removePrefix(UTF8_BOM).trimStart()
        if (asText.startsWith("<?xml") || asText.startsWith("<userdictionary")) {
            return DictionaryImportFormat.XML
        }
        // ROADMAP §6 N16.2 — SwiftKey `swiftkey-cloud.json` export starts with
        // either a top-level `{` (envelope object) or `[` (bare entry array).
        // Detect before the CSV branch because a JSON array of strings could
        // technically contain a comma.
        if (asText.startsWith("{") || asText.startsWith("[")) {
            return DictionaryImportFormat.JSON
        }
        if (looksLikeFlorisCombinedList(asText)) {
            return DictionaryImportFormat.FLORIS
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

    internal fun parseFlorisCombinedList(text: String): List<PersonalDictionaryEntry> {
        return UserDictionaryCombinedListCodec.decode(text)
    }

    internal fun parseZip(stream: InputStream): List<PersonalDictionaryEntry> {
        val found = mutableListOf<PersonalDictionaryEntry>()
        val cumulativeBytes = LongHolder()
        var sawCandidateEntry = false
        ZipInputStream(stream).use { zis ->
            var entryCount = 0
            while (true) {
                val entry = zis.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw DictionaryImportException(
                        "Zip archive contains too many files; expected a small dictionary export.",
                    )
                }
                val name = entry.name.lowercase()
                // Skip directories and Mac-style hidden files.
                if (entry.isDirectory) continue
                if (name.startsWith("__macosx/")) continue
                when {
                    name.endsWith(".xml") -> {
                        sawCandidateEntry = true
                        found += parseGboardXml(
                            zis.readUtf8TextLimited("Zip entry ${entry.name}", cumulativeBytes),
                        )
                        checkEntryLimit(found.size)
                    }
                    name.endsWith(".csv") -> {
                        sawCandidateEntry = true
                        found += parseCsv(
                            zis.readUtf8TextLimited("Zip entry ${entry.name}", cumulativeBytes),
                        )
                        checkEntryLimit(found.size)
                    }
                    name.endsWith(".json") -> {
                        // Two cases:
                        //   1. SwiftKey `swiftkey-cloud.json` — the actual
                        //      dictionary export. Parse it.
                        //   2. FlorisBoard backup manifest — descriptive only,
                        //      the entries live in a CSV/XML sibling. The
                        //      parser tolerates that shape by returning an
                        //      empty list (no `predictions` / `shortcuts` /
                        //      `words` keys present).
                        sawCandidateEntry = true
                        found += parseSwiftKeyJson(
                            zis.readUtf8TextLimited("Zip entry ${entry.name}", cumulativeBytes),
                        )
                        checkEntryLimit(found.size)
                    }
                    name.endsWith(".db") || name.endsWith(".sqlite") -> {
                        // Some FlorisBoard/HeliBoard backups carry the personal
                        // dictionary as a raw SQLite snapshot rather than the
                        // CSV/JSON payload this importer reads. There is no
                        // in-app route that opens such a snapshot, so the
                        // message must not invent one: tell the user to
                        // re-export in a supported format instead.
                        throw DictionaryImportException(
                            "This archive stores its dictionary as a SQLite snapshot (${entry.name}), " +
                                "which SwiftFloris cannot read. In the keyboard you are migrating from, " +
                                "export the personal dictionary as CSV or JSON and import that file instead. " +
                                "Archives whose dictionary is stored as CSV or JSON import normally.",
                        )
                    }
                }
            }
        }
        if (found.isEmpty()) {
            // Distinguish "we saw an XML/CSV/JSON candidate but it carried no
            // entries" (e.g. a FlorisBoard manifest sitting alongside a real
            // payload that turned out to be empty) from "the archive had no
            // candidate files at all" so the caller can surface the right
            // recovery copy.
            throw DictionaryImportException(
                if (sawCandidateEntry) {
                    "Zip archive contains a dictionary file but no entries were recognised."
                } else {
                    "Zip archive contains no recognisable dictionary file " +
                        "(looking for *.xml, *.csv, or *.json)."
                },
            )
        }
        return found
    }

    /** Mutable long holder used to track cumulative bytes read across the
     *  per-entry stream reads inside a single [parseZip] pass. */
    private class LongHolder {
        var value: Long = 0L
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
            checkEntryLimit(result.size)
        }
        return result
    }

    internal fun parseCsv(csv: String): List<PersonalDictionaryEntry> {
        // Format: `word,frequency,shortcut,locale`. Empty shortcut / locale
        // → null. The first line is a header only when its second column is
        // literally `frequency` (case-insensitive); we previously treated
        // any first row whose first column started with "word" as a header,
        // which dropped the legitimate entry `word,5,…` when a user
        // actually wanted to learn the dictionary word "word".
        val rows = csv.lineSequence()
            .map { it.removePrefix(UTF8_BOM).trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (rows.isEmpty()) return emptyList()
        val result = mutableListOf<PersonalDictionaryEntry>()
        val firstCells = splitCsvLine(rows[0]).map { it.trim() }
        val isHeader = firstCells.size >= 2 &&
            firstCells[0].equals("word", ignoreCase = true) &&
            firstCells[1].equals("frequency", ignoreCase = true)
        val startIndex = if (isHeader) 1 else 0
        for (i in startIndex..rows.lastIndex) {
            val cells = splitCsvLine(rows[i]).map { it.trim() }
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
            checkEntryLimit(result.size)
        }
        return result
    }

    /**
     * ROADMAP §6 N16.2 — SwiftKey `swiftkey-cloud.json` export parser.
     *
     * SwiftKey's cloud export is JSON. The exact wire shape was lightly
     * documented and not officially specified, and the upstream
     * `data.swiftkey.com` endpoint retires 2026-05-31 [SK-RETIRE], so this
     * parser is intentionally tolerant about the surrounding envelope and
     * keys: anywhere it finds an object with a `word`-class key (`word` /
     * `text` / `string`) it lifts that entry into a
     * [PersonalDictionaryEntry]. The frequency comes from any of
     * `frequency` / `count` / `rank`; locale from any of `locale` /
     * `language` / `lang`; shortcut from `shortcut` / `expansion`. Missing
     * fields collapse to the same defaults the Gboard XML / CSV paths use
     * (frequency=128, locale=null, shortcut=null).
     *
     * Envelope shapes covered:
     *  * `{ "predictions": [...], "shortcuts": [...] }` — recurse both keys.
     *  * `{ "user_data": { "predictions": [...] } }` — recurse the wrapped key.
     *  * `{ "words": [...] }` — single key bag.
     *  * `[ { "word": "..." }, ... ]` — bare array.
     *
     * Any other shape returns an empty list instead of throwing, so a
     * FlorisBoard backup manifest JSON sitting in the same zip doesn't
     * abort the whole import.
     */
    internal fun parseSwiftKeyJson(json: String): List<PersonalDictionaryEntry> {
        val parsed = runCatching { Json.parseToJsonElement(json) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<PersonalDictionaryEntry>()
        collectSwiftKeyEntries(parsed, out, depth = 0)
        return out
    }

    private fun collectSwiftKeyEntries(
        element: JsonElement,
        sink: MutableList<PersonalDictionaryEntry>,
        depth: Int,
    ) {
        if (depth > MAX_JSON_DEPTH) {
            throw DictionaryImportException("SwiftKey JSON dictionary is nested too deeply to import safely.")
        }
        when (element) {
            is JsonArray -> {
                for (child in element) {
                    collectSwiftKeyEntries(child, sink, depth + 1)
                }
            }
            is JsonObject -> {
                val word = swiftKeyWordField(element)
                if (word != null) {
                    sink += PersonalDictionaryEntry(
                        word = word,
                        frequency = swiftKeyFrequencyField(element),
                        shortcut = swiftKeyShortcutField(element),
                        locale = swiftKeyLocaleField(element),
                    )
                    checkEntryLimit(sink.size)
                    return
                }
                // Not an entry itself — recurse into nested arrays and objects so
                // we find entries wrapped in arbitrary envelope keys
                // (`user_data` / `predictions` / `shortcuts` / `words` / ...).
                for ((_, value) in element) {
                    if (value is JsonArray || value is JsonObject) {
                        collectSwiftKeyEntries(value, sink, depth + 1)
                    }
                }
            }
            else -> { /* primitive at the top level: ignore */ }
        }
    }

    private fun swiftKeyWordField(obj: JsonObject): String? {
        return swiftKeyStringField(obj, listOf("word", "text", "string"))?.takeIf { it.isNotBlank() }
    }

    private fun swiftKeyShortcutField(obj: JsonObject): String? {
        return swiftKeyStringField(obj, listOf("shortcut", "expansion"))?.takeIf { it.isNotBlank() }
    }

    private fun swiftKeyLocaleField(obj: JsonObject): String? {
        return swiftKeyStringField(obj, listOf("locale", "language", "lang"))?.takeIf { it.isNotBlank() }
    }

    private fun swiftKeyFrequencyField(obj: JsonObject): Int {
        for (key in listOf("frequency", "count", "rank")) {
            val v = obj[key] ?: continue
            val asInt = (v as? JsonPrimitive)?.content?.toIntOrNull() ?: continue
            return asInt.coerceIn(0, 255)
        }
        return 128
    }

    private fun swiftKeyStringField(obj: JsonObject, keys: List<String>): String? {
        for (key in keys) {
            val v = obj[key] ?: continue
            val s = (v as? JsonPrimitive)?.content ?: continue
            return s
        }
        return null
    }

    private fun looksLikeFlorisCombinedList(text: String): Boolean {
        val lines = text.lineSequence()
            .map { it.trimStart() }
            .filter { it.isNotEmpty() }
            .take(2)
            .toList()
        val first = lines.firstOrNull() ?: return false
        return first.startsWith("dictionary=", ignoreCase = true) ||
            lines.any { line ->
                line.startsWith("w=", ignoreCase = true) ||
                    line.startsWith("word=", ignoreCase = true)
            }
    }

    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                }
                c == ',' && !inQuotes -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells
    }

    private fun InputStream.readUtf8TextLimited(
        label: String,
        cumulative: LongHolder = LongHolder(),
    ): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var entryBytes = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            entryBytes += read
            cumulative.value += read
            if (entryBytes > MAX_IMPORT_FILE_BYTES) {
                throw DictionaryImportException(
                    "$label exceeds the ${MAX_IMPORT_FILE_BYTES / (1024 * 1024)} MiB safety limit.",
                    isSafetyLimit = true,
                )
            }
            if (cumulative.value > MAX_IMPORT_FILE_BYTES) {
                // Cumulative cap across the whole archive — without this a
                // 256-entry zip of 16 MiB-each entries could push 4 GiB
                // through the importer before the per-entry cap fired on
                // any single file.
                throw DictionaryImportException(
                    "Dictionary archive exceeds the ${MAX_IMPORT_FILE_BYTES / (1024 * 1024)} MiB total safety limit.",
                    isSafetyLimit = true,
                )
            }
            out.write(buffer, 0, read)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun checkEntryLimit(size: Int) {
        if (size > MAX_IMPORTED_ENTRIES) {
            throw DictionaryImportException(
                "Dictionary import contains more than $MAX_IMPORTED_ENTRIES entries; split the file and retry.",
                isSafetyLimit = true,
            )
        }
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
            // Note: when no closing quote exists, `i == raw.length` here and
            // we still grab the remaining substring as a best-effort value.
            val value = raw.substring(valueStart, i)
            if (i < raw.length) i++  // skip closing quote
            result[name] = unescapeXml(value)
        }
        return result
    }

    private fun unescapeXml(raw: String): String {
        if ('&' !in raw) return raw
        // Decimal / hex numeric character references (`&#65;`, `&#x41;`)
        // are part of the XML 1.0 grammar and Android's UserDictionary
        // exporter is known to emit them for non-ASCII code points. Decode
        // them ahead of the named-entity substitutions so they pass
        // through without being garbled.
        val numeric = NUMERIC_ENTITY_REGEX.replace(raw) { match ->
            val hexDigits = match.groupValues[1]
            val decDigits = match.groupValues[2]
            val codePoint = if (hexDigits.isNotEmpty()) {
                hexDigits.toIntOrNull(16)
            } else {
                decDigits.toIntOrNull(10)
            }
            if (codePoint == null || codePoint !in 0..0x10FFFF) {
                match.value
            } else {
                try {
                    String(Character.toChars(codePoint))
                } catch (_: IllegalArgumentException) {
                    match.value
                }
            }
        }
        return numeric
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    companion object {
        private const val MAX_SNIFF_BYTES = 1024
        internal const val MAX_IMPORT_FILE_BYTES = 16L * 1024L * 1024L
        internal const val MAX_IMPORTED_ENTRIES = 50_000
        private const val MAX_JSON_DEPTH = 64
        private const val MAX_ZIP_ENTRIES = 256
        // Lazy up to the self-closing "/>", not "any run containing no slash".
        // A personal dictionary is exactly where "24/7", "km/h" and "n/a" live,
        // and excluding "/" from the attribute run meant those entries never
        // matched and were dropped without a word of warning.
        private val ENTRY_REGEX = Regex(
            pattern = "<entry\\s+([^>]*?)/>",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        private val NUMERIC_ENTITY_REGEX = Regex("&#(?:[xX]([0-9a-fA-F]+)|([0-9]+));")
        // U+FEFF zero-width no-break space (UTF-8 BOM after decoding). Spelled
        // out as an escape sequence so the source file itself does not need
        // to carry a BOM-bearing literal.
        private const val UTF8_BOM = "\uFEFF"
    }
}

enum class DictionaryImportFormat {
    ZIP,
    XML,
    CSV,
    /** ROADMAP §6 N16.2 — SwiftKey `swiftkey-cloud.json` export. */
    JSON,
    /** SwiftFloris / legacy Floris semicolon key-value personal dictionary export. */
    FLORIS,
    UNKNOWN,
}

data class PersonalDictionaryEntry(
    val word: String,
    val frequency: Int,
    val shortcut: String?,
    val locale: String?,
)

class DictionaryImportException(
    message: String,
    val isSafetyLimit: Boolean = false,
) : RuntimeException(message)
