package dev.hachifridge.hachidroid.ui.patcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hachifridge.hachidroid.MainActivity
import dev.hachifridge.hachidroid.MainViewModel
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.core.PatchMode
import dev.hachifridge.hachidroid.core.PrefKey
import dev.hachifridge.hachidroid.core.AstraeaLibraries
import dev.hachifridge.hachidroid.core.dataStore
import dev.hachifridge.hachidroid.core.getPrefValue
import dev.hachifridge.hachidroid.patcher.AppPatcher
import dev.hachifridge.hachidroid.shizuku.ShizukuState
import dev.hachifridge.hachidroid.ui.component.SimpleOkCancelDialog
import dev.hachifridge.hachidroid.ui.navigation.Navigator
import dev.hachifridge.hachidroid.ui.screen.HomeViewModel
import dev.hachifridge.hachidroid.ui.theme.brand_success_dark
import dev.hachifridge.hachidroid.ui.theme.brand_success_light
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

enum class PatcherInstallMethod(
    val id: Int,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val icon: ImageVector
) {
    SAVE(0, R.string.save_patched_file, R.string.method_save_desc, Icons.Outlined.FileDownload),
    NORMAL(1, R.string.normal_install, R.string.method_normal_desc, Icons.Outlined.SystemUpdate),
    DIRECT(2, R.string.direct_install, R.string.method_direct_desc, Icons.Outlined.Bolt),
    SHIZUKU(3, R.string.shizuku_install, R.string.method_shizuku_desc, Icons.Outlined.Security),
    LEGACY(4, R.string.legacy_install, R.string.method_legacy_desc, Icons.Outlined.History);

    companion object {
        fun fromId(id: Int): PatcherInstallMethod = entries.find { it.id == id } ?: NORMAL

        /**
         * Astraea patches the split set rather than an installed game, so root injection and the
         * legacy mount installer have nothing to operate on.
         */
        val ASTRAEA_METHODS = listOf(SAVE, NORMAL, SHIZUKU)
    }
}

private enum class PatchTargetSource {
    INSTALLED,
    FILES
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PatchingStudioCard(
    navigator: Navigator,
    mode: PatchMode,
    homeViewModel: HomeViewModel,
    libraries: AstraeaLibraries.Status = AstraeaLibraries.Status.EMPTY
) {
    val isAstraea = mode == PatchMode.ASTRAEA
    // Readiness is the two game libraries plus whatever the mod source produced. The mod is not a
    // library-patching concern, but a patch without it starts the game and does nothing.
    val modLibraryReady = libraries.isPresent(AstraeaLibraries.MOD_LIBRARY)
    val astraeaLibrariesReady = libraries.protectedReady && modLibraryReady
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mainViewModel: MainViewModel = viewModel(context as MainActivity)
    val isRootAvailable = mainViewModel.isRooted.value
    val isShizukuAvailable by ShizukuState.isAvailable
    val isGameInstalled = GameChecker.isPackageInstalled(context.packageManager)

    var showMethodSheet by remember { mutableStateOf(false) }
    var showShizukuRationaleDialog by remember { mutableStateOf(false) }
    var showShizukuNotAvailableDialog by remember { mutableStateOf(false) }
    var staleFilesError by remember { mutableStateOf<String?>(null) }

    // Target source: Installed Game vs External APK Files. Astraea always reads files: it has to
    // replace the CrackProof shell, which only happens by rebuilding and reinstalling the splits.
    var targetSource by rememberSaveable(mode) {
        mutableStateOf(
            if (!isAstraea && isGameInstalled && isRootAvailable) PatchTargetSource.INSTALLED
            else PatchTargetSource.FILES
        )
    }

    val methods = if (isAstraea) PatcherInstallMethod.ASTRAEA_METHODS else PatcherInstallMethod.entries

    val selectedMethodIndex = rememberSaveable(mode) { mutableIntStateOf(PatcherInstallMethod.NORMAL.id) }
    val currentMethod = if (PatcherInstallMethod.fromId(selectedMethodIndex.intValue) in methods) {
        PatcherInstallMethod.fromId(selectedMethodIndex.intValue)
    } else {
        PatcherInstallMethod.NORMAL
    }

    // Keyed on the mode: the two families patch different games, so a picked split belongs to
    // exactly one of them.
    var fileUris by rememberSaveable(mode) { mutableStateOf<Array<Uri>>(arrayOf()) }

    var stateLoaded by remember(mode) { mutableStateOf(false) }
    var installMethodLoaded by rememberSaveable(mode) { mutableStateOf(false) }

    val fileSelectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val newFileUris = uris.toTypedArray()
        for (uri in fileUris) releasePersistableUriPermission(context, uri)
        for (uri in newFileUris) tryTakePersistableUriPermission(context, uri)
        fileUris = newFileUris
        coroutineScope.launch { saveFileUris(context, mode, newFileUris) }
    }

