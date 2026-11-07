package dev.hachifridge.hachidroid.ui.patcher

import android.content.Context
import dev.hachifridge.hachidroid.MainActivity
import dev.hachifridge.hachidroid.patcher.Patcher
import dev.hachifridge.hachidroid.patcher.PatchingService
import dev.hachifridge.hachidroid.ui.navigation.Destination
import dev.hachifridge.hachidroid.utils.getActivity
import dev.hachifridge.hachidroid.ui.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PatcherLauncher {
    @Volatile var patcher: Patcher? = null
    @Volatile var patching = false

    fun launch(navigator: Navigator, patcher: Patcher) {
        if (patching) return

        this.patcher = patcher
        navigator.navigate(Destination.Patching)
    }

    fun cancelPatcher() {
        patcher?.isCancelled = true
    }

    /** Logs through the active patcher's callback; usable from non-activity contexts. */
    fun log(line: String) {
        patcher?.onLog?.invoke(line)
    }

    suspend fun runPatcher(context: Context, callback: (Boolean) -> Unit) {
        val patcher = this.patcher
        if (patcher == null || patching) return

        val activity = context.getActivity() as MainActivity
        val appContext = context.applicationContext
        val title = context.getString(patcher.notificationTitleRes)
        withContext(Dispatchers.IO) {
            activity.useKeepScreenOn {
                patching = true
                PatchingService.start(appContext, title = title)
                try {
                    val result = patcher.safeRun(context)
                    callback(result)
                } finally {
                    patching = false
                    this@PatcherLauncher.patcher = null
                    PatchingService.stop(appContext)
                }
            }
        }
    }
}
