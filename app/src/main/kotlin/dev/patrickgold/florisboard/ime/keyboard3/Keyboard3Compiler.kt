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

import dev.patrickgold.florisboard.ime.core.SubtypeLayoutMap
import dev.patrickgold.florisboard.ime.core.SubtypePreset
import dev.patrickgold.florisboard.ime.keyboard.AbstractKeyData
import dev.patrickgold.florisboard.ime.keyboard.CurrencySet
import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangement
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangementComponent
import dev.patrickgold.florisboard.ime.keyboard.LayoutTypeId
import dev.patrickgold.florisboard.ime.keyboard.extCoreCurrencySet
import dev.patrickgold.florisboard.ime.keyboard.extCorePopupMapping
import dev.patrickgold.florisboard.ime.popup.PopupSet
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.ime.text.composing.WithRules
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.MultiTextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.lib.ext.ExtensionDefaults
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.lib.ext.ExtensionJsonConfig
import dev.patrickgold.florisboard.lib.ext.ExtensionMaintainer
import dev.patrickgold.florisboard.lib.ext.ExtensionMeta
import dev.patrickgold.florisboard.lib.io.DefaultJsonConfig
import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.encodeToString
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson

data class Keyboard3CompilationResult(
    val extension: KeyboardExtension?,
    val arrangements: Map<String, LayoutArrangement>,
    val diagnostics: List<Keyboard3Diagnostic>,
    val sourceXml: String? = null,
) {
    val isSuccess: Boolean get() = extension != null && diagnostics.isEmpty()
}

/**
 * Compiles the safe subset of Keyboard3 into the extension format already
 * consumed by FlorisBoard. The compiler does not broaden the parser's trust
 * boundary: source imports are never read here, and runtime features without
 * an equivalent FlorisBoard key model are rejected with a diagnostic.
 */
object Keyboard3Compiler {
    private const val MaxCompiledLayouts = 64
    private val RtlLanguages = setOf("ar", "dv", "fa", "he", "ku", "ps", "sd", "ug", "ur", "yi")

    fun compileXml(xml: String): Keyboard3CompilationResult {
        val parsed = Keyboard3Parser.parse(xml)
        if (!parsed.isSuccess) {
            return Keyboard3CompilationResult(null, emptyMap(), parsed.diagnostics, xml)
        }
        return compile(parsed.layout!!, sourceId = xml, sourceXml = xml)
    }

    fun compile(
        layout: Keyboard3Layout,
        sourceId: String = "${layout.locale}:${layout.name}:${layout.conformsTo}",
        sourceXml: String? = null,
    ): Keyboard3CompilationResult {
        return try {
            compileInternal(layout, sourceId, sourceXml)
        } catch (abort: CompileAbort) {
            Keyboard3CompilationResult(null, emptyMap(), listOf(abort.diagnostic), sourceXml)
        } catch (_: Throwable) {
            Keyboard3CompilationResult(
                extension = null,
                arrangements = emptyMap(),
                diagnostics = listOf(
                    Keyboard3Diagnostic(
                        Keyboard3DiagnosticCode.UNSUPPORTED_RUNTIME_FEATURE,
                        "compiler",
                        "Keyboard3 source cannot be represented by the current FlorisBoard layout model.",
                    ),
                ),
                sourceXml = sourceXml,
            )
        }
    }

