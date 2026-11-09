package app.genrix.android.local

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.genrix.android.Diag
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors

/**
 * Hears one request with Vosk's small Indian-English model, entirely on the phone (no
 * network). The model ships in the APK's assets and is copied to app storage once, because
 * Vosk reads it from files.
 */
class OfflineSpeech private constructor(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "offline-speech") }
    @Volatile private var model: Model? = null

    fun available(): Boolean = runCatching { context.assets.list(ASSETS)?.isNotEmpty() == true }.getOrDefault(false)

    /** Listens for up to [MAX_MS]; [done] gets up to three guesses (none if nothing was said). */
    fun listen(done: (List<String>) -> Unit) {
        worker.execute {
            val heard = runCatching { hear() }.getOrElse {
                Diag.w("local: offline speech failed (${it.javaClass.simpleName})")
                emptyList()
            }
            main.post { done(heard) }
        }
    }

    /** Frees the model's memory (about the model's size); the next request loads it again. */
    fun release() {
        worker.execute {
            model?.close()
            model = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun hear(): List<String> {
        val rate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, rate * 2 * BUFFER_S),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            Diag.w("local: mic busy")
            return emptyList()
        }
        // Record first: the mic buffer keeps what the user says while the model loads.
        record.startRecording()
        val loaded = runCatching { model() }.getOrElse {
            record.stop()
            record.release()
            throw it
        }
        return Recognizer(loaded, rate.toFloat()).use { recognizer ->
            recognizer.setMaxAlternatives(3)
            val chunk = ShortArray(CHUNK)
            val start = SystemClock.elapsedRealtime()
            var speaking = false
            try {
                while (true) {
                    val read = record.read(chunk, 0, chunk.size)
                    if (read <= 0) break
                    val ended = recognizer.acceptWaveForm(chunk, read)
                    if (ended && speaking) break
                    val elapsed = SystemClock.elapsedRealtime() - start
                    if (!speaking) speaking = partial(recognizer.partialResult).isNotEmpty()
                    if ((!speaking && elapsed > NO_SPEECH_MS) || elapsed > MAX_MS) break
                }
            } finally {
                record.stop()
                record.release()
            }
            alternatives(recognizer.finalResult)
        }
    }

    /** The loaded model (loads it if needed; blocks). */
    @Synchronized
    fun model(): Model = model ?: load().also { model = it }

    private fun load(): Model {
        val dir = File(context.filesDir, MODEL_DIR)
        val ready = File(dir, ".ready")
        if (!ready.exists()) {
            val began = SystemClock.elapsedRealtime()
            dir.deleteRecursively()
            copy(ASSETS, dir)
            ready.writeText("ok")
            Diag.i("local: offline speech model unpacked in ${SystemClock.elapsedRealtime() - began} ms")
        }
        val began = SystemClock.elapsedRealtime()
        return Model(dir.absolutePath).also { Diag.i("local: offline speech model loaded in ${SystemClock.elapsedRealtime() - began} ms") }
    }

    private fun copy(asset: String, target: File) {
        val children = context.assets.list(asset).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(asset).use { input -> target.outputStream().use { input.copyTo(it) } }
            return
        }
        children.forEach { copy("$asset/$it", File(target, it)) }
    }

    private fun partial(json: String) = runCatching { JSONObject(json).optString("partial") }.getOrDefault("")

    companion object {
        @Volatile private var instance: OfflineSpeech? = null

        fun get(context: Context): OfflineSpeech = instance ?: synchronized(this) {
            instance ?: OfflineSpeech(context.applicationContext).also { instance = it }
        }

        private const val ASSETS = "vosk"
        private const val MODEL_DIR = "vosk-small-en-in-0.4"
        private const val CHUNK = 1600 // 100 ms
        private const val NO_SPEECH_MS = 5_000L
        private const val MAX_MS = 8_000L
        private const val BUFFER_S = 4 // covers loading the model (about 0.8 s on the test phone)

        /** Vosk's alternatives JSON → texts, best first, blanks dropped. */
        fun alternatives(json: String): List<String> = runCatching {
            val root = JSONObject(json)
            val list = root.optJSONArray("alternatives")
            if (list == null) {
                listOf(root.optString("text"))
            } else {
                (0 until list.length()).map { list.getJSONObject(it).optString("text") }
            }
        }.getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
