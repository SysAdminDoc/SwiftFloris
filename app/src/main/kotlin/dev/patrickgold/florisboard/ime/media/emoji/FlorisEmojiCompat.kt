/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.media.emoji

import android.annotation.SuppressLint
import android.content.Context
import androidx.emoji2.text.DefaultEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Helper object which manages EmojiCompat instances for the two replace modes. The default no-replace instance is
 * loaded during application startup, while the replace-all instance is created and loaded only when an editor requests
 * it. This keeps the common path to one EmojiCompat metadata graph while preserving compatibility for editors that
 * explicitly ask for replace-all behavior.
 *
 * TODO: investigate how AOSP-like ROMs without any GMS services installed handle backwards emoji compatibility. Same
 *  goes for newer Huawei devices, which are subjected to no Google services. (Probably these devices rely on the good
 *  old method of just querying the system painter, which we already use as a fallback in the palette logic).
 *
 * TODO: investigate if having two instances of EmojiCompat causes other logic issues or if there's a better way of
 *  achieving the same result than the current implementation does.
 */
object FlorisEmojiCompat {
    private lateinit var instanceNoReplace: InstanceHandler
    private lateinit var instanceReplaceAll: InstanceHandler
    @Volatile private var initialized = false

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Initialize this helper and its EmojiCompat instances with given [context]. Immediately begins loading the emoji
     * metadata in a background thread. After this method has been called, it is safe to call [getAsFlow].
     */
    fun init(context: Context) {
        synchronized(this) {
            if (initialized) {
                return
            }
            val appContext = context.applicationContext
            instanceNoReplace = InstanceHandler(appContext, replaceAll = false)
            instanceReplaceAll = InstanceHandler(appContext, replaceAll = true)
            initialized = true
        }
        instanceNoReplace.ensureLoad(scope)
    }

    /**
     * Gets the current EmojiCompat instance based on [replaceAll] and sets it as the default instance if
     * [setAsDefaultInstance] is true. Calling this method before [init] will cause an exception to be thrown.
     *
     * @return A state flow providing the latest EmojiCompat instance for given args. The flow may provide null if
     *  EmojiCompat is still loading or if it has failed.
     */
    @SuppressLint("RestrictedApi", "VisibleForTests")
    fun getAsFlow(replaceAll: Boolean, setAsDefaultInstance: Boolean = true): StateFlow<EmojiCompat?> {
        check(initialized && ::instanceNoReplace.isInitialized && ::instanceReplaceAll.isInitialized) {
            "${FlorisEmojiCompat::class.simpleName} has not been initialized. Call init(context) before getAsFlow()."
        }
        val handler = if (replaceAll) {
            instanceReplaceAll
        } else {
            instanceNoReplace
        }
        if (setAsDefaultInstance) {
            handler.setAsDefaultWhenAvailable()
        }
        handler.ensureLoad(scope)
        return handler.publishedInstanceFlow
    }

    private class InstanceHandler(
        private val context: Context,
        private val replaceAll: Boolean = false,
    ) {
        val publishedInstanceFlow = MutableStateFlow<EmojiCompat?>(null)

        @Volatile private var loadStarted = false
        @Volatile private var setAsDefaultWhenLoaded = false

        private val initCallback: EmojiCompat.InitCallback = object : EmojiCompat.InitCallback() {
            override fun onInitialized() {
                super.onInitialized()
                flogInfo { "EmojiCompat(replaceAll=$replaceAll) successfully loaded!" }
                val loadedInstance = instance ?: return
                publishedInstanceFlow.value = loadedInstance
                if (setAsDefaultWhenLoaded) {
                    setAsDefault(loadedInstance)
                }
            }

            override fun onFailed(throwable: Throwable?) {
                super.onFailed(throwable)
                flogError { "EmojiCompat(replaceAll=$replaceAll) failed to load: $throwable" }
            }
        }

        private val config: EmojiCompat.Config? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            DefaultEmojiCompatConfig.create(context)?.apply {
                setReplaceAll(replaceAll)
                setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL)
                registerInitCallback(initCallback)
            }
        }

        // Despite its name, `EmojiCompat.reset()` actually creates a new instance, exactly what we need
        private val instance: EmojiCompat? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            config?.let { createInstance(it) }
        }

        fun setAsDefaultWhenAvailable() {
            setAsDefaultWhenLoaded = true
            publishedInstanceFlow.value?.let { setAsDefault(it) }
        }

        fun ensureLoad(scope: CoroutineScope) {
            var shouldStart = false
            synchronized(this) {
                if (!loadStarted) {
                    loadStarted = true
                    shouldStart = true
                }
            }
            if (shouldStart) {
                scope.launch {
                    load()
                }
            }
        }

        /**
         * Manually loads the EmojiCompat instance. Call this method on a background thread to avoid blocking main.
         *
         * @see EmojiCompat.load
         */
        private fun load() {
            val emojiCompat = instance
            if (emojiCompat == null) {
                flogError { "EmojiCompat(replaceAll=$replaceAll) default config is unavailable" }
                return
            }
            emojiCompat.load()
        }

        @SuppressLint("RestrictedApi")
        private fun createInstance(config: EmojiCompat.Config): EmojiCompat {
            return EmojiCompat.reset(config)
        }

        @SuppressLint("RestrictedApi")
        private fun setAsDefault(instance: EmojiCompat) {
            flogInfo { "Set default EmojiCompat instance to $instance(replaceAll=$replaceAll)" }
            // This API is not really supposed to be used by third-party apps, but it is really handy and does
            // exactly what we need, so we suppress the restriction here.
            EmojiCompat.reset(instance)
        }
    }
}
