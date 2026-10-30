package com.bdesign.controlcenter

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

/**
 * Shizuku donne à l'app les droits « shell » (ceux d'ADB) une fois démarré depuis le téléphone.
 * C'est ce qui permet de basculer Wi-Fi, Bluetooth, données et mode avion d'un seul appui.
 */
object ShizukuShell {
    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4242

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun status(ctx: Context): ShizukuStatus {
        val installed = runCatching { ctx.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess
        return when {
            !installed -> ShizukuStatus.NOT_INSTALLED
            !isRunning() -> ShizukuStatus.NOT_RUNNING
            !hasPermission() -> ShizukuStatus.NEEDS_PERMISSION
            else -> ShizukuStatus.READY
        }
    }

    /** Exécute une commande shell avec les droits Shizuku. À appeler hors du thread principal. */
    fun run(command: String): Boolean = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        process.waitFor() == 0
    }.getOrDefault(false)
}
