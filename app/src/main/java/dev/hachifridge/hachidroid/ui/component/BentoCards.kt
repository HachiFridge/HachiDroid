package dev.hachifridge.hachidroid.ui.component

import android.content.Intent
import android.text.format.Formatter
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.core.HachimiSource
import dev.hachifridge.hachidroid.core.PatchMode
import dev.hachifridge.hachidroid.core.AstraeaLibraries
import dev.hachifridge.hachidroid.patcher.HachimiLibrarySafeguard
import dev.hachifridge.hachidroid.ui.screen.HomeViewModel

/**
 * Two tiles, one per thing a patch needs from outside the game: where the mod comes from, and the
 * libraries it is written with. Tracen's second tile is its plugin list; Astraea has no plugins, so
 * it gets the library checklist instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BentoDashboardRow(
    viewModel: HomeViewModel,
    mode: PatchMode,
    libraries: AstraeaLibraries.Status
) {
    // Both tiles are forced to the tallest content's height; otherwise each card wraps
    // its own text and the pair reads as mismatched.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModSourceBentoCard(
            viewModel = viewModel,
            libraries = libraries,
            mode = mode,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )

        if (mode == PatchMode.ASTRAEA) {
            LibrariesBentoCard(
                viewModel = viewModel,
                libraries = libraries,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        } else {
            PluginBentoCard(viewModel, modifier = Modifier.weight(1f).fillMaxHeight())
        }
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
 * Header shared by both bento tiles: a fixed-height icon box, a flexible gap and a badge.
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

/** Where the mod comes from, in whichever shape that game needs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModSourceBentoCard(
    viewModel: HomeViewModel,
    libraries: AstraeaLibraries.Status,
    mode: PatchMode,
    modifier: Modifier = Modifier
) {
    if (mode == PatchMode.ASTRAEA) {
        AstraeaModSourceCard(viewModel, libraries, modifier)
    } else {
        TracenModSourceCard(viewModel, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TracenModSourceCard(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    val modSource = viewModel.tracenModSource
    val tracenModSoName = viewModel.tracenModSoName
    val mascot = R.drawable.ic_tracen_mascot

    val tracenModSoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "libmain-arm64-v8a.so"

        // Patching Safeguard: ensure it's a valid Hachimi library and not a plugin or wrong .so
        val validation = HachimiLibrarySafeguard.validateModLibraryUri(context, uri, name)
        when (validation) {
            is HachimiLibrarySafeguard.ValidationResult.Valid -> {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                viewModel.importTracenMod(context, uri, name)
                Toast.makeText(
                    context,
                    context.getString(R.string.tracen_mod_loaded, validation.version),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is HachimiLibrarySafeguard.ValidationResult.Error -> {
                Toast.makeText(context, validation.message(context), Toast.LENGTH_LONG).show()
            }
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
                    text = stringResource(R.string.tracen_mod_source_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.tracen_mod_source_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: GitHub Releases
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (modSource == "github") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, if (modSource == "github") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setTracenModSource(context, "github")
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = modSource == "github",
                                onClick = { viewModel.setTracenModSource(context, "github") }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.mod_source_github),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = viewModel.hachimiRepo.ifEmpty {
                                        stringResource(R.string.astraea_mod_repo_missing)
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
                                cached = viewModel.tracenModCached,
                                busy = viewModel.fetchingTracenMod,
                                onClick = { viewModel.fetchTracenMod(context) }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (viewModel.tracenModCached) {
                                    stringResource(R.string.mod_library_ready, HachimiSource.MOD_LIB_NAME)
                                } else {
                                    stringResource(R.string.mod_library_missing)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Option 2: Local .so Library
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (modSource == "local") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, if (modSource == "local") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setTracenModSource(context, "local")
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = modSource == "local",
                                onClick = { viewModel.setTracenModSource(context, "local") }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.mod_source_local),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (tracenModSoName.isNotEmpty()) tracenModSoName
                                    else stringResource(R.string.mod_no_file),
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
                                onClick = { tracenModSoLauncher.launch(arrayOf("*/*")) },
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
        modifier = modifier
            .clickable { showSheet = true },
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
                subtitle = if (modSource == "github") viewModel.hachimiRepo
                    else tracenModSoName.ifEmpty { stringResource(R.string.mod_source_local) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginBentoCard(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val plugins by viewModel.plugins.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

    val importPluginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "plugin.so"

        // Patching Safeguard: ensure it's a valid 64-bit plugin and not the core Hachimi engine or engine collision
        val validation = HachimiLibrarySafeguard.validatePluginUri(context, uri, name)
        when (validation) {
            is HachimiLibrarySafeguard.ValidationResult.Valid -> {
                viewModel.addPlugin(context, uri)
            }
            is HachimiLibrarySafeguard.ValidationResult.Error -> {
                Toast.makeText(context, validation.message(context), Toast.LENGTH_LONG).show()
            }
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.plugins_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = { importPluginLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.add_plugin))
                    }
                }

                // Security Warning Banner
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.plugin_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (plugins.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_plugins),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        plugins.forEach { plugin ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Switch(
                                        checked = plugin.enabled,
                                        onCheckedChange = { isChecked ->
                                            viewModel.setPluginEnabled(context, plugin.fileName, isChecked)
                                        }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = plugin.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = plugin.fileName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removePlugin(context, plugin.fileName) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.remove_plugin),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Card(
        modifier = modifier
            .clickable { showSheet = true },
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
                        modifier = Modifier.size(BentoGlyphSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            BentoTextBlock(
                title = stringResource(R.string.plugins_title),
                subtitle = if (plugins.isEmpty()) "None installed" else "${plugins.size} installed"
            )
        }
    }
}

