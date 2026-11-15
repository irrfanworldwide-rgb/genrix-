package app.genrix.android.ui

import androidx.compose.foundation.layout.height
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import app.genrix.android.Branding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A titled group of rows on a raised card. */
@Composable
fun Section(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(3.dp, 18.dp).background(Accent, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Surface, MaterialTheme.shapes.medium)
                .border(1.dp, glowBrush(), MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            content = content,
        )
    }
}

/** One setting: a title, a plain-language explanation and a switch. */
@Composable
fun SwitchRow(title: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        rowIcon(title)?.let { (icon, color) -> IconCircle(icon, color, size = 48.dp); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange, colors = genrixSwitchColors())
    }
}

/** A row of text with an optional trailing control. */
@Composable
fun InfoRow(title: String, detail: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        rowIcon(title)?.let { (icon, color) -> IconCircle(icon, color, size = 48.dp); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A small rounded label, e.g. "Standby" or "On phone". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, dot: Boolean = true) {
    Row(
        modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Material's microphone glyph (not in the core icon set). */
val MicIcon: ImageVector by lazy {
    ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 14f)
            curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
            lineTo(15f, 5f)
            curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
            reflectiveCurveTo(9f, 3.34f, 9f, 5f)
            verticalLineToRelative(6f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
            close()
            moveTo(17.3f, 11f)
            curveToRelative(0f, 3f, -2.54f, 5.1f, -5.3f, 5.1f)
            reflectiveCurveTo(6.7f, 14f, 6.7f, 11f)
            lineTo(5f, 11f)
            curveToRelative(0f, 3.41f, 2.72f, 6.23f, 6f, 6.72f)
            lineTo(11f, 21f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-3.28f)
            curveToRelative(3.28f, -0.48f, 6f, -3.3f, 6f, -6.72f)
            horizontalLineToRelative(-1.7f)
            close()
        }
    }.build()
}

/** The app's switch: cyan when on, quiet and bordered when off. */
@Composable
fun genrixSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = GenrixColors.Glow,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Muted,
    uncheckedTrackColor = Raised,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

/** A screen's title, an optional one-line description and an optional control on the right. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, planet: Boolean = false, trailing: @Composable (() -> Unit)? = null) {
    val limb = GenrixColors.Cyan
    val haze = GenrixColors.Glow
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (planet) {
                    Modifier.drawBehind {
                        val r = size.width * 0.62f
                        val c = Offset(size.width, size.height * 1.4f)
                        drawCircle(
                            Brush.radialGradient(0.80f to Color.Transparent, 0.93f to haze.copy(alpha = 0.28f), 0.985f to limb.copy(alpha = 0.75f), 1f to Color.Transparent, center = c, radius = r),
                            radius = r,
                            center = c,
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (trailing != null) trailing()
    }
}

/** Clickable with a soft spring "press" instead of a ripple, for cards and buttons. */
@Composable
fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val factor by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this.scale(factor).clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
}

/** The soft cyan to violet edge used on glass surfaces. */
@Composable
fun glowBrush(): Brush = Brush.linearGradient(listOf(Accent.copy(alpha = 0.45f), AccentAlt.copy(alpha = 0.28f), Accent.copy(alpha = 0.10f)))

/** A rounded icon on a faint cyan/violet gradient. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .size(size)
            .background(Brush.linearGradient(listOf(Accent.copy(alpha = 0.24f), AccentAlt.copy(alpha = 0.24f))), shape)
            .border(1.dp, Accent.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(size * 0.5f))
    }
}

private fun rowIcon(title: String): Pair<ImageVector, Color>? {
    val t = title.lowercase()
    return when {
        "key" in t -> Icons.Filled.Lock to GenrixColors.Glow
        "assistant" in t -> Icons.Filled.Home to GenrixColors.Glow
        "talk" in t -> Icons.Filled.Call to GenrixColors.Glow
        "wave" in t -> Icons.Filled.Face to Color(0xFFFF6FE0)
        "orb" in t || "any app" in t -> Icons.Filled.Star to GenrixColors.Violet
        "control" in t || "on this phone" in t -> Icons.Filled.Build to GenrixColors.Glow
        "wake" in t || "notif" in t -> Icons.Filled.Notifications to GenrixColors.Warning
        "speech" in t -> Icons.Filled.Search to GenrixColors.Glow
        "learn" in t || "knows" in t -> Icons.Filled.Person to GenrixColors.Violet
        "background" in t || "standby" in t -> Icons.Filled.Refresh to GenrixColors.Glow
        "gemini" in t -> Icons.Filled.Star to GenrixColors.Violet
        t == Branding.ASSISTANT_NAME.lowercase() -> Icons.Filled.Info to GenrixColors.Cyan
        else -> null
    }
}

/** A round icon with a coloured ring and a soft tint, as in the storyboard. */
@Composable
fun IconCircle(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 54.dp) {
    Box(
        modifier
            .size(size)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.08f))), CircleShape)
            .border(1.5.dp, color.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.48f))
    }
}

/** Soft violet and blue light in the corners behind everything (dark mode only). */
@Composable
fun Modifier.nebula(): Modifier {
    if (!isSystemInDarkTheme()) return this
    return this.drawBehind {
        drawRect(Brush.radialGradient(listOf(GenrixColors.Violet.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.05f), size.width))
        drawRect(Brush.radialGradient(listOf(GenrixColors.Glow.copy(alpha = 0.20f), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.95f), size.width * 1.1f))
    }
}

/** A row of equal pill filters; the selected one glows blue. */
@Composable
fun ChipRow(options: List<String>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            val on = option == selected
            val shape = RoundedCornerShape(50)
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(
                        if (on) Brush.horizontalGradient(listOf(Color(0xFF00B8FF), Color(0xFF2A8BFF))) else Brush.horizontalGradient(listOf(Surface, Surface)),
                        shape,
                    )
                    .border(1.dp, if (on) Color.Transparent else glowBrush(), shape)
                    .pressScale { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (on && option == "All") "✦ $option" else option,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A live waveform: bars that move while [active], and swell with the microphone or voice [level]. */
@Composable
fun Waveform(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val cyan = Accent
    val violet = AccentAlt
    val time = if (active) {
        rememberInfiniteTransition(label = "wave").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "phase",
        )
    } else {
        null
    }
    Canvas(modifier) {
        val bars = 30
        val step = size.width / bars
        val phase = (time?.value ?: 0f) * 2f * PI.toFloat()
        val lv = level.coerceIn(0f, 1f)
        for (i in 0 until bars) {
            val wave = 0.5f + 0.5f * sin(phase + i * 0.55f)
            val h = size.height * (0.10f + (if (active) 0.15f + 0.75f * lv else 0f) * wave)
            val x = step * (i + 0.5f)
            drawLine(
                if (i % 2 == 0) cyan else violet,
                Offset(x, center.y - h / 2f),
                Offset(x, center.y + h / 2f),
                strokeWidth = 3f * density,
                cap = StrokeCap.Round,
            )
        }
    }
}
