package app.genrix.android.ui

import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import android.os.BatteryManager
import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.Canvas
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
        state.problem ?: if (wakeOn) "Say \"${WakeWordModel.PHRASE}\", or tap the mic." else "Tap to talk or say “Hey Genrix”",
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
    onMenu: () -> Unit = {},
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
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val tint = Accent
            Box(
                Modifier
                    .size(46.dp)
                    .background(Surface, RoundedCornerShape(14.dp))
                    .border(1.dp, glowBrush(), RoundedCornerShape(14.dp))
                    .pressScale(onMenu)
                    .semantics { contentDescription = "Menu" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(22.dp)) {
                    val line = 2.2f * density
                    drawLine(tint, Offset(size.width * 0.1f, size.height * 0.28f), Offset(size.width * 0.9f, size.height * 0.28f), strokeWidth = line, cap = StrokeCap.Round)
                    drawLine(tint, Offset(size.width * 0.1f, size.height * 0.5f), Offset(size.width * 0.9f, size.height * 0.5f), strokeWidth = line, cap = StrokeCap.Round)
                    drawLine(tint, Offset(size.width * 0.1f, size.height * 0.72f), Offset(size.width * 0.6f, size.height * 0.72f), strokeWidth = line, cap = StrokeCap.Round)
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Branding.ASSISTANT_NAME.uppercase(),
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.onBackground, Accent)),
                        fontFamily = GenrixFonts.Ui,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                    ),
                )
                Text(Branding.DESCRIPTOR, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 5.sp), color = Muted)
            }
            Row(
                Modifier
                    .background(Positive.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .border(1.dp, Positive.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).background(Positive, CircleShape))
                Text("Online", style = MaterialTheme.typography.labelLarge, color = Positive)
            }
        }

        // The AI core is the hero; it takes whatever room is left, up to a comfortable size.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val coreSize = minOf(maxWidth, maxHeight, 340.dp)
            Orb(
                phase = now.orb,
                level = level,
                executing = localPhase == LocalPhase.Doing,
                error = problem,
                rings = true,
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
            Text(
                "G",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Color(0xFF00E8FF), Color(0xFF2A8BFF), Color(0xFF8B5CFF))),
                    fontFamily = GenrixFonts.Ui,
                    fontSize = (coreSize.value * 0.24f).sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .background(Surface, RoundedCornerShape(26.dp))
                    .border(1.dp, glowBrush(), RoundedCornerShape(26.dp))
                    .padding(horizontal = 26.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    now.title.uppercase(),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "status",
                ) { title ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniWave()
                        Text(
                            title,
                            style = MaterialTheme.typography.displaySmall.copy(fontSize = 19.sp, letterSpacing = 2.sp),
                            color = if (now.live) Accent else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                Text(
                    now.hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (problem) Warning else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // The task in progress, or one that just finished (2 minutes).
        val recentTask = task?.takeIf { now.live || System.currentTimeMillis() - it.at < TASK_SHOWN_MS }
        recentTask?.let { TaskCard(it, Modifier.padding(bottom = 12.dp)) }

        if (state.phase != Phase.Off && state.lines.isNotEmpty()) {
            Conversation(state.lines, Modifier.fillMaxWidth().weight(1f))
        } else if (state.phase == Phase.Listening || state.phase == Phase.Speaking) {
            Waveform(level, active = true, modifier = Modifier.fillMaxWidth().height(56.dp))
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

/** The main voice control: a glowing glass disc with rings and two curved lines under it. */
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
    val accent = Accent
    val blue = GenrixColors.Glow
    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val p1 = Path().apply {
                moveTo(0f, size.height * 0.92f)
                quadraticBezierTo(size.width / 2f, size.height * 0.15f, size.width, size.height * 0.92f)
            }
            drawPath(p1, accent.copy(alpha = 0.35f), style = Stroke(1.5f * density))
            val p2 = Path().apply {
                moveTo(size.width * 0.12f, size.height)
                quadraticBezierTo(size.width / 2f, size.height * 0.30f, size.width * 0.88f, size.height)
            }
            drawPath(p2, blue.copy(alpha = 0.40f), style = Stroke(1.5f * density))
        }
        Box(
            Modifier.size(150.dp).drawBehind {
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), center, size.minDimension / 2f), radius = size.minDimension / 2f)
            },
        )
        if (live) {
            Box(Modifier.size(96.dp + 28.dp * pulse).alpha(0.7f * (1f - pulse)).border(1.5.dp, accent, CircleShape))
        }
        Box(Modifier.size(108.dp).border(1.dp, accent.copy(alpha = 0.22f), CircleShape))
        Box(Modifier.size(96.dp).border(1.5.dp, accent.copy(alpha = 0.5f), CircleShape))
        Box(
            Modifier
                .size(84.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF0A2A52), Color(0xFF04102A))), CircleShape)
                .border(2.dp, accent, CircleShape)
                .pressScale(onClick)
                .semantics { contentDescription = if (live) "Stop" else "Talk" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (live) Icons.Filled.Close else MicIcon, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
        }
    }
}

