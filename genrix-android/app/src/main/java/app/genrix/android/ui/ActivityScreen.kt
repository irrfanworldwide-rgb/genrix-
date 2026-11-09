package app.genrix.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.genrix.android.local.ActivityLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the assistant did recently, newest first; kept in memory only. */
@Composable
fun ActivityScreen(entries: List<ActivityLog.Entry>, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Activity", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
        }
        Text(
            "What was done, not what was said. Cleared when the app closes.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (entries.isEmpty()) {
            Text("Nothing yet.", style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 24.dp))
            return
        }
        LazyColumn {
            items(entries, key = { it.at.toString() + it.what }) { entry ->
                ActivityRow(entry)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun ActivityRow(entry: ActivityLog.Entry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(entry.what, style = MaterialTheme.typography.titleMedium)
            Text(entry.result, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Pill(if (entry.local) "On phone" else "Gemini", if (entry.local) Accent else Warning, dot = false)
            Text(
                SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(entry.at)),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