    // Restore this mode's persisted state. Keyed on mode, so switching families starts the new
    // mode from its own picks instead of inheriting the previous one's split APKs.
    LaunchedEffect(mode) {
        if (!installMethodLoaded) {
            val savedInstallMethod = (context.getPrefValue(PrefKey.installMethod(mode)) as Int?)
                ?: takeLegacyInstallMethod(context, mode)
            selectedMethodIndex.intValue = savedInstallMethod
                ?.takeIf { PatcherInstallMethod.fromId(it) in methods }
                ?: PatcherInstallMethod.NORMAL.id
            installMethodLoaded = true
        }

        if (fileUris.isEmpty()) {
            // No default value exists for a per-mode key, so an untouched mode reads back as
            // null rather than "". An empty string means the user cleared the picker, and must
            // not fall back to the legacy slot.
            val storedFileUris = (context.getPrefValue(PrefKey.fileUris(mode)) as String?)
                ?: takeLegacyFileUris(context, mode)
            val savedFileUris = storedFileUris
                .split('\n')
                .filter { it.isNotEmpty() }
                .map { uri -> uri.toUri() }
            val existingFileUris = mutableListOf<Uri>()
            for (uri in savedFileUris) {
                if (canOpenUri(context, uri)) {
                    existingFileUris.add(uri)
                } else {
                    releasePersistableUriPermission(context, uri)
                }
            }
            if (existingFileUris.size != savedFileUris.size) {
                saveFileUris(context, mode, existingFileUris.toTypedArray())
            }
            if (existingFileUris.isNotEmpty()) {
                fileUris = existingFileUris.toTypedArray()
            }
        }
        stateLoaded = true
    }

    // The Libraries tile unpacks from these, so the studio keeps it in step with its own picks.
    LaunchedEffect(fileUris, mode) {
        if (isAstraea) homeViewModel.updateAstraeaSplits(fileUris.toList())
    }

    // Persist install method for this mode only
    LaunchedEffect(installMethodLoaded, mode) {
        if (!installMethodLoaded) return@LaunchedEffect
        snapshotFlow { selectedMethodIndex.intValue }
            .drop(1)
            .collectLatest { methodId ->
                saveInstallMethod(context, mode, methodId)
            }
    }

