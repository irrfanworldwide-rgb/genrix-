package app.genrix.android.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import java.nio.FloatBuffer

/**
 * openWakeWord on the phone: every 80 ms of 16 kHz audio becomes mel frames, the last 76
 * frames become one 96-number speech embedding, and the last 16 embeddings give the
 * wake-word score (0..1). Nothing leaves the phone.
 *
 * The models live in the debug build's assets only: the pretrained "hey jarvis" model is
 * CC BY-NC-SA 4.0 (non-commercial), so it must not ship in a release (docs/ANDROID.md).
 */
class WakeWordModel(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val mel = load(context, "melspectrogram.onnx")
    private val embedding = load(context, "embedding_model.onnx")
    private val word = load(context, WORD_MODEL)
    private val wordFrames = ((word.inputInfo.values.first().info as TensorInfo).shape[1]).toInt()

    private val raw = ShortArray(CONTEXT + CHUNK)
    private val melFrames = ArrayDeque<FloatArray>()
    private val embeddings = ArrayDeque<FloatArray>()
    private var scored = 0

    init {
        reset()
    }

    fun reset() {
        raw.fill(0)
        melFrames.clear()
        repeat(MEL_WINDOW) { melFrames.addLast(FloatArray(MEL_BINS) { 1f }) }
        embeddings.clear()
        scored = 0
    }

    /** Scores one 80 ms chunk ([CHUNK] samples). The first few scores are always 0. */
    fun score(chunk: ShortArray): Float {
        val score = rawScore(chunk) ?: return 0f
        scored++
        return if (scored <= WARM_UP) 0f else score
    }

    /** The speech-feature window behind the last score (for the voice profile). */
    fun window(): FloatArray = FloatArray(wordFrames * EMBEDDING).also { features ->
        embeddings.forEachIndexed { i, e -> e.copyInto(features, i * EMBEDDING) }
    }

    /**
     * Voice setup: the feature window where [audio] sounds most like the phrase, and that
     * score. Null if the recording is too short (needs about 1.5 s before the phrase).
     */
    fun bestWindow(audio: ShortArray): Pair<FloatArray, Float>? {
        reset()
        var best: Pair<FloatArray, Float>? = null
        var start = 0
        while (start + CHUNK <= audio.size) {
            val score = rawScore(audio.copyOfRange(start, start + CHUNK))
            if (score != null && score > (best?.second ?: -1f)) best = window() to score
            start += CHUNK
        }
        reset()
        return best
    }

    private fun rawScore(chunk: ShortArray): Float? {
        // Keep the last 30 ms of the previous chunk so mel frames line up across chunks.
        System.arraycopy(raw, CHUNK, raw, 0, CONTEXT)
        System.arraycopy(chunk, 0, raw, CONTEXT, CHUNK)
        addMelFrames()
        addEmbedding()
        if (embeddings.size < wordFrames) return null
        return run(word, window(), longArrayOf(1, wordFrames.toLong(), EMBEDDING.toLong()))[0]
    }

    private fun addMelFrames() {
        val audio = FloatArray(raw.size) { raw[it].toFloat() }
        val out = run(mel, audio, longArrayOf(1, audio.size.toLong()))
        for (f in 0 until out.size / MEL_BINS) {
            melFrames.addLast(FloatArray(MEL_BINS) { out[f * MEL_BINS + it] / 10f + 2f })
        }
        while (melFrames.size > MEL_WINDOW) melFrames.removeFirst()
    }

    private fun addEmbedding() {
        val window = FloatArray(MEL_WINDOW * MEL_BINS)
        melFrames.forEachIndexed { i, frame -> frame.copyInto(window, i * MEL_BINS) }
        embeddings.addLast(run(embedding, window, longArrayOf(1, MEL_WINDOW.toLong(), MEL_BINS.toLong(), 1)))
        while (embeddings.size > wordFrames) embeddings.removeFirst()
    }

    private fun run(session: OrtSession, data: FloatArray, shape: LongArray): FloatArray =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { result ->
                val buffer = (result[0] as OnnxTensor).floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }

    private fun load(context: Context, name: String): OrtSession =
        env.createSession(context.assets.open("$DIR/$name").use { it.readBytes() }, options())

    /**
     * One thread, no spinning. ONNX Runtime's defaults (a thread per core that busy-waits
     * between runs) kept about 2.8 cores busy around the clock on the test phone.
     */
    private fun options() = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(1)
        setInterOpNumThreads(1)
        setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        addConfigEntry("session.intra_op.allow_spinning", "0")
        addConfigEntry("session.inter_op.allow_spinning", "0")
    }

    override fun close() {
        mel.close()
        embedding.close()
        word.close()
    }

    companion object {
        const val CHUNK = 1280 // 80 ms at 16 kHz
        // What the pretrained model really hears. A "Hey Genrix" phrase needs its own trained model.
        const val PHRASE = "Hey Jarvis"
        private const val CONTEXT = 480
        private const val MEL_BINS = 32
        private const val MEL_WINDOW = 76
        private const val EMBEDDING = 96
        private const val WARM_UP = 5
        private const val DIR = "wake"
        private const val WORD_MODEL = "hey_jarvis_v0.1.onnx"

        /** False in builds without the models (release builds). */
        fun available(context: Context): Boolean =
            runCatching { context.assets.list(DIR)?.contains(WORD_MODEL) == true }.getOrDefault(false)
    }
}
