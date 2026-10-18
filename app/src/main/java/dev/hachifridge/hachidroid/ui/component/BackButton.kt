package dev.hachifridge.hachidroid.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.hachifridge.hachidroid.utils.safeNavigate
import dev.hachifridge.hachidroid.ui.navigation.Navigator

@Composable
fun BackButton(navigator: Navigator, enabled: Boolean = true) {
    val lifecycleOwner = LocalLifecycleOwner.current
    IconButton(
        onClick = {
            safeNavigate(lifecycleOwner) {
                navigator.popBackStack()
            }
        },
        enabled = enabled
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
    }
}