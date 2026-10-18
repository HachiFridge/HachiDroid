package dev.hachifridge.hachidroid.ui.component

import android.content.Intent
import android.content.pm.PackageInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hachifridge.hachidroid.BuildConfig
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.core.PatchMode
import dev.hachifridge.hachidroid.patcher.HachimiUninstallPatcher
import dev.hachifridge.hachidroid.ui.navigation.Destination
import dev.hachifridge.hachidroid.ui.navigation.Navigator
import dev.hachifridge.hachidroid.ui.patcher.PatcherLauncher
import dev.hachifridge.hachidroid.ui.theme.brand_success_dark
import dev.hachifridge.hachidroid.ui.theme.brand_success_light
import dev.hachifridge.hachidroid.utils.safeNavigate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameHeroCard(
    navigator: Navigator,
    mode: PatchMode,
    onModeChange: (PatchMode) -> Unit,
    packageInfo: PackageInfo?,
    pluginCount: Int,
    shizukuAvailable: Boolean,
    rootAvailable: Boolean,
    rootInitialized: Boolean,
    onUninstallRequest: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val lifecycleOwner = LocalLifecycleOwner.current
    val successColor = if (isSystemInDarkTheme()) brand_success_dark else brand_success_light
    val errorColor = MaterialTheme.colorScheme.error

    // The installed game's own label wins; the fallback follows the mode so a mode with the game
    // missing does not announce the other game's name.
    val fallbackLabel = if (mode == PatchMode.ASTRAEA) "Princess Connect! Re:Dive" else "Umamusume: Pretty Derby"
    val appLabel = remember(packageInfo, fallbackLabel) {
        if (packageInfo != null) {
            try {
                pm.getApplicationLabel(packageInfo.applicationInfo!!).toString()
            } catch (_: Exception) {
                fallbackLabel
            }
        } else {
            fallbackLabel
        }
    }

    val appIcon = remember(packageInfo) {
        if (packageInfo?.applicationInfo != null) {
            try {
                packageInfo.applicationInfo!!.loadIcon(pm).toBitmap().asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val regionResId = remember(packageInfo) {
        val pkg = packageInfo?.packageName ?: GameChecker.currentPackageName ?: ""
        when (pkg) {
            "jp.co.cygames.umamusume" -> R.string.client_jp
            "com.komoe.kmumamusumegp" -> R.string.client_tw
            "com.komoe.umamusumeofficial" -> R.string.client_glb
            "com.kakaogames.umamusume" -> R.string.client_kr
            "jp.co.cygames.princessconnectredive" -> R.string.client_jp
            else -> R.string.target_client
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Logo + App Name + Version & System Capability Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))

                // Mode switches the whole dashboard identity, so it belongs in the card header.
                ModeSelectorChip(mode = mode, onModeChange = onModeChange)
            }

            Spacer(Modifier.height(16.dp))

            // Game Hero Canvas: Displays the target client, game icon, and real-time status
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        safeNavigate(lifecycleOwner) {
                            navigator.navigate(Destination.GameManagement)
                        }
                    },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))

                        if (packageInfo != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(successColor, CircleShape)
                                )
                                Text(
                                    text = "v${packageInfo.versionName ?: "Unknown"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = stringResource(R.string.status_ready_patched),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = successColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = packageInfo.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape)
                                )
                                Text(
                                    text = stringResource(R.string.game_not_installed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.tap_to_select_app),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Target client lives with the game identity rather than in the app header.
                    AssistChip(
                        onClick = {
                            safeNavigate(lifecycleOwner) {
                                navigator.navigate(Destination.GameManagement)
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(regionResId),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = stringResource(R.string.switch_client),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }

            // Quick launch and uninstall actions if the game is installed
            if (packageInfo != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val targetPackage = packageInfo.packageName
                            val launchIntent = pm.getLaunchIntentForPackage(targetPackage)?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        },
                        // Tonal default (secondaryContainer) sinks into the card and reads
                        // as a disabled button; primaryContainer keeps it clearly enabled
                        // while leaving the filled "Start patching" as the stronger action.
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.launch_game),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // Astraea has no stock to go back to: a patched Priconne is a rebuilt split set with
                    // the CrackProof shell removed, and the only way back is a store reinstall.
                    if (rootAvailable && mode != PatchMode.ASTRAEA) {
                        OutlinedButton(
                            onClick = onUninstallRequest,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_uninstall),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.uninstall_mod),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Capability Pill indicators (Root, Shizuku, 16KB)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HeroStatusBadge(
                    label = stringResource(R.string.capability_root),
                    state = when {
                        !rootInitialized -> stringResource(R.string.working)
                        rootAvailable -> stringResource(R.string.chip_state_granted)
                        else -> stringResource(R.string.chip_state_not_granted)
                    },
                    active = rootAvailable,
                    activeColor = successColor,
                    inactiveColor = if (rootInitialized) errorColor else MaterialTheme.colorScheme.onSurfaceVariant
                )

                HeroStatusBadge(
                    label = stringResource(R.string.capability_shizuku),
                    state = if (shizukuAvailable)
                        stringResource(R.string.chip_state_ready)
                    else stringResource(R.string.chip_state_unavailable),
                    active = shizukuAvailable,
                    activeColor = successColor,
                    inactiveColor = errorColor
                )

                HeroStatusBadge(
                    label = stringResource(R.string.capability_16kb),
                    state = stringResource(R.string.chip_state_ready),
                    active = true,
                    // Same success tone as the other "ready" chips: primary is close to
                    // error in several palettes, so it read as a failure at a glance.
                    activeColor = successColor,
                    inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compact mode switcher shown in the hero header.
 *
 * A dropdown rather than a segmented control: two options fit in a chip, and the header already
 * carries the app name, so the lighter control keeps the row from competing for width.
 */
@Composable
private fun ModeSelectorChip(mode: PatchMode, onModeChange: (PatchMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Text(
                    text = stringResource(mode.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = stringResource(R.string.mode_switch),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PatchMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        expanded = false
                        onModeChange(option)
                    },
                    trailingIcon = {
                        if (option == mode) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HeroStatusBadge(
    label: String,
    state: String,
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color
) {
    val dotColor = if (active) activeColor else inactiveColor
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$label: $state",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
