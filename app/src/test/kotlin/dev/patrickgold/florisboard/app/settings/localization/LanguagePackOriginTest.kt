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

package dev.patrickgold.florisboard.app.settings.localization

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.nlp.LanguagePackComponent
import dev.patrickgold.florisboard.ime.nlp.LanguagePackExtension
import dev.patrickgold.florisboard.lib.ext.ExtensionMaintainer
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.lib.ext.ExtensionMeta
import dev.patrickgold.florisboard.lib.io.FlorisRef
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Robolectric, since a pack's origin comes from its source ref and FlorisRef is built on android.net.Uri.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LanguagePackOriginTest {
    @Test
    fun catalogTellsBuiltInPacksFromImportedOnes() {
        val module = ExtensionManager.IME_LANGUAGEPACK_PATH
        // Where the extension index points each pack: an asset folder for the ones in the APK,
        // a .flex file in internal storage for the ones the user imported.
        val bundled = languagePack("org.florisboard.languagepack", "Bundled Pack").apply {
            sourceRef = FlorisRef.assets(module).subRef("org.florisboard.languagepack")
        }
        val imported = languagePack("org.example.imported", "Imported Pack").apply {
            sourceRef = FlorisRef.internal(module).subRef("org.example.imported.flex")
        }

        val origins = LanguagePackManagerPolicy.catalogEntries(
            extensions = listOf(bundled, imported),
            activeLocaleTags = emptySet(),
        ).associate { it.extensionId to it.origin }

        origins shouldBe mapOf(
            "org.florisboard.languagepack" to LanguagePackOrigin.BuiltIn,
            "org.example.imported" to LanguagePackOrigin.Imported,
        )
        // A row says "Imported" exactly when it has a Delete button.
        val manager = ExtensionManager(ApplicationProvider.getApplicationContext())
        listOf(bundled, imported).forEach { pack ->
            (origins[pack.meta.id] == LanguagePackOrigin.Imported) shouldBe manager.canDelete(pack)
        }
    }
}

private fun languagePack(id: String, title: String): LanguagePackExtension {
    return LanguagePackExtension(
        meta = ExtensionMeta(
            id = id,
            version = "1.0",
            title = title,
            maintainers = listOf(ExtensionMaintainer("SwiftFloris")),
            license = "Apache-2.0",
        ),
        items = listOf(LanguagePackComponent(id = "zh-Hans", label = "zh-Hans", authors = listOf("SwiftFloris"))),
    )
}
