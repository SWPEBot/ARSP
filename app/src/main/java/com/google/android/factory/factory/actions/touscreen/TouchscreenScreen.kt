package com.google.android.factory.factory.actions.touchscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchscreenScreen(viewModel: TouchscreenViewModel, modifier: Modifier = Modifier) {
  val uiState by viewModel.touchscreenState.collectAsStateWithLifecycle()
  val onKeyPressed = { event: KeyEvent ->
    when {
      event.type != KeyEventType.KeyDown -> {
        false
      }
      event.key == Key.S -> {
        viewModel.toggleTestStarted()
        true
      }
      event.key == Key.C -> {
        viewModel.haltTest()
        true
      }
      else -> false
    }
  }
  val requester = remember { FocusRequester() }
  LaunchedEffect(Unit) { requester.requestFocus() }
  Column(
    modifier.fillMaxSize().onKeyEvent(onKeyPressed).focusRequester(requester).focusable(),
    Arrangement.Center,
    Alignment.CenterHorizontally,
  ) {
    Column(
      modifier.weight(1f).padding(24.dp),
      Arrangement.Center,
      Alignment.CenterHorizontally,
    ) {
      if (uiState.hardwareDetected == false) {
        Text(
          "未探测到触屏硬件",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
          "如果该设备【应该】有触屏，请检查硬件连接或驱动。\n防止漏测风险：若无触屏硬件且不是预期行为，请报修。",
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Text("按 S 键重试探测，或按 C 键确认失败返回", style = MaterialTheme.typography.bodyMedium)
      } else {
        Text(
          "请将手指移开屏幕，然后按 S 键开始测试...",
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
      Button({ viewModel.toggleTestStarted() }) {
        Text(if (uiState.hardwareDetected == false) "重试探测 (S)" else "开始测试 (S)")
      }
      if (uiState.hardwareDetected == false) {
        Button(
          { viewModel.haltTest() },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("确认失败并返回 (C)")
        }
      }
    }
  }
  if (uiState.testStarted) {
    Dialog(
      onDismissRequest = { viewModel.haltTest() },
      properties =
        DialogProperties(
          dismissOnBackPress = false,
          dismissOnClickOutside = false,
          usePlatformDefaultWidth = false,
        ),
    ) {
      Surface(
        Modifier.pointerInteropFilter { viewModel.onTouch(it) }
          .fillMaxSize()
          .focusRequester(requester)
          .focusable()
      ) {
        TouchscreenSegments(
          viewModel.xSegments,
          viewModel.ySegments,
          uiState.isTouchedTested,
          Modifier.fillMaxSize(),
        )
      }
      if (uiState.countDown != -1) {
        Row(Modifier.fillMaxWidth(), Arrangement.Center) {
          Text("剩余时间: ${uiState.countDown}", style = MaterialTheme.typography.titleLarge)
        }
      }
    }
  }
}

@Composable
private fun TouchscreenSegments(
  xSegments: Int,
  ySegments: Int,
  touchedTested: List<List<Boolean>>,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    for (y in 1..ySegments) {
      Row(Modifier.fillMaxWidth().weight(1f)) {
        for (x in 1..xSegments) {
          BoxWithConstraints(
            Modifier.fillMaxHeight()
              .weight(1f)
              .background(
                if (touchedTested[y - 1][x - 1]) {
                  Color.Green
                } else {
                  Color.Gray
                }
              ),
            contentAlignment = Alignment.Center,
          ) {
            val scaleFactor = 0.1f
            val fontSize = with(LocalDensity.current) { (maxWidth * scaleFactor).toSp() }
            Text("touch-$y-$x", textAlign = TextAlign.Center, fontSize = fontSize)
          }
        }
      }
    }
  }
}
