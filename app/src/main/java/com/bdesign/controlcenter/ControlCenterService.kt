package com.bdesign.controlcenter

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bdesign.controlcenter.ui.ControlPanel
import com.bdesign.controlcenter.ui.PanelActions
import kotlin.math.abs

class ControlCenterService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ControlCenterService? = null

        const val PREFS = "cc_prefs"
        const val KEY_INTERCEPT = "intercept_quick_settings"
        private const val SYSTEM_UI = "com.android.systemui"

        /** Derniers événements SystemUI vus (affichés dans l'app pour le diagnostic). */
        val recentSystemUiEvents = ArrayDeque<String>()
    }

    private lateinit var wm: WindowManager
    private val owner = OverlayLifecycleOwner()
    private val handler = Handler(Looper.getMainLooper())

    // États lus par Compose
    private val panelOpen = mutableStateOf(false)
    private val blurState = mutableStateOf(false)

    private var trigger: View? = null

    // Le panneau est créé à l'avance (invisible, déjà composé) pour apparaître sans délai.
    private var panel: PanelRoot? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var controls: SystemControls? = null
    private var panelShown = false
    private var closing = false
    private var ignoreShadeUntil = 0L

    /** Texte « Réglages rapides » tel que SystemUI l'annonce dans la langue du téléphone. */
    private val quickSettingsLabel: String? by lazy {
        runCatching {
            val res = packageManager.getResourcesForApplication(SYSTEM_UI)
            val id = res.getIdentifier("accessibility_desc_quick_settings", "string", SYSTEM_UI)
            if (id != 0) res.getString(id).trim('.', ' ') else null
        }.getOrNull()
    }

    private val finishHide = Runnable {
        val root = panel
        val lp = panelParams
        if (root != null && lp != null) {
            root.visibility = View.INVISIBLE
            lp.flags = lp.flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            lp.blurBehindRadius = 0
            runCatching { wm.updateViewLayout(root, lp) }
        }
        panelShown = false
        closing = false
    }

    private val actions = object : PanelActions {
        override fun close() = hidePanel()

        override fun launch(intent: Intent) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(
                    this@ControlCenterService,
                    "Action indisponible sur cet appareil",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            hidePanel()
        }

        override fun global(action: Int) {
            if (action == AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS ||
                action == AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            ) {
                // Évite que notre propre ouverture du volet système soit « interceptée ».
                ignoreShadeUntil = SystemClock.uptimeMillis() + 2500
            }
            hidePanel()
            handler.postDelayed({ performGlobalAction(action) }, 380)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        owner.start()
        instance = this
        addTrigger()
        ensurePanel()
    }

    /**
     * Dans une app, le système garde le swipe sur la barre d'état : le volet Samsung s'ouvre.
     * On le voit s'ouvrir, on affiche aussitôt notre panneau par-dessus et on referme le volet.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != SYSTEM_UI) return

        val cls = event.className?.toString().orEmpty()
        val text = event.text.joinToString(" ") { it?.toString().orEmpty() }
        recordSystemUiEvent("$cls | $text")

        if (!getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_INTERCEPT, true)) return
        if (panelShown || SystemClock.uptimeMillis() < ignoreShadeUntil) return
        if (looksLikeQuickSettings("$cls $text")) {
            ignoreShadeUntil = SystemClock.uptimeMillis() + 1500
            showPanel()
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
    }

    private fun recordSystemUiEvent(line: String) {
        recentSystemUiEvents.addFirst(line.take(120))
        while (recentSystemUiEvents.size > 8) recentSystemUiEvents.removeLast()
    }

    private fun looksLikeQuickSettings(haystack: String): Boolean {
        val h = haystack.lowercase()
        val label = quickSettingsLabel?.lowercase()
        return (label != null && label.isNotEmpty() && h.contains(label)) ||
            h.contains("quick settings") ||
            h.contains("réglages rapides") ||
            h.contains("quicksettings") ||
            h.contains("quickpanel")
    }

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::wm.isInitialized) {
            // Rotation, mode sombre… : on reconstruit le panneau avec les bonnes valeurs.
            destroyPanel()
            ensurePanel()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        owner.stop()
        instance = null
        super.onDestroy()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        destroyPanel()
        trigger?.let { runCatching { wm.removeView(it) } }
        trigger = null
    }

    // ---------- Zone de déclenchement (coin supérieur droit) ----------

    private fun addTrigger() {
        if (trigger != null) return
        val view = TriggerView(this) { showPanel() }
        val lp = WindowManager.LayoutParams(
            dp(120),
            dp(26),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
        }
        wm.addView(view, lp)
        trigger = view
    }

    // ---------- Panneau ----------

    /** Crée la fenêtre du panneau, invisible et non tactile, déjà prête à s'afficher. */
    private fun ensurePanel() {
        if (panel != null) return

        val ctrl = SystemControls(this)
        blurState.value = wm.isCrossWindowBlurEnabled
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val topInset = statusBarHeightDp() + 8.dp

        val root = PanelRoot(this) { hidePanel() }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)

        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ControlPanel(
                    visible = panelOpen.value,
                    blurAvailable = blurState.value,
                    dark = dark,
                    topInset = topInset,
                    controls = ctrl,
                    actions = actions,
                )
            }
        }
        root.addView(
            compose,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        root.visibility = View.INVISIBLE
        root.isFocusableInTouchMode = true

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            blurBehindRadius = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        panelOpen.value = false
        if (runCatching { wm.addView(root, lp) }.isSuccess) {
            panel = root
            panelParams = lp
            controls = ctrl
        } else {
            ctrl.dispose()
        }
    }

    private fun destroyPanel() {
        handler.removeCallbacks(finishHide)
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        panelParams = null
        controls?.dispose()
        controls = null
        panelShown = false
        closing = false
        panelOpen.value = false
    }

    fun showPanel() {
        if (panelShown && !closing) return
        ensurePanel()
        val root = panel ?: return
        val lp = panelParams ?: return

        handler.removeCallbacks(finishHide)
        closing = false
        panelShown = true

        val blur = wm.isCrossWindowBlurEnabled
        blurState.value = blur

        // Fenêtre tactile et focalisable (pour le bouton retour), déjà à moitié floutée.
        lp.flags = lp.flags and
            (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).inv()
        lp.blurBehindRadius = if (blur) 28 else 0
        root.visibility = View.VISIBLE
        runCatching { wm.updateViewLayout(root, lp) }

        panelOpen.value = true
        if (blur) animateBlur(lp, root, 52, 170)
        root.requestFocus()
        root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun hidePanel() {
        val root = panel ?: return
        if (!panelShown || closing) return
        closing = true
        panelOpen.value = false
        val lp = panelParams
        if (lp != null && lp.blurBehindRadius > 0) animateBlur(lp, root, 0, 240)
        handler.postDelayed(finishHide, 280)
    }

    private fun animateBlur(lp: WindowManager.LayoutParams, view: View, to: Int, durationMs: Long) {
        ValueAnimator.ofInt(lp.blurBehindRadius, to).apply {
            duration = durationMs
            addUpdateListener {
                lp.blurBehindRadius = it.animatedValue as Int
                if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, lp) }
            }
            start()
        }
    }

    // ---------- Utilitaires ----------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun statusBarHeightDp(): Dp {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val density = resources.displayMetrics.density
        val px = if (id > 0) resources.getDimensionPixelSize(id) else (24 * density).toInt()
        return (px / density).dp
    }
}

/** Petite vue invisible : détecte un swipe vers le bas et déclenche le panneau. */
private class TriggerView(context: Context, private val onSwipeDown: () -> Unit) : View(context) {
    private var downX = 0f
    private var downY = 0f
    private var fired = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                fired = false
            }
            MotionEvent.ACTION_MOVE -> if (!fired) {
                val dy = event.rawY - downY
                val dx = event.rawX - downX
                if (dy > 24 * resources.displayMetrics.density && dy > abs(dx)) {
                    fired = true
                    onSwipeDown()
                }
            }
        }
        return true
    }
}

/** Racine de la fenêtre du panneau : gère le bouton / geste retour. */
private class PanelRoot(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
