/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class StringsTest : FunSpec({
    context("Test String.curlyFormat (arg mapping)") {
        context("With no template variables") {
            withData(
                Triple("Hello World!", arrayOf(), "Hello World!"),
                Triple("Hello name!", arrayOf("name" to "Alex"), "Hello name!"),
                Triple("Test123", arrayOf(), "Test123"),
                Triple("", arrayOf(), ""),
                Triple(" ", arrayOf(), " "),
            ) { (inputStr, args, formattedStr) ->
                inputStr.curlyFormat(*args) shouldBe formattedStr
            }
        }

        context("With only curly braces in template") {
            withData(
                Triple("{", arrayOf(), "{"),
                Triple("}", arrayOf(), "}"),
                Triple("{}", arrayOf(), "{}"),
                Triple("}{", arrayOf(), "}{"),
                Triple("{{", arrayOf(), "{{"),
                Triple("}}", arrayOf(), "}}"),

                Triple("{", arrayOf("" to "Alex"), "{"),
                Triple("}", arrayOf("" to "Alex"), "}"),
                Triple("{}", arrayOf("" to "Alex"), "{}"),
                Triple("}{", arrayOf("" to "Alex"), "}{"),
                Triple("{{", arrayOf("" to "Alex"), "{{"),
                Triple("}}", arrayOf("" to "Alex"), "}}"),

                Triple("{", arrayOf("name" to "Alex"), "{"),
                Triple("}", arrayOf("name" to "Alex"), "}"),
                Triple("{}", arrayOf("name" to "Alex"), "{}"),
                Triple("}{", arrayOf("name" to "Alex"), "}{"),
                Triple("{{", arrayOf("name" to "Alex"), "{{"),
                Triple("}}", arrayOf("name" to "Alex"), "}}"),
            ) { (inputStr, args, formattedStr) ->
                inputStr.curlyFormat(*args) shouldBe formattedStr
            }
        }

        context("With curly braces and named variables in template") {
            withData(
                Triple("{name", arrayOf(), "{name"),
                Triple("}name", arrayOf(), "}name"),
                Triple("name{", arrayOf(), "name{"),
                Triple("name}", arrayOf(), "name}"),
                Triple("{name}", arrayOf(), "{name}"),
                Triple("}name{", arrayOf(), "}name{"),
                Triple("{name{", arrayOf(), "{name{"),
                Triple("}name}", arrayOf(), "}name}"),

                Triple("{name", arrayOf("name" to "Alex"), "{name"),
                Triple("}name", arrayOf("name" to "Alex"), "}name"),
                Triple("name{", arrayOf("name" to "Alex"), "name{"),
                Triple("name}", arrayOf("name" to "Alex"), "name}"),
                Triple("{name}", arrayOf("name" to "Alex"), "Alex"),
                Triple("}name{", arrayOf("name" to "Alex"), "}name{"),
                Triple("{name{", arrayOf("name" to "Alex"), "{name{"),
                Triple("}name}", arrayOf("name" to "Alex"), "}name}"),

                Triple("{name_with_underscore", arrayOf("name_with_underscore" to "Alex"), "{name_with_underscore"),
                Triple("}name_with_underscore", arrayOf("name_with_underscore" to "Alex"), "}name_with_underscore"),
                Triple("name_with_underscore{", arrayOf("name_with_underscore" to "Alex"), "name_with_underscore{"),
                Triple("name_with_underscore}", arrayOf("name_with_underscore" to "Alex"), "name_with_underscore}"),
                Triple("{name_with_underscore}", arrayOf("name_with_underscore" to "Alex"), "Alex"),
                Triple("}name_with_underscore{", arrayOf("name_with_underscore" to "Alex"), "}name_with_underscore{"),
                Triple("{name_with_underscore{", arrayOf("name_with_underscore" to "Alex"), "{name_with_underscore{"),
                Triple("}name_with_underscore}", arrayOf("name_with_underscore" to "Alex"), "}name_with_underscore}"),
            ) { (inputStr, args, formattedStr) ->
                inputStr.curlyFormat(*args) shouldBe formattedStr
            }
        }

        context("With positional variables in template") {
            withData(
                Triple("Howdy {0}!", arrayOf(), "Howdy {0}!"),
                Triple("Howdy {1}!", arrayOf(), "Howdy {1}!"),
                Triple("Howdy {-1}!", arrayOf(), "Howdy {-1}!"),
                Triple("Howdy {11}!", arrayOf(), "Howdy {11}!"),
                Triple("Howdy {1,1}!", arrayOf(), "Howdy {1,1}!"),
                Triple("Howdy {1.1}!", arrayOf(), "Howdy {1.1}!"),
                Triple("Howdy {00}!", arrayOf(), "Howdy {00}!"),
                Triple("Howdy {01}!", arrayOf(), "Howdy {01}!"),
                Triple("Howdy {0} and {0}!", arrayOf(), "Howdy {0} and {0}!"),
                Triple("Howdy {0} and {1}!", arrayOf(), "Howdy {0} and {1}!"),
                Triple("Howdy {1} and {0}!", arrayOf(), "Howdy {1} and {0}!"),
                Triple("Howdy {1} and {1}!", arrayOf(), "Howdy {1} and {1}!"),

                Triple("Howdy {0}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy Alex!"),
                Triple("Howdy {1}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy Emily!"),
                Triple("Howdy {-1}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy {-1}!"),
                Triple("Howdy {11}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy {11}!"),
                Triple("Howdy {11}!", arrayOf(
                    "" to "Alex", "" to "Emily", "" to "Tom", "" to "Angela", "" to "Bob",
                    "" to "Elon", "" to "Mark", "" to "Samantha", "" to "Alice", "" to "Michael",
                    "" to "Andy", "" to "Tamara",
                ), "Howdy Tamara!"),
                Triple("Howdy {1,1}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy {1,1}!"),
                Triple("Howdy {1.1}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy {1.1}!"),
                Triple("Howdy {00}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy {00}!"),
                Triple("Howdy {01}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy {01}!"),
                Triple("Howdy {0} and {0}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy Alex and Alex!"),
                Triple("Howdy {0} and {1}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy Alex and Emily!"),
                Triple("Howdy {1} and {0}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy Emily and Alex!"),
                Triple("Howdy {1} and {1}!", arrayOf("" to "Alex", "" to "Emily"), "Howdy Emily and Emily!"),
            ) { (inputStr, args, formattedStr) ->
                inputStr.curlyFormat(*args) shouldBe formattedStr
            }
        }
    }

    context("Test String.curlyFormat (self-referential values)") {
        // Several call sites interpolate text the user does not control. A ZIP
        // entry name is echoed verbatim into a SecurityException message, and
        // that message is then interpolated into a "... Details: {error_message}"
        // toast. A value carrying its own placeholder must be inserted once and
        // never rescanned, or restoring a crafted archive hangs or OOMs Settings.
        test("a value equal to its own placeholder is substituted exactly once") {
            "Details: {error_message}".curlyFormat(
                "error_message" to "{error_message}",
            ) shouldBe "Details: {error_message}"
        }

        test("a value containing its own placeholder does not grow without bound") {
            val hostile = "entry name '../{error_message}' is unsafe"
            "Details: {error_message}".curlyFormat(
                "error_message" to hostile,
            ) shouldBe "Details: $hostile"
        }

        test("a value containing a later argument's placeholder is left alone") {
            "{first} then {second}".curlyFormat(
                "first" to "carries {second}",
                "second" to "B",
            ) shouldBe "carries {second} then B"
        }

        test("the factory overload also refuses to rescan substituted text") {
            "Details: {error_message}".curlyFormat { key ->
                if (key == "error_message") "loop {error_message} loop" else null
            } shouldBe "Details: loop {error_message} loop"
        }

        test("an empty value does not swallow the placeholder after it") {
            // The old scanner resumed one character past the start of what it had
            // just inserted, so a zero-length value left the cursor beyond the
            // next opening brace and that placeholder was never substituted.
            "{a}{b}".curlyFormat { key -> if (key == "a") "" else "Y" } shouldBe "Y"
            "{a}{b}".curlyFormat("a" to "", "b" to "Y") shouldBe "Y"
        }

        test("every occurrence of a placeholder is still replaced") {
            "{a} and {a} and {a}".curlyFormat("a" to "x") shouldBe "x and x and x"
        }
    }

    context("Test String.curlyFormat (arg factory with dictionary)") {
        val dict = listOf(
            "app_name" to "UnitTestApp",
            "test_label" to "Test Label",
        )
        withData(
            Pair("Welcome to {app_name}", "Welcome to UnitTestApp"),
            Pair("Welcome to {app_name} and {file_name}", "Welcome to UnitTestApp and {file_name}"),
            Pair("{ Curly {test_label} }", "{ Curly Test Label }"),
        ) { (inputStr, formattedStr) ->
            inputStr.curlyFormat { key ->
                dict.find { it.first == key }?.second
            } shouldBe formattedStr
        }
    }
})
