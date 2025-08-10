package com.yourname.mdlbapp.rule.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yourname.mdlbapp.core.ui.AppHeightClass
import com.yourname.mdlbapp.core.ui.AppWidthClass
import com.yourname.mdlbapp.core.ui.rememberAppHeightClass
import com.yourname.mdlbapp.core.ui.rememberAppWidthClass
import com.yourname.mdlbapp.core.ui.rememberIsLandscape

internal data class BabyRulesUiTokens(
    val contentMaxWidth: Dp,
    val hPad: Dp,
    val vPad: Dp,
    val gap: Dp,
    val backIcon: Dp,
    val backText: Float,
    val titleSize: Float,
    val emptySize: Float,
    val listGap: Dp,
    val bottomPad: Dp
)

@Composable
internal fun rememberBabyRulesUiTokens(): BabyRulesUiTokens {
    val w = rememberAppWidthClass()
    val h = rememberAppHeightClass()
    val landscape = rememberIsLandscape()

    val phonePortrait  = !landscape && w == AppWidthClass.Compact
    val phoneLandscape =  landscape && h == AppHeightClass.Compact && w != AppWidthClass.Expanded
    val tablet         = w == AppWidthClass.Medium || w == AppWidthClass.Expanded

    val contentMaxWidth = when (w) {
        AppWidthClass.Expanded -> 800.dp
        AppWidthClass.Medium  -> 680.dp
        else -> if (phoneLandscape) 560.dp else 520.dp
    }
    val hPad = when {
        phoneLandscape -> 12.dp
        phonePortrait  -> 16.dp
        else -> 20.dp
    }
    val vPad = when {
        phoneLandscape -> 10.dp
        phonePortrait  -> 18.dp
        else -> 22.dp
    }
    val gap = if (phoneLandscape) 8.dp else 12.dp
    val listGap = if (phoneLandscape) 10.dp else 12.dp
    val bottomPad = if (phoneLandscape) 12.dp else 20.dp

    val backIcon = when {
        tablet -> 41.dp
        phoneLandscape -> 35.dp
        else -> 37.dp
    }
    val backText = when {
        tablet -> 18f
        phoneLandscape -> 16f
        else -> 17f
    }

    val titleSize = when {
        tablet -> 26f
        phoneLandscape -> 22f
        else -> 24f
    }
    val emptySize = when {
        tablet -> 18f
        phoneLandscape -> 16f
        else -> 17f
    }

    return BabyRulesUiTokens(
        contentMaxWidth, hPad, vPad, gap,
        backIcon, backText, titleSize, emptySize, listGap, bottomPad
    )
}