/*
 * Copyright (C) 2026 The SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.dictionary

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Shared persistence primitives for [PersonalBigramStore] and
 * [PersonalTrigramStore]. Both stores write one TSV file per locale, so the
 * token sanitization and the atomic-replace discipline must be identical.
 */
internal object PersonalNgramPersistence {

    /**
     * Normalizes a committed word into a persistable n-gram token, or returns
     * an empty string when the word must not be learned.
     *
     * Beyond length/digit gates, any token containing whitespace or a control
     * character is rejected outright: the TSV row format (`\t` columns, `\n`
     * rows) and the trigram context delimiter (`U+0000`) have no escaping, so
     * a single such character would silently corrupt the on-disk store.
     */
    fun normalizeToken(word: String): String {
        if (word.isBlank()) return ""
        val trimmed = word.trim().trim { ch -> !ch.isLetter() && ch != '\'' && ch != '-' }
        if (trimmed.length < 2 || trimmed.length > 32) return ""
        if (trimmed.any { it.isDigit() }) return ""
        if (trimmed.none { it.isLetter() }) return ""
        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) return ""
        return trimmed.lowercase()
    }

    /**
     * Atomically replaces [target] with content produced by [writeBody].
     *
     * Invariant: the destination is only ever replaced by a successful rename;
     * it is never deleted first. A flush that fails mid-write or mid-rename
     * leaves the previous good file untouched and drops only the new snapshot
     * (the next flush retries with fresher data). The temp file is synced to
     * disk before the rename so a crash between write and rename cannot leave
     * a truncated file behind the rename.
     *
     * @return true when the replace succeeded, false when the previous file
     *   was kept.
     */
    fun atomicReplace(target: File, writeBody: (BufferedWriter) -> Unit): Boolean {
        val tmp = File(target.parentFile, target.name + ".tmp")
        return runCatching {
            FileOutputStream(tmp).use { fos ->
                val writer = fos.bufferedWriter()
                writeBody(writer)
                writer.flush()
                fos.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                // Some filesystems (and JVM test hosts on Windows) refuse
                // rename-over-existing; fall back to an NIO replace which
                // still never deletes the destination ahead of the move.
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure {
            runCatching { tmp.delete() }
        }.isSuccess
    }
}
