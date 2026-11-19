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
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ArchiveEntryTooLargeException(
    val maxBytes: Long,
) : IllegalArgumentException("Archive entry exceeds the $maxBytes-byte limit")

object ZipUtils {
    /** [unzip] leaves out a file past this many bytes without failing. */
    internal const val MaxUnzippedEntrySizeBytes = 100_000_000L
    /**
     * [unzip] fails once it has inflated this many bytes in total. Files it leaves out for size count too, so an
     * archive of oversized entries can't make it inflate the same remaining budget over and over.
     */
    internal const val MaxUnzippedArchiveSizeBytes = 250_000_000L

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

    fun readFileFromArchive(
        context: Context,
        zipRef: FlorisRef,
        relPath: String,
        maxBytes: Long = Long.MAX_VALUE,
    ) = runCatching<String> {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        when {
            zipRef.isAssets -> {
                context.assets.open(zipRef.subRef(relPath).relativePath).use { input ->
                    input.readUtf8TextLimited(maxBytes)
                }
            }
            zipRef.isCache || zipRef.isInternal -> {
                val flexHandle = FsFile(zipRef.absolutePath(context))
                readFileFromArchive(flexHandle, relPath, maxBytes).getOrThrow()
            }
            else -> error("Unsupported source!")
        }
    }

    fun readFileFromArchive(
        srcFile: FsFile,
        relPath: String,
        maxBytes: Long = Long.MAX_VALUE,
    ) = runCatching<String> {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        check(srcFile.isFile) { "Given archive $srcFile is not a file!" }
        ZipFile(srcFile).use { archive ->
            val entry = archive.getEntry(relPath)
                ?: error("Failed to load requested file $relPath")
            check(!entry.isDirectory) { "Requested archive entry $relPath is a directory" }
            if (entry.size > maxBytes) {
                throw ArchiveEntryTooLargeException(maxBytes)
            }
            archive.getInputStream(entry).use { input ->
                input.readUtf8TextLimited(maxBytes)
            }
        }
    }

    fun validateFileInArchive(
        context: Context,
        zipRef: FlorisRef,
        relPath: String,
        maxBytes: Long,
    ) = runCatching<Unit> {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        when {
            zipRef.isAssets -> {
                context.assets.open(zipRef.subRef(relPath).relativePath).use { input ->
                    input.requireAtMost(maxBytes)
                }
            }
            zipRef.isCache || zipRef.isInternal -> {
                val flexHandle = FsFile(zipRef.absolutePath(context))
                validateFileInArchive(flexHandle, relPath, maxBytes).getOrThrow()
            }
            else -> error("Unsupported source!")
        }
    }

    fun validateFileInArchive(
        srcFile: FsFile,
        relPath: String,
        maxBytes: Long,
    ) = runCatching<Unit> {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        check(srcFile.isFile) { "Given archive $srcFile is not a file!" }
        ZipFile(srcFile).use { archive ->
            val entry = archive.getEntry(relPath)
                ?: error("Failed to load requested file $relPath")
            check(!entry.isDirectory) { "Requested archive entry $relPath is a directory" }
            if (entry.size > maxBytes) {
                throw ArchiveEntryTooLargeException(maxBytes)
            }
            if (maxBytes != Long.MAX_VALUE) {
                archive.getInputStream(entry).use { input ->
                    input.requireAtMost(maxBytes)
                }
            }
        }
    }

    fun zip(context: Context, srcRef: FlorisRef, dstRef: FlorisRef) =
        zip(context, FsDir(srcRef.absolutePath(context)), dstRef)

    fun zip(context: Context, srcDir: FsDir, dstRef: FlorisRef) = runCatching {
        check(srcDir.exists() && srcDir.isDirectory) { "Cannot zip standalone file." }
        when {
            dstRef.isCache || dstRef.isInternal -> {
                val flexFile = FsFile(dstRef.absolutePath(context))
                zip(srcDir, flexFile)
            }
            else -> error("Unsupported destination!")
        }
    }

    fun zip(
        srcDir: FsDir,
        dstFile: FsFile,
        validate: (FsFile) -> Unit = ::validateReadableArchive,
    ) {
        check(srcDir.exists() && srcDir.isDirectory) { "Cannot zip standalone file." }
        AtomicFileWriter.replace(
            targetFile = dstFile,
            write = { stagedFile ->
                FileOutputStream(stagedFile).use { outStream ->
                    ZipOutputStream(outStream).use { zipOut ->
                        zip(srcDir, zipOut, "")
                    }
                }
            },
            validate = { stagedFile ->
                validate(FsFile(stagedFile.path))
            }
        )
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
     * Security violations abort the entire extraction, and any failure removes
     * every file and folder this call created, so no partial extraction is left
     * on disk. Four classes of violation abort:
     *
     *  - Entry-name traversal / drive-letter / leading-slash patterns
     *    (see [isUnsafeEntryName]).
     *  - Post-canonical-resolution paths that fall outside `dstDir` (zip-slip).
     *  - More than [MaxUnzippedEntryCount] entries in the archive (zip-bomb).
     *  - More than [MaxUnzippedArchiveSizeBytes] bytes inflated in total, counting
     *    entries left out for size (zip-bomb).
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
        unzip(srcFile, dstDir, MaxUnzippedEntrySizeBytes, ArchiveWorkBudget(MaxUnzippedArchiveSizeBytes))
    }

