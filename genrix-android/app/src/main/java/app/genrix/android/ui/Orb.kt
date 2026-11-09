package app.genrix.android.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import app.genrix.android.assistant.Phase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Teal = Color(0xFF2F6BFF)
private val Jade = Color(0xFF1B2A6B)
private val Lime = Color(0xFFFF5FA8)
private val Violet = Color(0xFF9B5CFF)
private val Forest = Color(0xFF06060C)

/**
 * The assistant's presence: a soft, colour-shifting shape. Standby is one still frame (no
 * battery cost); listening breathes and swells with the voice [level] (0..1), thinking adds
 * a turning arc, speaking moves the most.
 */
@Composable
fun Orb(phase: Phase, modifier: Modifier = Modifier, level: Float = 0f) {
    val accent = Accent
    val highlight = if (isSystemInDarkTheme()) 0.30f else 0.55f
    val energy by animateFloatAsState(
        when (phase) {
            Phase.Off -> 0f
            Phase.Connecting -> 0.3f
            Phase.Listening -> 0.45f
            Phase.Speaking -> 0.85f
        },
        tween(700),
        label = "energy",
    )
    val voice by animateFloatAsState(level, spring(stiffness = Spring.StiffnessLow), label = "voice")
    val t = if (phase == Phase.Off) {
        0f
    } else {
        rememberInfiniteTransition(label = "orb").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "t",
        ).value
    }
    val lights = remember(accent) { listOf(Violet, Teal, Lime) }
    val path = remember { Path() }

    Canvas(modifier) {
        val c = center
        val base = size.minDimension / 2 * 0.56f * (1 + 0.06f * voice)
        val wobble = 0.025f + 0.05f * energy + 0.10f * voice
        val a = t * 2 * PI.toFloat()

        // Soft glow behind, brighter with the voice.
        drawCircle(
            Brush.radialGradient(listOf(Violet.copy(alpha = 0.22f + 0.25f * voice + 0.1f * energy), Color.Transparent), c, base * 1.9f),
            radius = base * 1.9f,
            center = c,
        )

        // The shape: a circle bent by three slow waves.
        path.reset()
        for (i in 0..POINTS) {
            val th = i * 2 * PI.toFloat() / POINTS
            val r = base * (
                1 + wobble * (
                    0.55f * sin(3 * th + 3 * a) +
                        0.30f * sin(5 * th - 2 * a) +
                        0.15f * sin(2 * th + 5 * a)
                    )
                )
            val x = c.x + r * cos(th)
            val y = c.y + r * sin(th)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        // Inside the shape, three coloured lights drift round each other, like a liquid.
        clipPath(path) {
            drawCircle(Brush.radialGradient(listOf(Jade, Forest), c, base * 1.1f), radius = base * 1.2f, center = c)
            lights.forEachIndexed { i, color ->
                val angle = a * (i + 1) * if (i % 2 == 0) 1 else -1
                val spot = Offset(c.x + base * 0.45f * cos(angle + i * 2.1f), c.y + base * 0.45f * sin(angle + i * 2.1f))
                drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), spot, base * 0.95f), radius = base * 1.2f, center = c)
            }
            // A light from the top left gives it depth.
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = highlight), Color.Transparent),
                    center = Offset(c.x - base * 0.35f, c.y - base * 0.4f),
                    radius = base * 0.9f,
                ),
                radius = base * 1.3f,
                center = c,
            )
        }

        // Thinking: an arc chasing round the outside.
        if (phase == Phase.Connecting) {
            val ring = base * 1.32f
            rotate(t * 360f * 8) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.White, Color.Transparent), c),
                    startAngle = 0f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(c.x - ring, c.y - ring),
                    size = Size(ring * 2, ring * 2),
                    style = Stroke(width = 3 * density),
                )
            }
        }
    }
}

private const val CYCLE_MS = 12_000
private const val POINTS = 96
