// com.yourname.mdlbapp.core.ui.Responsive.kt
package com.app.mdlbapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class AppWidthClass { Compact, Medium, Expanded }
enum class AppHeightClass { Compact, Medium, Expanded }

@Composable
fun rememberAppWidthClass(): AppWidthClass {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= 840 -> AppWidthClass.Expanded
        w >= 600 -> AppWidthClass.Medium
        else -> AppWidthClass.Compact
    }
}

@Composable
fun rememberAppHeightClass(): AppHeightClass {
    val h = LocalConfiguration.current.screenHeightDp
    return when {
        h >= 900 -> AppHeightClass.Expanded
        h >= 480 -> AppHeightClass.Medium
        else -> AppHeightClass.Compact
    }
}

@Composable
fun rememberIsLandscape(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.screenWidthDp > cfg.screenHeightDp
}