package com.app.mdlbapp.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val MdlbPrimary = Color(0xFF9C6F6F)
val MdlbOnPrimary = Color(0xFFFFFFFF)
val MdlbBackground = Color(0xFFFAEFEA)
val MdlbOnBackground = Color(0xFF2A2A2A)

val LightColors = lightColorScheme(
    primary = MdlbPrimary, onPrimary = MdlbOnPrimary,
    background = MdlbBackground, onBackground = MdlbOnBackground
)
val DarkColors = darkColorScheme(primary = MdlbPrimary, onPrimary = MdlbOnPrimary)