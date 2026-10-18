package dev.hachifridge.hachidroid.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.patcher.LegacyInstaller
import dev.hachifridge.hachidroid.ui.component.BackButton
import dev.hachifridge.hachidroid.ui.component.PatchLogViewer
import dev.hachifridge.hachidroid.ui.component.SectionCard
import dev.hachifridge.hachidroid.ui.component.SimpleOkCancelDialog
import dev.hachifridge.hachidroid.ui.component.TopBar
import dev.hachifridge.hachidroid.ui.component.bottomControlsPadding
import dev.hachifridge.hachidroid.ui.navigation.Navigator
import dev.hachifridge.hachidroid.ui.patcher.PatcherLauncher
import dev.hachifridge.hachidroid.utils.safeNavigate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** How the run currently reads at a glance: in flight, done, or failed. */
private enum class RunState { Working, Completed, Failed }

@Composable
fun PatchingScreen(
    navigator: Navigator,
    viewModel: PatchingViewModel = viewModel()
) {
    val workingStr = stringResource(R.string.working)
    val completedStr = stringResource(R.string.completed)
    val patchingStr = stringResource(R.string.patching)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val patchSuccessMsg = stringResource(PatcherLauncher.patcher?.successMessageRes ?: R.string.patch_success_msg)
    val patchFailedMsg = stringResource(PatcherLauncher.patcher?.failureMessageRes ?: R.string.patch_failed_msg)
    val patchCancelledMsg = stringResource(R.string.patching_cancelled_by_user)
    val cancelledTitle = stringResource(R.string.patch_cancelled_title)
    // Read before the run starts: PatcherLauncher clears the patcher once it finishes.
    val failureTitle = stringResource(PatcherLauncher.patcher?.failureTitleRes ?: R.string.patch_failed_title)

    val sfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.handleSaveFileResult(context, it.data?.data, completedStr)
    }

    LaunchedEffect(viewModel.sfFile) {
        val file = viewModel.sfFile
        if (file != null) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_TITLE, file.name)
                }
            sfLauncher.launch(intent)
        }
    }

    val legacyInstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.handleLegacyInstallResult(true)
    }

    val legacyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val file = viewModel.legacyFile
        if (file == null) {
            viewModel.handleLegacyInstallResult(false)
            return@rememberLauncherForActivityResult
        }
        if (LegacyInstaller.canRequestPackageInstalls(context)) {
            val intent = LegacyInstaller.buildInstallIntent(context, file)
            try {
                legacyInstallLauncher.launch(intent)
            } catch (_: Exception) {
                viewModel.handleLegacyInstallResult(false)
            }
        } else {
            viewModel.handleLegacyInstallResult(false)
        }
    }

    var showLegacyPermDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.legacyFile) {
        val file = viewModel.legacyFile
        if (file != null) {
            if (LegacyInstaller.canRequestPackageInstalls(context)) {
                val intent = LegacyInstaller.buildInstallIntent(context, file)
                try {
                    legacyInstallLauncher.launch(intent)
                } catch (_: Exception) {
                    viewModel.handleLegacyInstallResult(false)
                }
            } else {
                showLegacyPermDialog = true
            }
        }
    }

    if (showLegacyPermDialog) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.legacy_install_unknown_sources_required),
            onClose = { ok ->
                showLegacyPermDialog = false
                if (ok) {
                    val permIntent = LegacyInstaller.pickUnknownSourcesIntent(context)
                    if (permIntent != null) {
                        try {
                            legacyPermLauncher.launch(permIntent)
                        } catch (_: Exception) {
                            viewModel.handleLegacyInstallResult(false)
                        }
                    } else {
                        viewModel.handleLegacyInstallResult(false)
                    }
                } else {
                    viewModel.handleLegacyInstallResult(false)
                }
            }
        ) {
            Text(stringResource(R.string.legacy_install_unknown_sources_info))
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    BackHandler {
        if (viewModel.completed && viewModel.sfFile == null && viewModel.legacyFile == null) {
            safeNavigate(lifecycleOwner) {
                navigator.popBackStack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.init(context, patchSuccessMsg, patchFailedMsg, patchCancelledMsg, failureTitle)
    }

    val runState = when {
        !viewModel.completed -> RunState.Working
        viewModel.isSuccess -> RunState.Completed
        else -> RunState.Failed
    }

    var showLogViewer by rememberSaveable { mutableStateOf(false) }
    if (showLogViewer) {
        PatchLogViewer(
            logFile = viewModel.logFile,
            fallbackLog = viewModel.log,
            onDismiss = { showLogViewer = false }
        )
    }

    // Cancelling is its own outcome: it is not a failure and not a success.
    val cancelled = viewModel.completed && viewModel.isCancelled

    val totalStage = viewModel.stages.firstOrNull { it.label == "total" }
    val totalSeconds = totalStage?.let { String.format(Locale.ROOT, "%.2f", it.seconds) }
    val timingText = when {
        runState == RunState.Working || totalSeconds == null -> null
        cancelled -> stringResource(R.string.patch_cancelled_after, totalSeconds)
        runState == RunState.Failed -> stringResource(R.string.patch_failed_after, totalSeconds)
        else -> stringResource(R.string.patch_finished_in, totalSeconds)
    }

    // A failure used to read "Completed" over a generic message; it now names the verdict
    // and, when the patcher logged one, the cause.
    val outcomeTitle = when {
        cancelled -> cancelledTitle
        viewModel.isSuccess -> completedStr
        else -> viewModel.failureTitle.ifBlank { failureTitle }
    }
    val screenTitle = if (runState == RunState.Working) workingStr else outcomeTitle
    val failureReason = viewModel.failureReason
    val summaryDetail = when {
        runState == RunState.Working -> viewModel.currentTask
        cancelled -> patchCancelledMsg
        // Read from the ViewModel: PatcherLauncher clears the patcher before this recomposes.
        viewModel.isSuccess -> viewModel.successMessage.ifBlank { patchSuccessMsg }
        failureReason != null -> failureReason
        else -> stringResource(R.string.patch_failed_no_reason)
    }

    Scaffold(
        topBar = {
            TopBar(
                title = screenTitle,
                navigationIcon = { BackButton(navigator, enabled = viewModel.completed && viewModel.sfFile == null && viewModel.legacyFile == null) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RunSummaryCard(
                    runState = runState,
                    progress = viewModel.progress,
                    title = if (runState == RunState.Working) patchingStr else outcomeTitle,
                    detail = summaryDetail,
                    step = if (runState == RunState.Failed && !cancelled) {
                        viewModel.failedTask
                            ?.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.patch_failed_step, it) }
                    } else {
                        null
                    },
                    timing = timingText
                )

                if (viewModel.stages.isNotEmpty()) {
                    StageTimelineCard(
                        stages = viewModel.stages,
                        liveTask = if (runState == RunState.Working) viewModel.currentTask else null,
                        // A cancelled run ends where it was stopped, not on an error.
                        failed = runState == RunState.Failed && !cancelled
                    )
                }

                if (runState == RunState.Failed && viewModel.log.isNotEmpty()) {
                    RunLogCard(log = viewModel.log)
                }
            }

            Column(modifier = Modifier.bottomControlsPadding()) {
                // Available whatever the verdict: the log is how a successful run gets audited
                // and how a failed one gets reported.
                if (viewModel.completed) {
                    OutlinedButton(
                        onClick = { showLogViewer = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.patch_view_log),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (!viewModel.completed) {
                    Button(
                        onClick = { PatcherLauncher.cancelPatcher() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.stop_patching_and_cleanup),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else if (viewModel.sfFile == null && !viewModel.isSuccess) {
                    // Nothing left to install, so offer the one useful action instead of a dead button.
                    FilledTonalButton(
                        onClick = {
                            safeNavigate(lifecycleOwner) { navigator.popBackStack() }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.back_to_previous),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else if (viewModel.sfFile == null) {
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val pm = context.packageManager
                                GameChecker.init(pm)
                                var targetPackage = GameChecker.currentPackageName
                                    ?: "jp.co.cygames.umamusume"

                                var intent = pm.getLaunchIntentForPackage(targetPackage)?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                var retries = 3
                                while (intent == null && retries-- > 0) {
                                    delay(500)
                                    GameChecker.init(pm)
                                    targetPackage = GameChecker.currentPackageName ?: targetPackage
                                    intent = pm.getLaunchIntentForPackage(targetPackage)?.apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            R.string.game_not_installed,
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.launch_game),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Headline card: a progress ring with the live percentage, plus the current phase
 * or final verdict.
 */
@Composable
private fun RunSummaryCard(
    runState: RunState,
    progress: Float,
    title: String,
    detail: String,
    step: String?,
    timing: String?
) {
    val failed = runState == RunState.Failed
    SectionCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(runState = runState, progress = progress, modifier = Modifier.size(104.dp))
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (step != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = step,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (timing != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = timing,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(
    runState: RunState,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val arcColor = when (runState) {
        RunState.Failed -> scheme.error
        else -> scheme.primary
    }
    val strokeWidth = 10.dp
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }

    val indeterminate = runState == RunState.Working && progress < 0f
    // Only spin while indeterminate, so no animation clock runs for the whole patch.
    val rotation: Float
    if (indeterminate) {
        val spin = rememberInfiniteTransition(label = "ring")
        rotation = spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "rotation"
        ).value
    } else {
        rotation = 0f
    }
    val filled by animateFloatAsState(
        targetValue = if (progress < 0f) 0f else progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "filled"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = scheme.outlineVariant.copy(alpha = 0.45f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (indeterminate) {
                drawArc(
                    color = arcColor,
                    startAngle = rotation - 90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * filled,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        when {
            runState == RunState.Completed -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(40.dp)
            )
            runState == RunState.Failed -> Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = null,
                tint = scheme.error,
                modifier = Modifier.size(36.dp)
            )
            indeterminate -> Unit
            else -> Text(
                text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface
            )
        }
    }
}

/**
 * The run as a pipeline: one row per stage (merged across files, with an "n/m" badge),
 * sorted into pipeline order, with a pulsing row for whatever is running now.
 */
@Composable
private fun StageTimelineCard(
    stages: List<PatchStage>,
    liveTask: String?,
    failed: Boolean
) {
    // Stages finish out of pipeline order (the second APK's extract lands after the
    // first APK's sign), so sort by pipeline position; the sort is stable, which keeps
    // APKs in the order they were processed.
    val ordered = stages.sortedBy { stageOrder(it.label) }
    val hasDetail = ordered.any {
        it.label !in WRAPPER_STAGES && it.label != "total" && it.label != "sync mod library"
    }
    // Wrapper rows only stand in when the run has no per-stage detail (direct install).
    val rows = if (hasDetail) ordered.filterNot { it.label in WRAPPER_STAGES } else ordered

    // One row open at a time: this is a menu, not a list of independently expanded sections.
    var expandedLabel by rememberSaveable { mutableStateOf<String?>(null) }

    SectionCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        rows.forEachIndexed { index, stage ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
            // A row that folded runs on several files can be opened to list them, which is
            // exactly what the "n/m" badge on its own leaves out.
            val expandable = stage.entries.count { !it.detail.isNullOrBlank() } > 1
            val expanded = expandable && expandedLabel == stage.label
            StageRow(
                label = stageLabelRes(stage.label)?.let { stringResource(it) }
                    ?: stage.label.replaceFirstChar { it.uppercaseChar() },
                detail = stage.detail,
                seconds = stage.seconds,
                live = false,
                emphasized = stage.label == "total",
                // The total row is the verdict: a run that failed must not read as done.
                failed = failed && stage.label == "total",
                counter = if (stage.total > 1) "${stage.completed}/${stage.total}" else null,
                expandable = expandable,
                expanded = expanded,
                onToggle = { expandedLabel = if (expanded) null else stage.label }
            )
            if (expanded) {
                StageBreakdown(stage.entries)
            }
        }

        if (liveTask != null && liveTask.isNotBlank()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            StageRow(label = liveTask, detail = null, seconds = null, live = true, emphasized = false)
        }
    }
}

@Composable
private fun StageRow(
    label: String,
    detail: String?,
    seconds: Float?,
    live: Boolean,
    emphasized: Boolean,
    failed: Boolean = false,
    /** "1/2"-style progress for a row that covers several files; null for one-shot stages. */
    counter: String? = null,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggle: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (expandable) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                live -> PulsingDot()
                failed -> Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                else -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = if (emphasized) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (live || emphasized || failed) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (counter != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = counter,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (seconds != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${String.format(Locale.ROOT, "%.2f", seconds)}s",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = if (failed) {
                    MaterialTheme.colorScheme.error
                } else if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (expandable) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * The files behind a folded timeline row: which APK each run touched and what it cost, in the
 * order the patcher worked through them.
 */
@Composable
private fun StageBreakdown(entries: List<StageEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            val detail = entry.detail
            if (detail.isNullOrBlank()) return@forEachIndexed
            if (index > 0) Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.width(18.dp)
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${String.format(Locale.ROOT, "%.2f", entry.seconds)}s",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(9.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
    )
}

/**
 * Full patcher log for a failed run. The timeline only shows stages that completed, so
 * the real cause (I/O error, signature failure, shell output) lives here, ready to copy
 * into a bug report.
 */
@Composable
private fun RunLogCard(log: List<String>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // Recomputed on each pass while expanded: the log grows while the screen is open.
    val logText = log.joinToString("\n")

    SectionCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.patch_log_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(
                                ClipData.newPlainText(context.getString(R.string.app_name), logText)
                            )
                        Toast.makeText(
                            context,
                            context.getString(R.string.patch_log_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.patch_log_copy))
                }
            }
        }
    }
}

/** Stages that only wrap other stages; their children are what the user cares about. */
private val WRAPPER_STAGES = setOf("patch apk", "patch split apks", "direct install")

/** Position of a stage within the pipeline, independent of when it reported in. */
private fun stageOrder(label: String): Int = when (label) {
    "sync mod library" -> 0
    "copy input" -> 1
    "extract" -> 2
    "patch libraries" -> 3
    "patch extensions" -> 4
    "index apk entries" -> 5
    "rebuild apk" -> 6
    "sign apk" -> 7
    "merge apks" -> 8
    "stage for shizuku" -> 9
    "shizuku install" -> 10
    "stage apk" -> 11
    "package install" -> 12
    "legacy install" -> 13
    "clear legacy mount" -> 14
    "install mod library" -> 15
    "install plugins" -> 16
    "internal files marker" -> 17
    "cleanup" -> 18
    "patch apk", "patch split apks", "direct install" -> 19
    "total" -> 20
    else -> 18
}

/**
 * Resource for a patcher stage id, or null for an id this screen doesn't know (new stages keep
 * working, they just render their raw id). The ids stay code-level identifiers so the patcher
 * never has to know about resources; only their labels are translated.
 */
@StringRes
private fun stageLabelRes(label: String): Int? = when (label) {
    "sync mod library" -> R.string.stage_sync_mod_library
    "copy input" -> R.string.stage_copy_input
    "extract" -> R.string.stage_extract
    "patch libraries" -> R.string.stage_patch_libraries
    "patch manifest" -> R.string.stage_patch_manifest
    "patch extensions" -> R.string.stage_patch_extensions
    "index apk entries" -> R.string.stage_index_apk_entries
    "rebuild apk" -> R.string.stage_rebuild_apk
    "sign apk" -> R.string.stage_sign_apk
    "merge apks" -> R.string.stage_merge_apks
    "stage for shizuku" -> R.string.stage_stage_for_shizuku
    "shizuku install" -> R.string.stage_shizuku_install
    "package install" -> R.string.stage_package_install
    "stage apk" -> R.string.stage_stage_apk
    "legacy install" -> R.string.stage_legacy_install
    "clear legacy mount" -> R.string.stage_clear_legacy_mount
    "install mod library" -> R.string.stage_install_mod_library
    "install plugins" -> R.string.stage_install_plugins
    "internal files marker" -> R.string.stage_internal_files_marker
    "patch apk" -> R.string.stage_patch_apk
    "patch split apks" -> R.string.stage_patch_split_apks
    "direct install" -> R.string.stage_direct_install
    "cleanup" -> R.string.stage_cleanup
    "total" -> R.string.stage_total
    else -> null
}
