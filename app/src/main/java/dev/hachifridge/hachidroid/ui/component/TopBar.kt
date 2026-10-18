package dev.hachifridge.hachidroid.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection

/** Stable wrapper around Material's experimental [TopAppBarScrollBehavior]. */
@OptIn(ExperimentalMaterial3Api::class)
class TopBarScrollBehavior internal constructor(
    internal val behavior: TopAppBarScrollBehavior
) {
    /** Pass to `Modifier.nestedScroll(...)` on the screen's scrollable content. */
    val nestedScrollConnection: NestedScrollConnection
        get() = behavior.nestedScrollConnection
}

/** Top bar that collapses on scroll and picks up the scrolled container color (M3 guidance). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberTopBarScrollBehavior(): TopBarScrollBehavior =
    TopBarScrollBehavior(TopAppBarDefaults.exitUntilCollapsedScrollBehavior())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopBarScrollBehavior? = null,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge)
        },
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior?.behavior ?: TopAppBarDefaults.pinnedScrollBehavior()
    )
}
