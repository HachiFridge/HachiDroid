package dev.hachifridge.hachidroid.ui.screen

import android.content.Context
import androidx.lifecycle.ViewModel
import dev.hachifridge.hachidroid.core.UpdateChecker

class AboutViewModel : ViewModel() {
    fun checkForUpdates(context: Context) {
        UpdateChecker.run(context)
    }
}
