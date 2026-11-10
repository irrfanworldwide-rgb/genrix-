package app.genrix.android.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.genrix.android.Branding
import app.genrix.android.assistant.AssistantViewModel
import app.genrix.android.data.Settings as AppSettings
import app.genrix.android.memory.MemoryStore
import app.genrix.android.notify.NotificationWatch
import app.genrix.android.orb.FloatingOrb
import app.genrix.android.wake.WakeWordModel

@Composable
fun SettingsScreen(assistant: AssistantViewModel) {
    val context = LocalContext.current
    val wakeOn by assistant.wakeWord.collectAsStateWithLifecycle()
    var localOn by remember { mutableStateOf(assistant.localCommands) }
    var networkOn by remember { mutableStateOf(assistant.networkSpeech) }
    var engine by remember { mutableStateOf(assistant.speechEngine) }
    var batteryExempt by remember { mutableStateOf(assistant.batteryExempt) }
    LifecycleResumeEffect(Unit) {
        batteryExempt = assistant.batteryExempt
        engine = assistant.speechEngine
        onPauseOrDispose { }
    }
    val open = { action: String -> context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader("Settings", "Voice, floating orb, PC bridge and permissions.")
        Row(
            Modifier.fillMaxWidth().background(Surface, MaterialTheme.shapes.large).border(1.dp, glowBrush(), MaterialTheme.shapes.large).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).background(Brush.linearGradient(listOf(Accent.copy(alpha = 0.3f), AccentAlt.copy(alpha = 0.3f))), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("G", style = TextStyle(brush = Brush.linearGradient(listOf(Accent, AccentAlt)), fontSize = 26.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(Branding.ASSISTANT_NAME, style = MaterialTheme.typography.titleLarge)
                Text(Branding.DESCRIPTOR, style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        }

        Section("Gemini") {
            InfoRow("API key", "Your own key, encrypted with Android Keystore on this phone. Used only for requests the phone can't handle. Gemini usage counts against your key's limits.") {
                TextButton(onClick = assistant::forgetKey) { Text("Replace") }
            }
            InfoRow("Default assistant", "Long-press Home or swipe from a corner to talk.") {
                TextButton(onClick = { open(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) }) { Text("Open") }
            }
        }

        Section("Conversation") {
            var talkOn by remember { mutableStateOf(assistant.talkOver) }
            SwitchRow(
                "Talk over ${Branding.ASSISTANT_NAME}",
                "Speak while it answers to cut it off, also on the loudspeaker. An echo canceller on this phone removes its own voice first. Turn off if it cuts itself off.",
                talkOn,
            ) {
                talkOn = it
                assistant.talkOver = it
            }
            var waveOn by remember { mutableStateOf(assistant.waveToStop) }
            SwitchRow(
                "Wave to interrupt",
                "Wave a hand over the top of the phone while ${Branding.ASSISTANT_NAME} speaks to cut it off. With headphones you can also just talk over it.",
                waveOn,
            ) {
                waveOn = it
                assistant.waveToStop = it
            }
        }

        Section("Floating orb") {
            val prefs = remember { AppSettings(context) }
            var orbOn by remember { mutableStateOf(prefs.floatingOrb && FloatingOrb.allowed(context)) }
            LifecycleResumeEffect(Unit) {
                orbOn = prefs.floatingOrb && FloatingOrb.allowed(context)
                onPauseOrDispose { }
            }
            SwitchRow(
                "Use ${Branding.ASSISTANT_NAME} over any app",
                "A small orb floats over your apps. Tap to talk, double-tap to type, drag to move. " +
                    "Needs \"Display over other apps\"; it hides while ${Branding.ASSISTANT_NAME} is open.",
                orbOn,
            ) { on ->
                prefs.floatingOrb = on
                orbOn = on && FloatingOrb.allowed(context)
                if (on && !FloatingOrb.allowed(context)) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                FloatingOrb.sync(context)
            }
        }

        Section("PC bridge") {
            PcBridgeForm()
        }

        Section("Notifications") {
            var notifyOn by remember { mutableStateOf(NotificationWatch.allowed(context)) }
            LifecycleResumeEffect(Unit) {
                notifyOn = NotificationWatch.allowed(context)
                onPauseOrDispose { }
            }
            InfoRow(
                "What did I miss?",
                (if (notifyOn) "On. " else "Off. ") +
                    "Ask \"what did I miss?\" and hear messages from people first. Notifications stay in memory on this " +
                    "phone; Gemini gets a short summary only when you ask, with codes hidden.",
            ) {
                TextButton(onClick = { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text(if (notifyOn) "Change" else "Turn on") }
            }
        }

        Section("Wake word") {
            if (assistant.wakeWordAvailable) {
                SwitchRow(
                    "Say \"${WakeWordModel.PHRASE}\"",
                    "Heard on this phone only. Test model: not for release builds.",
                    wakeOn,
                ) { assistant.setWakeWord(it) }
                VoiceSetupRow(
                    saved = assistant.voiceSaved,
                    record = assistant::recordVoiceSample,
                    onSave = assistant::saveVoice,
                    onForget = assistant::forgetVoice,
                )
            } else {
                InfoRow("Not in this build", "The wake word ships once ${Branding.ASSISTANT_NAME} has its own model.")
            }
        }

        Section("Local commands") {
            SwitchRow(
                "Handle simple commands on the phone",
                "Volume, music, flashlight, apps, time, timers and alarms run here, without Gemini.",
                localOn,
            ) {
                localOn = it
                assistant.localCommands = it
            }
            SwitchRow(
                "Use the phone's speech service",
                "Better Hinglish, but sends what you say after the wake word to that service (usually Google) and needs internet. Off: speech stays on this phone.",
                networkOn,
                enabled = localOn,
            ) {
                networkOn = it
                assistant.networkSpeech = it
                engine = assistant.speechEngine
            }
            InfoRow("Speech heard by", engineText(engine))
        }

        Section("Learning") {
            var learnOn by remember { mutableStateOf(assistant.learning) }
            var learned by remember { mutableStateOf(assistant.learnedSummary()) }
            SwitchRow(
                "Learn from how I use ${Branding.ASSISTANT_NAME}",
                "Kept only on this phone: which commands you use and when, and your own phrases once Gemini has understood them. Never contacts.",
                learnOn,
            ) {
                learnOn = it
                assistant.learning = it
            }
            InfoRow("What it knows", learned) {
                TextButton(onClick = {
                    assistant.forgetLearned()
                    learned = assistant.learnedSummary()
                }) { Text("Forget") }
            }
        }

        Section("Permissions") {
            ControlCard(Modifier.padding(vertical = 8.dp), showWhenOn = true)
            InfoRow("Background running", if (batteryExempt) "Allowed. The phone won't stop the wake word." else "The phone may stop the wake word to save battery. Allow ${Branding.ASSISTANT_NAME} under Battery → Unrestricted.") {
                TextButton(onClick = { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) { Text("Open") }
            }
        }

        Section("Privacy") {
            InfoRow(
                "What leaves the phone",
                "The wake word and local commands stay on this phone. Gemini gets only requests the phone can't handle. " +
                    "Nothing is recorded. The diagnostic log keeps timings and states, never your words.",
            )
            InfoRow("Standby", "Between requests only the wake word runs; Gemini is disconnected.")
            val memory = remember { MemoryStore(context) }
            var notes by remember { mutableStateOf(memory.notes().size) }
            LifecycleResumeEffect(Unit) {
                notes = memory.notes().size
                onPauseOrDispose { }
            }
            InfoRow(
                "What ${Branding.ASSISTANT_NAME} remembers",
                "$notes note(s) you asked it to remember, kept on this phone only. Gemini sees one only when you ask about it.",
            ) {
                if (notes > 0) TextButton(onClick = { memory.clearNotes().also { notes = 0 } }) { Text("Forget all") }
            }
        }

        Section("About") {
            InfoRow(Branding.ASSISTANT_NAME, "Version ${versionName(context)}")
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun engineText(engine: String?) = when (engine) {
    "on-device" -> "The phone's on-device recogniser. No internet needed."
    "offline" -> "Offline model on this phone (Vosk). No internet needed; best with English words."
    "network" -> "The phone's speech service (usually Google). Needs internet."
    else -> "Nothing local is available, so Gemini hears every request."
}

private fun versionName(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
