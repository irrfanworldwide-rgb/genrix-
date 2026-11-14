package app.genrix.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.genrix.android.memory.MemoryRules
import app.genrix.android.memory.MemoryStore

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

private val PC = listOf(
    Command("PC apps and links", "\"PC pe Chrome kholo\" · \"PC par YouTube kholo\". Needs the PC bridge; only apps you listed on the PC."),
    Command("PC volume and media", "\"PC ki awaaz kam karo\" · \"PC pe music pause karo\" (needs the PC bridge)"),
    Command("PC power", "\"PC lock kar do\". You tap Allow on the phone first; sleep and shutdown must be switched on in the PC helper."),
)

private enum class Kind { Local, Approval, AI }

private class Entry(val command: Command, val kind: Kind)

private val ALL = LOCAL.map { Entry(it, Kind.Local) } +
    TASKS.map { Entry(it, Kind.Approval) } +
    CLOUD.map { Entry(it, Kind.AI) } +
    PC.map { Entry(it, if (it.title == "PC power") Kind.Approval else Kind.AI) }

private class Category(val title: String, val detail: String, val icon: ImageVector, val color: Color, val titles: List<String>)

private val PINK = Color(0xFFFF6FE0)

private val CATEGORIES = listOf(
    Category("On this phone", "Control your device", Icons.Filled.Phone, GenrixColors.Glow, listOf("Open apps", "Flashlight", "Stop")),
    Category("Media & Entertainment", "Play, pause, and more", Icons.Filled.PlayArrow, GenrixColors.Violet, listOf("Music", "Songs")),
    Category("Time, Date & Calendar", "Manage your schedule", Icons.Filled.DateRange, GenrixColors.Success, listOf("Time, date, battery", "Timers and alarms", "Reminders")),
    Category("System Controls", "Brightness, volume, etc.", Icons.Filled.Settings, GenrixColors.Warning, listOf("Volume", "Your screen")),
    Category("Tasks with your OK", "You check it, then tap Send, Call or Pay", Icons.Filled.Email, PINK, listOf("WhatsApp message", "Calls", "Movie tickets")),
    Category("PC controls", "Your computer, through the PC bridge", Icons.Filled.Build, GenrixColors.Glow, listOf("PC apps and links", "PC volume and media", "PC power")),
    Category("AI tasks", "Questions, with Gemini", Icons.Filled.Star, GenrixColors.Violet, listOf("Questions")),
)

@Composable
private fun kindColor(kind: Kind): Color = when (kind) {
    Kind.Local -> Accent
    Kind.AI -> AccentAlt
    Kind.Approval -> Warning
}

