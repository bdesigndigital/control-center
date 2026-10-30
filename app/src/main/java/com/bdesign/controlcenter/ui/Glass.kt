package com.bdesign.controlcenter.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Effet « verre » : dégradé translucide, reflet en haut à gauche et bordure lumineuse.
 * Le flou de l'arrière-plan vient de la fenêtre elle-même (blurBehind), pas de ce modifier.
 */
fun Modifier.glass(shape: Shape, alpha: Float = 0.16f): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = alpha + 0.08f),
                Color.White.copy(alpha = (alpha - 0.05f).coerceAtLeast(0.02f)),
            )
        )
    )
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * 0.15f, 0f),
                radius = size.maxDimension,
            )
        )
    }
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.60f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.28f),
            )
        ),
        shape,
    )

/** Clic avec léger rebond + retour haptique. À placer AVANT glass() pour que le fond rebondisse aussi. */
@Composable
fun Modifier.bounceClick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "press",
    )
    val haptic = LocalHapticFeedback.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}
