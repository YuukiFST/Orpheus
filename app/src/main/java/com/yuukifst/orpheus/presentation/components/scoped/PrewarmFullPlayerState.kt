package com.yuukifst.orpheus.presentation.components.scoped

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable
internal fun rememberPrewarmFullPlayer(
    currentSongId: String?,
    sheetExpansionFraction: Float = 0f,
): Boolean {
    val context = LocalContext.current

    // OPT #5: Skip prewarm entirely on low-RAM devices. Having two FullPlayerContent
    // instances in the composition tree simultaneously (even with alpha=0) doubles
    // the recomposition cost. On low-end hardware this is not worth the UX benefit.
    val isLowRamDevice = remember(context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.isLowRamDevice
    }

    var prewarmFullPlayer by remember { mutableStateOf(false) }

    if (isLowRamDevice) return false

    LaunchedEffect(currentSongId, sheetExpansionFraction) {
        if (currentSongId == null) return@LaunchedEffect
        // Defer prewarm until after the tap frame paints; only warm when the sheet is
        // already partially expanded so we do not compose a second FullPlayerContent on
        // every song change while collapsed.
        if (sheetExpansionFraction <= 0.01f) return@LaunchedEffect
        withFrameNanos { }
        prewarmFullPlayer = true
    }
    LaunchedEffect(currentSongId, prewarmFullPlayer) {
        if (prewarmFullPlayer) {
            delay(32)
            prewarmFullPlayer = false
        }
    }

    return prewarmFullPlayer
}
