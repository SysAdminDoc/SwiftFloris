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

package dev.patrickgold.florisboard.ime.cjk

import kotlinx.serialization.Serializable

/**
 * ROADMAP §7 L3 — Chinese / Japanese / Korean input-method facade.
 *
 * CJK input methods are characterised by **conversion** rather than
 * direct character entry: the user types romanised input (Pinyin /
 * Jyutping / Zhuyin / Romaji) or shape codes (Cangjie / Wubi), and a
 * candidate engine offers ranked CJK character / word completions
 * that the user picks via candidate-row tap.
 *
 * The de-facto open implementation is **librime** (BSD-3) — the
 * conversion engine behind fcitx5 on Linux and the iOS App Store's
 * Squirrel-derived inputs. Wiring librime into Android needs JNI to
 * its C++ runtime; that work lives in an **out-of-tree signed addon
 * APK** (slated identifier `cjk-librime`, distributed via GitHub
 * Releases / Obtainium / F-Droid alongside SwiftFloris, never bundled
 * into `:app`) that registers a concrete [CjkInputProvider] with
 * [CjkInputProviderRegistry] through the `AddonContract.Action.REGISTER_*`
 * enrolment path. The IME-side surface stays this facade so the
 * candidate-row + per-keystroke dispatch can ship and be tested
 * independently from the JNI bring-up.
 *
 * Three concrete provider classes are expected (L3.1–L3.3 in ROADMAP):
 *  - L3.1 Mandarin Pinyin / Cantonese Jyutping / Mandarin Zhuyin via
 *    librime.
 *  - L3.2 Compose candidate-row UI consuming the [CjkCandidate] stream
 *    this facade exposes.
 *  - L3.3 Japanese via mozc + Korean via Jamo IME shims (each with its
 *    own concrete provider).
 *
 * The facade is per-keystroke incremental: every keystroke pushes the
 * current input buffer through [convert], which returns ranked
 * candidates the candidate row renders. Commits travel through
 * [commit] so the engine can update its user-frequency table.
 */
interface CjkInputProvider {
    fun convert(input: String, schema: CjkSchema, maxCandidates: Int = 32): List<CjkCandidate>
    fun commit(candidate: CjkCandidate, schema: CjkSchema)
    val supportedSchemas: Set<CjkSchema>

    object Default : CjkInputProvider {
        override fun convert(input: String, schema: CjkSchema, maxCandidates: Int): List<CjkCandidate> =
            emptyList()
        override fun commit(candidate: CjkCandidate, schema: CjkSchema) = Unit
        override val supportedSchemas: Set<CjkSchema> = emptySet()
    }
}

/**
 * One CJK input scheme. Maps onto librime's `*.schema.yaml` files; the
 * enum value's [schemaId] doubles as the librime schema identifier so
 * the JNI wrapper can route directly.
 */
@Serializable
enum class CjkSchema(
    val schemaId: String,
    val displayName: String,
    val targetLocale: String,
) {
    PINYIN_SIMPLIFIED("luna_pinyin", "Pinyin (Simplified)", "zh-CN"),
    PINYIN_TRADITIONAL("luna_pinyin_tw", "Pinyin (Traditional)", "zh-TW"),
    JYUTPING("jyutping", "Jyutping (Cantonese)", "zh-HK"),
    ZHUYIN("bopomofo", "Zhuyin (Bopomofo)", "zh-TW"),
    CANGJIE_5("cangjie5", "Cangjie 5", "zh-HK"),
    WUBI_86("wubi86", "Wubi 86", "zh-CN"),
    QUICK_3("double_pinyin_xiaohe", "Quick (Double Pinyin Xiaohe)", "zh-CN"),
    JAPANESE_MOZC("mozc_ja", "Japanese (Mozc)", "ja-JP"),
    KOREAN_JAMO("hangul_2bul", "Korean (Jamo 2-bul)", "ko-KR"),
}

/**
 * Ranked CJK conversion candidate. [text] is the CJK form to commit
 * to the editor; [annotation] is a romanised-form hint shown under
 * the candidate (e.g. `text="你"`, `annotation="nǐ"`).
 */
data class CjkCandidate(
    val text: String,
    val annotation: String? = null,
    val confidence: Float,
    /** True when this candidate is the engine's preferred default for
     *  the current input buffer (rendered with a highlight ring). */
    val isPreferred: Boolean = false,
) {
    init {
        require(text.isNotEmpty()) { "CJK candidate text must not be empty" }
        // Explicitly reject NaN: `NaN in 0f..1f` is false, so this also rejects it,
        // but the message would be misleading. A 3rd-party (out-of-tree) provider
        // emitting NaN for an unranked candidate gets a clear error rather than a
        // confusing one.
        require(!confidence.isNaN() && confidence in 0f..1f) { "confidence must be a number in [0, 1]; was $confidence" }
    }
}

object CjkInputProviderRegistry {
    @Volatile
    private var current: CjkInputProvider = CjkInputProvider.Default
    val active: CjkInputProvider get() = current
    fun setActive(provider: CjkInputProvider) { current = provider }
    fun reset() { current = CjkInputProvider.Default }
}
