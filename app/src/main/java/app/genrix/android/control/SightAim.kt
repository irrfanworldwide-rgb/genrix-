package app.genrix.android.control

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import app.genrix.android.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Click-by-sight for screens with no readable controls (games, canvases, image buttons).
 * Draws an [AimGrid] on a screenshot, asks Gemini (the user's own key) which cell holds the
 * target, then repeats with a finer grid on the cells around it. The screenshot goes to
 * Gemini only for this request and is never stored or logged.
 */
class SightAim(private val http: OkHttpClient, private val apiKey: String) {
    sealed interface Outcome {
        data class Found(val x: Float, val y: Float, val sees: String) : Outcome
        data object NotFound : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun locate(shot: Bitmap, target: String): Outcome = withContext(Dispatchers.IO) {
        val coarse = AimGrid.coarse(shot.width.toFloat(), shot.height.toFloat())
        val first = ask(shot, coarse, target, fine = false) ?: return@withContext Outcome.Failed(FAILED)
        val cell = first.cell?.let(coarse::cell) ?: return@withContext Outcome.NotFound
        Diag.i("sight: coarse ${first.cell}")
        val fine = AimGrid.fine(coarse.around(first.cell)!!)
        val second = ask(shot, fine, target, fine = true)
        val exact = second?.cell?.let(fine::cell)
        Diag.i("sight: fine ${second?.cell ?: "-"}")
        val final = exact ?: cell
        Outcome.Found(final.centerX, final.centerY, (if (exact != null) second.sees else "").ifEmpty { first.sees })
    }

    private fun ask(shot: Bitmap, grid: AimGrid, target: String, fine: Boolean): AimAnswer? {
        val image = Base64.encodeToString(jpeg(draw(shot, grid)), Base64.NO_WRAP)
        val parts = JSONArray()
            .put(JSONObject().put("text", AimReply.prompt(target, grid, fine)))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", image)))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0))
        val request = Request.Builder()
            .url("$ENDPOINT/$MODEL:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Diag.w("sight: HTTP ${response.code}")
                    return null
                }
                val text = JSONObject(response.body!!.string())
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                AimReply.parse(text)
            }
        }.onFailure { Diag.w("sight: ${it.javaClass.simpleName}") }.getOrNull()
    }

    /** The grid's area cut from [shot], scaled to at most [MAX_SIDE] pixels, with lines and labels. */
    private fun draw(shot: Bitmap, grid: AimGrid): Bitmap {
        val a = grid.area
        val crop = Bitmap.createBitmap(shot, a.left.toInt(), a.top.toInt(), a.width.toInt(), a.height.toInt())
        val scale = minOf(1f, MAX_SIDE / maxOf(a.width, a.height))
        val out = Bitmap.createScaledBitmap(crop, (a.width * scale).toInt(), (a.height * scale).toInt(), true)
            .copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val line = Paint().apply { color = Color.MAGENTA; strokeWidth = 2f; alpha = 200 }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = LABEL_PX; isFakeBoldText = true }
        val tag = Paint().apply { color = Color.MAGENTA; alpha = 210 }
        for ((label, box) in grid.cells()) {
            val x = (box.left - a.left) * scale
            val y = (box.top - a.top) * scale
            canvas.drawRect(x, y, x + box.width * scale, y + box.height * scale, line.apply { style = Paint.Style.STROKE })
            canvas.drawRect(x, y, x + text.measureText(label) + 6f, y + LABEL_PX + 4f, tag)
            canvas.drawText(label, x + 3f, y + LABEL_PX, text)
        }
        return out
    }

    private fun jpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()

    private companion object {
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        const val MODEL = "gemini-flash-latest"
        const val MAX_SIDE = 1024f
        const val LABEL_PX = 22f
        const val FAILED = "Gemini couldn't look at the screen just now."
    }
}
