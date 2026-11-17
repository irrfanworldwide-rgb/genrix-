package app.genrix.android.orb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.genrix.android.Branding
import app.genrix.android.Diag
import app.genrix.android.MainActivity
import app.genrix.android.R

/**
 * Keeps Genrix alive while the floating orb is on. The orb is a window owned by this app; without
 * a foreground service Android puts the app to sleep soon after the user leaves it, and the orb
 * disappears or stops responding. The silent notification is what Android requires for that.
 */
class OrbService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Floating orb", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps the floating orb on your screen. Silent."
            },
        )
        val open = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${Branding.ASSISTANT_NAME} orb is on")
            .setContentText("Turn it off in Settings, Floating orb.")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            Diag.w("orb: service couldn't go foreground (${e.javaClass.simpleName})")
            stopSelf()
            return START_NOT_STICKY
        }
        FloatingOrb.syncWindow(this)
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "orb"
        private const val NOTIFICATION_ID = 77

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, OrbService::class.java))
            } catch (e: Exception) {
                Diag.w("orb: service start refused (${e.javaClass.simpleName})")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OrbService::class.java))
        }
    }
}
