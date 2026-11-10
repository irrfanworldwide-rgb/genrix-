package app.genrix.android.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.genrix.android.memory.MemoryRules
import app.genrix.android.memory.MemoryStore
import app.genrix.android.wake.WakeWordModel

private class Command(val title: String, val examples: String)

private val LOCAL = listOf(
    Command("Volume", "\"Volume 30 percent kar do\" · \"volume badhao\" · \"phone mute karo\""),
    Command("Music", "\"Music pause kar do\" · \"agla gaana\" · \"resume\""),
    Command("Flashlight", "\"Flashlight on karo\" · \"torch band karo\""),
    Command("Open apps", "\"YouTube kholo\" · \"open Calendar\" · \"Camera chalu karo\""),
    Command("Time, date, battery", "\"Kitne baje hai?\" · \"aaj ki date\" · \"kitni battery hai\""),
    Command("Timers and alarms", "\"5 minute ka timer\" · \"subah 6:30 ka alarm\""),
    Command("Stop", "\"Bas\" · \"stop\" · \"never mind\""),
)

private val TASKS = listOf(
    Command(
        "WhatsApp message",
        "\"WhatsApp Rahul ko bolo main 7 baje aaunga\". Asks if several Rahuls match, opens the chat with the message; " +
            "you check it and tap Send. With phone control on, it only says \"sent\" once the message shows in the chat.",
    ),
    Command(
        "Movie tickets",
        "\"Book 4 tickets for Pushpa tomorrow evening\". Opens your ticket app (BookMyShow, District or Paytm) or a web " +
            "search; you pick the show and seats and pay there. It never pays or books for you.",
    ),
    Command("Calls", "\"Call Mom\". Opens the dialer with the number; you tap Call."),
)

private val CLOUD = listOf(
    Command("Songs", "\"Play Kesariya on YouTube Music\""),
    Command("Your screen", "\"What's on my screen?\" · \"reply to this\" (needs phone control)"),
    Command("Questions", "Weather, facts, explanations, anything else"),
    Command("Reminders", "\"Remind me to call the bank at 5\""),
)

/** What can be done: on the phone, with the user's OK, or with Gemini. */
@Composable
fun AutomationsScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Automations", "Say \"${WakeWordModel.PHRASE}\", then the command. English, Hindi and Hinglish.")
        SearchField(query) { query = it }
        var filter by rememberSaveable { mutableStateOf("All") }
        ChipRow(listOf("All", "Local", "AI", "Approval"), filter) { filter = it }
        if (filter == "All") Routines(query)
        if (filter == "All" || filter == "Local") {
            Group("On this phone", "Works without internet and doesn't use your Gemini key.", Accent, "Local", LOCAL, query, mono = true, startOpen = true, icon = Icons.Filled.Phone)
        }
        if (filter == "All" || filter == "Approval") {
            Group("Tasks with your OK", "Gemini plans them; you finish anything that sends, calls or pays.", Warning, "Approval", TASKS, query, mono = false, icon = Icons.Filled.CheckCircle)
        }
        if (filter == "All" || filter == "AI") {
            Group("With Gemini", "Needs internet; uses your Gemini key.", AccentAlt, "AI", CLOUD, query, mono = false, icon = Icons.Filled.Star)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Search commands", color = MutedDim) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
            }
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The user's routines: say the name to run them; made by voice ("make a routine called ..."). */
@Composable
private fun Routines(query: String) {
    val context = LocalContext.current
    val store = remember { MemoryStore(context) }
    var routines by remember { mutableStateOf(store.routines()) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        routines = store.routines()
        onPauseOrDispose { }
    }
    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete routine?") },
            text = { Text("\"$name\" will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteRoutine(name).also { routines = store.routines() }
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
    val q = query.trim()
    val shown = routines.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.steps.any { step -> step.contains(q, ignoreCase = true) } }
    if (q.isNotEmpty() && shown.isEmpty()) return
    Section(null) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Routines", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text(
            "Say a routine's name to run its steps. Make one by voice: \"make a routine called good night: alarm at 6:30, what's on tomorrow\".",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        shown.forEach { r ->
            InfoRow(r.name, r.steps.joinToString(" · ")) {
                if (r !== MemoryRules.GOOD_MORNING) {
                    TextButton(onClick = { pendingDelete = r.name }) { Text("Delete") }
                }
            }
        }
    }
}

/** A category of commands: always visible as a header, expandable to its commands; a search opens the matches. */
@Composable
private fun Group(
    title: String,
    detail: String,
    color: Color,
    badge: String,
    commands: List<Command>,
    query: String,
    mono: Boolean,
    startOpen: Boolean = false,
    icon: ImageVector? = null,
) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    val q = query.trim()
    val shown = if (q.isEmpty()) commands else commands.filter { it.title.contains(q, ignoreCase = true) || it.examples.contains(q, ignoreCase = true) }
    if (q.isNotEmpty() && shown.isEmpty()) return
    val expanded = open || q.isNotEmpty()
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Section(null) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconTile(icon)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(8.dp))
            Pill(badge, color)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Muted, modifier = Modifier.rotate(angle))
        }
        AnimatedVisibility(expanded) {
            Column {
                shown.forEach { command ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(command.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            command.examples,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = if (mono) GenrixFonts.Mono else GenrixFonts.Ui),
                            color = Muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
