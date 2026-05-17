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
 * ROADMAP §7 Next-6.4a — macOS `.keylayout` import parser.
 *
 * Apple keyboard layouts are XML files containing a `<keyboard>` root,
 * one or more `<keyMapSet>` blocks, and a `<modifierMap>` that maps
 * modifier combinations to key-map indexes. This parser normalizes the
 * common ANSI/ISO key-map shape into [HardwareKeyboardLayout], using the
 * macOS virtual key code as the map key until the Android runtime mapper
 * (Next-6.4b) translates attached-device key codes at input time.
 *
 * Like [KeymanLdmlParser], this parser is XXE-hardened because imported
 * layout files cross a user/addon trust boundary.
 */
object MacKeylayoutParser {

    fun parse(xml: String): HardwareKeyboardLayout {
        if (xml.isBlank()) return HardwareKeyboardLayout.Empty
        return runCatching { parseInternal(xml) }.getOrDefault(HardwareKeyboardLayout.Empty)
    }

    private fun parseInternal(xml: String): HardwareKeyboardLayout {
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

        val root = document.documentElement ?: return HardwareKeyboardLayout.Empty
        if (!root.tagName.equals("keyboard", ignoreCase = true)) {
            return HardwareKeyboardLayout.Empty
        }

        val mapSet = root.selectedKeyMapSet() ?: return HardwareKeyboardLayout.Empty
        val keyMaps = mapSet.childElements("keyMap")
            .mapNotNull { keyMap ->
                val index = keyMap.getAttribute("index").toIntOrNull() ?: return@mapNotNull null
                index to keyMap
            }
            .toMap()
        if (keyMaps.isEmpty()) return HardwareKeyboardLayout.Empty

        val slotIndexes = root.parseModifierSlots()
        val actionOutputs = root.parseActionOutputs()
        val entries = LinkedHashMap<Int, MutableEntry>()

        for ((slot, mapIndex) in slotIndexes) {
            val keyMap = keyMaps[mapIndex] ?: continue
            for (key in keyMap.childElements("key")) {
                val code = key.getAttribute("code").toIntOrNull() ?: continue
                val resolved = key.resolvedOutput(actionOutputs) ?: continue
                val entry = entries.getOrPut(code) { MutableEntry(virtualKeyName = "MAC_$code") }
                entry.apply(slot, resolved)
            }
        }

        val scancodeMap = entries.mapNotNull { (code, entry) ->
            entry.toHardwareKeyEntry()?.let { code to it }
        }.toMap()
        if (scancodeMap.isEmpty()) return HardwareKeyboardLayout.Empty

        return HardwareKeyboardLayout(
            name = root.firstNonBlankAttribute("name", "id"),
            locale = root.firstNonBlankAttribute("locale", "language"),
            scancodeMap = scancodeMap,
        )
    }

    private fun Element.selectedKeyMapSet(): Element? {
        val requestedId = childElements("layouts")
            .firstOrNull()
            ?.childElements("layout")
            ?.firstNotNullOfOrNull { it.getAttribute("mapSet").takeIf(String::isNotBlank) }
        val sets = childElements("keyMapSet")
        if (requestedId != null) {
            sets.firstOrNull { it.getAttribute("id") == requestedId }?.let { return it }
        }
        return sets.firstOrNull()
    }

    private fun Element.parseModifierSlots(): Map<Slot, Int> {
        val slots = linkedMapOf<Slot, Int>()
        val modifierMap = childElements("modifierMap").firstOrNull()
        modifierMap?.getAttribute("defaultIndex")
            ?.toIntOrNull()
            ?.let { slots.putIfAbsent(Slot.NORMAL, it) }

        modifierMap?.childElements("keyMapSelect")?.forEach { select ->
            val index = select.getAttribute("mapIndex").toIntOrNull() ?: return@forEach
            val modifiers = select.childElements("modifier")
            if (modifiers.isEmpty()) {
                slots.putIfAbsent(Slot.NORMAL, index)
            }
            modifiers.forEach { modifier ->
                modifier.getAttribute("keys")
                    .classifyModifierSlot()
                    ?.let { slot -> slots.putIfAbsent(slot, index) }
            }
        }

        if (slots.isNotEmpty()) return slots
        return linkedMapOf(
            Slot.NORMAL to 0,
            Slot.SHIFT to 1,
            Slot.ALT_GR to 2,
            Slot.SHIFT_ALT_GR to 3,
        )
    }

