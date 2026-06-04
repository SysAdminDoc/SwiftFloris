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

package dev.patrickgold.florisboard.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.app.ext.ExtensionImportScreenType
import dev.patrickgold.florisboard.app.setup.NotificationPermissionState
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.LocalPreviewFieldController
import dev.patrickgold.florisboard.lib.compose.PreviewKeyboardField
import dev.patrickgold.florisboard.lib.compose.rememberPreviewFieldController
import dev.patrickgold.florisboard.lib.crashutility.CrashDialogActivity
import dev.patrickgold.florisboard.lib.crashutility.CrashUtility
import dev.patrickgold.florisboard.lib.util.AppVersionUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.hideAppIcon
import org.florisboard.lib.android.showAppIcon
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.collectIn

enum class AppTheme(val id: String) {
    AUTO("auto"),
    AUTO_AMOLED("auto_amoled"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED_DARK("amoled_dark");
}

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("LocalNavController not initialized")
}

// ROADMAP §6 N13.2 — bundle key used by `FlorisAppActivity` to round-trip the soft-IME
// visibility across a configuration change so Android 17 (API 37) builds can re-show
// the IME after `onCreate`. Pre-Android-17 the platform still restores the IME itself,
// so reading this back is a benign idempotent no-op.
const val SAVED_KEY_IME_VISIBLE = "swiftfloris.app.ime_visible"

class FlorisAppActivity : ComponentActivity() {
    private val prefs by FlorisPreferenceStore
    private val appContext by appContext()
    private val cacheManager by cacheManager()
    private var appTheme by mutableStateOf(AppTheme.AUTO)
    private var showAppIcon = true
    private var resourcesContext by mutableStateOf(this as Context)
    private var intentToBeHandled by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // R2-1: application init can stage a recoverable startup exception
        // before prefs load. Surface it before the splash keep condition can
        // wait forever on preferenceStoreLoaded.
        stagedStartupCrashIntent(this)?.let { crashIntent ->
            super.onCreate(savedInstanceState)
            startActivity(crashIntent)
            finish()
            return
        }
        // Splash screen should be installed before calling super.onCreate()
        installSplashScreen().apply {
            setKeepOnScreenCondition { !appContext.preferenceStoreLoaded.value }
        }
        super.onCreate(savedInstanceState)
        // Matrix #12 — Android 16 edge-to-edge migration. `targetSdk 36` forces edge-to-edge by default; we opt
        // in explicitly via the modern `enableEdgeToEdge()` API so the activity carries the same behavior across
        // pre-API-36 builds too, and so the `SystemBarStyle` is set deliberately (transparent bars). This also
        // covers the old `setDecorFitsSystemWindows(false)` contract that the inset-aware Scaffold path already
        // depends on inside `FlorisScreen`.
        enableEdgeToEdge()

