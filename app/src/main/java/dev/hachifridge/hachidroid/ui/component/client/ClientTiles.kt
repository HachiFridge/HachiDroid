package dev.hachifridge.hachidroid.ui.component.client

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.client.Client
import dev.hachifridge.hachidroid.client.LibrarySet
import dev.hachifridge.hachidroid.client.tracen.TracenLibrarySafeguard
import dev.hachifridge.hachidroid.ui.screen.HomeViewModel

/**
 * The dashboard tiles below [dev.hachifridge.hachidroid.ui.patcher.PatchingStudioCard]: where the
 * client's mod comes from, and the plugin list its runtime can load.
 *
 * The native-library checklist is no longer a tile: it gates the patch itself, so it lives inside
 * Patch Studio where the decision is made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientTilesRow(
    viewModel: HomeViewModel,
    client: Client,
    libraries: LibrarySet.Status
) {
    // Both tiles are forced to the tallest content's height; otherwise each card wraps its own
    // text and the pair reads as mismatched.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ClientModSourceCard(
            viewModel = viewModel,
            libraries = libraries,
            client = client,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )

        ClientPluginsCard(
            viewModel = viewModel,
            client = client,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

// Both tiles share one header geometry: the same icon box, badge shape and text metrics, so the
// pair reads as two halves of a row instead of two cards that happen to sit beside each other.
private val BentoIconBox = 32.dp
private val BentoGlyphSize = 24.dp
// The mascot is full-bleed art with no built-in padding, unlike a Material glyph, so it gets a
// slightly smaller box to keep the same optical weight as the plugin icon.
private val BentoArtSize = 28.dp

/**
 * Header shared by both tiles: a fixed-height icon box, a flexible gap and a badge.
 *
 * The fixed height is the point: an icon's own size no longer decides how far down the title
 * block starts, so the two cards' titles sit on the same line.
 */
@Composable
private fun BentoHeader(
    badge: @Composable () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BentoIconBox),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(BentoIconBox),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.weight(1f))
        badge()
    }
}

/** The "Local" / "0 active" pill, defined once so both tiles cannot drift apart. */
@Composable
private fun BentoBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** Title and one-line subtitle, matching in both tiles down to the gap and the ellipsis rule. */
@Composable
private fun BentoTextBlock(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** The client's second tile: the plugin libraries its mod runtime loads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientPluginsCard(
    viewModel: HomeViewModel,
    client: Client,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val plugins by viewModel.plugins.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

    val importPluginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "plugin.so"

        // Tracen's plugins are Hachimi extensions and carry its marker; Astraea's only need the
        // architecture and name checks the store performs.
        val error = if (client == Client.TRACEN) {
            (TracenLibrarySafeguard.validatePluginUri(context, uri, name)
                as? TracenLibrarySafeguard.ValidationResult.Error)?.message(context)
        } else {
            client.plugins.validationError(context, uri, name)
        }

        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        } else {
            viewModel.addPlugin(context, uri)
        }
    }

    val activeCount = plugins.count { it.enabled }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.plugins_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.plugin_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                if (plugins.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_plugins),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    plugins.forEach { plugin ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = plugin.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = plugin.enabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.setPluginEnabled(context, plugin.fileName, enabled)
                                    }
                                )
                                IconButton(
                                    onClick = { viewModel.removePlugin(context, plugin.fileName) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.remove_plugin),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                FilledTonalButton(
                    onClick = { importPluginLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_plugin))
                }
            }
        }
    }

    Card(
        modifier = modifier.clickable { showSheet = true },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            BentoHeader(
                badge = { BentoBadge(stringResource(R.string.plugins_active_badge, activeCount)) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(BentoGlyphSize)
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            BentoTextBlock(
                title = stringResource(R.string.plugins_title),
                subtitle = if (plugins.isEmpty()) {
                    stringResource(R.string.plugins_none_installed)
                } else {
                    stringResource(R.string.plugins_installed_count, plugins.size)
                }
            )
        }
    }
}

