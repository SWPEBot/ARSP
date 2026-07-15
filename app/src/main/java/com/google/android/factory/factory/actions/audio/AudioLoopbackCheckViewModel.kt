package com.google.android.factory.factory.actions.audio

import com.google.android.factory.base.logging.Log
import com.google.android.factory.factory.actions.interfaces.FactoryActionResult
import com.google.android.factory.factory.domain.audio.DrasDevice
import com.google.android.factory.factory.domain.audio.WavValidator
import com.google.android.factory.factory.processor.ksp.GenerateTestArgsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

@GenerateTestArgsUtils
/**
 * Arguments for the audio loopback check action.
 */
data class AudioLoopbackCheckArgs(
  override var target: AudioTarget = AudioTarget.FRONT_MIC,
  override val duration: Double = 1.0,
  override var frequency: Double = 1000.0,
  override val sampleRate: Int = 48000,
  override val targetChannel: Int = -1,
  override val bitDepth: Int = 32,
  var similarityThreshold: Double = 0.7,
  var rmsThreshold: Pair<Double, Double> = Pair(0.02, Double.MAX_VALUE),
  var startTimeToSkip: Double = -1.0,
  val recordChannel: Int = -1,
) : BaseAudioArgs

internal data class AudioTestConfig(
  val effectiveRmsThreshold: Pair<Double, Double>,
  val playbackDevice: DrasDevice,
  val recordDevice: DrasDevice,
  val testItemName: String,
)

class AudioLoopbackCheckViewModel : BaseAudioTestViewModel<AudioLoopbackCheckArgs>() {

  override val testTitle = "Audio Loopback Test (Turbo)"

  override suspend fun runActionImpl(): FactoryActionResult {
    return withContext(Dispatchers.Default) {
        withContext(Dispatchers.Main) { screenController.title = testTitle }
        val testAudioJack = args.target == AudioTarget.AUDIO_JACK
        val devicesFlow = deviceManager.getConnectedDevicesFlow()
        val initialPlugged = devicesFlow.first().any { it.isWiredHeadset() }
        
        if (initialPlugged != testAudioJack) {
          val action = if (initialPlugged) "remove" else "plugin"
          withContext(Dispatchers.Main) { updateInstructionAndLogMessage("Please $action the audio jack.") }
        }

        deviceManager.waitUntilHeadsetConnected(testAudioJack)
        withContext(Dispatchers.Main) { updateInstructionAndLogMessage("Ready to run.") }

        val file = File(factoryContext.applicationContext.cacheDir, "loopback_playback.wav")
        this@AudioLoopbackCheckViewModel.playbackWavFile = file
        WavValidator.generateSineWaveWav(file.absolutePath, args.duration, args.frequency, args.sampleRate, 2, args.targetChannel, args.bitDepth)

        runAudioTestImpl(file.absolutePath)
    }
  }

  override suspend fun runAudioTestImpl(playbackWavFilePath: String): FactoryActionResult {
    val config = getAudioLoopbackConfig(args.target)

    val recordFile = File(factoryContext.applicationContext.cacheDir, "loopback_record.wav")
    this.recordWavFile = recordFile
    if (recordFile.exists()) recordFile.delete()
    recordFile.createNewFile()

    withContext(Dispatchers.Main) { updateInstructionAndLogMessage("Loopback Testing: ${config.playbackDevice.deviceName} -> ${config.recordDevice.deviceName}") }
    
    val drasResult = drasTool.playAndRecord(playbackWavFilePath, recordFile.absolutePath, config.playbackDevice, config.recordDevice)
      
    if (drasResult.exitCode != 0) {
        withContext(Dispatchers.Main) { updateInstructionAndLogMessage("Dras error", true) }
        return FactoryActionResult.Failure
    }

    withContext(Dispatchers.Main) { updateInstructionAndLogMessage("Analyzing loopback signal...") }
    
    val recordedMetadata = WavValidator.readWavFile(recordFile.absolutePath, 0.0, 0.1).metadata
    val ch = if (args.recordChannel == -1) 0 else args.recordChannel
    val skipTime = if (args.startTimeToSkip < 0) min(args.duration * 0.1, 0.1) else args.startTimeToSkip

    val referenceWav = File(factoryContext.applicationContext.cacheDir, "loopback_ref.wav")
    WavValidator.generateSineWaveWav(referenceWav.absolutePath, args.duration, args.frequency, args.sampleRate, 1, -1, recordedMetadata.bitsPerSample)

    val result = WavValidator.compare(
      f1 = referenceWav.absolutePath,
      f2 = recordFile.absolutePath,
      channel = ch,
      threshold = args.similarityThreshold,
      rmsRange = args.rmsThreshold,
      start = skipTime,
      duration = args.duration - (skipTime * 2)
    )

    referenceWav.delete()

    return result.fold(
      onSuccess = {
        withContext(Dispatchers.Main) { updateInstructionAndLogMessage("Passed.") }
        FactoryActionResult.Success
      },
      onFailure = { e ->
        withContext(Dispatchers.Main) { updateInstructionAndLogMessage("Failed: ${e.message}", true) }
        FactoryActionResult.Failure
      },
    )
  }

  private fun getAudioLoopbackConfig(target: AudioTarget): AudioTestConfig {
    return when (target) {
      AudioTarget.AUDIO_JACK -> AudioTestConfig(Pair(0.05, 1.0), DrasDevice.HEADPHONE, DrasDevice.MIC, "audio jack")
      AudioTarget.FRONT_MIC -> AudioTestConfig(Pair(0.001, 1.0), DrasDevice.INTERNAL_SPEAKER, DrasDevice.FRONT_MIC, "front mic")
      AudioTarget.REAR_MIC -> AudioTestConfig(Pair(0.001, 1.0), DrasDevice.INTERNAL_SPEAKER, DrasDevice.REAR_MIC, "rear mic")
      else -> AudioTestConfig(Pair(0.01, 1.0), DrasDevice.INTERNAL_SPEAKER, DrasDevice.INTERNAL_MIC, "internal speaker")
    }
  }
}
