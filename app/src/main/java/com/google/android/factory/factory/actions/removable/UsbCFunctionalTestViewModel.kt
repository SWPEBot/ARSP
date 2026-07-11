package com.google.android.factory.factory.actions.removable

// import com.google.android.factory.factory.viewmodel.display.checkIsRightDisplayProof
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.ui.input.key.Key
import com.google.android.factory.base.adb.interfaces.AdbShell
import com.google.android.factory.base.logging.Log
import com.google.android.factory.factory.actions.base.FactoryActionViewModel
import com.google.android.factory.factory.actions.interfaces.FactoryActionResult
import com.google.android.factory.factory.processor.ksp.GenerateTestArgsUtils
import com.google.android.factory.factory.viewmodel.display.ExternalDisplayTestPhase
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// ─────────────────────────────────────────────────────────────────────────────
// Args
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Arguments for [UsbCFunctionalTestViewModel].
 *
 * ## Connection modes Supports two modes — configure [port2SysfsPrefix] and [port3SysfsPrefix]
 * accordingly:
 *
 * ### Mode A – Via USB-C Hub (single physical port) Insert a USB-C Hub into one physical port, then
 * plug one TypeC USB 2.0 and one TypeC USB 3.0 stick into it.
 * ```
 * Physical port 3-6 (TypeC USB 2.0 bus) → Hub → TypeC USB 2.0 stick appears as 3-6.2
 * Physical port 4-2 (TypeC USB 3.0 bus) → Hub → TypeC USB 3.0 stick appears as 4-2.3
 * ```
 * Set prefixes to the root port: `port2SysfsPrefix="3-6"`, `port3SysfsPrefix="4-2"`.
 *
 * ### Mode B – Direct (two separate USB-C ports or one port with two USB-C types) Plug TypeC USB
 * 2.0 stick directly into one port and TypeC USB 3.0 into another.
 * ```
 * Physical port 3-6 → TypeC USB 2.0 stick appears as 3-6:1.0
 * Physical port 4-2 → TypeC USB 3.0 stick appears as 4-2:1.0
 * ```
 * Same prefix values work — detection matches both `3-6:` and `3-6.` automatically.
 *
 * ## How to find a port's sysfs prefix
 * ```
 * adb shell readlink /sys/block/sda   # after inserting a USB stick
 * ```
 * Example: `../devices/.../usb3/3-6/3-6.2/.../block/sda` → prefix is `3-6`.
 *
 * ## Test flow Each port runs its own independent coroutine: **detect → (auto-format if needed) →
 * speed test**. Whichever stick is inserted first starts testing first. Both must pass for overall
 * success.
 *
 * @property fileSizeToWriteInMb File size written during the speed test per device (MB).
 * @property port2SysfsPrefix Sysfs bus prefix for the TypeC USB 2.0 stick (e.g. `"3-6"`).
 * @property port3SysfsPrefix Sysfs bus prefix for the TypeC USB 3.0 stick (e.g. `"4-2"`).
 * @property autoFormatToFat32 Auto-format non-FAT32 sticks to FAT32 before testing.
 * @property detectTimeoutSecs Hard timeout (seconds) for the entire test; triggers FAIL when
 * exceeded.
 * @property port2MinWriteSpeedMb TypeC USB 2.0 minimum write speed (MB/s). Cheap sticks: 3–20 MB/s.
 * @property port2MinReadSpeedMb TypeC USB 2.0 minimum read speed (MB/s).
 * @property port3MinWriteSpeedMb TypeC USB 3.0 minimum write speed (MB/s). Branded sticks: ≥10
 * MB/s.
 * @property port3MinReadSpeedMb TypeC USB 3.0 minimum read speed (MB/s).
 * @property enableDisplayTest Enable or disable the HDMI/display test.
 * @property requireReverseCycle Require a second reversed USB cycle before final PASS.
 */
@GenerateTestArgsUtils
data class UsbCFunctionalTestArgs(
        var fileSizeToWriteInMb: Int = 8,
        var port2SysfsPrefix: String = "",
        var port3SysfsPrefix: String = "",
        var autoFormatToFat32: Boolean = true,
        var detectTimeoutSecs: Int = 9999,
        // TypeC USB 2.0 speed thresholds (slower — cheaper sticks can be 3-20 MB/s)
        var port2MinWriteSpeedMb: Double = 3.0,
        var port2MinReadSpeedMb: Double = 5.0,
        // TypeC USB 3.0 speed thresholds (faster — good sticks should do ≥10/15 MB/s)
        var port3MinWriteSpeedMb: Double = 10.0,
        var port3MinReadSpeedMb: Double = 15.0,
        var drmSysfsPath: String = "/sys/class/drm/card0",
        var displayId: String = "DP-1",
        var enableDisplayTest: Boolean = true,
        var requireReverseCycle: Boolean = true,
        // TypeC CC1/CC2 orientation test via `ectool usbpd <port>`.
        // Set to the EC port number (0 = left, 1 = right). -1 disables the CC test.
        var ccEctoolPort: Int = -1,
)

