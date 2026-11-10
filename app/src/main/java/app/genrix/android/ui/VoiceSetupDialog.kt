package app.genrix.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.genrix.android.wake.VoiceProfile
import app.genrix.android.wake.VoiceSetup
import app.genrix.android.wake.WakeWordModel
import kotlinx.coroutines.launch

/** "Teach my voice": five recordings of the wake phrase, kept only on this phone. */
@Composable
fun VoiceSetupDialog(
    record: suspend (speakNow: () -> Unit) -> VoiceSetup.Sample,
    onSave: (List<FloatArray>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val samples = remember { mutableStateListOf<FloatArray>() }
    var status by remember { mutableStateOf("Tap Record, wait for \"Say it now\", then say \"${WakeWordModel.PHRASE}\" normally.") }
    var busy by remember { mutableStateOf(false) }
    val done = samples.size >= VoiceProfile.SAMPLES

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Teach ${WakeWordModel.PHRASE} your voice") },
        text = {
            Column {
                Text("Sample ${minOf(samples.size + 1, VoiceProfile.SAMPLES)} of ${VoiceProfile.SAMPLES}", color = Accent)
                Text(status, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Kept only on this phone as speech features, not recordings. You can forget it any time.",
                    color = Muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            if (done) {
                Button(onClick = {
                    onSave(samples.toList())
                    onDismiss()
                }) { Text("Save") }
            } else {
                Button(enabled = !busy, onClick = {
                    busy = true
                    status = "Get ready…"
                    scope.launch {
                        val sample = record { status = "Say \"${WakeWordModel.PHRASE}\" now" }
                        busy = false
                        status = when (sample) {
                            is VoiceSetup.Sample.Ok -> {
                                samples += sample.window
                                if (samples.size >= VoiceProfile.SAMPLES) {
                                    "Got all ${VoiceProfile.SAMPLES}. Tap Save."
                                } else {
                                    "Got it. Tap Record for the next one."
                                }
                            }
                            is VoiceSetup.Sample.Problem -> sample.message
                        }
                    }
                }) { Text(if (busy) "Listening…" else "Record") }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Under the wake-word switch: teach / redo / forget the user's voice. */
@Composable
fun VoiceSetupRow(
    saved: Boolean,
    record: suspend (speakNow: () -> Unit) -> VoiceSetup.Sample,
    onSave: (List<FloatArray>) -> Unit,
    onForget: () -> Unit,
) {
    var hasVoice by remember { mutableStateOf(saved) }
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (hasVoice) "Your voice is saved." else "Not waking for you? Teach it your voice.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { open = true }) { Text(if (hasVoice) "Redo" else "Teach my voice", color = Accent) }
        if (hasVoice) {
            TextButton(onClick = {
                onForget()
                hasVoice = false
            }) { Text("Forget", color = Muted) }
        }
    }
    if (open) {
        VoiceSetupDialog(
            record = record,
            onSave = {
                onSave(it)
                hasVoice = true
            },
            onDismiss = { open = false },
        )
    }
}
