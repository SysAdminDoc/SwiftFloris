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

package dev.patrickgold.florisboard.ime.keyboard3

/**
 * Writes the parsed Keyboard3 model as deterministic, self-contained XML.
 * Bundled implied imports are expanded on output so the result remains a
 * portable local fixture and never depends on a filesystem or network path.
 */
object Keyboard3XmlWriter {
    fun write(layout: Keyboard3Layout): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine(
            "<keyboard3 conformsTo=${attribute(layout.conformsTo.toString())} locale=${attribute(layout.locale)}>",
        )
        append("  <info name=").append(attribute(layout.name))
        layout.author?.let { append(" author=").append(attribute(it)) }
        layout.layoutHint?.let { append(" layout=").append(attribute(it)) }
        layout.indicator?.let { append(" indicator=").append(attribute(it)) }
        appendLine("/>")
        layout.version?.let { appendLine("  <version number=${attribute(it)}/>") }
        if (layout.additionalLocales.isNotEmpty()) {
            appendLine("  <locales>")
            layout.additionalLocales.forEach { locale ->
                appendLine("    <locale id=${attribute(locale)}/>")
            }
            appendLine("  </locales>")
        }
        if (layout.normalizationDisabled) {
            appendLine("  <settings normalization=\"disabled\"/>")
        }
        writeVariables(layout.variables)
        if (layout.displays.isNotEmpty()) {
            appendLine("  <displays>")
            layout.displays.forEach { display ->
                append("    <display")
                display.output?.let { append(" output=").append(attribute(it)) }
                display.keyId?.let { append(" keyId=").append(attribute(it)) }
                append(" display=").append(attribute(display.display)).appendLine("/>")
            }
            appendLine("  </displays>")
        }
        appendLine("  <keys>")
        layout.keys.values.forEach { key ->
            append("    <key id=").append(attribute(key.id))
            if (key.gap) {
                append(" gap=\"true\"")
            } else {
                key.output?.let { append(" output=").append(textAttribute(it)) }
            }
            key.flickId?.let { append(" flickId=").append(attribute(it)) }
            if (key.longPressKeyIds.isNotEmpty()) {
                append(" longPressKeyIds=").append(attribute(key.longPressKeyIds.joinToString(" ")))
            }
            key.longPressDefaultKeyId?.let { append(" longPressDefaultKeyId=").append(attribute(it)) }
            if (key.multiTapKeyIds.isNotEmpty()) {
                append(" multiTapKeyIds=").append(attribute(key.multiTapKeyIds.joinToString(" ")))
            }
            if (key.stretch) append(" stretch=\"true\"")
            key.layerId?.let { append(" layerId=").append(attribute(it)) }
            key.width?.let { append(" width=").append(attribute(it.toString())) }
            appendLine("/>")
        }
        appendLine("  </keys>")
        if (layout.flicks.isNotEmpty()) {
            appendLine("  <flicks>")
            layout.flicks.values.forEach { flick ->
                appendLine("    <flick id=${attribute(flick.id)}>")
                flick.segments.forEach { segment ->
                    appendLine(
                        "      <flickSegment directions=${attribute(segment.directions.joinToString(" "))} keyId=${attribute(segment.keyId)}/>",
                    )
                }
                appendLine("    </flick>")
            }
            appendLine("  </flicks>")
        }
        if (layout.forms.values.any { it.scanCodeRows.isNotEmpty() }) {
            appendLine("  <forms>")
            layout.forms.values.filter { it.scanCodeRows.isNotEmpty() }.forEach { form ->
                appendLine("    <form id=${attribute(form.id)}>")
                form.scanCodeRows.forEach { row ->
                    appendLine("      <scanCodes codes=${attribute(row.joinToString(" ") { "%02X".format(it) })}/>")
                }
                appendLine("    </form>")
            }
            appendLine("  </forms>")
        }
        layout.layerSets.forEach { layerSet ->
            append("  <layers formId=").append(attribute(layerSet.formId))
            layerSet.minDeviceWidth?.let { append(" minDeviceWidth=").append(attribute(it.toString())) }
            appendLine(">")
            layerSet.layers.forEach { layer ->
                append("    <layer")
                layer.id?.let { append(" id=").append(attribute(it)) }
                layer.modifiers?.let { append(" modifiers=").append(attribute(it)) }
                appendLine(">")
                layer.rows.forEach { row ->
                    appendLine("      <row keys=${attribute(row.keyIds.joinToString(" "))}/>")
                }
                appendLine("    </layer>")
            }
            appendLine("  </layers>")
        }
        layout.transforms.groupBy { it.type }.forEach { (type, groups) ->
            appendLine("  <transforms type=${attribute(type)}>")
            groups.forEach { group ->
                appendLine("    <transformGroup>")
                group.transforms.forEach { transform ->
                    append("      <transform from=").append(attribute(transform.from))
                    transform.to?.let { append(" to=").append(attribute(it)) }
                    appendLine("/>")
                }
                appendLine("    </transformGroup>")
            }
            appendLine("  </transforms>")
        }
        appendLine("</keyboard3>")
    }

    private fun StringBuilder.writeVariables(variables: Keyboard3Variables) {
        if (variables.strings.isEmpty() && variables.sets.isEmpty() && variables.unicodeSets.isEmpty()) return
        appendLine("  <variables>")
        variables.strings.forEach { (id, value) ->
            appendLine("    <string id=${attribute(id)} value=${attribute(value)}/>")
        }
        variables.sets.forEach { (id, values) ->
            appendLine("    <set id=${attribute(id)} value=${attribute(values.joinToString(" "))}/>")
        }
        variables.unicodeSets.forEach { (id, value) ->
            appendLine("    <uset id=${attribute(id)} value=${attribute(value)}/>")
        }
        appendLine("  </variables>")
    }

    private fun attribute(value: String): String {
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(char)
                }
            }
            append('"')
        }
    }

    private fun textAttribute(value: String): String {
        return if (value.isBlank()) {
            val points = buildList {
                var index = 0
                while (index < value.length) {
                    val point = value.codePointAt(index)
                    add("%X".format(point))
                    index += Character.charCount(point)
                }
            }
            attribute("\\u{${points.joinToString(" ")}}")
        } else {
            attribute(value)
        }
    }
}
