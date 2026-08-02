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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

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

    private val snippetsDir: File
        get() = File(filesDir, "snippets").also { it.mkdirs() }

    /**
     * Loads persisted snippets for IME use without requiring the Settings screen to be opened.
     *
     * The returned job is shared so repeated application startup calls do not start concurrent
     * file scans. Settings can still call [loadAll] when it explicitly refreshes its view.
     */
    fun initialize(): Job = initializationJob

    fun loadAll() {
        val dir = snippetsDir
        if (!dir.exists()) {
            _snippets.value = emptyList()
            return
        }
        val all = mutableListOf<EspansoMatch>()
        dir.listFiles { f -> f.extension == "yml" || f.extension == "yaml" }
            ?.forEach { file ->
                runCatching {
                    val yaml = file.inputStream().use(SnippetImportPolicy::readYamlTextLimited)
                    all.addAll(EspansoMatchParser.parse(yaml))
                }
            }
        _snippets.value = all
    }

    fun importYaml(yamlContent: String, filename: String): SnippetImportResult {
        val result = EspansoMatchParser.parseWithDiagnostics(yamlContent)
        if (result.matches.isEmpty()) {
            return SnippetImportResult(
                importedCount = 0,
                diagnostics = result.diagnostics,
            )
        }
        val safeName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val target = File(snippetsDir, safeName)
        target.writeText(yamlContent)
        loadAll()
        return SnippetImportResult(
            importedCount = result.matches.size,
            diagnostics = result.diagnostics,
        )
    }

    fun removeFile(filename: String) {
        File(snippetsDir, filename).delete()
        loadAll()
    }

    fun listFiles(): List<String> {
        return snippetsDir.listFiles { f -> f.extension == "yml" || f.extension == "yaml" }
            ?.map { it.name }
            .orEmpty()
    }

    fun clearAll() {
        snippetsDir.listFiles()?.forEach { it.delete() }
        _snippets.value = emptyList()
    }
}

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
