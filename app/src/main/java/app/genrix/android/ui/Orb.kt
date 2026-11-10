package app.genrix.android.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import app.genrix.android.assistant.Phase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val CYCLE_MS = 12_000
private const val BREATH_MS = 5_000

/**
 * The Genrix AI core: a glowing, slightly organic sphere with thin rings and a few drifting
 * particles. Its look follows the assistant's existing phase:
 *  - Off: slow breathing and a faint glow (a still frame if the phone has animations off, or
 *    when [idleMotion] is false, as in the floating orb over other apps).
 *  - Connecting: rings turn quickly, the core stays focused.
 *  - Listening: brighter, pulsing with [level].
 *  - Speaking: fluid, wave-like swelling with [level].
 *  - [executing]: one short energy burst when the assistant starts a command.
 *  - [error]: the colours cool towards amber.
 * Frames are drawn only while something is moving, and values are read in the draw step so
 * the composable itself does not recompose every frame.
 */
@Composable
fun Orb(
    phase: Phase,
    modifier: Modifier = Modifier,
    level: Float = 0f,
    executing: Boolean = false,
    error: Boolean = false,
    idleMotion: Boolean = true,
    rings: Boolean = false,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val motionAllowed = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val animate = motionAllowed && (phase != Phase.Off || idleMotion)

    val energy = animateFloatAsState(
        when (phase) {
            Phase.Off -> 0f
            Phase.Connecting -> 0.3f
            Phase.Listening -> 0.5f
            Phase.Speaking -> 0.9f
        },
        tween(700),
        label = "energy",
    )
    val voice = animateFloatAsState(level, spring(stiffness = Spring.StiffnessLow), label = "voice")
    val warn = animateFloatAsState(if (error) 1f else 0f, tween(500), label = "warn")
    val burst = remember { Animatable(1f) }
    LaunchedEffect(executing) {
        if (executing) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(700))
        }
    }

    val time: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "orb").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing)),
            label = "time",
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val breath: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }
    val spin = when (phase) {
        Phase.Off -> 0
        Phase.Listening -> 1
        Phase.Speaking -> 2
        Phase.Connecting -> 6
    }
    val path = remember { Path() }

    Canvas(modifier) {
        val c = center
        val t = time.value
        val e = energy.value
        val v = voice.value
        val w = warn.value
        val b = burst.value
        val br = breath.value
        val a = t * 2f * PI.toFloat()
        val base = size.minDimension / 2f * 0.52f * (1f + 0.06f * v + 0.025f * br)
        val wobble = 0.025f + 0.05f * e + 0.10f * v
        val cyan = lerp(GenrixColors.Cyan, GenrixColors.Warning, 0.6f * w)
        val violet = lerp(GenrixColors.Violet, GenrixColors.Error, 0.35f * w)
        val glow = GenrixColors.Glow

        // Ambient glow.
        drawCircle(
            Brush.radialGradient(listOf(glow.copy(alpha = 0.16f + 0.20f * e + 0.22f * v + 0.04f * br), Color.Transparent), c, base * 2.1f),
            radius = base * 2.1f,
            center = c,
        )

        // Thin rings with a bright arc that turns with the phase.
        val r1 = base * 1.28f
        val r2 = base * 1.46f
        drawCircle(cyan.copy(alpha = 0.10f + 0.10f * e), radius = r1, center = c, style = Stroke(1.2f * density))
        drawCircle(violet.copy(alpha = 0.07f + 0.07f * e), radius = r2, center = c, style = Stroke(1.2f * density))
        rotate(t * 360f * (1 + spin), c) {
            drawArc(
                cyan.copy(alpha = 0.25f + 0.65f * e),
                startAngle = 0f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(c.x - r1, c.y - r1),
                size = Size(r1 * 2f, r1 * 2f),
                style = Stroke(2f * density, cap = StrokeCap.Round),
            )
        }
        rotate(-t * 360f * (1 + spin / 2), c) {
            drawArc(
                violet.copy(alpha = 0.20f + 0.55f * e),
                startAngle = 180f,
                sweepAngle = 40f,
                useCenter = false,
                topLeft = Offset(c.x - r2, c.y - r2),
                size = Size(r2 * 2f, r2 * 2f),
                style = Stroke(1.6f * density, cap = StrokeCap.Round),
            )
        }

        // A few drifting particles, only while the assistant is active.
        if (phase != Phase.Off || e > 0.05f) {
            for (i in 0 until 8) {
                val sign = if (i % 2 == 0) 1 else -1
                val ang = a * sign * (1 + i % 3) + i * (2f * PI.toFloat() / 8f)
                val rr = base * (1.55f + 0.12f * sin(a * 2f + i))
                val alpha = (0.15f + 0.5f * e) * (0.5f + 0.5f * sin(a * 3f + i))
                drawCircle(
                    cyan.copy(alpha = alpha),
                    radius = (1.2f + (i % 3) * 0.5f) * density,
                    center = Offset(c.x + rr * cos(ang), c.y + rr * sin(ang)),
                )
            }
        }

        // Planet-style orbit rings, back half (drawn before the core).
        if (rings) {
            rotate(-18f + 3f * sin(a), c) {
                val rw = base * 3.0f
                val rh = base * 0.95f
                drawArc(cyan.copy(alpha = 0.55f + 0.3f * e), 180f, 180f, false, Offset(c.x - rw / 2f, c.y - rh / 2f), Size(rw, rh), style = Stroke(2f * density))
                drawArc(violet.copy(alpha = 0.40f + 0.2f * e), 180f, 180f, false, Offset(c.x - rw * 0.58f, c.y - rh * 0.62f), Size(rw * 1.16f, rh * 1.24f), style = Stroke(1.4f * density))
            }
        }

        // The core: an organic blob filled with drifting coloured light.
        path.rewind()
        val steps = 120
        for (i in 0..steps) {
            val th = i.toFloat() / steps * 2f * PI.toFloat()
            val r = base * (1f + wobble * (0.55f * sin(3f * th + 3f * a) + 0.30f * sin(5f * th - 2f * a) + 0.15f * sin(2f * th + 5f * a)))
            val x = c.x + r * cos(th)
            val y = c.y + r * sin(th)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        clipPath(path) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF12203A), GenrixColors.Background), c, base * 1.15f),
                radius = base * 1.25f,
                center = c,
            )
            val lights = arrayOf(cyan, violet, glow)
            for (i in 0..2) {
                val dir = if (i % 2 == 0) 1 else -1
                val ang = a * (i + 1) * dir + i * 2.1f
                val spot = Offset(c.x + base * 0.45f * cos(ang), c.y + base * 0.45f * sin(ang))
                drawCircle(
                    Brush.radialGradient(listOf(lights[i].copy(alpha = 0.9f), lights[i].copy(alpha = 0f)), spot, base * 0.95f),
                    radius = base * 1.25f,
                    center = c,
                )
            }
            // Thin inner energy ring.
            drawCircle(
                Color.White.copy(alpha = 0.10f + 0.12f * e),
                radius = base * (0.55f + 0.08f * sin(a * 3f)),
                center = c,
                style = Stroke(1f * density),
            )
            // Soft highlight, top left.
            val hl = Offset(c.x - base * 0.38f, c.y - base * 0.42f)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.28f else 0.5f), Color.Transparent), hl, base * 0.6f),
                radius = base * 1.25f,
                center = c,
            )
        }
        // Luminous rim.
        drawPath(
            path,
            Brush.sweepGradient(
                listOf(cyan.copy(alpha = 0.85f), Color.Transparent, violet.copy(alpha = 0.7f), Color.Transparent, cyan.copy(alpha = 0.85f)),
                c,
            ),
            style = Stroke(1.5f * density),
        )
        // Front half of the orbit rings, over the core.
        if (rings) {
            rotate(-18f + 3f * sin(a), c) {
                val rw = base * 3.0f
                val rh = base * 0.95f
                drawArc(cyan.copy(alpha = 0.55f + 0.3f * e), 0f, 180f, false, Offset(c.x - rw / 2f, c.y - rh / 2f), Size(rw, rh), style = Stroke(2f * density))
                drawArc(violet.copy(alpha = 0.40f + 0.2f * e), 0f, 180f, false, Offset(c.x - rw * 0.58f, c.y - rh * 0.62f), Size(rw * 1.16f, rh * 1.24f), style = Stroke(1.4f * density))
            }
        }
        // One burst when a command starts.
        if (b < 1f) {
            drawCircle(
                cyan.copy(alpha = 0.7f * (1f - b)),
                radius = base * (1.05f + 0.8f * b),
                center = c,
                style = Stroke((3f - 2f * b) * density),
            )
        }
    }
}
