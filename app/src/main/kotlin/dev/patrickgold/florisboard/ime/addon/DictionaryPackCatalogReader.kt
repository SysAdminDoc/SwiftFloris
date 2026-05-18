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

package dev.patrickgold.florisboard.ime.addon

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Reads descriptor raw resources from enrolled dictionary-pack APKs and builds
 * the validated catalog used by both Settings and the runtime asset loader.
 */
internal class DictionaryPackCatalogReader(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    suspend fun build(manifests: List<AddonManifest>): DictionaryPackCatalog =
        withContext(Dispatchers.IO) {
            DictionaryPackCatalog.build(
                manifests = manifests,
                descriptorJsonFor = ::readDescriptorJson,
            )
        }

    private fun readDescriptorJson(manifest: AddonManifest): String? {
        return try {
            packageManager.getResourcesForApplication(manifest.packageName)
                .openRawResource(manifest.descriptorResourceId)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Resources.NotFoundException) {
            null
        } catch (_: IOException) {
            null
        }
    }
}
