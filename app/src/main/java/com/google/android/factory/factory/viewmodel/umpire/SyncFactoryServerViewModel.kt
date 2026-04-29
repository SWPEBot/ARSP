package com.google.android.factory.factory.viewmodel.umpire

import android.app.Application
import android.os.Build
import androidx.lifecycle.viewModelScope
import com.google.android.factory.base.logging.Log
import com.google.android.factory.base.utils.createChannel
import com.google.android.factory.base.utils.shutdownChannel
import com.google.android.factory.factory.data.interfaces.Vpd
import com.google.android.factory.factory.domain.ap.Crossystem
import com.google.android.factory.factory.domain.device.DeviceData as FactoryDeviceData
import com.google.android.factory.factory.domain.device.Phase
import com.google.android.factory.factory.domain.logging.DeviceData
import com.google.android.factory.factory.domain.logging.LogEntry
import com.google.android.factory.factory.domain.logging.LogLevel
import com.google.android.factory.factory.domain.logging.Logging
import com.google.android.factory.factory.domain.logging.LoggingAPI
import com.google.android.factory.factory.domain.logging.withStep
import com.google.android.factory.factory.domain.otaupdate.OtaUpdate
import com.google.android.factory.factory.processor.ksp.GenerateTestArgsUtils
import com.google.android.factory.factory.proto.Component
import com.google.android.factory.factory.proto.DeviceMetadata
import com.google.android.factory.factory.proto.GetOtaPackageRequest
import com.google.android.factory.factory.proto.GetUpdateVersionRequest
import com.google.android.factory.factory.proto.SyncDeviceTimeRequest
import com.google.android.factory.factory.proto.SyncDeviceTimeResponse
import com.google.android.factory.factory.proto.TestObject
import com.google.android.factory.factory.proto.TestPhase
import com.google.android.factory.factory.proto.UmpireDUTCommandsGrpc
import com.google.android.factory.factory.proto.UmpireDUTCommandsGrpcKt
import com.google.android.factory.factory.proto.UpdateFactoryAppRequest
import com.google.android.factory.factory.proto.UpdateFactoryAppResponse
import com.google.android.factory.factory.proto.UploadFileRequest
import com.google.android.factory.factory.viewmodel.base.TestStateListener
import com.google.android.factory.factory.viewmodel.base.TestViewModel
import com.google.protobuf.ByteString
import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.stub.AbstractStub
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Arguments of [SyncFactoryServerViewModel].
 *
 * @property updateToolkit Whether to check factory update.
 * @property updateWithoutPrompt Update without prompting when an update is available.
 * @property waitUpdateSecs Number of seconds to wait before the update. This only applies when
 *   [updateWithoutPrompt] is true.
 * @property downloadOtaPackage Whether to download OTA package.
 * @property serverUrl Set and keep new factory server URL.
 * @property caName The CA bundle, which should be installed as assets of the apk.
 * @property developer If set, then the test won't fail/pass automatically.
 * @property uploadReport Whether to upload factory report to the factory server.
 * @property disableLogging Disable logging and delete logs; if set with ``uploadReport``, this
 *   makes effect after upload.
 * @property syncDeviceTime Whether to synchronize the device time/timezone.
 * @property timeoutSecs Number of seconds to wait for the factory server to become
 *   reachable before connecting to it.
 */
@GenerateTestArgsUtils
data class SyncFactoryServerArgs(
  var updateToolkit: Boolean = false,
  var updateWithoutPrompt: Boolean = false,
  var waitUpdateSecs: Int = 5,
  var downloadOtaPackage: Boolean = false,
  var serverUrl: String = "",
  var caName: String = "",
  var developer: Boolean = false,
  var uploadReport: Boolean = false,
  var disableLogging: Boolean = false,
  var syncDeviceTime: Boolean = false,
  var timeoutSecs: Int = 60,
)

