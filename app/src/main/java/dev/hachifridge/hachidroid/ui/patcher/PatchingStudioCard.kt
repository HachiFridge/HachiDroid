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
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.rotate
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
import dev.hachifridge.hachidroid.client.Client
import dev.hachifridge.hachidroid.client.ClientProfile
import dev.hachifridge.hachidroid.core.PrefKey
import dev.hachifridge.hachidroid.client.LibrarySet
import dev.hachifridge.hachidroid.core.dataStore
import dev.hachifridge.hachidroid.core.getPrefValue
import dev.hachifridge.hachidroid.patcher.ClientPatcher
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
         * Shell-protected clients patch the split set rather than an installed game, so root
         * injection and the legacy mount installer have nothing to operate on.
         */
        val SPLIT_METHODS = listOf(SAVE, NORMAL, SHIZUKU)
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
    client: Client,
    homeViewModel: HomeViewModel,
    profile: ClientProfile = homeViewModel.profile,
    libraries: LibrarySet.Status = profile.libraries.emptyStatus(
        shellProtected = profile.shellProtected || homeViewModel.patchProtected
    )
) {
    // Protection belongs to the build/profile: Tracen covers both Uma releases and
    // only the packed one needs the split-set flow.
    val shellProtected = profile.shellProtected || homeViewModel.patchProtected
    // A packed build has to have its restored game files before anything is written. The mod
    // runtime is deliberately not part of this: the patcher syncs it itself and gates on it after
    // the download, so a missing runtime must not leave the button disabled.
    val gameFilesReady = libraries.gameLibrariesReady
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

    // A packed install cannot be patched in place: its shell is only removed by rebuilding and
    // reinstalling the split set.
    val canPatchInstalled = isGameInstalled && !homeViewModel.installedTargetProtected

    // Target source: Installed Game vs External APK Files.
    var targetSource by rememberSaveable(profile) {
        mutableStateOf(
            if (!shellProtected && canPatchInstalled && isRootAvailable) PatchTargetSource.INSTALLED
            else PatchTargetSource.FILES
        )
    }

    // Detection lands a moment after the picks are read, and a packed build cannot be patched in
    // place, so the studio moves itself back to the split-set source when that happens.
    LaunchedEffect(shellProtected, canPatchInstalled) {
        if (shellProtected || !canPatchInstalled) targetSource = PatchTargetSource.FILES
    }

    val methods = if (shellProtected) PatcherInstallMethod.SPLIT_METHODS else PatcherInstallMethod.entries

    val selectedMethodIndex = rememberSaveable(profile) { mutableIntStateOf(PatcherInstallMethod.NORMAL.id) }
    val currentMethod = if (PatcherInstallMethod.fromId(selectedMethodIndex.intValue) in methods) {
        PatcherInstallMethod.fromId(selectedMethodIndex.intValue)
    } else {
        PatcherInstallMethod.NORMAL
    }

    // Keyed on the profile: preserves picks per server client profile (e.g. 1 GLB and 1 JP).
    var fileUris by rememberSaveable(profile) { mutableStateOf<Array<Uri>>(arrayOf()) }

    var stateLoaded by remember(profile) { mutableStateOf(false) }
    var installMethodLoaded by rememberSaveable(profile) { mutableStateOf(false) }

    val fileSelectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val newFileUris = uris.toTypedArray()
        for (uri in fileUris) releasePersistableUriPermission(context, uri)
        for (uri in newFileUris) tryTakePersistableUriPermission(context, uri)
        fileUris = newFileUris
        coroutineScope.launch { saveFileUris(context, profile, newFileUris) }
    }

    // Libraries are part of the studio now, so its section owns the picker as well.
    val libraryImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) homeViewModel.importLibraries(context, uris)
    }

    // Restore this profile's persisted state.
    LaunchedEffect(profile) {
        val savedInstallMethod = takeLegacyInstallMethod(context, profile)
        selectedMethodIndex.intValue = savedInstallMethod
            ?.takeIf { PatcherInstallMethod.fromId(it) in methods }
            ?: PatcherInstallMethod.NORMAL.id
        installMethodLoaded = true

        val storedFileUris = takeLegacyFileUris(context, profile)
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
            saveFileUris(context, profile, existingFileUris.toTypedArray())
        }
        fileUris = existingFileUris.toTypedArray()
        stateLoaded = true
    }

    // The studio's library section unpacks from these picks, and the build they belong to decides
    // which libraries are required, so the view model is kept in step here.
    LaunchedEffect(fileUris, profile) {
        homeViewModel.updatePatchSplits(context, fileUris.toList())
    }

    // Persist install method for this profile
    LaunchedEffect(installMethodLoaded, profile) {
        if (!installMethodLoaded) return@LaunchedEffect
        snapshotFlow { selectedMethodIndex.intValue }
            .drop(1)
            .collectLatest { methodId ->
                saveInstallMethod(context, profile, methodId)
            }
    }

    // Shizuku permission listener. Disposable: Shizuku's dialog outlives this screen, and a grant
    // arriving after it was left used to launch a patch from a card that no longer exists.
    DisposableEffect(navigator, currentMethod, fileUris, profile) {
        MainActivity.onShizukuPermissionResult = { grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                PatcherLauncher.launch(
                    navigator,
                    ClientPatcher(
                        fileUris = fileUris,
                        install = true,
                        directInstall = false,
                        shizukuInstall = true,
                        legacyInstall = false,
                        client = profile.client,
                        profile = profile
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

    LaunchedEffect(pendingUpdateDeepLink, profile) {
        if (!pendingUpdateDeepLink) return@LaunchedEffect
        mainViewModel.pendingUpdateDeepLink.value = false

        // The saved method belongs to the build shape that was selected when it was chosen; a
        // deep link can arrive after the picks turned out packed, so the sheet's filter applies
        // here too (the patcher refuses the mismatched methods as a last line of defence).
        val method = if (currentMethod in methods) currentMethod else PatcherInstallMethod.NORMAL

        val needsFiles = method != PatcherInstallMethod.DIRECT
        if (needsFiles && fileUris.isEmpty()) {
            Toast.makeText(context, deepLinkMissingApks, Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }

        PatcherLauncher.launch(
            navigator,
            ClientPatcher(
                fileUris = if (needsFiles) fileUris else arrayOf(),
                install = method == PatcherInstallMethod.NORMAL,
                directInstall = method == PatcherInstallMethod.DIRECT,
                shizukuInstall = method == PatcherInstallMethod.SHIZUKU,
                legacyInstall = method == PatcherInstallMethod.LEGACY,
                client = profile.client,
                profile = profile
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
                            PatcherInstallMethod.DIRECT -> isRootAvailable && canPatchInstalled
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

            // A shell-protected client's entry point is always the split set, so both the source
            // switch and the "installed game" state would only be dead UI.
            if (!shellProtected) SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = targetSource == PatchTargetSource.INSTALLED,
                    onClick = {
                        targetSource = PatchTargetSource.INSTALLED
                        if (isRootAvailable) {
                            selectedMethodIndex.intValue = PatcherInstallMethod.DIRECT.id
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = canPatchInstalled,
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
                                        if (shellProtected) R.string.shell_drop_files_subtitle
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
                                        coroutineScope.launch { saveFileUris(context, profile, arrayOf()) }
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

            if (shellProtected) {
                // A shell-protected install has no stock copy to go back to: the shell the patch removes is
                // exactly what an uninstall would need. Say it here, where the decision is made.
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.shell_patch_is_one_way),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // Native libraries gate the patch itself, so they live here rather than in a tile.
            LibraryGateSection(
                libraries = libraries,
                shellProtected = shellProtected,
                splitsAvailable = homeViewModel.patchSplits.isNotEmpty(),
                onDeviceUnpackAvailable = homeViewModel.onDeviceUnpackAvailable,
                unpacking = homeViewModel.unpackingLibraries,
                unpackError = homeViewModel.unpackError,
                onUnpack = {
                    homeViewModel.unpackLibrariesFromSplits(context, homeViewModel.patchSplits)
                },
                onImport = { libraryImportLauncher.launch(arrayOf("*/*")) },
                onRemove = { name -> homeViewModel.removeLibrary(context, name) }
            )

            // Primary Action Button
            val isButtonEnabled = when {
                shellProtected -> fileUris.isNotEmpty() && gameFilesReady
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
                            coroutineScope.launch { saveFileUris(context, profile, aliveUris.toTypedArray()) }
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
                                ClientPatcher(
                                    fileUris = fileUris,
                                    install = true,
                                    directInstall = false,
                                    shizukuInstall = true,
                                    legacyInstall = false,
                                    client = profile.client,
                                    profile = profile
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
                            ClientPatcher(
                                fileUris = if (currentMethod == PatcherInstallMethod.DIRECT) arrayOf() else fileUris,
                                install = currentMethod == PatcherInstallMethod.NORMAL,
                                directInstall = currentMethod == PatcherInstallMethod.DIRECT,
                                shizukuInstall = false,
                                legacyInstall = currentMethod == PatcherInstallMethod.LEGACY,
                                client = profile.client,
                                profile = profile
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

/**
 * The native libraries a patch writes, with their readiness, inside the studio rather than in a
 * separate tile: this is the last thing between the user and Start Patcher.
 *
 * Collapsed to a summary once everything is present, open by default while something is missing.
 */
@Composable
private fun LibraryGateSection(
    libraries: LibrarySet.Status,
    shellProtected: Boolean,
    splitsAvailable: Boolean,
    onDeviceUnpackAvailable: Boolean,
    unpacking: Boolean,
    unpackError: String?,
    onUnpack: () -> Unit,
    onImport: () -> Unit,
    onRemove: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(!libraries.isReady) }
    val ready = libraries.isReady
    // A ready summary would hide the cases that need attention, so the section opens itself.
    LaunchedEffect(libraries.artifactsStale, libraries.hasMisaligned) {
        if (libraries.artifactsStale || libraries.hasMisaligned) expanded = true
    }
    val accent = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val gameLibrariesReady = libraries.gameLibrariesReady

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.libraries_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (ready) {
                            stringResource(R.string.libraries_ready_count, libraries.presentCount, libraries.total)
                        } else {
                            stringResource(
                                R.string.shell_libraries_incomplete,
                                libraries.libs.filterNot { it.present }
                                    .joinToString(", ") { it.name }
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (ready) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "${libraries.presentCount}/${libraries.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ready) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 180f else 0f)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    libraries.libs.forEach { lib ->
                        StudioLibraryRow(lib, onRemove = { onRemove(lib.name) })
                    }

                    if (libraries.artifactsStale) {
                        Text(
                            text = stringResource(
                                R.string.libraries_stale_warning,
                                libraries.artifactsStamp.orEmpty(),
                                libraries.targetStamp.orEmpty()
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (libraries.hasMisaligned) {
                        Text(
                            text = stringResource(R.string.libraries_misaligned_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (unpacking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = stringResource(R.string.libraries_unpacking),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Only a packed build has anything to restore; a plain one already ships its
                    // libraries and offers import for the runtime alone.
                    if (shellProtected) {
                        if (onDeviceUnpackAvailable) {
                            FilledTonalButton(
                                onClick = onUnpack,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !gameLibrariesReady && splitsAvailable && !unpacking
                            ) {
                                Text(
                                    stringResource(
                                        if (gameLibrariesReady) R.string.libraries_unpack_cached
                                        else R.string.libraries_unpack_action
                                    )
                                )
                            }
                            Text(
                                text = stringResource(
                                    when {
                                        gameLibrariesReady -> R.string.libraries_unpack_cached_hint
                                        !splitsAvailable -> R.string.libraries_unpack_need_files
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

                    FilledTonalButton(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.libraries_import_action))
                    }

                    if (shellProtected) {
                        Text(
                            text = stringResource(R.string.libraries_import_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Stays until the next attempt: a toast is gone before it can be read.
                    unpackError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/** One library in the checklist, with its size and a way to drop a stale copy. */
@Composable
private fun StudioLibraryRow(lib: LibrarySet.LibStatus, onRemove: () -> Unit) {
    val context = LocalContext.current
    val okColor = MaterialTheme.colorScheme.primary
    val badColor = MaterialTheme.colorScheme.error

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (lib.present) Icons.Outlined.Check else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (lib.present) okColor else badColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lib.name,
                    style = MaterialTheme.typography.labelLarge,
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
                        modifier = Modifier.size(16.dp)
                    )
                }
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

private suspend fun saveFileUris(context: Context, profile: ClientProfile, fileUris: Array<Uri>) {
    context.dataStore.edit {
        it[PrefKey.fileUris(profile)] = fileUris.joinToString("\n") { uri -> uri.toString() }
        it[PrefKey.fileUris(profile.client)] = fileUris.joinToString("\n") { uri -> uri.toString() }
    }
}

private suspend fun saveInstallMethod(context: Context, profile: ClientProfile, installMethod: Int) {
    context.dataStore.edit {
        it[PrefKey.installMethod(profile)] = installMethod
        it[PrefKey.installMethod(profile.client)] = installMethod
    }
}

private suspend fun takeLegacyFileUris(context: Context, profile: ClientProfile): String {
    val profileKeyVal = (context.getPrefValue(PrefKey.fileUris(profile)) as? String)?.ifEmpty { null }
    if (profileKeyVal != null) return profileKeyVal
    val clientKeyVal = (context.getPrefValue(PrefKey.fileUris(profile.client)) as? String)?.ifEmpty { null }
    if (clientKeyVal != null) return clientKeyVal
    return takeLegacyFileUris(context, profile.client)
}

private suspend fun takeLegacyInstallMethod(context: Context, profile: ClientProfile): Int? {
    val profileMethod = (context.getPrefValue(PrefKey.installMethod(profile)) as? Int)
    if (profileMethod != null) return profileMethod
    val clientMethod = (context.getPrefValue(PrefKey.installMethod(profile.client)) as? Int)
    if (clientMethod != null) return clientMethod
    return takeLegacyInstallMethod(context, profile.client)
}

/**
 * The single studio slot predates the second game family, so anything in it can only have been
 * picked for Tracen. Reading it once for that client, and then clearing it, keeps an upgrade from
 * losing the current selection while making sure Astraea never adopts a Umamusume split.
 */
private suspend fun takeLegacyFileUris(context: Context, client: Client): String {
    if (client != Client.TRACEN) return ""
    val legacy = context.getPrefValue(PrefKey.FILE_URIS) as String
    if (legacy.isEmpty()) return ""
    context.dataStore.edit { prefs ->
        prefs[PrefKey.fileUris(client)] = legacy
        prefs[PrefKey.FILE_URIS] = ""
    }
    return legacy
}

private suspend fun takeLegacyInstallMethod(context: Context, client: Client): Int? {
    if (client != Client.TRACEN) return null
    val legacy = context.getPrefValue(PrefKey.INSTALL_METHOD) as Int
    context.dataStore.edit { prefs ->
        prefs[PrefKey.installMethod(client)] = legacy
        prefs.remove(PrefKey.INSTALL_METHOD)
    }
    return legacy
}
