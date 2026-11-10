package app.genrix.android.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.genrix.android.Branding
import app.genrix.android.control.ControlService

/**
 * Asks the user to switch on phone control (Accessibility), with the prominent disclosure
 * Google requires and the "restricted settings" step sideloaded apps need on Android 13+.
 */
@Composable
fun ControlCard(modifier: Modifier = Modifier, showWhenOn: Boolean = false) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(controlEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = controlEnabled(context)
        onPauseOrDispose { }
    }
    if (enabled) {
        if (showWhenOn) {
            InfoRow("Phone control", "On. Send, call, delete and pay still wait for your Allow.") {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open") }
            }
        }
        return
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text("Phone control is off", color = Accent, fontSize = 15.sp)
        Text(
            "Turn it on so ${Branding.ASSISTANT_NAME} can read your screen and tap, type and scroll " +
                "when you ask, e.g. play a song or reply in WhatsApp. Send, call, delete and pay " +
                "always wait for you to tap Allow. Banking, UPI, password and Settings apps are never read.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Row {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Turn on") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Sideloaded apps: Android greys the switch out until "Allow restricted settings".
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Switch greyed out?", color = Muted) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(
                "If the switch is grey: App info → ⋮ (top right) → Allow restricted settings, then try again.",
                color = Muted,
                fontSize = 12.sp,
            )
        }
    }
}

private fun controlEnabled(context: Context): Boolean {
    if (ControlService.instance != null) return true
    val wanted = ComponentName(context, ControlService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return enabled.split(':').any { it.equals(wanted, ignoreCase = true) }
}
