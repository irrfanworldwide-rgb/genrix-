package app.genrix.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.genrix.android.Branding
import app.genrix.android.assistant.AssistantViewModel
import app.genrix.android.assistant.Phase
import app.genrix.android.wake.WakeWordModel

private enum class Step { Welcome, Key, LocalCloud, Mic, Wake, Control, Done }

/**
 * First run: welcome, the user's Gemini key (required), what runs where, the microphone,
 * the wake word and the optional phone control. Optional steps can be skipped.
 */
@Composable
fun Onboarding(assistant: AssistantViewModel, hasKey: Boolean, onFinished: () -> Unit) {
    // Coming back for a new key only: skip the introduction.
    var index by rememberSaveable { mutableIntStateOf(if (assistant.onboarded) Step.Key.ordinal else 0) }
    val step = Step.entries[index]
    val next: () -> Unit = { if (index < Step.entries.lastIndex) index++ else onFinished() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Progress(index, Modifier.padding(top = 20.dp, bottom = 8.dp))
        AnimatedContent(step, label = "step", modifier = Modifier.weight(1f)) { shown ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
                when (shown) {
                    Step.Welcome -> Page(Branding.ASSISTANT_NAME, "A voice assistant for your phone, in English, Hindi and Hinglish.") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Orb(Phase.Listening, Modifier.size(180.dp))
                        }
                        Primary("Get started", next)
                    }
                    Step.Key -> Page("Your Gemini key", "${Branding.ASSISTANT_NAME} uses Google Gemini, with your own key, for anything the phone can't do by itself.") {
                        if (hasKey) {
                            Text("Your key is saved and checked.", color = Accent, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(16.dp))
                            Primary("Continue", next)
                        } else {
                            KeyForm(onSave = { key -> assistant.saveKey(key).also { if (it == null) next() } })
                        }
                    }
                    Step.LocalCloud -> Page("What runs where", "Simple commands never leave the phone. The rest goes to Gemini.") {
                        Section(null) {
                            InfoRow("On this phone", "Volume, music, flashlight, opening apps, time, battery, timers and alarms. No internet, no Gemini.") {
                                Pill("Local", Accent)
                            }
                            InfoRow("With Gemini", "Questions, calls and messages, songs by name, your screen. Needs internet and uses your key.") {
                                Pill("Gemini", Warning)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Primary("Continue", next)
                    }
                    Step.Mic -> MicStep(next)
                    Step.Wake -> Page("Hands-free", "Say \"${WakeWordModel.PHRASE}\" instead of touching the phone. It listens on the phone only; nothing is sent until you say it.") {
                        if (assistant.wakeWordAvailable && micAllowed()) {
                            var on by remember { mutableStateOf(assistant.wakeWordOn) }
                            Section(null) {
                                SwitchRow("Wake word", "Uses a little battery while it waits.", on) {
                                    on = it
                                    assistant.setWakeWord(it)
                                }
                                VoiceSetupRow(assistant.voiceSaved, assistant::recordVoiceSample, assistant::saveVoice, assistant::forgetVoice)
                            }
                        } else {
                            Text(
                                if (assistant.wakeWordAvailable) "Allow the microphone first (Settings → Permissions)." else "Not in this build yet.",
                                color = Muted,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Primary("Continue", next)
                    }
                    Step.Control -> Page("Phone control (optional)", "Lets ${Branding.ASSISTANT_NAME} read the screen and tap for you, e.g. press Play or fill in a message. You can turn it on later in Settings.") {
                        ControlCard(showWhenOn = true)
                        Spacer(Modifier.height(24.dp))
                        Primary("Continue", next)
                        TextButton(onClick = next, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
                    }
                    Step.Done -> Page("You're set", "Say \"${WakeWordModel.PHRASE}\" or tap the mic. Settings has everything you chose here.") {
                        Spacer(Modifier.height(16.dp))
                        Primary("Start", onFinished)
                    }
                }
            }
        }
    }
}

@Composable
private fun MicStep(next: () -> Unit) {
    var allowed by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val already = micAllowed()
    Page("Microphone", "Needed to hear you. Audio for the wake word and local commands stays on the phone; Gemini hears you only during a conversation with it.") {
        if (already || allowed) {
            Text("Microphone allowed.", color = Accent, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Primary("Continue", next)
        } else {
            Primary("Allow microphone") { ask.launch(Manifest.permission.RECORD_AUDIO) }
            TextButton(onClick = next, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        }
    }
}

@Composable
private fun micAllowed(): Boolean =
    ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
private fun Page(title: String, detail: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.displaySmall)
    Text(detail, style = MaterialTheme.typography.bodyLarge, color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
    Column(content = content)
}

@Composable
private fun Primary(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(label) }
}

@Composable
private fun Progress(index: Int, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Step.entries.forEach {
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(if (it.ordinal <= index) Accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
            )
        }
    }
}
