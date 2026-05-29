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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Helper object which manages EmojiCompat instances for the two replace modes. The default no-replace instance is
 * loaded during application startup, while the replace-all instance is created and loaded only when an editor requests
 * it. This keeps the common path to one EmojiCompat metadata graph while preserving compatibility for editors that
 * explicitly ask for replace-all behavior.
 *
 * ## Why we defer singleton install until load completes (issue #1)
 *
 * `EmojiCompat.reset(Config)` constructs the instance AND assigns it to the process-wide `sInstance`. From that moment
 * `EmojiCompat.isConfigured()` returns `true`, but `EmojiCompat.isInitialized()` stays `false` until the metadata load
 * succeeds. Compose's `AndroidParagraphHelper.createCharSequence` gates on `isConfigured()` only and then calls
 * `EmojiCompat.get().process(...)`, which throws `IllegalStateException("Not initialized yet")` during that window.
 * The crash was reproducible by opening the emoji picker before metadata loaded.
 *
 * Fix: construct the EmojiCompat instance via the package-private constructor (reflection), so the singleton stays
 * `null`. Compose then sees `isConfigured() == false` and uses the raw text path — no race, no crash. Once the metadata
 * load completes (InitCallback#onInitialized), we publish the loaded instance via the standard `EmojiCompat.reset(EmojiCompat)`
 * overload, which only assigns `sInstance` and never reinitialises state. From that point Compose sees both
 * `isConfigured()` and `isInitialized()` as `true`.
 *
 * ## Fallback contract on GMS-less devices
 *
 * `DefaultEmojiCompatConfig.create(context)` returns `null` whenever the device has no emoji-font provider installed.
 * That covers:
 *  - AOSP-derived ROMs without Google Play Services and without microG (LineageOS without microG, /e/OS without GMS,
 *    GrapheneOS without sandboxed Play Services).
 *  - Huawei devices on HMS-only firmwares (no GMS).
 *  - Stripped firmwares that have intentionally removed Play Services and any AOSP downloadable-fonts provider.
 *
 * In that case the per-instance load is skipped and the [InstanceHandler.loadState] transitions directly to
 * [EmojiCompatLoadState.Unavailable]. The [InstanceHandler.publishedInstanceFlow] stays at `null` and consumers must
 * treat that as "fall back to the system font painter for glyph availability checks". `EmojiPaletteView` already does
 * exactly that via `Paint.hasGlyph(...)`, so the no-GMS path is non-fatal by construction.
 *
 * ## Why two EmojiCompat instances
 *
 * Some editors set `EditorInfo.extras.EDITOR_INFO_REPLACE_ALL_KEY = true`, which asks the IME to actively replace
 * already-rendered system emoji glyphs with the EmojiCompat-managed ones. That mode requires its own EmojiCompat
 * instance because `setReplaceAll(...)` is a per-Config flag baked into the metadata graph at load time. Carrying both
 * a `replaceAll=false` (the default) and a `replaceAll=true` instance is the smallest correct implementation; the
 * replace-all instance is created lazily so apps that never ask for it do not pay the load cost.
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
            instanceNoReplace = InstanceHandler(replaceAll = false) {
                defaultConfigFor(appContext, replaceAll = false)
            }
            instanceReplaceAll = InstanceHandler(replaceAll = true) {
                defaultConfigFor(appContext, replaceAll = true)
            }
            initialized = true
        }
        instanceNoReplace.ensureLoad(scope)
    }

    /**
     * Gets the current EmojiCompat instance based on [replaceAll] and sets it as the default instance if
     * [setAsDefaultInstance] is true. Calling this method before [init] will cause an exception to be thrown.
     *
     * @return A state flow providing the latest EmojiCompat instance for given args. The flow may provide null if
     *  EmojiCompat is still loading, has failed, or is unavailable on this device (no GMS / no font provider). Callers
     *  must treat `null` as a fall-back-to-system-painter signal.
     */
    @SuppressLint("RestrictedApi", "VisibleForTests")
    fun getAsFlow(replaceAll: Boolean, setAsDefaultInstance: Boolean = true): StateFlow<EmojiCompat?> {
        val handler = handlerFor(replaceAll)
        if (setAsDefaultInstance) {
            handler.setAsDefaultWhenAvailable()
        }
        handler.ensureLoad(scope)
        return handler.publishedInstanceFlow
    }

    /**
     * Exposes the load state for the requested instance. Consumers can observe transitions between
     * [EmojiCompatLoadState.Loading], [EmojiCompatLoadState.Loaded], [EmojiCompatLoadState.Failed], and
     * [EmojiCompatLoadState.Unavailable]. Calling this method before [init] will cause an exception to be thrown.
     */
    fun loadStateFlow(replaceAll: Boolean): StateFlow<EmojiCompatLoadState> {
        return handlerFor(replaceAll).loadStateFlow
    }

    private fun handlerFor(replaceAll: Boolean): InstanceHandler {
        check(initialized && ::instanceNoReplace.isInitialized && ::instanceReplaceAll.isInitialized) {
            "${FlorisEmojiCompat::class.simpleName} has not been initialized. Call init(context) before using it."
        }
        return if (replaceAll) instanceReplaceAll else instanceNoReplace
    }

    @SuppressLint("RestrictedApi")
    private fun defaultConfigFor(appContext: Context, replaceAll: Boolean): EmojiCompat.Config? {
        return DefaultEmojiCompatConfig.create(appContext)?.apply {
            setReplaceAll(replaceAll)
            setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL)
        }
    }

    internal class InstanceHandler(
        private val replaceAll: Boolean,
        private val configProvider: () -> EmojiCompat.Config?,
    ) {
        val publishedInstanceFlow = MutableStateFlow<EmojiCompat?>(null)

        private val mutableLoadState = MutableStateFlow<EmojiCompatLoadState>(EmojiCompatLoadState.Loading)
        val loadStateFlow: StateFlow<EmojiCompatLoadState> = mutableLoadState.asStateFlow()

        @Volatile private var loadStarted = false
        @Volatile private var setAsDefaultWhenLoaded = false

        private val initCallback: EmojiCompat.InitCallback = object : EmojiCompat.InitCallback() {
            override fun onInitialized() {
                super.onInitialized()
                flogInfo { "EmojiCompat(replaceAll=$replaceAll) successfully loaded!" }
                val loadedInstance = instance ?: return
                publishedInstanceFlow.value = loadedInstance
                mutableLoadState.value = EmojiCompatLoadState.Loaded
                if (setAsDefaultWhenLoaded) {
                    setAsDefault(loadedInstance)
                }
            }

            override fun onFailed(throwable: Throwable?) {
                super.onFailed(throwable)
                flogError { "EmojiCompat(replaceAll=$replaceAll) failed to load: $throwable" }
                mutableLoadState.value = EmojiCompatLoadState.Failed
            }
        }

        private val config: EmojiCompat.Config? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            configProvider()?.apply {
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
                mutableLoadState.value = EmojiCompatLoadState.Unavailable
                return
            }
            emojiCompat.load()
        }

        @SuppressLint("RestrictedApi")
        private fun createInstance(config: EmojiCompat.Config): EmojiCompat {
            // Construct WITHOUT installing as singleton (see class kdoc "Why we defer singleton install").
            // Falls back to EmojiCompat.reset(config) if reflection fails — that path still has the race window
            // but is preferable to crashing during creation.
            return try {
                val ctor = EmojiCompat::class.java.getDeclaredConstructor(EmojiCompat.Config::class.java)
                // Loud guard: an androidx-emoji2 bump that changes the package-private
                // EmojiCompat(Config) constructor shape silently sends us down the
                // reset(config) fallback, which reintroduces the bind-before-init race
                // (see kdoc "Why we defer singleton install"). Fail the construction so
                // the error is logged with an actionable message rather than masked, and
                // FlorisEmojiCompatReflectionGuardTest catches the same drift in CI.
                check(isExpectedEmojiCompatConstructor(ctor)) {
                    "EmojiCompat(Config) constructor shape changed unexpectedly"
                }
                ctor.isAccessible = true
                ctor.newInstance(config)
            } catch (t: Throwable) {
                flogError {
                    "Reflective EmojiCompat construction failed or its (Config) constructor shape changed " +
                        "(androidx-emoji2 bump?): $t. Falling back to reset(config), which reintroduces the " +
                        "bind-before-init race window — update FlorisEmojiCompat.createInstance for the new shape."
                }
                EmojiCompat.reset(config)
            }
        }

        @SuppressLint("RestrictedApi")
        private fun setAsDefault(instance: EmojiCompat) {
            flogInfo { "Set default EmojiCompat instance to $instance(replaceAll=$replaceAll)" }
            // Install the loaded instance as the process-wide singleton. Only called after the InitCallback fires
            // onInitialized, so isInitialized() == true the moment Compose can observe isConfigured() == true.
            EmojiCompat.reset(instance)
        }
    }
}

