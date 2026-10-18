package dev.hachifridge.hachidroid

import android.app.Application
import dev.hachifridge.hachidroid.patcher.LegacyInstallCleanup
import dev.hachifridge.hachidroid.shizuku.ShizukuState

class HachiDroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuState.init()
        LegacyInstallCleanup.register(this)
    }
}