    // Shizuku permission listener. Disposable: Shizuku's dialog outlives this screen, and a grant
    // arriving after it was left used to launch a patch from a card that no longer exists.
    DisposableEffect(navigator, currentMethod, fileUris) {
        MainActivity.onShizukuPermissionResult = { grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                PatcherLauncher.launch(
                    navigator,
                    AppPatcher(
                        fileUris = fileUris,
                        install = true,
                        directInstall = false,
                        shizukuInstall = true,
                        legacyInstall = false,
                        mode = mode

                    )
                )
            }
        }
        onDispose { MainActivity.onShizukuPermissionResult = null }
    }

    // Handle DeepLink update
    val deepLinkMissingApks = stringResource(R.string.deep_link_missing_apks)
    val selectedFilesUnavailable = stringResource(R.string.selected_files_no_longer_available)
    val pendingUpdateDeepLink by mainViewModel.pendingUpdateDeepLink

    LaunchedEffect(pendingUpdateDeepLink, mode) {
        if (!pendingUpdateDeepLink) return@LaunchedEffect
        if (mode != PatchMode.TRACEN) return@LaunchedEffect
        mainViewModel.pendingUpdateDeepLink.value = false

        val needsFiles = currentMethod != PatcherInstallMethod.DIRECT
        if (needsFiles && fileUris.isEmpty()) {
            Toast.makeText(context, deepLinkMissingApks, Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }

        PatcherLauncher.launch(
            navigator,
            AppPatcher(
                fileUris = if (needsFiles) fileUris else arrayOf(),
                install = currentMethod == PatcherInstallMethod.NORMAL,
                directInstall = currentMethod == PatcherInstallMethod.DIRECT,
                shizukuInstall = currentMethod == PatcherInstallMethod.SHIZUKU,
                legacyInstall = currentMethod == PatcherInstallMethod.LEGACY,
                mode = mode

            )
        )
    }

    if (showShizukuRationaleDialog) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.shizuku_permission_required),
            onClose = { ok ->
                showShizukuRationaleDialog = false
                if (ok) {
                    Shizuku.requestPermission(MainActivity.SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            }
        ) {
            Text(stringResource(R.string.shizuku_permission_required))
        }
    }

    val uriHandler = LocalUriHandler.current
    if (showShizukuNotAvailableDialog) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.shizuku_unavailable),
            onClose = { ok ->
                showShizukuNotAvailableDialog = false
                if (ok) {
                    uriHandler.openUri("https://shizuku.rikka.app/download")
                }
            }
        ) {
            Text(stringResource(R.string.shizuku_unavailable_info))
        }
    }

    if (staleFilesError != null) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.patch),
            onClose = { staleFilesError = null }
        ) {
            Text(staleFilesError!!)
        }
    }

    // Install Method Modal Bottom Sheet
    if (showMethodSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showMethodSheet = false },
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
                    text = stringResource(R.string.select_install_method),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.select_install_method_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    methods.forEach { method ->
                        val isSelected = currentMethod == method
                        val isAvailable = when (method) {
                            PatcherInstallMethod.DIRECT -> isRootAvailable && isGameInstalled
                            PatcherInstallMethod.SHIZUKU -> isShizukuAvailable
                            else -> true
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isAvailable) {
                                    selectedMethodIndex.intValue = method.id
                                    if (method == PatcherInstallMethod.DIRECT) {
                                        targetSource = PatchTargetSource.INSTALLED
                                    }
                                    showMethodSheet = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = method.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(method.titleRes),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        val indicatorText = when (method) {
                                            PatcherInstallMethod.DIRECT -> if (isAvailable) stringResource(R.string.install_state_available) else stringResource(R.string.install_state_root_required)
                                            PatcherInstallMethod.SHIZUKU -> if (isAvailable) stringResource(R.string.install_state_available) else stringResource(R.string.install_state_unavailable)
                                            else -> stringResource(R.string.install_state_available)
                                        }
                                        val indicatorColor = if (isAvailable) {
                                            if (isSystemInDarkTheme()) brand_success_dark else brand_success_light
                                        } else MaterialTheme.colorScheme.error

                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = indicatorText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = indicatorColor
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(method.descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Title + Interactive Method Selector Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_apk_install),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.patch_studio),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.weight(1f))

                // Interactive Install Method Chip opens the bottom sheet
                AssistChip(
                    onClick = { showMethodSheet = true },
                    label = {
                        Text(
                            text = stringResource(currentMethod.titleRes),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = currentMethod.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                )
            }

            // Astraea's entry point is always the split set, so both the source switch and the
            // "installed game" state would only be dead UI.
            if (!isAstraea) SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = targetSource == PatchTargetSource.INSTALLED,
                    onClick = {
                        targetSource = PatchTargetSource.INSTALLED
                        if (isRootAvailable) {
                            selectedMethodIndex.intValue = PatcherInstallMethod.DIRECT.id
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = isGameInstalled,
                    icon = { SegmentedButtonDefaults.Icon(active = targetSource == PatchTargetSource.INSTALLED) }
                ) {
                    Text(stringResource(R.string.target_source_installed))
                }

                SegmentedButton(
                    selected = targetSource == PatchTargetSource.FILES,
                    onClick = {
                        targetSource = PatchTargetSource.FILES
                        if (currentMethod == PatcherInstallMethod.DIRECT) {
                            selectedMethodIndex.intValue = if (isShizukuAvailable) PatcherInstallMethod.SHIZUKU.id else PatcherInstallMethod.NORMAL.id
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { SegmentedButtonDefaults.Icon(active = targetSource == PatchTargetSource.FILES) }
                ) {
                    Text(stringResource(R.string.target_source_files))
                }
            }

            // Target Content Area
            if (targetSource == PatchTargetSource.INSTALLED && currentMethod == PatcherInstallMethod.DIRECT) {
                // Direct Install summary card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.direct_root_injection),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.direct_root_injection_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // File Selection Drop-Zone
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { fileSelectLauncher.launch(arrayOf("*/*")) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_file_open),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (fileUris.isEmpty()) stringResource(R.string.drop_files_title) else pluralStringResource(R.plurals.n_files_selected, fileUris.size, fileUris.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(
                                        if (isAstraea) R.string.astraea_drop_files_subtitle
                                        else R.string.drop_files_subtitle
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (fileUris.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        for (uri in fileUris) releasePersistableUriPermission(context, uri)
                                        fileUris = arrayOf()
                                        coroutineScope.launch { saveFileUris(context, mode, arrayOf()) }
                                    }
                                ) {
                                    Text(stringResource(R.string.clear_files), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Selected File Chips preview
                        if (fileUris.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                fileUris.forEach { uri ->
                                    val fileName = remember(uri) {
                                        DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment ?: "file.apk"
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Text(
                                            text = fileName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isAstraea) {
                // Priconne has no stock install to go back to: the shell the patch removes is
                // exactly what an uninstall would need. Say it here, where the decision is made.
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.astraea_patch_is_one_way),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // The tiles own the fix; this says the patch cannot start until they are done.
            if (isAstraea && !astraeaLibrariesReady) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(
                                R.string.astraea_libraries_incomplete,
                                libraries.libs.filterNot { it.present }
                                    .joinToString(", ") { it.name }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Primary Action Button
            val isButtonEnabled = when {
                isAstraea -> fileUris.isNotEmpty() && astraeaLibrariesReady
                targetSource == PatchTargetSource.INSTALLED && currentMethod == PatcherInstallMethod.DIRECT -> isGameInstalled
                else -> fileUris.isNotEmpty()
            }

            Button(
                enabled = isButtonEnabled,
                onClick = {
                    if (currentMethod != PatcherInstallMethod.DIRECT && fileUris.isNotEmpty() && stateLoaded) {
                        val (aliveUris, deadUris) = fileUris.partition { canOpenUri(context, it) }
                        if (deadUris.isNotEmpty()) {
                            for (uri in deadUris) releasePersistableUriPermission(context, uri)
                            fileUris = aliveUris.toTypedArray()
                            coroutineScope.launch { saveFileUris(context, mode, aliveUris.toTypedArray()) }
                            staleFilesError = selectedFilesUnavailable
                            return@Button
                        }
                    }

                    if (currentMethod == PatcherInstallMethod.SHIZUKU && !isShizukuAvailable) {
                        showShizukuNotAvailableDialog = true
                        return@Button
                    }

                    if (currentMethod == PatcherInstallMethod.SHIZUKU) {
                        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                            PatcherLauncher.launch(
                                navigator,
                                AppPatcher(
                                    fileUris = fileUris,
                                    install = true,
                                    directInstall = false,
                                    shizukuInstall = true,
                                    legacyInstall = false,
                                    mode = mode

                                )
                            )
                        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                            showShizukuRationaleDialog = true
                        } else {
                            Shizuku.requestPermission(MainActivity.SHIZUKU_PERMISSION_REQUEST_CODE)
                        }
                    } else {
                        PatcherLauncher.launch(
                            navigator,
                            AppPatcher(
                                fileUris = if (currentMethod == PatcherInstallMethod.DIRECT) arrayOf() else fileUris,
                                install = currentMethod == PatcherInstallMethod.NORMAL,
                                directInstall = currentMethod == PatcherInstallMethod.DIRECT,
                                shizukuInstall = false,
                                legacyInstall = currentMethod == PatcherInstallMethod.LEGACY,
                                mode = mode

                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_apk_install),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.start_patching),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun canOpenUri(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.read() } != null
    } catch (_: Exception) {
        false
    }
}

private fun tryTakePersistableUriPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
    }
}

private fun releasePersistableUriPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
    }
}

private suspend fun saveFileUris(context: Context, mode: PatchMode, fileUris: Array<Uri>) {
    context.dataStore.edit {
        it[PrefKey.fileUris(mode)] = fileUris.joinToString("\n") { uri -> uri.toString() }
    }
}

private suspend fun saveInstallMethod(context: Context, mode: PatchMode, installMethod: Int) {
    context.dataStore.edit {
        it[PrefKey.installMethod(mode)] = installMethod
    }
}

/**
 * The single studio slot predates the second game family, so anything in it can only have been
 * picked for Tracen. Reading it once for that mode, and then clearing it, keeps an upgrade from
 * losing the current selection while making sure Astraea never adopts a Umamusume split.
 */
private suspend fun takeLegacyFileUris(context: Context, mode: PatchMode): String {
    if (mode != PatchMode.TRACEN) return ""
    val legacy = context.getPrefValue(PrefKey.FILE_URIS) as String
    if (legacy.isEmpty()) return ""
    context.dataStore.edit { prefs ->
        prefs[PrefKey.fileUris(mode)] = legacy
        prefs[PrefKey.FILE_URIS] = ""
    }
    return legacy
}

private suspend fun takeLegacyInstallMethod(context: Context, mode: PatchMode): Int? {
    if (mode != PatchMode.TRACEN) return null
    val legacy = context.getPrefValue(PrefKey.INSTALL_METHOD) as Int
    context.dataStore.edit { prefs ->
        prefs[PrefKey.installMethod(mode)] = legacy
        prefs.remove(PrefKey.INSTALL_METHOD)
    }
    return legacy
}
