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

package dev.patrickgold.florisboard.lib.io

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun interface AtomicFileMover {
    fun replace(stagedFile: File, targetFile: File)
}

/**
 * Writes, syncs, validates, and replaces a regular file without deleting the
 * last good target first.
 */
internal object AtomicFileWriter {
    private val systemMover = AtomicFileMover { stagedFile, targetFile ->
        try {
            Files.move(
                stagedFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                stagedFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun replace(
        targetFile: File,
        mover: AtomicFileMover = systemMover,
        write: (stagedFile: File) -> Unit,
        validate: (stagedFile: File) -> Unit,
    ) {
        val absoluteTarget = targetFile.absoluteFile
        val targetDir = requireNotNull(absoluteTarget.parentFile) {
            "Atomic target must have a parent directory"
        }
        check(targetDir.isDirectory || targetDir.mkdirs()) {
            "Could not create atomic target directory"
        }

        val stagedFile = File.createTempFile(
            ".${absoluteTarget.name}.",
            ".tmp",
            targetDir,
        )
        var primaryFailure: Throwable? = null
        try {
            check(stagedFile.parentFile?.canonicalFile == targetDir.canonicalFile) {
                "Atomic staging file must be beside its target"
            }
            write(stagedFile)
            check(stagedFile.isFile) { "Atomic writer did not produce a regular file" }
            FileOutputStream(stagedFile, true).use { output ->
                output.fd.sync()
            }
            validate(stagedFile)
            mover.replace(stagedFile, absoluteTarget)
            check(absoluteTarget.isFile) { "Atomic replacement did not produce the target file" }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            if (stagedFile.exists()) {
                runCatching {
                    Files.deleteIfExists(stagedFile.toPath())
                }.onFailure { cleanupError ->
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(cleanupError)
                    } else {
                        throw cleanupError
                    }
                }
            }
        }
    }
}
