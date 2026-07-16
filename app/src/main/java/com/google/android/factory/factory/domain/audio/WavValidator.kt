package com.google.android.factory.factory.domain.audio

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

sealed class WavValidatorException(message: String) : Exception(message) {
  class RmsTooLowException(message: String) : WavValidatorException(message)
  class SimilarityTooLowException(message: String) : WavValidatorException(message)
}

data class WavMetadata(
  var audioFormat: Int = 0,
  var numChannels: Int = 0,
  var sampleRate: Long = 0,
  var byteRate: Long = 0,
  var blockAlign: Int = 0,
  var bitsPerSample: Int = 0,
  var dataSize: Long = 0,
)

data class WavFile(val metadata: WavMetadata, val signal: Array<DoubleArray>)

object WavValidator {
  private const val ID_RIFF = 0x46464952
  private const val ID_WAVE = 0x45564157
  private const val ID_FMT = 0x20746d66
  private const val ID_DATA = 0x61746164
  private const val MAX_SIGNED_16BIT = 32768.0
  private const val MAX_SIGNED_32BIT = 2147483648.0

  fun generateMultiToneStereoWav(
    filePath: String,
    totalDuration: Double,
    frequenciesHz: List<Double>,
    patterns: List<Int>, // Added: [0=Left, 1=Right, -1=Both]
    sampleRate: Int,
    bitDepth: Int = 32,
    isReferenceMono: Boolean = false
  ) {
    val numSamples = (sampleRate * totalDuration).toInt()
    val numChannels = if (isReferenceMono) 1 else 2
    val bytesPerSample = bitDepth / 8
    val blockAlign = numChannels * bytesPerSample
    val dataSize = numSamples * blockAlign
    val amplitude = if (bitDepth == 32) 2147483647.0 else 32767.0

    BufferedOutputStream(FileOutputStream(filePath)).use { bos ->
      val writeLEInt = { v: Int -> bos.write(v and 0xFF); bos.write((v shr 8) and 0xFF); bos.write((v shr 16) and 0xFF); bos.write((v shr 24) and 0xFF) }
      val writeLEShort = { v: Short -> bos.write(v.toInt() and 0xFF); bos.write((v.toInt() shr 8) and 0xFF) }

      bos.write("RIFF".toByteArray()); writeLEInt(dataSize + 36); bos.write("WAVE".toByteArray())
      bos.write("fmt ".toByteArray()); writeLEInt(16); writeLEShort(1); writeLEShort(numChannels.toShort())
      writeLEInt(sampleRate); writeLEInt(sampleRate * blockAlign); writeLEShort(blockAlign.toShort()); writeLEShort(bitDepth.toShort())
      bos.write("data".toByteArray()); writeLEInt(dataSize)

      val segmentSamples = numSamples / frequenciesHz.size
      frequenciesHz.forEachIndexed { index, freq ->
        val angleIncrement = 2.0 * PI * freq / sampleRate
        val pattern = patterns[index % patterns.size]
        for (i in 0 until segmentSamples) {
          val sineValue = (sin(i * angleIncrement) * amplitude).toInt()
          if (isReferenceMono) {
              if (bitDepth == 32) writeLEInt(sineValue) else writeLEShort(sineValue.toShort())
          } else {
              var leftVal = 0; var rightVal = 0
              if (pattern == 0 || pattern == -1) leftVal = sineValue
              if (pattern == 1 || pattern == -1) rightVal = sineValue
              if (bitDepth == 32) { writeLEInt(leftVal); writeLEInt(rightVal) } 
              else { writeLEShort(leftVal.toShort()); writeLEShort(rightVal.toShort()) }
          }
        }
      }
    }
  }

  fun generateSineWaveWav(
    filePath: String, durationSec: Double, frequencyHz: Double, sampleRate: Int,
    numChannels: Int, targetChannel: Int, bitDepth: Int = 32
  ) {
    val numSamples = (sampleRate * durationSec).toInt()
    val bytesPerSample = bitDepth / 8
    val blockAlign = numChannels * bytesPerSample
    val dataSize = numSamples * blockAlign
    val amplitude = if (bitDepth == 32) 2147483647.0 else 32767.0
    BufferedOutputStream(FileOutputStream(filePath)).use { bos ->
      val writeLEInt = { v: Int -> bos.write(v and 0xFF); bos.write((v shr 8) and 0xFF); bos.write((v shr 16) and 0xFF); bos.write((v shr 24) and 0xFF) }
      val writeLEShort = { v: Short -> bos.write(v.toInt() and 0xFF); bos.write((v.toInt() shr 8) and 0xFF) }
      bos.write("RIFF".toByteArray()); writeLEInt(dataSize + 36); bos.write("WAVE".toByteArray())
      bos.write("fmt ".toByteArray()); writeLEInt(16); writeLEShort(1); writeLEShort(numChannels.toShort())
      writeLEInt(sampleRate); writeLEInt(sampleRate * blockAlign); writeLEShort(blockAlign.toShort()); writeLEShort(bitDepth.toShort())
      bos.write("data".toByteArray()); writeLEInt(dataSize)
      val angleIncrement = 2.0 * PI * frequencyHz / sampleRate
      for (i in 0 until numSamples) {
        val s = (sin(i * angleIncrement) * amplitude).toInt()
        for (c in 0 until numChannels) {
          val v = if (targetChannel == -1 || c == targetChannel) s else 0
          if (bitDepth == 32) writeLEInt(v) else writeLEShort(v.toShort())
        }
      }
    }
  }

