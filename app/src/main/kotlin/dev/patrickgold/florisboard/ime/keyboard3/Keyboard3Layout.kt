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

import java.io.StringReader
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** A stable, user-facing reason for rejecting a Keyboard3 source file. */
enum class Keyboard3DiagnosticCode {
    SOURCE_TOO_LARGE,
    DTD_FORBIDDEN,
    MALFORMED_XML,
    ROOT_NOT_KEYBOARD3,
    UNSUPPORTED_CONFORMANCE,
    MISSING_REQUIRED_ATTRIBUTE,
    INVALID_ATTRIBUTE,
    INVALID_ID,
    DUPLICATE_ID,
    UNSUPPORTED_ELEMENT,
    UNSUPPORTED_SETTING,
    REMOTE_IMPORT,
    LOCAL_IMPORT_UNSUPPORTED,
    UNSAFE_IMPORT_PATH,
    UNBUNDLED_CLDR_IMPORT,
    RECURSIVE_IMPORT,
    INVALID_REFERENCE,
    REORDER_UNSUPPORTED,
    UNSUPPORTED_TRANSFORM_TYPE,
    UNSUPPORTED_RUNTIME_FEATURE,
    OVER_BUDGET,
}

data class Keyboard3Diagnostic(
    val code: Keyboard3DiagnosticCode,
    val path: String,
    val message: String,
)

data class Keyboard3ParseResult(
    val layout: Keyboard3Layout?,
    val diagnostics: List<Keyboard3Diagnostic>,
) {
    val isSuccess: Boolean get() = layout != null && diagnostics.isEmpty()

    fun getOrThrow(): Keyboard3Layout {
        return layout ?: error(diagnostics.joinToString { "${it.code} at ${it.path}: ${it.message}" })
    }
}

data class Keyboard3Layout(
    val locale: String,
    val additionalLocales: List<String>,
    val conformsTo: Int,
    val version: String?,
    val name: String,
    val author: String?,
    val layoutHint: String?,
    val indicator: String?,
    val normalizationDisabled: Boolean,
    val variables: Keyboard3Variables,
    val displays: List<Keyboard3Display>,
    val keys: Map<String, Keyboard3Key>,
    val flicks: Map<String, Keyboard3Flick>,
    val forms: Map<String, Keyboard3Form>,
    val layerSets: List<Keyboard3LayerSet>,
    val transforms: List<Keyboard3TransformGroup>,
)

data class Keyboard3Variables(
    val strings: Map<String, String> = emptyMap(),
    val sets: Map<String, List<String>> = emptyMap(),
    val unicodeSets: Map<String, String> = emptyMap(),
)

data class Keyboard3Display(
    val output: String?,
    val keyId: String?,
    val display: String,
)

data class Keyboard3Key(
    val id: String,
    val output: String?,
    val flickId: String?,
    val gap: Boolean,
    val longPressKeyIds: List<String>,
    val longPressDefaultKeyId: String?,
    val multiTapKeyIds: List<String>,
    val stretch: Boolean,
    val layerId: String?,
    val width: Float?,
)

data class Keyboard3Flick(
    val id: String,
    val segments: List<Keyboard3FlickSegment>,
)

data class Keyboard3FlickSegment(
    val directions: List<String>,
    val keyId: String,
)

data class Keyboard3Form(
    val id: String,
    val scanCodeRows: List<List<Int>>,
)

data class Keyboard3LayerSet(
    val formId: String,
    val minDeviceWidth: Int?,
    val layers: List<Keyboard3Layer>,
)

data class Keyboard3Layer(
    val id: String?,
    val modifiers: String?,
    val rows: List<Keyboard3Row>,
)

data class Keyboard3Row(
    val keyIds: List<String>,
)

data class Keyboard3TransformGroup(
    val type: String,
    val transforms: List<Keyboard3Transform>,
)

data class Keyboard3Transform(
    val from: String,
    val to: String?,
)

/**
 * Parser for Unicode LDML Keyboard3 files.
 *
 * This parser deliberately resolves only the small CLDR import catalog that is
 * bundled in the app. It never reads a path, URI, network resource, DTD, or
 * external entity from an imported file.
 */
object Keyboard3Parser {
    const val MaxSourceBytes = 2 * 1024 * 1024
    const val MaxElementCount = 8_192
    const val MaxKeys = 4_096
    const val MaxRows = 512
    const val MaxTransforms = 4_096
    const val MinSupportedConformsTo = 45
    const val MaxSupportedConformsTo = 48

