package app.genrix.android.orb

import android.content.Context
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A Compose view that can live in a window Genrix adds over other apps. Compose needs a
 * lifecycle and a saved-state owner, which an Activity normally provides; this gives it its
 * own, alive from [open] until [close]. [onBack] runs when Back is pressed in a focusable window.
 */
class OverlayHost(context: Context, private val onBack: (() -> Unit)? = null) : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    val view: FrameLayout = object : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && onBack != null) {
                if (event.action == KeyEvent.ACTION_UP) onBack.invoke()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    fun open(content: @Composable () -> Unit) {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.addView(ComposeView(view.context).apply { setContent(content) })
    }

    fun close() {
        registry.currentState = Lifecycle.State.DESTROYED
        view.removeAllViews()
    }
}
