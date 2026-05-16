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

package dev.patrickgold.florisboard.ime.translate

import java.util.concurrent.atomic.AtomicReference

/**
 * ROADMAP §10.5 L2.1e — translation language-pack manager.
 *
 * The Bergamot translator addon ships per-pair model bundles
 * (e.g. `en-es-tiny`, `en-fr-base`). The IME's Settings → Translate
 * surface needs to show:
 *
 *  - Which pairs are currently **installed** (the addon registered
 *    them via `InlineTranslatorRegistry.setActive`).
 *  - Which pairs are **available for download** but not yet
 *    installed (advertised by the addon's manifest descriptor).
 *  - The user's **preferred default** target locale (the pair the
 *    Translate quick-action pre-fills when the user hits it).
 *
 * The manager is intentionally bookkeeping-only — it doesn't talk
 * to the addon, doesn't issue downloads. The addon-side download
 * pipeline calls `setInstalled` + `setAvailable` to keep this
 * snapshot fresh; the IME's translation surface observes the
 * snapshot to render the language list.
 *
 * Atomic snapshots — concurrent reads from the IME thread never
 * see a half-replaced state.
 */
object TranslationLanguagePackManager {

    private val installedRef = AtomicReference<List<LanguagePairDescriptor>>(emptyList())
    private val availableRef = AtomicReference<List<LanguagePairDescriptor>>(emptyList())
    private val preferredTargetRef = AtomicReference<String?>(null)

    fun installedPairs(): List<LanguagePairDescriptor> = installedRef.get()

    fun availablePairs(): List<LanguagePairDescriptor> = availableRef.get()

    /** Pairs that exist in [availablePairs] but not in [installedPairs]. */
    fun downloadablePairs(): List<LanguagePairDescriptor> {
        val installed = installedRef.get().mapTo(HashSet()) { it.pairKey }
        return availableRef.get().filterNot { it.pairKey in installed }
    }

    fun setInstalled(pairs: List<LanguagePairDescriptor>) {
        installedRef.set(pairs.distinctBy { it.pairKey })
    }

    fun setAvailable(pairs: List<LanguagePairDescriptor>) {
        availableRef.set(pairs.distinctBy { it.pairKey })
    }

    fun preferredTargetLocale(): String? = preferredTargetRef.get()

    fun setPreferredTargetLocale(locale: String?) {
        require(locale == null || (locale.isNotBlank() && locale == locale.lowercase())) {
            "locale must be null or non-blank lowercase ISO 639-1"
        }
        preferredTargetRef.set(locale)
    }

    /**
     * Choose a default `LanguagePairDescriptor` for a given source
     * locale. Picks the installed pair whose target matches the
     * user's [preferredTargetLocale] when available, falls back to
     * the first installed pair with that source, otherwise null.
     */
    fun defaultPairFor(sourceLocale: String): LanguagePairDescriptor? {
        val installed = installedRef.get()
        val preferred = preferredTargetRef.get()
        if (preferred != null) {
            installed.firstOrNull {
                it.sourceLocale == sourceLocale && it.targetLocale == preferred
            }?.let { return it }
        }
        return installed.firstOrNull { it.sourceLocale == sourceLocale }
    }

    /** Test-only reset. */
    internal fun resetForTest() {
        installedRef.set(emptyList())
        availableRef.set(emptyList())
        preferredTargetRef.set(null)
    }
}
