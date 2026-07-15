package com.google.android.factory.factory.actions.audio

import com.google.android.factory.base.logging.Log
import com.google.android.factory.factory.actions.interfaces.FactoryActionResult
import com.google.android.factory.factory.domain.audio.DrasDevice
import com.google.android.factory.factory.processor.ksp.GenerateTestArgsUtils
import kotlinx.coroutines.awaitCancellation

@GenerateTestArgsUtils
/**
 * Arguments for [PlayAudioViewModel].
 *
 * @property target The target audio device to test.
 * @property duration The duration of the played audio in seconds.
 * @property frequency The frequency of the generated sine wave in Hz.
 * @property sampleRate The sample rate of the audio in Hz.
 * @property targetChannel The target channel to generate audio on (-1 for all channels).
 * @property bitDepth The bit depth of the audio (valid values are 16 or 32).
 * @property scale The volume scale factor for the playback.
 */
data class PlayAudioArgs(
  override var target: AudioTarget = AudioTarget.FRONT_MIC,
  override val duration: Double = 1.0,
  override var frequency: Double = 1000.0,
  override val sampleRate: Int = 48000,
  override val targetChannel: Int = -1,
  override val bitDepth: Int = 32,
  var scale: Float = 1.0f,
) : BaseAudioArgs

class PlayAudioViewModel : BaseAudioTestViewModel<PlayAudioArgs>() {

  override val testTitle = "Play Audio Test"

  override suspend fun runAudioTestImpl(playbackWavFilePath: String): FactoryActionResult {
    val device =
      when (args.target) {
        AudioTarget.AUDIO_JACK -> DrasDevice.HEADPHONE
        AudioTarget.BUILT_IN_SPEAKER -> DrasDevice.INTERNAL_SPEAKER
        AudioTarget.FRONT_MIC,
        AudioTarget.REAR_MIC -> {
          updateInstructionAndLogMessage(
            "Unsupported target: ${args.target} for Play Audio Test.",
            isErrorMsg = true,
          )
          return FactoryActionResult.Failure
        }
      }
    updateInstructionAndLogMessage("Start playback with ${device.deviceName}.")

    Log.info("Starting playback on ${device.deviceName}")
    val result = drasTool.play(playbackWavFilePath, device, args.scale)
    val success = result.exitCode == 0

    if (!success) {
      updateInstructionAndLogMessage(
        "Failed to playback audio on ${device.deviceName}.",
        isErrorMsg = true,
      )
      return FactoryActionResult.Failure
    }

    updateInstructionAndLogMessage("Finished playback successfully.")
    updateInstructionAndLogMessage(
      "Please press Mark Passed or Failed according to the played sound."
    )
    awaitCancellation()
  }
}
