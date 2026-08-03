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

import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangement
import dev.patrickgold.florisboard.ime.text.keyboard.MultiTextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.lib.ext.ExtensionJsonConfig
import dev.patrickgold.florisboard.lib.ext.ExtensionPackagePolicy
import dev.patrickgold.florisboard.lib.io.DefaultJsonConfig
import dev.patrickgold.florisboard.lib.io.loadJsonAsset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Files

class Keyboard3Test : FunSpec({
    test("parses the Keyboard3 layout surface and compiles it to a local extension") {
        val parsed = Keyboard3Parser.parse(FIXTURE)

        parsed.isSuccess shouldBe true
        val layout = parsed.getOrThrow()
        layout.locale shouldBe "en"
        layout.additionalLocales shouldContainExactly listOf("en-US")
        layout.keys.keys shouldContain "custom"
        layout.forms.keys shouldContain "us"
        layout.flicks["accent"]!!.segments.single().keyId shouldBe "acute"
        layout.transforms.single().transforms.single().to shouldBe "the"

        val compiled = Keyboard3Compiler.compileXml(FIXTURE)
        compiled.isSuccess shouldBe true
        val extension = compiled.extension!!
        extension.meta.title shouldBe "Fixture Keyboard"
        extension.subtypePresets.single().locale.languageTag() shouldBe "en"
        compiled.arrangements.keys.size shouldBe 1
        val custom = compiled.arrangements.values.single().first()[1] as TextKeyData
        custom.label shouldBe "É"
        custom.popup!!.main!!.asString(true) shouldBe "á"

        val encoded = DefaultJsonConfig.encodeToString<LayoutArrangement>(compiled.arrangements.values.single())
        val decoded = DefaultJsonConfig.decodeFromString<LayoutArrangement>(encoded)
        decoded.size shouldBe 1
        (decoded.first()[1] as TextKeyData).label shouldBe "É"
    }

    test("retains multi-code-point Keyboard3 output in the compiled arrangement") {
        val source = FIXTURE.replace("output=\"é\"", "output=\"\\u{0065 0301}\"")
        val compiled = Keyboard3Compiler.compileXml(source)

        compiled.isSuccess shouldBe true
        val key = compiled.arrangements.values.single().first()[1] as MultiTextKeyData
        key.codePoints.toList() shouldBe listOf(0x65, 0x301)
    }

    test("round-trips a parsed UTS-shaped fixture through the canonical XML writer") {
        val original = Keyboard3Parser.parse(FIXTURE).getOrThrow()
        val rewritten = Keyboard3XmlWriter.write(original)
        val reparsed = Keyboard3Parser.parse(rewritten)

        reparsed.isSuccess shouldBe true
        reparsed.getOrThrow().keys.keys shouldBe original.keys.keys
        reparsed.getOrThrow().layerSets.single().layers.single().rows.single().keyIds shouldBe
            original.layerSets.single().layers.single().rows.single().keyIds
        reparsed.getOrThrow().transforms shouldBe original.transforms
    }

    test("writes a policy-valid local flex package with its source provenance") {
        val root = Files.createTempDirectory("keyboard3-package").toFile()
        try {
            val compiled = Keyboard3Compiler.compileXml(FIXTURE)
            Keyboard3Compiler.writePackage(compiled, root)
            val extension = loadJsonAsset<Extension>(
                root.resolve("extension.json").readText(),
                ExtensionJsonConfig,
            ).getOrThrow()

            extension.meta.title shouldBe "Fixture Keyboard"
            ExtensionPackagePolicy.validateExtracted(extension, root)
            root.resolve("keyboard3/source.xml").isFile shouldBe true
        } finally {
            root.deleteRecursively()
        }
    }

    test("rejects DTDs and external imports before compilation") {
        diagnostic("<!DOCTYPE keyboard3 [<!ENTITY x SYSTEM \"https://example.invalid/x\">]>$FIXTURE") shouldBe
            Keyboard3DiagnosticCode.DTD_FORBIDDEN
        diagnostic(FIXTURE.replace("45/scanCodes-implied.xml", "https://example.invalid/scanCodes.xml")) shouldBe
            Keyboard3DiagnosticCode.UNSAFE_IMPORT_PATH
        diagnostic(FIXTURE.replace("base=\"cldr\"", "base=\"https\"")) shouldBe
            Keyboard3DiagnosticCode.REMOTE_IMPORT
        diagnostic(FIXTURE.replace("45/keys-Latn-implied.xml", "45/keys.xml")) shouldBe
            Keyboard3DiagnosticCode.UNBUNDLED_CLDR_IMPORT
    }

    test("rejects traversal, local imports, duplicate imports, and duplicate ids") {
        diagnostic(FIXTURE.replace("45/keys-Latn-implied.xml", "45/../keys-Latn-implied.xml")) shouldBe
            Keyboard3DiagnosticCode.UNSAFE_IMPORT_PATH
        diagnostic(FIXTURE.replace("base=\"cldr\" path=\"45/scanCodes-implied.xml\"", "path=\"local.xml\"")) shouldBe
            Keyboard3DiagnosticCode.LOCAL_IMPORT_UNSUPPORTED
        diagnostic(FIXTURE.replace("<import base=\"cldr\" path=\"45/keys-Latn-implied.xml\"/>", "<import base=\"cldr\" path=\"45/keys-Latn-implied.xml\"/><import base=\"cldr\" path=\"45/keys-Latn-implied.xml\"/>") ) shouldBe
            Keyboard3DiagnosticCode.RECURSIVE_IMPORT
        diagnostic(FIXTURE.replace("<key id=\"custom\"", "<key id=\"q\"")) shouldBe
            Keyboard3DiagnosticCode.DUPLICATE_ID
    }

    test("reports unsupported Keyboard3 settings and reorder transforms precisely") {
        diagnostic(FIXTURE.replace("<layers formId=\"touch\">", "<settings reorder=\"true\"/><layers formId=\"touch\">")) shouldBe
            Keyboard3DiagnosticCode.UNSUPPORTED_SETTING
        diagnostic(FIXTURE.replace("<transforms type=\"simple\"><transformGroup>", "<transforms type=\"simple\"><transformGroup><reorder/>")) shouldBe
            Keyboard3DiagnosticCode.REORDER_UNSUPPORTED
        diagnostic(FIXTURE.replace("<keyboard3", "<notKeyboard3").replace("</keyboard3>", "</notKeyboard3>")) shouldBe
            Keyboard3DiagnosticCode.ROOT_NOT_KEYBOARD3
    }

    test("enforces the bounded source and conformance gates") {
        Keyboard3Parser.parse("<keyboard3" + "x".repeat(Keyboard3Parser.MaxSourceBytes) + ">").diagnostics.single().code shouldBe
            Keyboard3DiagnosticCode.SOURCE_TOO_LARGE
        diagnostic(FIXTURE.replace("conformsTo=\"45\"", "conformsTo=\"44\"")) shouldBe
            Keyboard3DiagnosticCode.UNSUPPORTED_CONFORMANCE
    }
}) {
    companion object {
        private fun diagnostic(source: String): Keyboard3DiagnosticCode {
            return Keyboard3Parser.parse(source).diagnostics.single().code
        }

        private val FIXTURE = """
            <keyboard3 xmlns="urn:unicode:ldml:keyboard3" conformsTo="45" locale="en">
              <info name="Fixture Keyboard" author="SwiftFloris" layout="Fixture import"/>
              <version number="1.0.0"/>
              <locales><locale id="en-US"/></locales>
              <displays><display keyId="custom" display="É"/></displays>
              <keys>
                <import base="cldr" path="45/keys-Latn-implied.xml"/>
                <key id="custom" output="é" flickId="accent" longPressKeyIds="acute" longPressDefaultKeyId="acute"/>
                <key id="acute" output="á"/>
                <key id="gap" gap="true"/>
                <key id="space" output="\u{20}"/>
              </keys>
              <flicks>
                <flick id="accent"><flickSegment directions="n" keyId="acute"/></flick>
              </flicks>
              <forms><import base="cldr" path="45/scanCodes-implied.xml"/></forms>
              <layers formId="touch"><layer id="base"><row keys="q custom gap space"/></layer></layers>
              <transforms type="simple"><transformGroup><transform from="teh" to="the"/></transformGroup></transforms>
            </keyboard3>
        """.trimIndent()
    }
}
