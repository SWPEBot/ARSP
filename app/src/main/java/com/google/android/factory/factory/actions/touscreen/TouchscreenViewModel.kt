package com.google.android.factory.factory.actions.touchscreen

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import com.google.android.factory.base.logging.Log
import com.google.android.factory.factory.actions.base.FactoryActionViewModel
import com.google.android.factory.factory.actions.common.withCountdown
import com.google.android.factory.factory.actions.interfaces.FactoryActionResult
import com.google.android.factory.factory.domain.touch.TouchManager
import com.google.android.factory.factory.processor.ksp.GenerateTestArgsUtils
import kotlin.math.min
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Arguments of [TouchscreenViewModel].
 *
 * @property deviceId The device id. Auto detect if set to -1.
 * @property timeoutSecs Timeout for the test.
 * @property xSegments Number of X axis segments to test. Default is 5.
 * @property ySegments Number of Y axis segments to test. Default is 5.
 * @property optional If true, the test will pass if no touchscreen is detected.
 */
@GenerateTestArgsUtils
data class TouchscreenArgs(
  var deviceId: Int = -1,
  var timeoutSecs: Int = 20,
  var xSegments: Int = 5,
  var ySegments: Int = 5,
  var optional: Boolean = false,
)

data class TouchscreenState(
  val testStarted: Boolean = false,
  val deviceIds: List<Int> = emptyList(),
  val isTouchedTested: List<List<Boolean>> = emptyList<List<Boolean>>(),
  val countDown: Int = -1,
  val isHalted: Boolean = false,
  val hardwareDetected: Boolean? = null,
)

/**
 * Tests the functionality of touchscreen.
 *
 * The test checks the functionality of touchscreen by detecting touch events on every segments on
 * touchscreen.
 */
class TouchscreenViewModel : FactoryActionViewModel<TouchscreenArgs>() {

  private val inputManager by lazy {
    factoryContext.applicationContext.getSystemService(Context.INPUT_SERVICE) as InputManager
  }
  private val manager by lazy { TouchManager(factoryContext) }
  private val _touchscreenState = MutableStateFlow(TouchscreenState())
  val touchscreenState = _touchscreenState.asStateFlow()

  val xSegments: Int
    get() = args.xSegments

  val ySegments: Int
    get() = args.ySegments

  override suspend fun runActionImpl(): FactoryActionResult {
    _touchscreenState.update { TouchscreenState() }

    // First judge if hardware exists
    val initialDevices = manager.getTouchscreenDevices(args.deviceId)
    if (initialDevices.isNotEmpty()) {
      Log.info("Touchscreen hardware detected, starting test automatically.")
      _touchscreenState.update {
        it.copy(
          testStarted = true,
          hardwareDetected = true,
          deviceIds = initialDevices.map { it.id },
          isTouchedTested = List(args.ySegments) { List(args.xSegments) { false } },
        )
      }
    } else {
      Log.info("No touchscreen hardware detected.")
      if (args.optional) {
        Log.info("Test is optional, skipping.")
        return FactoryActionResult.Success
      }
      _touchscreenState.update { it.copy(hardwareDetected = false) }
    }

    // Wait for test to start (auto-started or manual S pressed)
    while (true) {
      val state = touchscreenState.first { it.testStarted || it.isHalted }
      if (state.isHalted) {
        return FactoryActionResult.Failure
      }

      val devices = manager.getTouchscreenDevices(args.deviceId)
      if (devices.isEmpty()) {
        Log.warn("Still no touchscreen devices found after S pressed.")
        _touchscreenState.update { it.copy(testStarted = false, hardwareDetected = false) }
        continue
      }

      val deviceIds = devices.map { it.id }
      _touchscreenState.update {
        it.copy(
          testStarted = true,
          hardwareDetected = true,
          deviceIds = deviceIds,
          isTouchedTested = List(args.ySegments) { List(args.xSegments) { false } },
        )
      }
      break
    }

    try {
      val unused =
        factoryContext.withCountdown(
          args.timeoutSecs,
          { remainingSeconds -> _touchscreenState.update { it.copy(countDown = remainingSeconds) } },
        ) {
          touchscreenState.first {
            it.isTouchedTested.all { row -> row.all { cell -> cell } } || it.isHalted
          }
        }
      if (touchscreenState.value.isHalted) {
        return FactoryActionResult.Failure
      }
      _touchscreenState.update { it.copy(countDown = 0) }
      return FactoryActionResult.Success
    } catch (e: TimeoutCancellationException) {
      _touchscreenState.update { it.copy(countDown = 0) }
      Log.info("Timeout")
      return FactoryActionResult.Failure
    }
  }

  override suspend fun tearDown() {
    _touchscreenState.update { it.copy(isHalted = true) }
  }

  private fun isTouchscreenEvent(event: MotionEvent): Boolean {
    return event.deviceId in touchscreenState.value.deviceIds &&
      event.source == InputDevice.SOURCE_TOUCHSCREEN &&
      event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
  }

  fun onTouch(event: MotionEvent): Boolean {
    if (!isTouchscreenEvent(event)) {
      return false
    }

    val actionUpIndex =
      when (event.actionMasked) {
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_POINTER_UP -> {
          event.actionIndex
        }
        else -> {
          null
        }
      }
    val device = inputManager.getInputDevice(event.deviceId) ?: event.device
    val xMax = device.getMotionRange(MotionEvent.AXIS_X).max
    val yMax = device.getMotionRange(MotionEvent.AXIS_Y).max
    val points = IntRange(0, event.pointerCount - 1).filter { it != actionUpIndex }

    _touchscreenState.update {
      val isTouchedTested = it.isTouchedTested.map { it.toMutableList() }.toMutableList()
      for (point in points) {
        val xIndex = min((event.getX(point) / xMax * args.xSegments).toInt(), args.xSegments - 1)
        val yIndex = min((event.getY(point) / yMax * args.ySegments).toInt(), args.ySegments - 1)
        isTouchedTested[yIndex][xIndex] = true
      }
      it.copy(isTouchedTested = isTouchedTested)
    }
    return true
  }

  fun toggleTestStarted() {
    if (!touchscreenState.value.testStarted) {
      _touchscreenState.update { it.copy(testStarted = true) }
    }
  }

  fun haltTest() {
    _touchscreenState.update { it.copy(isHalted = true) }
  }
}
