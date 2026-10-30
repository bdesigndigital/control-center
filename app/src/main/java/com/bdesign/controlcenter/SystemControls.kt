package com.bdesign.controlcenter

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

data class MediaInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
)

data class ControlState(
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val cellular: Boolean = false,
    val airplane: Boolean = false,
    val torch: Boolean = false,
    val dnd: Boolean = false,
    val autoRotate: Boolean = true,
    val canWrite: Boolean = true,
    val media: MediaInfo? = null,
    val mediaAccess: Boolean = true,
)

/** Intents vers les panneaux / écrans système. */
object Intents {
    fun writeSettings(pkg: String) =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$pkg"))

    fun dnd() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    fun notificationListener() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    fun internetPanel() = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    fun bluetooth() = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    fun airplane() = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    fun camera() = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    fun calculator(): Intent =
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)

    fun timer() = Intent(AlarmClock.ACTION_SHOW_TIMERS)
    fun settings() = Intent(Settings.ACTION_SETTINGS)

    // Appuis longs
    fun wifiSettings() = Intent(Settings.ACTION_WIFI_SETTINGS)
    fun dataSettings() = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
    fun zenSettings() = Intent("android.settings.ZEN_MODE_SETTINGS")
    fun displaySettings() = Intent(Settings.ACTION_DISPLAY_SETTINGS)
    fun videoCamera() = Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
    fun alarms() = Intent(AlarmClock.ACTION_SHOW_ALARMS)
}

/**
 * Lit l'état du téléphone et exécute les actions du panneau.
 * Tout se passe sur le thread principal.
 */
class SystemControls(context: Context) {

    private val ctx: Context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val camera = ctx.getSystemService(CameraManager::class.java)
    private val notifications = ctx.getSystemService(NotificationManager::class.java)
    private val wifiManager = ctx.getSystemService(WifiManager::class.java)
    private val connectivity = ctx.getSystemService(ConnectivityManager::class.java)
    private val bluetoothManager = ctx.getSystemService(BluetoothManager::class.java)
    private val sessions = ctx.getSystemService(MediaSessionManager::class.java)

    var state by mutableStateOf(ControlState())
        private set

    private var torchId: String? = null
    private var torchOn = false
    private var controller: MediaController? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                state = state.copy(torch = enabled)
            }
        }
    }

    init {
        torchId = findTorchId()
        runCatching { camera.registerTorchCallback(torchCallback, handler) }
        refresh()
    }

    fun dispose() {
        runCatching { camera.unregisterTorchCallback(torchCallback) }
    }

    // ---------- Lecture de l'état ----------

    fun refresh() {
        val (media, mediaAccess) = readMedia(state.media)
        state = ControlState(
            wifi = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false),
            bluetooth = runCatching { bluetoothManager.adapter?.isEnabled == true }.getOrDefault(false),
            // État réel du réglage « données mobiles » (pas seulement « le réseau actif est mobile »).
            cellular = Settings.Global.getInt(ctx.contentResolver, "mobile_data", 1) == 1,
            airplane = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            torch = torchOn,
            dnd = notifications.currentInterruptionFilter.let {
                it != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            },
            autoRotate = Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1,
            canWrite = Settings.System.canWrite(ctx),
            media = media,
            mediaAccess = mediaAccess,
        )
    }

    private fun readMedia(previous: MediaInfo?): Pair<MediaInfo?, Boolean> {
        return try {
            val list = sessions.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
            val current = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list.firstOrNull()
            controller = current
            if (current == null) {
                null to true
            } else {
                val md = current.metadata
                val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Lecture en cours"
                val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: ""
                val art = if (previous != null && previous.title == title &&
                    previous.artist == artist && previous.art != null
                ) {
                    previous.art
                } else {
                    md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                }
                MediaInfo(title, artist, art, current.playbackState?.state == PlaybackState.STATE_PLAYING) to true
            }
        } catch (e: SecurityException) {
            controller = null
            null to false
        } catch (e: Exception) {
            controller = null
            null to true
        }
    }

    // ---------- Actions ----------

    /**
     * Bascule directe via Shizuku. Renvoie false si Shizuku n'est pas prêt :
     * l'interface ouvre alors le panneau système à la place.
     */
    private fun shell(command: String): Boolean {
        if (!ShizukuShell.hasPermission()) return false
        Thread {
            ShizukuShell.run(command)
            handler.post { refresh() }
            handler.postDelayed({ refresh() }, 900)
        }.start()
        return true
    }

    fun toggleWifi() = shell("svc wifi ${if (state.wifi) "disable" else "enable"}")

    fun toggleBluetooth() = shell("svc bluetooth ${if (state.bluetooth) "disable" else "enable"}")

    fun toggleData() = shell("svc data ${if (state.cellular) "disable" else "enable"}")

    fun toggleAirplane() =
        shell("cmd connectivity airplane-mode ${if (state.airplane) "disable" else "enable"}")

    private var torchLevelIndex = -1

    /** Appui long sur la lampe : allume et passe à l'intensité suivante (faible, moyenne, max). */
    fun cycleTorchStrength() {
        if (Build.VERSION.SDK_INT < 33) {
            toggleTorch()
            return
        }
        val id = torchId ?: return
        runCatching {
            val max = camera.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            if (max <= 1) {
                toggleTorch()
                return
            }
            val levels = listOf(1, (max / 2).coerceAtLeast(1), max).distinct()
            torchLevelIndex = (torchLevelIndex + 1) % levels.size
            camera.turnOnTorchWithStrengthLevel(id, levels[torchLevelIndex])
        }
    }

    fun toggleTorch() {
        val id = torchId ?: return
        runCatching { camera.setTorchMode(id, !torchOn) }
    }

    /** Renvoie false si l'accès « Ne pas déranger » n'est pas encore accordé. */
    fun toggleDnd(): Boolean {
        if (!notifications.isNotificationPolicyAccessGranted) return false
        val target = if (state.dnd) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }
        notifications.setInterruptionFilter(target)
        refresh()
        return true
    }

    /** Renvoie false si la permission « Modifier les réglages système » manque. */
    fun toggleRotation(): Boolean {
        if (!Settings.System.canWrite(ctx)) return false
        Settings.System.putInt(
            ctx.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            if (state.autoRotate) 0 else 1,
        )
        refresh()
        return true
    }

    fun brightness(): Float =
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

    fun setBrightness(fraction: Float) {
        if (!Settings.System.canWrite(ctx)) return
        val value = (fraction * 255f).roundToInt().coerceIn(1, 255)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
    }

    fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).roundToInt().coerceIn(0, max), 0)
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
        handler.postDelayed({ refresh() }, 200)
    }

    fun next() {
        controller?.transportControls?.skipToNext()
        handler.postDelayed({ refresh() }, 300)
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
        handler.postDelayed({ refresh() }, 300)
    }

    private fun findTorchId(): String? = runCatching {
        camera.cameraIdList.firstOrNull { id ->
            val c = camera.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()
}
