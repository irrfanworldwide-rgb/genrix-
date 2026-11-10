package app.genrix.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.genrix.android.local.ActivityLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the assistant did recently, newest first; kept in memory only. */
@Composable
fun ActivityScreen(entries: List<ActivityLog.Entry>, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("Activity", "What was done, not what was said. Cleared when the app closes.") {
            if (entries.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
        }
        Spacer(Modifier.height(16.dp))
        if (entries.isEmpty()) {
            EmptyActivity()
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.at.toString() + it.what }) { entry ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Surface, MaterialTheme.shapes.medium)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                ) { ActivityRow(entry) }
            }
        }
    }
}

@Composable
private fun EmptyActivity() {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).background(Raised, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.List, contentDescription = null, tint = Muted)
        }
        Text("Nothing yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Commands you run show up here, newest first.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun ActivityRow(entry: ActivityLog.Entry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (entry.local) Accent else AccentAlt, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.what, style = MaterialTheme.typography.titleMedium)
            Text(entry.result, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Pill(if (entry.local) "On phone" else "Gemini", if (entry.local) Accent else AccentAlt, dot = false)
            Text(
                SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(entry.at)),
                style = MaterialTheme.typography.labelMedium,
                color = MutedDim,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
