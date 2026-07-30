/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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
import dev.patrickgold.florisboard.lib.io.FlorisRef
import dev.patrickgold.florisboard.lib.io.ZipUtils
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.resultOk

/**
 * An extension container holding a parsed config, a working directory file
 * object as well as a reference to the original flex file.
 *
 * @property meta The parsed config of this extension.
 * @property workingDir The working directory, used as a cache and as a staging
 *  area for modifications to extension files.
 * @property sourceRef Optional, defines where the original flex file is stored.
 */
@Polymorphic
@Serializable
abstract class Extension {
    @Transient var workingDir: FsDir? = null
    @Transient var sourceRef: FlorisRef? = null

    abstract val meta: ExtensionMeta
    abstract val dependencies: List<String>?

    abstract fun serialType(): String

    abstract fun components(): List<ExtensionComponent>

    fun isLoaded() = workingDir?.let { it.exists() && it.isDirectory } == true

    open fun onBeforeLoad(context: Context, cacheDir: FsDir) {
        /* Empty */
    }

    open fun onAfterLoad(context: Context, cacheDir: FsDir) {
        /* Empty */
    }

    fun load(context: Context, force: Boolean = false): Result<Unit> {
        if (isLoaded() && !force) {
            return resultOk()
        }
        if (workingDir != null) {
            unload(context)
        }
        val sourceRef = sourceRef ?: return resultOk()
        val cacheDir = FsDir(context.cacheDir, meta.id)
        val result = runCatching {
            if (cacheDir.exists()) {
                check(cacheDir.deleteRecursively() && !cacheDir.exists()) {
                    "Could not remove stale extension working directory"
                }
            }
            check(cacheDir.mkdirs() || cacheDir.isDirectory) {
                "Could not create extension working directory"
            }
            onBeforeLoad(context, cacheDir)
            ZipUtils.unzip(context, sourceRef, cacheDir).getOrThrow()
            if (sourceRef.isAssets) {
                ExtensionPackagePolicy.inspect(this)
            } else {
                ExtensionPackagePolicy.validateExtracted(this, cacheDir)
            }
            workingDir = cacheDir
            onAfterLoad(context, cacheDir)
        }
        result.exceptionOrNull()?.let { loadError ->
            if (workingDir == cacheDir) {
                runCatching {
                    onBeforeUnload(context, cacheDir)
                }.onFailure(loadError::addSuppressed)
            }
            workingDir = null
            runCatching {
                check(!cacheDir.exists() || (cacheDir.deleteRecursively() && !cacheDir.exists())) {
                    "Could not remove failed extension working directory"
                }
            }.onFailure(loadError::addSuppressed)
        }
        return result
    }

    open fun onBeforeUnload(context: Context, cacheDir: FsDir) {
        /* Empty */
    }

    open fun onAfterUnload(context: Context, cacheDir: FsDir) {
        /* Empty */
    }

    fun unload(context: Context) {
        val cacheDir = workingDir ?: FsDir(context.cacheDir, meta.id)
        if (workingDir == null && !cacheDir.exists()) return
        try {
            onBeforeUnload(context, cacheDir)
        } finally {
            try {
                check(!cacheDir.exists() || (cacheDir.deleteRecursively() && !cacheDir.exists())) {
                    "Could not remove extension working directory"
                }
            } finally {
                workingDir = null
                onAfterUnload(context, cacheDir)
            }
        }
    }

    fun readExtensionFile(context: Context, relPath: String): String? {
        val cacheDir = FsDir(context.cacheDir, meta.id)
        if (cacheDir.exists() && cacheDir.isDirectory) {
            return runCatching {
                val file = ExtensionPackagePolicy.resolveRequiredFile(cacheDir, relPath)
                ExtensionPackagePolicy.readComponentJson(file)
            }.getOrNull()
        }
        return null
    }

    abstract fun edit(): ExtensionEditor
}


interface ExtensionEditor {
    var meta: ExtensionMeta
    val dependencies: MutableList<String>

    fun build(): Extension
}