data class SyncFactoryServerUiState(
  var proxyError: String = "",
  var updateAppResponse: UpdateFactoryAppResponse? = null,
  var syncTimeResponse: SyncDeviceTimeResponse? = null,
  var promptForUpdateState: Int = PROMPT_INITIAL,
  var localVersion: String? = null,
  var updateVersion: String? = null,
  var countDownSecsForUpdate: Int = 0,
) {
  companion object {
    const val PROMPT_INITIAL = 0
    const val PROMPT_WAITING = 1
    const val PROMPT_ACCEPTED = 2
    const val PROMPT_DENIED = 3
  }
}

private fun parseServerUrls(serverUrl: String): List<String> =
  serverUrl
    .split(',', ';', '\n')
    .map { it.trim() }
    .filter { it.isNotEmpty() }

private fun Phase.toTestPhase(): TestPhase {
  return when (this) {
    Phase.PROTO -> TestPhase.TEST_PHASE_PROTO
    Phase.EVT -> TestPhase.TEST_PHASE_EVT
    Phase.DVT -> TestPhase.TEST_PHASE_DVT
    Phase.PVT -> TestPhase.TEST_PHASE_PVT
    Phase.MP -> TestPhase.TEST_PHASE_MP
  }
}

class SyncFactoryServerViewModel(
  application: Application,
  testStateListener: TestStateListener,
  test: TestObject,
) :
  TestViewModel<SyncFactoryServerArgs>(
    application,
    testStateListener,
    test,
    SyncFactoryServerArgs(),
    SyncFactoryServerArgs_GeneratedConverter(),
  ) {
  private val context
    get() = getApplication<Application>().applicationContext

  private val grpcExecutor = Executors.newSingleThreadExecutor()
  private val grpcContext = grpcExecutor.asCoroutineDispatcher()
  private var runTestJob: Job? = null
  private var tearDownJob: Job? = null
  private var channel: ManagedChannel? = null

  private val _uiState = MutableStateFlow(SyncFactoryServerUiState())
  val uiState = _uiState.asStateFlow()

  init {
    addCloseable {
      grpcContext.cancel()
      grpcExecutor.shutdown()
    }
  }

  private suspend fun <S : AbstractStub<*>> createStub(
    args: SyncFactoryServerArgs,
    getStub: (channel: Channel) -> S,
  ): S {
    val logError = { err: Exception ->
      _uiState.update { it.copy(proxyError = "$err ${err.cause}") }
      Log.info("proxyError: ${_uiState.value.proxyError}")
      Unit
    }

    val serverUrl =
      if (args.serverUrl.isEmpty()) {
        Log.info("The server URL is empty, fetch from factory config.")
        val factoryConfig = factoryContext.factoryPartition.fetchFactoryConfig()
        factoryConfig.umpireServerUri
      } else {
        args.serverUrl
      }
    if (serverUrl.isEmpty()) {
      Log.error("The server URL is empty.")
      failTest(args.developer)
    }

    val tempChannel = createChannel(context, args.caName, parseServerUrls(serverUrl).first(), logError)
    if (tempChannel == null) {
      failTest(args.developer)
    }
    try {
      return getStub(tempChannel).also {
        // We assign the channel after both creating a channel and getting a stub succeed.
        channel = tempChannel
      }
    } catch (err: Exception) {
      shutdownChannel(tempChannel)
      logError(err)
      failTest(args.developer)
    }
  }

  private suspend fun createFutureStub(
    args: SyncFactoryServerArgs
  ): UmpireDUTCommandsGrpc.UmpireDUTCommandsFutureStub =
    createStub(args) { UmpireDUTCommandsGrpc.newFutureStub(it) }

  private suspend fun createCoroutineStub(
    args: SyncFactoryServerArgs
  ): UmpireDUTCommandsGrpcKt.UmpireDUTCommandsCoroutineStub =
    createStub(args) { UmpireDUTCommandsGrpcKt.UmpireDUTCommandsCoroutineStub(it) }

  private suspend fun waitForServerUrlArgs(): SyncFactoryServerArgs {
    val argsWithServerUrl =
      args
        .map { it.copy(serverUrl = it.serverUrl.trim()) }
        .first { it.serverUrl.isNotEmpty() }
    Log.info("Got server URL from args: ${argsWithServerUrl.serverUrl}")
    return argsWithServerUrl
  }

  private data class ServerEndpoint(val host: String, val port: Int)

  private fun parseServerEndpoint(serverUrl: String): ServerEndpoint {
    val uri = URI(if ("://" in serverUrl) serverUrl else "grpc://$serverUrl")
    val host = uri.host
    val port = uri.port
    if (host.isNullOrEmpty() || port < 0) {
      throw IllegalArgumentException("Invalid server URL: $serverUrl")
    }
    return ServerEndpoint(host, port)
  }

  private suspend fun waitForServerReady(args: SyncFactoryServerArgs): SyncFactoryServerArgs {
    if (args.timeoutSecs <= 0) {
      return args.copy(serverUrl = parseServerUrls(args.serverUrl).first())
    }
    val serverUrls = parseServerUrls(args.serverUrl)
    if (serverUrls.isEmpty()) {
      Log.error("The server URL is empty.")
      failTest(args.developer)
    }
    Log.info(
      "Wait for factory servers ${serverUrls.joinToString()} " +
        "for ${args.timeoutSecs} seconds."
    )
    var readyServerUrl: String? = null
    val ready =
      withTimeoutOrNull(args.timeoutSecs.seconds) {
        while (true) {
          for (serverUrl in serverUrls) {
            val endpoint =
              try {
                parseServerEndpoint(serverUrl)
              } catch (err: IllegalArgumentException) {
                Log.error(err.message ?: "Invalid server URL: $serverUrl")
                continue
              }
            val canConnect =
              withContext(factoryContext.ioDispatcher) {
                runCatching {
                    Socket().use {
                      it.connect(InetSocketAddress(endpoint.host, endpoint.port), 1000)
                    }
                  }
                  .isSuccess
              }
            if (canConnect) {
              readyServerUrl = serverUrl
              return@withTimeoutOrNull true
            }
          }
          delay(1.seconds)
        }
      }
    if (ready != true) {
      Log.error("Factory servers ${serverUrls.joinToString()} are unreachable.")
      failTest(args.developer)
    }
    val selectedServerUrl = readyServerUrl ?: serverUrls.first()
    Log.info("Factory server $selectedServerUrl is reachable.")
    return args.copy(serverUrl = selectedServerUrl)
  }

  private suspend fun downloadOtaPackage(args: SyncFactoryServerArgs) {

    val stub = createFutureStub(args)
    // TODO: chungsheng - Move the OTA package path to domain/.
    val downloadPath =
      File(OtaUpdate.UMPIRE_DOWNLOAD_DIR, OtaUpdate.OTA_PACKAGE_ZIP_NAME).absolutePath
    val response =
      try {
        stub.getOtaPackage(GetOtaPackageRequest.newBuilder().setPath(downloadPath).build()).await()
      } catch (err: Exception) {
        _uiState.update { it.copy(proxyError = "$err ${err.cause}") }
        Log.info("proxyError: ${_uiState.value.proxyError}")
        failTest(args.developer)
      }
    Log.info("otaPackageResponse: $response")
    if (!response.success) {
      failTest(args.developer)
    }
  }

  /** Updates the ALMAK app from the factory server. */
  private suspend fun updateToolkit(args: SyncFactoryServerArgs) {
    val localVersion = factoryContext.systemInfo.calculateAppMd5Hash()
    Log.info("localVersion: $localVersion")
    _uiState.update { it.copy(localVersion = localVersion) }
    val stub = createFutureStub(args)
    val updateVersion =
      try {
        stub
          .getUpdateVersion(
            GetUpdateVersionRequest.newBuilder().setComponent(Component.COMPONENT_TOOLKIT).build()
          )
          .await()
          .version
      } catch (err: Exception) {
        _uiState.update { it.copy(proxyError = "$err ${err.cause}") }
        Log.info("proxyError getUpdateVersion: ${_uiState.value.proxyError}")
        failTest(args.developer)
      }
    Log.info("updateVersion: $updateVersion")
    _uiState.update { it.copy(updateVersion = updateVersion) }
    when {
      updateVersion.isEmpty() -> {
        Log.info("No update in the server.")
        return
      }
      // version from DOME contains parentheses. Ex. local version: 439b6af62634d94b2642b6f69dfc57a0
      // update version: (439b6af62634d94b2642b6f69dfc57a0)
      localVersion in updateVersion -> {
        Log.info("No need to update from the server.")
        return
      }
    }
    if (!args.updateWithoutPrompt || args.waitUpdateSecs > 0) {
      _uiState.update { it.copy(promptForUpdateState = SyncFactoryServerUiState.PROMPT_WAITING) }
      val countDownTimer =
        if (args.updateWithoutPrompt) {
          viewModelScope.async {
            for (index in 0 until args.waitUpdateSecs) {
              _uiState.update { it.copy(countDownSecsForUpdate = args.waitUpdateSecs - index) }
              delay(1.seconds)
            }
            _uiState.update {
              it.copy(
                countDownSecsForUpdate = 0,
                promptForUpdateState = SyncFactoryServerUiState.PROMPT_ACCEPTED,
              )
            }
          }
        } else {
          null
        }

      val state = _uiState.first {
        it.promptForUpdateState in
          arrayOf(SyncFactoryServerUiState.PROMPT_ACCEPTED, SyncFactoryServerUiState.PROMPT_DENIED)
      }
      countDownTimer?.cancel()
      if (state.promptForUpdateState == SyncFactoryServerUiState.PROMPT_DENIED) {
        failTest(args.developer)
      }
    }

    _uiState.update { it.copy(updateAppResponse = null) }
    val response =
      try {
        stub.updateFactoryApp(UpdateFactoryAppRequest.newBuilder().build()).await()
      } catch (err: Exception) {
        _uiState.update { it.copy(proxyError = "$err ${err.cause}") }
        Log.info("proxyError: ${_uiState.value.proxyError}")
        failTest(args.developer)
      }
    _uiState.update { it.copy(updateAppResponse = response) }
    Log.info("response: $response")
    if (!response.success) {
      failTest(args.developer)
    }
  }

  /** Synchronizes the device time using the Umpire server's timezone. */
  private suspend fun syncDeviceTime(args: SyncFactoryServerArgs) {
    _uiState.update { it.copy(syncTimeResponse = null) }
    val stub = createFutureStub(args)
    val request = SyncDeviceTimeRequest.newBuilder().build()

    val response =
      try {
        stub.syncDeviceTime(request).await()
      } catch (err: Exception) {
        _uiState.update { it.copy(proxyError = "$err ${err.cause}") }
        Log.info("proxyError syncDeviceTime: ${_uiState.value.proxyError}")
        failTest(args.developer)
      }

    _uiState.update { it.copy(syncTimeResponse = response) }
    Log.info("syncTimeResponse: $response")
    if (!response.success) {
      failTest(args.developer)
    }
  }

  /**
   * Prints device data to the logs for further analysis in the backend logging pipeline.
   *
   * TODO: b/410317834 - Also collect EDID observations data.
   */
  private suspend fun logDeviceData() {
    withStep("Collect device data") {
      val crossystem = Crossystem(factoryContext.adbClient)
      val factoryDeviceData = FactoryDeviceData(factoryContext)
      val systemInfo = factoryContext.systemInfo
      val vpd = factoryContext.vpd
      val deviceData =
        JSONObject().apply {
          put(DeviceData.API_VERSION, LoggingAPI.VERSION)
          put(DeviceData.APP_VERSION, systemInfo.appVersionName)
          put(DeviceData.FWID, crossystem.getFwid() ?: "")
          put(DeviceData.HW_DESCRIPTOR, vpd.get(Vpd.Key.HARDWARE_DESCRIPTOR))
          put(DeviceData.IMAGE_VERSION, Build.FINGERPRINT)
          put(DeviceData.MODEL_NAME, Build.MODEL)
          put(DeviceData.PHASE, Phase.get(factoryContext).name)
          put(DeviceData.RO_FWID, crossystem.getRoFwid() ?: "")
          put(DeviceData.SERIAL_NUMBER, factoryDeviceData.serialNumber.get()?.stringValue ?: "")
          put(DeviceData.WPSW_CUR, crossystem.getWpswCur() ?: "")
        }

      withStep(
        "Print device data to logs",
        attributes = JSONObject(mapOf(DeviceData.ATTRIBUTE_KEY to deviceData)),
      ) {
        // Empty step for printing data purpose.
      }
    }
  }

  private fun Flow<ByteString>.toFileRequestFlow(): Flow<UploadFileRequest> =
    this.map { UploadFileRequest.newBuilder().setChunk(it).build() }
      .onStart {
        val deviceData = FactoryDeviceData(factoryContext)
        val serialNumber = deviceData.serialNumber.get()?.stringValue
        if (serialNumber.isNullOrEmpty()) {
          throw Exception("Cannot get serial number.")
        }
        val testPhase = Phase.get(factoryContext).toTestPhase()
        val deviceMetadataRequest =
          UploadFileRequest.newBuilder()
            .setDeviceMetadata(
              DeviceMetadata.newBuilder()
                .setSerialNumber(serialNumber)
                .setTestPhase(testPhase)
                .build()
            )
            .build()
        emit(deviceMetadataRequest)
      }

  /** Uploads logs to the factory server. */
  private suspend fun uploadReport(args: SyncFactoryServerArgs) {
    withStep("Upload factory report to the factory server") {
      val requestFlow =
        Logging.readLogs()
          .mapNotNull { logEntry ->
            // Filter out debug logs since the backend pipeline does not need this. If the level
            // field does not exist, we can infer the entry is of type "step", which we also want to
            // keep.
            val level = logEntry.optString(LogEntry.Key.LEVEL)
            if (level == LogLevel.DEBUG.name) {
              null
            } else {
              ByteString.copyFrom((logEntry.toString() + '\n').encodeToByteArray())
            }
          }
          .toFileRequestFlow()
      val stub = createCoroutineStub(args)
      withContext(factoryContext.ioDispatcher) {
        val unused = stub.uploadReport(requestFlow)
      }
    }
  }

  override fun runTest() {
    super.runTest()
    val initialArgs = args.value
    val cachedTearDownJob = tearDownJob?.also { tearDownJob = null }
    runTestJob =
      viewModelScope.launchTestJob(grpcContext, manual = initialArgs.developer) {
        cachedTearDownJob?.join()
        val args = waitForServerReady(waitForServerUrlArgs())

        if (args.updateToolkit) {
          updateToolkit(args)
        } else {
          Log.info("Skip updating toolkit.")
        }
        if (args.downloadOtaPackage) {
          downloadOtaPackage(args)
        } else {
          Log.info("Skip downloading OTA package.")
        }
        if (args.uploadReport) {
          logDeviceData()
          uploadReport(args)
        } else {
          Log.info("Skip uploading factory report.")
        }
        if (args.disableLogging) {
          Log.info("Logging disabled; there should be no more logs.")
          Logging.disable()
        }
        if (args.syncDeviceTime) {
          syncDeviceTime(args)
        } else {
          Log.info("Skip synchronizing device time.")
        }

        passTest(args.developer)
      }
  }

  override fun tearDown() {
    super.tearDown()
    val cachedRunTestJob = runTestJob?.also { runTestJob = null }
    val cachedChannel = channel?.also { channel = null }
    tearDownJob =
      viewModelScope.launch(grpcContext) {
        cachedRunTestJob?.cancelAndJoin()
        cachedChannel?.also { shutdownChannel(it) }
        _uiState.update { SyncFactoryServerUiState() }
      }
  }

  fun setPromptForUpdate(value: Int) {
    _uiState.update { it.copy(promptForUpdateState = value) }
  }
}
