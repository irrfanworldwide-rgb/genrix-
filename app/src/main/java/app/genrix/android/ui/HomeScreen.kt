package app.genrix.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.genrix.android.Branding
import app.genrix.android.assistant.Line
import app.genrix.android.assistant.Phase
import app.genrix.android.assistant.UiState
import app.genrix.android.learn.HabitKeys
import app.genrix.android.local.ActivityLog
import app.genrix.android.local.LocalCommand
import app.genrix.android.local.LocalPhase
import app.genrix.android.local.MediaKey
import app.genrix.android.task.Task
import app.genrix.android.wake.WakeWordModel

/** What the assistant is doing, in words and a colour, from the session and the local turn. */
private data class Status(val title: String, val hint: String, val badge: String, val orb: Phase, val live: Boolean)

private fun status(state: UiState, local: LocalPhase, wakeOn: Boolean): Status = when {
    local == LocalPhase.Listening -> Status("Listening", "Say a command. Simple ones run on this phone.", "On phone", Phase.Listening, true)
    local == LocalPhase.Doing -> Status("Executing", "Doing it on this phone.", "On phone", Phase.Connecting, true)
    local == LocalPhase.Speaking -> Status("Speaking", "Answered on this phone.", "On phone", Phase.Speaking, true)
    state.phase == Phase.Connecting -> Status("Thinking", "Connecting to Gemini…", "Gemini", Phase.Connecting, true)
    state.phase == Phase.Listening -> Status("Listening", "Talk any time, in English, Hindi or Hinglish.", "Gemini", Phase.Listening, true)
    state.phase == Phase.Speaking -> Status("Speaking", "Tap the orb to cut in.", "Gemini", Phase.Speaking, true)
    else -> Status(
        "Standby",
        state.problem ?: if (wakeOn) "Say \"${WakeWordModel.PHRASE}\", or tap the mic." else "Tap the mic to talk.",
        if (wakeOn) "Wake word on" else "Wake word off",
        Phase.Off,
        false,
    )
}

@Composable
fun HomeScreen(
    state: UiState,
    localPhase: LocalPhase,
    level: Float,
    task: Task?,
    activity: List<ActivityLog.Entry>,
    wakeOn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInterrupt: () -> Unit,
    onMicDenied: () -> Unit,
    onPermissionAnswered: (Boolean) -> Unit,
    onQuick: (LocalCommand) -> String,
    onSeeActivity: () -> Unit,
    favourites: List<LocalCommand> = emptyList(),
) {
    val context = LocalContext.current
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStart() else onMicDenied()
    }
    // A tool needs Contacts or notifications: show Android's own Allow dialog.
    val askForTool = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onPermissionAnswered)
    LaunchedEffect(state.permissionNeeded) {
        state.permissionNeeded?.let(askForTool::launch)
    }
    val startListening = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) onStart() else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    val now = status(state, localPhase, wakeOn)
    val problem = state.problem != null && !now.live

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    Branding.ASSISTANT_NAME.uppercase(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = GenrixFonts.Mono, letterSpacing = 5.sp),
                )
                Text(Branding.DESCRIPTOR, style = MaterialTheme.typography.labelMedium, color = MutedDim)
            }
            Pill(now.badge, if (now.live) Accent else Muted)
        }

        // The AI core is the hero; it takes whatever room is left, up to a comfortable size.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val coreSize = minOf(maxWidth, maxHeight, 340.dp)
            Orb(
                phase = now.orb,
                level = level,
                executing = localPhase == LocalPhase.Doing,
                error = problem,
                modifier = Modifier
                    .size(coreSize)
                    .semantics { contentDescription = "${Branding.ASSISTANT_NAME}: ${now.title}" }
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        when (state.phase) {
                            Phase.Off -> if (localPhase == LocalPhase.Idle) startListening()
                            Phase.Speaking -> onInterrupt()
                            else -> Unit
                        }
                    },
            )
        }

        AnimatedContent(
            now.title.uppercase(),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "status",
        ) { title ->
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = if (now.live) Accent else MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            now.hint,
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem) Warning else Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
        )

        // The task in progress, or one that just finished (2 minutes).
        val recentTask = task?.takeIf { now.live || System.currentTimeMillis() - it.at < TASK_SHOWN_MS }
        recentTask?.let { TaskCard(it, Modifier.padding(bottom = 12.dp)) }

        if (state.phase != Phase.Off && state.lines.isNotEmpty()) {
            Conversation(state.lines, Modifier.fillMaxWidth().weight(1f))
        } else {
            QuickActions(onQuick, favourites)
            Spacer(Modifier.height(16.dp))
            Recent(activity.take(2), onSeeActivity)
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            MicButton(live = now.live, onClick = { if (now.live) onStop() else startListening() })
        }
    }
}

