package app.genrix.android.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.genrix.android.Diag
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Genrix's phone control (Android Accessibility). It only acts when a tool asks, never on
 * its own; risky taps wait for the user's Allow on [ControlOverlay]. Nothing it reads is
 * stored or logged.
 */
class ControlService : AccessibilityService() {
    // Lazy: Android can destroy the service before it ever connects.
    private val overlayLazy = lazy { ControlOverlay(this) }
    val overlay: ControlOverlay by overlayLazy

    // Numbers from the last ui_read, so "tap 4" means what the model just saw.
    @Volatile private var lastNodes: Map<Int, AccessibilityNodeInfo> = emptyMap()

    override fun onServiceConnected() {
        instance = this
        Diag.i("control: on")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        if (overlayLazy.isInitialized()) overlay.hideAll()
        Diag.i("control: off")
        super.onDestroy()
    }

    class Snapshot(val packageName: String, val appLabel: String, val elements: List<ScreenElement>, val more: Boolean) {
        val blocked get() = ControlRules.blocked(packageName)
    }

    fun foregroundPackage(): String = rootInActiveWindow?.packageName?.toString().orEmpty()

    /** Reads the app in front. Blocked apps come back without their contents. */
    fun snapshot(): Snapshot? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString().orEmpty()
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        if (ControlRules.blocked(pkg)) {
            lastNodes = emptyMap()
            return Snapshot(pkg, label, emptyList(), false)
        }
        val elements = mutableListOf<ScreenElement>()
        val nodes = mutableMapOf<Int, AccessibilityNodeInfo>()
        var more = false
        fun visit(node: AccessibilityNodeInfo, insideTappable: Boolean) {
            if (!node.isVisibleToUser) return
            if (node.isScrollable) more = true
            val tappable = node.isClickable || node.isEditable || node.isCheckable
            val own = ownLabel(node)
            val include = when {
                tappable -> true
                own.isNotEmpty() && !insideTappable -> true
                else -> false
            }
            if (include && elements.size < ScreenText.MAX_ELEMENTS) {
                val text = own.ifEmpty { childText(node) }.ifEmpty { node.viewIdResourceName?.substringAfterLast('/').orEmpty() }
                if (text.isNotEmpty() || node.isEditable) {
                    val number = elements.size + 1
                    elements += ScreenElement(number, role(node), text, if (node.isEditable) node.text?.toString().orEmpty() else "")
                    nodes[number] = node
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, insideTappable || tappable) }
        }
        visit(root, false)
        lastNodes = nodes
        return Snapshot(pkg, label, elements, more || elements.size >= ScreenText.MAX_ELEMENTS)
    }

    fun node(number: Int): AccessibilityNodeInfo? = lastNodes[number]?.takeIf { it.refresh() }

    /** The first visible node (screen order) that matches [test]. */
    fun findFirst(test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (!node.isVisibleToUser) return null
            if (test(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)?.let { return it }
            return null
        }
        return visit(root)
    }

    fun labelOf(node: AccessibilityNodeInfo): String = ownLabel(node).ifEmpty { childText(node) }

    fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        val bounds = Rect().also(node::getBoundsInScreen)
        return tap(bounds.exactCenterX(), bounds.exactCenterY())
    }

    fun type(node: AccessibilityNodeInfo, text: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun focusedField(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

    /** Scrolls the biggest scrollable area; "down" shows what is further down. */
    fun scroll(down: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        fun visit(node: AccessibilityNodeInfo) {
            if (node.isScrollable && node.isVisibleToUser) {
                val r = Rect().also(node::getBoundsInScreen)
                if (r.width() * r.height() > bestArea) {
                    best = node
                    bestArea = r.width() * r.height()
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        visit(root)
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (best?.performAction(action) == true) return true
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val (from, to) = if (down) metrics.heightPixels * 0.7f to metrics.heightPixels * 0.3f else metrics.heightPixels * 0.3f to metrics.heightPixels * 0.7f
        return swipe(x, from, x, to)
    }

    /**
     * The screen as a software bitmap, or null when Android refuses: before Android 11, or a
     * protected window (banking, payments and password screens come back as an error).
     */
    suspend fun screenshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { done ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val shot = result.hardwareBuffer.use { buffer ->
                            Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                        }
                        done.resume(shot)
                    }

                    override fun onFailure(errorCode: Int) {
                        Diag.w("control: screenshot refused ($errorCode)")
                        done.resume(null)
                    }
                },
            )
        }
    }

    /** Taps a screen point (click-by-sight). Callers check [nodeAt] and the risk first. */
    fun tapAt(x: Float, y: Float): Boolean = !overlay.confirming && tap(x, y)

    /** The deepest visible node under a screen point, if the app exposes one there. */
    fun nodeAt(x: Float, y: Float): AccessibilityNodeInfo? {
        val point = Rect()
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (!node.isVisibleToUser) return null
            node.getBoundsInScreen(point)
            if (!point.contains(x.toInt(), y.toInt())) return null
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(::visit)?.let { return it }
            return node
        }
        // Top window first, so Genrix's own overlay (Allow / Stop) is found when it is there.
        return windows.sortedByDescending { it.layer }.firstNotNullOfOrNull { it.root?.let(::visit) }
    }

    /** Waits until [packageName] is in front (after opening an app). */
    suspend fun waitFor(packageName: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (foregroundPackage() == packageName) return true
            delay(250)
        }
        return false
    }

    private fun tap(x: Float, y: Float) = swipe(x, y, x, y, durationMs = 60)

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun ownLabel(node: AccessibilityNodeInfo): String =
        (node.text ?: node.contentDescription ?: node.hintText)?.toString()?.trim().orEmpty()

    private fun childText(node: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        fun visit(n: AccessibilityNodeInfo) {
            if (parts.size >= 3) return
            ownLabel(n).takeIf { it.isNotEmpty() && n !== node }?.let { parts += it }
            for (i in 0 until n.childCount) n.getChild(i)?.let(::visit)
        }
        visit(node)
        return parts.joinToString(" · ")
    }

    private fun role(node: AccessibilityNodeInfo): String = when {
        node.isEditable -> "field"
        node.isCheckable -> if (node.isChecked) "switch on" else "switch off"
        node.isClickable -> "button"
        else -> "text"
    }

    companion object {
        @Volatile var instance: ControlService? = null
            private set
    }
}
