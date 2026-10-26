package app.permatasks.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Text-to-speech for hands-free use. Reading out reminders only happens while a Bluetooth
 * audio device (typically the car) is connected, so it never talks out loud in a meeting.
 */
class Speaker(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<String>()

    @Synchronized
    fun speak(text: String) {
        val engine = tts
        if (engine != null && ready) {
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
            return
        }
        pending += text
        if (engine == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                synchronized(this) {
                    ready = status == TextToSpeech.SUCCESS
                    val device = Locale.getDefault()
                    tts?.language = if (device.language == "en") device else Locale.US
                    if (ready) pending.forEach { tts?.speak(it, TextToSpeech.QUEUE_ADD, null, it.hashCode().toString()) }
                    pending.clear()
                }
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun isInCar(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }

    fun speakIfInCar(titles: List<String>) {
        if (!isInCar()) return
        speak(
            if (titles.size == 1) "Reminder: ${titles.first()}. Have you done it?"
            else "You have ${titles.size} reminders: ${titles.joinToString(". ")}.",
        )
    }
}