  fun readWavFile(filePath: String, startSec: Double = 0.0, durationSec: Double = -1.0): WavFile {
    val file = File(filePath)
    DataInputStream(BufferedInputStream(FileInputStream(file))).use { stream ->
      val readLEInt = { val b = ByteArray(4); stream.readFully(b); ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int }
      val readLEShort = { val b = ByteArray(2); stream.readFully(b); ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short }
      if (readLEInt() != ID_RIFF) throw IOException("Not RIFF")
      readLEInt(); if (readLEInt() != ID_WAVE) throw IOException("Not WAVE")
      val metadata = WavMetadata()
      while (stream.available() > 0) {
        val chunkId = readLEInt(); val chunkSize = readLEInt().toLong()
        when (chunkId) {
          ID_FMT -> {
            metadata.audioFormat = readLEShort().toInt(); metadata.numChannels = readLEShort().toInt()
            metadata.sampleRate = readLEInt().toLong(); metadata.byteRate = readLEInt().toLong()
            metadata.blockAlign = readLEShort().toInt(); metadata.bitsPerSample = readLEShort().toInt()
            if (chunkSize > 16) stream.skipBytes((chunkSize - 16).toInt())
          }
          ID_DATA -> {
            metadata.dataSize = chunkSize
            val bps = metadata.bitsPerSample / 8; val totalFrames = (chunkSize / (metadata.numChannels * bps)).toInt()
            val startFrame = (startSec * metadata.sampleRate).toInt().coerceIn(0, totalFrames)
            val framesToRead = if (durationSec < 0) totalFrames - startFrame else (durationSec * metadata.sampleRate).toInt().coerceIn(0, totalFrames - startFrame)
            stream.skipBytes(startFrame * metadata.numChannels * bps)
            val signal = Array(metadata.numChannels) { DoubleArray(framesToRead) }
            for (f in 0 until framesToRead) {
              for (c in 0 until metadata.numChannels) {
                signal[c][f] = if (metadata.bitsPerSample == 16) readLEShort().toDouble() / MAX_SIGNED_16BIT else readLEInt().toDouble() / MAX_SIGNED_32BIT
              }
            }
            return WavFile(metadata, signal)
          }
          else -> stream.skipBytes(chunkSize.toInt())
        }
      }
    }
    throw IOException("No data chunk")
  }

  fun compare(f1: String, f2: String, channel: Int, threshold: Double, rmsRange: Pair<Double, Double>, start: Double, duration: Double): Result<Unit> {
    val wav1 = runCatching { readWavFile(f1, start, duration) }.getOrElse { return Result.failure(it) }
    val searchWindowSec = 0.2
    val wav2 = runCatching { readWavFile(f2, (start - searchWindowSec).coerceAtLeast(0.0), duration + searchWindowSec * 2) }.getOrElse { return Result.failure(it) }
    val sig1 = wav1.signal[0]
    val sig2Full = wav2.signal[channel]
    val rmsVal = sqrt(sig2Full.fold(0.0) { acc, d -> acc + d * d } / sig2Full.size)
    if (rmsVal < rmsRange.first) return Result.failure(WavValidatorException.RmsTooLowException("RMS ${"%.4f".format(rmsVal)} low"))

    var maxSim = 0.0; val step = 10
    if (sig2Full.size < sig1.size) return Result.failure(WavValidatorException.SimilarityTooLowException("Short"))
    for (shift in 0 until (sig2Full.size - sig1.size) step step) {
      var dot = 0.0; var m1 = 0.0; var m2 = 0.0
      for (i in sig1.indices) {
        val s1 = sig1[i]; val s2 = sig2Full[shift + i]
        dot += s1 * s2; m1 += s1 * s1; m2 += s2 * s2
      }
      val sim = if (m1 < 1e-9 || m2 < 1e-9) 0.0 else abs(dot) / (sqrt(m1) * sqrt(m2))
      if (sim > maxSim) maxSim = sim
      if (maxSim > 0.9) break
    }
    return if (maxSim < threshold) Result.failure(WavValidatorException.SimilarityTooLowException("Sim ${"%.3f".format(maxSim)} < $threshold")) else Result.success(Unit)
  }
}
