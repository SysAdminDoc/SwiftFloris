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
import io.kotest.matchers.shouldBe
import java.util.Locale

class SelectedTextCasePolicyTest : FunSpec({
    val locale = Locale.ENGLISH

    test("detectCase identifies lowercase") {
        SelectedTextCasePolicy.detectCase("hello world", locale) shouldBe
            SelectedTextCasePolicy.CaseMode.LOWER
    }

    test("detectCase identifies uppercase") {
        SelectedTextCasePolicy.detectCase("HELLO WORLD", locale) shouldBe
            SelectedTextCasePolicy.CaseMode.UPPER
    }

    test("detectCase identifies title case") {
        SelectedTextCasePolicy.detectCase("Hello World", locale) shouldBe
            SelectedTextCasePolicy.CaseMode.TITLE
    }

    test("detectCase treats mixed case as lowercase") {
        SelectedTextCasePolicy.detectCase("hELLO wORLD", locale) shouldBe
            SelectedTextCasePolicy.CaseMode.LOWER
    }

    test("detectCase handles empty string") {
        SelectedTextCasePolicy.detectCase("", locale) shouldBe
            SelectedTextCasePolicy.CaseMode.LOWER
    }

    test("detectCase handles single word title case") {
        SelectedTextCasePolicy.detectCase("Hello", locale) shouldBe
            SelectedTextCasePolicy.CaseMode.TITLE
    }

    test("nextCase cycles lower -> title -> upper -> lower") {
        SelectedTextCasePolicy.nextCase(SelectedTextCasePolicy.CaseMode.LOWER) shouldBe
            SelectedTextCasePolicy.CaseMode.TITLE
        SelectedTextCasePolicy.nextCase(SelectedTextCasePolicy.CaseMode.TITLE) shouldBe
            SelectedTextCasePolicy.CaseMode.UPPER
        SelectedTextCasePolicy.nextCase(SelectedTextCasePolicy.CaseMode.UPPER) shouldBe
            SelectedTextCasePolicy.CaseMode.LOWER
    }

    test("applyCase produces correct lowercase") {
        SelectedTextCasePolicy.applyCase("Hello World", SelectedTextCasePolicy.CaseMode.LOWER, locale) shouldBe
            "hello world"
    }

    test("applyCase produces correct uppercase") {
        SelectedTextCasePolicy.applyCase("Hello World", SelectedTextCasePolicy.CaseMode.UPPER, locale) shouldBe
            "HELLO WORLD"
    }

    test("applyCase produces correct title case") {
        SelectedTextCasePolicy.applyCase("hello world", SelectedTextCasePolicy.CaseMode.TITLE, locale) shouldBe
            "Hello World"
    }

    test("title case preserves hyphens and apostrophes as word boundaries") {
        SelectedTextCasePolicy.applyCase(
            "don't-know-why",
            SelectedTextCasePolicy.CaseMode.TITLE,
            locale,
        ) shouldBe "Don'T-Know-Why"
    }

    test("full cycle on a lowercase phrase") {
        val text = "hello world"
        val step1 = SelectedTextCasePolicy.applyCase(
            text,
            SelectedTextCasePolicy.nextCase(SelectedTextCasePolicy.detectCase(text, locale)),
            locale,
        )
        step1 shouldBe "Hello World"

        val step2 = SelectedTextCasePolicy.applyCase(
            step1,
            SelectedTextCasePolicy.nextCase(SelectedTextCasePolicy.detectCase(step1, locale)),
            locale,
        )
        step2 shouldBe "HELLO WORLD"

        val step3 = SelectedTextCasePolicy.applyCase(
            step2,
            SelectedTextCasePolicy.nextCase(SelectedTextCasePolicy.detectCase(step2, locale)),
            locale,
        )
        step3 shouldBe "hello world"
    }
})
