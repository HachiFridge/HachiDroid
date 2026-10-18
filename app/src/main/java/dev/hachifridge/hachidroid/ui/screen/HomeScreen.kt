package dev.hachifridge.hachidroid.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hachifridge.hachidroid.MainActivity
import dev.hachifridge.hachidroid.MainViewModel
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.patcher.HachimiUninstallPatcher
import dev.hachifridge.hachidroid.shizuku.ShizukuState
import dev.hachifridge.hachidroid.ui.component.BentoDashboardRow
import dev.hachifridge.hachidroid.ui.component.BottomBarScrollSpacer
import dev.hachifridge.hachidroid.ui.component.GameHeroCard
import dev.hachifridge.hachidroid.ui.component.SimpleOkCancelDialog
import dev.hachifridge.hachidroid.ui.component.TopBar
import dev.hachifridge.hachidroid.ui.component.rememberTopBarScrollBehavior
import dev.hachifridge.hachidroid.ui.navigation.Navigator
import dev.hachifridge.hachidroid.ui.patcher.PatcherLauncher
import dev.hachifridge.hachidroid.ui.patcher.PatchingStudioCard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hachifridge.hachidroid.ui.navigation.Destination
import dev.hachifridge.hachidroid.utils.safeNavigate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navigator: Navigator,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainViewModel: MainViewModel = viewModel(context as MainActivity)
    var showUninstallConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStatus(context)
    }

    val scrollBehavior = rememberTopBarScrollBehavior()

    if (showUninstallConfirm) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.uninstall_hachimi),
            onClose = { ok ->
                showUninstallConfirm = false
                if (ok) {
                    PatcherLauncher.launch(navigator, HachimiUninstallPatcher())
                }
            }
        ) {
            Text(stringResource(R.string.uninstall_confirm))
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            safeNavigate(lifecycleOwner) {
                                navigator.navigate(Destination.Settings)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                    IconButton(
                        onClick = {
                            safeNavigate(lifecycleOwner) {
                                navigator.navigate(Destination.About)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.about)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val packageInfo by viewModel.packageInfo.collectAsState()
            val plugins by viewModel.plugins.collectAsState()
            val libraries by viewModel.libraries.collectAsState()

            // 1. Dynamic Game Hero Canvas: Game identity, status telemetry, and launch/uninstall actions
            GameHeroCard(
                navigator = navigator,
                mode = viewModel.mode,
                onModeChange = { viewModel.setMode(context, it) },
                packageInfo = packageInfo,
                pluginCount = plugins.size,
                shizukuAvailable = ShizukuState.isAvailable.value,
                rootAvailable = mainViewModel.isRooted.value,
                rootInitialized = mainViewModel.rootInitialized.value,
                onUninstallRequest = { showUninstallConfirm = true }
            )

            // 2. Modern Patching Studio: segmented target source, smart method selector, and file drop-zone.
            // The mode decides what the card offers: Astraea patches a Priconne split set, so root
            // injection, the legacy installer and the installed-game target do not apply.
            PatchingStudioCard(
                navigator = navigator,
                mode = viewModel.mode,
                homeViewModel = viewModel,
                libraries = libraries
            )

            // 3. Bento Dashboard Row: Mod source and plugin tiles (Tracen) or mod source and library tiles (Astraea).
            BentoDashboardRow(viewModel = viewModel, mode = viewModel.mode, libraries = libraries)

            BottomBarScrollSpacer()
        }
    }
}
