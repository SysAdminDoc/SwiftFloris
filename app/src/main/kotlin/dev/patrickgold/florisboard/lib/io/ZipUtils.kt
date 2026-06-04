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

package dev.patrickgold.florisboard.lib.io

import android.content.Context
import android.net.Uri
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import org.florisboard.lib.android.copyRecursively
import org.florisboard.lib.android.write
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.FsFile
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipUtils {
    private const val MaxUnzippedEntrySizeBytes = 100_000_000L
    private const val MaxUnzippedArchiveSizeBytes = 250_000_000L

    // Cap on entry count to defeat archives that ship millions of empty entries
    // (zip bombs that pass per-entry / total-byte gates but exhaust inodes,
    // dentries, or the FS path cache). DictionaryImporter caps at 256; the
    // generic backup-restore archive realistically tops out a few thousand
    // entries (themes, dictionaries, extensions). 10_000 leaves comfortable
    // headroom for legitimate archives and stops abuse cold.
    private const val MaxUnzippedEntryCount = 10_000

    // Disallow entry names that would escape the destination root pre-canonical
    // resolution — defense in depth on top of the canonical-prefix check.
    // Rejects: any `..` path segment, leading `/` or `\`, Windows drive prefixes
    // (`C:`), or NUL bytes. Empty-string entry names are also refused.
    internal fun isUnsafeEntryName(name: String): Boolean {
        if (name.isEmpty()) return true
        if (name.contains('\u0000')) return true
        if (name.startsWith('/') || name.startsWith('\\')) return true
        // Windows drive-letter prefix like "C:\path" or "C:/path"
        if (name.length >= 2 && name[1] == ':' && (name[0] in 'A'..'Z' || name[0] in 'a'..'z')) return true
        for (segment in name.split('/', '\\')) {
            if (segment == ".." ) return true
        }
        return false
    }

    fun readFileFromArchive(context: Context, zipRef: FlorisRef, relPath: String) = runCatching<String> {
        when {
            zipRef.isAssets -> {
                zipRef.subRef(relPath).loadTextAsset(context).getOrThrow()
            }
            zipRef.isCache || zipRef.isInternal -> {
                val flexHandle = FsFile(zipRef.absolutePath(context))
                check(flexHandle.isFile) { "Given ref $zipRef is not a file!" }
                var fileContents: String? = null
                ZipFile(flexHandle).use { flexFile ->
                    flexFile.getEntry(relPath)?.let { flexEntry ->
                        fileContents = flexFile.getInputStream(flexEntry).bufferedReader().use { it.readText() }
                    }
                }
                fileContents ?: error("Failed to load requested file $relPath")
            }
            else -> error("Unsupported source!")
        }
    }

    fun zip(context: Context, srcRef: FlorisRef, dstRef: FlorisRef) =
        zip(context, FsDir(srcRef.absolutePath(context)), dstRef)

    fun zip(context: Context, srcDir: FsDir, dstRef: FlorisRef) = runCatching {
        check(srcDir.exists() && srcDir.isDirectory) { "Cannot zip standalone file." }
        when {
            dstRef.isCache || dstRef.isInternal -> {
                val flexFile = FsFile(dstRef.absolutePath(context))
                flexFile.parentFile?.mkdirs()
                flexFile.delete()
                FileOutputStream(flexFile).use { fileOut ->
                    ZipOutputStream(fileOut).use { zipOut ->
                        zip(srcDir, zipOut, "")
                    }
                }
            }
            else -> error("Unsupported destination!")
        }
    }

    fun zip(srcDir: FsDir, dstFile: FsFile) {
        check(srcDir.exists() && srcDir.isDirectory) { "Cannot zip standalone file." }
        dstFile.parentFile?.mkdirs()
        dstFile.delete()
        FileOutputStream(dstFile).use { outStream ->
            ZipOutputStream(outStream).use { zipOut ->
                zip(srcDir, zipOut, "")
            }
        }
    }

    fun zip(context: Context, srcDir: FsDir, uri: Uri) = runCatching {
        check(srcDir.exists() && srcDir.isDirectory) { "Cannot zip standalone file." }
        context.contentResolver.write(uri) { fileOut ->
            ZipOutputStream(fileOut).use { zipOut ->
                zip(srcDir, zipOut, "")
            }
        }
    }

    internal fun zip(srcDir: FsDir, zipOut: ZipOutputStream, base: String) {
        val dir = FsDir(srcDir, base)
        for (file in dir.listFiles() ?: arrayOf()) {
            val path = if (base.isBlank()) file.name else "$base/${file.name}"
            if (file.isDirectory) {
                zipOut.putNextEntry(ZipEntry("$path/"))
                zipOut.closeEntry()
                zip(srcDir, zipOut, path)
            } else {
                zipOut.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        }
    }

    fun unzip(context: Context, srcRef: FlorisRef, dstRef: FlorisRef) =
        unzip(context, srcRef, FsDir(dstRef.absolutePath(context)))

    fun unzip(context: Context, srcRef: FlorisRef, dstDir: FsFile) = runCatching {
        check(dstDir.exists() && dstDir.isDirectory) { "Cannot unzip into file." }
        dstDir.mkdirs()
        when {
            srcRef.isAssets -> {
                context.assets.copyRecursively(srcRef.relativePath.removeSuffix("/"), dstDir)
            }
            srcRef.isCache || srcRef.isInternal -> {
                val flexHandle = FsFile(srcRef.absolutePath(context))
                unzip(srcFile = flexHandle, dstDir = dstDir)
            }
            else -> error("Unsupported source!")
        }
    }

    /**
     * Unzips a given Zip file to the destination directory.
     *
     * Security violations abort the entire extraction (no partial extraction is
     * left on disk). Three classes of violation abort:
     *
     *  - Entry-name traversal / drive-letter / leading-slash patterns
     *    (see [isUnsafeEntryName]).
     *  - Post-canonical-resolution paths that fall outside `dstDir` (zip-slip).
     *  - More than [MaxUnzippedEntryCount] entries in the archive (zip-bomb).
     *
     * Benign anomalies (name longer than 255 chars, destination path longer than
     * 1023 chars, individual entry exceeding the per-entry byte cap) still skip
     * the offending entry and continue. Those are unlikely to be attacker-
     * controlled and are common in legitimate archives produced by tools that
     * encode unusual paths.
     *
     * Callers that wrap this in [runCatching] receive a [Result.Failure] with a
     * [SecurityException] on abort and an [IllegalArgumentException] /
     * [java.io.IOException] / [java.util.zip.ZipException] for the other
     * documented failure modes.
     *
     * @param srcFile The source Zip file handle.
     * @param dstDir The destination directory where the [srcFile] contents should be unzipped to.
     *
     * @throws IllegalArgumentException If the given [srcFile] is not existing on the file system or if it points to
     *  a directory instead.
     * @throws SecurityException On any of the abort-class security violations described above.
     * @throws java.util.zip.ZipException If a Zip format error has occurred.
     * @throws java.io.IOException If an I/O error has occurred.
     */
    fun unzip(srcFile: FsFile, dstDir: FsDir) {
        require(srcFile.exists() && srcFile.isFile) { "Given src file `$srcFile` is not valid or a directory." }
        dstDir.mkdirs()
        ZipFile(srcFile).use { flexFile ->
            val flexEntries = flexFile.entries()
            var totalUnzippedBytes = 0L
            var processedEntries = 0
            while (flexEntries.hasMoreElements()) {
                val flexEntry = flexEntries.nextElement()
                processedEntries++
                if (processedEntries > MaxUnzippedEntryCount) {
                    // Abort: archives this large are not legitimate restore
                    // payloads. A user trying to restore a 10k+ entry archive
                    // should be told why, not silently get a truncated result.
                    flogWarning {
                        "ZipUtils.unzip aborting: archive contains > $MaxUnzippedEntryCount entries (src=$srcFile)"
                    }
                    throw SecurityException(
                        "Archive contains more than $MaxUnzippedEntryCount entries; refusing to extract (zip-bomb defence)."
                    )
                }
                if (flexEntry.name.length > 255) {
                    // Benign anomaly: skip but continue. Some legitimate
                    // archives carry deeply-nested paths.
                    flogWarning {
                        "ZipUtils.unzip skipping entry: name longer than 255 chars (src=$srcFile, name=${flexEntry.name})"
                    }
                    continue
                }
                // Pre-canonical guard: reject entry names containing `..`, leading
                // `/` or `\`, Windows drive prefixes, or NUL bytes. Defends
                // against attackers who craft entry names so that File()'s
                // canonicalisation step erases the traversal segments.
                if (isUnsafeEntryName(flexEntry.name)) {
                    flogWarning {
                        "ZipUtils.unzip aborting: unsafe entry name (src=$srcFile, name=${flexEntry.name})"
                    }
                    throw SecurityException(
                        "Refusing to extract archive: entry name '${flexEntry.name}' contains a path-traversal pattern."
                    )
                }
                val flexEntryFile = FsFile(dstDir, flexEntry.name)
                val canonicalDestinationDirPath = dstDir.canonicalPath
                val canonicalDestinationFilePath = flexEntryFile.canonicalPath
                if (canonicalDestinationFilePath.length > 1023) {
                    flogWarning {
                        "ZipUtils.unzip skipping entry: destination path > 1023 chars (src=$srcFile, name=${flexEntry.name})"
                    }
                    continue
                }
                if (!canonicalDestinationFilePath.startsWith(canonicalDestinationDirPath + FsFile.separator)) {
                    // Zip-slip: this can only mean the entry name resolved
                    // outside dstDir through canonical-path normalisation
                    // (the pre-canonical guard caught the obvious patterns
                    // already). At this point an attacker is actively trying
                    // to escape the sandbox — abort the entire restore so the
                    // user gets a clear error rather than a half-applied
                    // archive.
                    flogWarning {
                        "ZipUtils.unzip aborting: would escape destination root (src=$srcFile, name=${flexEntry.name})"
                    }
                    throw SecurityException(
                        "Refusing to extract archive: entry '${flexEntry.name}' resolved outside the destination directory."
                    )
                }
                if (flexEntry.isDirectory) {
                    flexEntryFile.mkdirs()
                } else {
                    val copiedBytes = flexFile.copy(
                        srcEntry = flexEntry,
                        dstFile = flexEntryFile,
                        maxEntryBytes = MaxUnzippedEntrySizeBytes,
                        maxRemainingArchiveBytes = MaxUnzippedArchiveSizeBytes - totalUnzippedBytes,
                    )
                    if (copiedBytes > 0L) {
                        totalUnzippedBytes += copiedBytes
                    } else if (flexEntry.size > 0L) {
                        flogWarning {
                            "ZipUtils.unzip skipped oversized entry (src=$srcFile, name=${flexEntry.name}, size=${flexEntry.size})"
                        }
                    }
                }
            }
        }
    }

    private fun ZipFile.copy(
        srcEntry: ZipEntry,
        dstFile: FsFile,
        maxEntryBytes: Long,
        maxRemainingArchiveBytes: Long,
    ): Long {
        if (maxRemainingArchiveBytes <= 0L || srcEntry.size > maxEntryBytes) {
            return 0L
        }
        dstFile.parentFile?.mkdirs()
        var copiedBytes = 0L
        var exceededBound = false
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        this.getInputStream(srcEntry).use { inStream ->
            dstFile.outputStream().use { outStream ->
                while (true) {
                    val readBytes = inStream.read(buffer)
                    if (readBytes < 0) break
                    copiedBytes += readBytes
                    if (copiedBytes > maxEntryBytes || copiedBytes > maxRemainingArchiveBytes) {
                        // Stop writing — must break out (rather than delete
                        // mid-`use`) so the output stream is closed before
                        // we try to delete the file. Some filesystems
                        // reject `delete()` on a still-open handle.
                        exceededBound = true
                        break
                    }
                    outStream.write(buffer, 0, readBytes)
                }
            }
        }
        if (exceededBound) {
            dstFile.delete()
            return 0L
        }
        return copiedBytes
    }
}
