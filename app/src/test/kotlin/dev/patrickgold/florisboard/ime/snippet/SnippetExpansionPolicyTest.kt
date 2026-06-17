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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class SnippetExpansionPolicyTest : FunSpec({

    val snippets = listOf(
        EspansoMatch(trigger = ":sig", replace = "Best regards,\nMatt"),
        EspansoMatch(trigger = ":bug", replace = "I found a bug in"),
        EspansoMatch(trigger = ":addr", replace = "123 Main St"),
        EspansoMatch(trigger = ":skip", replace = "passive", passive = true),
    )

    test("matches trigger at end of text") {
        val result = SnippetExpansionPolicy.findMatch(
            textBeforeCursor = "Hi :sig",
            snippets = snippets,
            isSensitiveField = false,
        )
        result.shouldNotBeNull()
        result.triggerLength shouldBe 4
        result.replacement shouldBe "Best regards,\nMatt"
    }

    test("matches trigger that is the entire text") {
        val result = SnippetExpansionPolicy.findMatch(
            textBeforeCursor = ":bug",
            snippets = snippets,
            isSensitiveField = false,
        )
        result.shouldNotBeNull()
        result.triggerLength shouldBe 4
        result.replacement shouldBe "I found a bug in"
    }

    test("no match when trigger is not at end") {
        val result = SnippetExpansionPolicy.findMatch(
            textBeforeCursor = ":sig more text",
            snippets = snippets,
            isSensitiveField = false,
        )
        result.shouldBeNull()
    }

    test("no match on sensitive fields") {
        val result = SnippetExpansionPolicy.findMatch(
            textBeforeCursor = ":sig",
            snippets = snippets,
            isSensitiveField = true,
        )
        result.shouldBeNull()
    }

    test("no match with empty text") {
        val result = SnippetExpansionPolicy.findMatch(
            textBeforeCursor = "",
            snippets = snippets,
            isSensitiveField = false,
        )
        result.shouldBeNull()
    }

    test("no match with empty snippet list") {
        val result = SnippetExpansionPolicy.findMatch(
            textBeforeCursor = ":sig",
            snippets = emptyList(),
            isSensitiveField = false,
        )
        result.shouldBeNull()
    }

    test("passive snippets are skipped") {
        val result = SnippetExpansionPolicy.findMatch(
            textBeforeCursor = ":skip",
            snippets = snippets,
            isSensitiveField = false,
        )
        result.shouldBeNull()
    }

    test("first matching trigger wins") {
        val dupes = listOf(
            EspansoMatch(trigger = ":x", replace = "first"),
            EspansoMatch(trigger = ":x", replace = "second"),
        )
        val result = SnippetExpansionPolicy.findMatch(
            textBeforeCursor = ":x",
            snippets = dupes,
            isSensitiveField = false,
        )
        result.shouldNotBeNull()
        result.replacement shouldBe "first"
    }
})
