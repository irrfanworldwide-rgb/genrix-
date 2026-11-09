package app.genrix.android

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Keeps the diagnostic log in the app's private files so a test survives a restart or a
 * full system log: `adb shell run-as app.genrix.android cat files/diag.log` (debug builds).
 * Two files of [limit] bytes at most; the older one is dropped. Never backed up or sent.
 */
class DiagFile(dir: File, private val limit: Long = LIMIT) {
    private val current = File(dir, NAME)
    private val previous = File(dir, OLD_NAME)
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "diag").apply { isDaemon = true } }
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun add(level: Char, message: String) {
        val line = "${time.format(Date())} $level $message\n"
        writer.execute { runCatching { write(line) } }
    }

    /** Waits for queued lines; for tests. */
    fun flush() {
        writer.submit {}.get()
    }

    private fun write(line: String) {
        if (current.length() + line.length > limit) {
            previous.delete()
            current.renameTo(previous)
        }
        current.appendText(line)
    }

    companion object {
        const val NAME = "diag.log"
        const val OLD_NAME = "diag.1.log"
        private const val LIMIT = 256 * 1024L
    }
}
