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

package dev.patrickgold.florisboard.app.ext

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import io.kotest.matchers.collections.shouldBeEmpty
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * A file shared to the app used to be copied by the activity, which dropped any failure and opened the import
 * screen on its empty "No files selected" state. The screen now copies shared files itself, the way it copies
 * files picked there.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
class ExtensionImportSharedSourcesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private lateinit var source: SharedImportSource
    private val uri = Uri.parse("content://${SharedImportSource.AUTHORITY}/pack.flex")

    @Before
    fun setUp() {
        source = Robolectric.setupContentProvider(SharedImportSource::class.java, SharedImportSource.AUTHORITY)
    }

    @Test
    fun aShareTheAppMayNotReadShowsAnAnnouncedErrorAndRetryReadsItAgain() {
        source.mode = SharedImportSource.Mode.Denied
        showShared()

        awaitAnnounced(R.string.ext__import__error_title)
        assertNoEmptyState()
        assertNoWorkspaceLeft()

        // Permission came back (here the provider just stops refusing), so Retry gets further this time.
        source.mode = SharedImportSource.Mode.Malformed
        val queriesBefore = source.queries.get()
        composeRule.onNodeWithText(string(R.string.action__retry)).performClick()

        awaitAnnounced(R.string.ext__import__none_ready_title)
        check(source.queries.get() > queriesBefore) { "Retry didn't read the shared file again" }
    }

    @Test
    fun anOversizeShareShowsAnAnnouncedError() {
        source.mode = SharedImportSource.Mode.Oversize
        showShared()

        awaitAnnounced(R.string.ext__import__error_title)
        composeRule.onNodeWithText(string(R.string.action__retry)).assertExists()
        assertNoEmptyState()
        assertNoWorkspaceLeft()
    }

    @Test
    fun aTruncatedShareShowsAnAnnouncedWarningWithAnAction() {
        source.mode = SharedImportSource.Mode.Truncated
        showShared()

        awaitAnnounced(R.string.ext__import__none_ready_title)
        assertActionOn(R.string.ext__import__none_ready_title)
        assertNoEmptyState()
    }

    @Test
    fun aMalformedShareShowsAnAnnouncedWarningWithAnAction() {
        source.mode = SharedImportSource.Mode.Malformed
        showShared()

        awaitAnnounced(R.string.ext__import__none_ready_title)
        assertActionOn(R.string.ext__import__none_ready_title)
        assertNoEmptyState()
    }

    @Test
    fun aScreenThatComesBackAfterTheAppWasEndedCopiesTheSharedFileAgain() {
        source.mode = SharedImportSource.Mode.Malformed
        val restorer = StateRestorationTester(composeRule)
        // Only the first screen is handed the share. The one that comes back has nothing but what it saved.
        var shared: List<String>? = listOf(uri.toString())
        restorer.setContent {
            Frame {
                ExtensionImportScreen(ExtensionImportScreenType.EXT_ANY, initUuid = null, sharedSources = shared)
            }
        }
        awaitAnnounced(R.string.ext__import__none_ready_title)
        val queriesBefore = source.queries.get()

        // A new process starts with no import workspaces in memory.
        val cacheManager by composeRule.activity.cacheManager()
        cacheManager.importer.dir.listFiles().orEmpty().forEach { cacheManager.importer.removeByUuid(it.name) }
        shared = null
        restorer.emulateSavedInstanceStateRestore()

        composeRule.waitUntil(timeoutMillis = 10_000) { source.queries.get() > queriesBefore }
        awaitAnnounced(R.string.ext__import__none_ready_title)
        assertNoEmptyState()
    }

    private fun showShared() {
        composeRule.setContent {
            Frame {
                ExtensionImportScreen(ExtensionImportScreenType.EXT_ANY, initUuid = null, sharedSources = listOf(uri.toString()))
            }
        }
    }

    private fun string(@StringRes id: Int) = composeRule.activity.getString(id)

    private fun awaitAnnounced(@StringRes title: Int) {
        val announced = hasText(string(title)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(announced).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertActionOn(@StringRes title: Int) {
        composeRule.onAllNodes(hasText(string(title)) and hasText(string(R.string.action__select_files)))
            .assertCountEquals(1)
    }

    private fun assertNoEmptyState() {
        composeRule.onAllNodesWithText(string(R.string.ext__import__empty_title)).assertCountEquals(0)
    }

    private fun assertNoWorkspaceLeft() {
        val cacheManager by composeRule.activity.cacheManager()
        cacheManager.importer.dir.listFiles().orEmpty().toList().shouldBeEmpty()
    }
}

@Composable
private fun Frame(content: @Composable () -> Unit) {
    ProvideLocalizedResources(resourcesContext = LocalContext.current, appName = R.string.app_name) {
        FlorisAppTheme(theme = AppTheme.LIGHT) {
            val navController = rememberNavController()
            CompositionLocalProvider(LocalNavController provides navController) {
                ProvideDefaultDialogPrefStrings(
                    confirmLabel = stringRes(R.string.action__ok),
                    dismissLabel = stringRes(R.string.action__cancel),
                    neutralLabel = stringRes(R.string.action__default),
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        content()
                    }
                }
            }
        }
    }
}

/** Shares one file named pack.flex and fails the way [mode] says. */
class SharedImportSource : ContentProvider() {
    enum class Mode { Denied, Oversize, Truncated, Malformed }

    @Volatile
    var mode = Mode.Malformed

    val queries = AtomicInteger()

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        queries.incrementAndGet()
        if (mode == Mode.Denied) {
            throw SecurityException("Permission Denial: reading $uri requires a grant this app doesn't have")
        }
        val reportedSize = when (mode) {
            Mode.Oversize -> 600L * 1024 * 1024
            else -> ARCHIVE.size.toLong()
        }
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any>(uri.lastPathSegment!!, reportedSize))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val served = when (this.mode) {
            Mode.Truncated -> ARCHIVE.copyOf(ARCHIVE.size / 2)
            else -> "this is not a zip archive".toByteArray()
        }
        val file = File(context!!.filesDir, "shared-source").apply { writeBytes(served) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "swiftfloris.test.sharedimport"

        /** A real archive, so cutting it in half gives a truncated one rather than noise. */
        private val ARCHIVE: ByteArray = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("extension.json"))
                zip.write("""{"\$": "ime.extension.theme", "meta": {"id": "test.shared"}}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("stylesheets/padding.json"))
                zip.write(ByteArray(4096) { (it % 251).toByte() })
                zip.closeEntry()
            }
        }.toByteArray()
    }
}
