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

import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * ROADMAP §7 L8.1 — Keyman LDML `<transforms>` sequencer.
 *
 * A LDML keyboard's `<keys>` block declares the *direct* output of
 * each key. The `<transforms>` block separately declares **chained
 * substitutions**: a buffer-state machine where typing sequences of
 * characters trigger an emit (compose-key style dead-keys, ligature
 * formation, vowel-mark stacking).
 *
 * Examples from real Keyman keyboards:
 *  - Vietnamese: `aa` + `f` (mood marker) → `ầ`.
 *  - Tigrinya: `s` + `'` → `ጡ`.
 *  - French: `e` + `'` → `é`.
 *
 * The LDML `<transforms type="simple">` element nests `<transformGroup>`
 * which nests `<transform from="..." to="..."/>` rules.
 *
 * This parser produces a deterministic [LdmlTransformTable] the
 * runtime engine ([LdmlTransformEngine] below) consumes: it tracks a
 * sliding buffer of typed characters and on every keystroke checks
 * for a `from`-prefix match. On match the matched chars are deleted
 * and the `to` chars are emitted. No-match means the keystroke
 * advances the buffer normally.
 *
 * Reference: [LDML keyboards spec §6](https://www.unicode.org/reports/tr35/tr35-keyboards.html#transform).
 */
object LdmlTransformsParser {

    /**
     * Parse `<transforms>` rules from an LDML keyboard XML blob.
     * Returns an empty table when no transforms are declared or the
     * XML is malformed.
     */
    fun parse(xml: String): LdmlTransformTable {
        if (xml.isBlank()) return LdmlTransformTable.Empty
        return runCatching { parseInternal(xml) }.getOrDefault(LdmlTransformTable.Empty)
    }

    private fun parseInternal(xml: String): LdmlTransformTable {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        document.documentElement.normalize()

        val rules = ArrayList<LdmlTransformRule>()
        val transformNodes = document.getElementsByTagName("transform")
        for (i in 0 until transformNodes.length) {
            val node = transformNodes.item(i) as? Element ?: continue
            val from = node.getAttribute("from")
            val to = node.getAttribute("to")
            if (from.isEmpty() || to.isEmpty()) continue
            rules += LdmlTransformRule(from = from, to = to)
        }
        return if (rules.isEmpty()) LdmlTransformTable.Empty else LdmlTransformTable(rules)
    }
}

/**
 * One `<transform from="..." to="..."/>` rule. The runtime checks
 * input-buffer suffixes against the [from] pattern (interpreted as a
 * **fixed string** in this scaffold; LDML allows `\u` escapes which
 * the parser already passed through as resolved characters) and on
 * match emits [to].
 */
data class LdmlTransformRule(
    val from: String,
    val to: String,
) {
    init {
        require(from.isNotEmpty()) { "from must not be empty" }
        require(to.isNotEmpty()) { "to must not be empty" }
    }

    val patternLength: Int get() = from.length
}

/**
 * Sorted bundle of transform rules. The engine ([LdmlTransformEngine])
 * matches by **longest pattern first** so a `from="aae"` rule wins
 * over a `from="ae"` rule when both could fire.
 */
class LdmlTransformTable(rules: List<LdmlTransformRule>) {
    /** Rules sorted descending by pattern length for greedy matching. */
    val rulesByLengthDesc: List<LdmlTransformRule> =
        rules.sortedByDescending { it.patternLength }

    val isEmpty: Boolean get() = rulesByLengthDesc.isEmpty()

    companion object {
        val Empty: LdmlTransformTable = LdmlTransformTable(emptyList())
    }
}

/**
 * Stateful runtime that turns a stream of incoming characters into
 * a stream of output characters, applying the table's transform rules
 * on every input. Reset before each composing session.
 */
class LdmlTransformEngine(private val table: LdmlTransformTable) {

    /** Output emitted so far in this composing session. */
    var output: StringBuilder = StringBuilder()
        private set

    /** Maximum lookback window — set to the table's longest pattern. */
    private val maxLookback: Int = table.rulesByLengthDesc.firstOrNull()?.patternLength ?: 0

    /** Append [char] to the engine; checks for a transform match and
     *  emits + rewrites in-place when one fires. Returns the new tail
     *  of [output] so the IME can sync its composing region. */
    fun consume(char: Char): String {
        output.append(char)
        if (table.isEmpty || maxLookback < 2) return char.toString()
        // Check rules — longest first — against the tail of the output.
        for (rule in table.rulesByLengthDesc) {
            val tailStart = output.length - rule.patternLength
            if (tailStart < 0) continue
            val tail = output.substring(tailStart, output.length)
            if (tail == rule.from) {
                output.setLength(tailStart)
                output.append(rule.to)
                return rule.to
            }
        }
        return char.toString()
    }

    fun reset() {
        output = StringBuilder()
    }

    /** Append a whole string char-by-char; convenient for batch tests. */
    fun consumeAll(input: String): String {
        reset()
        for (ch in input) {
            consume(ch)
        }
        return output.toString()
    }
}
