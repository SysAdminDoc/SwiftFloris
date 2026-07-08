/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.editor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class EditorClipboardSuppressionContractTest : FunSpec({
    val source = locateEditorInstanceSource().readText()

    test("suppressed cut and copy still place sensitive text on the system clipboard") {
        val cutBody = extractFunctionBody(source, "fun performClipboardCut(")
        val copyBody = extractFunctionBody(source, "fun performClipboardCopy(")

        for (body in listOf(cutBody, copyBody)) {
            body shouldContain "if (shouldSuppressClipboardHistory())"
            body shouldContain "setSensitivePrimaryClipWithoutHistory(text.toString())"
            body shouldContain "clipboardManager.addNewPlaintext(text.toString())"
        }
    }

    test("suppressed clipboard helper bypasses history and marks the clip sensitive") {
        val body = extractFunctionBody(source, "private fun setSensitivePrimaryClipWithoutHistory(")

        body shouldContain "clipboardManager.updatePrimaryClip"
        body shouldContain "ClipboardItem.text(text).copy(isSensitive = true)"
        body shouldNotContain "clipboardManager.addNewPlaintext"
    }
})

private fun locateEditorInstanceSource(): File {
    val candidates = listOf(
        File("app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt"),
        File("src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt"),
    )
    return candidates.firstOrNull { it.exists() }
        ?: error("EditorInstance.kt not reachable from working directory ${File(".").absolutePath}")
}

private fun extractFunctionBody(source: String, startsWith: String): String {
    val declStart = source.indexOf(startsWith)
    require(declStart >= 0) { "Function declaration '$startsWith' not found in source" }
    val openBrace = source.indexOf('{', declStart)
    require(openBrace >= 0) { "Function '$startsWith' has no opening brace" }

    var depth = 0
    var i = openBrace
    while (i < source.length) {
        when (source[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return source.substring(openBrace, i + 1)
            }
        }
        i++
    }
    error("Function '$startsWith' is missing its closing brace")
}
