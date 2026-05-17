/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.settings.about

import dev.patrickgold.florisboard.R

enum class AiFeatureSurface {
    NEXT_WORD,
    GLIDE_TYPING,
    VOICE_INPUT,
    TRANSLATION,
    SMART_COMPOSE,
}

data class AiFeatureDisclosure(
    val surface: AiFeatureSurface,
    val titleRes: Int,
    val summaryRes: Int,
)

object AiFeatureDisclosureCatalog {
    val requiredFirstRunSurfaces = setOf(
        AiFeatureSurface.NEXT_WORD,
        AiFeatureSurface.GLIDE_TYPING,
        AiFeatureSurface.VOICE_INPUT,
        AiFeatureSurface.TRANSLATION,
        AiFeatureSurface.SMART_COMPOSE,
    )

    val rows = listOf(
        AiFeatureDisclosure(
            surface = AiFeatureSurface.NEXT_WORD,
            titleRes = R.string.about__ai_features__next_word_title,
            summaryRes = R.string.about__ai_features__next_word_summary,
        ),
        AiFeatureDisclosure(
            surface = AiFeatureSurface.GLIDE_TYPING,
            titleRes = R.string.about__ai_features__glide_title,
            summaryRes = R.string.about__ai_features__glide_summary,
        ),
        AiFeatureDisclosure(
            surface = AiFeatureSurface.VOICE_INPUT,
            titleRes = R.string.about__ai_features__voice_title,
            summaryRes = R.string.about__ai_features__voice_summary,
        ),
        AiFeatureDisclosure(
            surface = AiFeatureSurface.TRANSLATION,
            titleRes = R.string.about__ai_features__translation_title,
            summaryRes = R.string.about__ai_features__translation_summary,
        ),
        AiFeatureDisclosure(
            surface = AiFeatureSurface.SMART_COMPOSE,
            titleRes = R.string.about__ai_features__smart_compose_title,
            summaryRes = R.string.about__ai_features__smart_compose_summary,
        ),
    )

    fun coversFirstRunSurfaces(): Boolean {
        return rows.mapTo(mutableSetOf()) { it.surface }.containsAll(requiredFirstRunSurfaces)
    }
}