/** The mod library Astraea needs, from the same two places Hachimi's comes from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AstraeaModSourceCard(
    viewModel: HomeViewModel,
    libraries: AstraeaLibraries.Status,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    val modSource = viewModel.astraeaModSource
    val modReady = libraries.isPresent(AstraeaLibraries.MOD_LIBRARY)

    val modSoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importAstraeaMod(context, uri)
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
                    text = stringResource(R.string.astraea_mod_source_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.astraea_mod_source_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: GitHub Releases
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
                        .clickable { viewModel.setAstraeaModSource(context, "github") }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = modSource == "github",
                                onClick = { viewModel.setAstraeaModSource(context, "github") }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.mod_source_github),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = viewModel.daringTactRepo.ifEmpty {
                                        stringResource(R.string.astraea_mod_repo_missing)
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
                                busy = viewModel.fetchingAstraeaMod,
                                onClick = { viewModel.fetchAstraeaMod(context) }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (modReady) {
                                    stringResource(
                                        R.string.mod_library_ready,
                                        AstraeaLibraries.MOD_LIBRARY
                                    )
                                } else {
                                    stringResource(R.string.mod_library_missing)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Option 2: Local .so Library
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
                        .clickable { viewModel.setAstraeaModSource(context, "local") }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = modSource == "local",
                                onClick = { viewModel.setAstraeaModSource(context, "local") }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.mod_source_local),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = viewModel.astraeaModSoName.ifEmpty {
                                        stringResource(R.string.mod_no_file)
                                    },
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
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (modReady) {
                                    stringResource(
                                        R.string.astraea_mod_saved_to,
                                        "${AstraeaLibraries.DIRECTORY_NAME}/${AstraeaLibraries.MOD_LIBRARY}"
                                    )
                                } else {
                                    stringResource(R.string.astraea_mod_local_none)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        painter = painterResource(R.drawable.ic_astraea_mascot),
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
                subtitle = if (modSource == "github") {
                    viewModel.daringTactRepo
                } else {
                    viewModel.astraeaModSoName.ifEmpty { stringResource(R.string.mod_no_file) }
                }
            )
        }
    }
}

/**
 * One button for both games: a cached library may have gone stale, so it offers a re-download
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

/** How the two protected libraries arrive. The mod is not part of this choice. */
private enum class LibraryInput {
    /** Senbei unpacks both from the split APKs selected in the studio, on this device. */
    ON_DEVICE,

