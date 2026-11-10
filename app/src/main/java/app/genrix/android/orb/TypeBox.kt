package app.genrix.android.orb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.genrix.android.Branding
import app.genrix.android.Diag
import app.genrix.android.assistant.VoiceSession
import app.genrix.android.ui.GenrixTheme

/**
 * The orb's double-tap: a one-line box above the keyboard, over whatever app is open. What
 * the user types goes to Genrix as their turn (a new conversation, or the running one).
 * Back or tapping outside closes it.
 */
object TypeBox {
    private var host: OverlayHost? = null

    fun show(context: Context) {
        if (host != null) return
        val windows = context.getSystemService(WindowManager::class.java)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        val box = OverlayHost(context) { hide(context) }
        box.view.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_OUTSIDE) hide(context)
            false
        }
        box.open {
            GenrixTheme {
                var text by remember { mutableStateOf("") }
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                val send = {
                    if (text.isNotBlank()) send(context, text.trim())
                    hide(context)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Ask ${Branding.ASSISTANT_NAME}…") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    FilledIconButton(onClick = send, enabled = text.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
        runCatching { windows.addView(box.view, params) }
            .onSuccess { host = box }
            .onFailure { Diag.w("orb: type box failed (${it.javaClass.simpleName})") }
    }

    fun hide(context: Context) {
        val box = host ?: return
        host = null
        runCatching { context.getSystemService(WindowManager::class.java).removeView(box.view) }
        box.close()
    }

    private fun send(context: Context, text: String) {
        val micAllowed = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val session = VoiceSession.get(context)
        if (!micAllowed || !session.state.value.hasKey) return FloatingOrb.openApp(context)
        Diag.i("orb: typed request")
        session.start(text)
    }
}
