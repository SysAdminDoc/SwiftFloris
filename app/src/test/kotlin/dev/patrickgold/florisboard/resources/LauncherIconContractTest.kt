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

package dev.patrickgold.florisboard.resources

import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherIconContractTest {
    private val layerSizes = linkedMapOf(
        "mdpi" to 108,
        "hdpi" to 162,
        "xhdpi" to 216,
        "xxhdpi" to 324,
        "xxxhdpi" to 432,
    )

    @Test
    fun adaptiveAndThemedLayersUseTheSuppliedRasterContract() {
        val res = projectFile("app/src/main/res")
        for (name in listOf("ic_launcher.xml", "ic_launcher_round.xml")) {
            val xml = File(res, "mipmap-anydpi-v26/$name").readText()
            assertTrue("@color/ic_launcher_background" in xml)
            assertTrue("@mipmap/ic_launcher_foreground" in xml)
            assertTrue("@mipmap/ic_launcher_monochrome" in xml)
        }
        assertTrue("#1F0B25" in File(res, "values/ic_launcher_background.xml").readText())

        for ((density, size) in layerSizes) {
            assertLayer(File(res, "mipmap-$density/ic_launcher_foreground.png"), size)
            assertLayer(File(res, "mipmap-$density/ic_launcher_monochrome.png"), size)
            assertFalse(File(res, "mipmap-$density/ic_launcher.png").exists())
            assertFalse(File(res, "mipmap-$density/ic_launcher_round.png").exists())
        }
    }

    @Test
    fun storeIconsMatchTheCurrentOpaqueMasterContract() {
        for (path in listOf(
            "app/src/main/ic_app_icon_stable-playstore.png",
            "app/src/main/ic_app_icon_preview-playstore.png",
            "app/src/main/ic_app_icon_debug-playstore.png",
            "fastlane/metadata/android/en-US/images/icon.png",
            "fastlane/metadata/androidbeta/en-US/images/icon.png",
        )) {
            val image = ImageIO.read(projectFile(path))
            assertEquals(512, image.width)
            assertEquals(512, image.height)
            assertTrue((0 until image.height).all { y ->
                (0 until image.width).all { x -> ((image.getRGB(x, y) ushr 24) and 0xff) == 255 }
            })
        }
    }

    @Test
    fun obsoleteLauncherArtworkIsGone() {
        for (path in listOf(
            "app/src/main/res/drawable/ic_swiftfloris_foreground.png",
            "app/src/main/res/drawable/ic_floris_monochrome.xml",
            "app/src/main/res/values/colors_branding.xml",
            "app/src/beta/res/drawable/ic_app_icon_foreground.xml",
            "app/src/beta/res/drawable/ic_app_icon_monochrome.xml",
        )) {
            assertFalse(projectPath(path).exists(), "Obsolete icon remains: $path")
        }
    }

    private fun assertLayer(file: File, size: Int) {
        assertTrue(file.isFile, "Missing icon layer: ${file.path}")
        val image = ImageIO.read(file)
        assertEquals(size, image.width)
        assertEquals(size, image.height)

        var transparent = 0
        var opaque = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                when ((image.getRGB(x, y) ushr 24) and 0xff) {
                    0 -> transparent++
                    255 -> opaque++
                }
            }
        }
        assertTrue(transparent > 100, "Expected transparent pixels in ${file.path}")
        assertTrue(opaque > 100, "Expected opaque pixels in ${file.path}")
    }

    private fun projectFile(path: String): File {
        val file = projectPath(path)
        assertTrue(file.isFile || file.isDirectory, "Missing project path: $path")
        return file
    }

    private fun projectPath(path: String): File {
        return sequenceOf(File(path), File("../$path"))
            .firstOrNull { it.exists() }
            ?: File(path)
    }
}
