package dev.hachifridge.hachidroid.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hachifridge.hachidroid.BuildConfig
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.ui.component.BackButton
import dev.hachifridge.hachidroid.ui.component.BottomBarScrollSpacer
import dev.hachifridge.hachidroid.ui.component.SectionCard
import dev.hachifridge.hachidroid.ui.component.SectionHeader
import dev.hachifridge.hachidroid.ui.component.TopBar
import dev.hachifridge.hachidroid.ui.component.rememberTopBarScrollBehavior
import dev.hachifridge.hachidroid.ui.navigation.Destination
import dev.hachifridge.hachidroid.ui.navigation.Navigator
import dev.hachifridge.hachidroid.utils.safeNavigate

private val SOURCE_URL = "https://github.com/${BuildConfig.REPO_PATH}"
private val ISSUES_URL = "$SOURCE_URL/issues"

/** Shown under "View source code"; derived so it can never disagree with [SOURCE_URL]. */
private val SOURCE_DISPLAY = SOURCE_URL.removePrefix("https://")

@Composable
fun AboutScreen(
    navigator: Navigator,
    viewModel: AboutViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current

    val scrollBehavior = rememberTopBarScrollBehavior()

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.about),
                navigationIcon = { BackButton(navigator) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
        ) {
            AboutHero()

            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                SectionHeader(stringResource(R.string.about_section_details))
                SectionCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp)) {
                    AboutInfoRow(
                        label = stringResource(R.string.about_version_label),
                        value = BuildConfig.VERSION_NAME
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    AboutInfoRow(
                        label = stringResource(R.string.about_build_label),
                        value = BuildConfig.VERSION_CODE.toString()
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    AboutInfoRow(
                        label = stringResource(R.string.about_package_label),
                        value = context.packageName,
                        monospaced = true
                    )
                }

                Spacer(Modifier.height(24.dp))

                SectionHeader(stringResource(R.string.about_section_resources))
                SectionCard(contentPadding = PaddingValues(vertical = 2.dp)) {
                    AboutActionRow(
                        title = stringResource(R.string.check_for_updates),
                        subtitle = stringResource(R.string.about_check_updates_desc),
                        icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) }
                    ) {
                        viewModel.checkForUpdates(context)
                    }
                    AboutActionRow(
                        title = stringResource(R.string.open_source_licenses),
                        subtitle = stringResource(R.string.about_licenses_desc),
                        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        showDivider = false
                    ) {
                        safeNavigate(lifecycleOwner) {
                            navigator.navigate(Destination.OpenSourceLicenses)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                SectionHeader(stringResource(R.string.about_section_project))
                SectionCard(contentPadding = PaddingValues(vertical = 2.dp)) {
                    AboutActionRow(
                        title = stringResource(R.string.view_source_code),
                        subtitle = SOURCE_DISPLAY,
                        icon = { Icon(Icons.Outlined.Code, contentDescription = null) }
                    ) {
                        uriHandler.openUri(SOURCE_URL)
                    }
                    AboutActionRow(
                        title = stringResource(R.string.about_issue),
                        subtitle = stringResource(R.string.about_issue_desc),
                        icon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                        showDivider = false
                    ) {
                        uriHandler.openUri(ISSUES_URL)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.about_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )

                BottomBarScrollSpacer()
            }
        }
    }
}

/** Icon, name, version chip and what the app actually does. */
@Composable
private fun AboutHero() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The art carries its own background, so the container is only a mask: no frame,
        // no inner padding, the image runs to the rounded edge like a launcher icon.
        Image(
            painter = painterResource(R.drawable.ic_about_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(28.dp))
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Text(
                text = stringResource(
                    R.string.about_version_chip,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.app_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Read-only label/value line, e.g. the version code or the package name. */
@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    monospaced: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospaced) FontFamily.Monospace else null,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Tappable row with a title, an optional explanation and a trailing chevron. */
@Composable
private fun AboutActionRow(
    title: String,
    icon: @Composable () -> Unit,
    showDivider: Boolean = true,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 62.dp)
            )
        }
    }
}
