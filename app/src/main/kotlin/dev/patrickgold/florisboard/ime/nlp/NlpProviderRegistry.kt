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

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.nlp.advanced.AdvancedPredictionProvider
import dev.patrickgold.florisboard.ime.nlp.advanced.AdvancedSpellingProvider
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.kotlin.guardedByLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class NlpProviderRegistry private constructor(
    createProviders: () -> List<NlpProvider>,
) {
    constructor(context: Context) : this(createProviders = { NlpProviderFactory(context).createProviders() })

    internal constructor(testProviders: List<NlpProvider>) : this(createProviders = { testProviders })

    private val providers = guardedByLock {
        createProviders().associateBy { it.providerId }.mapValues { (_, provider) ->
            ProviderInstanceWrapper(provider)
        }
    }
    private val providersForceSuggestionOn = ConcurrentHashMap<String, Boolean>()

    suspend fun spellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    suspend fun suggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    suspend fun preload(subtype: Subtype) {
        val providersToPreload = providers.withLock { providers ->
            buildList {
                subtype.nlpProviders.forEach { _, providerId ->
                    providers[providerId]?.let(::add)
                }
            }
        }.distinct()
        for (provider in providersToPreload) {
            provider.preload(subtype)
        }
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        return providersForceSuggestionOn.getOrPut(subtype.nlpProviders.suggestion) {
            runBlocking {
                suggestionProvider(subtype).forcesSuggestionOn
            }
        }
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private val lifecycleLock = Mutex(locked = false)
        private var isInstanceAlive = AtomicBoolean(false)

        private suspend fun createIfNecessary() {
            if (isInstanceAlive.compareAndSet(false, true)) {
                try {
                    provider.create()
                } catch (error: Throwable) {
                    isInstanceAlive.set(false)
                    throw error
                }
            }
        }

        suspend fun preload(subtype: Subtype) {
            lifecycleLock.withLock {
                createIfNecessary()
                provider.preload(subtype)
            }
        }

        suspend fun destroyIfNecessary() {
            lifecycleLock.withLock {
                if (isInstanceAlive.getAndSet(false)) provider.destroy()
            }
        }
    }
}

internal class NlpProviderFactory(private val context: Context) {
    fun createProviders(): List<NlpProvider> {
        return listOf(
            LatinLanguageProvider(context),
            HanShapeBasedLanguageProvider(context),
            AdvancedSpellingProvider(context),
            AdvancedPredictionProvider(context),
        )
    }
}
