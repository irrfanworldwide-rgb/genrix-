package app.genrix.android.control

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.genrix.android.Branding
import app.genrix.android.Diag
import app.genrix.android.task.TaskTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Genrix's own on-screen controls while it uses the phone: a "using your phone · Stop"
 * strip, and the Allow / Cancel card for risky taps. Only a real finger can press them;
 * ui_click never touches Genrix's own windows.
 */
class ControlOverlay(private val service: AccessibilityService) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var strip: View? = null
    private var card: View? = null
    private val hideStrip = Runnable { remove(strip).also { strip = null } }

    /** True while the Allow card is up; no taps by coordinates may run then. */
    @Volatile var confirming = false
        private set

    /** Set by the Stop button; cleared when a new voice session starts. */
    @Volatile var stopped = false

    /** Shows the strip for a few seconds after each action. */
    fun showActive() = main.post {
        if (strip == null) {
            strip = row(
                text("${Branding.ASSISTANT_NAME} is using your phone", 14f),
                button("Stop") {
                    stopped = true
                    Diag.i("control: stopped by the user")
                    remove(strip).also { strip = null }
                },
            ).also { add(it, Gravity.TOP) }
        }
        main.removeCallbacks(hideStrip)
        main.postDelayed(hideStrip, STRIP_MS)
    }

    private val oneCard = Mutex()
    @Volatile private var pending: CompletableDeferred<Boolean>? = null

    /**
     * Asks the user to Allow [what]; false on Cancel, after 30 s, when the session ends or
     * while the phone is locked (the card can't be tapped over the lock screen). One card at
     * a time: a second risky tap waits for the first answer.
     */
    suspend fun confirm(what: String): Boolean = oneCard.withLock {
        if (service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            Diag.i("control: risky tap cancelled (phone locked)")
            return@withLock false
        }
        val answer = CompletableDeferred<Boolean>().also { pending = it }
        TaskTracker.confirming(what)
        val box = withContext(Dispatchers.Main) {
            confirming = true
            LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(PAD, PAD, PAD, PAD)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(11, 17, 32))
                    cornerRadius = 36f
                    setStroke(3, Color.rgb(100, 217, 255))
                }
                addView(text("${Branding.ASSISTANT_NAME} wants to:", 13f))
                addView(text(what, 17f))
                addView(
                    row(
                        button("Cancel") { answer.complete(false) },
                        button("Allow") { answer.complete(true) },
                    ),
                )
                card = this
                add(this, Gravity.BOTTOM)
            }
        }
        val allowed = try {
            withTimeoutOrNull(CONFIRM_MS) { answer.await() } ?: false
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                remove(box)
                if (card === box) card = null
                confirming = false
            }
            pending = null
        }
        Diag.i("control: risky tap ${if (allowed) "allowed" else "cancelled"}")
        TaskTracker.confirmed(allowed)
        allowed
    }

    /**
     * Hands one step to the user ("Enter your UPI PIN and pay"): a card at the top, so it
     * doesn't cover the app's own buttons, with Done and Stop. True on Done, false on Stop,
     * when the session ends or after [HANDOVER_MS]. The phone stays awake meanwhile.
     */
    suspend fun handover(what: String): Boolean = oneCard.withLock {
        val answer = CompletableDeferred<Boolean>().also { pending = it }
        val box = withContext(Dispatchers.Main) {
            LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(PAD, PAD / 2, PAD, PAD / 2)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(11, 17, 32))
                    cornerRadius = 36f
                    setStroke(3, Color.rgb(255, 198, 109))
                }
                addView(text("Your turn — ${Branding.ASSISTANT_NAME} waits:", 13f))
                addView(text(what, 16f))
                addView(
                    row(
                        button("Stop") { answer.complete(false) },
                        button("Done") { answer.complete(true) },
                    ),
                )
                card = this
                add(this, Gravity.TOP)
            }
        }
        val done = try {
            withTimeoutOrNull(HANDOVER_MS) { answer.await() } ?: false
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                remove(box)
                if (card === box) card = null
            }
            pending = null
        }
        Diag.i("control: handover ${if (done) "done" else "stopped"}")
        done
    }

    /** The session ended: a card still waiting counts as Cancel. */
    fun cancelPending() {
        pending?.complete(false)
    }

    fun hideAll() = main.post {
        remove(strip)
        remove(card)
        strip = null
        card = null
    }

    private fun add(view: View, gravity: Int) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Keeps the screen on while Genrix works in an app or waits for Allow, so the phone
            // doesn't lock halfway through a task (a 30 s screen timeout ends a ticket booking).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            y = 48
        }
        runCatching { windows.addView(view, params) }.onFailure { Diag.w("control: overlay failed") }
    }

    private fun remove(view: View?) {
        if (view?.isAttachedToWindow == true) windows.removeView(view)
    }

    private fun text(value: String, size: Float) = TextView(service).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(8, 8, 8, 8)
    }

    private fun button(label: String, onTap: () -> Unit) = Button(service).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onTap() }
    }

    private fun row(vararg views: View) = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        setPadding(PAD / 2, 0, PAD / 2, 0)
        setBackgroundColor(Color.argb(230, 16, 22, 30))
        views.forEach(::addView)
    }

    private companion object {
        const val PAD = 32
        const val STRIP_MS = 60_000L // the user may be answering a question mid-task
        const val CONFIRM_MS = 30_000L
        const val HANDOVER_MS = 150_000L // under the session's 3-minute idle stop
    }
}
