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

package org.florisboard.lib.kotlin

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


fun String.safeSubstring(startIndex: Int): String {
    return try {
        this.substring(startIndex)
    } catch (_: IndexOutOfBoundsException) {
        ""
    }
}

fun String.safeSubstring(startIndex: Int, endIndex: Int): String {
    return try {
        this.substring(startIndex, endIndex)
    } catch (_: IndexOutOfBoundsException) {
        ""
    }
}

private const val CURLY_ARG_OPEN = '{'
private const val CURLY_ARG_CLOSE = '}'

typealias CurlyArg = Pair<String, Any?>

/**
 * Substitutes `{name}` placeholders using [argValueFactory].
 *
 * The template is scanned exactly once, left to right, and substituted values
 * are appended verbatim without ever being re-examined. That matters because
 * several call sites interpolate text the user does not control, such as a ZIP
 * entry name echoed back inside a `SecurityException` message. A value that
 * contains its own placeholder would otherwise be substituted into itself
 * forever, hanging or exhausting the heap of whichever process formatted it.
 */
fun String.curlyFormat(argValueFactory: (argName: String) -> String?): String {
    contract {
        callsInPlace(argValueFactory, InvocationKind.UNKNOWN)
    }
    if (isEmpty()) return this
    val out = StringBuilder(length)
    var cursor = 0
    while (cursor < length) {
        val openIndex = indexOf(CURLY_ARG_OPEN, cursor)
        if (openIndex < 0) break
        val closeIndex = indexOf(CURLY_ARG_CLOSE, openIndex + 1)
        if (closeIndex < 0) break
        // A second '{' before the closing brace means the first one opened
        // nothing; restart the scan from the inner brace.
        val nextOpenIndex = indexOf(CURLY_ARG_OPEN, openIndex + 1)
        if (nextOpenIndex in 0 until closeIndex) {
            out.append(this, cursor, nextOpenIndex)
            cursor = nextOpenIndex
            continue
        }
        val argValue = argValueFactory(substring(openIndex + 1, closeIndex))
        if (argValue != null) {
            out.append(this, cursor, openIndex).append(argValue)
        } else {
            out.append(this, cursor, closeIndex + 1)
        }
        cursor = closeIndex + 1
    }
    if (cursor < length) {
        out.append(this, cursor, length)
    }
    return out.toString()
}

fun String.curlyFormat(vararg args: CurlyArg): String {
    return this.curlyFormat(args.asList())
}

fun String.curlyFormat(args: List<CurlyArg>): String {
    if (args.isEmpty()) return this
    // Positional and named keys are registered in argument order and the first
    // binding for a key wins, matching how the arguments used to be applied one
    // after another over the whole template.
    val bindings = HashMap<String, String>(args.size * 2)
    for ((index, arg) in args.withIndex()) {
        val (argName, argValue) = arg
        val rendered = argValue.toString()
        bindings.putIfAbsent(index.toString(), rendered)
        if (argName.isNotBlank()) {
            bindings.putIfAbsent(argName, rendered)
        }
    }
    return curlyFormat { bindings[it] }
}
