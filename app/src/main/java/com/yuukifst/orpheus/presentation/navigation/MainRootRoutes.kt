package com.yuukifst.orpheus.presentation.navigation

internal fun isMainRootRoute(route: String?): Boolean = when (route) {
    Screen.Search.route,
    Screen.Library.route,
    Screen.Downloads.route -> true
    Screen.Home.route -> true
    else -> false
}

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Library.route, Screen.Home.route -> 0
    Screen.Search.route -> 1
    Screen.Downloads.route -> 2
    else -> null
}
