/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.translate.TranslationSuppressionReason
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class QuickActionTranslateSelectionTest : FunSpec({
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
        translateSelectionSuppressedMessageRes(TranslationSuppressionReason.TranslatorTimedOut) shouldBe
            R.string.quick_action__translation_timeout
        translateSelectionSuppressedMessageRes(TranslationSuppressionReason.TranslationCancelled) shouldBe
            R.string.quick_action__translation_cancelled
    }
})
