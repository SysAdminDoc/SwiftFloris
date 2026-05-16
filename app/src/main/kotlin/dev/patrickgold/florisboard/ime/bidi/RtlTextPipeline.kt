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

package dev.patrickgold.florisboard.ime.bidi

/**
 * ROADMAP §7 L4.8 — combined RTL text pipeline.
 *
 * The RTL stack now ships five independent transforms:
 *  - [ArabicShaper]              — Arabic connected-form shaping.
 *  - [PersianUrduNormalizer]     — Persian Yeh/Kaf + Urdu Tatweel
 *                                  cleanup.
 *  - [HebrewNiqqudNormalizer]    — Hebrew Niqqud strip + Geresh/
 *                                  Gershayim rewrite.
 *  - [ArabicPersianNumeralConverter] — Western ↔ Arabic-Indic ↔
 *                                      Extended Arabic-Indic.
 *  - [VisualLogicalReorderer]    — visual ↔ logical reordering.
 *
 * Each transform is useful in isolation, but the typical commit path
 * (`EditorInstance.commitText`) wants to run several at once with
 * subtype-specific knobs.  This pipeline composes them, with toggles
 * matching the existing Settings → RTL screen one-to-one.
 *
 * Pipeline order matters and is fixed:
 *
 *   1. Niqqud strip (Hebrew only) — removes characters before shaping.
 *   2. Persian/Urdu normalise — folds Persian Yeh/Kaf BEFORE shaping
 *      so the shaper sees canonical letters.
 *   3. Arabic shape — runs the FE70-FEFC mapping.
 *   4. Numeral convert — independent of shape, runs last.
 *
 * This file is intentionally compose-only; the individual transforms
 * keep their existing one-shot API so the pipeline is the *option*,
 * not the default.
 */
object RtlTextPipeline {

    /**
     * Run the pipeline against [input] using [options]. Returns the
     * transformed string. Unchanged input round-trips cheaply when
     * every option is at its default (no-op).
     */
    fun process(input: String, options: Options): String {
        if (input.isEmpty()) return input
        var text = input
        if (options.stripHebrewNiqqud || options.useGereshGershayim) {
            text = HebrewNiqqudNormalizer.normalize(
                text = text,
                stripNiqqud = options.stripHebrewNiqqud,
                useGereshGershayim = options.useGereshGershayim,
            )
        }
        if (options.normalisePersianUrdu) {
            text = PersianUrduNormalizer.normalize(text)
        }
        if (options.shapeArabic) {
            text = ArabicShaper.shape(text)
        }
        text = applyNumeralConversion(text, options.numeralTarget)
        return text
    }

    private fun applyNumeralConversion(
        text: String,
        target: NumeralTarget,
    ): String = when (target) {
        NumeralTarget.LEAVE_UNCHANGED -> text
        NumeralTarget.WESTERN -> ArabicPersianNumeralConverter.normaliseToWestern(text)
        NumeralTarget.ARABIC_INDIC ->
            ArabicPersianNumeralConverter.westernToArabicIndic(text)
        NumeralTarget.EXTENDED_ARABIC_INDIC ->
            ArabicPersianNumeralConverter.westernToExtendedArabicIndic(text)
    }

    /**
     * Pipeline options. Defaults to "everything off" — caller must
     * opt-in to each transform so the pipeline never surprises text
     * surfaces.
     */
    data class Options(
        val stripHebrewNiqqud: Boolean = false,
        val useGereshGershayim: Boolean = false,
        val normalisePersianUrdu: Boolean = false,
        val shapeArabic: Boolean = false,
        val numeralTarget: NumeralTarget = NumeralTarget.LEAVE_UNCHANGED,
    ) {
        /** True when the pipeline would be a no-op. */
        val isNoOp: Boolean
            get() = !stripHebrewNiqqud &&
                !useGereshGershayim &&
                !normalisePersianUrdu &&
                !shapeArabic &&
                numeralTarget == NumeralTarget.LEAVE_UNCHANGED

        companion object {
            /** Profile: stock Arabic subtype on a Saudi locale. */
            val ARABIC_DEFAULT: Options = Options(
                shapeArabic = true,
                numeralTarget = NumeralTarget.ARABIC_INDIC,
            )

            /** Profile: Persian / Urdu subtype with Tatweel cleanup. */
            val PERSIAN_URDU_DEFAULT: Options = Options(
                normalisePersianUrdu = true,
                shapeArabic = true,
                numeralTarget = NumeralTarget.EXTENDED_ARABIC_INDIC,
            )

            /** Profile: Hebrew subtype with Niqqud stripping on. */
            val HEBREW_DEFAULT: Options = Options(
                stripHebrewNiqqud = true,
                useGereshGershayim = true,
                numeralTarget = NumeralTarget.LEAVE_UNCHANGED,
            )
        }
    }

    /** Numeral-conversion target for the pipeline's final stage. */
    enum class NumeralTarget {
        LEAVE_UNCHANGED,
        WESTERN,
        ARABIC_INDIC,
        EXTENDED_ARABIC_INDIC,
    }
}
