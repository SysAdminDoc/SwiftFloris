/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class QuickActionTranslateSelectionTest : FunSpec({
    test("TranslateSelection routes through TranslationRouter with consent and sensitive-field inputs") {
        val source = locateQuickActionSource().readText()
        val body = extractObjectBody(source, "data object TranslateSelection")

        body shouldContain "TranslationRouter("
        body shouldContain "TranslationRouter.Request("
        body shouldContain "inputType = activeInfo.inputAttributes.raw"
        body shouldContain "imeOptions = activeInfo.imeOptions.raw"
        body shouldContain "prefs.privacy.translationConsent.get().allowsInvocation()"
        body shouldContain "withContext(Dispatchers.IO)"
        body shouldNotContain ".translate(raw, sourceLocale, targetLocale)"
    }

    test("suppressed translation outcomes surface specific user feedback") {
        translateSelectionSuppressedMessage("blank input") shouldBe null
        translateSelectionSuppressedMessage("consent required") shouldBe
            "Enable translation addon consent in Privacy settings."
        translateSelectionSuppressedMessage("sensitive field") shouldBe
            "Translation is blocked in sensitive fields."
        translateSelectionSuppressedMessage("source == target") shouldBe
            "Choose a different translation target language."
        translateSelectionSuppressedMessage("source-locale detection failed") shouldBe
            "Could not detect the selection language."
        translateSelectionSuppressedMessage("no target locale resolved") shouldBe
            "Choose a translation target language first."
        translateSelectionSuppressedMessage("no installed pair for en->es") shouldBe
            "Install an InlineTranslator addon and language pack to translate selections."
        translateSelectionSuppressedMessage("translator returned Unavailable") shouldBe
            "Install an InlineTranslator addon and language pack to translate selections."
    }
})

private fun locateQuickActionSource(): File {
    val candidates = listOf(
        File("app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/QuickAction.kt"),
        File("src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/QuickAction.kt"),
    )
    return candidates.firstOrNull { it.exists() }
        ?: error("QuickAction.kt not reachable from working directory ${File(".").absolutePath}")
}

private fun extractObjectBody(source: String, startsWith: String): String {
    val declStart = source.indexOf(startsWith)
    require(declStart >= 0) { "Object declaration '$startsWith' not found in source" }
    val openBrace = source.indexOf('{', declStart)
    require(openBrace >= 0) { "Object '$startsWith' has no opening brace" }

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
    error("Object '$startsWith' is missing its closing brace")
}
