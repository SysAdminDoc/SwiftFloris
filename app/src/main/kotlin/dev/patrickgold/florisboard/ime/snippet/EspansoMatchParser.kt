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
 *
 * ROADMAP §7 L11a — extended-match shapes the parser doesn't surface
 * yet are captured here as nullable fields so a future parser
 * extension can populate them without breaking the existing
 * triggerstring→replacement contract:
 *  - [regex] — when non-null, this match is regex-driven and [trigger]
 *    is empty. Espanso's `regex: "p[ae]rty"` shape.
 *  - [vars] — values bound at expansion time (e.g. `{{date}}` →
 *    formatted today). Engine integration lives in [EspansoVarsExpander].
 *  - [isWordSensitive] — whether the match requires a word boundary.
 *  - [passive] — whether the match should not auto-expand and instead
 *    show up only in the smartbar's expander list.
 */
data class EspansoMatch(
    val trigger: String,
    val replace: String,
    val regex: String? = null,
    val vars: List<EspansoVar> = emptyList(),
    val isWordSensitive: Boolean = false,
    val passive: Boolean = false,
) {
    init {
        // Either a literal trigger or a regex must be set.
        require(trigger.isNotBlank() || !regex.isNullOrBlank()) {
            "either trigger or regex must be set on an EspansoMatch"
        }
    }
}

/**
 * One named variable in an Espanso match. Mirrors Espanso's
 * `vars: - name: date type: date params: format: "%Y-%m-%d"`
 * shape.
 */
data class EspansoVar(
    val name: String,
    val type: String,
    val params: Map<String, String> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "var name must not be blank" }
        require(type.isNotBlank()) { "var type must not be blank" }
    }
}

/**
 * ROADMAP §7 L11a — Espanso vars expander.
 *
 * Walks an Espanso match's [EspansoMatch.replace] template, finds
 * every `{{name}}` placeholder, and substitutes the resolved value
 * from the match's [EspansoMatch.vars] list. Built-in var types
 * supported at the scaffold tier:
 *
 *  - **date**: today's date formatted via Java's [java.time.format.DateTimeFormatter]
 *    pattern (default `yyyy-MM-dd`).
 *  - **clipboard**: the current clipboard primary text. The caller
 *    passes the clipboard provider as a `(() -> String?)` so the
 *    expander stays platform-neutral.
 *  - **echo**: a literal string from `params.echo` — useful for
 *    parameterising sub-templates.
 *  - **random**: choose a random line from `params.choices`
 *    (semicolon-separated list).
 *
 * Other Espanso var types (shell, script, form) require process
 * execution / interactive UI and are intentionally not surfaced here.
 */
object EspansoVarsExpander {

    fun expand(
        match: EspansoMatch,
        clipboardProvider: () -> String? = { null },
        nowProvider: () -> java.time.LocalDateTime = { java.time.LocalDateTime.now() },
        randomProvider: (List<String>) -> String? = { choices -> choices.randomOrNull() },
    ): String {
        if (match.vars.isEmpty()) return match.replace
        val resolved = match.vars.associateBy({ it.name }) { v ->
            resolveVar(v, clipboardProvider, nowProvider, randomProvider)
        }
        val placeholder = Regex("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}")
        return placeholder.replace(match.replace) { mr ->
            val name = mr.groupValues[1]
            resolved[name] ?: mr.value
        }
    }

    private fun resolveVar(
        v: EspansoVar,
        clipboardProvider: () -> String?,
        nowProvider: () -> java.time.LocalDateTime,
        randomProvider: (List<String>) -> String?,
    ): String {
        return when (v.type) {
            "date" -> {
                val pattern = v.params["format"] ?: "yyyy-MM-dd"
                runCatching {
                    nowProvider().format(java.time.format.DateTimeFormatter.ofPattern(pattern))
                }.getOrDefault("")
            }
            "clipboard" -> clipboardProvider().orEmpty()
            "echo" -> v.params["echo"].orEmpty()
            "random" -> {
                val choices = v.params["choices"]?.split(';')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                if (choices.isEmpty()) "" else randomProvider(choices).orEmpty()
            }
            else -> ""
        }
    }
}