/** The main voice control: a cyan disc with a quiet ring, and a pulsing ring while live. */
@Composable
private fun MicButton(live: Boolean, onClick: () -> Unit) {
    val pulse = if (live) {
        rememberInfiniteTransition(label = "mic").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
            label = "pulse",
        ).value
    } else {
        0f
    }
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        if (live) {
            Box(Modifier.size(76.dp + 20.dp * pulse).alpha(0.5f * (1f - pulse)).border(1.5.dp, Accent, CircleShape))
        } else {
            Box(Modifier.size(92.dp).border(1.dp, Accent.copy(alpha = 0.25f), CircleShape))
        }
        Box(
            Modifier
                .size(72.dp)
                .background(if (live) Raised else Accent, CircleShape)
                .border(1.dp, if (live) MaterialTheme.colorScheme.outline else Color.Transparent, CircleShape)
                .pressScale(onClick)
                .semantics { contentDescription = if (live) "Stop" else "Talk" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (live) Icons.Filled.Close else MicIcon,
                contentDescription = null,
                tint = if (live) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** One-tap phone actions; they run here, with no network. */
@Composable
private fun QuickActions(onQuick: (LocalCommand) -> String, favourites: List<LocalCommand>) {
    var torchOn by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    // Learned favourites first, topped up with the defaults.
    val learned = favourites.mapNotNull { command -> HabitKeys.key(command)?.let { HabitKeys.describe(it).replaceFirstChar(Char::uppercase) to { command } } }
    val defaults = listOf(
        (if (torchOn) "Torch off" else "Torch") to {
            torchOn = !torchOn
            LocalCommand.Torch(torchOn)
        },
        "Battery" to { LocalCommand.Battery },
        "Time" to { LocalCommand.Time },
        "Pause" to { LocalCommand.Media(MediaKey.Pause) },
    )
    val actions = (learned + defaults.filter { d -> learned.none { it.first.startsWith(d.first, ignoreCase = true) } }).take(4)
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            actions.forEach { (label, command) ->
                val shape = RoundedCornerShape(18.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .background(Surface, shape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                        .pressScale { answer = onQuick(command()) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        answer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
    }
}

@Composable
private fun Recent(entries: List<ActivityLog.Entry>, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(start = 4.dp).size(3.dp, 12.dp).background(Accent, RoundedCornerShape(2.dp)))
        Text("RECENT", style = MaterialTheme.typography.titleSmall, color = Muted, modifier = Modifier.weight(1f).padding(start = 8.dp))
        TextButton(onClick = onSeeAll) { Text("See all") }
    }
    if (entries.isEmpty()) {
        Text("Nothing yet. What you ask shows up here.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(start = 4.dp))
    } else {
        Section(null) { entries.forEach { ActivityRow(it) } }
    }
}

@Composable
private fun Conversation(lines: List<Line>, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(lines.size, lines.lastOrNull()?.text) {
        if (lines.isNotEmpty()) list.animateScrollToItem(lines.lastIndex)
    }
    LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lines, key = { it.id }) { line ->
            Box(Modifier.fillMaxWidth(), contentAlignment = if (line.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                val shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (line.fromUser) 18.dp else 6.dp,
                    bottomEnd = if (line.fromUser) 6.dp else 18.dp,
                )
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(if (line.fromUser) MaterialTheme.colorScheme.primaryContainer else Surface, shape)
                        .border(1.dp, if (line.fromUser) Accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant, shape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private const val TASK_SHOWN_MS = 2 * 60_000L
