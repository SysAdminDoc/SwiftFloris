/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.translate.TranslationSuppressionReason
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
        body shouldContain "R.string.quick_action__translation_selection_changed"
        body shouldNotContain ".translate(raw, sourceLocale, targetLocale)"
        body shouldNotContain "Selection changed before translation completed."
    }

    test("suppressed translation outcomes map to localized string resources") {
        translateSelectionSuppressedMessageRes(TranslationSuppressionReason.BlankInput) shouldBe null
        translateSelectionSuppressedMessageRes(TranslationSuppressionReason.ConsentRequired) shouldBe
            R.string.quick_action__translation_consent_required
        translateSelectionSuppressedMessageRes(TranslationSuppressionReason.SensitiveField) shouldBe
            R.string.quick_action__translation_sensitive_field
        translateSelectionSuppressedMessageRes(TranslationSuppressionReason.SourceEqualsTarget) shouldBe
            R.string.quick_action__translation_same_language
        translateSelectionSuppressedMessageRes(TranslationSuppressionReason.SourceLocaleDetectionFailed) shouldBe
            R.string.quick_action__translation_source_detection_failed
        translateSelectionSuppressedMessageRes(TranslationSuppressionReason.NoTargetLocaleResolved) shouldBe
            R.string.quick_action__translation_target_missing
        translateSelectionSuppressedMessageRes(TranslationSuppressionReason.NoInstalledPair) shouldBe
            R.string.quick_action__translation_pair_unavailable
        translateSelectionSuppressedMessageRes(TranslationSuppressionReason.TranslatorUnavailable) shouldBe
            R.string.quick_action__translation_pair_unavailable
    }

    test("translation quick action does not keep hard-coded English failure toasts") {
        val body = extractObjectBody(locateQuickActionSource().readText(), "data object TranslateSelection")

        body shouldNotContain "Translation is not available in this context."
        body shouldNotContain "Enable translation addon consent in Privacy settings."
        body shouldNotContain "Translation is blocked in sensitive fields."
        body shouldNotContain "Choose a different translation target language."
        body shouldNotContain "Could not detect the selection language."
        body shouldNotContain "Choose a translation target language first."
        body shouldNotContain "Install an InlineTranslator addon and language pack to translate selections."
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
