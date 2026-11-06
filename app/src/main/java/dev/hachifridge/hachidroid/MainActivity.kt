package dev.hachifridge.hachidroid

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.map
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.hachifridge.hachidroid.core.PatchMode
import dev.hachifridge.hachidroid.core.PrefKey
import dev.hachifridge.hachidroid.core.dataStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import dev.hachifridge.hachidroid.patcher.LegacyInstaller
import dev.hachifridge.hachidroid.ui.component.ChangelogFormatter
import dev.hachifridge.hachidroid.ui.component.SimpleOkCancelDialog
import dev.hachifridge.hachidroid.ui.navigation.Destination
import dev.hachifridge.hachidroid.ui.navigation.Navigator
import dev.hachifridge.hachidroid.ui.screen.AboutScreen
import dev.hachifridge.hachidroid.ui.screen.GameManagementScreen
import dev.hachifridge.hachidroid.ui.screen.HomeScreen
import dev.hachifridge.hachidroid.ui.screen.OpenSourceLicensesScreen
import dev.hachifridge.hachidroid.ui.screen.PatchingScreen
import dev.hachifridge.hachidroid.ui.screen.SettingsScreen
import dev.hachifridge.hachidroid.ui.theme.HachiDroidTheme
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 9975
        var onShizukuPermissionResult: ((grantResult: Int) -> Unit)? = null
        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
            )
        }
    }

    /** Root ViewModel, so an inbound deep link can be delivered after the first composition. */
    private val mainViewModel: MainViewModel by viewModels()

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                onShizukuPermissionResult?.invoke(grantResult)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        checkDeepLink(intent)

        setContent {
            LaunchedEffect(Unit) {
                mainViewModel.init(this@MainActivity)
            }

            // Dynamic color is opt-in via Settings; the flow is hoisted to dynamicColorFlow()
            // so no flow operator runs inside composition.
            val dynamicColor by remember { dynamicColorFlow(dataStore) }
                .collectAsState(initial = false)

            // The mode drives the palette as well as the dashboard, so it is read at the root:
            // switching it in the hero card re-themes the whole app, not just the home screen.
            val savedMode by remember { patchModeFlow(dataStore) }
                .collectAsState(initial = PatchMode.TRACEN.prefValue)

            HachiDroidTheme(dynamicColor = dynamicColor, mode = PatchMode.fromPref(savedMode)) {
                MainContent(mainViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkDeepLink(intent)
    }

    private fun checkDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        // Hachimi-Edge opens hachidroid://update-hachimi; the UmaPatcher-Edge scheme stays as fallback.
        val isUpdateScheme = uri.scheme == "hachidroid" || uri.scheme == "umapatcher-edge"
        if (!isUpdateScheme || uri.host != "update-hachimi") return

        // Deliver straight to the ViewModel: on a cold start this lands before init(), which is
        // fine because Home consumes the flag when it composes. A warm onNewIntent() previously
        // wrote to a companion flag nothing ever read again, so the prompt never appeared.
        mainViewModel.pendingUpdateDeepLink.value = true
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        onShizukuPermissionResult = null
    }

    suspend fun useKeepScreenOn(callback: suspend () -> Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        try {
            callback()
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

/** Built outside composition so flow operators never run during recomposition. */
private fun dynamicColorFlow(dataStore: DataStore<Preferences>): Flow<Boolean> =
    dataStore.data.map { prefs -> prefs[PrefKey.DYNAMIC_COLOR] ?: false }

/** Raw preference value; parsing to [PatchMode] stays at the call site. */
private fun patchModeFlow(dataStore: DataStore<Preferences>): Flow<String> =
    dataStore.data.map { prefs -> prefs[PrefKey.PATCH_MODE] ?: PatchMode.TRACEN.prefValue }

@Composable
private fun MainContent(viewModel: MainViewModel = viewModel()) {
    val backStack = rememberNavBackStack(Destination.Home)
    val navigator = remember { Navigator(backStack) }
    val context = LocalContext.current

    if (viewModel.rootInitialized.value) {
        NavDisplay(
            backStack = backStack,
            // Every entry owns its ViewModelStore, cleared when the entry is popped, so screen
            // state cannot leak from one visit or patch run into the next. The saveable-state
            // holder is Nav3's default decorator and has to stay first.
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            onBack = { navigator.popBackStack() },
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(250)),
                    initialContentExit = slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        targetOffset = { it / 4 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(200))
                )
            },
            popTransitionSpec = {
                ContentTransform(
                    targetContentEnter = slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        initialOffset = { -it / 4 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(250)),
                    initialContentExit = slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(200))
                )
            },
            predictivePopTransitionSpec = { _ ->
                ContentTransform(
                    targetContentEnter = slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        initialOffset = { -it / 4 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(250)),
                    initialContentExit = slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(200))
                )
            },
            entryProvider = entryProvider {
                entry<Destination.Home> { HomeScreen(navigator) }
                entry<Destination.Settings> { SettingsScreen(navigator) }
                entry<Destination.About> { AboutScreen(navigator) }
                entry<Destination.GameManagement> { GameManagementScreen(navigator) }
                entry<Destination.Patching> { PatchingScreen(navigator) }
                entry<Destination.OpenSourceLicenses> { OpenSourceLicensesScreen(navigator) }
            }
        )
    }
        // After a verified download: install through the unknown-sources gate on first use.
        var showUpdatePermDialog by remember { mutableStateOf(false) }
        val updatePermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (LegacyInstaller.canRequestPackageInstalls(context)) {
                viewModel.installUpdate(context)
            } else {
                viewModel.clearUpdateReady()
            }
        }
        LaunchedEffect(viewModel.updateReadyFile.value) {
            if (viewModel.updateReadyFile.value == null) return@LaunchedEffect
            if (LegacyInstaller.canRequestPackageInstalls(context)) {
                viewModel.installUpdate(context)
            } else {
                showUpdatePermDialog = true
            }
        }
        if (showUpdatePermDialog) {
            SimpleOkCancelDialog(
                title = stringResource(R.string.legacy_install_unknown_sources_required),
                onClose = { ok ->
                    showUpdatePermDialog = false
                    if (ok) {
                        val permIntent = LegacyInstaller.pickUnknownSourcesIntent(context)
                        if (permIntent != null) {
                            try {
                                updatePermLauncher.launch(permIntent)
                            } catch (_: Exception) {
                                viewModel.clearUpdateReady()
                            }
                        } else {
                            viewModel.clearUpdateReady()
                        }
                    } else {
                        viewModel.clearUpdateReady()
                    }
                }
            ) {
                Text(stringResource(R.string.legacy_install_unknown_sources_info))
            }
        }
        if (viewModel.openUpdateDialog.value) {
            when (viewModel.updatePhase.value) {
                UpdatePhase.DOWNLOADING -> AlertDialog(
                    // In-flight downloads are only closable via the Cancel button.
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.update_downloading_title)) },
                    text = {
                        Column {
                            val progress = viewModel.updateProgress.floatValue
                            if (progress < 0f) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = viewModel.updateProgressLabel.value,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.cancelUpdateDownload() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
                UpdatePhase.FAILED -> AlertDialog(
                    onDismissRequest = { viewModel.closeUpdateDialog() },
                    title = { Text(stringResource(R.string.update_failed_title)) },
                    text = { Text(viewModel.updateErrorText.value) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.startUpdateDownload(context) }) {
                            Text(stringResource(R.string.update_retry))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.closeUpdateDialog() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
                else -> SimpleOkCancelDialog(
                    title = stringResource(R.string.update_available),
                    confirmText = stringResource(R.string.update_download),
                    onClose = { ok ->
                        if (ok) viewModel.startUpdateDownload(context)
                        else viewModel.closeUpdateDialog()
                    }
                ) {
                    Text(
                        stringResource(R.string.update_available_desc).format(
                            viewModel.updateTagName.value,
                            BuildConfig.VERSION_NAME
                        )
                    )
                    viewModel.updateChangelog.value?.let { rawChangelog ->
                        val formatted = remember(rawChangelog) {
                            ChangelogFormatter.format(rawChangelog)
                        }
                        if (formatted.text.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.update_changelog_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                            ) {
                                val scrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .verticalScroll(scrollState)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = formatted,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (viewModel.openMigrationDialog.value) {
            MigrationDialog(viewModel)
        }
        viewModel.migrationMessage.value?.let { message ->
            LaunchedEffect(message) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                viewModel.migrationMessage.value = null
            }
        }
    }

@Composable
private fun MigrationDialog(viewModel: MainViewModel) {
    val context = LocalContext.current
    val running = viewModel.migrationRunning.value
    val noRoot = !viewModel.isRooted.value

    val importKeyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importMigrationKeystore(context, uri)
        }
    }

    AlertDialog(
        // Back/outside tap = "ask again next launch"; only "Not now" dismisses permanently.
        onDismissRequest = { if (!running) viewModel.openMigrationDialog.value = false },
        title = { Text(stringResource(R.string.migration_title)) },
        text = {
            Text(
                stringResource(
                    if (noRoot) R.string.migration_no_root_desc else R.string.migration_desc
                )
            )
        },
        confirmButton = {
            if (!noRoot) {
                TextButton(
                    enabled = !running,
                    onClick = { viewModel.startMigration(context) }
                ) {
                    Text(stringResource(if (running) R.string.migration_running else R.string.migration_migrate))
                }
            } else {
                TextButton(
                    enabled = !running,
                    onClick = {
                        importKeyLauncher.launch(arrayOf("*/*"))
                    }
                ) {
                    Text(stringResource(if (running) R.string.migration_running else R.string.import_signing_key))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !running,
                onClick = { viewModel.dismissMigration(context) }
            ) {
                Text(stringResource(R.string.migration_not_now))
            }
        }
    )
}
