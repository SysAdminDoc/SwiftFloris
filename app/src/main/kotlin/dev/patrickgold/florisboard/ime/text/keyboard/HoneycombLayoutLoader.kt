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

package dev.patrickgold.florisboard.ime.text.keyboard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ROADMAP §8 L9.2 — pure-JVM parser that converts the honeycomb
 * layout JSON shape into a `List<List<String>>` of row labels the
 * [HoneycombKeyboardRow] renderer can consume directly.
 *
 * The layout JSON format mirrors the existing FlorisBoard character
 * layouts (`qwerty.json`, `colemak.json`, etc.): an outer array of
 * rows, each row an array of key objects with at minimum a `"label"`
 * field. Keys without a `"label"` (modifiers, system_gui, etc.) are
 * filtered out — the L9.2 renderer slice covers character keys
 * only; modifier handling rides with the eventual TextKeyboardLayout
 * integration.
 *
 * Uses `kotlinx.serialization.json` (already on the project
 * `implementation` classpath via `libs.kotlinx.serialization.json`).
 * Tolerates malformed input by returning an empty list rather than
 * throwing — keeps the renderer fail-safe on disk corruption / bad
 * addon-supplied layouts.
 */
object HoneycombLayoutLoader {

    private val parser: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    /**
     * Parse a layout JSON string into a `List<List<String>>` of
     * character-key labels per row. Non-character keys (modifiers,
     * `system_gui`, `enter_editing`) are filtered out. Returns an
     * empty list when the input is malformed.
     */
    fun parse(json: String): List<List<String>> {
        return try {
            val root = parser.parseToJsonElement(json).jsonArray
            buildList {
                for (rowElement in root) {
                    val rowJson = rowElement as? JsonArray ?: continue
                    val rowLabels = mutableListOf<String>()
                    for (keyElement in rowJson) {
                        val keyJson = keyElement as? JsonObject ?: continue
                        val label = keyJson["label"]?.jsonPrimitive?.content?.trim().orEmpty()
                        if (label.isEmpty()) continue
                        if (isCharacterKey(keyJson, label)) {
                            rowLabels += label
                        }
                    }
                    if (rowLabels.isNotEmpty()) add(rowLabels)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * A key is character-class when its `type` field is absent (the
     * default in the FlorisBoard layout schema) and its label isn't
     * one of the known modifier / system_gui words used in
     * `honeycomb.json`'s non-character cells.
     */
    private fun isCharacterKey(keyJson: JsonObject, label: String): Boolean {
        val type = keyJson["type"]?.jsonPrimitive?.content.orEmpty()
        if (type.isNotEmpty()) return false
        return label !in MODIFIER_LABELS
    }

    private val MODIFIER_LABELS = setOf(
        "shift", "delete", "space", "enter",
        "view_symbols", "view_numeric", "view_numeric_advanced",
        "view_characters", "view_phone", "view_phone2",
    )
}
