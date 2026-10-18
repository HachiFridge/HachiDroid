package dev.hachifridge.hachidroid.ui.screen

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hachifridge.hachidroid.MainActivity
import dev.hachifridge.hachidroid.MainViewModel
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.core.PatchMode
import dev.hachifridge.hachidroid.patcher.HachimiUninstallPatcher
import dev.hachifridge.hachidroid.ui.component.BackButton
import dev.hachifridge.hachidroid.ui.component.BottomBarScrollSpacer
import dev.hachifridge.hachidroid.ui.component.SimpleOkCancelDialog
import dev.hachifridge.hachidroid.ui.component.TopBar
import dev.hachifridge.hachidroid.ui.component.rememberTopBarScrollBehavior
import dev.hachifridge.hachidroid.ui.navigation.Navigator
import dev.hachifridge.hachidroid.ui.patcher.PatcherLauncher
import dev.hachifridge.hachidroid.ui.theme.brand_success_dark
import dev.hachifridge.hachidroid.ui.theme.brand_success_light
import dev.hachifridge.hachidroid.utils.safeNavigate

/**
 * Picks which installed client HachiDroid targets.
 *
 * Everything here is named for the active mode: the two families have different clients, different
 * engines and different uninstall rules, so an Umamusume-shaped screen would be lying to a Priconne
 * user about what it is showing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameManagementScreen(
    navigator: Navigator,
    viewModel: GameManagementViewModel = viewModel()
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainViewModel: MainViewModel = viewModel(context as MainActivity)
    val isRootAvailable = mainViewModel.isRooted.value
    val isAstraea = GameChecker.mode == PatchMode.ASTRAEA

    var uninstallTargetPackage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
    }

    val scrollBehavior = rememberTopBarScrollBehavior()

    if (uninstallTargetPackage != null) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.uninstall_hachimi),
            onClose = { ok ->
                val target = uninstallTargetPackage
                uninstallTargetPackage = null
                if (ok && target != null) {
                    GameChecker.currentPackageName = target
                    PatcherLauncher.launch(navigator, HachimiUninstallPatcher())
                }
            }
        ) {
            Text(stringResource(R.string.uninstall_confirm))
        }
    }

    // The game, its region list and its mod engine all come from the mode the user is in.
    val gameName = stringResource(
        if (isAstraea) R.string.game_family_astraea else R.string.game_family_tracen
    )
    val regions = stringResource(
        if (isAstraea) R.string.game_regions_astraea else R.string.game_regions_tracen
    )
    val engineName = stringResource(
        if (isAstraea) R.string.mod_engine_daringtact else R.string.mod_engine_hachimi
    )

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.game_management_title),
                navigationIcon = { BackButton(navigator) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        val clients by viewModel.clients.collectAsState()

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ManagementSummaryTile(gameName = gameName, count = clients.size, isAstraea = isAstraea)

            if (clients.isEmpty()) {
                NoClientsTile(gameName = gameName, regions = regions, isAstraea = isAstraea)
            } else {
                clients.forEach { client ->
                    GameClientManagementCard(
                        client = client,
                        engineName = engineName,
                        isRootAvailable = isRootAvailable,
                        onSelectTarget = {
                            viewModel.selectTarget(client.packageName, context)
                            safeNavigate(lifecycleOwner) {
                                navigator.popBackStack()
                            }
                        },
                        onLaunch = {
                            val launchIntent = pm.getLaunchIntentForPackage(client.packageName)?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        },
                        onUninstall = {
                            uninstallTargetPackage = client.packageName
                        }
                    )
                }
            }

            BottomBarScrollSpacer()
        }
    }
}

/** What this screen does, in the shape the dashboard uses for its tiles. */
@Composable
private fun ManagementSummaryTile(gameName: String, count: Int, isAstraea: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    if (isAstraea) R.drawable.ic_astraea_mascot else R.drawable.ic_tracen_mascot
                ),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                // Coloured artwork: tinting it would flatten it into a single stroke.
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.game_management_clients_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.multi_client_desc, gameName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun NoClientsTile(gameName: String, regions: String, isAstraea: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (isAstraea) R.drawable.ic_astraea_mascot else R.drawable.ic_tracen_mascot
                ),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Color.Unspecified
            )
            Text(
                text = stringResource(R.string.no_games_detected, gameName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.no_games_detected_desc, gameName, regions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GameClientManagementCard(
    client: ManagedGameClient,
    engineName: String,
    isRootAvailable: Boolean,
    onSelectTarget: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val successColor = if (isSystemInDarkTheme()) brand_success_dark else brand_success_light

    val iconBitmap = remember(client.packageInfo) {
        try {
            client.packageInfo.applicationInfo?.loadIcon(pm)?.toBitmap()?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (client.isCurrentTarget) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            if (client.isCurrentTarget) 2.dp else 1.dp,
            if (client.isCurrentTarget) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = client.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        RegionPill(label = client.regionLabel, tag = client.regionTag)
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "${client.packageName} • v${client.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatePill(
                    text = if (client.isPatched) {
                        stringResource(R.string.status_patched, engineName)
                    } else {
                        stringResource(R.string.status_unpatched)
                    },
                    tint = if (client.isPatched) successColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    filled = client.isPatched
                )
                if (client.isCurrentTarget) {
                    StatePill(
                        text = stringResource(R.string.active_target_badge),
                        tint = MaterialTheme.colorScheme.primary,
                        filled = true
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!client.isCurrentTarget) {
                    FilledTonalButton(
                        onClick = onSelectTarget,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.set_as_active))
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.target_selected),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Button(
                    onClick = onLaunch,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.launch_game))
                }

                // Astraea has nothing to uninstall back to: the patched install replaced the CrackProof
                // shell, so there is nothing to put back.
                if (isRootAvailable && client.isPatched && GameChecker.mode != PatchMode.ASTRAEA) {
                    OutlinedButton(
                        onClick = onUninstall,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_uninstall),
                            contentDescription = stringResource(R.string.uninstall_hachimi),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Region of the client, e.g. Japan (JP). */
@Composable
private fun RegionPill(label: String, tag: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = "$label ($tag)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * One fact about the client: the engine it is patched with, or that it is the current target.
 * A filled pill marks the state the row is in; the outline version only reports it.
 */
@Composable
private fun StatePill(text: String, tint: Color, filled: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (filled) tint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, if (filled) tint.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(tint, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = tint
            )
        }
    }
}
