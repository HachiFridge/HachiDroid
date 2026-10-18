package dev.hachifridge.hachidroid.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.BuildConfig
import dev.hachifridge.hachidroid.patcher.Patcher
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.concurrent.thread
import kotlin.coroutines.resume

object ShizukuInstaller {
    suspend fun install(context: Context, files: Array<File>, patcher: Patcher): Boolean {
        patcher.task = context.getString(R.string.shizuku_starting_service)
        patcher.progress = -1f

        val componentName = ComponentName(context, InstallerService::class.java)
        val userServiceArgs = Shizuku.UserServiceArgs(componentName).apply {
            daemon(false)
            processNameSuffix("installer")
            debuggable(BuildConfig.DEBUG)
            version(BuildConfig.VERSION_CODE)
        }

        return suspendCancellableCoroutine { continuation ->
            val resumed = java.util.concurrent.atomic.AtomicBoolean(false)

            fun resumeOnce(result: Boolean) {
                if (resumed.compareAndSet(false, true)) {
                    continuation.resume(result)
                }
            }

            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    if (!continuation.isActive) return

                    thread {
                        val installerService = IInstallerService.Stub.asInterface(service)
                        try {
                            patcher.task = context.getString(R.string.shizuku_installing)
                            val paths = files.map { it.absolutePath }
                            val result = installerService.install(paths)

                            if (result == null) {
                                patcher.log(context.getString(R.string.shizuku_install_success))
                                resumeOnce(true)
                            } else {
                                patcher.log(context.getString(R.string.shizuku_install_fail, result))
                                resumeOnce(false)
                            }
                        } catch (e: Exception) {
                            patcher.log(context.getString(R.string.shizuku_install_error, e.message))
                            resumeOnce(false)
                        } finally {
                            Shizuku.unbindUserService(userServiceArgs, this, true)
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    if (resumed.get()) return
                    patcher.log(context.getString(R.string.shizuku_install_fail_unexpected))
                    resumeOnce(false)
                }
            }

            continuation.invokeOnCancellation {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            }
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        }
    }
}
