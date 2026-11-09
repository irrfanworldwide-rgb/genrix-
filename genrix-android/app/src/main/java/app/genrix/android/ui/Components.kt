package app.genrix.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = Muted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Surface, MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            content = content,
        )
    }
}

/** One setting: a title, a plain-language explanation and a switch. */
@Composable
fun SwitchRow(title: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

/** A row of text with an optional trailing control. */
@Composable
fun InfoRow(title: String, detail: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
