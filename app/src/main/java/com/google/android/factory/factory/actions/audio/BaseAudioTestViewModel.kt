package com.google.android.factory.factory.actions.audio

import com.google.android.factory.base.logging.Log
import com.google.android.factory.factory.actions.base.FactoryActionViewModel
import com.google.android.factory.factory.actions.common.SimpleMessageScreenController
import com.google.android.factory.factory.actions.common.SimpleMessageScreenViewModelInterface
import com.google.android.factory.factory.actions.interfaces.FactoryActionResult
import com.google.android.factory.factory.domain.audio.AudioDeviceManager
import com.google.android.factory.factory.domain.audio.DrasTool
import com.google.android.factory.factory.domain.audio.WavValidator
import java.io.File
import kotlinx.coroutines.flow.first

enum class AudioTarget {
  AUDIO_JACK,
  BUILT_IN_SPEAKER,
  FRONT_MIC,
  REAR_MIC,
}

interface BaseAudioArgs {
  var target: AudioTarget
  val duration: Double
  var frequency: Double
  val sampleRate: Int
  val targetChannel: Int
  val bitDepth: Int
}

abstract class BaseAudioTestViewModel<T : BaseAudioArgs>(
  protected val screenController: SimpleMessageScreenController = SimpleMessageScreenController()
) : FactoryActionViewModel<T>(), SimpleMessageScreenViewModelInterface by screenController {

  protected val deviceManager by lazy { AudioDeviceManager(factoryContext) }
  protected val drasTool by lazy { DrasTool(factoryContext) }

  protected var playbackWavFile: File? = null
  protected var recordWavFile: File? = null

  protected abstract val testTitle: String

  protected abstract suspend fun runAudioTestImpl(playbackWavFilePath: String): FactoryActionResult

  protected fun updateInstructionAndLogMessage(message: String, isErrorMsg: Boolean = false) {
    screenController.content = message
    if (isErrorMsg) {
      Log.error(message)
    } else {
      Log.info(message)
    }
  }

  override suspend fun runActionImpl(): FactoryActionResult {
    screenController.title = testTitle

    val testAudioJack = args.target == AudioTarget.AUDIO_JACK

    val devicesFlow = deviceManager.getConnectedDevicesFlow()
    val initialPlugged = devicesFlow.first().any { it.isWiredHeadset() }
    if (initialPlugged != testAudioJack) {
      val action = if (initialPlugged) "remove" else "plugin"
      updateInstructionAndLogMessage("Please $action the audio jack.")
    }

    deviceManager.waitUntilHeadsetConnected(testAudioJack)
    updateInstructionAndLogMessage("Ready to run the test.")

    if (args.bitDepth != 16 && args.bitDepth != 32) {
      Log.error("Bit depth must be 16 or 32, but got ${args.bitDepth}")
      return FactoryActionResult.Failure
    }

    val file = File(factoryContext.applicationContext.cacheDir, "playbackFilePath.wav")
    this.playbackWavFile = file

    // Note: The bitDepth should be 16 for audio jack loopback test only.
    WavValidator.generateSineWaveWav(
      file.absolutePath,
      args.duration,
      args.frequency,
      args.sampleRate,
      numChannels = 2,
      targetChannel = args.targetChannel,
      bitDepth = args.bitDepth,
    )

    return runAudioTestImpl(file.absolutePath)
  }

  override suspend fun tearDown() {
    playbackWavFile?.delete()
    recordWavFile?.delete()
  }
}