/**
 * Validates that [ctor] is the package-private `EmojiCompat(EmojiCompat.Config)`
 * constructor the deferred-singleton reflection path in
 * [FlorisEmojiCompat.InstanceHandler] depends on: exactly one parameter, of type
 * [EmojiCompat.Config]. Extracted so the shape is asserted both at runtime
 * (loudly logged on mismatch) and in CI (`FlorisEmojiCompatReflectionGuardTest`),
 * catching an androidx-emoji2 bump before it silently reintroduces the
 * bind-before-init race.
 */
internal fun isExpectedEmojiCompatConstructor(ctor: java.lang.reflect.Constructor<*>?): Boolean {
    return ctor != null &&
        ctor.parameterTypes.size == 1 &&
        ctor.parameterTypes[0] == EmojiCompat.Config::class.java
}

/**
 * Public load state for an [androidx.emoji2.text.EmojiCompat] instance managed by [FlorisEmojiCompat].
 *
 * - [Loading]: the load has been started but neither succeeded nor failed yet.
 * - [Loaded]: the instance is available via [FlorisEmojiCompat.getAsFlow].
 * - [Failed]: the load was attempted but the EmojiCompat runtime reported a failure callback. The fallback contract is
 *   the same as [Unavailable] — consumers should treat the published instance as `null` and use a system-painter glyph
 *   check.
 * - [Unavailable]: no EmojiCompat config could be built. This is the GMS-less / no-font-provider device path.
 *
 * @see FlorisEmojiCompat.loadStateFlow
 */
sealed class EmojiCompatLoadState {
    object Loading : EmojiCompatLoadState()
    object Loaded : EmojiCompatLoadState()
    object Failed : EmojiCompatLoadState()
    object Unavailable : EmojiCompatLoadState()
}
