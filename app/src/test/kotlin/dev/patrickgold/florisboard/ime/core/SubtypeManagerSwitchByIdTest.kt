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

package dev.patrickgold.florisboard.ime.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class SubtypeManagerSwitchByIdTest : FunSpec({
    test("switchToSubtypeById uses one nullable lookup before activation") {
        val body = extractFunctionBody(
            source = locateSubtypeManagerSource().readText(),
            startsWith = "fun switchToSubtypeById(id: Long)",
        )

        body shouldContain "val subtype = getSubtypeById(id) ?: return@launch"
        body shouldContain "activateSubtype(subtype, source = SubtypeSwitchSource.Manual)"
        body shouldNotContain "getSubtypeById(id)!!"
        body shouldNotContain "subtypes.any { it.id == id }"
    }
})

private fun locateSubtypeManagerSource(): File {
    val candidates = listOf(
        File("app/src/main/kotlin/dev/patrickgold/florisboard/ime/core/SubtypeManager.kt"),
        File("src/main/kotlin/dev/patrickgold/florisboard/ime/core/SubtypeManager.kt"),
    )
    return candidates.firstOrNull { it.exists() && it.canRead() }
        ?: error("Unable to locate SubtypeManager.kt from ${File("").absolutePath}")
}

private fun extractFunctionBody(source: String, startsWith: String): String {
    val declStart = source.indexOf(startsWith)
    require(declStart >= 0) { "Function declaration '$startsWith' not found in source" }
    val openBrace = source.indexOf('{', declStart)
    require(openBrace >= 0) { "Function body for '$startsWith' not found in source" }

    var depth = 0
    var i = openBrace
    while (i < source.length) {
        when (source[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return source.substring(openBrace, i + 1)
                }
            }
        }
        i++
    }
    error("Function body for '$startsWith' is not balanced")
}