        // ROADMAP §6 N13.2 — Android 17 (API 37) no longer auto-restores soft-IME
        // visibility across configuration changes for apps that don't handle the
        // change themselves [STD-A17-BEHAVIOR]. FlorisAppActivity has no
        // `android:configChanges` set, so it recreates on rotation, and a focused
        // text-input surface in Settings (search bars, dictionary editor) would
        // appear with the IME closed even though the user had it open. Restore the
        // IME explicitly here when the activity is recreated and the saved bundle
        // remembers it was visible. Pre-Android-17 builds also benefit (the call
        // is a no-op when the IME is already visible).
        if (savedInstanceState?.getBoolean(SAVED_KEY_IME_VISIBLE, false) == true) {
            window?.decorView?.post {
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.ime())
            }
        }

        prefs.other.settingsTheme.asFlow().collectIn(lifecycleScope) {
            appTheme = it
        }
        prefs.other.settingsLanguage.asFlow().collectIn(lifecycleScope) {
            val config = Configuration(resources.configuration)
            val locale = if (it == "auto") FlorisLocale.default() else FlorisLocale.fromTag(it)
            config.setLocale(locale.base)
            resourcesContext = createConfigurationContext(config)
        }
        if (AndroidVersion.ATMOST_API28_P) {
            prefs.other.showAppIcon.asFlow().collectIn(lifecycleScope) {
                showAppIcon = it
            }
        }

        // We defer the setContent call until the datastore model is loaded, until then the splash screen stays drawn
        val isModelLoaded = AtomicBoolean(false)
        appContext.preferenceStoreLoaded.collectIn(lifecycleScope) { loaded ->
            if (!loaded || isModelLoaded.getAndSet(true)) return@collectIn
            stagedStartupCrashIntent(this)?.let { crashIntent ->
                startActivity(crashIntent)
                finish()
                return@collectIn
            }
            // Check if android 13+ is running and the NotificationPermission is not set
            if (AndroidVersion.ATLEAST_API33_T &&
                prefs.internal.notificationPermissionState.get() == NotificationPermissionState.NOT_SET
            ) {
                // update pref value to show the setup screen again
                prefs.internal.isImeSetUp.set(false)
            }
            AppVersionUtils.updateVersionOnInstallAndLastUse(this, prefs)
            setContent {
                ProvideLocalizedResources(
                    resourcesContext,
                    appName = R.string.app_name,
                ) {
                    FlorisAppTheme(theme = appTheme) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            AppContent()
                        }
                    }
                }
            }
            onNewIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // ROADMAP §6 N13.2 — snapshot whether the soft IME is currently visible so
        // the post-rotation `onCreate` can restore it. Window may be null during
        // some teardown paths so probe defensively. WindowInsetsCompat collapses
        // the API-26..API-30 platform variants into a single `Type.ime()` accessor.
        val imeVisible = window?.decorView?.let { decor ->
            ViewCompat.getRootWindowInsets(decor)?.isVisible(WindowInsetsCompat.Type.ime())
        } ?: false
        outState.putBoolean(SAVED_KEY_IME_VISIBLE, imeVisible)
    }

    override fun onPause() {
        super.onPause()

        // App icon visibility control was restricted in Android 10.
        // See https://developer.android.com/reference/android/content/pm/LauncherApps#getActivityList(java.lang.String,%20android.os.UserHandle)
        if (AndroidVersion.ATMOST_API28_P) {
            if (showAppIcon) {
                this.showAppIcon()
            } else {
                this.hideAppIcon()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.action == Intent.ACTION_VIEW && intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true) {
            intentToBeHandled = intent
            return
        }
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            intentToBeHandled = intent
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.importUris().isNotEmpty()) {
            intentToBeHandled = intent
            return
        }
        intentToBeHandled = null
    }

    @Composable
    private fun AppContent() {
        val navController = rememberNavController()
        val previewFieldController = rememberPreviewFieldController()

        val isImeSetUp by prefs.internal.isImeSetUp.collectAsState()

        CompositionLocalProvider(
            LocalNavController provides navController,
            LocalPreviewFieldController provides previewFieldController,
        ) {
            ProvideDefaultDialogPrefStrings(
                confirmLabel = stringRes(R.string.action__ok),
                dismissLabel = stringRes(R.string.action__cancel),
                neutralLabel = stringRes(R.string.action__default),
            ) {
                Column(
                    modifier = Modifier
                        //.statusBarsPadding()
                        .navigationBarsPadding()
                        .conditional(LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            displayCutoutPadding()
                        }
                        .imePadding(),
                ) {
                    Routes.AppNavHost(
                        modifier = Modifier.weight(1.0f),
                        navController = navController,
                        startDestination = if (isImeSetUp) Routes.Settings.Home::class else Routes.Setup.Screen::class,
                    )
                    PreviewKeyboardField(previewFieldController)
                }
            }
        }

        LaunchedEffect(intentToBeHandled) {
            val intent = intentToBeHandled
            if (intent != null) {
                if (intent.action == Intent.ACTION_VIEW && intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true) {
                    navController.handleDeepLink(intent)
                } else {
                    val uris = intent.importUris()
                    val workspace = if (uris.isNotEmpty()) {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                cacheManager.readFromUriIntoCache(uris)
                            }
                        }.getOrNull()
                    } else {
                        null
                    }
                    navController.navigate(Routes.Ext.Import(ExtensionImportScreenType.EXT_ANY, workspace?.uuid))
                }
            }
            intentToBeHandled = null
        }
    }

    private fun Intent.importUris(): List<Uri> {
        return buildList {
            if (action == Intent.ACTION_VIEW) {
                data?.let { add(it) }
                return@buildList
            }
            clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let { add(it) }
                }
            }
            streamExtraUri()?.let { uri ->
                if (none { it == uri }) add(uri)
            }
        }
    }

    private fun Intent.streamExtraUri(): Uri? {
        return if (AndroidVersion.ATLEAST_API33_T) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

internal fun stagedStartupCrashIntent(context: Context): Intent? {
    return if (CrashUtility.consumeStagedException(context.applicationContext)) {
        Intent(context, CrashDialogActivity::class.java)
    } else {
        null
    }
}