// ─────────────────────────────────────────────────────────────────────────────
// UI state
// ─────────────────────────────────────────────────────────────────────────────

/** Live state for a single USB port (2.0 or 3.0). */
data class UsbCPortState(
        val label: String = "",
        val isPresent: Boolean = false,
        val isFormatting: Boolean = false,
        val writeSpeed: Double? = null,
        val readSpeed: Double? = null,
        val status: String = "Waiting for USB stick...",
        val isFinished: Boolean = false,
        val isPassed: Boolean = false,
)

/**
 * Phases for the TypeC CC1/CC2 orientation test.
 *
 * Flow: IDLE → DETECTING_FIRST → WAITING_UNPLUG → WAITING_REPLUG → DONE
 *
 * - DETECTING_FIRST runs concurrently with the USB speed tests.
 * - WAITING_UNPLUG is entered after all USB/HDMI tests finish; OP must unplug the TypeC cable.
 * - WAITING_REPLUG is entered after the cable is unplugged; OP must re-plug in opposite
 * orientation.
 * - DONE carries the result (both CC pins covered = passed).
 */
sealed class TypeCCcPhase {
  /** CC test not started (or disabled). */
  object IDLE : TypeCCcPhase()
  /** Polling orientation while USB tests run. [detected] is null until a reading arrives. */
  data class DETECTING_FIRST(val detected: String? = null) : TypeCCcPhase()
  /** USB tests done; OP must unplug the TypeC cable. [firstCc] = "CC1" or "CC2". */
  data class WAITING_UNPLUG(val firstCc: String) : TypeCCcPhase()
  /** Cable unplugged; OP must re-plug in the opposite orientation. */
  data class WAITING_REPLUG(val firstCc: String) : TypeCCcPhase()
  /** Both orientations tested. [passed] = true when CC1 and CC2 were both detected. */
  data class DONE(val firstCc: String, val secondCc: String, val passed: Boolean) : TypeCCcPhase()
}

