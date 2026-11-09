package app.genrix.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.genrix.android.Branding
import app.genrix.android.data.KeyVault
import app.genrix.android.data.Settings
import app.genrix.android.pc.PcBridge
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Address and token of the user's PC helper, with a test button. The token is kept encrypted. */
@Composable
fun PcBridgeForm() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings(context) }
    val vault = remember { KeyVault(context) }
    val client = remember { OkHttpClient() }
    var url by remember { mutableStateOf(settings.pcUrl) }
    var token by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(!vault.read(KeyVault.PC_TOKEN).isNullOrEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            "Run pc-helper/genrix_pc.py on your computer and enter the address and token it prints. " +
                "Works on your home Wi-Fi (or over Tailscale). ${Branding.ASSISTANT_NAME} can then open apps, " +
                "control volume and lock the PC by voice. Power actions ask for your Allow tap.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; result = null },
            label = { Text("PC address, e.g. http://192.168.1.20:8765") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; result = null },
            label = { Text(if (hasToken) "Token (saved; type to replace)" else "Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        result?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Button(
                enabled = !busy && url.isNotBlank() && (token.isNotBlank() || hasToken),
                onClick = {
                    val clean = PcBridge.cleanUrl(url)
                    if (clean == null) {
                        result = "Use a home-network address like http://192.168.1.20:8765 (http, not the internet)."
                        return@Button
                    }
                    settings.pcUrl = clean
                    url = clean
                    if (token.isNotBlank()) {
                        vault.write(KeyVault.PC_TOKEN, token.trim())
                        token = ""
                        hasToken = true
                    }
                    busy = true
                    result = "Checking the PC…"
                    scope.launch {
                        val reply = PcBridge.call(client, context, "status")
                        result = if (reply.ok) "Connected: ${reply.message}" else reply.message
                        busy = false
                    }
                },
            ) { Text("Save and test") }
            Spacer(Modifier.width(8.dp))
            if (settings.pcUrl.isNotEmpty() || hasToken) {
                TextButton(onClick = {
                    settings.pcUrl = ""
                    vault.remove(KeyVault.PC_TOKEN)
                    url = ""
                    token = ""
                    hasToken = false
                    result = "PC bridge removed."
                }) { Text("Remove") }
            }
        }
    }
}
