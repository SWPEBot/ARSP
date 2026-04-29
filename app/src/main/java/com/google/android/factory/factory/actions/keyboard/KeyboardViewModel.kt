package com.google.android.factory.factory.actions.keyboard

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.factory.base.logging.Log
import com.google.android.factory.factory.actions.base.FactoryActionViewModel
import com.google.android.factory.factory.actions.common.SimpleMessageScreenController
import com.google.android.factory.factory.actions.common.SimpleMessageScreenViewModelInterface
import com.google.android.factory.factory.actions.interfaces.FactoryActionResult
import com.google.android.factory.factory.processor.ksp.GenerateTestArgsUtils
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The arguments class for the Keyboard action.
 *
 * @property timeoutSecs The timeout of the test in seconds.
 */
@GenerateTestArgsUtils
data class KeyboardArgs(
  var timeoutSecs: Int = 60,
  var layoutIndex: Int = 1,
  var detectUnexistingKey: Boolean = false,
)

data class InputDeviceData(
  val id: Int, // The ID listed in dumpsys output.
  val name: String,
  var keyLayoutFile: String? = null,
  var sysfsDevicePath: String? = null,
  var deviceId: String? = null, // The device ID in sysfs path.
)

class KeyboardViewModel(
  private val controller: SimpleMessageScreenController = SimpleMessageScreenController()
) : FactoryActionViewModel<KeyboardArgs>(), SimpleMessageScreenViewModelInterface by controller {
  private var testKeyLayoutFile: String? = null
  private var defaultKeyboard: InputDeviceData? = null
  private var driverPath: String? = null
  private var testKeyLayoutFileIsMounted = false
  private var powerButtonShortPressIsDisabled = false

  override suspend fun runActionImpl(): FactoryActionResult {
    val dumpsysOutput = factoryContext.adbClient.runShellCommand("dumpsys input").outText
    defaultKeyboard = parseDefaultKeyboard(dumpsysOutput)
    if (defaultKeyboard == null) {
      Log.error("Failed to parse default keyboard from dumpsys output.")
      return FactoryActionResult.Failure
    }

    Log.info("Default keyboard: ${defaultKeyboard}")
    driverPath =
      factoryContext.adbClient
        .runShellCommand("realpath ${defaultKeyboard!!.sysfsDevicePath}/driver")
        .outText
        .trim()

    testKeyLayoutFile = generateTestKeyboardLayout(File(defaultKeyboard!!.keyLayoutFile!!))
    if (testKeyLayoutFile == null) {
      Log.error("Failed to generate test keyboard layout.")
      return FactoryActionResult.Failure
    }

    factoryContext.adbClient.openAdbShell().use { shell ->
      // Disable the power button short press since some project have power button on keyboard.
      shell.runAndCheck("settings put global power_button_short_press 0")
      powerButtonShortPressIsDisabled = true
      // Mount the test key layout file to the default key layout file.
      shell.runAndCheck("mount --bind ${testKeyLayoutFile} ${defaultKeyboard!!.keyLayoutFile}")
      testKeyLayoutFileIsMounted = true
      // Unbind and bind the driver to apply the new key layout file.
      shell.runAndCheck("echo -n '${defaultKeyboard!!.deviceId}' > ${driverPath}/unbind")
      // Delay to ensure the unbind is complete before binding again.
      delay(1000)
      shell.runAndCheck("echo -n '${defaultKeyboard!!.deviceId}' > ${driverPath}/bind")
    }
    val keyboardDiagArgs =
      Bundle().apply {
        putInt(EXTRA_LAYOUT_INDEX, args.layoutIndex)
        putBoolean(EXTRA_DETECT_UNEXISTING_KEY, args.detectUnexistingKey)
      }
    val intent =
      Intent(DIAGNOSTIC_APP_KEYBOARD_TEST_ACTION).apply {
        component =
          ComponentName(DIAGNOSTIC_APP_PACKAGE_NAME, DIAGNOSTIC_APP_KEYBOARD_TEST_ACTIVITY_NAME)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    val result =
      withTimeoutOrNull(args.timeoutSecs.seconds) {
        factoryContext.intentResultProxy.startActivityForResult(intent, keyboardDiagArgs)
      }
    if (result == null) {
      Log.error("Keyboard test action is timed out.")
      return FactoryActionResult.Failure
    }
    val success = result.bundle?.getBoolean(EXTRA_RESULT_KEY) ?: false
    if (!success) {
      Log.error("Keyboard test action failed.")
      return FactoryActionResult.Failure
    }
    return FactoryActionResult.Success
  }

  private suspend fun generateTestKeyboardLayout(originalKeyLayoutFile: File): String? {
    val appCacheDir = factoryContext.applicationContext.cacheDir

    // Since we cannot directly read or alter the original key layout file, we need to copy it to
    // the app cache directory first.
    val copiedKeyLayoutFile = File(appCacheDir.absolutePath, originalKeyLayoutFile.name)
    factoryContext.adbClient.openAdbShell().use { shell ->
      shell.runAndCheck(
        "cp ${originalKeyLayoutFile.absolutePath} ${copiedKeyLayoutFile.absolutePath}"
      )
      shell.runAndCheck("chown u10_system:u10_system ${copiedKeyLayoutFile.absolutePath}")
    }

    val newFileContent = java.lang.StringBuilder()
    try {
      // Read line by line and append to our StringBuilder
      copiedKeyLayoutFile.forEachLine { line ->
        if (line.contains(FALLBACK_MAPPING_KEYWORD)) {
          // Skip the fallback mapping.
          return@forEachLine
        }
        val matchResult = Regex("^\\s*key\\s+(\\d+)\\s+.*").find(line)

        if (matchResult != null) {
          val scancode = matchResult.groupValues[1].toIntOrNull()

          if (scancode != null && TARGET_SCANCODES.contains(scancode)) {
            newFileContent.append("key $scancode\t$KEY_PLACEHOLDER\n")
            Log.info("Remapped scancode $scancode to $KEY_PLACEHOLDER")
          } else {
            newFileContent.append(line).append("\n")
          }
        } else {
          newFileContent.append(line).append("\n")
        }
      }
      copiedKeyLayoutFile.writeText(newFileContent.toString())
      Log.info(
        "Successfully remapped F1~F14 to $KEY_PLACEHOLDER in ${copiedKeyLayoutFile.absolutePath}"
      )
      factoryContext.adbClient.openAdbShell().use { shell ->
        shell.runAndCheck("chcon u:object_r:system_file:s0 ${copiedKeyLayoutFile.absolutePath}")
      }
      return copiedKeyLayoutFile.absolutePath
    } catch (e: Exception) {
      Log.error("Failed to modify Generic.kl: ${e.message}")
      return null
    }
  }

  fun parseDefaultKeyboard(dumpsysOutput: String): InputDeviceData? {
    val devices = mutableMapOf<Int, InputDeviceData>()
    var builtInId: Int? = null

    var inEventHubState: Boolean = false
    var id: Int? = null

    // Use lineSequence() for memory-efficient lazy evaluation
    dumpsysOutput.lineSequence().forEach { line ->
      val trimmed = line.trim()

      // 1. Scope strictly to the "Event Hub State" section
      if (trimmed == "Event Hub State:") {
        inEventHubState = true
        return@forEach // Continue to next line in the sequence
      }

      // Stop parsing if we hit the next major dumpsys section to save time
      if (
        inEventHubState &&
          (trimmed == "Input Reader State:" || trimmed == "Input Dispatcher State:")
      ) {
        inEventHubState = false
        return@forEach
      }

      if (inEventHubState) {
        // 2. Find the designated Built-In Keyboard ID
        if (trimmed.startsWith("BuiltInKeyboardId:")) {
          builtInId = trimmed.substringAfter(":").trim().toIntOrNull()
        }

        // 3. Detect new device blocks (Matches lines like "-1: Virtual" or "2: gpio-keys")
        val deviceMatch = Regex("^(-?\\d+):\\s+(.*)$").find(trimmed)
        if (deviceMatch != null) {
          id = deviceMatch.groupValues[1].toInt()
          val deviceName = deviceMatch.groupValues[2]
          devices[id] = InputDeviceData(id, deviceName)
        }

        // 4. Extract target properties for the currently scoped device
        if (id != null) {
          if (trimmed.startsWith("KeyLayoutFile:")) {
            val path = trimmed.substringAfter(":").trim()
            if (path.isNotEmpty()) {
              devices[id]?.keyLayoutFile = path
            }
          }
          if (trimmed.startsWith("SysfsDevicePath:")) {
            val path = trimmed.substringAfter(":").trim()
            if (path.isNotEmpty()) {
              devices[id]?.sysfsDevicePath = path
              devices[id]?.deviceId = path.substringAfterLast("/")
            }
          }
        }
      }
    }
    if (builtInId == null) {
      Log.error("Built-in keyboard ID not found.")
      return null
    }
    return devices[builtInId]!!
  }

  @SuppressLint("NewApi")
  override suspend fun tearDown() {
    val activityManager =
      factoryContext.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)
        as ActivityManager
    activityManager.forceStopPackage(DIAGNOSTIC_APP_PACKAGE_NAME)
    factoryContext.adbClient.openAdbShell().use { shell ->
      if (powerButtonShortPressIsDisabled) {
        runCatching { shell.runAndCheck("settings delete global power_button_short_press") }
          .onFailure { Log.error("Failed to reset power_button_short_press setting: $it") }
      }
      if (testKeyLayoutFileIsMounted) {
        runCatching { shell.runAndCheck("umount ${defaultKeyboard!!.keyLayoutFile}") }
          .onFailure { Log.error("Failed to umount key layout file: $it") }
        runCatching {
            shell.runAndCheck("echo -n '${defaultKeyboard!!.deviceId}' > ${driverPath}/unbind")
            // Delay to ensure the unbind is complete before binding again.
            delay(1000)
            shell.runAndCheck("echo -n '${defaultKeyboard!!.deviceId}' > ${driverPath}/bind")
          }
          .onFailure { Log.error("Failed to unbind and bind driver: $it") }
      }
      if (testKeyLayoutFile != null) {
        runCatching { shell.runAndCheck("rm ${testKeyLayoutFile}") }
          .onFailure { Log.error("Failed to remove test key layout file: $it") }
      }
    }
  }

  companion object {
    val TARGET_SCANCODES =
      mapOf<Int, String>(
        158 to "BACK",
        172 to "HOME",
        173 to "REFRESH",
        372 to "FULLSCREEN",
        120 to "RECENT_APPS",
        99 to "SYSRQ",
        224 to "BRIGHTNESS_DOWN",
        225 to "BRIGHTNESS_UP",
        228 to "KEYBOARD_BACKLIGHT_TOGGLE",
        586 to "ICTATE",
        590 to "ACCESSIBILITY",
        248 to "MUTE",
        113 to "VOLUME_MUTE",
        114 to "VOLUME_DOWN",
        115 to "VOLUME_UP",
        116 to "POWER",
        152 to "COFFEE",
      )

    const val KEY_PLACEHOLDER = "STEM_1"
    const val FALLBACK_MAPPING_KEYWORD = "FALLBACK_USAGE_MAPPING"
    const val DIAGNOSTIC_APP_PACKAGE_NAME = "com.google.android.factory.diagnostics"
    const val DIAGNOSTIC_APP_KEYBOARD_TEST_ACTIVITY_NAME =
      "com.google.android.factory.diagnostics.FactoryKeyboardActivity"
    const val DIAGNOSTIC_APP_KEYBOARD_TEST_ACTION =
      "com.google.android.factory.diagnostics.ACTION_FACTORY_KEYBOARD"

    const val EXTRA_LAYOUT_INDEX = "layout_index"
    const val EXTRA_DETECT_UNEXISTING_KEY = "detect_unexisting_key"
    const val EXTRA_RESULT_KEY = "success"
  }
}
