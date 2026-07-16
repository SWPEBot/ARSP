package com.google.android.factory.factory.domain.audio

import com.google.android.factory.base.adb.interfaces.AdbShell
import com.google.android.factory.factory.data.interfaces.FactoryContext
import com.google.android.factory.factory.domain.file.withRawResourceFileAbsPath

/** Audio device names for dras_tool. */
enum class DrasDevice(val deviceName: String) {
  INTERNAL_SPEAKER("Speaker"),
  HEADPHONE("Headphone"),
  MIC("Mic"),
  INTERNAL_MIC("InternalMic"),
  FRONT_MIC("FrontMic"),
  REAR_MIC("RearMic"),
}

/** A wrapper of dras_tool (audio/dras/src/bin/dras_tool.rs) for audio tests. */
class DrasTool(private val factoryContext: FactoryContext) {
  suspend fun playWavResource(
    resId: Int,
    deviceName: DrasDevice = DrasDevice.INTERNAL_SPEAKER,
    scale: Float = 1.0f,
  ): AdbShell.Result {
    return factoryContext.applicationContext.withRawResourceFileAbsPath(resId, null) { path ->
      play(path, deviceName, scale)
    }
  }

  suspend fun play(
    wavFile: String,
    deviceName: DrasDevice = DrasDevice.INTERNAL_SPEAKER,
    scale: Float = 1.0f,
  ): AdbShell.Result {
    val cmd =
      mutableListOf(
          DRAS_TOOL,
          "expplay",
          "--device",
          deviceName.deviceName,
          "--scale",
          scale.toString(),
          wavFile,
        )
        .joinToString(" ")
    return factoryContext.adbClient.runShellCommand(cmd)
  }

  suspend fun playAndRecord(
    playbackFile: String,
    recordWavFile: String,
    playbackDeviceName: DrasDevice = DrasDevice.HEADPHONE,
    recordDeviceName: DrasDevice = DrasDevice.MIC,
    scale: Float = 1.0f,
  ): AdbShell.Result {
    val cmd = mutableListOf(
          DRAS_TOOL,
          "expplayrecord",
          "--play-device", playbackDeviceName.deviceName,
          "--record-device", recordDeviceName.deviceName,
    )
    
    // In expplayrecord, some versions use --play-scale or --scale.
    // Try --scale first but place it before positional arguments.
    if (scale != 1.0f) {
        cmd.add("--scale")
        cmd.add(scale.toString())
    }
    
    cmd.add(playbackFile)
    cmd.add(recordWavFile)
    
    return factoryContext.adbClient.runShellCommand(cmd.joinToString(" "))
  }

  companion object {
    const val DRAS_TOOL = "/apex/com.android.hardware.audio.desktop/bin/dras_tool"
  }
}
