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
import dev.patrickgold.florisboard.lib.io.ZipUtils
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.util.UUID
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExtensionLifecycleCleanupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun failedLoadRemovesPartiallyExtractedWorkingDirectory() {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val extension = ThemeExtension(
            meta = ExtensionMeta(
                id = "org.swiftfloris.test_$suffix",
                version = "1.0",
                title = "Cleanup fixture",
                maintainers = listOf(ExtensionMaintainer("SwiftFloris")),
                license = "Apache-2.0",
            ),
            themes = listOf(
                ThemeExtensionComponentImpl(
                    id = "missing",
                    label = "Missing stylesheet",
                    authors = listOf("SwiftFloris"),
                ),
            ),
        )
        val stagingDir = context.cacheDir.subDir("extension-source-$suffix")
        val archiveRef = FlorisRef.cache("extension-source-$suffix.flex")
        val archiveFile = archiveRef.absoluteFile(context)
        val workingDir = context.cacheDir.subDir(extension.meta.id)
        try {
            stagingDir.mkdirs()
            val manifest: Extension = extension
            stagingDir.subFile(ExtensionDefaults.MANIFEST_FILE_NAME)
                .writeJson(manifest, ExtensionJsonConfig)
            ZipUtils.zip(stagingDir, archiveFile)
            extension.sourceRef = archiveRef

            extension.load(context).isFailure shouldBe true

            extension.workingDir shouldBe null
            workingDir.exists() shouldBe false
        } finally {
            extension.unload(context)
            stagingDir.deleteRecursively()
            archiveFile.delete()
            workingDir.deleteRecursively()
        }
    }

    @Test
    fun unloadRemovesWorkingDirectoryWhenLifecycleCallbackFails() {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        var afterUnloadCalled = false
        val extension = object : Extension() {
            override val meta = ExtensionMeta(
                id = "org.swiftfloris.test_$suffix",
                version = "1.0",
                title = "Cleanup fixture",
                maintainers = listOf(ExtensionMaintainer("SwiftFloris")),
                license = "Apache-2.0",
            )
            override val dependencies: List<String>? = null

            override fun serialType() = "test"

            override fun components() = emptyList<ExtensionComponent>()

            override fun onBeforeUnload(context: Context, cacheDir: java.io.File) {
                error("injected unload failure")
            }

            override fun onAfterUnload(context: Context, cacheDir: java.io.File) {
                afterUnloadCalled = true
            }

            override fun edit(): ExtensionEditor = error("unused")
        }
        val workingDir = context.cacheDir.subDir(extension.meta.id)
        workingDir.mkdirs()
        workingDir.subFile("partial").writeText("temporary")
        extension.workingDir = workingDir

        shouldThrow<IllegalStateException> {
            extension.unload(context)
        }

        extension.workingDir shouldBe null
        workingDir.exists() shouldBe false
        afterUnloadCalled shouldBe true
    }
}
