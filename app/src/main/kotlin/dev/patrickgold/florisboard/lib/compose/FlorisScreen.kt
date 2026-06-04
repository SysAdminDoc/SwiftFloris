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

package dev.patrickgold.florisboard.lib.compose

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.settings.search.SettingsSearchHighlightStore
import dev.patrickgold.florisboard.app.settings.search.SettingsSearchTarget
import dev.patrickgold.jetpref.datastore.ui.PreferenceLayout
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiContent
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.FlorisAppBar
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.autoMirrorForRtl
import org.florisboard.lib.compose.florisVerticalScroll
import org.florisboard.lib.compose.stringRes

internal object FlorisScreenFocusOrder {
    const val AppBar = 0f
    const val Content = 1f
    const val BottomBar = 2f
    const val FloatingActionButton = 3f

    val orderedTraversal = listOf(AppBar, Content, BottomBar, FloatingActionButton)
}

@Composable
fun FlorisScreen(builder: @Composable FlorisScreenScope.() -> Unit) {
    val scope = remember { FlorisScreenScopeImpl() }
    builder(scope)
    scope.Render()
}

typealias FlorisScreenActions = @Composable RowScope.() -> Unit
typealias FlorisScreenBottomBar = @Composable () -> Unit
typealias FlorisScreenContent = PreferenceUiContent<FlorisPreferenceModel>
typealias FlorisScreenFab = @Composable () -> Unit
typealias FlorisScreenNavigationIcon = @Composable () -> Unit

interface FlorisScreenScope {
    var title: String

    var navigationIconVisible: Boolean

    var previewFieldVisible: Boolean

    var scrollable: Boolean

    var iconSpaceReserved: Boolean

    fun actions(actions: FlorisScreenActions)

    fun bottomBar(bottomBar: FlorisScreenBottomBar)

    fun content(content: FlorisScreenContent)

    fun floatingActionButton(fab: FlorisScreenFab)

    fun navigationIcon(navigationIcon: FlorisScreenNavigationIcon)
}

private class FlorisScreenScopeImpl : FlorisScreenScope {
    override var title: String by mutableStateOf("")
    override var navigationIconVisible: Boolean by mutableStateOf(true)
    override var previewFieldVisible: Boolean by mutableStateOf(false)
    override var scrollable: Boolean by mutableStateOf(true)
    override var iconSpaceReserved: Boolean by mutableStateOf(true)

    private var actions: FlorisScreenActions = @Composable { }
    private var bottomBar: FlorisScreenBottomBar = @Composable { }
    private var content: FlorisScreenContent = @Composable { }
    private var fab: FlorisScreenFab = @Composable { }
    private var navigationIcon: FlorisScreenNavigationIcon = @Composable {
        val navController = LocalNavController.current
        FlorisIconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.autoMirrorForRtl(),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringRes(R.string.action__back),
        )
    }

    override fun actions(actions: FlorisScreenActions) {
        this.actions = actions
    }

    override fun bottomBar(bottomBar: FlorisScreenBottomBar) {
        this.bottomBar = bottomBar
    }

    override fun content(content: FlorisScreenContent) {
        this.content = content
    }

    override fun floatingActionButton(fab: FlorisScreenFab) {
        this.fab = fab
    }

    override fun navigationIcon(navigationIcon: FlorisScreenNavigationIcon) {
        this.navigationIcon = navigationIcon
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Render() {
        val context = LocalContext.current
        val previewFieldController = LocalPreviewFieldController.current
        val colorScheme = MaterialTheme.colorScheme

        @Suppress("DEPRECATION")
        SideEffect {
            val window = (context as Activity).window
            previewFieldController?.isVisible = previewFieldVisible
            window.statusBarColor = Color.Transparent.toArgb()
            if (AndroidVersion.ATLEAST_API29_Q) {
                window.navigationBarColor = Color.Transparent.toArgb()
                window.isNavigationBarContrastEnforced = true
            } else {
                window.navigationBarColor = colorScheme.scrim.toArgb()
            }
        }

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

        // Matrix #11 — Android 16 pane-aware accessibility migration. Setting `paneTitle` on the Scaffold root
        // makes TalkBack announce the new screen name when the user navigates into it, without relying on the
        // deprecated disruptive `TYPE_ANNOUNCEMENT` events that Android 16 (API 36) discourages.
        Scaffold(
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .semantics {
                    isTraversalGroup = true
                    if (title.isNotBlank()) paneTitle = title
                },
            containerColor = colorScheme.background,
            topBar = {
                FlorisAppBar(
                    title = title,
                    navigationIcon = navigationIcon.takeIf { navigationIconVisible },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.semantics {
                        traversalIndex = FlorisScreenFocusOrder.AppBar
                    },
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier.semantics {
                        traversalIndex = FlorisScreenFocusOrder.BottomBar
                    },
                ) {
                    bottomBar()
                }
            },
            floatingActionButton = {
                Box(
                    modifier = Modifier.semantics {
                        traversalIndex = FlorisScreenFocusOrder.FloatingActionButton
                    },
                ) {
                    fab()
                }
            },
        ) { innerPadding ->
            val scrollModifier = if (scrollable) {
                Modifier.florisVerticalScroll()
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .semantics {
                        traversalIndex = FlorisScreenFocusOrder.Content
                    },
            ) {
                PreferenceLayout(
                    FlorisPreferenceStore,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = if (scrollable) 8.dp else 0.dp)
                        .then(scrollModifier),
                    iconSpaceReserved = iconSpaceReserved,
                    content = {
                        val pendingSearchTarget = SettingsSearchHighlightStore.activeTarget
                        var displayedSearchTarget by remember(title) {
                            mutableStateOf<SettingsSearchTarget?>(null)
                        }
                        LaunchedEffect(title, pendingSearchTarget) {
                            SettingsSearchHighlightStore.consumeTargetFor(title)?.let { target ->
                                displayedSearchTarget = target
                            }
                        }
                        displayedSearchTarget?.let { searchTarget ->
                            SettingsSearchHighlightCard(
                                modifier = Modifier.padding(8.dp),
                                text = stringRes(
                                    R.string.settings__search__highlight_title,
                                    "setting_title" to searchTarget.title,
                                ),
                                secondaryText = searchTarget.summary ?: stringRes(
                                    R.string.settings__search__highlight_summary,
                                    "screen_title" to searchTarget.screenTitle,
                                ),
                                onDismiss = { displayedSearchTarget = null },
                            )
                        }
                        content()
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchHighlightCard(
    text: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp),
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringRes(R.string.action__close),
                )
            }
        }
    }
}
