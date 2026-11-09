package app.genrix.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.padding(top = 12.dp)) {
            Text("Automations", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Say \"${WakeWordModel.PHRASE}\", then the command. English, Hindi and Hinglish.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Routines()
        Group("On this phone", "Works without internet and doesn't use your Gemini key.", Accent, "Local", LOCAL)
        Group("Tasks with your OK", "Gemini plans them; you finish anything that sends, calls or pays.", Warning, "Needs you", TASKS)
        Group("With Gemini", "Needs internet; uses your Gemini key.", Warning, "Gemini", CLOUD)
        Spacer(Modifier.height(8.dp))
    }
}

/** The user's routines: say the name to run them; made by voice ("make a routine called …"). */
@Composable
private fun Routines() {
    val context = LocalContext.current
    val store = remember { MemoryStore(context) }
    var routines by remember { mutableStateOf(store.routines()) }
    LifecycleResumeEffect(Unit) {
        routines = store.routines()
        onPauseOrDispose { }
    }
    Section(null) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Routines", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text(
            "Say a routine's name to run its steps. Make one by voice: \"make a routine called good night: alarm at 6:30, what's on tomorrow\".",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        routines.forEach { r ->
            InfoRow(r.name, r.steps.joinToString(" · ")) {
                if (r !== MemoryRules.GOOD_MORNING) {
                    TextButton(onClick = { store.deleteRoutine(r.name).also { routines = store.routines() } }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, detail: String, color: Color, badge: String, commands: List<Command>) {
    Section(null) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Pill(badge, color)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        commands.forEach { InfoRow(it.title, it.examples) }
    }
}
