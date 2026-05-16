/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.theme

import android.content.Context
import dev.patrickgold.florisboard.lib.devtools.flogError
import org.florisboard.lib.kotlin.io.subFile
import org.florisboard.lib.snygg.value.SnyggAssetResolver
import java.io.File
import java.net.URI

class FlorisAssetResolver(val context: Context, val themeInfo: ThemeManager.ThemeInfo) : SnyggAssetResolver {
    override fun resolveAbsolutePath(uri: String) = runCatching {
        val baseDir = checkNotNull(themeInfo.loadedDir) { "Loaded directory was null" }
        resolveFlexAssetPath(baseDir, uri).getOrThrow()
    }.onFailure { exception ->
        flogError { "FlorisAssetResolver failed to resolve URI '$uri'\n  error: ${exception.message}\n  with:  $themeInfo" }
    }
}

internal fun resolveFlexAssetPath(baseDir: File, rawUri: String) = runCatching {
    val uri = URI.create(rawUri)
    require(uri.scheme == "flex") { "Expected flex URI scheme" }
    require(uri.authority.isNullOrEmpty()) { "Expected flex URI without authority" }
    val relativePath = uri.path.orEmpty().removePrefix("/")
    require(relativePath.isNotBlank()) { "Expected non-empty flex URI path" }

    val canonicalBase = baseDir.canonicalFile
    val canonicalFile = canonicalBase.subFile(relativePath).canonicalFile
    val basePath = canonicalBase.toPath()
    val filePath = canonicalFile.toPath()
    check(filePath.startsWith(basePath)) {
        "Calculated path '$canonicalFile' does not stay within base path '$canonicalBase'"
    }
    check(canonicalFile.exists()) {
        "Calculated path '$canonicalFile' does not exist"
    }
    check(canonicalFile.isFile()) {
        "Calculated path '$canonicalFile' is not a file"
    }
    canonicalFile.path
}
