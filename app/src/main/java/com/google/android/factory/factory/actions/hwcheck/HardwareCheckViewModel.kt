package com.google.android.factory.factory.actions.HardwareCheck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.factory.factory.actions.base.FactoryActionViewModel
import com.google.android.factory.factory.actions.interfaces.FactoryActionResult
import com.google.android.factory.factory.domain.device.DeviceData
import com.google.android.factory.factory.processor.ksp.GenerateTestArgsUtils
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.google.protobuf.Value
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

// ==========================================
// 1. Data Structures
// ==========================================
@GenerateTestArgsUtils
data class HardwareCheckArgs(
    var unused: String = ""
)

data class HardwareInfo(
    val cpu: String,
    val memoryGb: Int,
    val storageGb: Int
)

sealed class CheckStatus {
    object Loading : CheckStatus()
    object MissingData : CheckStatus()
    data class Pass(val expected: HardwareInfo, val actual: HardwareInfo, val countdown: Int) : CheckStatus()
    data class Fail(val expected: HardwareInfo, val actual: HardwareInfo) : CheckStatus()
}

// ==========================================
// 2. ViewModel
// ==========================================
class HardwareCheckViewModel : FactoryActionViewModel<HardwareCheckArgs>() {
    private val _status = MutableStateFlow<CheckStatus>(CheckStatus.Loading)
    val status = _status.asStateFlow()

    private val userExitSignal = CompletableDeferred<Unit>()

    override suspend fun runActionImpl(): FactoryActionResult {
        val deviceData = DeviceData(factoryContext)
        
        // 1. 搜集 device-data store 里的预期信息
        // 使用更鲁棒的读取方式，并添加日志
        val expectedCpu = deviceData["google.cpu_type"].get()?.toSimpleString()
        val expectedMem = deviceData["google.memory_size"].get()?.toSimpleString()
        val expectedStorage = deviceData["google.storage_size"].get()?.toSimpleString()

        com.google.android.factory.base.logging.Log.info("HardwareCheck: expectedCpu=$expectedCpu, expectedMem=$expectedMem, expectedStorage=$expectedStorage")

        if (expectedCpu.isNullOrBlank() || expectedMem.isNullOrBlank() || expectedStorage.isNullOrBlank()) {
            _status.value = CheckStatus.MissingData
            userExitSignal.await()
            return FactoryActionResult.Failure
        }

        val expectedInfo = HardwareInfo(
            cpu = expectedCpu,
            memoryGb = expectedMem.toIntOrNull() ?: 0,
            storageGb = if (expectedStorage.equals("SSD", ignoreCase = true)) -1 else expectedStorage.toIntOrNull() ?: 0
        )

        // 2. 读取本机实际硬件信息 (通过 Shell 命令)
        val actualInfo = try {
            readActualHardware()
        } catch (_: Exception) {
            HardwareInfo("Error reading CPU", 0, 0)
        }

        // 3. 比对逻辑
        val isCpuMatch = actualInfo.cpu.contains(expectedInfo.cpu, ignoreCase = true) || 
                         expectedInfo.cpu.contains(actualInfo.cpu, ignoreCase = true)
        val isMemMatch = actualInfo.memoryGb == expectedInfo.memoryGb
        val isStorageMatch = expectedInfo.storageGb == -1 || actualInfo.storageGb == expectedInfo.storageGb

        return if (isCpuMatch && isMemMatch && isStorageMatch) {
            // 成功：显示 5 秒并结束
            for (i in 5 downTo 1) {
                _status.value = CheckStatus.Pass(expectedInfo, actualInfo, i)
                delay(1000)
            }
            FactoryActionResult.Success
        } else {
            // 失败：显示差异，等待 Esc 按键
            _status.value = CheckStatus.Fail(expectedInfo, actualInfo)
            userExitSignal.await()
            FactoryActionResult.Failure
        }
    }

    private fun Value.toSimpleString(): String {
        return when (kindCase) {
            Value.KindCase.STRING_VALUE -> stringValue
            Value.KindCase.NUMBER_VALUE -> numberValue.toInt().toString()
            Value.KindCase.BOOL_VALUE -> boolValue.toString()
            else -> ""
        }
    }

