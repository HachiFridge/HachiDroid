package dev.hachifridge.hachidroid.patcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.ui.patcher.PatcherLauncher
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PackageInstallerStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java
                )
                if (confirmationIntent != null) {
                    context.startActivity(confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val pending = contList.toList()
                contList.clear()
                pending.forEach { it.resume(true) }
            }
            else -> {
                val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                if (!statusMessage.isNullOrEmpty())
                    PatcherLauncher.log(context.getString(R.string.install_failed) + ": $statusMessage")
                val pending = contList.toList()
                contList.clear()
                pending.forEach { it.resume(false) }
            }
        }
    }

    companion object {
        val contList: MutableList<Continuation<Boolean>> = CopyOnWriteArrayList()

        /**
         * Upper bound for the whole install. The system's confirmation dialog (STATUS_PENDING_USER_ACTION)
         * is answered by the user and a large split install can run for minutes, so a 1-minute cap
         * reported "Install failed" while the install was still going to succeed.
         */
        suspend fun waitForInstallFinish(timeoutMs: Long = 10 * 60_000L): Boolean {
            contList.clear()
            return withTimeoutOrNull(timeoutMs) {
                suspendCoroutine<Boolean> { cont ->
                    contList.add(cont)
                }
            } ?: run {
                contList.clear()
                false
            }
        }
    }
}
