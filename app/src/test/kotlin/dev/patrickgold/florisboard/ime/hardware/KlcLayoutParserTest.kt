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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private val SAMPLE_KLC = """
KBD	"USDVORAK"	"United States-Dvorak"

COPYRIGHT	"(c) 2026 SwiftFloris"

COMPANY	"SwiftFloris"

LOCALENAME	"en-US"

LOCALEID	"00010409"

VERSION	1.0

SHIFTSTATE

0	//Column 4
1	//Column 5 : Shft
2	//Column 6 :       Ctrl
6	//Column 7 : Shft+ Ctrl

LAYOUT       ;an extra '@' at the end is a dead key

//SC	VK_		Cap	0	1	2	6
//--	----		----	----	----	----	----

02	1		0	0031	0021	-1	-1
03	2		0	0032	0040	-1	-1
10	OEM_3		1	0027	0022	-1	-1	;'  "
11	OEM_COMMA	1	002C	003C	-1	-1	;,  <
12	OEM_PERIOD	1	002E	003E	-1	-1	;.  >
1E	A		1	0061	0041	-1	-1
2D	X		1	0078	0058	-1	-1
31	N		1	006E	004E	-1	-1
1A	OEM_4		0	002F	003F	-1	-1	;/  ?

DEADKEY 0027

KEYNAME

ENDKBD
""".trimIndent()

class KlcLayoutParserTest : FunSpec({
    test("parses KLC header metadata") {
        val layout = KlcLayoutParser.parse(SAMPLE_KLC)
        layout.name shouldBe "United States-Dvorak"
        layout.locale shouldBe "en-US"
    }

    test("parses LAYOUT rows into the scancode map") {
        val layout = KlcLayoutParser.parse(SAMPLE_KLC)
        layout.scancodeMap shouldContainKey 0x1E  // 'A' scancode
        val a = layout.scancodeMap[0x1E].shouldNotBeNull()
        a.virtualKeyName shouldBe "A"
        a.normal shouldBe 0x0061
        a.shift shouldBe 0x0041
        a.capsLock shouldBe true
    }

    test("recognises non-alpha keys (digits, OEM punctuation)") {
        val layout = KlcLayoutParser.parse(SAMPLE_KLC)
        val one = layout.scancodeMap[0x02].shouldNotBeNull()
        one.virtualKeyName shouldBe "1"
        one.normal shouldBe 0x0031
        one.shift shouldBe 0x0021
        one.capsLock shouldBe false

        val slash = layout.scancodeMap[0x1A].shouldNotBeNull()
        slash.virtualKeyName shouldBe "OEM_4"
        slash.normal shouldBe 0x002F
        slash.shift shouldBe 0x003F
    }

    test("returns Empty when no LAYOUT rows present") {
        val empty = KlcLayoutParser.parse(
            """
            KBD	"NONE"	"None"
            LOCALENAME	"xx-XX"
            ENDKBD
            """.trimIndent()
        )
        empty shouldBe HardwareKeyboardLayout.Empty
        empty.isLoaded shouldBe false
    }

    test("dead-key trigger is captured when the slot is suffixed with @") {
        val klc = """
            KBD	"DEAD"	"Dead-test"
            LOCALENAME	"en-US"
            SHIFTSTATE
            0
            1
            LAYOUT
            ;SC VK CAP NORM SHIFT
            10	OEM_3	0	0060@	007E
            ENDKBD
        """.trimIndent()
        val layout = KlcLayoutParser.parse(klc)
        val entry = layout.scancodeMap[0x10].shouldNotBeNull()
        entry.normal shouldBe 0x0060
        entry.deadKeyTrigger shouldBe 0x0060
    }

    test("tolerates malformed rows without crashing") {
        val klc = """
            KBD	"BAD"	"Bad-rows"
            LOCALENAME	"en-US"
            LAYOUT
            not-a-valid-row
            zz	X	0	%	%
            ENDKBD
        """.trimIndent()
        // Should produce an Empty layout because neither row resolves.
        val layout = KlcLayoutParser.parse(klc)
        layout shouldBe HardwareKeyboardLayout.Empty
    }

    test("diagnostics report malformed LAYOUT rows") {
        val klc = """
            KBD	"MIX"	"Mixed-rows"
            LOCALENAME	"en-US"
            LAYOUT
            1E	A	1	0061	0041
            not-valid
            ENDKBD
        """.trimIndent()
        val result = KlcLayoutParser.parseWithDiagnostics(klc)
        result.layout.scancodeMap.size shouldBe 1
        result.diagnostics.skippedCount shouldBe 1
        result.diagnostics.hasSkipped shouldBe true
    }

    test("diagnostics report zero skipped for clean layouts") {
        val result = KlcLayoutParser.parseWithDiagnostics(SAMPLE_KLC)
        result.layout.scancodeMap.size shouldBe 9
        result.diagnostics.skippedCount shouldBe 0
        result.diagnostics.hasSkipped shouldBe false
    }

    test("fixture import keeps valid KLC rows and reports skipped malformed rows") {
        val result = KlcLayoutParser.parseWithDiagnostics(resourceText("import-fixtures/windows_klc_partial.klc"))

        result.layout.name shouldBe "Partial Layout"
        result.layout.scancodeMap shouldContainKey 0x1E
        result.layout.scancodeMap shouldContainKey 0x30
        result.diagnostics.skippedCount shouldBe 1
        result.diagnostics.summary().contains("LAYOUT row 2") shouldBe true
    }
})

private fun resourceText(path: String): String {
    val classLoader = requireNotNull(KlcLayoutParserTest::class.java.classLoader)
    return requireNotNull(classLoader.getResource(path)) {
        "Missing test resource: $path"
    }.readText()
}
