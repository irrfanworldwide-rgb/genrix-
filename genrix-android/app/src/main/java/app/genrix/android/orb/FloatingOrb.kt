package app.genrix.android.orb

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.genrix.android.Branding
import app.genrix.android.Diag
import app.genrix.android.MainActivity
import app.genrix.android.assistant.Phase
import app.genrix.android.assistant.VoiceSession
import app.genrix.android.data.Settings
import app.genrix.android.ui.Orb
import app.genrix.android.ui.GenrixTheme
import kotlin.math.roundToInt

/**
 * Genrix's orb floating over every app, so it can be used without opening Genrix: tap to talk
 * (tap again to stop, or to cut in while it speaks), double-tap to type, drag to move; it
 * settles at the nearest side. Hidden while Genrix's own screen is open, which has the big orb.
 */
object FloatingOrb {
    private var host: OverlayHost? = null
    private var params: WindowManager.LayoutParams? = null

    /** True while Genrix's own screen is in front. */
    @Volatile var appVisible = false

    fun allowed(context: Context) = AndroidSettings.canDrawOverlays(context)

    /** Shows or hides the orb to match the setting, the permission and whether the app is open. */
    fun sync(context: Context) {
        val app = context.applicationContext
        val want = Settings(app).floatingOrb && allowed(app) && !appVisible
        if (want && host == null) show(app) else if (!want && host != null) hide(app)
    }

    private fun show(context: Context) {
        val windows = context.getSystemService(WindowManager::class.java)
        val size = (ORB_DP * context.resources.displayMetrics.density).roundToInt()
        val settings = Settings(context)
        val screen = context.resources.displayMetrics
        val p = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.orbX.takeIf { it >= 0 } ?: (screen.widthPixels - size)
            y = settings.orbY.takeIf { it >= 0 } ?: (screen.heightPixels * 0.6f).roundToInt()
        }
        val orb = OverlayHost(context)
        orb.open {
            val session = VoiceSession.get(context)
            val state by session.state.collectAsStateWithLifecycle()
            val level by session.level.collectAsStateWithLifecycle()
            GenrixTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "${Branding.ASSISTANT_NAME}: tap to talk, double-tap to type" }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { tap(context) }, onDoubleTap = { TypeBox.show(context) })
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { settle(context) },
                                onDrag = { change, delta ->
                                    change.consume()
                                    move(context, delta.x.roundToInt(), delta.y.roundToInt())
                                },
                            )
                        },
                ) {
                    Orb(state.phase, Modifier.fillMaxSize(), level)
                }
            }
        }
        runCatching { windows.addView(orb.view, p) }
            .onSuccess {
                host = orb
                params = p
                Diag.i("orb: shown")
            }
            .onFailure { Diag.w("orb: couldn't show (${it.javaClass.simpleName})") }
    }

    private fun hide(context: Context) {
        val orb = host ?: return
        runCatching { context.getSystemService(WindowManager::class.java).removeView(orb.view) }
        orb.close()
        host = null
        params = null
        TypeBox.hide(context)
        Diag.i("orb: hidden")
    }

    private fun tap(context: Context) {
        val session = VoiceSession.get(context)
        val micAllowed = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when {
            !micAllowed || !session.state.value.hasKey -> openApp(context)
            !session.active -> session.start()
            session.state.value.phase == Phase.Speaking -> session.interrupt("orb")
            else -> session.stop("orb tap")
        }
    }

    private fun move(context: Context, dx: Int, dy: Int) {
        val p = params ?: return
        val screen = context.resources.displayMetrics
        p.x = (p.x + dx).coerceIn(0, screen.widthPixels - p.width)
        p.y = (p.y + dy).coerceIn(0, screen.heightPixels - p.height)
        update(context, p)
    }

    /** Slides to the nearest side and remembers the spot. */
    private fun settle(context: Context) {
        val p = params ?: return
        val width = context.resources.displayMetrics.widthPixels
        p.x = if (p.x + p.width / 2 < width / 2) 0 else width - p.width
        update(context, p)
        Settings(context).apply {
            orbX = p.x
            orbY = p.y
        }
    }

    private fun update(context: Context, p: WindowManager.LayoutParams) {
        val view = host?.view ?: return
        runCatching { context.getSystemService(WindowManager::class.java).updateViewLayout(view, p) }
    }

    /** The app asks for the mic or the key; the orb can't. */
    fun openApp(context: Context) {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val ORB_DP = 64
}