/**
 * Where this client's mod runtime comes from.
 *
 * There is exactly one of these per client and one control for it: the patch reads whatever this
 * source put in the library folder, so a second "fetch mod" button in the studio would only be a
 * second way to change the same file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientModSourceCard(
    viewModel: HomeViewModel,
    libraries: LibrarySet.Status,
    client: Client,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    val modSource = viewModel.modSourceKind
    val source = client.modSource
    val modSoName = viewModel.modSoName
    val mascot = if (client == Client.ASTRAEA) R.drawable.ic_astraea_mascot else R.drawable.ic_tracen_mascot
    val modReady = libraries.isPresent(source.libraryName)
    val modLoadedPattern = stringResource(R.string.tracen_mod_loaded)

    val modSoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = DocumentFile.fromSingleUri(context, uri)?.name ?: source.libraryName

        // Hachimi's runtime is picked out of its own releases, so a plugin or an unrelated library
        // must be rejected before it replaces it. DaringTact only needs the ELF/architecture check
        // the import performs.
        if (client == Client.TRACEN) {
            when (val validation = TracenLibrarySafeguard.validateModLibraryUri(context, uri, name)) {
                is TracenLibrarySafeguard.ValidationResult.Valid -> {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                    }
                    viewModel.setModSource(context, "local")
                    viewModel.importMod(context, uri)
                    Toast.makeText(
                        context,
                        String.format(modLoadedPattern, validation.version),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is TracenLibrarySafeguard.ValidationResult.Error ->
                    Toast.makeText(context, validation.message(context), Toast.LENGTH_LONG).show()
            }
        } else {
            viewModel.setModSource(context, "local")
            viewModel.importMod(context, uri)
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.mod_engine_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.mod_source_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: GitHub releases
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (modSource == "github") MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(
                        1.dp,
                        if (modSource == "github") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setModSource(context, "github") }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = modSource == "github",
                                onClick = { viewModel.setModSource(context, "github") }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.mod_source_github),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = viewModel.modRepo.ifEmpty {
                                        stringResource(R.string.mod_repo_missing)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (modSource == "github") {
                            Spacer(Modifier.height(12.dp))
                            ModLibraryButton(
                                cached = modReady,
                                busy = viewModel.fetchingMod,
                                onClick = { viewModel.fetchMod(context) }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (modReady) {
                                    stringResource(R.string.mod_library_ready, source.libraryName)
                                } else {
                                    stringResource(R.string.mod_library_missing)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Option 2: a local .so
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (modSource == "local") MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(
                        1.dp,
                        if (modSource == "local") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setModSource(context, "local") }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = modSource == "local",
                                onClick = { viewModel.setModSource(context, "local") }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.mod_source_local),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = modSoName.ifEmpty { stringResource(R.string.mod_no_file) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (modSource == "local") {
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { modSoLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_file_open),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.tap_to_select_mod_lib))
                            }
                        }
                    }
                }
            }
        }
    }

    Card(
        modifier = modifier.clickable { showSheet = true },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            BentoHeader(
                badge = {
                    BentoBadge(
                        if (modSource == "github") stringResource(R.string.mod_badge_online)
                        else stringResource(R.string.mod_badge_local)
                    )
                },
                icon = {
                    Icon(
                        painter = painterResource(mascot),
                        contentDescription = null,
                        modifier = Modifier.size(BentoArtSize),
                        // Coloured artwork: tinting it would flatten it into a single stroke.
                        tint = Color.Unspecified
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            BentoTextBlock(
                title = stringResource(R.string.mod_engine_title),
                subtitle = if (modSource == "github") viewModel.modRepo
                else modSoName.ifEmpty { stringResource(R.string.mod_no_file) }
            )
        }
    }
}

/**
 * One button for both clients: a cached library may have gone stale, so it offers a re-download
 * rather than claiming the same thing twice.
 */
@Composable
private fun ModLibraryButton(cached: Boolean, busy: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        enabled = !busy
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_file_open),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                if (cached) R.string.mod_library_redownload else R.string.mod_library_download
            )
        )
    }
}
