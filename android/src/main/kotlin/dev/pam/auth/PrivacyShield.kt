package dev.pam.auth

import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** A native cover. Removing it requires an explicit foreground authorization. */
internal class PrivacyShield(private val activity: FragmentActivity) : DefaultLifecycleObserver, AutoCloseable {
    private val root = activity.window.decorView as ViewGroup
    private val cover = FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Protected content"
        elevation = Float.MAX_VALUE
    }
    private var closed = false
    val concealed: Boolean get() = !closed && cover.visibility == View.VISIBLE

    init {
        checkMainThread()
        // The system may capture recents before onPause draws the cover.
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(false)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        root.addView(cover, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        activity.lifecycle.addObserver(this)
        conceal()
    }

    fun conceal() {
        checkMainThread()
        if (closed) return
        cover.visibility = View.VISIBLE
        cover.bringToFront()
    }

    fun reveal(): Boolean {
        checkMainThread()
        if (closed || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        cover.visibility = View.GONE
        return true
    }

    override fun onPause(owner: LifecycleOwner) = conceal()
    override fun onStop(owner: LifecycleOwner) = conceal()
    override fun onDestroy(owner: LifecycleOwner) = close()

    override fun close() {
        checkMainThread()
        if (closed) return
        closed = true
        activity.lifecycle.removeObserver(this)
        root.removeView(cover)
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper()) {
        "PrivacyShield requires the main thread"
    }
}
