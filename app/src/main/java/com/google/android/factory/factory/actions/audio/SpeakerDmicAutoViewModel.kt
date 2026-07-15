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
import kotlin.random.Random

@GenerateTestArgsUtils
data class SpeakerDmicAutoArgs(
  override var target: AudioTarget = AudioTarget.BUILT_IN_SPEAKER,
  var micTarget: AudioTarget = AudioTarget.BUILT_IN_SPEAKER,
  override val duration: Double = 10.0,
  override var frequency: Double = 1000.0,
  override val sampleRate: Int = 48000,
  override var targetChannel: Int = -1,
  override val bitDepth: Int = 32,
  val recordChannel: Int = -1,
  val rmsMin: Double = 0.005,
  val similarityMin: Double = 0.5,
  val startTimeToSkip: Double = -1.0,
  val useRandomFreq: Boolean = true,
  val minFreq: Double = 1000.0,
  val maxFreq: Double = 3000.0,
  val runAllBuiltinSpeakerMicCombos: Boolean = true,
  var playbackScale: Float = 1.0f,
) : BaseAudioArgs

class SpeakerDmicAutoViewModel : BaseAudioTestViewModel<SpeakerDmicAutoArgs>() {

  override val testTitle = "Audio Turbo Multi-Tone Test"
  private val testFrequencies = mutableListOf<Double>()
  private val testPatterns = mutableListOf<Int>()

  override suspend fun runActionImpl(): FactoryActionResult {
    return withContext(Dispatchers.Default) {
        testFrequencies.clear()
        repeat(5) { testFrequencies.add((Math.round(Random.nextDouble(args.minFreq, args.maxFreq) / 10.0) * 10).toDouble()) }
        
        testPatterns.clear()
        testPatterns.addAll(listOf(0, 1, 0, 1, -1)) // 2L, 2R, 1Both
        testPatterns.shuffle() // SHUFFLE THE ORDER!
        
        if (args.runAllBuiltinSpeakerMicCombos) {
          args.target = AudioTarget.BUILT_IN_SPEAKER
          args.micTarget = AudioTarget.BUILT_IN_SPEAKER
        }

        withContext(Dispatchers.Main) { screenController.title = testTitle }
        val testAudioJack = args.target == AudioTarget.AUDIO_JACK
        deviceManager.waitUntilHeadsetConnected(testAudioJack)

        val file = File(factoryContext.applicationContext.cacheDir, "playback_turbo.wav")
        this@SpeakerDmicAutoViewModel.playbackWavFile = file
        WavValidator.generateMultiToneStereoWav(file.absolutePath, args.duration, testFrequencies, testPatterns, args.sampleRate, args.bitDepth, isReferenceMono = false)

        val playbackDevice = if (args.target == AudioTarget.AUDIO_JACK) DrasDevice.HEADPHONE else DrasDevice.INTERNAL_SPEAKER
        val recordDevice = if (args.micTarget == AudioTarget.AUDIO_JACK) DrasDevice.MIC else DrasDevice.INTERNAL_MIC

        val recordFile = File(factoryContext.applicationContext.cacheDir, "Record_turbo.wav")
        this@SpeakerDmicAutoViewModel.recordWavFile = recordFile
        if (recordFile.exists()) recordFile.delete()
        recordFile.createNewFile()

        withContext(Dispatchers.Main) { updateInstructionAndLogMessage("正在录制音频序列...") }
        val drasResult = drasTool.playAndRecord(file.absolutePath, recordFile.absolutePath, playbackDevice, recordDevice, args.playbackScale)

        if (drasResult.exitCode != 0) {
            val errorOut = drasResult.errText.ifEmpty { drasResult.outText }
            Log.error("dras_tool failed. Exit code: ${drasResult.exitCode}, Error: $errorOut")
            withContext(Dispatchers.Main) { 
                updateInstructionAndLogMessage("录制失败($errorOut)。建议检查 playbackScale 或硬件状态。", true) 
            }
            return@withContext FactoryActionResult.Failure
        }

        withContext(Dispatchers.Main) { updateInstructionAndLogMessage("分析音频波形...") }
        
        val recordedMetadata = WavValidator.readWavFile(recordFile.absolutePath, 0.0, 0.1).metadata
        val numChannels = recordedMetadata.numChannels
        val channelsToAnalyze = if (args.runAllBuiltinSpeakerMicCombos) (0 until numChannels).toList() else listOf(0)

        val refFile = File(factoryContext.applicationContext.cacheDir, "reference_mono.wav")
        WavValidator.generateMultiToneStereoWav(refFile.absolutePath, args.duration, testFrequencies, testPatterns, args.sampleRate, recordedMetadata.bitsPerSample, isReferenceMono = true)

        for (ch in channelsToAnalyze) {
            val micLabel = if (ch == 0) "主麦" else "副麦"
            for (seg in 0 until 5) {
                val start = seg * 2.0 + 0.75 
                val result = WavValidator.compare(refFile.absolutePath, recordFile.absolutePath, ch, args.similarityMin, Pair(args.rmsMin, 1.0), start, 0.5)
                
                if (result.isFailure) {
                    val detail = result.exceptionOrNull()?.message ?: ""
                    withContext(Dispatchers.Main) { updateInstructionAndLogMessage("$micLabel 校验失败: $detail", true) }
                    refFile.delete()
                    return@withContext FactoryActionResult.Failure
                }
            }
        }

        refFile.delete()
        withContext(Dispatchers.Main) { updateInstructionAndLogMessage("测试通过！") }
        FactoryActionResult.Success
    }
  }

  override suspend fun runAudioTestImpl(playbackWavFilePath: String) = FactoryActionResult.Success
}
