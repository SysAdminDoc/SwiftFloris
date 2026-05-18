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

package dev.patrickgold.florisboard.ime.bidi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeGreaterThan
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class NastaliqFontProviderTest : FunSpec({
    test("bundled Nastaliq font asset is committed") {
        val fontPath = findAsset(NastaliqFontProvider.BUNDLED_FONT_PATH)
        Files.isRegularFile(fontPath).shouldBeTrue()
        Files.size(fontPath).shouldBeGreaterThan(100_000L)

        Files.newInputStream(fontPath).use { input ->
            val magic = ByteArray(4)
            input.read(magic)
            magic.contentEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00)).shouldBeTrue()
        }
    }

    test("OFL license is committed next to the font asset") {
        val licensePath = findAsset("fonts/LICENSE-OFL.txt")
        Files.isRegularFile(licensePath).shouldBeTrue()
        String(Files.readAllBytes(licensePath), Charsets.UTF_8)
            .contains("SIL Open Font License")
            .shouldBeTrue()
    }

    test("Nastaliq routing is scoped to Urdu Arabic-script labels") {
        NastaliqFontProvider.shouldRouteText("ur", "\u0627").shouldBeTrue()
        NastaliqFontProvider.shouldRouteText("UR", "\u06CC").shouldBeTrue()
        NastaliqFontProvider.shouldRouteText("ur", "!?").shouldBeFalse()
        NastaliqFontProvider.shouldRouteText("ur", "abc").shouldBeFalse()
        NastaliqFontProvider.shouldRouteText("fa", "\u0627").shouldBeFalse()
        NastaliqFontProvider.shouldRouteText("ar", "\u0627").shouldBeFalse()
    }
})

private fun findAsset(relativePath: String): Path {
    return listOf(
        Paths.get("src/main/assets").resolve(relativePath),
        Paths.get("app/src/main/assets").resolve(relativePath),
    ).firstOrNull { Files.exists(it) }
        ?: error("Unable to find asset $relativePath from ${Paths.get("").toAbsolutePath()}")
}
