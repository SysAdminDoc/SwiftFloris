/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.snippet

import android.content.Context
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.importing.ImportDiagnostics
import dev.patrickgold.florisboard.ime.smartcompose.SensitiveFieldGuard
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

data class SnippetFileInfo(
    val filename: String,
    val triggerCount: Int,
)

data class SnippetLoadReport(
    val skippedFileCount: Int = 0,
)

class SnippetManager internal constructor(private val filesDir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val initializationJob: Job by lazy {
        ioScope.launch {
            loadAll()
        }
    }

    private val _snippets = MutableStateFlow<List<EspansoMatch>>(emptyList())
    val snippets: StateFlow<List<EspansoMatch>> = _snippets.asStateFlow()

    private val _fileStates = MutableStateFlow<List<SnippetFileInfo>>(emptyList())
    val fileStates: StateFlow<List<SnippetFileInfo>> = _fileStates.asStateFlow()

    private val _loadReport = MutableStateFlow(SnippetLoadReport())
    val loadReport: StateFlow<SnippetLoadReport> = _loadReport.asStateFlow()

    private val loadMutex = Mutex()

    private val snippetsDir: File
        get() = File(filesDir, "snippets").also { it.mkdirs() }

    /**
     * Loads persisted snippets for IME use without requiring the Settings screen to be opened.
     *
     * The returned job is shared so repeated application startup calls do not start concurrent
     * file scans. Settings can still call [loadAll] when it explicitly refreshes its view.
     */
    fun initialize(): Job = initializationJob

    suspend fun loadAll() = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            val dir = snippetsDir
            if (!dir.exists()) {
                publishLoadedState(emptyList(), emptyList(), 0)
                return@withLock
            }
            val all = mutableListOf<EspansoMatch>()
            val files = mutableListOf<SnippetFileInfo>()
            var skippedFileCount = 0
            dir.listFiles { f -> f.extension.lowercase() in SUPPORTED_SNIPPET_EXTENSIONS }
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    runCatching {
                        val yaml = file.inputStream().use(SnippetImportPolicy::readYamlTextLimited)
                        val result = EspansoMatchParser.parseWithDiagnostics(yaml)
                        all.addAll(result.matches)
                        files += SnippetFileInfo(file.name, result.matches.size)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        skippedFileCount++
                        flogWarning {
                            "SnippetManager.loadAll skipped ${file.name}: ${error::class.java.simpleName}"
                        }
                    }
                }
            publishLoadedState(all, files, skippedFileCount)
        }
    }

    suspend fun importYaml(yamlContent: String, filename: String): SnippetImportResult =
        withContext(Dispatchers.IO) {
            require(yamlContent.toByteArray(Charsets.UTF_8).size <= SnippetImportPolicy.MaxYamlBytes) {
                "Snippet YAML exceeds the ${SnippetImportPolicy.MaxYamlBytes / (1024L * 1024L)} MiB safety limit."
            }
            val result = EspansoMatchParser.parseWithDiagnostics(yamlContent)
            if (result.matches.isEmpty()) {
                return@withContext SnippetImportResult(
                    importedCount = 0,
                    diagnostics = result.diagnostics,
                )
            }
            val safeName = sanitizeFileName(filename)
            val target = File(snippetsDir, safeName)
            target.writeText(yamlContent)
            loadAll()
            SnippetImportResult(
                importedCount = result.matches.size,
                diagnostics = result.diagnostics,
            )
        }

    suspend fun removeFile(filename: String): Boolean = withContext(Dispatchers.IO) {
        val target = safeFile(filename) ?: return@withContext false
        val removed = target.isFile && target.delete()
        if (!removed) {
            flogWarning { "SnippetManager.removeFile could not delete ${target.name}" }
        }
        loadAll()
        removed
    }

    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        val files = snippetsDir.listFiles().orEmpty()
        var success = true
        files.forEach { file ->
            if (!file.delete()) {
                success = false
                flogWarning { "SnippetManager.clearAll could not delete ${file.name}" }
            }
        }
        if (!success) loadAll() else publishLoadedState(emptyList(), emptyList(), 0)
        success
    }

    private fun publishLoadedState(
        matches: List<EspansoMatch>,
        files: List<SnippetFileInfo>,
        skippedFileCount: Int,
    ) {
        _snippets.value = matches
        _fileStates.value = files
        _loadReport.value = SnippetLoadReport(skippedFileCount)
    }

    private fun safeFile(filename: String): File? {
        val directory = snippetsDir.canonicalFile
        val target = File(directory, filename).canonicalFile
        return target.takeIf { it.parentFile == directory }
    }

    /**
     * Turns an arbitrary source name into one this directory can hold, and that
     * [loadAll] will actually pick back up.
     *
     * The extension is not cosmetic. `loadAll` lists `.yml` and `.yaml` only, so
     * a name that loses its suffix produces a file that was written, reported as
     * imported, and is then invisible in the file list and unreachable from the
     * delete action. That is exactly what a SAF pick used to do: the caller
     * passes `uri.lastPathSegment`, which for a document URI is an id such as
     * `msf:1000000123` or `primary:Download/snips.yml`, and mapping the
     * disallowed characters to underscores left `msf_1000000123` behind.
     */
    internal fun sanitizeFileName(filename: String): String {
        val sanitized = filename
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(128)
            .takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: return DEFAULT_SNIPPET_FILE_NAME
        val hasSupportedExtension = SUPPORTED_SNIPPET_EXTENSIONS.any { extension ->
            sanitized.endsWith(".$extension", ignoreCase = true)
        }
        if (hasSupportedExtension) return sanitized
        // Trim before appending so the result still fits the same budget.
        val stem = sanitized.take(128 - DEFAULT_SNIPPET_EXTENSION.length - 1).trimEnd('.')
        return if (stem.isEmpty()) {
            DEFAULT_SNIPPET_FILE_NAME
        } else {
            "$stem.$DEFAULT_SNIPPET_EXTENSION"
        }
    }
}

