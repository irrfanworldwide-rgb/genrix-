package app.genrix.android.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

private val HEADPHONE_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
)

/** With headphones there's no echo, so the user can talk over the reply. */
fun headphonesConnected(context: Context): Boolean {
    val audio = context.getSystemService(AudioManager::class.java) ?: return false
    return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }
}
