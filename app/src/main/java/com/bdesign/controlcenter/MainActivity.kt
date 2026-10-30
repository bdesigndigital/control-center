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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.bdesign.controlcenter.ui.bounceClick
import com.bdesign.controlcenter.ui.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SetupScreen() }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1B1F3B), Color(0xFF3D5AFE), Color(0xFF7B61FF))))
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
            Text("Control Center", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Une fois configuré, glisse vers le bas depuis le coin supérieur droit de l’écran pour ouvrir le panneau.",
                color = Color.White.copy(alpha = 0.8f),
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
                    .background(Color.White)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Tester le panneau", color = Color(0xFF1B1F3B), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (done) "✓ Activé" else "À faire",
                color = if (done) Color(0xFF30D158) else Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        }
        Text(description, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(action, onClick)
            if (action2 != null && onClick2 != null) PillButton(action2, onClick2)
        }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .bounceClick(onClick)
            .glass(CircleShape, 0.22f)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
