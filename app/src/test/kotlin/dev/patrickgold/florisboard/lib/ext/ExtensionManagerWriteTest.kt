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

package dev.patrickgold.florisboard.lib.ext

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import dev.patrickgold.florisboard.ime.theme.ThemeExtensionComponentImpl
import dev.patrickgold.florisboard.lib.io.FlorisRef
import io.kotest.matchers.shouldBe
import java.util.UUID
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExtensionManagerWriteTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun importedThemeExtensionIsPersistedAsAReadableArchive() {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val extension = ThemeExtension(
            meta = ExtensionMeta(
                id = "org.swiftfloris.test_$suffix",
                version = "1.0.0",
                title = "Write fixture",
                description = "Theme extension write fixture",
                maintainers = listOf(ExtensionMaintainer("SwiftFloris")),
                license = "Apache-2.0",
            ),
            themes = listOf(
                ThemeExtensionComponentImpl(
                    id = "fixture",
                    label = "Fixture",
                    authors = listOf("SwiftFloris"),
                ),
            ),
        )
        val workingDir = context.cacheDir.subDir("extension-write-$suffix")
        val storedArchive = FlorisRef
            .internal(ExtensionManager.IME_THEME_PATH)
            .subRef(ExtensionDefaults.createFlexName(extension.meta.id))
            .absoluteFile(context)
        try {
            workingDir.mkdirs()
            workingDir.subFile("stylesheets/fixture.json").also { stylesheetFile ->
                stylesheetFile.parentFile?.mkdirs()
                stylesheetFile.writeText("{}")
            }
            extension.workingDir = workingDir

            ExtensionManager(context).import(extension)

            storedArchive.isFile shouldBe true
            val persisted = ExtensionPackagePolicy.readValidatedArchive(
                archiveFile = storedArchive,
                serializer = ThemeExtension.serializer(),
            )
            persisted.meta.id shouldBe extension.meta.id
            persisted.themes.map { it.id } shouldBe listOf("fixture")
        } finally {
            workingDir.deleteRecursively()
            storedArchive.delete()
        }
    }
}
