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

package dev.patrickgold.florisboard.ime.input

import android.content.Context
import android.net.Uri
import dev.patrickgold.florisboard.lib.io.AtomicFileWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/** The four Android keypress effect classes that can be replaced locally. */
enum class KeypressSoundClass(val fileName: String) {
    STANDARD("standard.sound"),
    DELETE("delete.sound"),
    RETURN("return.sound"),
    SPACEBAR("spacebar.sound"),

    ;

    companion object {
        fun fromKeyCode(code: Int): KeypressSoundClass = when (code) {
            dev.patrickgold.florisboard.ime.text.key.KeyCode.DELETE -> DELETE
            dev.patrickgold.florisboard.ime.text.key.KeyCode.ENTER -> RETURN
            dev.patrickgold.florisboard.ime.text.key.KeyCode.SPACE -> SPACEBAR
            else -> STANDARD
        }
    }
}

object KeypressSoundStore {
    const val DirectoryName = "keypress_sounds"
    const val MaxSoundBytes = 1L * 1024L * 1024L

    fun directory(context: Context): File =
        File(context.applicationContext.filesDir, DirectoryName)

    fun file(context: Context, soundClass: KeypressSoundClass): File =
        File(directory(context), soundClass.fileName)

    fun available(context: Context): Set<KeypressSoundClass> =
        KeypressSoundClass.entries.filterTo(linkedSetOf()) { soundClass ->
            file(context, soundClass).isFile
        }

    fun delete(context: Context, soundClass: KeypressSoundClass): Boolean =
        !file(context, soundClass).exists() || file(context, soundClass).delete()

    fun deleteAll(context: Context) {
        directory(context).deleteRecursively()
    }

    fun import(context: Context, soundClass: KeypressSoundClass, source: Uri) {
        val resolver = context.applicationContext.contentResolver
        val input = resolver.openInputStream(source)
            ?: error("The selected sound could not be opened.")
        input.use { sourceStream ->
            AtomicFileWriter.replace(
                targetFile = file(context, soundClass),
                write = { stagedFile ->
                    FileOutputStream(stagedFile).use { output ->
                        copyBounded(sourceStream, output, MaxSoundBytes)
                    }
                },
                validate = { stagedFile ->
                    require(stagedFile.length() in 1L..MaxSoundBytes) {
                        "The selected sound is empty or too large."
                    }
                },
            )
        }
    }

    internal fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        require(limit > 0L) { "The sound size limit must be positive." }
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "The selected sound is too large." }
            output.write(buffer, 0, count)
        }
        return total
    }
}