    private val SafeId = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]*$")
    private val SafeLayerId = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")
    private val Directions = setOf("n", "e", "s", "w", "ne", "nw", "se", "sw")
    private val SupportedCldrImports = setOf(
        "keys-Latn-implied.xml",
        "scanCodes-implied.xml",
    )

    fun parse(xml: String): Keyboard3ParseResult {
        val bytes = xml.toByteArray(Charsets.UTF_8)
        if (bytes.size > MaxSourceBytes) {
            return failure(
                Keyboard3DiagnosticCode.SOURCE_TOO_LARGE,
                "source",
                "Keyboard3 source exceeds the ${MaxSourceBytes / 1024} KiB limit.",
            )
        }
        if (DOCTYPE_REGEX.containsMatchIn(xml)) {
            return failure(
                Keyboard3DiagnosticCode.DTD_FORBIDDEN,
                "source",
                "DTD and external entities are not accepted in local Keyboard3 addons.",
            )
        }
        return try {
            Keyboard3ParseResult(parseInternal(xml), emptyList())
        } catch (abort: ParseAbort) {
            Keyboard3ParseResult(null, listOf(abort.diagnostic))
        } catch (_: Throwable) {
            failure(
                Keyboard3DiagnosticCode.MALFORMED_XML,
                "source",
                "Keyboard3 source is not a readable XML document.",
            )
        }
    }

    private fun parseInternal(xml: String): Keyboard3Layout {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val elementCount = countElements(document.documentElement)
        if (elementCount > MaxElementCount) {
            fail(
                Keyboard3DiagnosticCode.OVER_BUDGET,
                "source",
                "Keyboard3 source contains more than $MaxElementCount XML elements.",
            )
        }

        val root = document.documentElement ?: fail(
            Keyboard3DiagnosticCode.ROOT_NOT_KEYBOARD3,
            "source",
            "Keyboard3 source has no document element.",
        )
        if (root.localNameOrTagName() != "keyboard3") {
            fail(
                Keyboard3DiagnosticCode.ROOT_NOT_KEYBOARD3,
                "source",
                "Expected a <keyboard3> root element.",
            )
        }

        val conformsTo = root.requiredAttribute("conformsTo", "keyboard3")
            .toIntOrNull()
            ?: fail(
                Keyboard3DiagnosticCode.INVALID_ATTRIBUTE,
                "keyboard3@conformsTo",
                "conformsTo must be a whole number.",
            )
        if (conformsTo !in MinSupportedConformsTo..MaxSupportedConformsTo) {
            fail(
                Keyboard3DiagnosticCode.UNSUPPORTED_CONFORMANCE,
                "keyboard3@conformsTo",
                "SwiftFloris supports CLDR Keyboard3 conformance $MinSupportedConformsTo through $MaxSupportedConformsTo.",
            )
        }
        val locale = root.requiredAttribute("locale", "keyboard3")
        if (!isLanguageTag(locale)) {
            fail(
                Keyboard3DiagnosticCode.INVALID_ATTRIBUTE,
                "keyboard3@locale",
                "locale must be a BCP 47 language tag.",
            )
        }

        val allowedRootChildren = setOf(
            "info", "version", "settings", "locales", "variables", "displays",
            "keys", "flicks", "forms", "layers", "transforms",
        )
        root.elementChildren().forEachIndexed { index, child ->
            if (child.localNameOrTagName() !in allowedRootChildren) {
                fail(
                    Keyboard3DiagnosticCode.UNSUPPORTED_ELEMENT,
                    "keyboard3/${child.localNameOrTagName()}[$index]",
                    "Unsupported keyboard3 child '${child.localNameOrTagName()}'.",
                )
            }
        }

        val seenImports = linkedSetOf<String>()
        val importedKeys = linkedMapOf<String, Keyboard3Key>()
        val importedForms = linkedMapOf<String, Keyboard3Form>()
        root.descendants("import").forEachIndexed { index, import ->
            val path = "keyboard3/import[$index]"
            val parentName = import.parentNode?.let { (it as? Element)?.localNameOrTagName() }.orEmpty()
            val resolved = resolveImport(import, parentName, conformsTo, seenImports, path)
            when (resolved) {
                BundledImport.ImpliedKeys -> importedKeys.putAll(impliedKeys())
                BundledImport.ImpliedForms -> importedForms.putAll(impliedForms())
            }
        }

        val info = root.directChildren("info").singleOrNull() ?: fail(
            Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE,
            "keyboard3/info",
            "Keyboard3 requires exactly one <info> element.",
        )
        val name = info.requiredAttribute("name", "keyboard3/info")
        val version = root.directChildren("version").singleOrNull()?.let {
            it.requiredAttribute("number", "keyboard3/version")
        }
        val settings = root.directChildren("settings").singleOrNull()
        val normalizationDisabled = settings?.let { parseSettings(it) } ?: false
        val additionalLocales = root.directChildren("locales").singleOrNull()
            ?.directChildren("locale")
            ?.mapIndexed { index, element -> element.requiredAttribute("id", "keyboard3/locales/locale[$index]") }
            .orEmpty()

        val variables = parseVariables(root.directChildren("variables").singleOrNull())
        val displays = parseDisplays(root.directChildren("displays").singleOrNull())
        val keys = parseKeys(root.directChildren("keys").singleOrNull(), importedKeys)
        val flicks = parseFlicks(root.directChildren("flicks").singleOrNull())
        val forms = parseForms(root.directChildren("forms").singleOrNull(), importedForms)
        val layerSets = parseLayerSets(root.directChildren("layers"))
        val transforms = parseTransforms(root.directChildren("transforms"))

        validateReferences(keys, flicks, forms, layerSets, displays)
        if (layerSets.isEmpty()) {
            fail(
                Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE,
                "keyboard3/layers",
                "Keyboard3 requires at least one <layers> element.",
            )
        }

        return Keyboard3Layout(
            locale = locale,
            additionalLocales = additionalLocales,
            conformsTo = conformsTo,
            version = version,
            name = name,
            author = info.attributeOrNull("author"),
            layoutHint = info.attributeOrNull("layout"),
            indicator = info.attributeOrNull("indicator"),
            normalizationDisabled = normalizationDisabled,
            variables = variables,
            displays = displays,
            keys = keys,
            flicks = flicks,
            forms = forms,
            layerSets = layerSets,
            transforms = transforms,
        )
    }

    private fun parseSettings(element: Element): Boolean {
        val normalization = element.attributeOrNull("normalization")
        val unknown = element.attributeNames() - setOf("normalization")
        if (unknown.isNotEmpty()) {
            fail(
                Keyboard3DiagnosticCode.UNSUPPORTED_SETTING,
                "keyboard3/settings",
                "Unsupported Keyboard3 setting '${unknown.first()}'.",
            )
        }
        if (normalization != null && normalization != "disabled") {
            fail(
                Keyboard3DiagnosticCode.UNSUPPORTED_SETTING,
                "keyboard3/settings@normalization",
                "Only normalization=\"disabled\" is defined by Keyboard3.",
            )
        }
        return normalization == "disabled"
    }

    private fun parseVariables(element: Element?): Keyboard3Variables {
        if (element == null) return Keyboard3Variables()
        val ids = mutableSetOf<String>()
        val strings = linkedMapOf<String, String>()
        val sets = linkedMapOf<String, List<String>>()
        val unicodeSets = linkedMapOf<String, String>()
        for ((index, child) in element.elementChildren().withIndex()) {
            val path = "keyboard3/variables/${child.localNameOrTagName()}[$index]"
            val id = child.requiredAttribute("id", path)
            if (!ids.add(id)) fail(Keyboard3DiagnosticCode.DUPLICATE_ID, path, "Variable id '$id' is declared more than once.")
            val value = child.requiredAttribute("value", path)
            when (child.localNameOrTagName()) {
                "string" -> strings[id] = decodeText(value, "$path@value")
                "set" -> sets[id] = value.trim().split(Regex("\\s+")).filter(String::isNotEmpty).map {
                    decodeText(it, "$path@value")
                }
                "uset" -> unicodeSets[id] = value
                else -> fail(
                    Keyboard3DiagnosticCode.UNSUPPORTED_ELEMENT,
                    path,
                    "Unsupported variables child '${child.localNameOrTagName()}'.",
                )
            }
        }
        return Keyboard3Variables(strings, sets, unicodeSets)
    }

    private fun parseDisplays(element: Element?): List<Keyboard3Display> {
        if (element == null) return emptyList()
        val displays = mutableListOf<Keyboard3Display>()
        val seenTargets = mutableSetOf<String>()
        for ((index, child) in element.elementChildren().withIndex()) {
            val path = "keyboard3/displays/${child.localNameOrTagName()}[$index]"
            when (child.localNameOrTagName()) {
                "display" -> {
                    val output = child.attributeOrNull("output")?.let { decodeText(it, "$path@output") }
                    val keyId = child.attributeOrNull("keyId")
                        ?: child.attributeOrNull("id")
                    if (output == null && keyId == null) {
                        fail(Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE, path, "A display needs output or keyId.")
                    }
                    val display = decodeText(child.requiredAttribute("display", path), "$path@display")
                    val target = "${keyId.orEmpty()}|${output.orEmpty()}"
                    if (!seenTargets.add(target)) {
                        fail(Keyboard3DiagnosticCode.DUPLICATE_ID, path, "Display target '$target' is declared more than once.")
                    }
                    displays += Keyboard3Display(output, keyId, display)
                }
                "displayOptions" -> {
                    // The current compiler uses the standard dotted-circle fallback.
                    // Keep the option rejected rather than silently changing keytops.
                    fail(
                        Keyboard3DiagnosticCode.UNSUPPORTED_SETTING,
                        path,
                        "Keyboard3 displayOptions are not supported by the current key renderer.",
                    )
                }
                "import" -> Unit
                else -> fail(
                    Keyboard3DiagnosticCode.UNSUPPORTED_ELEMENT,
                    path,
                    "Unsupported displays child '${child.localNameOrTagName()}'.",
                )
            }
        }
        return displays
    }

    private fun parseKeys(element: Element?, imported: Map<String, Keyboard3Key>): Map<String, Keyboard3Key> {
        if (element == null) return imported
        val keys = linkedMapOf<String, Keyboard3Key>().apply { putAll(imported) }
        val localIds = mutableSetOf<String>()
        var count = 0
        for ((index, child) in element.elementChildren().withIndex()) {
            val path = "keyboard3/keys/${child.localNameOrTagName()}[$index]"
            if (child.localNameOrTagName() == "import") continue
            if (child.localNameOrTagName() != "key") {
                fail(Keyboard3DiagnosticCode.UNSUPPORTED_ELEMENT, path, "Unsupported keys child '${child.localNameOrTagName()}'.")
            }
            if (++count > MaxKeys) fail(Keyboard3DiagnosticCode.OVER_BUDGET, "keyboard3/keys", "Keyboard3 declares more than $MaxKeys keys.")
            val id = child.requiredAttribute("id", path)
            validateId(id, path)
            if (!localIds.add(id) || keys.containsKey(id)) {
                fail(Keyboard3DiagnosticCode.DUPLICATE_ID, path, "Key id '$id' is declared more than once.")
            }
            val output = child.attributeOrNull("output")?.let { decodeText(it, "$path@output") }
            val layerId = child.attributeOrNull("layerId")
            val gap = child.attributeOrNull("gap")?.let { parseBoolean(it, "$path@gap") } ?: false
            if (output == null && layerId == null && !gap) {
                fail(Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE, path, "A key needs output, layerId, or gap=true.")
            }
            if (gap && (output != null || layerId != null || child.attributeOrNull("flickId") != null)) {
                fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, path, "A gap key cannot also emit output, switch layers, or flick.")
            }
            val longPress = child.attributeOrNull("longPressKeyIds").orEmpty().splitTokens()
            val longPressDefault = child.attributeOrNull("longPressDefaultKeyId")
            if (longPressDefault != null && longPressDefault !in longPress) {
                fail(Keyboard3DiagnosticCode.INVALID_REFERENCE, path, "longPressDefaultKeyId '$longPressDefault' is not in longPressKeyIds.")
            }
            val width = child.attributeOrNull("width")?.let {
                it.toFloatOrNull()?.takeIf { value -> value.isFinite() && value > 0f && value <= 16f }
                    ?: fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, "$path@width", "width must be greater than 0 and at most 16.")
            }
            keys[id] = Keyboard3Key(
                id = id,
                output = output,
                flickId = child.attributeOrNull("flickId"),
                gap = gap,
                longPressKeyIds = longPress,
                longPressDefaultKeyId = longPressDefault,
                multiTapKeyIds = child.attributeOrNull("multiTapKeyIds").orEmpty().splitTokens(),
                stretch = child.attributeOrNull("stretch")?.let { parseBoolean(it, "$path@stretch") } ?: false,
                layerId = layerId,
                width = width,
            )
        }
        return keys
    }

    private fun parseFlicks(element: Element?): Map<String, Keyboard3Flick> {
        if (element == null) return emptyMap()
        val flicks = linkedMapOf<String, Keyboard3Flick>()
        for ((index, child) in element.elementChildren().withIndex()) {
            if (child.localNameOrTagName() == "import") continue
            if (child.localNameOrTagName() != "flick") {
                fail(
                    Keyboard3DiagnosticCode.UNSUPPORTED_ELEMENT,
                    "keyboard3/flicks/${child.localNameOrTagName()}[$index]",
                    "Unsupported flicks child '${child.localNameOrTagName()}'.",
                )
            }
            val flickElement = child
            val path = "keyboard3/flicks/flick[$index]"
            val id = flickElement.requiredAttribute("id", path)
            validateId(id, path)
            if (flicks.containsKey(id)) fail(Keyboard3DiagnosticCode.DUPLICATE_ID, path, "Flick id '$id' is declared more than once.")
            val segments = flickElement.directChildren("flickSegment").mapIndexed { segmentIndex, segment ->
                val segmentPath = "$path/flickSegment[$segmentIndex]"
                val directions = segment.requiredAttribute("directions", segmentPath).splitTokens()
                if (directions.isEmpty() || directions.any { it !in Directions }) {
                    fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, "$segmentPath@directions", "directions must contain only n, e, s, w, ne, nw, se, or sw.")
                }
                Keyboard3FlickSegment(directions, segment.requiredAttribute("keyId", segmentPath))
            }
            if (segments.isEmpty()) fail(Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE, path, "A flick needs at least one flickSegment.")
            flicks[id] = Keyboard3Flick(id, segments)
        }
        return flicks
    }

    private fun parseForms(element: Element?, imported: Map<String, Keyboard3Form>): Map<String, Keyboard3Form> {
        if (element == null) return imported
        val forms = linkedMapOf<String, Keyboard3Form>().apply { putAll(imported) }
        val localIds = mutableSetOf<String>()
        for ((index, form) in element.directChildren("form").withIndex()) {
            val path = "keyboard3/forms/form[$index]"
            val id = form.requiredAttribute("id", path)
            if (id == "touch") fail(Keyboard3DiagnosticCode.INVALID_ID, path, "The touch form is implied and cannot be declared explicitly.")
            if (!SafeLayerId.matches(id)) fail(Keyboard3DiagnosticCode.INVALID_ID, path, "Form id '$id' is not safe.")
            if (!localIds.add(id) || forms.containsKey(id)) {
                fail(Keyboard3DiagnosticCode.DUPLICATE_ID, path, "Form id '$id' is declared more than once.")
            }
            val rows = form.directChildren("scanCodes").mapIndexed { rowIndex, scanCodes ->
                val rowPath = "$path/scanCodes[$rowIndex]"
                val values = scanCodes.requiredAttribute("codes", rowPath).splitTokens().map {
                    it.toIntOrNull(16)?.takeIf { code -> code in 0..255 }
                        ?: fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, "$rowPath@codes", "Scan codes must be two-digit hexadecimal bytes.")
                }
                if (values.isEmpty()) fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, rowPath, "A scanCodes row cannot be empty.")
                values
            }
            forms[id] = Keyboard3Form(id, rows)
        }
        return forms
    }

    private fun parseLayerSets(elements: List<Element>): List<Keyboard3LayerSet> {
        val layerSets = mutableListOf<Keyboard3LayerSet>()
        val seenWidths = mutableSetOf<String>()
        var rowCount = 0
        for ((index, set) in elements.withIndex()) {
            val path = "keyboard3/layers[$index]"
            val formId = set.requiredAttribute("formId", path)
            val minDeviceWidth = set.attributeOrNull("minDeviceWidth")?.let {
                it.toIntOrNull()?.takeIf { width -> width in 1..999 }
                    ?: fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, "$path@minDeviceWidth", "minDeviceWidth must be a whole number from 1 through 999.")
            }
            val widthKey = "$formId|${minDeviceWidth ?: 0}"
            if (!seenWidths.add(widthKey)) fail(Keyboard3DiagnosticCode.DUPLICATE_ID, path, "Layer set '$widthKey' is declared more than once.")
            val layerIds = mutableSetOf<String>()
            set.elementChildren().forEachIndexed { childIndex, child ->
                if (child.localNameOrTagName() != "layer") {
                    fail(
                        Keyboard3DiagnosticCode.UNSUPPORTED_ELEMENT,
                        "$path/${child.localNameOrTagName()}[$childIndex]",
                        "Unsupported layers child '${child.localNameOrTagName()}'.",
                    )
                }
            }
            val layers = set.directChildren("layer").mapIndexed { layerIndex, layer ->
                val layerPath = "$path/layer[$layerIndex]"
                val id = layer.attributeOrNull("id")
                if (id != null && !SafeLayerId.matches(id)) fail(Keyboard3DiagnosticCode.INVALID_ID, layerPath, "Layer id '$id' is not safe.")
                if (id != null && !layerIds.add(id)) fail(Keyboard3DiagnosticCode.DUPLICATE_ID, layerPath, "Layer id '$id' is declared more than once.")
                if (formId == "touch" && id == null) fail(Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE, layerPath, "Touch layers require an id.")
                if (formId != "touch" && layer.attributeOrNull("modifiers").isNullOrBlank()) {
                    fail(Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE, layerPath, "Hardware layers require modifiers.")
                }
                val rows = layer.directChildren("row").mapIndexed { rowIndex, row ->
                    val rowPath = "$layerPath/row[$rowIndex]"
                    val keyIds = row.requiredAttribute("keys", rowPath).splitTokens()
                    if (keyIds.isEmpty()) fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, rowPath, "A row cannot be empty.")
                    rowCount++
                    if (rowCount > MaxRows) fail(Keyboard3DiagnosticCode.OVER_BUDGET, "keyboard3/layers", "Keyboard3 declares more than $MaxRows rows.")
                    Keyboard3Row(keyIds)
                }
                if (rows.isEmpty()) fail(Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE, layerPath, "A layer needs at least one row.")
                Keyboard3Layer(id, layer.attributeOrNull("modifiers"), rows)
            }
            if (layers.isEmpty()) fail(Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE, path, "A layer set needs at least one layer.")
            layerSets += Keyboard3LayerSet(formId, minDeviceWidth, layers)
        }
        return layerSets
    }

    private fun parseTransforms(elements: List<Element>): List<Keyboard3TransformGroup> {
        if (elements.isEmpty()) return emptyList()
        val groups = mutableListOf<Keyboard3TransformGroup>()
        val types = mutableSetOf<String>()
        var transformCount = 0
        for ((index, transforms) in elements.withIndex()) {
            val path = "keyboard3/transforms[$index]"
            val type = transforms.requiredAttribute("type", path).lowercase(Locale.ROOT)
            if (type !in setOf("simple", "backspace")) {
                fail(Keyboard3DiagnosticCode.UNSUPPORTED_TRANSFORM_TYPE, "$path@type", "Transform type '$type' is not supported.")
            }
            if (!types.add(type)) fail(Keyboard3DiagnosticCode.DUPLICATE_ID, path, "Only one transforms element per type is allowed.")
            for ((groupIndex, group) in transforms.directChildren("transformGroup").withIndex()) {
                val groupPath = "$path/transformGroup[$groupIndex]"
                if (group.directChildren("reorder").isNotEmpty()) {
                    fail(Keyboard3DiagnosticCode.REORDER_UNSUPPORTED, groupPath, "Keyboard3 reorder transforms require a dedicated reorder engine.")
                }
                val rules = group.directChildren("transform").mapIndexed { ruleIndex, transform ->
                    val rulePath = "$groupPath/transform[$ruleIndex]"
                    transformCount++
                    if (transformCount > MaxTransforms) fail(Keyboard3DiagnosticCode.OVER_BUDGET, "keyboard3/transforms", "Keyboard3 declares more than $MaxTransforms transforms.")
                    Keyboard3Transform(
                        from = transform.requiredAttribute("from", rulePath),
                        to = transform.attributeOrNull("to"),
                    )
                }
                if (rules.isEmpty()) fail(Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE, groupPath, "A transform group cannot be empty.")
                groups += Keyboard3TransformGroup(type, rules)
            }
        }
        return groups
    }

    private fun validateReferences(
        keys: Map<String, Keyboard3Key>,
        flicks: Map<String, Keyboard3Flick>,
        forms: Map<String, Keyboard3Form>,
        layerSets: List<Keyboard3LayerSet>,
        displays: List<Keyboard3Display>,
    ) {
        for ((id, key) in keys) {
            key.flickId?.takeIf { it !in flicks }?.let {
                fail(Keyboard3DiagnosticCode.INVALID_REFERENCE, "keyboard3/keys/key[$id]@flickId", "Flick id '$it' does not exist.")
            }
            for (ref in key.longPressKeyIds + key.multiTapKeyIds) {
                if (ref !in keys) fail(Keyboard3DiagnosticCode.INVALID_REFERENCE, "keyboard3/keys/key[$id]", "Key id '$ref' does not exist.")
            }
        }
        for ((flickId, flick) in flicks) {
            for ((segmentIndex, segment) in flick.segments.withIndex()) {
                if (segment.keyId !in keys) fail(Keyboard3DiagnosticCode.INVALID_REFERENCE, "keyboard3/flicks/flick[$flickId]/flickSegment[$segmentIndex]", "Key id '${segment.keyId}' does not exist.")
            }
        }
        for ((setIndex, layerSet) in layerSets.withIndex()) {
            if (layerSet.formId != "touch" && layerSet.formId !in forms) {
                fail(Keyboard3DiagnosticCode.INVALID_REFERENCE, "keyboard3/layers[$setIndex]@formId", "Form id '${layerSet.formId}' does not exist.")
            }
            for ((layerIndex, layer) in layerSet.layers.withIndex()) {
                for ((rowIndex, row) in layer.rows.withIndex()) {
                    row.keyIds.forEachIndexed { keyIndex, keyId ->
                        if (keyId !in keys) fail(Keyboard3DiagnosticCode.INVALID_REFERENCE, "keyboard3/layers[$setIndex]/layer[$layerIndex]/row[$rowIndex]/key[$keyIndex]", "Key id '$keyId' does not exist.")
                    }
                }
            }
        }
        for ((index, display) in displays.withIndex()) {
            display.keyId?.takeIf { it !in keys }?.let {
                fail(Keyboard3DiagnosticCode.INVALID_REFERENCE, "keyboard3/displays/display[$index]@keyId", "Key id '$it' does not exist.")
            }
        }
    }

    private fun resolveImport(
        import: Element,
        parentName: String,
        conformsTo: Int,
        seenImports: MutableSet<String>,
        path: String,
    ): BundledImport {
        val base = import.attributeOrNull("base")
            ?: fail(Keyboard3DiagnosticCode.LOCAL_IMPORT_UNSUPPORTED, path, "Local Keyboard3 imports are not accepted; use a bundled versioned CLDR import.")
        if (base.lowercase(Locale.ROOT) != "cldr") {
            fail(Keyboard3DiagnosticCode.REMOTE_IMPORT, "$path@base", "Only the bundled CLDR import base is accepted.")
        }
        val importPath = import.requiredAttribute("path", path)
        if (importPath.startsWith('/') || '\\' in importPath || ':' in importPath || importPath.split('/').any { it == ".." || it.isBlank() }) {
            fail(Keyboard3DiagnosticCode.UNSAFE_IMPORT_PATH, "$path@path", "CLDR import paths must be relative and traversal-free.")
        }
        val segments = importPath.split('/')
        val major = segments.firstOrNull()?.toIntOrNull()
        val leaf = segments.drop(1).singleOrNull()
        if (major == null || leaf == null || major !in MinSupportedConformsTo..MaxSupportedConformsTo || major > conformsTo) {
            fail(Keyboard3DiagnosticCode.UNBUNDLED_CLDR_IMPORT, "$path@path", "CLDR import '$importPath' is not in the bundled versioned catalog.")
        }
        if (!SupportedCldrImports.contains(leaf)) {
            fail(Keyboard3DiagnosticCode.UNBUNDLED_CLDR_IMPORT, "$path@path", "CLDR import '$importPath' is not in the bundled versioned catalog.")
        }
        if (!seenImports.add(importPath)) {
            fail(Keyboard3DiagnosticCode.RECURSIVE_IMPORT, "$path@path", "CLDR import '$importPath' was requested more than once.")
        }
        return when {
            leaf == "keys-Latn-implied.xml" && parentName == "keys" -> BundledImport.ImpliedKeys
            leaf == "scanCodes-implied.xml" && parentName == "forms" -> BundledImport.ImpliedForms
            else -> fail(Keyboard3DiagnosticCode.INVALID_REFERENCE, path, "CLDR import '$importPath' is not valid under <$parentName>.")
        }
    }

    private fun impliedKeys(): Map<String, Keyboard3Key> {
        val keys = linkedMapOf<String, Keyboard3Key>()
        for (char in ('0'..'9') + ('A'..'Z') + ('a'..'z')) {
            val id = char.toString()
            keys[id] = Keyboard3Key(id, id, null, false, emptyList(), null, emptyList(), false, null, null)
        }
        return keys
    }

    private fun impliedForms(): Map<String, Keyboard3Form> {
        // The exact scan-code rows are deliberately not duplicated here: the
        // app only uses touch layers today. These IDs are still needed to
        // validate hardware layer references without opening arbitrary files.
        return listOf("abnt2", "iso", "jis", "us", "ks")
            .associateWith { Keyboard3Form(it, emptyList()) }
    }

    private fun decodeText(value: String, path: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '\\') {
                if (index + 2 < value.length && value[index + 1] == 'u' && value[index + 2] == '{') {
                    val close = value.indexOf('}', index + 3)
                    if (close < 0) fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, path, "Unclosed Unicode escape.")
                    val points = value.substring(index + 3, close).trim().split(Regex("\\s+")).filter(String::isNotEmpty)
                    if (points.isEmpty()) fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, path, "Unicode escape contains no code points.")
                    for (hex in points) {
                        val codePoint = hex.toIntOrNull(16)?.takeIf { Character.isValidCodePoint(it) }
                            ?: fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, path, "Invalid Unicode code point escape.")
                        output.appendCodePoint(codePoint)
                    }
                    index = close + 1
                    continue
                }
                if (value.startsWith("\\m{", index)) {
                    val close = value.indexOf('}', index + 3)
                    if (close < 0) fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, path, "Unclosed marker escape.")
                    output.append(value, index, close + 1)
                    index = close + 1
                    continue
                }
                fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, path, "Unsupported escape sequence.")
            }
            output.append(value[index])
            index++
        }
        return output.toString()
    }

    private fun parseBoolean(value: String, path: String): Boolean {
        return when (value) {
            "true" -> true
            "false" -> false
            else -> fail(Keyboard3DiagnosticCode.INVALID_ATTRIBUTE, path, "Boolean attributes must be true or false.")
        }
    }

    private fun validateId(id: String, path: String) {
        if (!SafeId.matches(id)) fail(Keyboard3DiagnosticCode.INVALID_ID, path, "Id '$id' is not a safe Keyboard3 identifier.")
    }

    private fun isLanguageTag(value: String): Boolean {
        if (value == "und") return true
        if (!Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$").matches(value)) return false
        return Locale.forLanguageTag(value).language.isNotBlank()
    }

    private fun countElements(element: Element?): Int {
        if (element == null) return 0
        val pending = java.util.ArrayDeque<Element>()
        pending.addLast(element)
        var count = 0
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            count++
            current.elementChildren().forEach(pending::addLast)
        }
        return count
    }

    private fun Element.requiredAttribute(name: String, path: String): String {
        return attributeOrNull(name) ?: fail(Keyboard3DiagnosticCode.MISSING_REQUIRED_ATTRIBUTE, "$path@$name", "Required attribute '$name' is missing.")
    }

    private fun Element.attributeOrNull(name: String): String? {
        return getAttribute(name).takeIf { it.isNotBlank() }
    }

    private fun Element.attributeNames(): Set<String> {
        val attributes = attributes
        return buildSet {
            for (index in 0 until attributes.length) add(attributes.item(index).nodeName)
        }
    }

    private fun Element.localNameOrTagName(): String {
        return (localName ?: tagName).substringAfter(':')
    }

    private fun Element.elementChildren(): List<Element> {
        val elements = mutableListOf<Element>()
        for (index in 0 until childNodes.length) {
            val node = childNodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) elements += node as Element
        }
        return elements
    }

    private fun Element.directChildren(name: String): List<Element> {
        return elementChildren().filter { it.localNameOrTagName() == name }
    }

    private fun Element.descendants(name: String): List<Element> {
        val result = mutableListOf<Element>()
        fun visit(element: Element) {
            for (child in element.elementChildren()) {
                if (child.localNameOrTagName() == name) result += child
                visit(child)
            }
        }
        visit(this)
        return result
    }

    private fun String.splitTokens(): List<String> {
        return trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    }

    private fun failure(code: Keyboard3DiagnosticCode, path: String, message: String): Keyboard3ParseResult {
        return Keyboard3ParseResult(null, listOf(Keyboard3Diagnostic(code, path, message)))
    }

    private fun fail(code: Keyboard3DiagnosticCode, path: String, message: String): Nothing {
        throw ParseAbort(Keyboard3Diagnostic(code, path, message))
    }

    private class ParseAbort(val diagnostic: Keyboard3Diagnostic) : RuntimeException()

    private enum class BundledImport {
        ImpliedKeys,
        ImpliedForms,
    }

    private val DOCTYPE_REGEX = Regex("<!DOCTYPE", RegexOption.IGNORE_CASE)
}