    private fun Element.parseActionOutputs(): Map<String, String> {
        val outputs = LinkedHashMap<String, String>()
        childElements("actions").forEach { actions ->
            actions.childElements("action").forEach { action ->
                val id = action.getAttribute("id").takeIf(String::isNotBlank) ?: return@forEach
                val output = action.childElements("when")
                    .firstNotNullOfOrNull { it.getAttribute("output").takeIf(String::isNotBlank) }
                    ?: return@forEach
                outputs[id] = output
            }
        }
        return outputs
    }

    private fun Element.resolvedOutput(actionOutputs: Map<String, String>): ResolvedOutput? {
        getAttribute("output")
            .takeIf(String::isNotBlank)
            ?.let { return ResolvedOutput(text = it, deadKey = false) }
        val actionId = getAttribute("action").takeIf(String::isNotBlank) ?: return null
        val actionOutput = actionOutputs[actionId] ?: return null
        return ResolvedOutput(text = actionOutput, deadKey = true)
    }

    private fun String.classifyModifierSlot(): Slot? {
        val keys = lowercase()
        if (keys.isBlank() || keys == "none") return Slot.NORMAL
        val hasShift = "shift" in keys
        val hasOption = "option" in keys || "alt" in keys
        val hasControl = "control" in keys || "ctrl" in keys
        val hasCommand = "command" in keys || "cmd" in keys
        if (hasControl || hasCommand) return null
        if (!hasShift && !hasOption) return null
        return when {
            hasShift && !hasOption -> Slot.SHIFT
            !hasShift && hasOption -> Slot.ALT_GR
            else -> Slot.SHIFT_ALT_GR
        }
    }

    private fun Element.firstNonBlankAttribute(vararg names: String): String {
        for (name in names) {
            val value = getAttribute(name)
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun Element.childElements(tagName: String): List<Element> {
        val elements = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && (node as Element).tagName == tagName) {
                elements += node
            }
        }
        return elements
    }

    private enum class Slot { NORMAL, SHIFT, ALT_GR, SHIFT_ALT_GR }

    private data class ResolvedOutput(
        val text: String,
        val deadKey: Boolean,
    ) {
        val codePoint: Int? = text.takeIf(String::isNotEmpty)?.codePointAt(0)
    }

    private data class MutableEntry(
        val virtualKeyName: String,
        var normal: Int? = null,
        var shift: Int? = null,
        var altGr: Int? = null,
        var shiftAltGr: Int? = null,
        var deadKeyTrigger: Int? = null,
        var displayLabel: String? = null,
    ) {
        fun apply(slot: Slot, output: ResolvedOutput) {
            val codePoint = output.codePoint ?: return
            when (slot) {
                Slot.NORMAL -> normal = codePoint
                Slot.SHIFT -> shift = codePoint
                Slot.ALT_GR -> altGr = codePoint
                Slot.SHIFT_ALT_GR -> shiftAltGr = codePoint
            }
            if (output.deadKey && deadKeyTrigger == null) {
                deadKeyTrigger = codePoint
                displayLabel = output.text
            }
        }

        fun toHardwareKeyEntry(): HardwareKeyEntry? {
            if (normal == null && shift == null && altGr == null &&
                shiftAltGr == null && deadKeyTrigger == null
            ) {
                return null
            }
            return HardwareKeyEntry(
                virtualKeyName = virtualKeyName,
                normal = normal,
                shift = shift,
                altGr = altGr,
                shiftAltGr = shiftAltGr,
                deadKeyTrigger = deadKeyTrigger,
                displayLabel = displayLabel,
            )
        }
    }
}
