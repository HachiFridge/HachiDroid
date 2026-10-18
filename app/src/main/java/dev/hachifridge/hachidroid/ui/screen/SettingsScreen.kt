package dev.hachifridge.hachidroid.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.ui.component.BooleanOption
import dev.hachifridge.hachidroid.ui.component.BottomBarScrollSpacer
import dev.hachifridge.hachidroid.ui.component.OptionBase
import dev.hachifridge.hachidroid.ui.component.OptionGroup
import dev.hachifridge.hachidroid.ui.component.StringOption
import dev.hachifridge.hachidroid.ui.component.TopBar
import dev.hachifridge.hachidroid.ui.component.rememberTopBarScrollBehavior
import dev.hachifridge.hachidroid.utils.ksFile
import dev.hachifridge.hachidroid.utils.showToast

import dev.hachifridge.hachidroid.ui.component.BackButton
import dev.hachifridge.hachidroid.ui.navigation.Navigator

@Composable
fun SettingsScreen(
    navigator: Navigator,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    val exportKsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportKeystore(context, uri)
    }

    val importKsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.importKeystore(context, uri)
    }

    LaunchedEffect(viewModel.hachimiLibsVersion) {
        if (!viewModel.configRead) return@LaunchedEffect
        viewModel.saveHachimiLibsVersion(context, viewModel.hachimiLibsVersion)
    }

    LaunchedEffect(Unit) {
        viewModel.loadConfig(context)
    }

    val scrollBehavior = rememberTopBarScrollBehavior()
    // Read strings during composition so the click handlers get configuration-aware values.
    val noKeystoreToExport = stringResource(R.string.no_keystore_to_export)

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.settings),
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!viewModel.configRead) return@Column

            OptionGroup(stringResource(R.string.settings_group_appearance)) {
                BooleanOption(
                    title = stringResource(R.string.follow_wallpaper_colors),
                    desc = stringResource(R.string.follow_wallpaper_colors_desc),
                    value = viewModel.dynamicColor,
                    onCheckedChange = { viewModel.updateDynamicColor(context, it) },
                    showDivider = false
                )
            }

            OptionGroup(stringResource(R.string.settings_group_updates)) {
                BooleanOption(
                    title = stringResource(R.string.check_for_updates),
                    desc = stringResource(R.string.check_for_updates_desc),
                    value = viewModel.checkForUpdates,
                    onCheckedChange = { viewModel.updateCheckForUpdates(context, it) }
                )
                StringOption(
                    title = stringResource(R.string.hachimi_repo),
                    value = viewModel.hachimiRepo,
                    placeholder = "Tenshou170/Hachimi-Edge",
                    onValueChange = { viewModel.updateHachimiRepo(context, it) }
                )
                // Astraea's mod is a different runtime from Hachimi, so it has its own source
                // rather than one field that would mean two things at once.
                StringOption(
                    title = stringResource(R.string.daringtact_repo),
                    value = viewModel.daringTactRepo,
                    placeholder = "HachiFridge/DaringTact",
                    onValueChange = { viewModel.updateDaringTactRepo(context, it) },
                    showDivider = false
                )
            }

            OptionGroup(stringResource(R.string.settings_group_patching)) {
                BooleanOption(
                    title = stringResource(R.string.merge_apks),
                    desc = stringResource(R.string.merge_apks_desc),
                    value = viewModel.mergeApks,
                    onCheckedChange = { viewModel.updateMergeApks(context, it) }
                )
                BooleanOption(
                    title = stringResource(R.string.export_internal_data_provider),
                    desc = stringResource(R.string.export_internal_data_provider_desc),
                    value = viewModel.exportInternalDataProvider,
                    onCheckedChange = { viewModel.updateExportInternalDataProvider(context, it) }
                )
                BooleanOption(
                    title = stringResource(R.string.use_internal_files_dir),
                    desc = stringResource(R.string.use_internal_files_dir_desc),
                    value = viewModel.useInternalFilesDir,
                    onCheckedChange = { viewModel.updateUseInternalFilesDir(context, it) },
                    showDivider = false
                )
            }

            OptionGroup(stringResource(R.string.settings_group_signing)) {
                OptionBase(
                    title = stringResource(R.string.export_signing_key),
                    desc = stringResource(R.string.export_signing_key_desc),
                    onClick = {
                        if (context.ksFile.exists()) {
                            exportKsLauncher.launch("HachiDroid-Key.bks")
                        } else {
                            context.showToast(
                                noKeystoreToExport,
                                Toast.LENGTH_SHORT
                            )
                        }
                    }
                ) {}
                OptionBase(
                    title = stringResource(R.string.import_signing_key),
                    desc = stringResource(R.string.import_signing_key_desc),
                    showDivider = false,
                    onClick = {
                        importKsLauncher.launch(arrayOf("*/*"))
                    }
                ) {}
            }

            BottomBarScrollSpacer()
        }
    }
}
