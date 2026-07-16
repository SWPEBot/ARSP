package com.google.android.factory.factory.domain.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.google.android.factory.factory.data.interfaces.FactoryContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

/**
 * Data class to store information about an audio device.
 *
 * @property name The name of the audio device.
 * @property id The ID of the audio device.
 * @property type The type of the audio device (e.g., [AudioDeviceInfo.TYPE_BUILTIN_SPEAKER]).
 * @property channelCnt The maximum number of channels supported by the audio device.
 */
data class AudioInfo(
  val name: String = "",
  val id: Int = -1,
  val type: Int = -1,
  val channelCnt: Int = -1,
) {
  override fun toString(): String {
    val typeString =
      when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in Earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line Analog"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line Digital"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Device"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
        AudioDeviceInfo.TYPE_DOCK -> "Dock"
        AudioDeviceInfo.TYPE_FM -> "FM"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
        AudioDeviceInfo.TYPE_FM_TUNER -> "FM Tuner"
        AudioDeviceInfo.TYPE_TV_TUNER -> "TV Tuner"
        AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
        AudioDeviceInfo.TYPE_AUX_LINE -> "Auxiliary Line"
        AudioDeviceInfo.TYPE_IP -> "IP"
        AudioDeviceInfo.TYPE_BUS -> "Bus"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
        AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing Aid"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Built-in Speaker Safe"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Remote Submix"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth Low Energy headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth Low Energy speaker"
        else -> "Unknown Device Type id: $type"
      }
    return "AudioInfo(name='$name', id=$id, type=$typeString, channelCnt=$channelCnt)"
  }

  /** Returns true if the device is a wired or USB headset/headphones. */
  fun isWiredHeadset(): Boolean {
    return type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
      type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
      type == AudioDeviceInfo.TYPE_USB_HEADSET ||
      type == AudioDeviceInfo.TYPE_USB_DEVICE
  }
}

/**
 * Manager for handling audio device monitoring and state.
 *
 * It provides a flow of currently connected audio devices and tracks headset status.
 */
class AudioDeviceManager(private val factoryContext: FactoryContext) {

  private val audioManager by lazy {
    factoryContext.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }

  /** Returns a list of currently connected audio devices. */
  fun getConnectedDevices(): List<AudioInfo> {
    return audioManager
      .getDevices(AudioManager.GET_DEVICES_INPUTS or AudioManager.GET_DEVICES_OUTPUTS)
      .map { mapAudioDeviceInfo(it) }
  }

  /** Returns a [Flow] that emits the list of connected audio devices whenever it changes. */
  fun getConnectedDevicesFlow(): Flow<List<AudioInfo>> = callbackFlow {
    val listener =
      object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo?>?) {
          trySend(getConnectedDevices())
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo?>?) {
          trySend(getConnectedDevices())
        }
      }

    audioManager.registerAudioDeviceCallback(listener, null)
    trySend(getConnectedDevices())

    awaitClose { audioManager.unregisterAudioDeviceCallback(listener) }
  }

  private fun mapAudioDeviceInfo(deviceInfo: AudioDeviceInfo): AudioInfo {
    return AudioInfo(
      name = deviceInfo.productName.toString(),
      id = deviceInfo.id,
      type = deviceInfo.type,
      channelCnt = deviceInfo.channelCounts.maxOrNull() ?: 0,
    )
  }

  /** Waits until the audio jack status matches the expected state. */
  suspend fun waitUntilHeadsetConnected(expected: Boolean) {
    getConnectedDevicesFlow().first { devices ->
      devices.any { it.isWiredHeadset() } == expected
    }
  }
}
