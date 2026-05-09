/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.core.SubtypePreset
import dev.patrickgold.florisboard.ime.nlp.PunctuationRule
import dev.patrickgold.florisboard.ime.popup.PopupMappingComponent
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.lib.ext.ExtensionComponent
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.lib.ext.ExtensionEditor
import dev.patrickgold.florisboard.lib.ext.ExtensionMeta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName(KeyboardExtension.SERIAL_TYPE)
@Serializable
data class KeyboardExtension(
    override val meta: ExtensionMeta,
    override val dependencies: List<String>? = null,
    val composers: List<Composer> = listOf(),
    val currencySets: List<CurrencySet> = listOf(),
    val layouts: Map<String, List<LayoutArrangementComponent>> = mapOf(),
    val punctuationRules: List<PunctuationRule> = listOf(),
    val popupMappings: List<PopupMappingComponent> = listOf(),
    val subtypePresets: List<SubtypePreset> = listOf(),
) : Extension() {

    companion object {
        const val SERIAL_TYPE = "ime.extension.keyboard"
    }

    override fun serialType() = SERIAL_TYPE

    override fun components(): List<ExtensionComponent> {
        return emptyList()
    }

    override fun edit() = KeyboardExtensionEditor(
        meta = meta,
        dependencies = dependencies?.toMutableList() ?: mutableListOf(),
        composers = composers.toMutableList(),
        currencySets = currencySets.toMutableList(),
        layouts = layouts.mapValues { (_, v) -> v.toMutableList() }.toMutableMap(),
        punctuationRules = punctuationRules.toMutableList(),
        popupMappings = popupMappings.toMutableList(),
        subtypePresets = subtypePresets.toMutableList(),
    )
}

class KeyboardExtensionEditor(
    override var meta: ExtensionMeta,
    override val dependencies: MutableList<String>,
    val composers: MutableList<Composer>,
    val currencySets: MutableList<CurrencySet>,
    val layouts: MutableMap<String, MutableList<LayoutArrangementComponent>>,
    val punctuationRules: MutableList<PunctuationRule>,
    val popupMappings: MutableList<PopupMappingComponent>,
    val subtypePresets: MutableList<SubtypePreset>,
) : ExtensionEditor {

    override fun build() = KeyboardExtension(
        meta = meta,
        dependencies = dependencies.takeUnless { it.isEmpty() }?.toList(),
        composers = composers.toList(),
        currencySets = currencySets.toList(),
        layouts = layouts.mapValues { (_, v) -> v.toList() }.toMap(),
        punctuationRules = punctuationRules.toList(),
        popupMappings = popupMappings.toList(),
        subtypePresets = subtypePresets.toList(),
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun extCoreComposer(id: String): ExtensionComponentName {
    return ExtensionComponentName(
        extensionId = "org.florisboard.composers",
        componentId = id,
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun extCoreCurrencySet(id: String): ExtensionComponentName {
    return ExtensionComponentName(
        extensionId = "org.florisboard.currencysets",
        componentId = id,
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun extCoreLayout(id: String): ExtensionComponentName {
    return ExtensionComponentName(
        extensionId = "org.florisboard.layouts",
        componentId = id,
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun extCorePunctuationRule(id: String): ExtensionComponentName {
    return ExtensionComponentName(
        extensionId = "org.florisboard.localization",
        componentId = id,
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun extCorePopupMapping(id: String): ExtensionComponentName {
    return ExtensionComponentName(
        extensionId = "org.florisboard.localization",
        componentId = id,
    )
}