/** Seven small bars, the voice icon on the status pill. */
@Composable
private fun MiniWave() {
    val a = Accent
    val b = GenrixColors.Glow
    Canvas(Modifier.size(width = 28.dp, height = 22.dp)) {
        val heights = floatArrayOf(0.30f, 0.60f, 1f, 0.70f, 1f, 0.60f, 0.30f)
        val step = size.width / heights.size
        heights.forEachIndexed { i, h ->
            drawLine(
                if (i % 2 == 0) a else b,
                Offset(step * (i + 0.5f), size.height * (0.5f - h / 2f)),
                Offset(step * (i + 0.5f), size.height * (0.5f + h / 2f)),
                strokeWidth = 2.5f * density,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** One-tap phone actions; they run here, with no network. */
@Composable
private fun QuickActions(onQuick: (LocalCommand) -> String, favourites: List<LocalCommand>) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val clockText = remember(tick) { SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(tick)) }
    val battery = remember(tick) {
        (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
    }
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
                val shape = RoundedCornerShape(22.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .background(Surface, shape)
                        .border(1.dp, glowBrush(), shape)
                        .pressScale { answer = onQuick(command()) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToolIcon(label)
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        val key = label.lowercase()
                        val sub = if ("battery" in key) battery?.let { "$it%" } else if ("time" in key) clockText else null
                        if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1)
                    }
                }
            }
        }
        answer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
    }
}

@Composable
private fun Recent(entries: List<ActivityLog.Entry>, onSeeAll: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(Surface, shape)
            .border(1.dp, glowBrush(), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniWave()
            Text("Recent Activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
            Text("See all →", style = MaterialTheme.typography.labelLarge, color = Accent, modifier = Modifier.pressScale(onSeeAll).padding(8.dp))
        }
        if (entries.isEmpty()) {
            Text("Nothing yet. What you ask shows up here.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp))
        } else {
            entries.forEach { ActivityRow(it) }
        }
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

/** Line icons for the quick actions (drawn, so no extra icon library is needed). */
@Composable
private fun ToolIcon(label: String) {
    val tint = Accent
    val green = GenrixColors.Success
    val key = label.lowercase()
    Canvas(Modifier.size(30.dp)) {
        val w = size.width
        val line = Stroke(width = 2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when {
            "torch" in key -> {
                val bolt = Path().apply {
                    moveTo(w * 0.56f, w * 0.06f)
                    lineTo(w * 0.18f, w * 0.56f)
                    lineTo(w * 0.46f, w * 0.56f)
                    lineTo(w * 0.42f, w * 0.94f)
                    lineTo(w * 0.82f, w * 0.40f)
                    lineTo(w * 0.54f, w * 0.40f)
                    close()
                }
                drawPath(bolt, tint, style = line)
            }
            "battery" in key -> {
                drawRoundRect(green, Offset(w * 0.06f, w * 0.28f), Size(w * 0.76f, w * 0.44f), CornerRadius(4f * density), style = line)
                drawRoundRect(green, Offset(w * 0.14f, w * 0.36f), Size(w * 0.46f, w * 0.28f), CornerRadius(2f * density))
                drawLine(green, Offset(w * 0.9f, w * 0.42f), Offset(w * 0.9f, w * 0.58f), strokeWidth = 2.5f * density, cap = StrokeCap.Round)
            }
            "time" in key -> {
                drawCircle(tint, radius = w * 0.42f, center = center, style = line)
                drawLine(tint, center, Offset(center.x, w * 0.24f), strokeWidth = 2f * density, cap = StrokeCap.Round)
                drawLine(tint, center, Offset(w * 0.7f, center.y + w * 0.08f), strokeWidth = 2f * density, cap = StrokeCap.Round)
            }
            "pause" in key -> {
                drawCircle(tint, radius = w * 0.42f, center = center, style = line)
                drawLine(tint, Offset(w * 0.40f, w * 0.34f), Offset(w * 0.40f, w * 0.66f), strokeWidth = 2.5f * density, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.60f, w * 0.34f), Offset(w * 0.60f, w * 0.66f), strokeWidth = 2.5f * density, cap = StrokeCap.Round)
            }
            else -> drawCircle(tint, radius = w * 0.12f, center = center)
        }
    }
}