data class UsbCFunctionalTestUiState(
        val port1: UsbCPortState = UsbCPortState(label = "TypeC USB 2.0"),
        val port2: UsbCPortState = UsbCPortState(label = "TypeC USB 3.0"),
        val hdmiPort: UsbCPortState = UsbCPortState(label = "TypeC HDMI Display"),
        val overallInstruction: String =
                "Please insert TypeC USB 2.0 and TypeC USB 3.0 devices and HDMI display as well.",
        val hdmiTestPhase: ExternalDisplayTestPhase? = null,
        val hdmiPassed: Boolean? = null,
        /** Current phase of the TypeC CC1/CC2 orientation test. Null when the test is disabled. */
        val ccPhase: TypeCCcPhase? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Internal
// ─────────────────────────────────────────────────────────────────────────────

private data class PortDeviceC(val blockDev: String, val mountPoint: String, val uuid: String)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tests TypeC USB 2.0 and TypeC USB 3.0 sticks on a USB-C port.
 *
 * ## Connection modes
 * - **Hub mode**: one USB-C Hub plugged into the port, both sticks in the Hub.
 * - **Direct mode**: two separate USB-C ports, one stick in each.
 *
 * ## Device detection The `/sys/block/sdX` symlink encodes the USB topology. Both modes are
 * supported:
 * ```
 * Direct: /sys/block/sda → .../3-6/3-6:1.0/host0/.../block/sda   (matches /$prefix:)
 * Hub:    /sys/block/sda → .../3-6/3-6.2/3-6.2:1.0/.../block/sda (matches /$prefix.)
 * ```
 * UUID from `blkid` is used to locate the mount point at `/mnt/media_rw/<UUID>`.
 *
 * ## Test flow Each port runs **independently** in its own coroutine:
 * ```
 * TypeC USB 2.0 coroutine: [wait for detection] → [auto-format?] → [write/read speed test]
 * TypeC USB 3.0 coroutine: [wait for detection] → [auto-format?] → [write/read speed test]
 * ```
 * - Whichever stick is inserted first starts testing immediately.
 * - Both must pass their respective speed thresholds for overall success.
 * - [UsbCFunctionalTestArgs.detectTimeoutSecs] caps the total test duration.
 *
 * ## Auto-format If a non-FAT32 stick is detected and [UsbCFunctionalTestArgs.autoFormatToFat32] is
 * `true`, the device is formatted once per run via Android's `sm partition` command (vold).
 */
class UsbCFunctionalTestViewModel : FactoryActionViewModel<UsbCFunctionalTestArgs>() {

  private val _ui = MutableStateFlow(UsbCFunctionalTestUiState())
  val ui = _ui.asStateFlow()

  /**
   * Block devices already formatted this run (prevents infinite retry). Thread-safe for concurrent
   * access.
   */
  private val formattedDevices = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

  // ── Entry point ─────────────────────────────────────────────────────────────

  override suspend fun runActionImpl(): FactoryActionResult {
    // Always start with a clean slate — clears any state from a previous run.
    resetState()

    val ccEnabled = args.ccEctoolPort >= 0
    if (ccEnabled) _ui.update { it.copy(ccPhase = TypeCCcPhase.DETECTING_FIRST()) }
    if (!args.enableDisplayTest) {
      _ui.update {
        it.copy(
                hdmiPort = it.hdmiPort.copy(isFinished = true, isPassed = true, status = "SKIPPED"),
                hdmiPassed = true,
                hdmiTestPhase = ExternalDisplayTestPhase.DONE,
        )
      }
    }

    return try {
      withTimeout(args.detectTimeoutSecs * 1000L) {
        coroutineScope {
          val firstCyclePassed =
                  runUsbDisplayCycle(
                          cycleLabel = "front cycle",
                          ccEnabled = ccEnabled,
                          expectedCc = if (ccEnabled) "CC1" else null,
                  )
          if (!firstCyclePassed) {
            _ui.update { it.copy(overallInstruction = "Test failed ✗") }
            return@coroutineScope FactoryActionResult.Failure
          }

          if (args.requireReverseCycle) {
            _ui.update {
              it.copy(
                      overallInstruction =
                              "Front cycle passed. Flip the TypeC cable to start the reverse cycle."
              )
            }
            prepareForNextCycle()

            val secondCyclePassed =
                    runUsbDisplayCycle(
                            cycleLabel = "reverse cycle",
                            ccEnabled = ccEnabled,
                            expectedCc = if (ccEnabled) "CC2" else null,
                    )
            if (!secondCyclePassed) {
              _ui.update { it.copy(overallInstruction = "Test failed ✗") }
              return@coroutineScope FactoryActionResult.Failure
            }
          }

          if (ccEnabled) {
            _ui.update {
              it.copy(
                      ccPhase = TypeCCcPhase.DONE(firstCc = "CC1", secondCc = "CC2", passed = true),
              )
            }
          }

          val passed = true
          _ui.update {
            it.copy(overallInstruction = if (passed) "Test passed ✓" else "Test failed ✗")
          }
          if (passed) FactoryActionResult.Success else FactoryActionResult.Failure
        }
      }
    } catch (e: TimeoutCancellationException) {
      Log.error("UsbCFunctionalTest timed out after ${args.detectTimeoutSecs}s")
      _ui.update {
        it.copy(
                overallInstruction =
                        "Timeout: test did not finish within ${args.detectTimeoutSecs}s."
        )
      }
      FactoryActionResult.Failure
    }
  }

  /** Resets UI and format-tracking to a clean initial state. */
  private fun resetState() {
    formattedDevices.clear()
    _ui.value = UsbCFunctionalTestUiState()
  }

  private fun prepareForNextCycle() {
    _ui.update {
      it.copy(
              port1 = UsbCPortState(label = "TypeC USB 2.0"),
              port2 = UsbCPortState(label = "TypeC USB 3.0"),
              hdmiPort =
                      if (args.enableDisplayTest) {
                        UsbCPortState(label = "TypeC HDMI Display")
                      } else {
                        UsbCPortState(
                                label = "TypeC HDMI Display",
                                isFinished = true,
                                isPassed = true,
                                status = "SKIPPED"
                        )
                      },
              hdmiTestPhase = null,
              hdmiPassed = if (args.enableDisplayTest) null else true,
      )
    }
  }

  private suspend fun runUsbDisplayCycle(
          cycleLabel: String,
          ccEnabled: Boolean,
          expectedCc: String? = null,
  ): Boolean = coroutineScope {
    if (expectedCc != null) {
      waitForExpectedCc(
              expectedCc = expectedCc,
              cycleLabel = cycleLabel,
              requireUnplugFirst = cycleLabel == "reverse cycle",
              firstCc = if (cycleLabel == "reverse cycle") "CC1" else null,
      )
    }
    _ui.update {
      it.copy(
              overallInstruction =
                      "Running $cycleLabel. Keep the device connected and wait for the test to finish."
      )
    }

    // Each port runs fully independently: detect → auto-format (if needed) → speed test.
    // Whichever stick is inserted first, its test starts first.
    val port1Job =
            async(factoryContext.ioDispatcher) {
              runPortTest(
                      prefix = args.port2SysfsPrefix,
                      label = "TypeC USB 2.0",
                      portIndex = 1,
                      minWrite = args.port2MinWriteSpeedMb,
                      minRead = args.port2MinReadSpeedMb,
              )
            }
    val port2Job =
            async(factoryContext.ioDispatcher) {
              runPortTest(
                      prefix = args.port3SysfsPrefix,
                      label = "TypeC USB 3.0",
                      portIndex = 2,
                      minWrite = args.port3MinWriteSpeedMb,
                      minRead = args.port3MinReadSpeedMb,
              )
            }
    val hdmiJob =
            if (args.enableDisplayTest) {
              async(factoryContext.ioDispatcher) { runHdmiTest() }
            } else {
              null
            }
    val ccPollJob =
            if (ccEnabled) {
              async(factoryContext.ioDispatcher) { pollCcOrientationDuringTests() }
            } else null

    val passed = port1Job.await() && port2Job.await() && (hdmiJob?.await() ?: true)
    ccPollJob?.cancel()
    passed
  }

  private suspend fun waitForExpectedCc(
          expectedCc: String,
          cycleLabel: String,
          requireUnplugFirst: Boolean = false,
          firstCc: String? = null,
  ) {
    if (requireUnplugFirst && firstCc != null) {
      _ui.update {
        it.copy(
                ccPhase = TypeCCcPhase.WAITING_UNPLUG(firstCc = firstCc),
                overallInstruction =
                        "Front cycle passed. Unplug the TypeC cable before starting $cycleLabel.",
        )
      }
      while (readCcOrientation() != null) {
        delay(300)
      }

      _ui.update {
        it.copy(
                ccPhase = TypeCCcPhase.WAITING_REPLUG(firstCc = firstCc),
                overallInstruction =
                        "Cable removed. Flip the TypeC cable and reinsert it to continue $cycleLabel.",
        )
      }
    }

    while (true) {
      val currentCc = readCcOrientation()
      when (currentCc) {
        expectedCc -> {
          _ui.update {
            it.copy(
                    ccPhase = TypeCCcPhase.DETECTING_FIRST(detected = expectedCc),
                    overallInstruction = "Detected $expectedCc. Starting $cycleLabel.",
            )
          }
          return
        }
        "CC1", "CC2" -> {
          _ui.update {
            it.copy(
                    ccPhase =
                            if (requireUnplugFirst && firstCc != null) {
                              TypeCCcPhase.WAITING_REPLUG(firstCc = firstCc)
                            } else {
                              TypeCCcPhase.DETECTING_FIRST(detected = currentCc)
                            },
                    overallInstruction =
                            if (expectedCc == "CC1") {
                              "The first insertion must be CC1. Flip the USB and try again."
                            } else {
                              "The reverse cycle must detect CC2. Flip the USB and try again."
                            },
            )
          }
          delay(500)
        }
        else -> {
          _ui.update {
            it.copy(
                    ccPhase =
                            if (requireUnplugFirst && firstCc != null) {
                              TypeCCcPhase.WAITING_REPLUG(firstCc = firstCc)
                            } else {
                              TypeCCcPhase.DETECTING_FIRST()
                            },
                    overallInstruction =
                            if (expectedCc == "CC1") {
                              "Insert the TypeC cable in the front direction first. Only CC1 is accepted."
                            } else {
                              "Flip the TypeC cable and reinsert it. Only CC2 is accepted for the reverse cycle."
                            },
            )
          }
          delay(500)
        }
      }
    }
  }

  // ── TypeC CC1/CC2 orientation test ──────────────────────────────────────────

  /**
   * Reads USB-C CC orientation via `ectool usbpdmuxinfo`.
   *
   * Returns `"CC1"`, `"CC2"`, or `null` (cable not connected / USB inactive).
   *
   * Example output:
   * ```
   * Port 0: USB=1 DP=1 POLARITY=INVERTED HPD_IRQ=0 HPD_LVL=0 SAFE=0 TBT=0 USB4=0
   * Port 1: USB=0 DP=0 POLARITY=NORMAL  HPD_IRQ=0 HPD_LVL=0 SAFE=0 TBT=0 USB4=0
   * ```
   * - `POLARITY=NORMAL` → CC1
   * - `POLARITY=INVERTED` → CC2
   * - `USB=0` → cable disconnected → `null`
   */
  private suspend fun readCcOrientation(): String? =
          withContext(factoryContext.ioDispatcher) {
            val output = adb("ectool usbpdmuxinfo 2>/dev/null").outText
            // Find the line for this port: "Port N: USB=... POLARITY=... ..."
            val portLine =
                    output.lines().firstOrNull {
                      it.trimStart().startsWith("Port ${args.ccEctoolPort}:")
                    }
                            ?: return@withContext null
            // USB=0 means nothing is actively connected on this port → treat as unplugged.
            if ("USB=1" !in portLine) return@withContext null
            when {
              "POLARITY=NORMAL" in portLine -> "CC1"
              "POLARITY=INVERTED" in portLine -> "CC2"
              else -> null
            }
          }

  /**
   * Polls CC orientation while USB tests are running. Updates
   * [TypeCCcPhase.DETECTING_FIRST.detected] with the first non-unknown orientation found.
   * Intentionally returns early once detected; the caller cancels this coroutine on test
   * completion.
   */
  private suspend fun pollCcOrientationDuringTests() {
    while (true) {
      val orientation = readCcOrientation()
      if (orientation != null) {
        _ui.update { it.copy(ccPhase = TypeCCcPhase.DETECTING_FIRST(detected = orientation)) }
        delay(2000)
      } else {
        delay(500)
      }
    }
  }

  /**
   * Full lifecycle for one port: wait for device → (auto-format if needed) → speed test. Runs
   * entirely on [factoryContext.ioDispatcher]; starts the moment the device is inserted.
   */
  private suspend fun runPortTest(
          prefix: String,
          label: String,
          portIndex: Int,
          minWrite: Double,
          minRead: Double,
  ): Boolean {
    updatePortStatus(portIndex, "Waiting for $label stick...")
    val dev = waitForPortDeviceC(prefix, label, portIndex) ?: return false
    // Brief stabilization — let the filesystem settle after detection/format.
    delay(STABILIZE_MS)
    return performSpeedTest(
            label = label,
            portIndex = portIndex,
            mountPoint = dev.mountPoint,
            minWrite = minWrite,
            minRead = minRead,
    )
  }

  private suspend fun runHdmiTest(): Boolean {
    updatePortStatus(3, "Waiting for HDMI display...")
    val statusPath = "${args.drmSysfsPath}-${args.displayId}/status"
    try {
      while (true) {
        val statusText = adb("cat $statusPath 2>/dev/null").outText.trim()
        val isConnected = statusText == "connected"
        updatePort(3) { it.copy(isPresent = isConnected) }
        if (isConnected) {
          // Manual mode
          val displayManager =
                  factoryContext.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as
                          DisplayManager
          val externalDisplays =
                  displayManager.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }
          val display = externalDisplays.firstOrNull()
          if (display == null) {
            Log.warn("[HDMI] Connected in sysfs but not found in DisplayManager...")
            delay(1000)
            continue
          }

          val proof = (1..9).random()
          _ui.update { it.copy(hdmiTestPhase = ExternalDisplayTestPhase.TESTING(display, proof)) }
          updatePortStatus(3, "Waiting for key input...")

          // Wait until user presses the key OR display is disconnected
          var testResult: Boolean? = null
          coroutineScope {
            val monitorJob = launch {
              while (true) {
                delay(500)
                val statusTextMonitor = adb("cat $statusPath 2>/dev/null").outText.trim()
                if (statusTextMonitor != "connected") {
                  Log.warn("[HDMI] Disconnected during testing...")
                  _ui.update {
                    it.copy(hdmiTestPhase = ExternalDisplayTestPhase.WAITING_FOR_CONNECTION)
                  }
                  updatePortStatus(3, "Waiting for HDMI display...")
                  break
                }
              }
            }

            val finalState =
                    _ui.first {
                      it.hdmiTestPhase == ExternalDisplayTestPhase.DONE ||
                              it.hdmiTestPhase == ExternalDisplayTestPhase.WAITING_FOR_CONNECTION
                    }
            monitorJob.cancel()

            if (finalState.hdmiTestPhase == ExternalDisplayTestPhase.DONE) {
              testResult = finalState.hdmiPassed == true
            }
          }

          if (testResult != null) {
            updatePort(3) {
              it.copy(
                      isPresent = true,
                      isFinished = true,
                      isPassed = testResult!!,
                      status = if (testResult!!) "PASSED ✓" else "FAILED ✗"
              )
            }
            return testResult!!
          } else {
            continue
          }
        }
        delay(500)
      }
    } finally {
      // Ensure port is marked finished on timeout/cancellation.
      if (!_ui.value.hdmiPort.isFinished) {
        updatePort(3) { it.copy(isFinished = true, isPassed = false, status = "TIMEOUT ✗") }
      }
    }
  }

  fun checkIsRightDisplayProof(key: Key) {
    val pressedDigit = key.toDigit()
    val currentPhase = _ui.value.hdmiTestPhase
    if (currentPhase is ExternalDisplayTestPhase.TESTING) {
      _ui.update {
        it.copy(
                hdmiTestPhase = ExternalDisplayTestPhase.DONE,
                hdmiPassed = pressedDigit == currentPhase.displayProof,
        )
      }
    }
  }

  override suspend fun tearDown() = Unit

  // ── Device detection ────────────────────────────────────────────────────────

  /**
   * Returns the [PortDeviceC] for the given sysfs [prefix], or null if absent / not FAT32-mounted.
   *
   * Matches both connection modes:
   * - **Direct** (no Hub): sysfs link contains `/$prefix:` (e.g. `.../3-6/3-6:1.0/...`)
   * - **Via Hub**: sysfs link contains `/$prefix.` (e.g. `.../3-6/3-6.2/3-6.2:1.0/...`)
   *
   * When [autoFormat] is true and the device is non-FAT32, it is formatted automatically (once per
   * device per run — tracked by [formattedDevices]).
   */
  private suspend fun findBlockDevForPrefix(
          prefix: String,
          autoFormat: Boolean = false,
          portIndex: Int,
  ): PortDeviceC? =
          withContext(factoryContext.ioDispatcher) {
            try {
              val sdDevs =
                      adb("ls /sys/block/ 2>/dev/null").outText.lines().filter {
                        it.startsWith("sd")
                      }

              for (dev in sdDevs) {
                val link = adb("readlink /sys/block/$dev 2>/dev/null").outText.trim()

                // If we provide a base port like "3-1" or "4-2", we want to match:
                // - Direct: /3-1:
                // - Hub sub-ports: /3-1.4:, /3-1.5:, /3-1.1.2:, etc.
                // - Hub root: /3-1/
                // The regex looks for the literal "/$prefix", followed by either:
                // - a colon (:) indicating direct connection
                // - a period (.) indicating a downstream hub port
                // - a slash (/) indicating the hub itself or fallback matching
                val regex = Regex("/($prefix)[:./]")
                if (!regex.containsMatchIn(link)) continue

                // Found the root block device (e.g., sda).
                // Collect partitions (sda1, sda2...) plus the root disk (super-floppy scenario).
                val partsOut = adb("ls /sys/block/$dev/ 2>/dev/null").outText
                val diskDevs =
                        partsOut.lines()
                                .filter { it.startsWith(dev) && it != dev }
                                .sorted()
                                .toMutableList()
                diskDevs.add(dev)

                var needsFormat = true

                // Check partitions first, then root disk.
                for (targetDev in diskDevs.reversed()) {
                  val blkidOut = adb("blkid /dev/block/$targetDev 2>/dev/null").outText.trim()
                  val fsType =
                          Regex("""TYPE="([^"]+)"""").find(blkidOut)?.groupValues?.get(1)
                                  ?: continue

                  // Accept vfat or exfat (both are fast, portable native formats).
                  if (fsType.equals("vfat", ignoreCase = true) ||
                                  fsType.equals("exfat", ignoreCase = true)
                  ) {
                    val uuid =
                            Regex("""UUID="([^"]+)"""").find(blkidOut)?.groupValues?.get(1)
                                    ?: continue
                    val mountPoint = "/mnt/media_rw/$uuid"
                    val mountOk = adb("test -d $mountPoint && echo OK").outText.trim() == "OK"

                    if (mountOk) {
                      return@withContext PortDeviceC(
                              blockDev = targetDev,
                              mountPoint = mountPoint,
                              uuid = uuid
                      )
                    } else {
                      needsFormat = false // Wait for vold to mount it, do not reformat!
                      break
                    }
                  }
                }

                if (needsFormat && autoFormat && dev !in formattedDevices) {
                  formattedDevices.add(dev)
                  formatDeviceToFat32(prefix, dev, portIndex)
                }
              }
              null
            } catch (e: Exception) {
              Log.error("findBlockDevForPrefix($prefix): ${e.message}")
              null
            }
          }

  /**
   * Fast physical-presence check: returns true if ANY sd* device on this port is visible in
   * `/sys/block/`, regardless of filesystem type or mount state. Used only to drive the UI
   * connection dot — does NOT imply the device is ready for testing.
   */
  private suspend fun isPhysicallyPresent(prefix: String): Boolean =
          withContext(factoryContext.ioDispatcher) {
            try {
              val sdDevs =
                      adb("ls /sys/block/ 2>/dev/null").outText.lines().filter {
                        it.startsWith("sd")
                      }
              sdDevs.any { dev ->
                val link = adb("readlink /sys/block/$dev 2>/dev/null").outText.trim()
                val regex = Regex("/($prefix)[:./]")
                regex.containsMatchIn(link)
              }
            } catch (e: Exception) {
              false
            }
          }

  /**
   * Polls until a [PortDeviceC] is found for [prefix]. The overall test timeout handles
   * cancellation. Updates the UI connection dot on every cycle.
   */
  private suspend fun waitForPortDeviceC(
          prefix: String,
          label: String,
          portIndex: Int
  ): PortDeviceC? {
    var attempts = 0
    while (true) {
      // Update the connection dot based on physical presence (independent of FAT32/mount state).
      val present = isPhysicallyPresent(prefix)
      updatePort(portIndex) { it.copy(isPresent = present) }

      val dev =
              findBlockDevForPrefix(
                      prefix,
                      autoFormat = args.autoFormatToFat32,
                      portIndex = portIndex
              )
      if (dev != null) {
        updatePort(portIndex) { it.copy(isPresent = true) }
        return dev
      }
      attempts++
      val statusMsg =
              if (present) "$label detected — preparing... ($attempts)"
              else "Waiting for $label stick... ($attempts)"
      updatePortStatus(portIndex, statusMsg)
      delay(200)
    }
  }

  // ── Auto-format ─────────────────────────────────────────────────────────────

  /**
   * Formats [blockDev] using Android's built-in volume daemon (vold). `sm partition disk:X,Y
   * public` safely unmounts, partitions, and formats as FAT32/exFAT.
   */
  private suspend fun formatDeviceToFat32(
          prefix: String,
          blockDev: String,
          portIndex: Int
  ): Boolean =
          withContext(factoryContext.ioDispatcher) {
            setPortFormatting(portIndex, true)
            updatePortStatus(portIndex, "Formatting $blockDev...")

            val devNum = adb("cat /sys/block/$blockDev/dev 2>/dev/null").outText.trim()
            if (devNum.isEmpty()) {
              Log.error("[$prefix] Cannot read device number for $blockDev")
              setPortFormatting(portIndex, false)
              return@withContext false
            }

            val diskId = "disk:${devNum.replace(':', ',')}"
            Log.info("[$prefix] Triggering vold format: $diskId...")

            // Tell Android vold to wipe the disk, partition it, and format as public (FAT32/exFAT).
            // This command behaves identically to "Erase & Format" in the Android Settings UI.
            val fmtOut = adb("sm partition $diskId public 2>&1").outText.trim()
            Log.info("[$prefix] sm partition: $fmtOut")

            // Poll until the newly created VFAT/exFAT partition finishes mounting.
            updatePortStatus(portIndex, "Mounting formatted volume...")

            val maxPolls = (FORMAT_WAIT_MS / FORMAT_POLL_MS).toInt()
            repeat(maxPolls) { i ->
              if (i > 0) delay(FORMAT_POLL_MS)

              val partsOut = adb("ls /sys/block/$blockDev/ 2>/dev/null").outText
              val targets =
                      partsOut.lines()
                              .filter { it.startsWith(blockDev) && it != blockDev }
                              .toMutableList()
              targets.add(blockDev)

              for (targetDev in targets.reversed()) {
                val blkidOut = adb("blkid /dev/block/$targetDev 2>/dev/null").outText.trim()
                val fsType =
                        Regex("""TYPE="([^"]+)"""").find(blkidOut)?.groupValues?.get(1) ?: continue

                if (fsType.equals("vfat", true) || fsType.equals("exfat", true)) {
                  val uuid =
                          Regex("""UUID="([^"]+)"""").find(blkidOut)?.groupValues?.get(1)
                                  ?: continue
                  if (adb("test -d /mnt/media_rw/$uuid && echo OK").outText.trim() == "OK") {
                    setPortFormatting(portIndex, false)
                    updatePortStatus(portIndex, "Formatted ✓ — verifying...")
                    return@withContext true
                  }
                }
              }
            }

            Log.error("[$prefix] $diskId did not mount after format within ${FORMAT_WAIT_MS} ms.")
            updatePortStatus(portIndex, "Mount timed out after format")
            setPortFormatting(portIndex, false)
            false
          }

  // ── Speed test ──────────────────────────────────────────────────────────────

  private suspend fun performSpeedTest(
          label: String,
          portIndex: Int,
          mountPoint: String,
          minWrite: Double,
          minRead: Double,
  ): Boolean {
    val fileSizeMb = args.fileSizeToWriteInMb
    val testFile =
            File(mountPoint, "FactoryApp/SpeedTest_$portIndex.bin").apply {
              parentFile?.mkdirs()
              if (exists()) delete()
            }

    try {
      updatePortStatus(portIndex, "Writing $fileSizeMb MB…")
      // Single large-block write → only ONE fsync: much faster than N×(bs=1M conv=fsync).
      val writeOut =
              adb(
                      "dd if=/dev/zero of=${testFile.absolutePath} bs=${fileSizeMb}M count=1 conv=fsync"
              )
      val writeSpeed = parseDdSpeed(writeOut.errText)
      if (writeSpeed == null) {
        Log.error("[$label] Write failed — stderr: ${writeOut.errText}")
        updatePortStatus(portIndex, "ERROR: Write test failed", finished = true, passed = false)
        return false
      }
      Log.info("[$label] Write: %.2f MB/s".format(writeSpeed))

      // Drop pagecache to force a real USB read (not served from RAM).
      adb("echo 1 > /proc/sys/vm/drop_caches")
      delay(50)

      updatePortStatus(portIndex, "Reading $fileSizeMb MB…")
      // Match the write block size so read is also a single large sequential transfer.
      val readOut = adb("dd if=${testFile.absolutePath} of=/dev/null bs=${fileSizeMb}M")
      val readSpeed = parseDdSpeed(readOut.errText)
      if (readSpeed == null) {
        Log.error("[$label] Read failed — stderr: ${readOut.errText}")
        updatePortStatus(portIndex, "ERROR: Read test failed", finished = true, passed = false)
        return false
      }
      Log.info("[$label] Read: %.2f MB/s".format(readSpeed))

      val passed = writeSpeed >= minWrite && readSpeed >= minRead
      if (!passed) {
        Log.error(
                "[$label] FAILED — Write=%.1f(≥%.1f) Read=%.1f(≥%.1f) MB/s".format(
                        writeSpeed,
                        minWrite,
                        readSpeed,
                        minRead,
                )
        )
      }
      updatePort(portIndex) {
        it.copy(
                writeSpeed = writeSpeed,
                readSpeed = readSpeed,
                status = if (passed) "PASSED ✓" else "FAILED ✗",
                isFinished = true,
                isPassed = passed,
        )
      }
      return passed
    } finally {
      // Clean up test file.
      testFile.delete()
    }
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private suspend fun adb(command: String): AdbShell.Result =
          factoryContext.adbClient.runShellCommand(command)

  private fun parseDdSpeed(output: String): Double? {
    val match =
            """,\s*(\d+\.?\d*)\s*([KMG])(?:B?)/s""".toRegex().find(output)
                    ?: run {
                      Log.error("parseDdSpeed: no match in [$output]")
                      return null
                    }
    val (valueStr, unit) = match.destructured
    val value = valueStr.toDoubleOrNull() ?: return null
    return when (unit.uppercase()) {
      "K" -> value / 1024.0
      "M" -> value
      "G" -> value * 1024.0
      else -> null
    }
  }

  private fun updatePort(portIndex: Int, block: (UsbCPortState) -> UsbCPortState) {
    _ui.update { state ->
      when (portIndex) {
        1 -> state.copy(port1 = block(state.port1))
        2 -> state.copy(port2 = block(state.port2))
        3 -> state.copy(hdmiPort = block(state.hdmiPort))
        else -> {
          Log.error("updatePort: unknown portIndex $portIndex")
          state
        }
      }
    }
  }

  private fun updatePortStatus(
          portIndex: Int,
          status: String,
          finished: Boolean = false,
          passed: Boolean = false
  ) = updatePort(portIndex) { it.copy(status = status, isFinished = finished, isPassed = passed) }

  private fun setPortFormatting(portIndex: Int, isFormatting: Boolean) =
          updatePort(portIndex) { it.copy(isFormatting = isFormatting) }

  companion object {
    /** How long to wait for vold to mount the newly-formatted FAT32 volume (ms). */
    private const val FORMAT_WAIT_MS = 15_000L
    /** Poll interval for mount status checks after format (ms). Shorter = more responsive. */
    private const val FORMAT_POLL_MS = 100L
    /**
     * Delay (ms) after device detection before starting the speed test, for filesystem stability.
     */
    private const val STABILIZE_MS = 100L
  }
}

private fun Key.toDigit(): Int? {
  return when (this) {
    Key.Zero -> 0
    Key.One -> 1
    Key.Two -> 2
    Key.Three -> 3
    Key.Four -> 4
    Key.Five -> 5
    Key.Six -> 6
    Key.Seven -> 7
    Key.Eight -> 8
    Key.Nine -> 9
    else -> null
  }
}
