package dev.hachifridge.hachidroid.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BottomBarScrollSpacer(modifier: Modifier = Modifier) {
    // Clear system navigation bar / gesture pill with breathing room
    Spacer(modifier.navigationBarsPadding().height(24.dp))
}

fun Modifier.bottomControlsPadding(
    horizontal: Dp = 16.dp,
    top: Dp = 16.dp,
    bottom: Dp = 16.dp
) = this.navigationBarsPadding()
    .padding(start = horizontal, top = top, end = horizontal)
    .padding(bottom = bottom)
