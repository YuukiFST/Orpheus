package com.yuukifst.orpheus.presentation.components.scoped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import com.yuukifst.orpheus.presentation.navigation.Screen
import com.yuukifst.orpheus.presentation.navigation.navigateSafely
import com.yuukifst.orpheus.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun PlayerYouTubeChannelSearchEffect(
    navController: NavHostController,
    sheetCollapsedTargetY: Float,
    sheetMotionController: SheetMotionController,
    playerViewModel: PlayerViewModel,
) {
    val latestSheetCollapsedTargetY by rememberUpdatedState(sheetCollapsedTargetY)
    LaunchedEffect(navController) {
        playerViewModel.youTubeChannelSearchRequests.collectLatest { channelName ->
            if (channelName.isBlank()) return@collectLatest
            sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()
            navController.navigateSafely(Screen.Search.route)
        }
    }
}
