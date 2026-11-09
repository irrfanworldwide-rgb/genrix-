package app.genrix.android

import android.util.Log

/**
 * Diagnostic log, read with `adb logcat -s Genrix` and kept in [DiagFile]. States and
 * timings only: never what was said, screen text or keys.
 */
object Diag {
    private const val TAG = "Genrix"
    private val KEY_SHAPED = Regex("AIza[0-9A-Za-z_-]{10,}")
    @Volatile private var file: DiagFile? = null

    fun start(file: DiagFile) {
        this.file = file
    }

    fun i(message: String) {
        Log.i(TAG, message)
        file?.add('I', message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
        file?.add('W', message)
    }

    /** Server or error text: shortened, with anything that looks like a key blanked. */
    fun clean(text: String?): String = KEY_SHAPED.replace(text.orEmpty(), "[key]").take(160)
}
