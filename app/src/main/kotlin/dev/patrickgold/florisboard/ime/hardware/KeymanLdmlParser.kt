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
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * ROADMAP §7 L8 — Keyman LDML keyboard importer.
 *
 * Keyman.org publishes ~1,000 keyboards spanning 2,500+ languages
 * under MIT in the **LDML keyboard XML** dialect (CLDR's keyboards
 * subset; see the [LDML keyboards specification](https://www.unicode.org/reports/tr35/tr35-keyboards.html)).
 * Each keyboard is one `*.xml` file declaring per-iso-position output
 * characters across the various modifier states (`none`, `shift`,
 * `altR`, `caps`, etc.).
 *
 * The LDML keyboard XML root looks roughly like:
 *
 * ```xml
 * <keyboard locale="am-ET">
 *   <names>
 *     <name value="Amharic SERA"/>
 *   </names>
 *   <keys>
 *     <key id="A01" output="ሰ" longPress="ሠ"/>
 *     ...
 *   </keys>
 * </keyboard>
 * ```
 *
 * SwiftFloris maps each `<key>` to a [HardwareKeyEntry] keyed by ISO
 * position so the [HardwareKeyboardLayout] surface (Next-6.4) can
 * consume Keyman output without a second cross-format intermediate.
 * Future L8.x slices extend to the LDML `<transforms>` (dead-key +
 * ligature sequencer) and `<displays>` (visual-glyph hints) sub-trees.
 *
 * Uses `javax.xml.parsers.DocumentBuilderFactory` (in the JVM stdlib —
 * works in both Android and pure-JVM unit tests; no external
 * dependency). The factory is **hardened against XXE** by disabling
 * external entities, DTDs, and parameter entity processing per OWASP
 * XXE-prevention guidance — important because addon-supplied LDML
 * files cross the addon-IME trust boundary.
 */
object KeymanLdmlParser {

    /**
     * Parse the contents of an LDML keyboard XML file into a
     * [HardwareKeyboardLayout]. Returns [HardwareKeyboardLayout.Empty]
     * when no `<key>` entries resolve. Tolerates malformed XML by
     * returning [HardwareKeyboardLayout.Empty] rather than throwing.
     */
    fun parse(xml: String): HardwareKeyboardLayout {
        if (xml.isBlank()) return HardwareKeyboardLayout.Empty
        return runCatching { parseInternal(xml) }.getOrDefault(HardwareKeyboardLayout.Empty)
    }

    private fun parseInternal(xml: String): HardwareKeyboardLayout {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // OWASP XXE hardening — addon-supplied LDML crosses a trust boundary.
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

        val root = document.documentElement ?: return HardwareKeyboardLayout.Empty
        val locale = root.getAttribute("locale").orEmpty()

        var displayName = ""
        val nameNodes = root.getElementsByTagName("name")
        for (i in 0 until nameNodes.length) {
            val node = nameNodes.item(i) as? Element ?: continue
            val value = node.getAttribute("value")
            if (value.isNotBlank()) {
                displayName = value
                break
            }
        }

        val scancodeMap = LinkedHashMap<Int, HardwareKeyEntry>()
        val keyNodes = root.getElementsByTagName("key")
        var sequentialIndex = 0
        for (i in 0 until keyNodes.length) {
            val node = keyNodes.item(i) as? Element ?: continue
            val id = node.getAttribute("id")
            if (id.isBlank()) continue
            val output = node.getAttribute("output").takeIf { it.isNotEmpty() }
            val shiftOutput = node.getAttribute("longPress").takeIf { it.isNotEmpty() }
                ?: node.getAttribute("shift").takeIf { it.isNotEmpty() }
            val normalCp = output?.codePointAt(0)
            val shiftCp = shiftOutput?.codePointAt(0)
            if (normalCp == null && shiftCp == null) continue
            scancodeMap[0x100 + sequentialIndex] = HardwareKeyEntry(
                virtualKeyName = id,
                normal = normalCp,
                shift = shiftCp,
            )
            sequentialIndex++
        }

        if (scancodeMap.isEmpty()) return HardwareKeyboardLayout.Empty
        return HardwareKeyboardLayout(
            name = displayName.ifBlank { locale },
            locale = locale,
            scancodeMap = scancodeMap.toMap(),
        )
    }

    @Suppress("unused")
    private val Node.elementOrNull: Element?
        get() = this as? Element
}