/** Everything Genrix can do, grouped. Filters and search only narrow the list; nothing is hidden for good. */
@Composable
fun AutomationsScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var allOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Automations", "Smart actions. Just for you.") {
            GridButton { allOpen = !allOpen }
        }
        SearchField(query) { query = it }
        ChipRow(listOf("All", "Local", "AI", "Approval"), filter) { filter = it }
        if (filter == "All") Routines(query)
        SectionLabel("Command Categories")
        val kind = when (filter) {
            "Local" -> Kind.Local
            "AI" -> Kind.AI
            "Approval" -> Kind.Approval
            else -> null
        }
        CATEGORIES.forEach { CategoryCard(it, kind, query, forceOpen = allOpen) }
        ExploreCard {
            allOpen = true
            filter = "All"
            query = ""
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(Modifier.padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(3.dp, 18.dp).background(AccentAlt, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Expands or collapses every category at once. */
@Composable
private fun GridButton(onClick: () -> Unit) {
    val tint = Accent
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(46.dp)
            .background(Surface, shape)
            .border(1.dp, glowBrush(), shape)
            .pressScale(onClick)
            .semantics { contentDescription = "Expand or collapse all categories" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val cell = size.width * 0.38f
            val gap = size.width * 0.24f
            for (r in 0..1) {
                for (c in 0..1) {
                    drawRoundRect(tint, Offset(c * (cell + gap), r * (cell + gap)), Size(cell, cell), CornerRadius(3f * density))
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Search commands...", color = MutedDim) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
            } else {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
        shape = RoundedCornerShape(27.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Accent.copy(alpha = 0.32f),
            focusedBorderColor = Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BoltIcon(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val bolt = Path().apply {
            moveTo(w * 0.56f, w * 0.06f)
            lineTo(w * 0.18f, w * 0.56f)
            lineTo(w * 0.46f, w * 0.56f)
            lineTo(w * 0.42f, w * 0.94f)
            lineTo(w * 0.82f, w * 0.40f)
            lineTo(w * 0.54f, w * 0.40f)
            close()
        }
        drawPath(bolt, color, style = Stroke(2.2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The routines card: a violet hero with a layered illustration, then the user's own routines. */
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
    val shape = RoundedCornerShape(22.dp)
    val violet = GenrixColors.Violet
    val cyan = Accent
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(violet.copy(alpha = 0.38f), GenrixColors.BackgroundElevated)), shape)
            .border(1.dp, violet.copy(alpha = 0.6f), shape),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(54.dp)
                        .background(violet.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                        .border(1.5.dp, violet, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { BoltIcon(Color(0xFFC9A5FF)) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Routines", style = MaterialTheme.typography.titleMedium)
                    Text("Daily tasks made easy", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            Canvas(Modifier.align(Alignment.CenterEnd).size(width = 120.dp, height = 84.dp)) {
                val w = size.width
                val h = size.height
                val navy = Color(0xFF0A2864).copy(alpha = 0.85f)
                floatArrayOf(0.52f, 0.28f, 0.04f).forEachIndexed { i, y0 ->
                    val layer = Path().apply {
                        moveTo(w * 0.5f, h * y0)
                        lineTo(w * 0.95f, h * (y0 + 0.22f))
                        lineTo(w * 0.5f, h * (y0 + 0.44f))
                        lineTo(w * 0.05f, h * (y0 + 0.22f))
                        close()
                    }
                    drawPath(layer, if (i == 2) cyan.copy(alpha = 0.35f) else navy)
                    drawPath(layer, cyan, style = Stroke(1.6f * density))
                }
            }
        }
        Text(
            "Say a routine's name to run its steps. Make one by voice: \"make a routine called good night: alarm at 6:30, what's on tomorrow\".",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        shown.forEach { r ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    Text(r.steps.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                if (r !== MemoryRules.GOOD_MORNING) {
                    TextButton(onClick = { pendingDelete = r.name }) { Text("Delete") }
                }
            }
        }
    }
}

/** One category as a single glass row with a coloured icon; it expands to its commands, each with its kind. */
@Composable
private fun CategoryCard(category: Category, kind: Kind?, query: String, forceOpen: Boolean) {
    var open by rememberSaveable(category.title) { mutableStateOf(false) }
    val q = query.trim()
    val shown = ALL.filter {
        it.command.title in category.titles &&
            (kind == null || it.kind == kind) &&
            (q.isEmpty() || it.command.title.contains(q, ignoreCase = true) || it.command.examples.contains(q, ignoreCase = true))
    }
    if (shown.isEmpty()) return
    val expanded = open || forceOpen || q.isNotEmpty() || kind != null
    val angle by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().background(Surface, shape).border(1.dp, glowBrush(), shape)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(category.icon, category.color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(category.title, style = MaterialTheme.typography.titleMedium)
                Text(category.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.rotate(angle))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                shown.forEach { entry ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.command.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Pill(entry.kind.name, kindColor(entry.kind), dot = false)
                        }
                        Text(
                            entry.command.examples,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = if (entry.kind == Kind.Local) GenrixFonts.Mono else GenrixFonts.Ui,
                            ),
                            color = Muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The violet "Explore" card at the end; it opens every category. */
@Composable
private fun ExploreCard(onClick: () -> Unit) {
    val violet = GenrixColors.Violet
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(violet.copy(alpha = 0.30f), GenrixColors.BackgroundElevated)), shape)
            .border(1.dp, violet.copy(alpha = 0.7f), shape)
            .pressScale(onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .background(violet.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                .border(1.5.dp, violet, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Star, contentDescription = null, tint = violet, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Explore 100+ Commands", style = MaterialTheme.typography.titleMedium)
            Text("Unlock the full power of Genrix", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}
