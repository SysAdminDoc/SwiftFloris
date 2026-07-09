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

package dev.patrickgold.florisboard.app.settings.localization

import dev.patrickgold.florisboard.ime.nlp.LanguagePackComponent
import dev.patrickgold.florisboard.ime.nlp.LanguagePackExtension
import dev.patrickgold.florisboard.ime.nlp.LanguagePackKind

internal enum class LanguagePackManagerNotice {
    None,
    DeleteInProgress,
    DeleteSuccess,
    DeleteFailure,
}

internal enum class LanguagePackRuntimeState {
    ActiveForSubtype,
    InstalledStandby,
    MetadataOnly,
    DataUnavailable,
}

internal data class LanguagePackCatalogComponent(
    val id: String,
    val label: String,
    val localeTag: String,
    val isActive: Boolean,
)

internal data class LanguagePackCatalogEntry(
    val extensionId: String,
    val title: String,
    val kind: LanguagePackKind,
    val state: LanguagePackRuntimeState,
    val components: List<LanguagePackCatalogComponent>,
) {
    val componentCount: Int = components.size
    val activeComponentCount: Int = components.count { it.isActive }
}

internal object LanguagePackManagerPolicy {
    fun canTriggerImport(isDeleteInProgress: Boolean): Boolean {
        return !isDeleteInProgress
    }

    fun canDelete(
        extensionCanBeDeleted: Boolean,
        isDeleteInProgress: Boolean,
    ): Boolean {
        return extensionCanBeDeleted && !isDeleteInProgress
    }

    fun resolveNotice(
        isDeleteInProgress: Boolean,
        lastTerminalNotice: LanguagePackManagerNotice?,
    ): LanguagePackManagerNotice {
        return when {
            isDeleteInProgress -> LanguagePackManagerNotice.DeleteInProgress
            lastTerminalNotice != null -> lastTerminalNotice
            else -> LanguagePackManagerNotice.None
        }
    }

    fun catalogEntries(
        extensions: List<LanguagePackExtension>,
        activeLocaleTags: Set<String>,
        hasUsableHanRuntime: (LanguagePackExtension) -> Boolean = { extension ->
            !extension.isLoaded() || extension.hasOpenHanShapeBasedSQLiteDatabase()
        },
    ): List<LanguagePackCatalogEntry> {
        return extensions.map { extension ->
            val components = extension.items
                .sortedWith(compareBy<LanguagePackComponent> { it.label }.thenBy { it.id })
                .map { component ->
                    val localeTag = component.locale.localeTag()
                    LanguagePackCatalogComponent(
                        id = component.id,
                        label = component.label,
                        localeTag = localeTag,
                        isActive = localeTag in activeLocaleTags,
                    )
                }
            val state = when {
                !extension.supportsHanShapeBased() -> LanguagePackRuntimeState.MetadataOnly
                components.any { it.isActive } && !hasUsableHanRuntime(extension) ->
                    LanguagePackRuntimeState.DataUnavailable
                components.any { it.isActive } -> LanguagePackRuntimeState.ActiveForSubtype
                else -> LanguagePackRuntimeState.InstalledStandby
            }
            LanguagePackCatalogEntry(
                extensionId = extension.meta.id,
                title = extension.meta.title,
                kind = extension.kind,
                state = state,
                components = components,
            )
        }.sortedWith(compareBy<LanguagePackCatalogEntry> { it.title }.thenBy { it.extensionId })
    }
}
