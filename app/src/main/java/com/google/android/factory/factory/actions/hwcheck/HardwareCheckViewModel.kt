package com.google.android.factory.factory.actions.hardware

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.factory.factory.actions.base.FactoryActionViewModel
import com.google.android.factory.factory.actions.common.AwaitableButton
import com.google.android.factory.factory.actions.common.AwaitableButtonController
import com.google.android.factory.factory.actions.interfaces.FactoryActionResult
import com.google.android.factory.factory.processor.ksp.GenerateTestArgsUtils
import com.google.android.factory.factory.ui.base.keyevent.KeyEventFilter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

@GenerateTestArgsUtils
data class HardwareProbeArgs(var probes: List<String> = emptyList())

data class HardwareProbeUiState(
  val results: List<HardwareProbeResult> = emptyList()
)

data class HardwareProbeResult(
  val name: String,
  val command: String,
  val running: Boolean = false,
  val passed: Boolean? = null,
  val output: String = "",
)

class HardwareProbeViewModel : FactoryActionViewModel<HardwareProbeArgs>() {
  private val _uiState = MutableStateFlow(HardwareProbeUiState())
  val uiState = _uiState.asStateFlow()
  val awaitableButtonController = AwaitableButtonController()

  override suspend fun runActionImpl(): FactoryActionResult {
    val probes = args.probes.map { parseProbe(it) }
    _uiState.update { it.copy(results = probes.map { p -> p.copy(running = true) }) }

    coroutineScope {
      probes.forEachIndexed { index, probe ->
        launch {
          val result =
            withTimeoutOrNull(30.seconds) { factoryContext.adbClient.runShellCommand(probe.command) }
          val passed = result?.exitCode == 0
          val output =
            when {
              result == null -> "Timeout (30s)"
              result.stdout.isNotEmpty() -> String(result.stdout).trim()
              else -> String(result.stderr).trim()
            }

          _uiState.update { state ->
            state.copy(
              results =
                state.results.mapIndexed { i, old ->
                  if (i == index) {
                    old.copy(running = false, passed = passed, output = output)
                  } else {
                    old
                  }
                }
            )
          }
        }
      }
    }

    val finalResults = _uiState.value.results
    val allPassed = finalResults.all { it.passed == true }

    return if (probes.isEmpty() || allPassed) {
      FactoryActionResult.Success
    } else {
      awaitableButtonController.awaitClick("确认失败并返回", KeyEventFilter(Key.C))
      FactoryActionResult.Failure
    }
  }

  override suspend fun tearDown() {}

  private fun parseProbe(value: String): HardwareProbeResult {
    val separator = value.indexOf(':')
    if (separator <= 0) return HardwareProbeResult(value, "false")
    return HardwareProbeResult(
      value.substring(0, separator).trim(),
      value.substring(separator + 1).trim(),
    )
  }
}

@Composable
fun HardwareProbeScreen(viewModel: HardwareProbeViewModel, modifier: Modifier = Modifier) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val results = uiState.results
  val hasFailure = results.any { it.passed == false }
  val passedCount = results.count { it.passed == true }
  val failedCount = results.count { it.passed == false }
  val checkedCount = results.count { it.passed != null }
  Column(modifier.fillMaxSize().padding(24.dp)) {
    Text("Hardware Probe", style = MaterialTheme.typography.headlineMedium)
    Text("检查关键硬件识别结果", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SummaryBadge("总数 ${results.size}")
      SummaryBadge("通过 $passedCount", Color(0xFFE8F5E9), Color(0xFF2E7D32))
      SummaryBadge("失败 $failedCount", Color(0xFFFFEBEE), Color(0xFFC62828))
      if (checkedCount < results.size) {
        SummaryBadge("检查中 ${results.size - checkedCount}")
      }
    }
    if (hasFailure) {
      Spacer(Modifier.height(8.dp))
      Text(
        "有失败项目，请核对后再确认失败返回。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
    Spacer(Modifier.height(16.dp))
    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      items(results) { ProbeCard(it) }
    }
    AwaitableButton(viewModel.awaitableButtonController)
  }
}

@Composable
private fun ProbeCard(result: HardwareProbeResult) {
  val color =
    when (result.passed) {
      true -> Color(0xFFE8F5E9)
      false -> Color(0xFFFFEBEE)
      null -> MaterialTheme.colorScheme.surfaceVariant
    }
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(result.name, style = MaterialTheme.typography.titleMedium)
        if (result.passed == false) {
          val failureMessage = result.output.ifBlank { "未通过，请检查硬件或探测命令。" }
          Spacer(Modifier.height(4.dp))
          Text(
            failureMessage,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        } else if (result.running) {
          Spacer(Modifier.height(4.dp))
          Text("正在检查…", style = MaterialTheme.typography.bodySmall)
        }
      }
      Spacer(Modifier.width(12.dp))
      StatusBadge(result)
    }
  }
}

@Composable
private fun StatusBadge(result: HardwareProbeResult) {
  val (label, background, textColor) =
    when {
      result.running ->
        Triple(
          "检查中",
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
        )
      result.passed == true -> Triple("通过", Color(0xFFC8E6C9), Color(0xFF1B5E20))
      result.passed == false -> Triple("失败", Color(0xFFFFCDD2), Color(0xFFB71C1C))
      else ->
        Triple(
          "等待",
          MaterialTheme.colorScheme.surface,
          MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
  Box(
    modifier = Modifier.background(background, MaterialTheme.shapes.small)
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Text(label, color = textColor, style = MaterialTheme.typography.labelLarge)
  }
}

@Composable
private fun SummaryBadge(
  text: String,
  background: Color = MaterialTheme.colorScheme.surfaceVariant,
  textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  Box(
    modifier = Modifier.background(background, MaterialTheme.shapes.small)
      .padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Text(text, color = textColor, style = MaterialTheme.typography.labelLarge)
  }
}
