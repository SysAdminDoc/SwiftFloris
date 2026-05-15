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

package dev.patrickgold.florisboard.ime.snippet

/**
 * ROADMAP §7 L11 — Espanso (https://espanso.org) snippet config importer.
 *
 * Espanso ships text-expansion snippets in YAML files of the form:
 *
 * ```yaml
 * matches:
 *   - trigger: ":bug"
 *     replace: "I'm sorry"
 *   - trigger: ":sig"
 *     replace: "Best regards,\nMatt"
 *   - trigger: ":greet"
 *     replace: |
 *       Hello there,
 *       Hope you're well.
 * ```
 *
 * SwiftFloris' personal-snippet engine reuses the same `trigger` → `replace`
 * shape so an Espanso user can drop their existing `match/base.yml` into the
 * SwiftFloris importer with no manual conversion. This parser is intentionally
 * a minimal YAML reader — it handles the `matches:` list, scalar / quoted /
 * block (`|`, `>`) string forms, and full-line comments. Anchors, aliases,
 * advanced tags, regex triggers, image/clipboard/form matches, and the
 * Espanso-specific `vars:` interpolation are **not** parsed at this tier;
 * a follow-up L11a slice grows the parser when those features become
 * useful. Snakeyaml would handle the full spec but pulls a 600KB+ runtime;
 * for a personal-snippet importer that's wasteful.
 */
object EspansoMatchParser {

    /**
     * Parse [yaml] into a list of [EspansoMatch] entries. Returns an empty
     * list when the input has no `matches:` section or every entry is
     * malformed. Never throws — invalid lines are silently skipped so a
     * mistyped trigger doesn't blow up the whole import.
     */
    fun parse(yaml: String): List<EspansoMatch> {
        val lines = yaml.lines()
        var inMatchesBlock = false
        val results = mutableListOf<EspansoMatch>()

        // Mutable state for the current match in progress.
        var trigger: String? = null
        var replace: String = ""
        var blockMode: BlockStyle = BlockStyle.NONE
        var blockBodyIndent: Int = -1
        val blockBuffer = StringBuilder()

        fun emit() {
            val finalReplace = if (blockMode != BlockStyle.NONE) {
                val raw = blockBuffer.toString()
                if (blockMode == BlockStyle.LITERAL) raw.trimEnd('\n') else raw.trim()
            } else {
                replace
            }
            val tg = trigger
            if (tg != null && tg.isNotBlank()) {
                results += EspansoMatch(tg, finalReplace)
            }
            trigger = null
            replace = ""
            blockMode = BlockStyle.NONE
            blockBodyIndent = -1
            blockBuffer.clear()
        }

        for (rawLine in lines) {
            // Inside an in-progress block scalar, indent-based termination.
            if (blockMode != BlockStyle.NONE) {
                val indent = rawLine.indexOfFirst { !it.isWhitespace() }
                val isBlankLine = rawLine.isBlank()
                if (isBlankLine) {
                    blockBuffer.append('\n')
                    continue
                }
                if (blockBodyIndent == -1) {
                    blockBodyIndent = indent
                }
                if (indent < blockBodyIndent && !isBlankLine) {
                    // End of block. Fall through to normal directive parsing
                    // for this same line.
                } else {
                    val body = rawLine.substring(minOf(blockBodyIndent, rawLine.length))
                    blockBuffer.append(body)
                    blockBuffer.append(if (blockMode == BlockStyle.LITERAL) "\n" else " ")
                    continue
                }
            }

            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            // Skip standalone full-line comments outside any block scalar.
            if (trimmed.startsWith("#")) continue

            if (trimmed == "matches:") {
                inMatchesBlock = true
                continue
            }
            if (!inMatchesBlock) continue

            if (trimmed.startsWith("- ")) {
                // New list item. Commit whatever the previous match held.
                emit()
                val first = trimmed.removePrefix("- ").trim()
                applyKv(first) { key, value ->
                    when (key) {
                        "trigger" -> trigger = value
                        "replace" -> when (value) {
                            "|" -> {
                                blockMode = BlockStyle.LITERAL
                                blockBodyIndent = -1
                            }
                            ">" -> {
                                blockMode = BlockStyle.FOLDED
                                blockBodyIndent = -1
                            }
                            else -> replace = value
                        }
                    }
                }
            } else {
                applyKv(trimmed) { key, value ->
                    when (key) {
                        "trigger" -> trigger = value
                        "replace" -> when (value) {
                            "|" -> {
                                blockMode = BlockStyle.LITERAL
                                blockBodyIndent = -1
                            }
                            ">" -> {
                                blockMode = BlockStyle.FOLDED
                                blockBodyIndent = -1
                            }
                            else -> replace = value
                        }
                    }
                }
            }
        }
        emit()
        return results
    }

    private inline fun applyKv(line: String, sink: (key: String, value: String) -> Unit) {
        val sep = line.indexOf(':')
        if (sep < 0) return
        val key = line.substring(0, sep).trim()
        val rawValue = line.substring(sep + 1).trim()
        sink(key, stripQuotes(rawValue))
    }

    private fun stripQuotes(value: String): String {
        val unwrapped = if (
            (value.startsWith('"') && value.endsWith('"') && value.length >= 2) ||
            (value.startsWith('\'') && value.endsWith('\'') && value.length >= 2)
        ) {
            value.substring(1, value.length - 1)
        } else {
            value
        }
        return unwrapped.replace("\\n", "\n").replace("\\t", "\t")
    }

    private enum class BlockStyle { NONE, LITERAL, FOLDED }
}

/**
 * One parsed Espanso match: a `trigger` → `replace` mapping.
 */
data class EspansoMatch(
    val trigger: String,
    val replace: String,
) {
    init {
        require(trigger.isNotBlank()) { "trigger must not be blank" }
    }
}
