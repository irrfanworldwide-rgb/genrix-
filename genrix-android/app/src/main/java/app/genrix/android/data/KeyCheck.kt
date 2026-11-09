package app.genrix.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

sealed interface KeyCheckResult {
    data object Ok : KeyCheckResult
    data class Problem(val message: String) : KeyCheckResult
}

/** Asks Gemini about the voice model with this key, before the key is saved. */
suspend fun checkGeminiKey(http: OkHttpClient, key: String, model: String): KeyCheckResult =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model")
            .header("x-goog-api-key", key)
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val methods = runCatching {
                            JSONObject(body).optJSONArray("supportedGenerationMethods")
                        }.getOrNull()
                        val actions = (0 until (methods?.length() ?: 0)).map { methods!!.getString(it) }
                        if (actions.isNotEmpty() && "bidiGenerateContent" !in actions) {
                            KeyCheckResult.Problem("This key works, but not for live voice.")
                        } else {
                            KeyCheckResult.Ok
                        }
                    }
                    response.code == 404 -> KeyCheckResult.Problem("The voice model wasn't found for this key.")
                    response.code == 429 -> KeyCheckResult.Problem("Gemini rate limit reached. Try again in a minute.")
                    response.code in 400..403 -> KeyCheckResult.Problem("Gemini rejected this key. Check it and try again.")
                    else -> KeyCheckResult.Problem("Gemini didn't answer (${response.code}). Try again.")
                }
            }
        } catch (e: IOException) {
            KeyCheckResult.Problem("No connection to Gemini. Check the internet and try again.")
        }
    }
