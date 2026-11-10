package app.genrix.android.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.genrix.android.task.Risk
import app.genrix.android.task.Task
import app.genrix.android.task.TaskState

/** The task in progress: its steps as they really happened, and what is true now. */
@Composable
fun TaskCard(task: Task, modifier: Modifier = Modifier) {
    val (label, color) = stateLabel(task.state)
    Column(
        modifier
            .fillMaxWidth()
            .background(Surface, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (task.risk >= Risk.High) Pill(if (task.risk == Risk.Financial) "Payment: you" else "Needs you", Warning, Modifier.padding(end = 6.dp), dot = false)
            Pill(label, color)
        }
        task.steps.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepMark(step.done)
                Text(
                    "${i + 1}. ${step.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (step.done == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        if (task.detail.isNotEmpty()) {
            Text(task.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun StepMark(done: Boolean?) {
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        when (done) {
            null -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
            true -> Box(Modifier.size(10.dp).background(Accent, CircleShape))
            false -> Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
        }
    }
}

@Composable
private fun stateLabel(state: TaskState): Pair<String, Color> = when (state) {
    TaskState.Understanding -> "Understanding" to Accent
    TaskState.Planning -> "Needs your answer" to Warning
    TaskState.AwaitingConfirmation -> "Awaiting your OK" to Warning
    TaskState.Executing -> "Working" to Accent
    TaskState.WaitingForYou -> "Your turn" to Warning
    TaskState.Completed -> "Done" to Accent
    TaskState.Failed -> "Didn't work" to MaterialTheme.colorScheme.error
}
