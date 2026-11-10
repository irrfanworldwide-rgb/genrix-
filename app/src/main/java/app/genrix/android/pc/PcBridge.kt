package app.genrix.android.pc

import android.content.Context
import app.genrix.android.Diag
import app.genrix.android.data.KeyVault
import app.genrix.android.data.Settings
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Talks to the Genrix PC helper (pc-helper/genrix_pc.py) on the user's own computer.
 *
 * Every request is signed with HMAC-SHA256 over method, path, time, a random nonce and the
 * body, so the token itself never travels and a captured request can't be replayed. The
 * helper accepts only a fixed list of actions; there is no way to run arbitrary commands.
 * Only home-network addresses are allowed (plain http), plus Tailscale's 100.64/10 range.
 */
object PcBridge {
    data class Reply(val ok: Boolean, val message: String)

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val random = SecureRandom()

    /** True when an address and a token are saved. */
    fun configured(context: Context): Boolean =
        Settings(context).pcUrl.isNotEmpty() && !KeyVault(context).read(KeyVault.PC_TOKEN).isNullOrEmpty()

    /** Home-network or Tailscale addresses only: the token is never sent to the open internet. */
    fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local")) return true
        val p = host.split('.').map { it.toIntOrNull() ?: return false }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || p[0] == 127 ||
            (p[0] == 192 && p[1] == 168) ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 100 && p[1] in 64..127)
    }

    /** The checked address ("http://host:port") or null when it isn't a usable home-network address. */
    fun cleanUrl(raw: String): String? {
        val url = raw.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        if (url.scheme != "http" || !isPrivateHost(url.host)) return null
        return "http://${url.host}:${url.port}"
    }

    /** hex(HMAC-SHA256(token, "POST\n<path>\n<ts>\n<nonce>\n<body>")): the helper computes the same. */
    fun sign(token: String, path: String, ts: String, nonce: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal("POST\n$path\n$ts\n$nonce\n$body".toByteArray(Charsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }
    }

    suspend fun call(http: OkHttpClient, context: Context, action: String, args: JSONObject = JSONObject()): Reply =
        withContext(Dispatchers.IO) {
            val base = cleanUrl(Settings(context).pcUrl)
                ?: return@withContext Reply(false, "The PC address in Settings isn't a home-network address (http://192.168.x.x:8765).")
            val token = KeyVault(context).read(KeyVault.PC_TOKEN)
                ?: return@withContext Reply(false, "No PC token saved in Settings.")
            val path = "/v1/$action"
            val body = args.toString()
            val ts = (System.currentTimeMillis() / 1000).toString()
            val nonce = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            val request = Request.Builder()
                .url(base + path)
                .post(body.toRequestBody(JSON))
                .header("X-Genrix-Ts", ts)
                .header("X-Genrix-Nonce", nonce)
                .header("X-Genrix-Sig", sign(token, path, ts, nonce, body))
                .build()
            val client = http.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
            try {
                client.newCall(request).execute().use { response ->
                    val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                    Diag.i("pc: $action -> ${response.code}")
                    when {
                        response.code == 401 -> Reply(false, "The PC rejected the token or the clocks differ by minutes. Check both.")
                        json == null -> Reply(false, "The PC sent a reply Genrix couldn't read (${response.code}).")
                        else -> Reply(json.optBoolean("ok", false), json.optString("message").take(300))
                    }
                }
            } catch (_: IOException) {
                Diag.w("pc: $action unreachable")
                Reply(false, "Can't reach the PC. Is the helper running, and is the phone on the same Wi-Fi?")
            }
        }
}
