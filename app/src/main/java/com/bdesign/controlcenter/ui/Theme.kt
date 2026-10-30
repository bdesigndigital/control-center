package com.bdesign.controlcenter.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** Palette sobre, façon iOS : lisible en clair comme en sombre. */
data class PanelColors(
    val tile: Color,
    val border: Color,
    val circleOff: Color,
    val content: Color,
    val secondary: Color,
    val track: Color,
    val fill: Color,
    val fillIcon: Color,
    val screenBg: Color,
)

fun panelColors(dark: Boolean): PanelColors = if (dark) {
    PanelColors(
        tile = Color(0xFF2C2C2E).copy(alpha = 0.90f),
        border = Color.White.copy(alpha = 0.07f),
        circleOff = Color(0xFF48484A),
        content = Color.White,
        secondary = Color.White.copy(alpha = 0.60f),
        track = Color(0xFF48484A).copy(alpha = 0.92f),
        fill = Color.White,
        fillIcon = Color(0xFF1C1C1E),
        screenBg = Color(0xFF000000),
    )
} else {
    PanelColors(
        tile = Color.White.copy(alpha = 0.92f),
        border = Color.Black.copy(alpha = 0.06f),
        circleOff = Color(0xFFE5E5EA),
        content = Color(0xFF1C1C1E),
        secondary = Color(0xFF3C3C43).copy(alpha = 0.60f),
        track = Color(0xFFD1D1D6).copy(alpha = 0.95f),
        fill = Color.White,
        fillIcon = Color(0xFF1C1C1E),
        screenBg = Color(0xFFF2F2F7),
    )
}

val LocalPanelColors = staticCompositionLocalOf { panelColors(dark = true) }

/** Carte opaque (légèrement translucide) avec fine bordure. */
@Composable
fun Modifier.tile(shape: Shape): Modifier {
    val c = LocalPanelColors.current
    return this
        .clip(shape)
        .background(c.tile)
        .border(0.5.dp, c.border, shape)
}

/**
 * Appui avec léger rebond + retour haptique, et appui long optionnel.
 * À placer AVANT tile() pour que le fond rebondisse aussi.
 */
@Composable
fun Modifier.bounceClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "press",
    )
    val haptic = LocalHapticFeedback.current
    val currentClick by rememberUpdatedState(onClick)
    val currentLong by rememberUpdatedState(onLongClick)
    val hasLong = onLongClick != null

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(hasLong) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onLongPress = if (hasLong) {
                    { _: Offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentLong?.invoke()
                    }
                } else {
                    null
                },
                onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentClick()
                },
            )
        }
}
