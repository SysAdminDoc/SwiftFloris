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
import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.ime.keyboard.LayoutArrangementComponent
import dev.patrickgold.florisboard.lib.io.ZipUtils
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.kotlin.io.writeJson
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExtensionManagerLoadingStateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun extensionIndexStaysLoadingUntilItsInitialRefreshCompletes() {
        runBlocking {
            val manager = ExtensionManager(context)

            manager.themes.initialized.value shouldBe false

            manager.themes.init()

            manager.themes.initialized.value shouldBe true
        }
    }

    @Test
    fun extensionIsFoundByIdAsSoonAsItsIndexHasIt() {
        runBlocking {
            val manager = ExtensionManager(context)
            val extension = KeyboardExtension(
                meta = ExtensionMeta(
                    id = "org.example.layouts",
                    version = "1.0",
                    title = "Example layouts",
                    maintainers = listOf(ExtensionMaintainer("SwiftFloris")),
                    license = "Apache-2.0",
                ),
                layouts = mapOf(
                    "characters" to listOf(
                        LayoutArrangementComponent(
                            id = "example",
                            label = "Example",
                            authors = listOf("SwiftFloris"),
                            direction = "ltr",
                        ),
                    ),
                ),
            )
            val staging = Files.createTempDirectory("extension-index").toFile()
            try {
                val manifest: Extension = extension
                staging.subFile(ExtensionDefaults.MANIFEST_FILE_NAME).writeJson(manifest, ExtensionJsonConfig)
                staging.subFile("layouts/characters/example.json").also { layoutFile ->
                    layoutFile.parentFile?.mkdirs()
                    layoutFile.writeText("[]")
                }
                val moduleDir = manager.keyboardExtensions.internalModuleDir.also { it.mkdirs() }
                ZipUtils.zip(staging, moduleDir.subFile(ExtensionDefaults.createFlexName(extension.meta.id))) {
                    ExtensionPackagePolicy.validateArchive(extension, it)
                }

                // KeyboardManager reloads its layouts the moment this index
                // publishes, so look the extension up from a collector that
                // runs inside the index's own update (Unconfined, with init on
                // an IO thread), before `extensions` can catch up.
                val lookup = CompletableDeferred<Extension?>()
                val watcher = CoroutineScope(Dispatchers.Unconfined).launch {
                    manager.keyboardExtensions.collect { list ->
                        if (list.any { it.meta.id == extension.meta.id }) {
                            lookup.complete(manager.getExtensionById(extension.meta.id))
                        }
                    }
                }
                try {
                    withContext(Dispatchers.IO) { manager.keyboardExtensions.init() }

                    manager.keyboardExtensions.value.map { it.meta.id } shouldContain extension.meta.id
                    withTimeout(5_000) { lookup.await() }?.meta?.id shouldBe extension.meta.id
                } finally {
                    watcher.cancel()
                }
            } finally {
                staging.deleteRecursively()
            }
        }
    }
}