    internal fun unzip(srcFile: FsFile, dstDir: FsDir, maxEntryBytes: Long, budget: ArchiveWorkBudget) {
        require(srcFile.exists() && srcFile.isFile) { "Given src file `$srcFile` is not valid or a directory." }
        val created = ExtractedPaths()
        try {
            created.mkdirs(dstDir)
            extractEntries(srcFile, dstDir, maxEntryBytes, budget, created)
        } catch (error: Throwable) {
            created.removeAll()
            flogWarning { "ZipUtils.unzip failed and removed what it had extracted (src=$srcFile): ${error.message}" }
            throw error
        }
    }

    private fun extractEntries(
        srcFile: FsFile,
        dstDir: FsDir,
        maxEntryBytes: Long,
        budget: ArchiveWorkBudget,
        created: ExtractedPaths,
    ) {
        ZipFile(srcFile).use { flexFile ->
            val flexEntries = flexFile.entries()
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
                    created.mkdirs(flexEntryFile)
                } else {
                    val extracted = flexFile.copy(
                        srcEntry = flexEntry,
                        dstFile = flexEntryFile,
                        maxEntryBytes = maxEntryBytes,
                        budget = budget,
                        created = created,
                    )
                    if (!extracted) {
                        flogWarning {
                            "ZipUtils.unzip skipped oversized entry (src=$srcFile, name=${flexEntry.name}, size=${flexEntry.size})"
                        }
                    }
                }
            }
        }
    }

    /** Returns false when the entry is over [maxEntryBytes] and was left out. Every byte it inflates is charged to [budget]. */
    private fun ZipFile.copy(
        srcEntry: ZipEntry,
        dstFile: FsFile,
        maxEntryBytes: Long,
        budget: ArchiveWorkBudget,
        created: ExtractedPaths,
    ): Boolean {
        if (srcEntry.size > maxEntryBytes) {
            return false
        }
        // Unlike mkdirs(), this throws with the reason, so a full disk reads as one.
        dstFile.parentFile?.let { created.createDirectories(it) }
        created.file(dstFile)
        var copiedBytes = 0L
        var exceededBound = false
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        this.getInputStream(srcEntry).use { inStream ->
            dstFile.outputStream().use { outStream ->
                while (true) {
                    val readBytes = inStream.read(buffer)
                    if (readBytes < 0) break
                    // Charged before the entry check, so a left-out entry still uses up the budget.
                    budget.spend(readBytes)
                    copiedBytes += readBytes
                    if (copiedBytes > maxEntryBytes) {
                        // Stop writing. Break out (rather than delete
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
            return false
        }
        return true
    }

    private fun InputStream.readUtf8TextLimited(maxBytes: Long): String {
        val initialCapacity = minOf(maxBytes, DEFAULT_BUFFER_SIZE.toLong()).toInt()
        val output = ByteArrayOutputStream(initialCapacity)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val readBytes = read(buffer)
            if (readBytes < 0) break
            totalBytes += readBytes
            if (totalBytes > maxBytes) {
                throw ArchiveEntryTooLargeException(maxBytes)
            }
            output.write(buffer, 0, readBytes)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun InputStream.requireAtMost(maxBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val readBytes = read(buffer)
            if (readBytes < 0) return
            totalBytes += readBytes
            if (totalBytes > maxBytes) {
                throw ArchiveEntryTooLargeException(maxBytes)
            }
        }
    }

    private fun validateReadableArchive(file: FsFile) {
        ZipFile(file).use { archive ->
            archive.entries()
        }
    }
}

/** Bytes one [ZipUtils.unzip] call may inflate in total, whether the files they belong to are kept or not. */
internal class ArchiveWorkBudget(val maxBytes: Long) {
    var usedBytes: Long = 0L
        private set

    fun spend(bytes: Int) {
        usedBytes += bytes
        if (usedBytes > maxBytes) {
            throw SecurityException(
                "Archive inflates past $maxBytes bytes; refusing to extract (zip-bomb defence)."
            )
        }
    }
}

/** What one [ZipUtils.unzip] call made on disk, so a failed call can take it back. */
private class ExtractedPaths {
    private val paths = mutableListOf<FsFile>()

    fun mkdirs(dir: FsFile) {
        val missing = missingFrom(dir)
        dir.mkdirs()
        paths += missing
    }

    fun createDirectories(dir: FsFile) {
        val missing = missingFrom(dir)
        java.nio.file.Files.createDirectories(dir.toPath())
        paths += missing
    }

    /** A file that already existed is overwritten in place and stays. */
    fun file(file: FsFile) {
        if (!file.exists()) paths += file
    }

    /** Deepest first, so each folder is empty by its turn. A folder something else put a file in stays. */
    fun removeAll() {
        for (path in paths.asReversed()) path.delete()
    }

    private fun missingFrom(dir: FsFile): List<FsFile> {
        return generateSequence(dir) { it.parentFile }.takeWhile { !it.exists() }.toList().asReversed()
    }
}