    /** Writes a validated compiler result into an extension working directory. */
    fun writePackage(
        result: Keyboard3CompilationResult,
        workingDir: FsDir,
    ) {
        val extension = result.extension ?: error("Cannot write an unsuccessful Keyboard3 compilation")
        check(result.diagnostics.isEmpty()) { "Cannot write Keyboard3 diagnostics as an extension" }
        workingDir.mkdirs()
        val manifest: Extension = extension
        FsFile(workingDir, ExtensionDefaults.MANIFEST_FILE_NAME).writeJson(manifest, ExtensionJsonConfig)
        val layoutDir = workingDir.subDir("layouts").subDir(LayoutTypeId.CHARACTERS)
        layoutDir.mkdirs()
        for ((id, arrangement) in result.arrangements) {
            FsFile(layoutDir, "$id.json").writeText(
                DefaultJsonConfig.encodeToString<LayoutArrangement>(arrangement),
                Charsets.UTF_8,
            )
        }
        result.sourceXml?.let { source ->
            val sourceDir = workingDir.subDir("keyboard3")
            sourceDir.mkdirs()
            FsFile(sourceDir, "source.xml").writeText(source, Charsets.UTF_8)
        }
    }

    private fun compileInternal(
        layout: Keyboard3Layout,
        sourceId: String,
        sourceXml: String?,
    ): Keyboard3CompilationResult {
        if (layout.normalizationDisabled) {
            abort(
                Keyboard3DiagnosticCode.UNSUPPORTED_RUNTIME_FEATURE,
                "keyboard3/settings@normalization",
                "Keyboard3 normalization=\"disabled\" has no equivalent in the FlorisBoard composer.",
            )
        }
        if (layout.variables.strings.isNotEmpty() || layout.variables.sets.isNotEmpty() || layout.variables.unicodeSets.isNotEmpty()) {
            abort(
                Keyboard3DiagnosticCode.UNSUPPORTED_RUNTIME_FEATURE,
                "keyboard3/variables",
                "Keyboard3 variables require substitution semantics that the current layout compiler does not expose.",
            )
        }

        val touchLayerSets = layout.layerSets.filter { it.formId == "touch" }
        if (touchLayerSets.isEmpty()) {
            abort(
                Keyboard3DiagnosticCode.UNSUPPORTED_RUNTIME_FEATURE,
                "keyboard3/layers",
                "Keyboard3 import requires at least one touch layer set.",
            )
        }
        val touchLayers = touchLayerSets.flatMap { set ->
            set.layers.map { layer -> set to layer }
        }
        if (touchLayers.size > MaxCompiledLayouts) {
            abort(
                Keyboard3DiagnosticCode.OVER_BUDGET,
                "keyboard3/layers",
                "Keyboard3 declares more than $MaxCompiledLayouts compilable touch layers.",
            )
        }

        val extensionId = ExtensionDefaults.createLocalId(
            groupName = "keyboard3",
            extensionName = "${slug(layout.locale)}_${slug(layout.name)}_${digest(sourceId).take(12)}",
        )
        val author = layout.author?.takeIf { it.isNotBlank() } ?: "Keyboard3 import"
        val componentNames = mutableSetOf<String>()
        val arrangements = linkedMapOf<String, LayoutArrangement>()
        val components = mutableListOf<LayoutArrangementComponent>()
        for ((index, pair) in touchLayers.withIndex()) {
            val (layerSet, layer) = pair
            val rawId = buildString {
                append("layer_")
                append(layer.id ?: index.toString())
                layerSet.minDeviceWidth?.let { append("_w").append(it) }
            }
            val componentId = uniqueComponentId(slug(rawId), componentNames)
            val arrangement = layer.rows.map { row ->
                row.keyIds.map { keyId -> keyDataFor(layout, keyId, linkedSetOf()) }
            }
            arrangements[componentId] = arrangement
            components += LayoutArrangementComponent(
                id = componentId,
                label = layer.id?.let { "${layout.name} ($it)" } ?: layout.name,
                authors = listOf(author),
                direction = directionFor(layout.locale),
                arrangementFile = "layouts/${LayoutTypeId.CHARACTERS}/$componentId.json",
            )
        }
        val firstComponent = components.firstOrNull() ?: abort(
            Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE,
            "keyboard3/layers",
            "Keyboard3 has no compilable touch layer.",
        )

        val simpleRules = compileSimpleTransforms(layout.transforms)
        val composers: List<Composer>
        val composerName: ExtensionComponentName
        if (simpleRules.isEmpty()) {
            composers = emptyList()
            composerName = dev.patrickgold.florisboard.ime.keyboard.extCoreComposer("appender")
        } else {
            val composerId = "keyboard3_transform"
            composers = listOf(WithRules(composerId, "${layout.name} transforms", simpleRules))
            composerName = ExtensionComponentName(extensionId, composerId)
        }

        val extension = KeyboardExtension(
            meta = ExtensionMeta(
                id = extensionId,
                version = layout.version?.takeIf { it.isNotBlank() } ?: "1.0.0",
                title = layout.name,
                description = layout.layoutHint?.takeIf { it.isNotBlank() },
                maintainers = listOf(ExtensionMaintainer.fromOrTakeRaw(author)),
                license = "NOASSERTION",
            ),
            composers = composers,
            currencySets = emptyList<CurrencySet>(),
            layouts = mapOf(LayoutTypeId.CHARACTERS to components),
            popupMappings = emptyList(),
            subtypePresets = listOf(
                SubtypePreset(
                    locale = FlorisLocale.fromTag(layout.locale),
                    composer = composerName,
                    currencySet = extCoreCurrencySet("dollar"),
                    popupMapping = extCorePopupMapping("default"),
                    preferred = SubtypeLayoutMap(
                        characters = ExtensionComponentName(extensionId, firstComponent.id),
                    ),
                    secondaryLocales = layout.additionalLocales.map(FlorisLocale::fromTag),
                ),
            ),
        )
        return Keyboard3CompilationResult(extension, arrangements, emptyList(), sourceXml)
    }

