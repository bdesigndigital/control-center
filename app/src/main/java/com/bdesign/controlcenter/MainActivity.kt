package com.bdesign.controlcenter

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.bdesign.controlcenter.ui.LocalPanelColors
import com.bdesign.controlcenter.ui.bounceClick
import com.bdesign.controlcenter.ui.panelColors
import com.bdesign.controlcenter.ui.tile
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val Green = Color(0xFF30D158)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalPanelColors provides panelColors(isSystemInDarkTheme())) {
                SetupScreen()
            }
        }
    }
}

private fun isAccessibilityOn(ctx: Context): Boolean {
    val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val component = ComponentName(ctx, ControlCenterService::class.java)
    return enabled.split(':').any {
        it.equals(component.flattenToString(), ignoreCase = true) ||
            it.equals(component.flattenToShortString(), ignoreCase = true)
    }
}

@Composable
private fun SetupScreen() {
    val ctx = LocalContext.current
    val c = LocalPanelColors.current

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            tick++
        }
    }
    val accessibility = remember(tick) { isAccessibilityOn(ctx) }
    val writeSettings = remember(tick) { Settings.System.canWrite(ctx) }
    val notifications = remember(tick) {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }
    val dnd = remember(tick) {
        ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    }
    val events = remember(tick) { ControlCenterService.recentSystemUiEvents.toList() }
    val shizuku = remember(tick) { ShizukuShell.status(ctx) }

    val prefs = remember { ctx.getSharedPreferences(ControlCenterService.PREFS, Context.MODE_PRIVATE) }
    var intercept by remember { mutableStateOf(prefs.getBoolean(ControlCenterService.KEY_INTERCEPT, true)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.screenBg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Control Center", color = c.content, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Une fois configuré, glisse vers le bas depuis le coin supérieur droit de l’écran pour ouvrir le panneau.",
                color = c.secondary,
                fontSize = 14.sp,
            )

            Step(
                title = "1. Service d’accessibilité",
                description = "Obligatoire : détecte le geste et affiche le panneau. Sur Samsung, si l’option est grisée : Infos de l’app → ⋮ → Autoriser les paramètres restreints.",
                done = accessibility,
                action = "Activer",
                onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                action2 = "Infos de l’app",
                onClick2 = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                },
            )
            Step(
                title = "2. Modifier les réglages système",
                description = "Pour la luminosité et la rotation automatique.",
                done = writeSettings,
                action = "Autoriser",
                onClick = { ctx.startActivity(Intents.writeSettings(ctx.packageName)) },
            )
            Step(
                title = "3. Accès aux notifications",
                description = "Pour afficher le lecteur média (titre, pochette, lecture).",
                done = notifications,
                action = "Autoriser",
                onClick = { ctx.startActivity(Intents.notificationListener()) },
            )
            Step(
                title = "4. Ne pas déranger",
                description = "Pour activer le mode silence depuis le panneau.",
                done = dnd,
                action = "Autoriser",
                onClick = { ctx.startActivity(Intents.dnd()) },
            )
            Step(
                title = "5. Contrôle direct (Shizuku) — optionnel",
                description = when (shizuku) {
                    ShizukuStatus.NOT_INSTALLED ->
                        "Pour activer Wi-Fi, Bluetooth, données et mode avion d’un seul appui (Android l’interdit sinon). Installe d’abord l’app Shizuku."
                    ShizukuStatus.NOT_RUNNING ->
                        "Ouvre Shizuku et choisis « Démarrer via le débogage sans fil » (Wi-Fi requis, à refaire après chaque redémarrage)."
                    ShizukuStatus.NEEDS_PERMISSION ->
                        "Shizuku tourne. Autorise Control Center à l’utiliser."
                    ShizukuStatus.READY ->
                        "Actif : un appui bascule directement, un appui long ouvre les réglages."
                },
                done = shizuku == ShizukuStatus.READY,
                action = when (shizuku) {
                    ShizukuStatus.NOT_INSTALLED -> "Installer Shizuku"
                    ShizukuStatus.NOT_RUNNING -> "Ouvrir Shizuku"
                    ShizukuStatus.NEEDS_PERMISSION -> "Autoriser"
                    ShizukuStatus.READY -> "Ouvrir Shizuku"
                },
                onClick = {
                    when (shizuku) {
                        ShizukuStatus.NOT_INSTALLED -> ctx.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=${ShizukuShell.PACKAGE}"),
                            )
                        )
                        ShizukuStatus.NEEDS_PERMISSION -> ShizukuShell.requestPermission()
                        else -> ctx.packageManager.getLaunchIntentForPackage(ShizukuShell.PACKAGE)
                            ?.let { ctx.startActivity(it) }
                    }
                },
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .tile(RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Remplacer le volet rapide du système",
                        color = c.content,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = intercept,
                        onCheckedChange = {
                            intercept = it
                            prefs.edit().putBoolean(ControlCenterService.KEY_INTERCEPT, it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Green,
                            checkedThumbColor = Color.White,
                        ),
                    )
                }
                Text(
                    "Dans une app, Samsung garde le swipe sur la barre d’état. Quand il ouvre ses réglages rapides, le panneau est refermé et remplacé par celui-ci.",
                    color = c.secondary,
                    fontSize = 13.sp,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .bounceClick {
                        val service = ControlCenterService.instance
                        if (service != null) {
                            service.showPanel()
                        } else {
                            Toast.makeText(ctx, "Active d’abord le service d’accessibilité", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .background(c.content)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Tester le panneau", color = c.screenBg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            if (events.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .tile(RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Diagnostic", color = c.content, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Derniers événements du système (classe | texte). Utile si le remplacement ne se déclenche pas.",
                        color = c.secondary,
                        fontSize = 12.sp,
                    )
                    events.forEach {
                        Text(it, color = c.content, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(
    title: String,
    description: String,
    done: Boolean,
    action: String,
    onClick: () -> Unit,
    action2: String? = null,
    onClick2: (() -> Unit)? = null,
) {
    val c = LocalPanelColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .tile(RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = c.content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (done) "✓ Activé" else "À faire",
                color = if (done) Green else c.secondary,
                fontSize = 13.sp,
            )
        }
        Text(description, color = c.secondary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(action, onClick)
            if (action2 != null && onClick2 != null) PillButton(action2, onClick2)
        }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    val c = LocalPanelColors.current
    Box(
        Modifier
            .bounceClick(onClick = onClick)
            .clip(CircleShape)
            .background(c.circleOff)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = c.content, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
