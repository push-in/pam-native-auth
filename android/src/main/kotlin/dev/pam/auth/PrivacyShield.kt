package dev.pam.auth

import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** A native cover. Removing it requires an explicit foreground authorization. */
internal class PrivacyShield(private val activity: FragmentActivity) : LifecycleEventObserver, AutoCloseable {
    private val root = activity.window.decorView as ViewGroup
    private val cover = FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Protected content"
        elevation = Float.MAX_VALUE
    }
    private val accessibility = java.util.WeakHashMap<View, Int>()
    private val layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        if (concealed) hideContentAccessibility()
    }
    private var closed = false
    val concealed: Boolean get() = !closed && cover.visibility == View.VISIBLE

    init {
        checkMainThread()
        // The system may capture recents before onPause draws the cover.
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(false)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        root.addView(cover, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        activity.lifecycle.addObserver(this)
        conceal()
    }

    fun conceal() {
        checkMainThread()
        if (closed) return
        cover.visibility = View.VISIBLE
        cover.bringToFront()
        hideContentAccessibility()
    }

    fun reveal(): Boolean {
        checkMainThread()
        if (closed || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        restoreContentAccessibility()
        cover.visibility = View.GONE
        return true
    }

    override fun onStateChanged(owner: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> conceal()
            Lifecycle.Event.ON_DESTROY -> close()
            else -> Unit
        }
    }

    override fun close() {
        checkMainThread()
        if (closed) return
        closed = true
        activity.lifecycle.removeObserver(this)
        root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        restoreContentAccessibility()
        root.removeView(cover)
    }

    private fun hideContentAccessibility() {
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child === cover) continue
            if (!accessibility.containsKey(child)) accessibility[child] = child.importantForAccessibility
            child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    private fun restoreContentAccessibility() {
        for ((view, importance) in accessibility) view.importantForAccessibility = importance
        accessibility.clear()
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper()) {
        "PrivacyShield requires the main thread"
    }
}
