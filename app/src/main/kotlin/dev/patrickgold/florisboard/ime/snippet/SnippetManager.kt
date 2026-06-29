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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SnippetManager(private val context: Context) {

    private val _snippets = MutableStateFlow<List<EspansoMatch>>(emptyList())
    val snippets: StateFlow<List<EspansoMatch>> = _snippets.asStateFlow()

    private val snippetsDir: File
        get() = File(context.filesDir, "snippets").also { it.mkdirs() }

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
                    all.addAll(EspansoMatchParser.parse(file.readText()))
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
