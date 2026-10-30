package com.bdesign.controlcenter

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
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
    }

    private lateinit var wm: WindowManager
    private val owner = OverlayLifecycleOwner()
    private val handler = Handler(Looper.getMainLooper())
    private val panelOpen = mutableStateOf(true)

    private var trigger: View? = null
    private var panel: PanelRoot? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var controls: SystemControls? = null
    private var closing = false
    private var blurOn = false

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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        removePanel()
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
        removePanel()
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

    fun showPanel() {
        if (panel != null) return

        val ctrl = SystemControls(this)
        controls = ctrl
        closing = false
        panelOpen.value = true
        blurOn = wm.isCrossWindowBlurEnabled
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
                    blurAvailable = blurOn,
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

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            blurBehindRadius = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        wm.addView(root, lp)
        panel = root
        panelParams = lp

        root.isFocusableInTouchMode = true
        root.requestFocus()
        if (blurOn) animateBlur(lp, root, 48)
        root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun hidePanel() {
        val root = panel ?: return
        if (closing) return
        closing = true
        panelOpen.value = false
        val lp = panelParams
        if (blurOn && lp != null) animateBlur(lp, root, 0)
        handler.postDelayed({ removePanel() }, 280)
    }

    private fun removePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        panelParams = null
        controls?.dispose()
        controls = null
        closing = false
    }

    private fun animateBlur(lp: WindowManager.LayoutParams, view: View, to: Int) {
        ValueAnimator.ofInt(lp.blurBehindRadius, to).apply {
            duration = 260
            addUpdateListener {
                lp.blurBehindRadius = it.animatedValue as Int
                if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, lp) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {}
            })
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
