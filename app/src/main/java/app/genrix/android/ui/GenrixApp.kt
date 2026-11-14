package app.genrix.android.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.genrix.android.assistant.AssistantViewModel
import app.genrix.android.assistant.UiState
import app.genrix.android.local.ActivityLog
import app.genrix.android.local.LocalPhase
import app.genrix.android.task.Task

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Automations("Automations", Icons.Filled.Build),
    Activity("Activity", Icons.Filled.List),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun GenrixApp(assistant: AssistantViewModel) {
    val state by assistant.state.collectAsStateWithLifecycle()
    val localPhase by assistant.localPhase.collectAsStateWithLifecycle()
    val activity by assistant.activity.collectAsStateWithLifecycle()
    val wakeWord by assistant.wakeWord.collectAsStateWithLifecycle()
    val level by assistant.level.collectAsStateWithLifecycle()
    val task by assistant.task.collectAsStateWithLifecycle()
    var onboarded by rememberSaveable { mutableStateOf(assistant.onboarded) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    GenrixTheme {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            Content(assistant, state, localPhase, level, task, activity, wakeWord, onboarded, { onboarded = true }, tab) { tab = it }
        }
    }
}

@Composable
private fun Content(
    assistant: AssistantViewModel,
    state: UiState,
    localPhase: LocalPhase,
    level: Float,
    task: Task?,
    activity: List<ActivityLog.Entry>,
    wakeWord: Boolean,
    onboarded: Boolean,
    onOnboarded: () -> Unit,
    tab: Int,
    setTab: (Int) -> Unit,
) {
    if (!onboarded || !state.hasKey) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Onboarding(assistant, state.hasKey) {
                assistant.onboarded = true
                onOnboarded()
            }
        }
        return
    }
    Box(Modifier.fillMaxSize().background(Background).nebula()) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f), RoundedCornerShape(28.dp))
                    .border(1.dp, glowBrush(), RoundedCornerShape(28.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Tab.entries.forEach { item ->
                    val active = tab == item.ordinal
                    Column(
                        Modifier
                            .weight(1f)
                            .background(
                                if (active) Brush.verticalGradient(listOf(Accent.copy(alpha = 0.24f), GenrixColors.Glow.copy(alpha = 0.12f))) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
                                RoundedCornerShape(22.dp),
                            )
                            .border(1.dp, if (active) Accent.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(22.dp))
                            .pressScale { setTab(item.ordinal) }
                            .padding(vertical = 8.dp)
                            .semantics(mergeDescendants = true) { selected = active },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NavGlyph(item, if (active) Accent else Muted)
                        Text(item.label, style = MaterialTheme.typography.labelMedium, color = if (active) Accent else Muted, maxLines = 1)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { current ->
            when (Tab.entries[current]) {
                Tab.Home -> HomeScreen(
                    state = state,
                    localPhase = localPhase,
                    level = level,
                    task = task,
                    activity = activity,
                    wakeOn = assistant.wakeWordAvailable && wakeWord,
                    onStart = assistant::start,
                    onStop = assistant::stop,
                    onInterrupt = assistant::interrupt,
                    onMicDenied = assistant::micUnavailable,
                    onPermissionAnswered = assistant::permissionAnswered,
                    onQuick = assistant::quick,
                    onSeeActivity = { setTab(Tab.Activity.ordinal) },
                    onMenu = { setTab(Tab.Settings.ordinal) },
                    favourites = remember(activity.size) { assistant.favourites() },
                )
                Tab.Automations -> AutomationsScreen()
                Tab.Activity -> ActivityScreen(activity, assistant::clearActivity)
                Tab.Settings -> SettingsScreen(assistant)
            }
            }
        }
    }
    }
}

/** Navigation icons: the bolt and the bars are drawn; Home and Settings use the standard icons. */
@Composable
private fun NavGlyph(tab: Tab, color: Color) {
    when (tab) {
        Tab.Home -> Icon(Icons.Filled.Home, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Tab.Settings -> Icon(Icons.Filled.Settings, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Tab.Automations -> Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val path = Path().apply {
                moveTo(w * 0.56f, w * 0.06f)
                lineTo(w * 0.18f, w * 0.56f)
                lineTo(w * 0.46f, w * 0.56f)
                lineTo(w * 0.42f, w * 0.94f)
                lineTo(w * 0.82f, w * 0.40f)
                lineTo(w * 0.54f, w * 0.40f)
                close()
            }
            drawPath(path, color, style = Stroke(width = 2f * density, join = StrokeJoin.Round, cap = StrokeCap.Round))
        }
        Tab.Activity -> Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val line = Stroke(width = 2f * density, cap = StrokeCap.Round)
            drawRoundRect(color, Offset(w * 0.12f, w * 0.50f), Size(w * 0.18f, w * 0.38f), CornerRadius(3f * density), style = line)
            drawRoundRect(color, Offset(w * 0.41f, w * 0.12f), Size(w * 0.18f, w * 0.76f), CornerRadius(3f * density), style = line)
            drawRoundRect(color, Offset(w * 0.70f, w * 0.34f), Size(w * 0.18f, w * 0.54f), CornerRadius(3f * density), style = line)
        }
    }
}