    private fun compileSimpleTransforms(groups: List<Keyboard3TransformGroup>): Map<String, String> {
        val rules = linkedMapOf<String, String>()
        for (group in groups) {
            if (group.type != "simple") {
                abort(
                    Keyboard3DiagnosticCode.UNSUPPORTED_RUNTIME_FEATURE,
                    "keyboard3/transforms@type",
                    "Only literal simple transforms can be represented by WithRules; ${group.type} transforms require a different runtime.",
                )
            }
            for ((index, transform) in group.transforms.withIndex()) {
                if (transform.to == null || transform.from.isEmpty() || '\\' in transform.from || '\\' in transform.to) {
                    abort(
                        Keyboard3DiagnosticCode.UNSUPPORTED_RUNTIME_FEATURE,
                        "keyboard3/transforms/transform[$index]",
                        "Only non-empty literal from/to transform rules can be represented by WithRules.",
                    )
                }
                if (rules.put(transform.from, transform.to) != null) {
                    abort(
                        Keyboard3DiagnosticCode.DUPLICATE_ID,
                        "keyboard3/transforms/transform[$index]",
                        "Transform rule '${transform.from}' is declared more than once.",
                    )
                }
            }
        }
        return rules
    }

    private fun keyDataFor(
        layout: Keyboard3Layout,
        keyId: String,
        activePath: MutableSet<String>,
    ): AbstractKeyData {
        val key = layout.keys[keyId] ?: abort(
            Keyboard3DiagnosticCode.INVALID_REFERENCE,
            "keyboard3/layers/key[$keyId]",
            "Key id '$keyId' does not exist.",
        )
        if (!activePath.add(keyId)) {
            abort(
                Keyboard3DiagnosticCode.INVALID_REFERENCE,
                "keyboard3/keys/key[$keyId]",
                "Popup references form a cycle through key '$keyId'.",
            )
        }
        if (key.layerId != null) {
            abort(
                Keyboard3DiagnosticCode.UNSUPPORTED_RUNTIME_FEATURE,
                "keyboard3/keys/key[$keyId]@layerId",
                "Layer-switch keys are not representable by a static FlorisBoard layout.",
            )
        }
        if (key.width != null && key.width != 1f || key.stretch) {
            abort(
                Keyboard3DiagnosticCode.UNSUPPORTED_RUNTIME_FEATURE,
                "keyboard3/keys/key[$keyId]",
                "Keyboard3 key width/stretch is not representable by the current static layout model.",
            )
        }
        val primary = if (key.gap) {
            TextKeyData(type = KeyType.PLACEHOLDER, code = 0, label = "")
        } else {
            val output = key.output ?: abort(
                Keyboard3DiagnosticCode.UNSUPPORTED_RUNTIME_FEATURE,
                "keyboard3/keys/key[$keyId]",
                "A non-gap Keyboard3 key without output cannot be represented by a text layout.",
            )
            textKey(output, displayFor(layout, key), popup = null)
        }
        if (key.gap) {
            activePath.remove(keyId)
            return primary
        }
        val popupIds = buildList {
            addAll(key.longPressKeyIds)
            addAll(key.multiTapKeyIds)
            key.flickId?.let { flickId ->
                layout.flicks[flickId]?.segments?.forEach { add(it.keyId) }
            }
        }.distinct()
        if (popupIds.isEmpty()) {
            activePath.remove(keyId)
            return primary
        }
        val mainId = key.longPressDefaultKeyId
        val main = mainId?.let { keyDataFor(layout, it, activePath) }
        val relevant = popupIds.filter { it != mainId }.map { keyDataFor(layout, it, activePath) }
        val withPopup = when (primary) {
            is TextKeyData -> primary.copy(popup = PopupSet(main = main, relevant = relevant))
            is MultiTextKeyData -> MultiTextKeyData(
                type = primary.type,
                codePoints = primary.codePoints,
                label = primary.label,
                groupId = primary.groupId,
                popup = PopupSet(main = main, relevant = relevant),
            )
            else -> primary
        }
        activePath.remove(keyId)
        return withPopup
    }