    /** The user supplies the two libraries, loose or inside an archive. */
    IMPORT
}

/**
 * Astraea's second tile: readiness for the two libraries a patched install needs, and the place
 * they are handled. The split set is picked in Patch Studio, so unpacking reads those picks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesBentoCard(
    viewModel: HomeViewModel,
    libraries: AstraeaLibraries.Status,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    var libraryInput by rememberSaveable {
        mutableStateOf(
            if (viewModel.onDeviceUnpackAvailable) LibraryInput.ON_DEVICE else LibraryInput.IMPORT
        )
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importLibraries(context, uris)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.libraries_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // The two are alternatives, not steps: one unpacks the game's protected libraries,
                // the other takes whatever the user already has.
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = libraryInput == LibraryInput.ON_DEVICE,
                        onClick = { libraryInput = LibraryInput.ON_DEVICE },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text(stringResource(R.string.libraries_tab_on_device)) }
                    )
                    SegmentedButton(
                        selected = libraryInput == LibraryInput.IMPORT,
                        onClick = { libraryInput = LibraryInput.IMPORT },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text(stringResource(R.string.libraries_tab_import)) }
                    )
                }

                AstraeaLibraries.PROTECTED_LIBS.forEach { name ->
                    libraries.libs.firstOrNull { it.name == name }?.let { lib ->
                        LibraryRow(lib, onRemove = { viewModel.removeLibrary(context, lib.name) })
                    }
                }

                if (viewModel.unpackingLibraries) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = stringResource(R.string.libraries_unpacking),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when (libraryInput) {
                    LibraryInput.ON_DEVICE -> {
                        if (viewModel.onDeviceUnpackAvailable) {
                            val alreadyUnpacked = libraries.protectedReady
                            FilledTonalButton(
                                onClick = {
                                    viewModel.unpackLibrariesFromSplits(context, viewModel.astraeaSplits)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !alreadyUnpacked &&
                                    viewModel.astraeaSplits.isNotEmpty() &&
                                    !viewModel.unpackingLibraries
                            ) {
                                Text(
                                    stringResource(
                                        if (alreadyUnpacked) R.string.libraries_unpack_cached
                                        else R.string.libraries_unpack_action
                                    )
                                )
                            }
                            Text(
                                text = stringResource(
                                    when {
                                        alreadyUnpacked -> R.string.libraries_unpack_cached_hint
                                        viewModel.astraeaSplits.isEmpty() -> R.string.libraries_unpack_need_files
                                        else -> R.string.libraries_unpack_hint
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.libraries_unpack_unavailable),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    LibraryInput.IMPORT -> {
                        FilledTonalButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.libraries_import_action))
                        }
                        Text(
                            text = stringResource(R.string.libraries_import_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stays until the next attempt: the toast version was gone before it could be read.
                viewModel.unpackError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    val present = AstraeaLibraries.PROTECTED_LIBS.count { libraries.isPresent(it) }

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
                badge = { BentoBadge("$present/${AstraeaLibraries.PROTECTED_LIBS.size}") },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(BentoGlyphSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            BentoTextBlock(
                title = stringResource(R.string.libraries_title),
                subtitle = stringResource(
                    R.string.libraries_ready_count,
                    present,
                    AstraeaLibraries.PROTECTED_LIBS.size
                )
            )
        }
    }
}

/**
 * One library in the checklist. The alignment tag is not repeated here: the game card already
 * carries it, and this row needs its trailing space for removing a stale copy.
 */
@Composable
private fun LibraryRow(lib: AstraeaLibraries.LibStatus, onRemove: () -> Unit) {
    val context = LocalContext.current
    val okColor = MaterialTheme.colorScheme.primary
    val badColor = MaterialTheme.colorScheme.error

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (lib.present) Icons.Outlined.Check else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (lib.present) okColor else badColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lib.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (lib.present) {
                        Formatter.formatShortFileSize(context, lib.sizeBytes)
                    } else {
                        stringResource(R.string.libraries_missing_lib)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (lib.present) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.libraries_remove, lib.name),
                        tint = badColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
