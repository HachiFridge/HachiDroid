package dev.hachifridge.hachidroid.shizuku

import androidx.compose.runtime.mutableStateOf
import rikka.shizuku.Shizuku

object ShizukuState {
    val isAvailable = mutableStateOf(false)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        isAvailable.value = true
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isAvailable.value = false
    }

    fun init() {
        isAvailable.value = Shizuku.pingBinder()
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }
}
