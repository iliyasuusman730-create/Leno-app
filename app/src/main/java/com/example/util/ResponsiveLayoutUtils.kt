package com.example.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Utility functions for adaptive layout scaling across small screens (<= 360dp width,
 * e.g. 320px compact devices / Galaxy Fold cover screen) and large screens (>= 600dp width,
 * e.g. tablets, foldables, landscape).
 */

val MaxContentWidth: Dp = 640.dp
val MaxFormWidth: Dp = 480.dp
val MaxDialogWidth: Dp = 500.dp
val MaxChatWidth: Dp = 760.dp

@Composable
fun isSmallScreen(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp <= 360
}

@Composable
fun isLargeScreen(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp >= 600
}

@Composable
fun screenWidthDp(): Int {
    return LocalConfiguration.current.screenWidthDp
}

@Composable
fun responsiveHorizontalPadding(): Dp {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width <= 360 -> 12.dp
        width >= 600 -> 24.dp
        else -> 16.dp
    }
}

@Composable
fun responsiveCardPadding(): Dp {
    val width = LocalConfiguration.current.screenWidthDp
    return if (width <= 360) 12.dp else 16.dp
}

@Composable
fun responsiveTitleSize(smallSp: TextUnit = 22.sp, normalSp: TextUnit = 28.sp): TextUnit {
    return if (isSmallScreen()) smallSp else normalSp
}

@Composable
fun responsiveBubbleMaxWidth(isOfficial: Boolean = false): Dp {
    val screenW = LocalConfiguration.current.screenWidthDp
    val ratio = if (isOfficial) 0.84f else 0.76f
    val calculated = (screenW * ratio).coerceIn(200f, 480f)
    return calculated.dp
}

