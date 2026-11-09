package app.genrix.android.local

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import app.genrix.android.Diag
import app.genrix.android.control.ControlService
import app.genrix.android.tools.AppMatch
import app.genrix.android.tools.InstalledApp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** What a local command did: a short reply, or "let Gemini handle it" ([handOff]). */
data class LocalResult(val reply: String, val handOff: Boolean = false)

/** Carries out [LocalCommand]s on the phone. No network, no Gemini. */
class LocalActions(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun run(command: LocalCommand): LocalResult = when (command) {
        is LocalCommand.SetVolume -> setVolume(command.percent)
        is LocalCommand.StepVolume -> {
            val direction = if (command.up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            repeat(2) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            LocalResult("Volume ${volumePercent()} percent.")
        }
        is LocalCommand.Mute -> mute(command.on)
        is LocalCommand.Media -> media(command.key)
        is LocalCommand.Torch -> torch(command.on)
        LocalCommand.Battery -> battery()
        LocalCommand.Time -> LocalResult("It's ${SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date())}.")
        LocalCommand.Date -> LocalResult("Today is ${SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(Date())}.")
        is LocalCommand.Timer -> clock(
            Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, command.seconds),
            "Timer set for ${describe(command.seconds)}.",
        )
        is LocalCommand.Alarm -> alarm(command)
        is LocalCommand.OpenApp -> openApp(command.name)
        LocalCommand.Stop -> LocalResult("Okay.")
    }

    private fun setVolume(percent: Int): LocalResult {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (percent * max / 100f).roundToInt(), AudioManager.FLAG_SHOW_UI)
        return LocalResult("Volume ${volumePercent()} percent.")
    }

    private fun volumePercent(): Int =
        audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /** Media muted, and calls set to vibrate (silent needs Do Not Disturb access). */
    private fun mute(on: Boolean): LocalResult {
        val direction = if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        val ringer = runCatching {
            audio.ringerMode = if (on) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        }.isSuccess
        return when {
            on && ringer -> LocalResult("Muted. Calls will vibrate.")
            on -> LocalResult("Media muted.")
            else -> LocalResult("Sound is back on.")
        }
    }

    private fun media(key: MediaKey): LocalResult {
        val code = when (key) {
            MediaKey.Play -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaKey.Pause -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaKey.Next -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaKey.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return LocalResult("") // the music itself is the answer
    }

    private fun torch(on: Boolean): LocalResult {
        val camera = context.getSystemService(CameraManager::class.java)
        val id = camera.cameraIdList.firstOrNull {
            camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return LocalResult("This phone has no flashlight.")
        return runCatching { camera.setTorchMode(id, on) }
            .map { LocalResult(if (on) "Flashlight on." else "Flashlight off.") }
            .getOrElse { LocalResult("The camera is busy, so the flashlight can't change.") }
    }

    private fun battery(): LocalResult {
        val battery = context.getSystemService(BatteryManager::class.java)
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return LocalResult("Battery is at $level percent" + if (battery.isCharging) ", charging." else ".")
    }

    private fun alarm(command: LocalCommand.Alarm): LocalResult {
        val hour = resolveHour(command, Calendar.getInstance())
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, command.minute)
        val shown = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, command.minute)
        }
        return clock(intent, "Alarm set for ${SimpleDateFormat("h:mm a", Locale.ENGLISH).format(shown.time)}.")
    }

    private fun clock(intent: Intent, done: String): LocalResult {
        if (!canOpenApps()) return LocalResult("", handOff = true)
        return if (start(intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true))) LocalResult(done) else LocalResult("The Clock app didn't accept it.")
    }

    private fun openApp(name: String): LocalResult {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0).map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        val matches = AppMatch.find(name, apps)
        // Several matches need a question, and opening from the background needs phone control.
        if (matches.size != 1 || !canOpenApps()) {
            return if (matches.isEmpty()) LocalResult("I couldn't find an app called $name.") else LocalResult("", handOff = true)
        }
        val app = matches.single()
        val intent = pm.getLaunchIntentForPackage(app.packageName) ?: return LocalResult("${app.label} can't be opened from outside.")
        return if (start(intent)) LocalResult("") else LocalResult("${app.label} didn't open.")
    }

    // Android lets apps with a running Accessibility service open other apps from the background.
    private fun canOpenApps() = ControlService.instance != null || Settings.canDrawOverlays(context)

    private fun start(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Diag.w("local: couldn't start ${intent.action} (${it.javaClass.simpleName})") }.isSuccess

    companion object {
        /** 24-hour clock hour for an alarm; with no am/pm, the next time that hour comes round. */
        fun resolveHour(command: LocalCommand.Alarm, now: Calendar): Int {
            val h = command.hour
            return when (command.period) {
                Period.Am -> if (h == 12) 0 else h
                Period.Pm -> if (h == 12) 12 else h + 12
                null -> if (h > 12 || h == 0) {
                    h
                } else {
                    val minutesNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                    val morning = (h % 12) * 60 + command.minute
                    if (morning > minutesNow || morning + 12 * 60 <= minutesNow) h % 12 else h % 12 + 12
                }
            }
        }

        fun describe(seconds: Int): String {
            val h = seconds / 3600
            val m = seconds % 3600 / 60
            val s = seconds % 60
            return listOfNotNull(
                h.takeIf { it > 0 }?.let { "$it hour" + if (it > 1) "s" else "" },
                m.takeIf { it > 0 }?.let { "$it minute" + if (it > 1) "s" else "" },
                s.takeIf { it > 0 }?.let { "$it second" + if (it > 1) "s" else "" },
            ).joinToString(" ")
        }
    }
}