private val SUPPORTED_SNIPPET_EXTENSIONS = setOf("yml", "yaml")
private const val DEFAULT_SNIPPET_EXTENSION = "yml"
private const val DEFAULT_SNIPPET_FILE_NAME = "import.$DEFAULT_SNIPPET_EXTENSION"

internal object SnippetImportPolicy {
    const val MaxYamlBytes: Long = 2L * 1024L * 1024L

    fun readYamlTextLimited(inputStream: InputStream, maxBytes: Long = MaxYamlBytes): String {
        require(maxBytes > 0L) { "Argument `maxBytes` must be greater than 0" }
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = inputStream.read(buffer)
            if (read < 0) break
            total += read.toLong()
            if (total > maxBytes) {
                error("Snippet YAML exceeds the ${maxBytes / (1024L * 1024L)} MiB safety limit.")
            }
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

data class SnippetImportResult(
    val importedCount: Int,
    val diagnostics: ImportDiagnostics = ImportDiagnostics.NONE,
)

internal object SnippetExpansionPolicy {

    data class ExpansionResult(
        val triggerLength: Int,
        val replacement: String,
    )

    fun findMatch(
        textBeforeCursor: String,
        snippets: List<EspansoMatch>,
        isSensitiveField: Boolean,
    ): ExpansionResult? {
        if (isSensitiveField || snippets.isEmpty() || textBeforeCursor.isEmpty()) return null
        for (match in snippets) {
            if (match.passive || match.trigger.isBlank()) continue
            if (textBeforeCursor.endsWith(match.trigger)) {
                val expanded = if (match.vars.isEmpty()) {
                    match.replace
                } else {
                    EspansoVarsExpander.expand(match)
                }
                return ExpansionResult(
                    triggerLength = match.trigger.length,
                    replacement = expanded,
                )
            }
        }
        return null
    }
}
