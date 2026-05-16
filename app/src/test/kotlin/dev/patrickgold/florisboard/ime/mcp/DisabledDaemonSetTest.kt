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

package dev.patrickgold.florisboard.ime.mcp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DisabledDaemonSetTest : FunSpec({

    test("parse returns empty set for blank input") {
        DisabledDaemonSet.parse("") shouldBe emptySet()
        DisabledDaemonSet.parse("   ") shouldBe emptySet()
    }

    test("parse splits newline-separated package names") {
        DisabledDaemonSet.parse("com.daemon.a\ncom.daemon.b") shouldBe
            setOf("com.daemon.a", "com.daemon.b")
    }

    test("parse trims whitespace inside each entry") {
        DisabledDaemonSet.parse("  com.daemon.a  \n\tcom.daemon.b\n") shouldBe
            setOf("com.daemon.a", "com.daemon.b")
    }

    test("parse drops blank lines") {
        DisabledDaemonSet.parse("com.daemon.a\n\n\ncom.daemon.b\n") shouldBe
            setOf("com.daemon.a", "com.daemon.b")
    }

    test("encode joins package names with newlines, sorted for stable diff") {
        DisabledDaemonSet.encode(listOf("com.daemon.b", "com.daemon.a")) shouldBe
            "com.daemon.a\ncom.daemon.b"
    }

    test("encode deduplicates and drops blanks") {
        DisabledDaemonSet.encode(listOf("com.daemon.a", "", "com.daemon.a", "   ")) shouldBe
            "com.daemon.a"
    }

    test("encode returns empty string for empty input") {
        DisabledDaemonSet.encode(emptyList()) shouldBe ""
    }

    test("round-trips through parse+encode is idempotent") {
        val input = "com.daemon.a\ncom.daemon.b\ncom.daemon.c"
        DisabledDaemonSet.encode(DisabledDaemonSet.parse(input)) shouldBe input
    }

    test("add inserts the package name") {
        DisabledDaemonSet.add("com.daemon.a", "com.daemon.b") shouldBe
            "com.daemon.a\ncom.daemon.b"
    }

    test("add of an existing package is a no-op") {
        DisabledDaemonSet.add("com.daemon.a", "com.daemon.a") shouldBe "com.daemon.a"
    }

    test("add of blank is a no-op") {
        DisabledDaemonSet.add("com.daemon.a", "") shouldBe "com.daemon.a"
        DisabledDaemonSet.add("com.daemon.a", "   ") shouldBe "com.daemon.a"
    }

    test("remove drops the package name from the set") {
        DisabledDaemonSet.remove("com.daemon.a\ncom.daemon.b", "com.daemon.a") shouldBe "com.daemon.b"
    }

    test("remove of a missing package is a no-op") {
        DisabledDaemonSet.remove("com.daemon.a", "com.daemon.b") shouldBe "com.daemon.a"
    }

    test("contains is true only for present packages") {
        val serialized = "com.daemon.a\ncom.daemon.b"
        DisabledDaemonSet.contains(serialized, "com.daemon.a") shouldBe true
        DisabledDaemonSet.contains(serialized, "com.daemon.c") shouldBe false
        DisabledDaemonSet.contains(serialized, "") shouldBe false
    }
})
