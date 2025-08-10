package com.app.mdlbapp.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val MdlbPrimary = Color(0xFF9C6F6F) // «главная» краска — цвет кнопок и акцентов
val MdlbOnPrimary = Color(0xFF53291E) // цвет текста/иконок СВЕРХУ на «главной» краске
val MdlbBackground = Color(0xFFF5EAE3) //Задний фон
val MdlbOnBackground = Color(0xFF2A2A2A) // цвет текста/иконок на общем фоне


val LightColors = lightColorScheme(
    primary = MdlbPrimary, onPrimary = MdlbOnPrimary,
    background = MdlbBackground, onBackground = MdlbOnBackground
)
val DarkColors = darkColorScheme(primary = MdlbPrimary, onPrimary = MdlbOnPrimary)