    private fun textKey(output: String, label: String, popup: PopupSet<AbstractKeyData>?): AbstractKeyData {
        val codePoints = output.codePointsCompat()
        return if (codePoints.size == 1) {
            TextKeyData(type = KeyType.CHARACTER, code = codePoints[0], label = label, popup = popup)
        } else {
            MultiTextKeyData(type = KeyType.CHARACTER, codePoints = codePoints, label = label, popup = popup)
        }
    }

    private fun displayFor(layout: Keyboard3Layout, key: Keyboard3Key): String {
        return layout.displays.firstOrNull { display ->
            display.keyId == key.id || display.output == key.output
        }?.display ?: key.output.orEmpty()
    }

    private fun directionFor(locale: String): String {
        return if (Locale.forLanguageTag(locale).language.lowercase(Locale.ROOT) in RtlLanguages) "rtl" else "ltr"
    }

    private fun uniqueComponentId(candidate: String, used: MutableSet<String>): String {
        var value = candidate.ifBlank { "layer" }
        var suffix = 2
        while (!used.add(value)) {
            value = "${candidate}_$suffix"
            suffix++
        }
        return value
    }

    private fun slug(value: String): String {
        val result = value.lowercase(Locale.ROOT).map { char ->
            if (char in 'a'..'z' || char in '0'..'9') char else '_'
        }.joinToString("").trim('_')
        return if (result.firstOrNull()?.isDigit() == true) "x_$result" else result.ifBlank { "layout" }
    }

    private fun digest(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }
    }

    private fun String.codePointsCompat(): IntArray {
        val points = ArrayList<Int>()
        var index = 0
        while (index < length) {
            val point = codePointAt(index)
            points += point
            index += Character.charCount(point)
        }
        return points.toIntArray()
    }

    private class CompileAbort(val diagnostic: Keyboard3Diagnostic) : RuntimeException()

    private fun abort(code: Keyboard3DiagnosticCode, path: String, message: String): Nothing {
        throw CompileAbort(Keyboard3Diagnostic(code, path, message))
    }
}
