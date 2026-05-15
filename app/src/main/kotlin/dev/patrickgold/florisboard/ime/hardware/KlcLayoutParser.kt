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

package dev.patrickgold.florisboard.ime.hardware

/**
 * ROADMAP §7 Next-6.4 — Windows `.klc` (Microsoft Keyboard Layout Creator)
 * import parser, scaffold pass.
 *
 * The KLC format is a UTF-16 text file with these sections (only a subset
 * is honoured here; the rest is intentionally tolerated and skipped):
 *
 *  - `KBD`                     — internal identifier + display name
 *  - `COPYRIGHT`, `COMPANY`,
 *    `LOCALENAME`, `LOCALEID`,
 *    `VERSION`                 — metadata lines
 *  - `SHIFTSTATE`              — list of shift-state column ordinals
 *  - `LAYOUT`                  — `SC VK_NAME CAP NORMAL SHIFT CTRL ...`
 *  - `DEADKEY`, `KEYNAME`,
 *    `KEYNAME_EXT`, `LIGATURE` — extra context (skipped at the scaffold tier)
 *  - `ENDKBD`                  — terminator
 *
 * Each LAYOUT row's columns hold either `%` (no output), `-1` (dead key
 * placeholder), or a hex codepoint optionally followed by `@` to mark a
 * dead-key trigger. The parser produces a normalized [HardwareKeyboardLayout]
 * containing only the keys with at least one bound slot.
 *
 * The wiring from this descriptor into Android's [`InputManager`] /
 * `KeyEvent.getDeviceId(...)` routing lands in Next-6.4a; this commit pins
 * the parser so the import path and its tests can ship independently from
 * the runtime mapper.
 */
object KlcLayoutParser {

    /**
     * Parse the contents of a Windows KLC file into a [HardwareKeyboardLayout].
     * Returns [HardwareKeyboardLayout.Empty] when the input has no resolvable
     * LAYOUT rows.
     */
    fun parse(klcText: String): HardwareKeyboardLayout {
        var name = ""
        var locale = ""
        val scancodeMap = LinkedHashMap<Int, HardwareKeyEntry>()
        var section: Section = Section.UNKNOWN

        for (rawLine in klcText.lineSequence()) {
            val line = rawLine.trim().trimStart('\uFEFF')
            if (line.isEmpty() || line.startsWith("//") || line.startsWith(";")) continue

            // Section headers are tokens at column 0; case-insensitive match.
            when (val token = line.split(Regex("\\s+")).first().uppercase()) {
                "KBD" -> {
                    section = Section.KBD
                    val rest = line.substringAfter(token).trim()
                    // KBD "<internal>" "<display>"   ← KLC quotes the display name
                    val quoted = Regex("\"([^\"]*)\"").findAll(rest).map { it.groupValues[1] }.toList()
                    if (quoted.isNotEmpty()) {
                        name = quoted.last().ifBlank { rest.substringAfter(' ').trim() }
                    } else {
                        name = rest
                    }
                }
                "LOCALENAME" -> {
                    section = Section.METADATA
                    locale = line.substringAfter(token).trim().trim('"')
                }
                "COPYRIGHT", "COMPANY", "LOCALEID", "VERSION" -> {
                    section = Section.METADATA
                }
                "SHIFTSTATE" -> section = Section.SHIFTSTATE
                "LAYOUT" -> section = Section.LAYOUT
                "DEADKEY", "KEYNAME", "KEYNAME_EXT", "LIGATURE" -> section = Section.SKIPPED
                "ENDKBD" -> break
                else -> {
                    if (section == Section.LAYOUT) {
                        parseLayoutRow(line)?.let { (sc, entry) -> scancodeMap[sc] = entry }
                    }
                }
            }
        }
        if (scancodeMap.isEmpty()) return HardwareKeyboardLayout.Empty
        return HardwareKeyboardLayout(
            name = name,
            locale = locale,
            scancodeMap = scancodeMap.toMap(),
        )
    }

    /**
     * Parse a single LAYOUT row of the form
     * `SC VK_NAME CAP NORMAL SHIFT [CTRL [ALTGR [SHIFT+ALTGR ...]]]`. Skips
     * malformed rows by returning null so the parser stays tolerant of
     * documentation lines and KLC quirks.
     */
    internal fun parseLayoutRow(line: String): Pair<Int, HardwareKeyEntry>? {
        val parts = line.split(Regex("[\\s\\t]+")).filter { it.isNotEmpty() }
        if (parts.size < 3) return null
        val scancode = parts[0].toIntOrNull(16) ?: return null
        val virtualKeyName = parts[1]
        val capsLock = parts[2] == "1" || parts[2].equals("SGCap", ignoreCase = true)
        val columns = parts.drop(3)
        if (columns.isEmpty()) return null

        fun col(index: Int): Pair<Int?, Boolean> {
            val raw = columns.getOrNull(index) ?: return null to false
            if (raw == "%" || raw == "-1") return null to false
            val deadKey = raw.endsWith("@")
            val hex = if (deadKey) raw.dropLast(1) else raw
            val cp = hex.toIntOrNull(16) ?: return null to false
            return cp to deadKey
        }

        val (normalCp, normalDead) = col(0)
        val (shiftCp, shiftDead) = col(1)
        val (ctrlCp, _) = col(2)
        val (altGrCp, _) = col(3)
        val (shiftAltGrCp, _) = col(4)

        if (normalCp == null && shiftCp == null && ctrlCp == null &&
            altGrCp == null && shiftAltGrCp == null
        ) {
            return null
        }
        val deadTrigger = when {
            normalDead -> normalCp
            shiftDead -> shiftCp
            else -> null
        }
        return scancode to HardwareKeyEntry(
            virtualKeyName = virtualKeyName,
            normal = normalCp,
            shift = shiftCp,
            ctrl = ctrlCp,
            altGr = altGrCp,
            shiftAltGr = shiftAltGrCp,
            capsLock = capsLock,
            deadKeyTrigger = deadTrigger,
        )
    }

    private enum class Section { UNKNOWN, KBD, METADATA, SHIFTSTATE, LAYOUT, SKIPPED }
}
