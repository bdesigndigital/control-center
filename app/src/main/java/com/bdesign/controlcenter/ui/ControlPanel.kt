package com.bdesign.controlcenter.ui

import android.accessibilityservice.AccessibilityService
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdesign.controlcenter.ControlState
import com.bdesign.controlcenter.Intents
import com.bdesign.controlcenter.SystemControls
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val Blue = Color(0xFF2F80FF)
private val Green = Color(0xFF30D158)
private val Orange = Color(0xFFFF9F0A)
private val Purple = Color(0xFF7B61FF)
private val Yellow = Color(0xFFFFB800)

@Composable
fun ControlPanel(
    visible: Boolean,
    blurAvailable: Boolean,
    topInset: Dp,
    controls: SystemControls,
    actions: PanelActions,
) {
    val pkg = LocalContext.current.packageName
    val s = controls.state

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shown = true
        while (isActive) {
            controls.refresh()
            delay(600)
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (visible && shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "panel",
    )

    var brightness by remember { mutableFloatStateOf(controls.brightness()) }
    var volume by remember { mutableFloatStateOf(controls.volume()) }

    val scrimAlpha = if (blurAvailable) 0.20f else 0.55f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha * progress.coerceIn(0f, 1f)))
            .pointerInput(Unit) { detectTapGestures { actions.close() } }
    ) {
        Column(
            modifier = Modifier
                .padding(top = topInset, start = 16.dp, end = 16.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val p = progress
                    alpha = p.coerceIn(0f, 1f)
                    translationY = -48.dp.toPx() * (1f - p)
                    val sc = 0.92f + 0.08f * p
                    scaleX = sc
                    scaleY = sc
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .pointerInput(Unit) { detectTapGestures { } },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(168.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectivityCard(s, actions, Modifier.weight(1f).fillMaxHeight())
                MediaCard(s, controls, actions, Modifier.weight(1f).fillMaxHeight())
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(176.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassVerticalSlider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        controls.setBrightness(it)
                    },
                    icon = Icons.Rounded.WbSunny,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onBlocked = if (s.canWrite) {
                        null
                    } else {
                        { actions.launch(Intents.writeSettings(pkg)) }
                    },
                )
                GlassVerticalSlider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        controls.setVolume(it)
                    },
                    icon = Icons.Rounded.VolumeUp,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                Column(
                    Modifier
                        .weight(2f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GlassTile(
                            icon = Icons.Rounded.FlashOn,
                            label = "Lampe",
                            active = s.torch,
                            accent = Yellow,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) { controls.toggleTorch() }
                        GlassTile(
                            icon = Icons.Rounded.DoNotDisturbOn,
                            label = "Silence",
                            active = s.dnd,
                            accent = Purple,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            if (!controls.toggleDnd()) actions.launch(Intents.dnd())
                        }
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GlassTile(
                            icon = Icons.Rounded.ScreenRotation,
                            label = "Rotation",
                            active = s.autoRotate,
                            accent = Orange,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            if (!controls.toggleRotation()) actions.launch(Intents.writeSettings(pkg))
                        }
                        GlassTile(
                            icon = Icons.Rounded.CropFree,
                            label = "Capture",
                            active = false,
                            accent = Blue,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) { actions.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShortcutTile(Icons.Rounded.CameraAlt, "Appareil photo", Modifier.weight(1f).fillMaxHeight()) {
                    actions.launch(Intents.camera())
                }
                ShortcutTile(Icons.Rounded.Calculate, "Calculatrice", Modifier.weight(1f).fillMaxHeight()) {
                    actions.launch(Intents.calculator())
                }
                ShortcutTile(Icons.Rounded.Timer, "Minuteur", Modifier.weight(1f).fillMaxHeight()) {
                    actions.launch(Intents.timer())
                }
                ShortcutTile(Icons.Rounded.Lock, "Verrouiller", Modifier.weight(1f).fillMaxHeight()) {
                    actions.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                }
                ShortcutTile(Icons.Rounded.Settings, "Réglages", Modifier.weight(1f).fillMaxHeight()) {
                    actions.launch(Intents.settings())
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .bounceClick { actions.global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) }
                    .glass(CircleShape, 0.12f),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Réglages rapides système", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ConnectivityCard(s: ControlState, actions: PanelActions, modifier: Modifier) {
    Box(modifier.glass(RoundedCornerShape(32.dp)).padding(14.dp)) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CircleButton(Icons.Rounded.Wifi, "Wi-Fi", s.wifi, Blue) {
                    actions.launch(Intents.internetPanel())
                }
                CircleButton(Icons.Rounded.Bluetooth, "Bluetooth", s.bluetooth, Blue) {
                    actions.launch(Intents.bluetooth())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CircleButton(Icons.Rounded.SignalCellularAlt, "Données mobiles", s.cellular, Green) {
                    actions.launch(Intents.internetPanel())
                }
                CircleButton(Icons.Rounded.AirplanemodeActive, "Mode avion", s.airplane, Orange) {
                    actions.launch(Intents.airplane())
                }
            }
        }
    }
}

@Composable
private fun MediaCard(s: ControlState, controls: SystemControls, actions: PanelActions, modifier: Modifier) {
    val media = s.media
    Box(modifier.glass(RoundedCornerShape(32.dp)).padding(14.dp)) {
        if (media == null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                Text(
                    text = if (s.mediaAccess) "Rien en lecture" else "Autoriser l’accès aux médias",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = if (s.mediaAccess) {
                        Modifier
                    } else {
                        Modifier.clickable { actions.launch(Intents.notificationListener()) }
                    },
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val art = remember(media.art) { media.art?.asImageBitmap() }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (art != null) {
                            Image(
                                bitmap = art,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            media.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            media.artist,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaButton(Icons.Rounded.SkipPrevious) { controls.previous() }
                    MediaButton(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow) { controls.playPause() }
                    MediaButton(Icons.Rounded.SkipNext) { controls.next() }
                }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .bounceClick(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun CircleButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(58.dp)
            .bounceClick(onClick)
            .glass(CircleShape, 0.14f)
            .background(if (active) accent.copy(alpha = 0.92f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun GlassTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .bounceClick(onClick)
            .glass(RoundedCornerShape(26.dp))
            .background(if (active) accent.copy(alpha = 0.92f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
            Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ShortcutTile(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .bounceClick(onClick)
            .glass(RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** Grand slider vertical translucide (style ColorOS / iOS). */
@Composable
private fun GlassVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onBlocked: (() -> Unit)? = null,
) {
    val interaction = if (onBlocked != null) {
        Modifier.clickable { onBlocked() }
    } else {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange(1f - (offset.y / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        onValueChange(1f - (offset.y / size.height).coerceIn(0f, 1f))
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        onValueChange(1f - (change.position.y / size.height).coerceIn(0f, 1f))
                    },
                )
            }
    }

    Box(
        modifier
            .glass(RoundedCornerShape(30.dp), 0.14f)
            .then(interaction)
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0f, 1f))
                .background(Color.White.copy(alpha = 0.92f))
        )
        Icon(
            icon,
            contentDescription = null,
            tint = if (value > 0.12f) Color(0xFF2B2B2E) else Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .size(26.dp),
        )
    }
}
