package app.genrix.android.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.genrix.android.Diag

/**
 * Wave a hand over the top of the phone to cut a reply off: the proximity sensor, watched
 * only while a reply plays. The phone's echo canceller lets too much of the reply through
 * for a spoken "stop" to be heard reliably on the loudspeaker (measured on the test phone),
 * so this is the hands-free way; with headphones, talking over the reply works too.
 */
class WaveStop(context: Context, private val onWave: () -> Unit) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val proximity: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var watching = false
    private var wasNear: Boolean? = null

    val available: Boolean get() = proximity != null

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Watches while [on]; costs nothing otherwise. Any thread. */
    fun watch(on: Boolean) {
        main.post { set(on) }
    }

    private fun set(on: Boolean) {
        if (on == watching || proximity == null) return
        watching = on
        wasNear = null
        if (on) sensors.registerListener(this, proximity, SensorManager.SENSOR_DELAY_UI) else sensors.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val near = event.values[0] < (proximity?.maximumRange ?: 5f).coerceAtMost(NEAR_CM)
        // The first reading is the current state (a phone already covered, in a pocket or by the
        // ear): only a change from far to near counts as a wave.
        if (near && wasNear == false) {
            Diag.i("reply: wave seen")
            onWave()
        }
        wasNear = near
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val NEAR_CM = 5f
    }
}
