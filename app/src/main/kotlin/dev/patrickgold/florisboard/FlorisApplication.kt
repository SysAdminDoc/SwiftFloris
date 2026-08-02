/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.core.os.UserManagerCompat
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.clipboard.ClipboardManager
import dev.patrickgold.florisboard.ime.calendar.CalendarQuickInsertManager
import dev.patrickgold.florisboard.ime.core.SubtypeManager
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import dev.patrickgold.florisboard.ime.media.emoji.FlorisEmojiCompat
import dev.patrickgold.florisboard.ime.nlp.NlpManager
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingManager
import dev.patrickgold.florisboard.ime.text.keyboard.AdaptiveTouchModel
import dev.patrickgold.florisboard.ime.theme.PerAppAccentController
import dev.patrickgold.florisboard.ime.wordstyles.WordStylesCanvasRenderer
import dev.patrickgold.florisboard.ime.wordstyles.WordStylesRendererRegistry
import dev.patrickgold.florisboard.ime.theme.ThemeManager
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.crashutility.CrashUtility
import dev.patrickgold.florisboard.lib.devtools.Flog
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.tryOrNull
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [FlorisApplication] class. This is needed as in certain scenarios an application
 * reference is needed, but the Android framework hasn't finished setting up
 */
private var FlorisApplicationReference = WeakReference<FlorisApplication?>(null)

@Suppress("unused")
class FlorisApplication : Application() {
    private val mainHandler by lazy { Handler(mainLooper) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    val cacheManager = lazy { CacheManager(this) }
    val calendarQuickInsertManager = lazy { CalendarQuickInsertManager(this) }
    val clipboardManager = lazy { ClipboardManager(this) }
    val editorInstance = lazy { EditorInstance(this) }
    val extensionManager = lazy { ExtensionManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val keyboardManager = lazy { KeyboardManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val snippetManager = lazy { dev.patrickgold.florisboard.ime.snippet.SnippetManager(this) }
    val subtypeManager = lazy { SubtypeManager(this) }
    val themeManager = lazy { ThemeManager(this) }
    val perAppAccentController = lazy { PerAppAccentController(this) }

    override fun onCreate() {
        super.onCreate()
        FlorisApplicationReference = WeakReference(this)
        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                flogLevels = Flog.LEVEL_ALL,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)
            FlorisEmojiCompat.init(this)
            // ROADMAP §7 L12.1 — register the Android Canvas WordStyles
            // renderer at app boot so the smartbar quick-action sees a
            // working renderer without needing the L12 addon to be
            // installed. Out-of-tree addon variants can still override
            // via WordStylesRendererRegistry.setActive(...).
            WordStylesRendererRegistry.setActive(WordStylesCanvasRenderer(this))
            // SmartCompose feature contract F18 — bind the on-device n-gram
            // heuristic SmartComposeProvider as the baseline so inline
            // ghost-text works without any LLM addon. Gated at call time by
            // prefs.correction.heuristicSmartCompose (default off). The debug
            // provider (below) overrides this on debug builds. The optional
            // model-backed provider remains a contract only until a runtime is
            // implemented and wired.
            dev.patrickgold.florisboard.ime.smartcompose.SmartComposeProviderRegistry
                .setActive(
                    dev.patrickgold.florisboard.ime.smartcompose
                        .HeuristicSmartComposeProvider(this),
                )
            // ROADMAP §0 P1 (debug-only) — wire the debug
            // SmartComposeProvider via reflection so release builds
            // never reference it. The class only exists in
            // app/src/debug/kotlin/ so Class.forName throws on release; we
            // swallow that and stay on the heuristic provider registered
            // above.
            if (BuildConfig.DEBUG) {
                try {
                    val klass = Class.forName(
                        "dev.patrickgold.florisboard.debug.DebugSmartComposeProvider",
                    )
                    val instance = klass.getField("INSTANCE").get(null)
                        as dev.patrickgold.florisboard.ime.smartcompose.SmartComposeProvider
                    dev.patrickgold.florisboard.ime.smartcompose
                        .SmartComposeProviderRegistry.setActive(instance)
                } catch (_: Throwable) {
                    // Release / non-debug — leave the default no-op
                    // provider in place.
                }
            }
            if (!UserManagerCompat.isUserUnlocked(this)) {
                cacheDir?.deleteContentsRecursively()
                extensionManager.value.init()
                registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                return
            }

            init()
        } catch (e: Exception) {
            CrashUtility.stageException(e)
            return
        }
    }

    fun init() {
        cacheDir?.deleteContentsRecursively()
        scope.launch {
            initializePreferenceStoreForStartup(
                context = this@FlorisApplication,
                preferenceStoreLoaded = preferenceStoreLoaded,
            )
        }
        extensionManager.value.init()
        clipboardManager.value.initializeForContext(this)
        snippetManager.value.initialize()
        DictionaryManager.init(this)
        AdaptiveTouchModel.initialize(this)
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                mainHandler.post { init() }
            }
        }
    }
}

internal suspend fun initializePreferenceStoreForStartup(
    context: Context,
    preferenceStoreLoaded: MutableStateFlow<Boolean>,
    datastoreName: String = FlorisPreferenceModel.NAME,
    initializer: suspend (Context, String) -> Any? = { initContext, initDatastoreName ->
        FlorisPreferenceStore.initAndroid(
            context = initContext,
            datastoreName = initDatastoreName,
        )
    },
    logResult: (Any?) -> Unit = { result -> Log.i("PREFS", result.toString()) },
) {
    var markLoaded = true
    try {
        val result = initializer(context, datastoreName)
        logResult(result)
    } catch (e: CancellationException) {
        markLoaded = false
        throw e
    } catch (e: Exception) {
        CrashUtility.stageException(e)
        flogError(LogTopic.CRASH_UTILITY) {
            "Preference store initialization failed before Settings could render: $e"
        }
    } finally {
        if (markLoaded) {
            preferenceStoreLoaded.value = true
        }
    }
}

private tailrec fun Context.florisApplication(): FlorisApplication {
    return when (this) {
        is FlorisApplication -> this
        is ContextWrapper -> when {
            this.baseContext != null -> this.baseContext.florisApplication()
            else -> FlorisApplicationReference.get()
                ?: error("FlorisApplication has not been initialized or was garbage collected")
        }
        else -> tryOrNull { this.applicationContext as FlorisApplication }
            ?: FlorisApplicationReference.get()
            ?: error("FlorisApplication has not been initialized or was garbage collected")
    }
}

fun Context.appContext() = lazyOf(this.florisApplication())

fun Context.cacheManager() = this.florisApplication().cacheManager

fun Context.calendarQuickInsertManager() = this.florisApplication().calendarQuickInsertManager

fun Context.clipboardManager() = this.florisApplication().clipboardManager

fun Context.editorInstance() = this.florisApplication().editorInstance

fun Context.extensionManager() = this.florisApplication().extensionManager

fun Context.glideTypingManager() = this.florisApplication().glideTypingManager

fun Context.keyboardManager() = this.florisApplication().keyboardManager

fun Context.nlpManager() = this.florisApplication().nlpManager

fun Context.perAppAccentController() = this.florisApplication().perAppAccentController

fun Context.snippetManager() = this.florisApplication().snippetManager

fun Context.subtypeManager() = this.florisApplication().subtypeManager

fun Context.themeManager() = this.florisApplication().themeManager
