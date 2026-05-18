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

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import dev.patrickgold.florisboard.ime.addon.AddonContract
import dev.patrickgold.florisboard.ime.addon.AddonManifest
import dev.patrickgold.florisboard.ime.addon.AddonRegistry
import dev.patrickgold.florisboard.ime.addon.AddonRegistryStore
import dev.patrickgold.florisboard.ime.addon.DictionaryPackCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.readText
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * ROADMAP §7 Next-10.4 — mounts enrolled dictionary-pack APK assets without
 * extraction or temp-file copies. The planner puts addon dictionaries and Zipf
 * overlays ahead of bundled assets; [LatinDictionaryStore] then merges every
 * readable dictionary path so a pack augments the baseline for that language.
 */
internal class AddonDictionaryAssetMounts(
    context: Context,
    private val registryProvider: () -> AddonRegistry = { AddonRegistryStore.active() },
    private val generationProvider: () -> Long = { AddonRegistryStore.generation() },
) : LatinDictionaryAssetReader, LatinDictionaryAssetPlanner {
    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    override suspend fun planForLanguage(languageCode: String): LatinDictionaryAssetPlan {
        val normalizedLanguage = LatinDictionaryStore.normalizeLanguageCode(languageCode)
        val generation = generationProvider()
        val catalog = withContext(Dispatchers.IO) {
            DictionaryPackCatalog.build(
                manifests = registryProvider().dictionaryPacks(),
                descriptorJsonFor = ::readDescriptorJson,
            )
        }
        val entries = catalog.forLanguage(normalizedLanguage)
        val addonDictionaryPaths = entries.map { entry ->
            addonAssetPath(entry.packageName, entry.descriptor.fldicAssetPath)
        }
        val addonZipfPaths = entries.mapNotNull { entry ->
            entry.descriptor.zipfAssetPath?.let { path -> addonAssetPath(entry.packageName, path) }
        }
        return LatinDictionaryAssetPlan(
            generation = generation,
            dictionaryPaths = addonDictionaryPaths + LatinDictionaryStore.assetPathsForLanguage(normalizedLanguage),
            zipfPaths = addonZipfPaths + LatinDictionaryStore.zipfAssetPath(normalizedLanguage),
        )
    }

    override suspend fun read(path: String): String? = withContext(Dispatchers.IO) {
        val addonPath = decodeAddonAssetPath(path)
        if (addonPath != null) {
            readAddonAsset(addonPath.packageName, addonPath.assetPath)
        } else {
            readBundledAsset(path)
        }
    }

    private fun readBundledAsset(path: String): String? {
        return try {
            appContext.assets.readText(path)
        } catch (_: IOException) {
            null
        }
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

    private fun readAddonAsset(packageName: String, assetPath: String): String? {
        return try {
            packageManager.getResourcesForApplication(packageName)
                .assets
                .open(assetPath)
                .readUtf8WithLimit(AddonContract.ADDON_MAX_BUNDLE_BYTES)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    internal data class AddonAssetPath(
        val packageName: String,
        val assetPath: String,
    )

    companion object {
        private const val ADDON_ASSET_PREFIX = "addon://"

        internal fun addonAssetPath(packageName: String, assetPath: String): String =
            "$ADDON_ASSET_PREFIX$packageName/$assetPath"

        internal fun decodeAddonAssetPath(path: String): AddonAssetPath? {
            if (!path.startsWith(ADDON_ASSET_PREFIX)) return null
            val body = path.removePrefix(ADDON_ASSET_PREFIX)
            val separator = body.indexOf('/')
            if (separator <= 0 || separator == body.lastIndex) return null
            return AddonAssetPath(
                packageName = body.substring(0, separator),
                assetPath = body.substring(separator + 1),
            )
        }

        internal fun InputStream.readUtf8WithLimit(maxBytes: Long): String? {
            return use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) return null
                    out.write(buffer, 0, read)
                }
                out.toString(Charsets.UTF_8.name())
            }
        }
    }
}