    private suspend fun readActualHardware(): HardwareInfo {
        // CPU: cat /proc/cpuinfo 里面 model name 那一栏
        val cpuRaw = factoryContext.adbClient.runShellCommand("cat /proc/cpuinfo | grep 'model name' | head -n 1").outText
        val cpu = cpuRaw.substringAfter(":").trim()

        // Memory: cat /proc/meminfo 里面 MemTotal
        val memRaw = factoryContext.adbClient.runShellCommand("cat /proc/meminfo | grep MemTotal").outText
        val memKb = Regex("\\d+").find(memRaw)?.value?.toLongOrNull() ?: 0L
        val memGbRaw = (memKb / (1024.0 * 1024.0)).toInt()
        val memGb = roundSize(memGbRaw, 256)

        // Storage: disk_info --info 里面 Size 这一栏
        val storageRaw = factoryContext.adbClient.runShellCommand("disk_info --info | grep -i 'Size' | head -n 1").outText
        
        // 优先匹配 "xxx GB" 这种格式，如果没找到再解析大数字字节数
        val storageMatch = Regex("(\\d+(\\.\\d+)?)\\s*GB").find(storageRaw)
        val storageGbRaw = if (storageMatch != null) {
            storageMatch.groupValues[1].toDoubleOrNull()?.toInt() ?: 0
        } else {
            val firstNumStr = Regex("\\d+").find(storageRaw)?.value
            val firstNum = firstNumStr?.toLongOrNull() ?: 0L
            if (firstNum > 5000) { // 认为是大数字（字节）
                (firstNum / 1_000_000_000.0).toInt()
            } else {
                firstNum.toInt()
            }
        }
        
        com.google.android.factory.base.logging.Log.info("HardwareCheck: storageRaw=$storageRaw, parsedGb=$storageGbRaw")
        val storageGb = roundSize(storageGbRaw, 4096)

        return HardwareInfo(cpu, memGb, storageGb)
    }

    private fun roundSize(size: Int, max: Int): Int {
        if (size <= 0) return 0
        val standards = mutableListOf<Int>()
        var current = 2
        while (current <= max) {
            standards.add(current)
            if (current >= 8) {
                // 处理 12, 24 等非 2 的幂次常见内存/硬盘规格 (可选)
                if (current == 8) standards.add(12)
                if (current == 16) standards.add(24)
            }
            current *= 2
        }
        // 确保包含 4096G 等大容量规格
        if (!standards.contains(max)) standards.add(max)
        
        return standards.minByOrNull { abs(it - size) } ?: size
    }

    fun signalUserExit() {
        if (!userExitSignal.isCompleted) {
            userExitSignal.complete(Unit)
        }
    }

    override suspend fun tearDown() {}
}

// ==========================================
// 3. Screen (Compose UI)
// ==========================================
@Composable
fun HardwareCheckScreen(viewModel: HardwareCheckViewModel) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    viewModel.signalUserExit()
                    true
                } else {
                    false
                }
            }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "硬件合规性检测 (Hardware Check)", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            when (val s = status) {
                is CheckStatus.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在读取本机硬件信息...")
                }
                is CheckStatus.MissingData -> {
                    Text("⚠️ 未获取到相关信息", style = MaterialTheme.typography.titleLarge, color = Color(0xFFE65100))
                    Text("提示：device-data 中缺少 google.cpu_type, memory_size 或 storage_size", color = Color.Gray)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("请敲击 ESC 键标记失败并结束", style = MaterialTheme.typography.bodySmall)
                }
                is CheckStatus.Pass -> {
                    Text("✅ 检测通过 (PASS)", style = MaterialTheme.typography.displaySmall, color = Color(0xFF2E7D32))
                    Text("将在 ${s.countdown} 秒后自动标记成功并结束...", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    ComparisonTable(expected = s.expected, actual = s.actual)
                }
                is CheckStatus.Fail -> {
                    Text("❌ 检测失败 (FAIL)", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error)
                    Text("请检查下方差异项。敲击 ESC 键标记失败并结束。", color = Color.Gray)
                    Spacer(modifier = Modifier.height(24.dp))
                    ComparisonTable(expected = s.expected, actual = s.actual)
                }
            }
        }
    }
}

@Composable
fun ComparisonTable(expected: HardwareInfo, actual: HardwareInfo) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("项目", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("预期 (DeviceData)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.5f))
                Text("实际 (Actual)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.5f))
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            ComparisonRow("CPU 型号", expected.cpu, actual.cpu)
            ComparisonRow("内存容量", "${expected.memoryGb} GB", "${actual.memoryGb} GB")
            ComparisonRow(
                "硬盘容量", 
                if (expected.storageGb == -1) "SSD (不检查)" else "${expected.storageGb} GB", 
                actual.actualStorageString(expected.storageGb == -1)
            )
        }
    }
}

private fun HardwareInfo.actualStorageString(isSkip: Boolean): String {
    return if (isSkip) "N/A" else "$storageGb GB"
}

@Composable
fun ComparisonRow(label: String, expected: String, actual: String) {
    val isMatch = expected.contains(actual, ignoreCase = true) || 
                  actual.contains(expected, ignoreCase = true) || 
                  expected.contains("不检查")
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = expected, 
            modifier = Modifier.weight(1.5f), 
            color = if (isMatch) Color.Unspecified else Color(0xFF1976D2),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = actual, 
            modifier = Modifier.weight(1.5f), 
            color = if (isMatch) Color.Unspecified else Color.Red,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
