/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.theme

import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.io.subFile
import java.io.File

internal object ThemeStylesheetLoadPolicy {
    fun resolveStylesheetFile(loadedDir: FsDir, stylesheetPath: String): FsFile {
        require(stylesheetPath.isNotBlank()) { "Expected non-empty theme stylesheet path" }
        check(!File(stylesheetPath).isAbsolute) {
            "Theme stylesheet path '$stylesheetPath' must be relative"
        }
        val canonicalBase = loadedDir.canonicalFile
        val canonicalFile = canonicalBase.subFile(stylesheetPath).canonicalFile
        check(canonicalFile.toPath().startsWith(canonicalBase.toPath())) {
            "Theme stylesheet path '$stylesheetPath' escapes loaded theme directory '$canonicalBase'"
        }
        check(canonicalFile.isFile) {
            "Theme stylesheet path '$stylesheetPath' is not a file in '$canonicalBase'"
        }
        return canonicalFile
    }

    fun cleanupLoadedDir(loadedDir: FsDir) {
        loadedDir.deleteContentsRecursively()
        loadedDir.delete()
    }
}
