package app.genrix.android.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.genrix.android.Branding
import app.genrix.android.Diag
import app.genrix.android.MainActivity
import app.genrix.android.R
import java.time.LocalDateTime
import java.time.ZoneId

/** Genrix's own reminders: a notification at the time, kept across restarts. */
class Reminders(private val context: Context) {
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun all(): List<Reminder> = ReminderRules.decode(prefs.getString(KEY, null))

    fun add(text: String, at: LocalDateTime): Reminder {
        val reminder = Reminder(System.currentTimeMillis(), text, at)
        save(all() + reminder)
        schedule(reminder)
        return reminder
    }

    fun cancel(reminder: Reminder) {
        alarms.cancel(pending(reminder.id))
        save(all().filterNot { it.id == reminder.id })
    }

    /** After a reboot alarms are gone; set them again (overdue ones fire now). */
    fun rescheduleAll() = all().forEach(::schedule)

    /** True if reminders will ring on time; otherwise Android may delay them a few minutes. */
    fun exact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    private fun schedule(reminder: Reminder) {
        val millis = reminder.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (exact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(reminder.id))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(reminder.id))
        }
    }

    internal fun fire(id: Long) {
        val reminder = all().firstOrNull { it.id == id } ?: return
        save(all().filterNot { it.id == id })
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${Branding.ASSISTANT_NAME} reminder")
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id.toInt(), notification) }
            .onFailure { Diag.w("reminder: notification blocked") }
        Diag.i("reminder: rang")
    }

    private fun save(list: List<Reminder>) = prefs.edit().putString(KEY, ReminderRules.encode(list)).apply()

    private fun pending(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val KEY = "list"
        const val CHANNEL = "reminders"
        const val EXTRA_ID = "id"
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Reminders(context).fire(intent.getLongExtra(EXTRA_ID, -1))
        }
    }

    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders(context).rescheduleAll()
        }
    }
}
